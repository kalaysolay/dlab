param([string]$RunDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$run=(Resolve-Path -LiteralPath $RunDirectory).Path
$jar=Join-Path $run 'source/build/libs/damulab-0.0.1-SNAPSHOT.jar'
$base=[IO.Compression.ZipFile]::OpenRead((Join-Path $run 'evidence/production.jar'))
$candidate=[IO.Compression.ZipFile]::OpenRead($jar)
function HashEntry($entry) {
 $stream=$entry.Open(); try { $hash=[Security.Cryptography.SHA256]::Create(); try { return [Convert]::ToHexString($hash.ComputeHash($stream)).ToLowerInvariant() } finally {$hash.Dispose()} } finally {$stream.Dispose()}
}
try {
 $changes=@(); $missing=@(); $speaking=0
 foreach($entry in $base.Entries | Where-Object FullName -Like 'BOOT-INF/classes/*') {
  if($entry.FullName.EndsWith('/')){continue}
  $other=$candidate.GetEntry($entry.FullName)
  if(!$other){$missing+=$entry.FullName;continue}
  $same=(HashEntry $entry) -eq (HashEntry $other)
  if(!$same){$changes+=$entry.FullName}
  if($entry.FullName -like 'BOOT-INF/classes/kz/damulab/speaking/*') {if(!$same){throw "Изменён speaking: $($entry.FullName)"};$speaking++}
 }
 if($missing.Count){throw "Потеряны production-файлы: $missing"}
 foreach($name in @('V64__translation_request_history.sql','V65__grade_six_for_history_lessons.sql','V66__quiz_answer_drafts.sql')){if($candidate.GetEntry('BOOT-INF/classes/db/migration/'+$name)){throw "Посторонняя миграция: $name"}}
 if(!$candidate.GetEntry('BOOT-INF/classes/db/migration/V72__grade_six_for_lesson_studio.sql')){throw 'Нет V72'}
 if($candidate.GetEntry('BOOT-INF/classes/kz/damulab/translator/TranslationHistoryService.class')){throw 'Попал новый переводчик'}
 foreach($entry in $base.Entries | Where-Object FullName -Like 'BOOT-INF/classes/db/migration/*') {if(!$entry.FullName.EndsWith('/') -and (HashEntry $entry) -ne (HashEntry $candidate.GetEntry($entry.FullName))){throw "Изменена применённая миграция: $($entry.FullName)"}}
 $result=[ordered]@{jar=$jar;sha256=(Get-FileHash $jar).Hash.ToLowerInvariant();productionSha256=(Get-FileHash (Join-Path $run 'evidence/production.jar')).Hash.ToLowerInvariant();speakingClassesIdentical=$speaking;missingProductionResources=$missing;changedProductionResources=$changes;migration='Only new V72; all baseline migration bytes identical';translatorRuntime='unchanged';checkedAt=[DateTimeOffset]::UtcNow.ToString('o')}
 $result | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $run 'evidence/jar-verification.json') -Encoding utf8
 $result | ConvertTo-Json -Depth 8
}finally{$base.Dispose();$candidate.Dispose()}
