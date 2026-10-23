[CmdletBinding()]
param(
 [ValidateSet('Plan','Preflight','Verify','Clean')][string]$Action='Plan',
 [string]$ConfigPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
if($Action -eq 'Plan'){
 Write-Output 'NATIVE_TEST=PLAN Verify=ISOLATED_UNIT_ALLOWLIST Preflight=SELECT_ONLY Clean=FORBIDDEN_NO_GLOBAL_CLEANUP'
 return
}
if($Action -eq 'Clean'){throw 'GLOBAL_CLEANUP_FORBIDDEN_PROTECTED_RUTA_FIJA_TEST; use a separately authorized isolated harness'}
if($Action -eq 'Verify'){
 $safe=Join-Path $PSScriptRoot 'Test-RutaFijaBackendUnitSafe.ps1'
 if(-not(Test-Path -LiteralPath $safe -PathType Leaf)){throw 'SAFE_UNIT_RUNNER_REQUIRED_NO_FALLBACK_TO_LEGACY'}
 & (Join-Path $PSHOME 'powershell.exe') -NoLogo -NoProfile -ExecutionPolicy Bypass -File $safe -Execute
 exit $LASTEXITCODE
}
if(-not$ConfigPath){$ConfigPath=Join-Path $repoRoot 'backend/.local/ruta-fija-native.env'}
& (Join-Path $PSScriptRoot 'Test-RutaFijaLocalSecretPolicy.ps1') -Action Check -Execute -ConfigPath $ConfigPath
$settings=& (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -ConfigPath $ConfigPath -PassThru -Scope Test
$oldPassword=$env:PGPASSWORD;$oldOptions=$env:PGOPTIONS
try{
 $env:PGPASSWORD=$settings['SPRING_FLYWAY_PASSWORD'];$env:PGOPTIONS='-c default_transaction_read_only=on'
 $psql='C:/Program Files/PostgreSQL/16/bin/psql.exe'
 $rows=@(& $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d ruta_fija_test -At -c "SELECT current_database()||'|'||current_user||'|'||current_setting('server_version_num');" 2>&1)
 if($LASTEXITCODE -ne 0 -or @($rows|Where-Object{$_ -match '^ruta_fija_test\|rf_migrator\|16'}).Count -ne 1){throw 'READ_ONLY_TEST_DATABASE_IDENTITY_FAILED'}
 Write-Output 'NATIVE_PREFLIGHT=PASS_SELECT_ONLY database=ruta_fija_test postgresql=16 NO_TESTS_NO_CLEANUP'
}finally{$env:PGPASSWORD=$oldPassword;$env:PGOPTIONS=$oldOptions}
