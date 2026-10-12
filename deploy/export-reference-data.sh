#!/usr/bin/env bash
set -euo pipefail

# Формирует безопасный слепок учебного справочника PROD с сохранением первичных
# ключей. Пользовательские и операционные таблицы намеренно не включаются:
# файл можно использовать локально для подготовки импортов с настоящими ID.

APP_DIR="${DAMULAB_APP_DIR:-/opt/damulab}"
EXPORT_DIR="${DAMULAB_REFERENCE_EXPORT_DIR:-/var/backups/damulab}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_PATH="${1:-$EXPORT_DIR/reference-data-$STAMP.dump}"
TEMP_PATH="$OUTPUT_PATH.tmp.$$"

mkdir -p "$(dirname "$OUTPUT_PATH")"
cd "$APP_DIR"
trap 'rm -f "$TEMP_PATH"' EXIT

docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --format=custom \
    --data-only \
    --no-owner \
    --no-privileges \
    --table=public.subjects \
    --table=public.grades \
    --table=public.topics \
    --table=public.atomic_skills \
    --table=public.topic_ai_examples' \
  > "$TEMP_PATH"

chmod 600 "$TEMP_PATH"
mv "$TEMP_PATH" "$OUTPUT_PATH"
trap - EXIT

echo "Reference data export created: $OUTPUT_PATH"
sha256sum "$OUTPUT_PATH"
