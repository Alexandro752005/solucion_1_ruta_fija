[CmdletBinding()]
param([switch]$ExecuteLocale,[switch]$AuthorizeL08Rest02)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not $ExecuteLocale){Write-Output 'L08_REST02=PLAN_ONLY_NO_CONNECTION_NO_FILES';Write-Output 'DESTINATION=ruta_fija_test_l08_locale@127.0.0.1:55438';Write-Output 'LOCALE=Spanish_Peru.1252_UTF8';Write-Output 'PRESERVE=EXISTING_RESTORE_SHARED_TEST_AND_COUNTERS';return}
if(-not $AuthorizeL08Rest02){throw 'L08-REST-02 requiere autorización expresa del nuevo destino.'}
. (Join-Path $PSScriptRoot 'Invoke-RutaFijaL08DatabaseRehearsal.ps1') -FunctionsOnly
$localeManifest=Join-Path $privateRoot 'l08-locale-isolated-restore.json'
$credentials=$null
try{
 Add-Type -AssemblyName System.Security
 Assert-NoProtectedCase; Assert-NoPendingOwnedNative
 foreach($path in @($privateRoot,$dataRoot,$localeManifest)){Assert-PhysicalAncestors $path}
 Enter-RehearsalLock
 if(Test-Path -LiteralPath $localeManifest){throw 'SAME_CASE_ALREADY_PREPARED_PRESERVE_AND_DIAGNOSE'}
 $phase='VERIFY_OWNED_INSTANCE_AND_SAVED_CREDENTIALS'
 $credentials=Read-IsolatedCredentials
 if((Get-IsolatedClusterState).state -ne 'RUNNING_SAME_CLUSTER'){throw 'EXISTING_OWNED_CLUSTER_MUST_BE_RUNNING'}
 Assert-IsolatedServerIdentity
 $existing=@(Invoke-IsolatedOwnerQuery "select count(*) from pg_database where datname='ruta_fija_test_l08_locale';")
 if($existing.Count -ne 1 -or $existing[0] -ne '0'){throw 'NEW_LOCALE_DATABASE_MUST_NOT_EXIST_NO_OVERWRITE'}
 $manifest=Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8|ConvertFrom-Json
 if($manifest.caseId -ne $caseId -or $manifest.database -ne 'ruta_fija_test' -or $manifest.archiveSha256 -ine (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash){throw 'ORIGINAL_ARCHIVE_CASE_MISMATCH'}
 $manifest.database='ruta_fija_test_l08_locale';$manifest.purpose='L08_LOCALE_MATCHED_SYNTHETIC_TEST_RESTORE_ONLY'
 $phase='CREATE_ONLY_AUTHORIZED_NEW_LOCALE_DATABASE'
 $ignored=@(Invoke-IsolatedOwnerQuery "CREATE DATABASE ruta_fija_test_l08_locale OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'Spanish_Peru.1252' LC_CTYPE 'Spanish_Peru.1252' LOCALE_PROVIDER libc;")
 Write-Json $localeManifest $manifest
 $ignored=@(Invoke-IsolatedOwnerQuery 'GRANT pg_read_all_settings TO rf_migrator;')
 $keyBytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
 Set-ProcessSetting 'RF_L08_BACKUP_KEY_BASE64' ([Convert]::ToBase64String($keyBytes))
 Set-ProcessSetting 'RF_L08_RESTORE_AUTHORIZATION' 'NEW_LOCALE_MATCHED_DATABASE_55438'
 Set-ProcessSetting 'RF_L08_RESTORE_URL' 'jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale'
 Set-ProcessSetting 'RF_L08_RESTORE_PASSWORD' $credentials['rf_migrator']
 $dependencies=[IO.File]::ReadAllText((Join-Path $repoRoot 'backend/target/manual-otp-classpath.txt')).Trim()
 $javaClasspath=(Join-Path $repoRoot 'backend/target/classes')+';'+$dependencies
 $phase='RESTORE_SAME_AUTHENTICATED_ARCHIVE_IN_NEW_LOCALE_DATABASE'
 $output=@(Invoke-CapturedNative 'java' @('-Xms32m','-Xmx384m','-cp',$javaClasspath,'pe.rutafija.backup.IsolatedTestRestoreCli',(Join-Path $pgBin 'pg_restore.exe'),$archivePath,$localeManifest) $null 1900)
 $result=($output -join [Environment]::NewLine)|ConvertFrom-Json
 Write-Json (Join-Path $privateRoot 'restore-locale-result.json') $result
 if($result.result -ne 'PASS_ISOLATED_SQL_RESTORE_ONLY_INTEGRITY_AND_APP_CHECKS_STILL_REQUIRED'){throw 'KEEP_CASE_RESTORE_NOT_ACCREDITED'}
 Write-Output ('L08_REST02_RESULT='+$result.result)
 Write-Output ('L08_REST02_TABLES='+$result.tablesCompared)
}catch{
 Write-Output 'L08_REST02_RESULT=STOPPED_KEEP_BOTH_DATABASES_AND_CASE'
 Write-Output ('L08_REST02_PHASE='+$phase)
 Write-Output ('L08_REST02_NATIVE_PHASE='+$nativeFailurePhase)
 Write-Output ('L08_REST02_SQLSTATE='+$nativeSqlState)
 exit 1
}finally{
 try{if($null -ne $credentials){$ignored=@(Invoke-IsolatedOwnerQuery 'REVOKE pg_read_all_settings FROM rf_migrator;')}}catch{Write-Output 'L08_REST02_TEMPORARY_PERMISSION_REVOCATION=PENDING_CHECK'}
 if($null -ne $keyBytes){[Array]::Clear($keyBytes,0,$keyBytes.Length)}
 foreach($name in $savedEnvironment.Keys){[Environment]::SetEnvironmentVariable($name,$savedEnvironment[$name],'Process')}
 if($null -ne $launcherLock){$launcherLock.Dispose()}
}
