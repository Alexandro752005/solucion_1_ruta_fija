[CmdletBinding()]
param([switch]$Execute,[string]$EvidenceFile)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not$Execute){Write-Output 'DATABASE_FINGERPRINT=PLAN_READ_ONLY_NO_CONNECTION';return}
if(-not$EvidenceFile-or(Test-Path -LiteralPath $EvidenceFile)){throw 'CREATE_NEW_EVIDENCE_PATH_REQUIRED'}
$root=Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'Test-RutaFijaLocalSecretPolicy.ps1') -Action Check -Execute|Out-Null
$runtime=& (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -PassThru -Scope Runtime
$test=& (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -PassThru -Scope Test
$psql='C:/Program Files/PostgreSQL/16/bin/psql.exe'
$previousPassword=$env:PGPASSWORD;$previousOptions=$env:PGOPTIONS
$records=@();$started=[DateTimeOffset]::UtcNow
try{
 $env:PGOPTIONS='-c default_transaction_read_only=on -c timezone=UTC -c statement_timeout=15000'
 foreach($database in @('solucion_ruta_fija_1','ruta_fija_test')){
  $env:PGPASSWORD=if($database-eq'ruta_fija_test'){$test['SPRING_FLYWAY_PASSWORD']}else{$runtime['SPRING_FLYWAY_PASSWORD']}
  $arguments=@('-X','-w','-q','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','5432','-U','rf_migrator','-d',$database,'-At')
  $names=@(& $psql @arguments -c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;" 2>&1)
  if($LASTEXITCODE-ne0-or$names.Count-eq0){throw 'READONLY_TABLE_INVENTORY_FAILED'}
  $sql=[Collections.Generic.List[string]]::new();$sql.Add('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;')
  foreach($name in $names){
   if([string]$name-notmatch'^[a-z_][a-z_0-9]*$'){throw 'UNEXPECTED_TABLE_IDENTIFIER'}
   $rowHash="encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex')"
   $sql.Add("SELECT '$name'||'|'||count(*)||'|'||encode(sha256(convert_to(coalesce(string_agg($rowHash,'' ORDER BY $rowHash),''),'UTF8')),'hex') FROM public.$name t;")
  }
  $sql.Add('COMMIT;')
  $lines=@(($sql-join"`n")|& $psql @arguments 2>&1)
  if($LASTEXITCODE-ne0-or$lines.Count-ne$names.Count){throw 'READONLY_TABLE_FINGERPRINT_FAILED_NO_PARTIAL_CERTIFICATION'}
  $tables=@();foreach($line in $lines){$parts=([string]$line).Split('|');if($parts.Count-ne3-or$parts[2]-notmatch'^[a-f0-9]{64}$'){throw 'READONLY_HASH_FORMAT_FAILED'};$tables+=[ordered]@{table=$parts[0];rows=[long]$parts[1];contentSha256=$parts[2]}}
  $ledger=$null;$acceptances=$null
  if('pilot_account_creation'-in$names){$ledger=@(& $psql @arguments -c "SELECT role||'|'||count(*) FROM pilot_account_creation GROUP BY role ORDER BY role;" 2>&1);if($LASTEXITCODE-ne0){throw 'LEDGER_READ_FAILED'}}
  if('admin_terms_acceptance'-in$names){$acceptances=@(& $psql @arguments -c 'SELECT count(*) FROM admin_terms_acceptance;' 2>&1);if($LASTEXITCODE-ne0){throw 'ACCEPTANCE_READ_FAILED'}}
  $versions=@(& $psql @arguments -c "SELECT coalesce(version,'REPEATABLE')||'|'||type||'|'||success FROM flyway_schema_history ORDER BY installed_rank;" 2>&1)
  if($LASTEXITCODE-ne0){throw 'MIGRATION_HISTORY_READ_FAILED'}
  $records+=[ordered]@{database=$database;tableCount=$tables.Count;tables=$tables;pilotLedger=$ledger;adminTermsAcceptances=$acceptances;flyway=$versions;readOnlyRepeatableReadSnapshot=$true}
 }
}finally{$env:PGPASSWORD=$previousPassword;$env:PGOPTIONS=$previousOptions}
$proof=[ordered]@{startedAtUtc=$started.ToString('o');finishedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');result='PASS_TWO_DATABASE_READONLY_CONTENT_FINGERPRINTS';databases=$records;rowContentsExported=$false;dataChanged=$false;purpose='Before/after smoke preservation, table SHA256 from canonical JSONB row hashes ordered, UTC, read-only consistent snapshot per database'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidenceFile),($proof|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
Write-Output ('DATABASE_FINGERPRINT=PASS databases=2 tables='+(@($records|ForEach-Object{$_.tableCount}|Measure-Object -Sum).Sum))
