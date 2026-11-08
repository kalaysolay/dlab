#!/bin/sh
# Ручная поставка словаря для docker-compose.prod.yml. Рабочая БД меняется только
# при явном запуске этого файла; --preview ограничивается чтением.
set -eu
umask 077

package_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
app_dir=$(CDPATH= cd "$package_dir/../.." && pwd)
backup_dir=${DAMULAB_BACKUP_DIR:-/var/backups/damulab}
mode=replace-en-ru.psql
preview=false
case ${1:-} in
    '') ;;
    --preview) preview=true ;;
    --all-old-english) mode=replace-all-english.psql ;;
    *) echo 'Использование: apply-prod.sh [--preview|--all-old-english]' >&2; exit 2 ;;
esac
if [ "$#" -gt 1 ]; then echo 'Допускается только один аргумент.' >&2; exit 2; fi

dc() { docker compose --env-file "$app_dir/.env.prod" -f "$app_dir/docker-compose.prod.yml" "$@"; }
remote=
restart_app=false
cleanup() {
    result=$1
    trap - 0
    set +e
    # Возвращаем исходное состояние: если приложение работало, запускаем его и при
    # ошибке psql. SQL-обёртка откатывает всю незавершённую транзакцию при разрыве.
    if [ "$restart_app" = true ]; then
        if ! dc start app; then
            echo 'Не удалось запустить app. Выполните docker compose start app вручную.' >&2
            result=1
        fi
    fi
    # Удаляем только собственный каталог mktemp с проверенным префиксом.
    case "$remote" in
        /tmp/damulab-english-v3.????????) dc exec -T postgres rm -rf -- "$remote" ;;
    esac
    exit "$result"
}
trap 'cleanup $?' 0
trap 'exit 130' INT
trap 'exit 143' TERM

dc config --quiet
remote=$(dc exec -T postgres mktemp -d /tmp/damulab-english-v3.XXXXXXXX)
case "$remote" in
    /tmp/damulab-english-v3.????????) ;;
    *) echo 'Не удалось создать временный каталог в PostgreSQL-контейнере.' >&2; exit 1 ;;
esac
dc cp "$package_dir/." "postgres:$remote/"
dc exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "$1"' sh "$remote/00_preview.sql"
if [ "$preview" = true ]; then exit 0; fi

echo "Поставка: $mode. Старые связанные комнаты, ответы и результаты будут удалены."
running_app=$(dc ps --status running -q app)
if [ -n "$running_app" ]; then
    # Флаг выставляется до stop, чтобы попытаться вернуть приложение даже при
    # частично успешной остановке или сигнале во время команды.
    restart_app=true
    dc stop app
fi

# Копия снимается после остановки app, поэтому не теряет изменения в коротком
# промежутке между резервированием и заменой. На хосте остаётся полный dump БД.
mkdir -p "$backup_dir"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_path="$backup_dir/english-before-v3-$stamp-${remote##*.}.dump"
dc exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file="$1"' sh "$remote/before.dump"
dc exec -T postgres pg_restore --list "$remote/before.dump" >/dev/null
dc cp "postgres:$remote/before.dump" "$backup_path"
# Docker cp переносит права файла из контейнера, поэтому одного umask недостаточно.
chmod 600 "$backup_path"
echo "Резервная копия: $backup_path"

dc exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "$1"' sh "$remote/$mode"
echo 'Замена завершена: 500 заданий, 350 слов / 150 выражений. PostgreSQL подтвердил COMMIT.'
