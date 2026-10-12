[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DumpPath,

    [string]$ComposeFile = "docker-compose.yml",

    [string]$EnvFile,

    [string]$BackupDirectory = "backups/local-db",

    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Синхронизация сознательно удаляет зависимый локальный контент. Это позволяет
# не смешивать PROD ID справочника с вопросами и лекциями, созданными по старому
# дереву тем. Полный локальный backup создаётся до первого изменения БД.
if (-not $Force) {
    throw "Операция заменит локальный учебный справочник и очистит зависимые данные. Повторите с -Force."
}

$dumpFullPath = [System.IO.Path]::GetFullPath($DumpPath)
$composeFullPath = [System.IO.Path]::GetFullPath($ComposeFile)
$backupFullDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)

if (-not (Test-Path -LiteralPath $dumpFullPath -PathType Leaf)) {
    throw "Файл слепка не найден: $dumpFullPath"
}

if (-not (Test-Path -LiteralPath $composeFullPath -PathType Leaf)) {
    throw "Compose-файл не найден: $composeFullPath"
}

$composeArguments = @("compose")
if ($EnvFile) {
    $envFullPath = [System.IO.Path]::GetFullPath($EnvFile)
    if (-not (Test-Path -LiteralPath $envFullPath -PathType Leaf)) {
        throw "Env-файл не найден: $envFullPath"
    }
    $composeArguments += @("--env-file", $envFullPath)
}
$composeArguments += @("-f", $composeFullPath)

function Invoke-Docker {
    param([string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker завершился с кодом ${LASTEXITCODE}: docker $($Arguments -join ' ')"
    }
}

Invoke-Docker ($composeArguments + @("up", "-d", "--wait", "postgres"))

$containerId = @(& docker @composeArguments ps -q postgres | Select-Object -First 1) -join ""
$containerId = $containerId.Trim()
if ($LASTEXITCODE -ne 0 -or -not $containerId) {
    throw "Не удалось определить контейнер PostgreSQL."
}

$appContainerId = @(& docker @composeArguments ps --status running -q app | Select-Object -First 1) -join ""
$appContainerId = $appContainerId.Trim()
$wasAppRunning = [bool]$appContainerId
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$operationId = [Guid]::NewGuid().ToString("N")
$remoteDump = "/tmp/damulab-reference-$operationId.dump"
$remoteBackup = "/tmp/damulab-local-before-reference-$operationId.dump"
$databaseChanged = $false
$restoreSucceeded = $false

New-Item -ItemType Directory -Path $backupFullDirectory -Force | Out-Null
$localBackup = Join-Path $backupFullDirectory "database-before-reference-$stamp.dump"

try {
    # Бинарные dump-файлы передаются через docker cp, чтобы Windows PowerShell не
    # изменил байты при перенаправлении stdout.
    Invoke-Docker (@("cp", $dumpFullPath, "${containerId}:$remoteDump"))

    $validateArchive = @'
set -eu
archive="$1"
for table in subjects grades topics atomic_skills topic_ai_examples; do
  pg_restore --list "$archive" | grep -q "TABLE DATA public $table " || {
    echo "Archive does not contain TABLE DATA for public.$table" >&2
    exit 1
  }
done
'@
    Invoke-Docker ($composeArguments + @("exec", "-T", "postgres", "sh", "-c", $validateArchive, "sh", $remoteDump))

    if ($wasAppRunning) {
        Invoke-Docker ($composeArguments + @("stop", "app"))
    }

    $createBackup = 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --file="$1"'
    Invoke-Docker ($composeArguments + @("exec", "-T", "postgres", "sh", "-c", $createBackup, "sh", $remoteBackup))
    Invoke-Docker (@("cp", "${containerId}:$remoteBackup", $localBackup))
    Write-Host "Локальный backup создан: $localBackup"

    $truncateSql = @'
TRUNCATE TABLE
    topic_ai_examples,
    atomic_skills,
    topics,
    subjects,
    grades
RESTART IDENTITY CASCADE;
'@
    $runSql = 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"'
    $databaseChanged = $true
    Invoke-Docker ($composeArguments + @("exec", "-T", "postgres", "sh", "-c", $runSql, "sh", $truncateSql))

    $restoreArchive = 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --data-only --disable-triggers --exit-on-error "$1"'
    Invoke-Docker ($composeArguments + @("exec", "-T", "postgres", "sh", "-c", $restoreArchive, "sh", $remoteDump))

    # pg_dump с явным списком таблиц не обязан переносить состояние identity
    # sequences. После загрузки выставляем их по максимальному PROD ID, иначе
    # следующий локальный INSERT может столкнуться с уже существующим ключом.
    $sequenceSql = @'
SELECT setval(pg_get_serial_sequence('subjects', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM subjects;
SELECT setval(pg_get_serial_sequence('grades', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM grades;
SELECT setval(pg_get_serial_sequence('topics', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM topics;
SELECT setval(pg_get_serial_sequence('atomic_skills', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM atomic_skills;
SELECT setval(pg_get_serial_sequence('topic_ai_examples', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM topic_ai_examples;

SELECT
    (SELECT COUNT(*) FROM subjects) AS subjects,
    (SELECT COUNT(*) FROM grades) AS grades,
    (SELECT COUNT(*) FROM topics) AS topics,
    (SELECT COUNT(*) FROM atomic_skills) AS atomic_skills,
    (SELECT COUNT(*) FROM topic_ai_examples) AS topic_ai_examples;
'@
    Invoke-Docker ($composeArguments + @("exec", "-T", "postgres", "sh", "-c", $runSql, "sh", $sequenceSql))

    Write-Host "Справочник PROD восстановлен с исходными идентификаторами."
    $restoreSucceeded = $true
}
finally {
    & docker @composeArguments exec -T postgres rm -f $remoteDump $remoteBackup 2>$null

    if ($wasAppRunning -and ($restoreSucceeded -or -not $databaseChanged)) {
        & docker @composeArguments start app
    } elseif ($wasAppRunning -and $databaseChanged) {
        Write-Warning "Восстановление не завершено; приложение оставлено остановленным. Используйте backup: $localBackup"
    }
}
