[CmdletBinding()]
param(
    [ValidateSet('Plan','BackupTest','ResumeBackupTest','RestoreIsolated','ResumeIsolated','StopIsolated')][string]$Action='Plan',
    [switch]$Execute,
    [switch]$AuthorizeNewIsolatedCluster,
    [ValidateRange(0,2147483647)][int]$InterruptedLauncherProcessId=0,
    [switch]$FunctionsOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$caseId='c748e5ab-c20a-43d6-9c17-af5d0a703dab'
$repoRoot=Split-Path -Parent $PSScriptRoot
$privateRoot=Join-Path $repoRoot ('backend\.local\l08-restore-'+$caseId)
$clusterRoot=Join-Path $privateRoot 'cluster'
$dataRoot=Join-Path $clusterRoot 'data'
$archivePath=Join-Path $privateRoot 'ruta_fija_test.rfdb'
$keyPath=Join-Path $privateRoot 'backup-key.dpapi'
$credentialsPath=Join-Path $privateRoot 'target-credentials.dpapi'
$manifestPath=Join-Path $privateRoot 'l08-isolated-restore.json'
$pgBin='C:\Program Files\PostgreSQL\16\bin'
$savedEnvironment=@{}
$keyBytes=$null
$passwordFile=$null
$phase='ARGUMENTS'; $nativeFailureType='NONE'
$nativeFailurePhase='NONE'; $nativeSqlState='NONE'
$nativeTool='NONE'; $nativeWaitResult='NONE'; $launcherLock=$null

function Assert-PhysicalAncestors([string]$Path) {
    $candidate=[IO.Path]::GetFullPath($Path)
    while (-not [string]::IsNullOrWhiteSpace($candidate)) {
        if (Test-Path -LiteralPath $candidate) {
            $item=Get-Item -LiteralPath $candidate -Force -ErrorAction Stop
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'PATH_ANCESTOR_REPARSE_REFUSED' }
        }
        $parent=Split-Path -Parent $candidate
        if ($parent -eq $candidate) { break }
        $candidate=$parent
    }
}

function Set-PrivateDirectory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType Directory -Path $Path -ErrorAction Stop | Out-Null }
    $item=Get-Item -LiteralPath $Path -ErrorAction Stop
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'PRIVATE_DIRECTORY_REPARSE_REFUSED' }
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new(); $acl.SetOwner($sid); $acl.SetAccessRuleProtection($true,$false)
    $inheritance=[Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,[Security.AccessControl.FileSystemRights]::FullControl,$inheritance,[Security.AccessControl.PropagationFlags]::None,[Security.AccessControl.AccessControlType]::Allow))
    Set-Acl -LiteralPath $Path -AclObject $acl -ErrorAction Stop
}
function Set-ProcessSetting([string]$Name,[string]$Value) {
    if (-not $savedEnvironment.ContainsKey($Name)) { $savedEnvironment[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process') }
    [Environment]::SetEnvironmentVariable($Name,$Value,'Process')
}
function ConvertTo-WindowsNativeArgument([string]$Value) {
    if ($null -eq $Value -or $Value.Contains([char]0)) { throw 'INVALID_NATIVE_ARGUMENT' }
    $encoded=[Text.StringBuilder]::new(); [void]$encoded.Append([char]34); $slashes=0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq [char]92) { $slashes++; continue }
        if ($character -eq [char]34) { [void]$encoded.Append([char]92,($slashes*2+1)); [void]$encoded.Append([char]34) }
        else { [void]$encoded.Append([char]92,$slashes); [void]$encoded.Append($character) }
        $slashes=0
    }
    [void]$encoded.Append([char]92,($slashes*2)); [void]$encoded.Append([char]34)
    return $encoded.ToString()
}
function Invoke-CapturedNative([string]$Binary,[string[]]$Arguments,[string]$StandardInput,[int]$TimeoutSeconds=1800) {
    # A PowerShell & pipeline can wait for handles inherited by the PostgreSQL child.
    # Wait only for the owned executable, then bound the stdout/stderr drain separately.
    $nativeProcess=$null; $operationPath=$null; $operation=$null; $outputTask=$null; $errorTask=$null
    $script:nativeWaitResult='STARTING'; $script:nativeTool=[IO.Path]::GetFileName($Binary)
    try {
        if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 3600) { throw 'INVALID_NATIVE_TIMEOUT' }
        $executable=if ([IO.Path]::IsPathRooted($Binary)) { (Resolve-Path -LiteralPath $Binary -ErrorAction Stop).Path } else { (Get-Command -Name $Binary -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source }
        $startInfo=[Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName=$executable; $startInfo.UseShellExecute=$false; $startInfo.CreateNoWindow=$true
        $startInfo.WorkingDirectory=$repoRoot
        $startInfo.Arguments=(@($Arguments | ForEach-Object { ConvertTo-WindowsNativeArgument $_ }) -join ' ')
        $startInfo.RedirectStandardInput=$true; $startInfo.RedirectStandardOutput=$true; $startInfo.RedirectStandardError=$true
        $startInfo.StandardOutputEncoding=[Text.UTF8Encoding]::new($false); $startInfo.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
        $startInfo.EnvironmentVariables.Clear()
        $allowedEnvironment=@('SystemRoot','WINDIR','TEMP','TMP','PATH','PATHEXT','PGPASSWORD',
            'RF_TEST_DATASOURCE_URL','RF_TEST_DATASOURCE_USERNAME','RF_TEST_DATASOURCE_PASSWORD',
            'RF_TEST_MIGRATOR_URL','RF_TEST_MIGRATOR_USERNAME','RF_TEST_MIGRATOR_PASSWORD',
            'SPRING_FLYWAY_USERNAME','SPRING_FLYWAY_USER','SPRING_FLYWAY_PASSWORD',
            'RF_L08_BACKUP_KEY_BASE64','RF_L08_BACKUP_AUTHORIZATION','RF_L08_RESTORE_AUTHORIZATION','RF_L08_RESTORE_URL','RF_L08_RESTORE_PASSWORD')
        foreach ($name in $allowedEnvironment) {
            $value=[Environment]::GetEnvironmentVariable($name,'Process')
            if ($null -ne $value) { $startInfo.EnvironmentVariables[$name]=$value }
        }
        $startInfo.EnvironmentVariables['PGCONNECT_TIMEOUT']='5'; $startInfo.EnvironmentVariables['PGCLIENTENCODING']='UTF8'
        $nativeProcess=[Diagnostics.Process]::new(); $nativeProcess.StartInfo=$startInfo
        if (-not $nativeProcess.Start()) { throw 'OWNED_NATIVE_START_FAILED' }
        $operationRoot=Join-Path $privateRoot 'native-operations'
        if (-not (Test-Path -LiteralPath $operationRoot)) { Set-PrivateDirectory $operationRoot }
        $operationPath=Join-Path $operationRoot ('operation-'+[Guid]::NewGuid().ToString()+'.json')
        $operation=[ordered]@{caseId=$caseId;phase=$phase;tool=[IO.Path]::GetFileName($executable);processId=$nativeProcess.Id;
            processStartedAtUtc=$nativeProcess.StartTime.ToUniversalTime().ToString('o');startedAtUtc=[DateTime]::UtcNow.ToString('o');status='RUNNING';
            argumentsRecorded=$false;stdinRecorded=$false;stdoutRecorded=$false;stderrRecorded=$false;timeoutSeconds=$TimeoutSeconds}
        Write-Json $operationPath $operation
        $outputTask=$nativeProcess.StandardOutput.ReadToEndAsync(); $errorTask=$nativeProcess.StandardError.ReadToEndAsync()
        if ($null -ne $StandardInput) { $nativeProcess.StandardInput.WriteLine($StandardInput) }
        $nativeProcess.StandardInput.Close()
        if (-not $nativeProcess.WaitForExit($TimeoutSeconds*1000)) {
            $script:nativeWaitResult='OWNED_PROCESS_TIMEOUT'
            # Kill through the retained object for the exact executable just started, never by name or descendant tree.
            if (-not $nativeProcess.HasExited) { $nativeProcess.Kill(); [void]$nativeProcess.WaitForExit(5000) }
            throw 'OWNED_NATIVE_TIMEOUT_KEEP_CASE'
        }
        $nativeExit=$nativeProcess.ExitCode
        $operation['exitCode']=$nativeExit
        $script:nativeWaitResult='DRAINING_CAPTURE_AFTER_OWNED_EXIT'
        $outputReady=$outputTask.Wait(2000); $errorReady=$errorTask.Wait(2000)
        $stdout=if ($outputReady) { $outputTask.Result } else { '' }
        $stderr=if ($errorReady) { $errorTask.Result } else { '' }
        if (-not $outputReady -or -not $errorReady) {
            $script:nativeWaitResult='OWNED_PROCESS_EXITED_INHERITED_CAPTURE_HANDLES_CLOSED'
            $nativeProcess.StandardOutput.Close(); $nativeProcess.StandardError.Close()
            if ([IO.Path]::GetFileName($executable) -ine 'pg_ctl.exe' -or $nativeExit -ne 0) { throw 'NATIVE_CAPTURE_DRAIN_TIMEOUT_KEEP_CASE' }
        } else { $script:nativeWaitResult='OWNED_PROCESS_EXITED_AND_CAPTURE_DRAINED' }
        $captured=@(($stdout+$stderr) -split '\r?\n' | Where-Object { $_ -ne '' })
        if ($nativeExit -ne 0) {
            foreach ($line in $captured) {
                $safeText=$line.ToString()
                if ($safeText -match '"failureType"\s*:\s*"([A-Za-z0-9_]+)"') { $script:nativeFailureType=$matches[1] }
                if ($safeText -match '"failurePhase"\s*:\s*"([A-Z0-9_]+)"') { $script:nativeFailurePhase=$matches[1] }
                if ($safeText -match '"sqlState"\s*:\s*"([A-Z0-9]{5})"') { $script:nativeSqlState=$matches[1] }
                if ($safeText -match 'ERROR:\s+([A-Z0-9]{5})(?:\s|$)') { $script:nativeSqlState=$matches[1] }
            }
            throw 'NATIVE_OPERATION_STOPPED_DETAILS_REDACTED'
        }
        $operation['exitCode']=$nativeExit; $operation['status']='COMPLETED'
        return @($captured | ForEach-Object { $_.ToString() })
    } finally {
        if ($null -ne $operation) {
            if ($operation['status'] -eq 'RUNNING') { $operation['status']='STOPPED_KEEP_CASE' }
            $operation['finishedAtUtc']=[DateTime]::UtcNow.ToString('o'); $operation['waitResult']=$script:nativeWaitResult
            if ($null -ne $operationPath) { Write-Json $operationPath $operation }
        }
        if ($null -ne $nativeProcess) {
            try { if (-not $nativeProcess.HasExited) { $nativeProcess.Kill(); [void]$nativeProcess.WaitForExit(5000) } } catch { }
            try { $nativeProcess.StandardInput.Close() } catch { }
            try { $nativeProcess.StandardOutput.Close() } catch { }
            try { $nativeProcess.StandardError.Close() } catch { }
            $nativeProcess.Dispose()
        }
    }
}
function New-RandomBytes([int]$Count) {
    $value=New-Object byte[] $Count; $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($value) } finally { $rng.Dispose() }
    return ,$value
}
function Write-Json([string]$Path,$Value) { [IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false)) }
function Assert-NoProtectedCase {
    if (@(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend\.local') -Filter '*.active' -ErrorAction SilentlyContinue).Count -gt 0) { throw 'PROTECTED_CASE_ACTIVE' }
}
function Enter-RehearsalLock {
    if ($null -ne $script:launcherLock) { throw 'LAUNCHER_LOCK_ALREADY_HELD' }
    $lockPath=Join-Path $privateRoot 'launcher.lock'
    Assert-PhysicalAncestors $lockPath
    $script:launcherLock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
}
function Read-IsolatedCredentials {
    Assert-PhysicalAncestors $credentialsPath
    $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($credentialsPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    try {
        $decoded=[Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
        $names=@($decoded.PSObject.Properties.Name | Sort-Object)
        if (($names -join ',') -ne 'rf_l08_owner,rf_migrator,rf_test') { throw 'EXACT_DPAPI_CREDENTIAL_ROLES_REQUIRED' }
        $values=[ordered]@{}
        foreach ($role in @('rf_l08_owner','rf_migrator','rf_test')) {
            $secret=[string]$decoded.$role
            if ($secret -notmatch '^[A-Za-z0-9+/]{43}=$' -or [Convert]::FromBase64String($secret).Length -ne 32) { throw 'INVALID_DPAPI_CREDENTIAL_FORMAT' }
            $values[$role]=$secret
        }
        return $values
    } finally { [Array]::Clear($plain,0,$plain.Length) }
}
function Assert-NoPendingOwnedNative {
    $operations=Join-Path $privateRoot 'native-operations'
    if (-not (Test-Path -LiteralPath $operations)) { return }
    foreach ($file in @(Get-ChildItem -LiteralPath $operations -File -Filter 'operation-*.json')) {
        $operation=Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($operation.caseId -ne $caseId) { throw 'UNEXPECTED_NATIVE_OPERATION_RECORD' }
        $running=Get-Process -Id ([int]$operation.processId) -ErrorAction SilentlyContinue
        if ($null -ne $running) {
            $recorded=[DateTime]::Parse($operation.processStartedAtUtc).ToUniversalTime()
            if ([Math]::Abs(($running.StartTime.ToUniversalTime()-$recorded).TotalSeconds) -lt 1) { throw 'OWNED_NATIVE_OPERATION_STILL_RUNNING' }
        }
    }
}
function Get-IsolatedClusterState {
    $versionPath=Join-Path $dataRoot 'PG_VERSION'
    if (-not (Test-Path -LiteralPath $versionPath -PathType Leaf) -or [IO.File]::ReadAllText($versionPath).Trim() -ne '16') { throw 'SAME_INITIALIZED_POSTGRES16_CLUSTER_REQUIRED' }
    $listeners=@(Get-NetTCPConnection -LocalPort 55438 -State Listen -ErrorAction SilentlyContinue)
    $pidFile=Join-Path $dataRoot 'postmaster.pid'
    if (-not (Test-Path -LiteralPath $pidFile)) {
        if ($listeners.Count -ne 0) { throw 'ISOLATED_LISTENER_WITHOUT_OWNED_PIDFILE' }
        return [ordered]@{state='STOPPED_SAME_CLUSTER';postmasterId=$null}
    }
    Assert-PhysicalAncestors $pidFile
    $lines=[IO.File]::ReadAllLines($pidFile)
    if ($lines.Length -lt 4 -or $lines[0] -notmatch '^[1-9][0-9]*$' -or $lines[2] -notmatch '^[0-9]+$' -or
        [IO.Path]::GetFullPath($lines[1]) -ine [IO.Path]::GetFullPath($dataRoot) -or $lines[3].Trim() -ne '55438') { throw 'EXACT_ISOLATED_PIDFILE_REQUIRED' }
    $postmasterId=[int]$lines[0]
    $running=Get-Process -Id $postmasterId -ErrorAction SilentlyContinue
    if ($null -eq $running) {
        if ($listeners.Count -ne 0) { throw 'ISOLATED_PORT_OWNED_BY_ANOTHER_PROCESS' }
        return [ordered]@{state='STOPPED_SAME_CLUSTER_STALE_PID_PRESERVED';postmasterId=$postmasterId}
    }
    $started=[DateTimeOffset]::FromUnixTimeSeconds([long]$lines[2]).UtcDateTime
    if ($running.ProcessName -ine 'postgres' -or [Math]::Abs(($running.StartTime.ToUniversalTime()-$started).TotalSeconds) -gt 10 -or
        $listeners.Count -ne 1 -or $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne $postmasterId) { throw 'EXACT_OWNED_POSTMASTER_AND_LOOPBACK_LISTENER_REQUIRED' }
    return [ordered]@{state='RUNNING_SAME_CLUSTER';postmasterId=$postmasterId}
}
function Invoke-IsolatedOwnerQuery([string]$Sql,[string]$Database='postgres') {
    if ($Database -notin @('postgres','ruta_fija_test')) { throw 'ISOLATED_DATABASE_ALLOWLIST_REQUIRED' }
    Set-ProcessSetting 'PGPASSWORD' $credentials['rf_l08_owner']
    return @(Invoke-CapturedNative (Join-Path $pgBin 'psql.exe') @('-X','-q','-A','-t','-P','pager=off','-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','--no-password','-h','127.0.0.1','-p','55438','-U','rf_l08_owner','-d',$Database,'-f','-') $Sql 30)
}
function Assert-IsolatedServerIdentity {
    $identity=@(Invoke-IsolatedOwnerQuery "select current_user||'|'||current_database()||'|'||current_setting('port')||'|'||current_setting('data_directory')||'|'||current_setting('listen_addresses')||'|'||current_setting('server_version_num');")
    if ($identity.Count -ne 1) { throw 'EXACT_ISOLATED_SERVER_IDENTITY_REQUIRED' }
    $parts=$identity[0].Split('|')
    if ($parts.Count -ne 6 -or $parts[0] -ne 'rf_l08_owner' -or $parts[1] -ne 'postgres' -or $parts[2] -ne '55438' -or
        [IO.Path]::GetFullPath($parts[3]) -ine [IO.Path]::GetFullPath($dataRoot) -or $parts[4] -ne '127.0.0.1' -or
        [int]$parts[5] -lt 160000 -or [int]$parts[5] -ge 170000) { throw 'ISOLATED_SERVER_IDENTITY_MISMATCH' }
    $clients=@(Invoke-IsolatedOwnerQuery "select count(*) from pg_stat_activity where backend_type='client backend' and pid<>pg_backend_pid();")
    if ($clients.Count -ne 1 -or $clients[0] -ne '0') { throw 'OTHER_ISOLATED_CLIENT_STILL_ACTIVE' }
}
function Complete-IsolatedBootstrap {
    $script:phase='CHECK_ISOLATED_ROLE_FLAGS'
    $roleRows=@(Invoke-IsolatedOwnerQuery "select rolname||'|'||rolcanlogin::int||'|'||rolsuper::int||'|'||rolcreaterole::int||'|'||rolcreatedb::int from pg_roles where left(rolname,3)<>'pg_' order by rolname;")
    $expected=@{rf_l08_owner='rf_l08_owner|1|1|1|1';rf_migrator='rf_migrator|1|0|0|0';rf_test='rf_test|1|0|0|0';rf_app='rf_app|0|0|0|0'}
    $present=@{}
    foreach ($row in $roleRows) {
        $role=$row.Split('|')[0]
        if (-not $expected.ContainsKey($role) -or $row -ne $expected[$role]) { throw 'UNEXPECTED_ROLE_OR_ROLE_PRIVILEGES_PRESERVE_CLUSTER' }
        $present[$role]=$true
    }
    if (-not $present.ContainsKey('rf_l08_owner')) { throw 'ISOLATED_BOOTSTRAP_OWNER_MISSING' }
    $script:phase='CHECK_ISOLATED_DATABASES'
    $databaseRows=@(Invoke-IsolatedOwnerQuery "select datname||'|'||pg_get_userbyid(datdba) from pg_database where datname not in ('postgres','template0','template1') order by datname;")
    if ($databaseRows.Count -gt 1 -or ($databaseRows.Count -eq 1 -and $databaseRows[0] -ne 'ruta_fija_test|rf_migrator')) { throw 'UNEXPECTED_DATABASE_OR_DATABASE_OWNER' }
    if ($databaseRows.Count -eq 1) {
        $script:phase='CHECK_EXISTING_ISOLATED_TARGET_IS_EMPTY_BEFORE_ANY_WRITE'
        $objects=@(Invoke-IsolatedOwnerQuery "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname not in ('pg_catalog','information_schema') and n.nspname not like 'pg_toast%' and c.relkind in ('r','p','v','m','S','f');" 'ruta_fija_test')
        if ($objects.Count -ne 1 -or $objects[0] -ne '0') { throw 'TARGET_NOT_EMPTY_PRESERVE_EXISTING_RESTORE_NO_REPEAT' }
    }
    # Existing roles are authenticated with the saved DPAPI credentials; never reset their passwords.
    $script:phase='AUTHENTICATE_EXISTING_ISOLATED_ROLES_WITH_DPAPI'
    foreach ($role in @('rf_migrator','rf_test')) {
        if ($present.ContainsKey($role)) {
            Set-ProcessSetting 'PGPASSWORD' $credentials[$role]
            $authenticated=@(Invoke-CapturedNative (Join-Path $pgBin 'psql.exe') @('-X','-q','-A','-t','--no-password','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','55438','-U',$role,'-d','postgres','-c','select current_user;') $null 30)
            if ($authenticated.Count -ne 1 -or $authenticated[0] -ne $role) { throw 'EXISTING_ROLE_DPAPI_AUTHENTICATION_REQUIRED' }
        }
    }
    Set-ProcessSetting 'PGPASSWORD' $credentials['rf_l08_owner']
    $script:phase='CREATE_ONLY_MISSING_ISOLATED_ROLES'
    $statements=[Collections.Generic.List[string]]::new(); $statements.Add('BEGIN;')
    foreach ($role in @('rf_migrator','rf_test')) {
        if (-not $present.ContainsKey($role)) { $statements.Add("CREATE ROLE "+$role+" LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB PASSWORD '"+$credentials[$role]+"';") }
    }
    if (-not $present.ContainsKey('rf_app')) { $statements.Add('CREATE ROLE rf_app NOLOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB;') }
    $statements.Add('GRANT pg_read_all_settings TO rf_migrator;'); $statements.Add('COMMIT;')
    $ignored=@(Invoke-IsolatedOwnerQuery ($statements -join "`n"))
    if ($databaseRows.Count -eq 0) { $script:phase='CREATE_ONLY_MISSING_ISOLATED_DATABASE'; $ignored=@(Invoke-IsolatedOwnerQuery "CREATE DATABASE ruta_fija_test OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';") }
    $script:phase='VERIFY_FINAL_EMPTY_ISOLATED_TARGET'
    $objects=@(Invoke-IsolatedOwnerQuery "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname not in ('pg_catalog','information_schema') and n.nspname not like 'pg_toast%' and c.relkind in ('r','p','v','m','S','f');" 'ruta_fija_test')
    if ($objects.Count -ne 1 -or $objects[0] -ne '0') { throw 'TARGET_NOT_EMPTY_PRESERVE_EXISTING_RESTORE_NO_REPEAT' }
}

if($FunctionsOnly){return}
try {
    $phase='PHYSICAL_PATH_GUARDS'
    if ((Resolve-Path -LiteralPath $repoRoot).Path -ne $repoRoot) { throw 'REPOSITORY_PATH_CHANGED' }
    foreach ($path in @($privateRoot,$clusterRoot,$dataRoot)) {
        if (-not [IO.Path]::GetFullPath($path).StartsWith(($repoRoot+[IO.Path]::DirectorySeparatorChar),[StringComparison]::OrdinalIgnoreCase)) { throw 'DESTINATION_OUTSIDE_REPOSITORY' }
        Assert-PhysicalAncestors $path
    }
    if ($Action -eq 'Plan') {
        [ordered]@{caseId=$caseId;action='PLAN_NO_CONNECTION_NO_FILES';source='ruta_fija_test@127.0.0.1:5432';restore='NEW ruta_fija_test@127.0.0.1:55438';privateRelativeDirectory=('backend/.local/l08-restore-'+$caseId);sourceReadOnly=$true;sharedTargetOverwrite=$false;createsPilot=$false;independentMedia=$false;newClusterAuthorizationRequired=$true} | ConvertTo-Json -Compress
        exit 0
    }
    if (-not $Execute) { throw 'EXPLICIT_EXECUTE_REQUIRED' }
    Assert-NoProtectedCase
    Add-Type -AssemblyName System.Security
    $classFile=Join-Path $repoRoot 'backend\target\classes\pe\rutafija\backup\TestDatabaseBackupCli.class'
    if (-not (Test-Path -LiteralPath $classFile -PathType Leaf)) { throw 'COMPILED_BACKUP_CLIENT_REQUIRED' }
    $dependencies=[IO.File]::ReadAllText((Join-Path $repoRoot 'backend\target\manual-otp-classpath.txt')).Trim()
    $javaClasspath=(Join-Path $repoRoot 'backend\target\classes')+[IO.Path]::PathSeparator+$dependencies
    if ($Action -in @('BackupTest','ResumeBackupTest')) {
        $phase='BACKUP_CASE_GUARDS'
        if ($Action -eq 'BackupTest' -and (Test-Path -LiteralPath $privateRoot)) { throw 'EXISTING_CASE_PRESERVE_AND_REVIEW' }
        if ($Action -eq 'ResumeBackupTest' -and (-not (Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $archivePath) -or (Test-Path -LiteralPath $manifestPath))) { throw 'EXACT_INCOMPLETE_CASE_WITHOUT_ARCHIVE_REQUIRED' }
        foreach ($tool in @('pg_dump.exe','pg_restore.exe','initdb.exe','pg_ctl.exe','psql.exe')) { if (-not (Test-Path -LiteralPath (Join-Path $pgBin $tool))) { throw 'NATIVE_TOOL_MISSING' } }
        if ($Action -eq 'BackupTest') {
            Set-PrivateDirectory $privateRoot
            Enter-RehearsalLock
            $keyBytes=New-RandomBytes 32
            [IO.File]::WriteAllBytes($keyPath,[Security.Cryptography.ProtectedData]::Protect($keyBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
        } else {
            Enter-RehearsalLock
            $keyBytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        }
        $phase='PRIVATE_TEST_CONFIGURATION'
        $settings=& (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -Scope Test -PassThru
        foreach ($entry in $settings.GetEnumerator()) { Set-ProcessSetting $entry.Key $entry.Value }
        Set-ProcessSetting 'RF_L08_BACKUP_KEY_BASE64' ([Convert]::ToBase64String($keyBytes))
        Set-ProcessSetting 'RF_L08_BACKUP_AUTHORIZATION' 'LOCAL_TEST_READ_ONLY'
        $phase='READ_ONLY_ENCRYPTED_BACKUP'
        $output=Invoke-CapturedNative 'java' @('-Xms32m','-Xmx384m','-cp',$javaClasspath,'pe.rutafija.backup.TestDatabaseBackupCli',(Join-Path $pgBin 'pg_dump.exe'),$archivePath) $null 1900
        $result=($output -join [Environment]::NewLine) | ConvertFrom-Json
        if ($result.result -ne 'PASS_ENCRYPTED_READABLE_LOCAL_COPY_ONLY_NOT_RESTORE') { throw 'BACKUP_NOT_ACCREDITED_KEEP_STATE' }
        $manifest=[ordered]@{caseId=$caseId;purpose='L08_SYNTHETIC_TEST_RESTORE_ONLY';port=55438;database='ruta_fija_test';dataDirectory=$dataRoot;archiveSha256=$result.archiveSha256;sourceIntegrity=$result.sourceIntegrity;referencedAclRoles=@($result.referencedAclRoles);sourceSchemaVersion=$result.sourceSchemaVersion;sourceBackupAt=$result.finishedAt}
        Write-Json $manifestPath $manifest; Write-Json (Join-Path $privateRoot 'backup-result.json') $result
        Write-Output ('L08_BACKUP_CASE_ID='+$caseId)
        Write-Output ('L08_BACKUP_RESULT='+$result.result)
        Write-Output ('L08_BACKUP_SHA256='+$result.archiveSha256)
        exit 0
    }
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'EXISTING_BACKUP_MANIFEST_REQUIRED' }
    foreach ($file in @($manifestPath,$archivePath,$keyPath,$credentialsPath)) { Assert-PhysicalAncestors $file }
    $manifest=Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.caseId -ne $caseId -or $manifest.purpose -ne 'L08_SYNTHETIC_TEST_RESTORE_ONLY' -or $manifest.dataDirectory -ne $dataRoot -or $manifest.port -ne 55438) { throw 'EXACT_CASE_MANIFEST_REQUIRED' }
    if ($manifest.database -ne 'ruta_fija_test' -or $manifest.archiveSha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ine $manifest.archiveSha256) { throw 'EXACT_PRESERVED_ARCHIVE_REQUIRED' }
    Enter-RehearsalLock
    if ($Action -eq 'StopIsolated') {
        $phase='VERIFY_OWNED_ISOLATED_CLUSTER_BEFORE_STOP'
        $clusterState=Get-IsolatedClusterState
        if ($clusterState.state -ne 'RUNNING_SAME_CLUSTER') { Write-Output 'L08_RESTORE_CLUSTER=ALREADY_STOPPED_SAME_DATA_PRESERVED'; exit 0 }
        $phase='STOP_ONLY_OWNED_55438_CLUSTER'
        $output=Invoke-CapturedNative (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'stop','-m','fast','-w','-t','30') $null 45
        Write-Output 'L08_RESTORE_CLUSTER=STOPPED_OWNED_DATA_PRESERVED'; exit 0
    }
    if (Test-Path -LiteralPath (Join-Path $privateRoot 'restore-result.json')) { throw 'RESTORE_RESULT_EXISTS_PRESERVE_AND_REVIEW_NO_REPEAT' }
    if ($Action -eq 'RestoreIsolated') {
        $phase='NEW_ISOLATED_CLUSTER_GUARDS'
        if (-not $AuthorizeNewIsolatedCluster) { throw 'NEW_ISOLATED_CLUSTER_AUTHORIZATION_REQUIRED' }
        if ((Test-Path -LiteralPath $clusterRoot) -or (Test-Path -LiteralPath $credentialsPath)) { throw 'EXISTING_CLUSTER_PRESERVE_AND_REVIEW' }
        if (@(Get-NetTCPConnection -LocalPort 55438 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { throw 'ISOLATED_PORT_ALREADY_OCCUPIED' }
        Set-PrivateDirectory $clusterRoot
        $credentials=[ordered]@{}
        foreach ($role in @('rf_l08_owner','rf_migrator','rf_test')) { $credentials[$role]=[Convert]::ToBase64String((New-RandomBytes 32)) }
        $credentialBytes=[Text.Encoding]::UTF8.GetBytes(($credentials | ConvertTo-Json -Compress))
        try { [IO.File]::WriteAllBytes($credentialsPath,[Security.Cryptography.ProtectedData]::Protect($credentialBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)) } finally { [Array]::Clear($credentialBytes,0,$credentialBytes.Length) }
        $passwordFile=Join-Path $clusterRoot 'bootstrap-password.txt'
        [IO.File]::WriteAllText($passwordFile,$credentials['rf_l08_owner'],[Text.UTF8Encoding]::new($false))
        $phase='INITIALIZE_NEW_OWNED_CLUSTER'
        $output=Invoke-CapturedNative (Join-Path $pgBin 'initdb.exe') @('-D',$dataRoot,'-U','rf_l08_owner',('--pwfile='+$passwordFile),'--auth=scram-sha-256','--encoding=UTF8','--locale=C') $null 90
        [IO.File]::AppendAllText((Join-Path $dataRoot 'postgresql.conf'),"`nlisten_addresses='127.0.0.1'`nport=55438`nmax_connections=40`nshared_buffers='64MB'`nlogging_collector=off`nlog_statement='none'`nlog_min_error_statement='panic'`ntimezone='UTC'`n",[Text.UTF8Encoding]::new($false))
        $phase='START_NEW_OWNED_CLUSTER'
        $output=Invoke-CapturedNative (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'-l',(Join-Path $clusterRoot 'postgresql.log'),'start','-w','-t','30') $null 45
    } elseif ($Action -eq 'ResumeIsolated') {
        $phase='INTERRUPTED_LAUNCHER_MUST_BE_STOPPED'
        if ($InterruptedLauncherProcessId -lt 1) { throw 'EXACT_INTERRUPTED_LAUNCHER_PID_REQUIRED' }
        if ($null -ne (Get-Process -Id $InterruptedLauncherProcessId -ErrorAction SilentlyContinue)) { throw 'INTERRUPTED_LAUNCHER_STILL_ALIVE_DO_NOT_RUN_CONCURRENTLY' }
        Assert-NoPendingOwnedNative
        $phase='READ_SAME_ISOLATED_DPAPI_CREDENTIALS'
        if (-not (Test-Path -LiteralPath $credentialsPath -PathType Leaf)) { throw 'SAME_DPAPI_CREDENTIALS_REQUIRED_NO_REGENERATION' }
        $credentials=Read-IsolatedCredentials
        $phase='VERIFY_SAME_INITIALIZED_CLUSTER'
        $clusterState=Get-IsolatedClusterState
        if ($clusterState.state -ne 'RUNNING_SAME_CLUSTER') {
            $phase='START_SAME_PREVIOUSLY_INITIALIZED_CLUSTER'
            $output=Invoke-CapturedNative (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'-l',(Join-Path $clusterRoot 'postgresql.log'),'start','-w','-t','30') $null 45
        }
        $bootstrap=Join-Path $clusterRoot 'bootstrap-password.txt'
        if (Test-Path -LiteralPath $bootstrap) {
            $phase='VERIFY_EXACT_INTERRUPTED_BOOTSTRAP_FILE'
            Assert-PhysicalAncestors $bootstrap
            if ([IO.File]::ReadAllText($bootstrap) -cne $credentials['rf_l08_owner']) { throw 'BOOTSTRAP_FILE_DOES_NOT_MATCH_DPAPI_PRESERVE' }
            # Only this exact, verified temporary file is eligible for removal in finally.
            $passwordFile=$bootstrap
        }
    } else { throw 'EXACT_RESTORE_OR_RESUME_ACTION_REQUIRED' }
    $phase='VERIFY_LIVE_SAME_ISOLATED_POSTMASTER'
    $clusterState=Get-IsolatedClusterState
    if ($clusterState.state -ne 'RUNNING_SAME_CLUSTER') { throw 'SAME_CLUSTER_MUST_BE_READY' }
    $phase='VERIFY_ISOLATED_SQL_SERVER_IDENTITY'
    Assert-IsolatedServerIdentity
    $phase='CHECK_ROLES_AND_COMPLETE_ONLY_MISSING_BOOTSTRAP'
    Complete-IsolatedBootstrap
    if ($null -ne $passwordFile -and (Test-Path -LiteralPath $passwordFile)) {
        $phase='REMOVE_ONLY_VERIFIED_BOOTSTRAP_FILE'
        Assert-PhysicalAncestors $passwordFile
        Remove-Item -LiteralPath $passwordFile -ErrorAction Stop; $passwordFile=$null
    }
    $phase='RESTORE_ONLY_EMPTY_SAME_ISOLATED_DATABASE'
    $keyBytes=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyPath),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    Set-ProcessSetting 'RF_L08_BACKUP_KEY_BASE64' ([Convert]::ToBase64String($keyBytes))
    Set-ProcessSetting 'RF_L08_RESTORE_AUTHORIZATION' 'NEW_EMPTY_CLUSTER_55438'
    Set-ProcessSetting 'RF_L08_RESTORE_URL' 'jdbc:postgresql://127.0.0.1:55438/ruta_fija_test'
    Set-ProcessSetting 'RF_L08_RESTORE_PASSWORD' $credentials['rf_migrator']
    $output=Invoke-CapturedNative 'java' @('-Xms32m','-Xmx384m','-cp',$javaClasspath,'pe.rutafija.backup.IsolatedTestRestoreCli',(Join-Path $pgBin 'pg_restore.exe'),$archivePath,$manifestPath) $null 1900
    $result=($output -join [Environment]::NewLine) | ConvertFrom-Json
    Write-Json (Join-Path $privateRoot 'restore-result.json') $result
    if ($result.result -ne 'PASS_ISOLATED_SQL_RESTORE_ONLY_INTEGRITY_AND_APP_CHECKS_STILL_REQUIRED') { throw 'RESTORE_NOT_ACCREDITED_KEEP_CLUSTER_AND_CASE' }
    $phase='REVOKE_TEMPORARY_ISOLATED_SETTINGS_ACCESS'
    $output=@(Invoke-IsolatedOwnerQuery 'REVOKE pg_read_all_settings FROM rf_migrator;')
    Write-Output ('L08_RESTORE_CASE_ID='+$caseId)
    Write-Output ('L08_RESTORE_RESULT='+$result.result)
    Write-Output ('L08_RESTORE_SCHEMA='+$result.restoredSchemaVersion)
    Write-Output ('L08_RESTORE_TABLES_COMPARED='+$result.tablesCompared)
} catch {
    Write-Output 'L08_DATABASE_REHEARSAL=STOPPED_DETAILS_REDACTED_KEEP_FILES_AND_CASE'
    Write-Output ('L08_DATABASE_REHEARSAL_PHASE='+$phase)
    Write-Output ('L08_DATABASE_REHEARSAL_FAILURE_TYPE='+$_.Exception.GetType().Name)
    Write-Output ('L08_DATABASE_NATIVE_FAILURE_TYPE='+$nativeFailureType)
    Write-Output ('L08_DATABASE_NATIVE_FAILURE_PHASE='+$nativeFailurePhase)
    Write-Output ('L08_DATABASE_NATIVE_SQLSTATE='+$nativeSqlState)
    Write-Output ('L08_DATABASE_NATIVE_TOOL='+$nativeTool)
    Write-Output ('L08_DATABASE_NATIVE_WAIT='+$nativeWaitResult)
    exit 1
} finally {
    $cleanupFailed=$false
    try {
        if ($null -ne $passwordFile -and (Test-Path -LiteralPath $passwordFile)) {
            $expectedPasswordFile=[IO.Path]::GetFullPath((Join-Path $clusterRoot 'bootstrap-password.txt'))
            if ([IO.Path]::GetFullPath($passwordFile) -eq $expectedPasswordFile) {
                Assert-PhysicalAncestors $passwordFile
                Remove-Item -LiteralPath $passwordFile -ErrorAction Stop
            } else { throw 'BOOTSTRAP_CLEANUP_TARGET_MISMATCH' }
        }
    } catch { $cleanupFailed=$true; Write-Output 'L08_DATABASE_BOOTSTRAP_CLEANUP=STOPPED_KEEP_EXACT_FILE_AND_DIAGNOSE' }
    try { if ($null -ne $keyBytes) { [Array]::Clear($keyBytes,0,$keyBytes.Length) } } catch { $cleanupFailed=$true }
    try {
        foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$savedEnvironment[$name],'Process') }
    } catch { $cleanupFailed=$true }
    finally {
        if ($null -ne $launcherLock) { $launcherLock.Dispose() }
    }
    if ($cleanupFailed) { Write-Output 'L08_DATABASE_PRIVATE_CLEANUP=NOT_ACCREDITED'; exit 1 }
}
