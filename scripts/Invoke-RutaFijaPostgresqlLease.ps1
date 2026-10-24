[CmdletBinding()]
param(
 [ValidateSet('Plan','Status','Start','StopOwned','StopGlobal')][string]$Action='Plan',
 [switch]$Execute,
 [switch]$AuthorizeGlobalServiceStop,
 [string]$ConfigPath,
 [switch]$FunctionsOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$leaseFile=Join-Path $repoRoot '.runtime/ruta-fija-postgresql-lease.json'
$serviceName='postgresql-x64-16'
function Get-PostgresqlFacts {
 $service=Get-CimInstance Win32_Service -Filter "Name='postgresql-x64-16'" -ErrorAction Stop
 if($null-eq$service){throw 'EXPECTED_POSTGRESQL16_SERVICE_MISSING'}
 $pidValue=[int]$service.ProcessId
 $creation=$null
 if($pidValue-gt0){
  try{$process=Get-Process -Id $pidValue -ErrorAction Stop;if($null-ne$process.StartTime){$creation=$process.StartTime.ToUniversalTime().ToString('o')}}
  catch{$creation=$null}
 }
 [pscustomobject]@{name=$service.Name;state=$service.State;processId=$pidValue;processStartUtc=$creation;executable=$service.PathName}
}
function Read-PostgresqlLease {
 if(-not(Test-Path -LiteralPath $leaseFile)){return $null}
 $lease=Get-Content -LiteralPath $leaseFile -Raw -Encoding UTF8|ConvertFrom-Json
 if($lease.schemaVersion-ne1-or$lease.projectRoot-ne$repoRoot-or$lease.serviceName-ne$serviceName-or$lease.priorState-ne'Stopped'){throw 'POSTGRESQL_LEASE_INVALID_NO_STOP'}
 return $lease
}
function Save-PostgresqlLease($Lease){
 New-Item -ItemType Directory -Path (Split-Path -Parent $leaseFile) -Force|Out-Null
 if(Test-Path -LiteralPath $leaseFile){throw 'EXISTING_POSTGRESQL_LEASE_PRESERVE'}
 [IO.File]::WriteAllText($leaseFile,($Lease|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
function Remove-PostgresqlLease {if(Test-Path -LiteralPath $leaseFile){Remove-Item -LiteralPath $leaseFile}}
function Invoke-PostgresqlServiceAction([string]$RequestedAction){
 & (Join-Path $PSHOME 'powershell.exe') -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Set-RutaFijaPostgresqlService.ps1') -Action $RequestedAction
 if($LASTEXITCODE-ne0){throw 'POSTGRESQL_SERVICE_ACTION_FAILED'}
}
function Count-ConservativePostgresqlClients($Rows) {
 $clients=0
 foreach($row in $Rows){
  if($row.hasDatabase-or$row.hasUser-or$row.hasClientEndpoint){$clients++;continue}
  if($row.backendType-notin@('autovacuum launcher','logical replication launcher','checkpointer','background writer','walwriter','archiver','startup')){throw 'CLIENT_OBSERVABILITY_INCOMPLETE_PRESERVE_POSTGRESQL'}
 }
 return $clients
}
function Get-OtherPostgresqlClients {
 $path=if($ConfigPath){$ConfigPath}else{Join-Path $repoRoot 'backend/.local/ruta-fija-native.env'}
 & (Join-Path $PSScriptRoot 'Test-RutaFijaLocalSecretPolicy.ps1') -Action Check -Execute -ConfigPath $path|Out-Null
 $settings=& (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -ConfigPath $path -Scope Runtime -PassThru
 if($settings['SPRING_DATASOURCE_URL']-ne'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'-or$settings['SPRING_FLYWAY_USERNAME']-ne'rf_migrator'){throw 'LOCAL_CLIENT_COUNT_CONFIG_REFUSED'}
 $oldPassword=$env:PGPASSWORD;$oldOptions=$env:PGOPTIONS
 try{
  $env:PGPASSWORD=$settings['SPRING_FLYWAY_PASSWORD'];$env:PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=5000'
  $rows=@(& 'C:/Program Files/PostgreSQL/16/bin/psql.exe' -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d solucion_ruta_fija_1 -At -c "SELECT (datid IS NOT NULL)::int||'|'||(usesysid IS NOT NULL)::int||'|'||(client_addr IS NOT NULL OR client_port IS NOT NULL)::int||'|'||coalesce(backend_type,'') FROM pg_stat_activity WHERE pid<>pg_backend_pid();" 2>&1)
  if($LASTEXITCODE-ne0){throw 'CLIENT_COUNT_UNAVAILABLE_PRESERVE_POSTGRESQL'}
  $observations=@()
  foreach($line in $rows){
   $parts=([string]$line).Split('|')
   if($parts.Count-ne4-or$parts[0]-notin@('0','1')-or$parts[1]-notin@('0','1')-or$parts[2]-notin@('0','1')){throw 'CLIENT_COUNT_UNAVAILABLE_PRESERVE_POSTGRESQL'}
   $observations+=[pscustomobject]@{hasDatabase=$parts[0]-eq'1';hasUser=$parts[1]-eq'1';hasClientEndpoint=$parts[2]-eq'1';backendType=$parts[3]}
  }
  return Count-ConservativePostgresqlClients $observations
 }finally{$env:PGPASSWORD=$oldPassword;$env:PGOPTIONS=$oldOptions}
}
function Assert-PostgresqlLeaseIdentity($Lease,$Facts){
 if([string]::IsNullOrWhiteSpace([string]$Facts.processStartUtc)-or[string]::IsNullOrWhiteSpace([string]$Lease.processStartUtc)){throw 'POSTGRESQL_INSTANCE_START_NOT_OBSERVABLE_NO_STOP'}
 if($Facts.name-ne$Lease.serviceName-or$Facts.processId-ne$Lease.processId-or$Facts.processStartUtc-ne$Lease.processStartUtc-or$Facts.executable-ne$Lease.executable){throw 'POSTGRESQL_LEASE_IDENTITY_CHANGED_NO_STOP'}
}
function Start-OwnedPostgresql {
 $facts=Get-PostgresqlFacts;$lease=Read-PostgresqlLease
 if($facts.state-eq'Running'){
  if($null-ne$lease){Assert-PostgresqlLeaseIdentity $lease $facts;Write-Output 'POSTGRESQL_START=ALREADY_RUNNING_EXISTING_OWN_LEASE'}
  else{Write-Output 'POSTGRESQL_START=ALREADY_RUNNING_NOT_OWNED_WILL_NOT_AUTO_STOP'}
  return
 }
 if($null-ne$lease){throw 'STALE_POSTGRESQL_LEASE_REVIEW_REQUIRED_NO_REWRITE'}
 if($facts.state-ne'Stopped'){throw 'POSTGRESQL_TRANSITION_IN_PROGRESS_NO_ACTION'}
 Invoke-PostgresqlServiceAction 'Start';$started=Get-PostgresqlFacts
 if($started.state-ne'Running'-or$started.processId-le0-or[string]::IsNullOrWhiteSpace([string]$started.processStartUtc)){throw 'POSTGRESQL_START_IDENTITY_NOT_READY_NO_LEASE_NO_AUTO_STOP'}
 Save-PostgresqlLease ([ordered]@{schemaVersion=1;projectRoot=$repoRoot;serviceName=$serviceName;priorState='Stopped';acquiredAtUtc=[DateTimeOffset]::UtcNow.ToString('o');processId=$started.processId;processStartUtc=$started.processStartUtc;executable=$started.executable})
 Write-Output 'POSTGRESQL_START=STARTED_AND_OWNED_THIS_LAUNCHER'
}
function Stop-OwnedPostgresql {
 $lease=Read-PostgresqlLease
 if($null-eq$lease){Write-Output 'POSTGRESQL_STOP=PRESERVED_NO_OWNERSHIP_LEASE';return}
 $facts=Get-PostgresqlFacts
 if($facts.state-eq'Stopped'){Remove-PostgresqlLease;Write-Output 'POSTGRESQL_STOP=ALREADY_STOPPED_OWN_LEASE_RELEASED';return}
 if($facts.state-ne'Running'){throw 'POSTGRESQL_TRANSITION_IN_PROGRESS_NO_STOP'}
 Assert-PostgresqlLeaseIdentity $lease $facts
 foreach($folder in @((Join-Path $repoRoot '.local'),(Join-Path $repoRoot 'backend/.local'))){if((Test-Path -LiteralPath $folder)-and@(Get-ChildItem -LiteralPath $folder -File -Filter '*.active').Count){throw 'PROTECTED_CASE_ACTIVE_NO_SERVICE_STOP'}}
 $clients=Get-OtherPostgresqlClients
 if($clients-gt0){Write-Output 'POSTGRESQL_STOP=PRESERVED_OTHER_CLIENTS_PRESENT';return}
 # A second identity check narrows races. Client inspection is a point-in-time
 # check, not an exclusive reservation of the shared PostgreSQL service.
 Assert-PostgresqlLeaseIdentity $lease (Get-PostgresqlFacts)
 Invoke-PostgresqlServiceAction 'Stop'
 if((Get-PostgresqlFacts).state-ne'Stopped'){throw 'POSTGRESQL_SERVICE_NOT_STOPPED_KEEP_LEASE'}
 Remove-PostgresqlLease
 Write-Output 'POSTGRESQL_STOP=STOPPED_OWNED_NO_OTHER_CLIENTS_OBSERVED'
}
if($FunctionsOnly){return}
if($Action-eq'Plan'){Write-Output 'POSTGRESQL_LEASE=PLAN_NO_SERVICE_ACCESS Start=ACQUIRE_ONLY_IF_STOPPED StopOwned=IDENTITY_AND_CLIENTS StopGlobal=EXPLICIT_SEPARATE';return}
if($Action-eq'Status'){$facts=Get-PostgresqlFacts;[ordered]@{service=$facts.name;state=$facts.state;leasePresent=Test-Path -LiteralPath $leaseFile}|ConvertTo-Json;return}
if(-not$Execute){throw 'EXPLICIT_EXECUTE_REQUIRED'}
switch($Action){
 'Start'{Start-OwnedPostgresql}
 'StopOwned'{Stop-OwnedPostgresql}
 'StopGlobal'{if(-not$AuthorizeGlobalServiceStop){throw 'EXPLICIT_GLOBAL_STOP_AUTHORIZATION_REQUIRED'};Invoke-PostgresqlServiceAction 'Stop';Remove-PostgresqlLease;Write-Output 'POSTGRESQL_STOP=EXPLICIT_GLOBAL_SERVICE_STOP'}
}
