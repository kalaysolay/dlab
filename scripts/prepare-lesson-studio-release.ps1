param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$runsRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot '.run-logs'))
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $runsRoot ('lesson-studio-release-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $outputRoot.StartsWith($runsRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Каталог выпуска должен находиться внутри .run-logs репозитория.' }
if (Test-Path -LiteralPath $outputRoot) { throw 'Каталог уже существует. Выберите новый путь: готовый кандидат не перезаписывается.' }

# Базу берём из Git, затем накладываем только явно перечисленные файлы. Так чужие
# незавершённые изменения, секреты и содержимое рабочего build не входят в сборку.
$selection = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/LESSON_STUDIO_RELEASE.json') -Raw | ConvertFrom-Json
$sourceRoot = Join-Path $outputRoot 'source'
New-Item -ItemType Directory -Path $sourceRoot -Force | Out-Null
$baseArchive = Join-Path $outputRoot 'base.zip'
$baseRef = if ($selection.baseRevision) { $selection.baseRevision } else { 'HEAD' }
$baseRevision = (& git -C $repoRoot rev-parse "$baseRef^{commit}").Trim()
if ($LASTEXITCODE -ne 0) { throw 'Не удалось определить Git HEAD.' }
& git -C $repoRoot archive --format=zip "--output=$baseArchive" $baseRevision
if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать архив Git.' }
Expand-Archive -LiteralPath $baseArchive -DestinationPath $sourceRoot
$included = @()
foreach ($relative in $selection.files) {
    # Общие файлы с более свежими production-правками берём из проверенного
    # объединения, не подменяя ими незавершённый рабочий checkout.
    $override = if ($selection.sourceOverrides) { $selection.sourceOverrides.PSObject.Properties[$relative] } else { $null }
    $inputRelative = if ($override) { [string]$override.Value } else { $relative }
    $sourceFile = [IO.Path]::GetFullPath((Join-Path $repoRoot $inputRelative))
    $destination = [IO.Path]::GetFullPath((Join-Path $sourceRoot $relative))
    if (-not $sourceFile.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not $destination.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Недопустимый путь: $relative" }
    $entry = Get-Item -LiteralPath $sourceFile
    if ($entry.PSIsContainer -or ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Ожидался обычный файл: $relative" }
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    Copy-Item -LiteralPath $sourceFile -Destination $destination
    $included += [pscustomobject]@{ path=$relative; source=$inputRelative; sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$status = @(& git -C $repoRoot -c core.quotepath=false status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) { throw 'Не удалось прочитать состояние checkout.' }
$manifest = [ordered]@{
    kind=$selection.kind; createdAt=[DateTimeOffset]::UtcNow.ToString('o'); baseRevision=$baseRevision
    rendererVersion=$selection.rendererVersion; sourceDirectory=$sourceRoot
    baseArchiveSha256=(Get-FileHash -LiteralPath $baseArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    included=$included; excludedRuntimeChanges=$selection.excludedRuntimeChanges
    excludedWorkingChanges=@($status | Where-Object { $_.Length -gt 3 -and $_.Substring(3) -notin $selection.files })
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outputRoot 'manifest.json') -Encoding utf8
Write-Output "Кандидат подготовлен: $sourceRoot"
Write-Output "Следующий шаг: запустить .\gradlew.bat build из этого каталога. Git index, коммиты и прод не изменены."
