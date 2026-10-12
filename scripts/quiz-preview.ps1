<#
Подготавливает и запускает изолированный локальный стенд на проверенном bootJar.
Использует только quiz_test в контейнере с точным именем/label; данные сохраняются.
Глобальный флаг каталога, рабочая БД и другие контейнеры не меняются.
#>
param(
    [ValidateSet('Prepare','Start','Stop','Status','AddAnimals')][string]$Action='Start',
    [ValidateRange(1024,65535)][int]$Port=8088,
    [switch]$Legacy
)
$ErrorActionPreference='Stop'
$projectDir=Split-Path -Parent $PSScriptRoot
$previewDir=Join-Path $projectDir '.run-logs/quiz-preview'
$statePath=Join-Path $previewDir 'state.json'
$javaHome='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.3\jbr'
$java=Join-Path $javaHome 'bin/java.exe'
$container='dlab-quiz-stage1-pg'
$savedEnv=@{}
foreach($key in @('DB_URL','DB_USERNAME','DB_PASSWORD','QUIZ_PREVIEW_PASSWORD','QUIZ_PREVIEW_SCHEMA','QUIZ_PREVIEW_READY','QUIZ_PREVIEW_ANIMALS_ONLY')) {
    $savedEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
}

# Перед каждым start/stop проверяется именно разрешённый контейнер, без Compose и удаления.
function Assert-QuizContainer {
    $identity=& docker inspect --format '{{.Name}}|{{index .Config.Labels "codex.task"}}' $container
    if($LASTEXITCODE -ne 0 -or $identity -ne '/dlab-quiz-stage1-pg|quiz-games-stage1') { throw 'Не совпали имя/label изолированного PostgreSQL' }
}
function Start-QuizDatabase {
    Assert-QuizContainer
    & docker start $container | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Не удалось запустить изолированный PostgreSQL' }
    $mapping=@(& docker port $container 5432/tcp | Where-Object { $_ -match '^127\.0\.0\.1:[0-9]+$' })
    if($mapping.Count -ne 1) { throw 'Не найден однозначный loopback-порт PostgreSQL' }
    for($attempt=0;$attempt -lt 30;$attempt++) {
        & docker exec $container pg_isready -U quiz_test -d quiz_test *> $null
        if($LASTEXITCODE -eq 0) { return [int]($mapping[0] -split ':')[-1] }
        Start-Sleep -Milliseconds 500
    }
    throw 'PostgreSQL не готов'
}
function Get-PreviewProcess($state) {
    if(-not $state.processId) { return $null }
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($state.processId)"
    if($process -and ($process.Name -ne 'java.exe' -or -not $process.CommandLine.Contains($state.artifact))) {
        throw 'PID принадлежит другому процессу; остановка запрещена'
    }
    return $process
}
if($Action -eq 'Status') {
    if(-not (Test-Path -LiteralPath $statePath)) { Write-Output 'Стенд ещё не подготовлен'; exit 0 }
    $state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Write-Output "URL: http://localhost:$($state.port)/student/quiz"
    Write-Output "Java запущена: $([bool](Get-PreviewProcess $state))"
    Write-Output "Локальные учётные данные: $statePath"
    exit 0
}
if($Action -eq 'Stop') {
    if(Test-Path -LiteralPath $statePath) {
        $state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if(Get-PreviewProcess $state) { Stop-Process -Id $state.processId }
    }
    Assert-QuizContainer
    & docker stop $container | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Не удалось остановить изолированный PostgreSQL' }
    Write-Output 'Локальный стенд остановлен; схема и история сохранены'
    exit 0
}
New-Item -ItemType Directory -Path $previewDir -Force | Out-Null
$databasePort=Start-QuizDatabase
if($Action -eq 'Prepare' -and (Test-Path -LiteralPath $statePath)) { throw 'Стенд уже подготовлен; используйте Start. Существующие данные не перезаписываются' }
if($Action -eq 'AddAnimals' -and -not (Test-Path -LiteralPath $statePath)) { throw 'Для нового стенда используйте Prepare или Start: обе игры уже входят в загрузку' }
if(-not (Test-Path -LiteralPath $statePath) -or $Action -eq 'AddAnimals') {
    $previousState=$null
    if($Action -eq 'AddAnimals') {
        $previousState=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if(Get-PreviewProcess $previousState) { throw 'Перед добавлением игр остановите стенд через Stop' }
    }
    $builtJar=Join-Path $projectDir 'build/libs/damulab-0.0.1-SNAPSHOT.jar'
    if(-not (Test-Path -LiteralPath $builtJar)) { throw 'Сначала соберите bootJar через Gradle wrapper' }
    $hash=(Get-FileHash -LiteralPath $builtJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $artifact=Join-Path $previewDir ('damulab-'+$hash.Substring(0,12)+'.jar')
    if(-not (Test-Path -LiteralPath $artifact)) { Copy-Item -LiteralPath $builtJar -Destination $artifact }
    if((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) { throw 'Не совпал hash ранее закреплённого файла' }
    $runtime=Join-Path $previewDir ('runtime-'+$hash.Substring(0,12))
    if(-not (Test-Path -LiteralPath $runtime)) { [System.IO.Compression.ZipFile]::ExtractToDirectory($artifact,$runtime) }
    if($previousState) {
        # Меняется только закреплённый артефакт после успешной транзакционной публикации; схема не пересоздаётся.
        $state=$previousState; $schema=$state.schema; $password=$state.password
        $state.artifact=$artifact; $state.sha256=$hash; $state.processId=0
    } else {
        $schema='quiz_preview_'+[guid]::NewGuid().ToString('N')
        & docker exec $container psql -U quiz_test -d quiz_test -v ON_ERROR_STOP=1 -c "create schema $schema" | Out-Null
        if($LASTEXITCODE -ne 0) { throw 'Не удалось создать собственную схему стенда' }
        $password='Quiz-'+[guid]::NewGuid().ToString('N').Substring(0,16)
        $state=[pscustomobject]@{schema=$schema;artifact=$artifact;sha256=$hash;password=$password;host='quiz-host@local.test';guest='quiz-guest@local.test';admin='quiz-author@local.test';port=$Port;processId=0;catalogEnabled=$false}
    }
    $classDir=Join-Path $previewDir 'operator-classes'
    New-Item -ItemType Directory -Path $classDir -Force | Out-Null
    $classpath=(Join-Path $runtime 'BOOT-INF/classes')+';'+(Join-Path $runtime 'BOOT-INF/lib/*')
    & (Join-Path $javaHome 'bin/javac.exe') -encoding UTF-8 -cp $classpath -d $classDir (Join-Path $PSScriptRoot 'quiz-preview/QuizLocalPreview.java')
    if($LASTEXITCODE -ne 0) { throw 'Не удалось собрать операторский загрузчик' }
    $prepare=$true
} else {
    $state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if(Get-PreviewProcess $state) { throw 'Стенд уже запущен; сначала Stop' }
    $prepare=$false
}
if((Get-FileHash -LiteralPath $state.artifact -Algorithm SHA256).Hash.ToLowerInvariant() -ne $state.sha256) { throw 'Не совпал hash закреплённого bootJar' }
if($state.schema -notmatch '^quiz_preview_[a-f0-9]{32}$') { throw 'Недопустимая схема стенда' }
try {
    $env:DB_URL="jdbc:postgresql://127.0.0.1:$databasePort/quiz_test"
    $env:DB_USERNAME='quiz_test'; $env:DB_PASSWORD='quiz_test_local'
    $settings=@('--spring.profiles.active=local','--server.address=127.0.0.1',
        "--spring.datasource.hikari.schema=$($state.schema)","--spring.flyway.schemas=$($state.schema)","--spring.flyway.default-schema=$($state.schema)",
        '--damulab.demo-users.enabled=false','--damulab.quiz.strict-self-review=true','--damulab.ai.real-providers-enabled=false','--damulab.auth.google.enabled=false',
        '--damulab.email-verification.enabled=false','--damulab.push.vapid-public-key=','--damulab.push.vapid-private-key=')
    if($prepare) {
        $env:QUIZ_PREVIEW_PASSWORD=$password
        $env:QUIZ_PREVIEW_SCHEMA=$schema
        $env:QUIZ_PREVIEW_READY=Join-Path $previewDir 'ready.txt'
        $env:QUIZ_PREVIEW_ANIMALS_ONLY=if($previousState){'true'}else{'false'}
        & $java -Xmx256m -XX:ActiveProcessorCount=2 -cp "$classpath;$classDir" QuizLocalPreview @settings '--server.port=0' '--damulab.quiz.game-catalog-enabled=false' '--damulab.quiz.timeout-worker-enabled=false' *> (Join-Path $previewDir 'prepare.log')
        if($LASTEXITCODE -ne 0) { throw "Загрузка не завершилась: $previewDir\prepare.log. Схема сохранена" }
        $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
    }
    if($Action -eq 'Start') {
        if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw 'Порт стенда занят' }
        $state.port=$Port; $state.catalogEnabled=-not $Legacy
        $catalog=if($Legacy){'false'}else{'true'}
        $arguments=@('-Xmx256m','-XX:ActiveProcessorCount=2','-jar',('"'+$state.artifact+'"'))+$settings+@("--server.port=$Port","--damulab.quiz.game-catalog-enabled=$catalog")
        $process=Start-Process -FilePath $java -ArgumentList $arguments -WindowStyle Hidden -PassThru -WorkingDirectory $projectDir -RedirectStandardOutput (Join-Path $previewDir 'app.log') -RedirectStandardError (Join-Path $previewDir 'app-error.log')
        $state.processId=$process.Id
        $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
        Write-Output "Стенд запускается: http://localhost:$Port/student/quiz"
    }
    Write-Output "Учётные данные и hash: $statePath"
} finally {
    foreach($key in $savedEnv.Keys) {
        # Отсутствующая переменная должна отсутствовать: пустой JDBC URL подавляет fallback у Java/Gradle.
        if($null -eq $savedEnv[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
        else { Set-Item -LiteralPath "Env:$key" -Value $savedEnv[$key] }
    }
}
