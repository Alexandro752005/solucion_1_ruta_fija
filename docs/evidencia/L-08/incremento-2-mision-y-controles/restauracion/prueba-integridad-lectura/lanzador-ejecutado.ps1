$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$privateRoot=Join-Path $root 'backend/.local/l08-restore-c748e5ab-c20a-43d6-9c17-af5d0a703dab'
$plain=$null;$key=$null
try {
 Add-Type -AssemblyName System.Security
 $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $privateRoot 'target-credentials.dpapi')),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
 $credential=[Text.Encoding]::UTF8.GetString($plain)|ConvertFrom-Json
 $key=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $privateRoot 'backup-key.dpapi')),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
 $env:RF_L08_RESTORE_AUTHORIZATION='NEW_LOCALE_MATCHED_DATABASE_55438'
 $env:RF_L08_RESTORE_URL='jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale'
 $env:RF_L08_RESTORE_PASSWORD=$credential.rf_migrator
 $env:RF_L08_BACKUP_KEY_BASE64=[Convert]::ToBase64String($key)
 $cp=(Join-Path $root 'backend/target/l08-restore-collation-readonly/classes')+';'+(Join-Path $root 'backend/target/test-classes')+';'+(Join-Path $root 'backend/target/classes')+';'+[IO.File]::ReadAllText((Join-Path $root 'backend/target/manual-otp-classpath.txt')).Trim()
 $ErrorActionPreference='Continue'
 & java -Xms32m -Xmx256m -cp $cp pe.rutafija.support.L08RestoredConstraintCollationProof 'C:\Program Files\PostgreSQL\16\bin\pg_restore.exe' (Join-Path $privateRoot 'ruta_fija_test.rfdb') (Join-Path $privateRoot 'l08-locale-isolated-restore.json') --inspect-existing-read-only > (Join-Path $root 'backend/target/l08-restored-snapshot-collation-readonly.log') 2>&1
 $status=$LASTEXITCODE
 Write-Output ('L08_RESTORE_INSPECT_EXIT='+$status)
 exit $status
} finally {
 if($null -ne $plain){[Array]::Clear($plain,0,$plain.Length)}
 if($null -ne $key){[Array]::Clear($key,0,$key.Length)}
 foreach($name in @('RF_L08_RESTORE_AUTHORIZATION','RF_L08_RESTORE_URL','RF_L08_RESTORE_PASSWORD','RF_L08_BACKUP_KEY_BASE64')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
}
