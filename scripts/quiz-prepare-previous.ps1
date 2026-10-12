<#
Собирает конкретный предыдущий артефакт из git archive в новом ignored каталоге.
Не переключает checkout и не меняет рабочие файлы; использует только Gradle wrapper.
#>
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$previousCommit = 'cb97aac1f652d3b082771be6876d6fd2e5639f6b'
$outputDir = Join-Path $projectDir ('.run-logs/quiz-previous-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $outputDir | Out-Null
$archivePath = Join-Path $outputDir 'source.zip'
& git -C $projectDir archive --format=zip "--output=$archivePath" $previousCommit
if ($LASTEXITCODE -ne 0) { throw 'git archive завершился ошибкой' }
$sourceDir = Join-Path $outputDir 'source'
Expand-Archive -LiteralPath $archivePath -DestinationPath $sourceDir
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.3\jbr'
& (Join-Path $sourceDir 'gradlew.bat') -p $sourceDir bootJar --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx256m -Xms64m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=64m' --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Сборка предыдущего bootJar завершилась ошибкой' }
$artifactPath = Join-Path $sourceDir 'build/libs/damulab-0.0.1-SNAPSHOT.jar'
$sha256 = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
@{commit=$previousCommit; artifact=$artifactPath; sha256=$sha256} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir 'artifact.json') -Encoding utf8
Write-Output "QUIZ_PREVIOUS_JAR=$artifactPath"
Write-Output "SHA256=$sha256"
