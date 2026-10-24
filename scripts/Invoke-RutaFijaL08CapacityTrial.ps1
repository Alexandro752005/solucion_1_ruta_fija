[CmdletBinding()]
param(
    [ValidateSet('Plan','Prepare','Run','Inspect','Stop')][string]$Action = 'Plan',
    [switch]$Execute,
    [switch]$Resume
)
Set-StrictMode -Version Latest;
$ErrorActionPreference = 'Stop';
$caseId = '860c1cdb-b062-469a-9a3f-156ea23bb656';
$proposalHash = 'cda897e21d2063589d253dd6c4d18ec39811d11cb7231988e63f7195aa8df8fb';
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));
$privateRoot = Join-Path $repoRoot ('backend/.local/l08-cap25-' + $caseId);
$clusterRoot = Join-Path $privateRoot 'cluster';
$dataRoot = Join-Path $clusterRoot 'data';
$credentialsPath = Join-Path $privateRoot 'target-credentials.dpapi';
$jwtPath = Join-Path $privateRoot 'jwt-key.dpapi';
$manifestPath = Join-Path $privateRoot 'infrastructure-manifest.json';
$authPath = Join-Path $repoRoot 'docs/evidencia/L-08/CAP25-01-autorizacion.json';
$pgBin = 'C:\Program Files\PostgreSQL\16\bin';
$phase = 'ARGUMENTS';
$nativeCheck = 'NONE'; $nativeSqlState = 'NONE';
$credentials = $null; $launcherLock = $null; $passwordFile = $null; $jwtBytes = $null;
$manifest = $null;

function Assert-PhysicalAncestors([string]$Path) {
    $candidate = [IO.Path]::GetFullPath($Path);
    while (-not [string]::IsNullOrWhiteSpace($candidate)) {
        if (Test-Path -LiteralPath $candidate) {
            if (((Get-Item -LiteralPath $candidate -Force -ErrorAction Stop).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'PATH_ANCESTOR_REPARSE_REFUSED'; }
        }
        $parent = Split-Path -Parent $candidate;
        if ($parent -eq $candidate) { break; }
        $candidate = $parent;
    }
}
function Set-PrivateDirectory([string]$Path) {
    Assert-PhysicalAncestors $Path;
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User;
    if (Test-Path -LiteralPath $Path) {
        # Reuse the restricted directory. Reapplying SetOwner on an existing ACL
        # can require SeRestorePrivilege even when the expected SID already owns
        # it. Verify ownership and access instead of changing its security again.
        $existingAcl=Get-Acl -LiteralPath $Path -ErrorAction Stop;
        if ($existingAcl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid.Value -or -not $existingAcl.AreAccessRulesProtected) { throw 'EXISTING_PRIVATE_DIRECTORY_OWNER_OR_PROTECTION_CHANGED'; }
        foreach ($rule in $existingAcl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
            if ($rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow -and $rule.IdentityReference.Value -ne $sid.Value) { throw 'EXISTING_PRIVATE_DIRECTORY_EXPOSED_TO_OTHER_PRINCIPAL'; }
        }
        return;
    }
    New-Item -ItemType Directory -Path $Path -ErrorAction Stop | Out-Null;
    $acl = [Security.AccessControl.DirectorySecurity]::new();
    $acl.SetOwner($sid); $acl.SetAccessRuleProtection($true, $false);
    $inherit = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit;
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, [Security.AccessControl.FileSystemRights]::FullControl, $inherit, [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow));
    Set-Acl -LiteralPath $Path -AclObject $acl -ErrorAction Stop;
}
function Write-PrivateJson([string]$Path, $Value, [switch]$CreateNew) {
    Assert-PhysicalAncestors $Path;
    $mode = if ($CreateNew) { [IO.FileMode]::CreateNew } else { [IO.FileMode]::Create };
    $stream = [IO.File]::Open($Path, $mode, [IO.FileAccess]::Write, [IO.FileShare]::Read);
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 25));
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush(); } finally { [Array]::Clear($bytes, 0, $bytes.Length); }
    } finally { $stream.Dispose(); }
}
function Set-ManifestStage([string]$Stage) {
    $script:manifest['stage'] = $Stage;
    $script:manifest['updatedAtUtc'] = [DateTime]::UtcNow.ToString('o');
    Write-PrivateJson $manifestPath $script:manifest;
}
function New-RandomBytes([int]$Count) {
    $value = New-Object byte[] $Count;
    $random = [Security.Cryptography.RandomNumberGenerator]::Create();
    try { $random.GetBytes($value); } finally { $random.Dispose(); }
    return ,$value;
}
function ConvertTo-NativeArgument([string]$Value) {
    if ($null -eq $Value -or $Value.Contains([char]0)) { throw 'INVALID_NATIVE_ARGUMENT'; }
    $text = [Text.StringBuilder]::new(); [void]$text.Append([char]34); $slashes = 0;
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq [char]92) { $slashes++; continue; }
        if ($character -eq [char]34) { [void]$text.Append([char]92, ($slashes * 2 + 1)); [void]$text.Append([char]34); }
        else { [void]$text.Append([char]92, $slashes); [void]$text.Append($character); }
        $slashes = 0;
    }
    [void]$text.Append([char]92, ($slashes * 2)); [void]$text.Append([char]34);
    return $text.ToString();
}
function Invoke-CapNative([string]$Binary, [string[]]$Arguments, [string]$StandardInput, [hashtable]$Environment, [int]$TimeoutSeconds = 120) {
    $native = $null; $record = $null; $recordPath = $null;
    $stdoutTask = $null; $stderrTask = $null; $waitResult = 'STARTING';
    try {
        if ($TimeoutSeconds -lt 1 -or $TimeoutSeconds -gt 3600) { throw 'BOUNDED_NATIVE_TIMEOUT_REQUIRED'; }
        $executable = if ([IO.Path]::IsPathRooted($Binary)) { (Resolve-Path -LiteralPath $Binary -ErrorAction Stop).Path } else { (Get-Command -Name $Binary -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source };
        $info = [Diagnostics.ProcessStartInfo]::new();
        $info.FileName = $executable; $info.WorkingDirectory = $repoRoot;
        $info.Arguments = (@($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ');
        $info.UseShellExecute = $false; $info.CreateNoWindow = $true;
        $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true;
        $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false); $info.StandardErrorEncoding = [Text.UTF8Encoding]::new($false);
        $info.EnvironmentVariables.Clear();
        foreach ($name in @('SystemRoot','WINDIR','TEMP','TMP','PATH','PATHEXT','LOCALAPPDATA','USERPROFILE')) {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process');
            if ($null -ne $value) { $info.EnvironmentVariables[$name] = $value; }
        }
        $info.EnvironmentVariables['PGCONNECT_TIMEOUT'] = '5';
        $info.EnvironmentVariables['PGCLIENTENCODING'] = 'UTF8';
        foreach ($name in $Environment.Keys) { $info.EnvironmentVariables[$name] = [string]$Environment[$name]; }
        $native = [Diagnostics.Process]::new(); $native.StartInfo = $info;
        if (-not $native.Start()) { throw 'OWNED_NATIVE_START_REFUSED'; }
        $recordPath = Join-Path $privateRoot ('native-operations/operation-' + [Guid]::NewGuid().ToString() + '.json');
        $record = [ordered]@{caseId=$caseId;phase=$phase;tool=[IO.Path]::GetFileName($executable);processId=$native.Id;
            processStartedAtUtc=$native.StartTime.ToUniversalTime().ToString('o');startedAtUtc=[DateTime]::UtcNow.ToString('o');
            status='RUNNING';argumentsRecorded=$false;stdinRecorded=$false;stdoutRecorded=$false;stderrRecorded=$false;timeoutSeconds=$TimeoutSeconds};
        Write-PrivateJson $recordPath $record -CreateNew;
        $stdoutTask = $native.StandardOutput.ReadToEndAsync(); $stderrTask = $native.StandardError.ReadToEndAsync();
        if ($null -ne $StandardInput) { $native.StandardInput.WriteLine($StandardInput); }
        $native.StandardInput.Close();
        if (-not $native.WaitForExit($TimeoutSeconds * 1000)) {
            $waitResult = 'OWNED_PROCESS_TIMEOUT';
            if (-not $native.HasExited) { $native.Kill(); [void]$native.WaitForExit(5000); }
            throw 'OWNED_NATIVE_TIMEOUT_KEEP_CASE';
        }
        $nativeExit = $native.ExitCode; $record['exitCode'] = $nativeExit;
        $outReady = $stdoutTask.Wait(2000); $errReady = $stderrTask.Wait(2000);
        $stdout = if ($outReady) { $stdoutTask.Result } else { '' };
        $stderr = if ($errReady) { $stderrTask.Result } else { '' };
        if (-not $outReady -or -not $errReady) {
            $waitResult = 'OWNED_PROCESS_EXITED_INHERITED_CAPTURE_HANDLES_CLOSED';
            $native.StandardOutput.Close(); $native.StandardError.Close();
            if ([IO.Path]::GetFileName($executable) -ine 'pg_ctl.exe' -or $nativeExit -ne 0) { throw 'NATIVE_CAPTURE_DRAIN_TIMEOUT_KEEP_CASE'; }
        } else { $waitResult = 'OWNED_PROCESS_EXITED_AND_CAPTURE_DRAINED'; }
        $lines = @(($stdout + $stderr) -split '\r?\n' | Where-Object { $_ -ne '' });
        if ($nativeExit -ne 0) {
            foreach ($line in $lines) {
                if ($line -match '"check"\s*:\s*"([A-Z][A-Z0-9_]{2,100})"') { $script:nativeCheck = $matches[1]; }
                if ($line -match '(?:^|;\s)CHECK=([A-Z][A-Z0-9_]{2,100})(?:;|$)') { $script:nativeCheck = $matches[1]; }
                if ($line -match '"sqlState"\s*:\s*"([A-Z0-9]{5})"') { $script:nativeSqlState = $matches[1]; }
                if ($line -match 'ERROR:\s+([A-Z0-9]{5})(?:\s|$)') { $script:nativeSqlState = $matches[1]; }
            }
            $record['safeCheck']=$script:nativeCheck; $record['sqlState']=$script:nativeSqlState;
            throw 'NATIVE_NONZERO_KEEP_CAP25_CASE';
        }
        $record['status'] = 'COMPLETED';
        return @($lines | ForEach-Object { $_.ToString() });
    } finally {
        if ($null -ne $record) {
            if ($record['status'] -eq 'RUNNING') { $record['status'] = 'STOPPED_KEEP_CASE'; }
            $record['finishedAtUtc'] = [DateTime]::UtcNow.ToString('o'); $record['waitResult'] = $waitResult;
            Write-PrivateJson $recordPath $record;
        }
        if ($null -ne $native) {
            try { if (-not $native.HasExited) { $native.Kill(); [void]$native.WaitForExit(5000); } } catch { }
            foreach ($stream in @('StandardInput','StandardOutput','StandardError')) { try { $native.$stream.Close(); } catch { } }
            foreach ($name in $Environment.Keys) { $native.StartInfo.EnvironmentVariables.Remove($name); }
            $native.Dispose();
        }
    }
}
function Read-CapCredentials {
    Assert-PhysicalAncestors $credentialsPath;
    $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($credentialsPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
    try {
        $decoded = [Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json;
        if ((@($decoded.PSObject.Properties.Name | Sort-Object) -join ',') -ne 'rf_cap_owner,rf_migrator,rf_test') { throw 'EXACT_CAP25_DPAPI_ROLE_SET_REQUIRED'; }
        $values = @{};
        foreach ($role in @('rf_cap_owner','rf_migrator','rf_test')) {
            $value = [string]$decoded.$role;
            if ($value -notmatch '^[A-Za-z0-9+/]{43}=$' -or [Convert]::FromBase64String($value).Length -ne 32) { throw 'DPAPI_CREDENTIAL_FORMAT_REFUSED'; }
            $values[$role] = $value;
        }
        return $values;
    } finally { [Array]::Clear($plain, 0, $plain.Length); }
}
function Assert-NoPendingNative {
    $directory = Join-Path $privateRoot 'native-operations';
    if (-not (Test-Path -LiteralPath $directory)) { return; }
    foreach ($file in @(Get-ChildItem -LiteralPath $directory -File -Filter 'operation-*.json')) {
        $record = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json;
        if ($record.caseId -ne $caseId) { throw 'FOREIGN_NATIVE_OPERATION_REFUSED'; }
        $running = Get-Process -Id ([int]$record.processId) -ErrorAction SilentlyContinue;
        if ($null -ne $running -and [Math]::Abs(($running.StartTime.ToUniversalTime() - [DateTime]::Parse($record.processStartedAtUtc).ToUniversalTime()).TotalSeconds) -lt 1) { throw 'OWNED_NATIVE_OPERATION_STILL_RUNNING'; }
    }
}
function Get-CapClusterState {
    if (-not (Test-Path -LiteralPath (Join-Path $dataRoot 'PG_VERSION')) -or [IO.File]::ReadAllText((Join-Path $dataRoot 'PG_VERSION')).Trim() -ne '16') { throw 'OWNED_INITIALIZED_POSTGRES16_CLUSTER_REQUIRED'; }
    $listeners = @(Get-NetTCPConnection -LocalPort 55439 -State Listen -ErrorAction SilentlyContinue);
    $pidPath = Join-Path $dataRoot 'postmaster.pid';
    if (-not (Test-Path -LiteralPath $pidPath)) {
        if ($listeners.Count -gt 0) { throw 'FOREIGN_LISTENER_WITHOUT_OWNED_PIDFILE'; }
        return 'STOPPED_SAME_CLUSTER';
    }
    Assert-PhysicalAncestors $pidPath;
    $lines = [IO.File]::ReadAllLines($pidPath);
    if ($lines.Length -lt 4 -or $lines[0] -notmatch '^[1-9][0-9]*$' -or $lines[2] -notmatch '^[0-9]+$' -or
        [IO.Path]::GetFullPath($lines[1]) -ine [IO.Path]::GetFullPath($dataRoot) -or $lines[3].Trim() -ne '55439') { throw 'EXACT_CAP25_PIDFILE_REQUIRED'; }
    $running = Get-Process -Id ([int]$lines[0]) -ErrorAction SilentlyContinue;
    if ($null -eq $running) {
        if ($listeners.Count -gt 0) { throw 'FOREIGN_PORT_OWNER_REFUSED'; }
        return 'STOPPED_SAME_CLUSTER_STALE_PID_PRESERVED';
    }
    $started = [DateTimeOffset]::FromUnixTimeSeconds([long]$lines[2]).UtcDateTime;
    if ($running.ProcessName -ine 'postgres' -or [Math]::Abs(($running.StartTime.ToUniversalTime() - $started).TotalSeconds) -gt 10 -or
        $listeners.Count -ne 1 -or $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne [int]$lines[0]) { throw 'OWNED_POSTMASTER_LOOPBACK_IDENTITY_REQUIRED'; }
    return 'RUNNING_SAME_CLUSTER';
}
function Invoke-CapSql([string]$Sql, [string]$Database = 'postgres', [string]$Role = 'rf_cap_owner') {
    if ($Database -notin @('postgres','ruta_fija_test') -or $Role -notin @('rf_cap_owner','rf_migrator','rf_test')) { throw 'CLOSED_CAP25_SQL_TARGET_REQUIRED'; }
    return @(Invoke-CapNative (Join-Path $pgBin 'psql.exe') @('-X','-q','-A','-t','-P','pager=off','-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','--no-password','-h','127.0.0.1','-p','55439','-U',$Role,'-d',$Database,'-f','-') $Sql @{PGPASSWORD=$credentials[$Role]} 45);
}
function Assert-CapServerIdentity {
    if ((Get-CapClusterState) -ne 'RUNNING_SAME_CLUSTER') { throw 'OWNED_CAP25_POSTMASTER_REQUIRED'; }
    $rows = @(Invoke-CapSql "select current_user||'|'||current_database()||'|'||current_setting('port')||'|'||current_setting('data_directory')||'|'||current_setting('listen_addresses')||'|'||current_setting('server_version_num')||'|'||current_setting('max_connections');");
    if ($rows.Count -ne 1) { throw 'EXACT_CAP25_SERVER_IDENTITY_REQUIRED'; }
    $part = $rows[0].Split('|');
    if ($part.Count -ne 7 -or $part[0] -ne 'rf_cap_owner' -or $part[1] -ne 'postgres' -or $part[2] -ne '55439' -or
        [IO.Path]::GetFullPath($part[3]) -ine [IO.Path]::GetFullPath($dataRoot) -or $part[4] -ne '127.0.0.1' -or
        [int]$part[5] -lt 160000 -or [int]$part[5] -ge 170000 -or $part[6] -ne '80') { throw 'CAP25_SERVER_IDENTITY_MISMATCH'; }
}
function New-CapEnvironment {
    return @{
        RF_L08_CAP25_AUTHORIZATION='L08_CAP25_01_AUTHORIZED'; RF_L08_CAP25_ROOT=$repoRoot;
        RF_L08_CAP25_URL='jdbc:postgresql://127.0.0.1:55439/ruta_fija_test';
        RF_L08_CAP25_TEST_PASSWORD=$credentials['rf_test']; RF_L08_CAP25_MIGRATOR_PASSWORD=$credentials['rf_migrator'];
        RF_L08_CAP25_JWT=[Convert]::ToBase64String($jwtBytes);
    };
}

try {
    foreach ($path in @($privateRoot,$clusterRoot,$dataRoot,$authPath)) { Assert-PhysicalAncestors $path; }
    if ($Action -eq 'Plan') {
        [ordered]@{caseId=$caseId;action='PLAN_NO_CONNECTION_NO_FILES';database='ruta_fija_test';host='127.0.0.1';port=55439;backendPort=4314;
            privateRelativeDirectory=('backend/.local/l08-cap25-'+$caseId);maxConnections=80;maximumSyntheticAccounts=@{SUPER_ADMIN=1;ADMIN=4;CONDUCTOR=20};
            source5432Access=$false;restore55438Access=$false;restoresOrGlobalCleanup=$false;existingCaseRequiresExplicitResume=$true} | ConvertTo-Json -Depth 5 -Compress;
        return;
    }
    if (-not $Execute) { throw 'EXPLICIT_EXECUTE_REQUIRED'; }
    $phase = 'EXACT_USER_AUTHORIZATION_RECORD';
    $auth = Get-Content -LiteralPath $authPath -Raw -Encoding UTF8 | ConvertFrom-Json;
    if ($auth.id -ne 'L08-CAP25-01' -or $auth.status -ne 'AUTHORIZED_NOT_EXECUTED' -or $auth.caseId -ne $caseId -or
        $auth.proposalSha256 -ine $proposalHash -or $auth.host -ne '127.0.0.1' -or $auth.port -ne 55439 -or $auth.database -ne 'ruta_fija_test' -or
        $auth.backendPort -ne 4314 -or $auth.privateRelativeDirectory -ne ('backend/.local/l08-cap25-' + $caseId) -or
        $auth.maximumSyntheticAccounts.SUPER_ADMIN -ne 1 -or $auth.maximumSyntheticAccounts.ADMIN -ne 4 -or $auth.maximumSyntheticAccounts.CONDUCTOR -ne 20 -or
        -not $auth.simulatedEmailOnly -or -not $auth.simulatedGpsOnly -or $auth.writesTo5432Or55438Authorized -or $auth.resetHistoricalCountersAuthorized) { throw 'EXACT_CAP25_AUTHORIZATION_REQUIRED'; }
    if ((Get-FileHash -LiteralPath (Join-Path $repoRoot 'docs/operacion/L-08-CAP25-01-validacion-aislada-propuesta.md') -Algorithm SHA256).Hash -ine $proposalHash) { throw 'AUTHORIZED_PROPOSAL_CHANGED'; }
    $authHash = (Get-FileHash -LiteralPath $authPath -Algorithm SHA256).Hash.ToLowerInvariant();
    foreach ($tool in @('initdb.exe','pg_ctl.exe','psql.exe')) { if (-not (Test-Path -LiteralPath (Join-Path $pgBin $tool))) { throw 'POSTGRES16_NATIVE_TOOL_REQUIRED'; } }
    Add-Type -AssemblyName System.Security;
    $phase = 'EXISTING_OR_NEW_CASE_GUARDS';
    if (-not (Test-Path -LiteralPath $privateRoot)) {
        if ($Action -ne 'Prepare' -or $Resume) { throw 'NEW_CASE_PREPARE_ONLY_NO_RESUME'; }
        if (@(Get-NetTCPConnection -LocalPort 55439 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { throw 'NEW_CAP25_PORT_OCCUPIED'; }
        Set-PrivateDirectory $privateRoot;
        $manifest = [ordered]@{caseId=$caseId;purpose='L08_CAP25_SYNTHETIC_ISOLATED_25';host='127.0.0.1';port=55439;database='ruta_fija_test';
            dataDirectory=$dataRoot;owner='rf_cap_owner';createdAtUtc=[DateTime]::UtcNow.ToString('o');authorizationSha256=$authHash;
            proposalSha256=$proposalHash;stage='NEW_CASE_NO_CLUSTER';globalCleanupAllowed=$false;source5432Accessed=$false;restore55438Accessed=$false};
        Write-PrivateJson $manifestPath $manifest -CreateNew;
    } else {
        if ($Action -eq 'Prepare' -and -not $Resume) { throw 'EXISTING_CASE_PRESERVE_USE_EXACT_PREPARE_RESUME'; }
        $saved = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json;
        if ($saved.caseId -ne $caseId -or $saved.purpose -ne 'L08_CAP25_SYNTHETIC_ISOLATED_25' -or $saved.host -ne '127.0.0.1' -or
            $saved.port -ne 55439 -or $saved.database -ne 'ruta_fija_test' -or $saved.dataDirectory -ine $dataRoot -or
            $saved.owner -ne 'rf_cap_owner' -or $saved.authorizationSha256 -ine $authHash -or $saved.proposalSha256 -ine $proposalHash) { throw 'EXACT_SAVED_CAP25_MANIFEST_REQUIRED'; }
        $manifest = [ordered]@{}; foreach ($property in $saved.PSObject.Properties) { $manifest[$property.Name] = $property.Value; }
    }
    $launcherLock = [IO.File]::Open((Join-Path $privateRoot 'launcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None);
    Set-PrivateDirectory (Join-Path $privateRoot 'native-operations');
    Assert-NoPendingNative;
    if (-not (Test-Path -LiteralPath $credentialsPath)) {
        if ($manifest['stage'] -ne 'NEW_CASE_NO_CLUSTER' -or (Test-Path -LiteralPath $clusterRoot)) { throw 'MISSING_CREDENTIALS_NEVER_REGENERATE_EXISTING_CLUSTER'; }
        $credentials = @{};
        foreach ($role in @('rf_cap_owner','rf_migrator','rf_test')) { $random = New-RandomBytes 32; try { $credentials[$role] = [Convert]::ToBase64String($random); } finally { [Array]::Clear($random,0,$random.Length); } }
        $bytes = [Text.Encoding]::UTF8.GetBytes(($credentials | ConvertTo-Json -Compress));
        try {
            $encrypted = [Security.Cryptography.ProtectedData]::Protect($bytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
            $stream = [IO.File]::Open($credentialsPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None);
            try { $stream.Write($encrypted, 0, $encrypted.Length); } finally { $stream.Dispose(); }
        } finally { [Array]::Clear($bytes,0,$bytes.Length); }
    } else { $credentials = Read-CapCredentials; }
    $savedBootstrap = Join-Path $clusterRoot 'bootstrap-password.txt';
    if (Test-Path -LiteralPath $savedBootstrap) {
        Assert-PhysicalAncestors $savedBootstrap;
        if ([IO.File]::ReadAllText($savedBootstrap) -cne $credentials['rf_cap_owner']) { throw 'INTERRUPTED_BOOTSTRAP_FILE_MISMATCH_PRESERVE'; }
        $passwordFile = $savedBootstrap;
    }
    if (-not (Test-Path -LiteralPath $jwtPath)) {
        if ($manifest['stage'] -ne 'NEW_CASE_NO_CLUSTER' -or (Test-Path -LiteralPath $clusterRoot)) { throw 'MISSING_SAVED_JWT_NEVER_ROTATE_EXISTING_CASE'; }
        $jwtBytes = New-RandomBytes 32;
        $encryptedJwt = [Security.Cryptography.ProtectedData]::Protect($jwtBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
        $stream = [IO.File]::Open($jwtPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None);
        try { $stream.Write($encryptedJwt, 0, $encryptedJwt.Length); } finally { $stream.Dispose(); }
    } else {
        Assert-PhysicalAncestors $jwtPath;
        $jwtBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($jwtPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
    }
    if ($jwtBytes.Length -ne 32) { throw 'EXACT_SAVED_32_BYTE_JWT_REQUIRED'; }
    $jwtDigest = [Security.Cryptography.SHA256]::Create();
    try { $jwtHash = ([BitConverter]::ToString($jwtDigest.ComputeHash($jwtBytes))).Replace('-','').ToLowerInvariant(); } finally { $jwtDigest.Dispose(); }
    if ($manifest.Contains('ephemeralJwtSha256') -and $manifest['ephemeralJwtSha256'] -ne $jwtHash) { throw 'SAVED_JWT_FINGERPRINT_CHANGED_REFUSE'; }
    $manifest['ephemeralJwtSha256'] = $jwtHash;
    Write-PrivateJson $manifestPath $manifest;
    if ($Action -eq 'Prepare') {
        $phase = 'PREPARE_COMPILED_SETUP_REQUIRED';
        $classFile = Join-Path $repoRoot 'backend/target/test-classes/pe/rutafija/support/L08CapacityDatabaseSetup.class';
        if (-not (Test-Path -LiteralPath $classFile)) { throw 'COMPILE_CAP25_SETUP_BEFORE_PREPARE'; }
        $dependencies = [IO.File]::ReadAllText((Join-Path $repoRoot 'backend/target/manual-otp-classpath.txt')).Trim();
        $classpath = (Join-Path $repoRoot 'backend/target/test-classes') + ';' + (Join-Path $repoRoot 'backend/target/classes') + ';' + $dependencies;
        Set-PrivateDirectory $clusterRoot;
        if (-not (Test-Path -LiteralPath (Join-Path $dataRoot 'PG_VERSION'))) {
            if (Test-Path -LiteralPath $dataRoot) { throw 'INCOMPLETE_INITDB_PRESERVE_AND_DIAGNOSE'; }
            $phase = 'INITIALIZE_ONLY_NEW_CAP25_POSTGRES16_CLUSTER';
            $passwordFile = Join-Path $clusterRoot 'bootstrap-password.txt';
            if (Test-Path -LiteralPath $passwordFile) { throw 'EXISTING_BOOTSTRAP_FILE_PRESERVE_AND_DIAGNOSE'; }
            [IO.File]::WriteAllText($passwordFile, $credentials['rf_cap_owner'], [Text.UTF8Encoding]::new($false));
            $ignored = @(Invoke-CapNative (Join-Path $pgBin 'initdb.exe') @('-D',$dataRoot,'-U','rf_cap_owner',('--pwfile='+$passwordFile),'--auth=scram-sha-256','--encoding=UTF8','--locale=Spanish_Peru.1252') $null @{} 120);
            Set-ManifestStage 'INITIALIZED_CLUSTER';
        }
        if ([IO.File]::ReadAllText((Join-Path $dataRoot 'PG_VERSION')).Trim() -ne '16') { throw 'OWNED_POSTGRES16_VERSION_REQUIRED'; }
        $phase = 'CLOSED_LOOPBACK_DURABLE_CAP25_CONFIGURATION';
        $configuration = "`n# L08-CAP25-01 owned configuration`nlisten_addresses='127.0.0.1'`nport=55439`nmax_connections=80`nshared_buffers='128MB'`nlogging_collector=off`nlog_statement='none'`nlog_min_error_statement='panic'`nlog_error_verbosity='terse'`nfsync=on`nsynchronous_commit=on`nfull_page_writes=on`ntimezone='UTC'`n";
        $configPath = Join-Path $dataRoot 'postgresql.conf';
        $existing = [IO.File]::ReadAllText($configPath);
        if (-not $existing.Contains('# L08-CAP25-01 owned configuration')) { [IO.File]::AppendAllText($configPath, $configuration, [Text.UTF8Encoding]::new($false)); }
        elseif (-not $existing.EndsWith($configuration)) { throw 'SAVED_OWNED_CONFIGURATION_CHANGED'; }
        Set-ManifestStage 'CONFIGURED_CLUSTER';
        if ((Get-CapClusterState) -ne 'RUNNING_SAME_CLUSTER') {
            $phase = 'START_ONLY_OWNED_CAP25_55439_CLUSTER';
            $ignored = @(Invoke-CapNative (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'-l',(Join-Path $clusterRoot 'postgresql.log'),'start','-w','-t','30') $null @{} 45);
        }
        $phase = 'VERIFY_LIVE_CAP25_SERVER_IDENTITY'; Assert-CapServerIdentity;
        Set-ManifestStage 'RUNNING_CLUSTER';
        $phase = 'VERIFY_OR_CREATE_ONLY_MISSING_CAP25_ROLES';
        $rows = @(Invoke-CapSql "select rolname||'|'||rolcanlogin::int||'|'||rolsuper::int||'|'||rolcreaterole::int||'|'||rolcreatedb::int from pg_roles where left(rolname,3)<>'pg_' order by rolname;");
        $expected = @{rf_cap_owner='rf_cap_owner|1|1|1|1';rf_migrator='rf_migrator|1|0|0|0';rf_test='rf_test|1|0|0|0';rf_app='rf_app|0|0|0|0'};
        $present = @{};
        foreach ($row in $rows) { $role = $row.Split('|')[0]; if (-not $expected.ContainsKey($role) -or $row -ne $expected[$role]) { throw 'UNEXPECTED_CAP25_ROLE_OR_PRIVILEGE'; }; $present[$role] = $true; }
        if (-not $present.ContainsKey('rf_cap_owner')) { throw 'CAP25_CLUSTER_OWNER_REQUIRED'; }
        foreach ($role in @('rf_migrator','rf_test')) {
            if ($present.ContainsKey($role)) {
                $authenticated = @(Invoke-CapSql 'select current_user;' 'postgres' $role);
                if ($authenticated.Count -ne 1 -or $authenticated[0] -ne $role) { throw 'EXISTING_ROLE_DPAPI_AUTHENTICATION_REQUIRED'; }
            }
        }
        $statements = [Collections.Generic.List[string]]::new(); $statements.Add('BEGIN;');
        foreach ($role in @('rf_migrator','rf_test')) {
            if (-not $present.ContainsKey($role)) { $statements.Add("CREATE ROLE " + $role + " LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '" + $credentials[$role] + "';"); }
        }
        if (-not $present.ContainsKey('rf_app')) { $statements.Add('CREATE ROLE rf_app NOLOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS;'); }
        $statements.Add('COMMIT;'); $ignored = @(Invoke-CapSql ($statements -join "`n"));
        $phase = 'ONLY_AUTHORIZED_NEW_DATABASE_OR_EXACT_EXISTING_DATABASE';
        $databases = @(Invoke-CapSql "select datname||'|'||pg_get_userbyid(datdba)||'|'||pg_encoding_to_char(encoding)||'|'||datcollate||'|'||datctype from pg_database where datname not in('postgres','template0','template1') order by datname;");
        if ($databases.Count -eq 0) {
            $ignored = @(Invoke-CapSql "CREATE DATABASE ruta_fija_test OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'Spanish_Peru.1252' LC_CTYPE 'Spanish_Peru.1252' LOCALE_PROVIDER libc;");
        } elseif ($databases.Count -ne 1 -or $databases[0] -ne 'ruta_fija_test|rf_migrator|UTF8|Spanish_Peru.1252|Spanish_Peru.1252') { throw 'UNEXPECTED_DATABASE_IN_CAP25_CLUSTER'; }
        $ignored = @(Invoke-CapSql 'REVOKE CREATE ON SCHEMA public FROM PUBLIC;' 'ruta_fija_test');
        Set-ManifestStage 'DATABASE_PRESENT_PRESERVED';
        $phase = 'MIGRATE_EMPTY_OR_VERIFY_EXACT_EXISTING_V50_NO_RESET';
        $tables = @(Invoke-CapSql "select count(*) from pg_tables where schemaname='public';" 'ruta_fija_test');
        if ($tables.Count -ne 1 -or $tables[0] -notmatch '^[0-9]+$') { throw 'EXACT_SCHEMA_INSPECTION_REQUIRED'; }
        $setupAction = if ($tables[0] -eq '0') { '--migrate-new-v50' } else { '--verify-v50' };
        $environment = New-CapEnvironment;
        try { $text = @(Invoke-CapNative 'java' @('-Xms32m','-Xmx384m','-cp',$classpath,'pe.rutafija.support.L08CapacityDatabaseSetup',$setupAction) $null $environment 360); }
        finally { foreach ($name in @($environment.Keys)) { $environment[$name] = $null; }; $environment.Clear(); }
        $setup = ($text -join [Environment]::NewLine) | ConvertFrom-Json;
        if ($setup.result -ne 'PASS_CAP25_ISOLATED_V50_SCHEMA_AND_ROLE_BOUNDARIES' -or $setup.caseId -ne $caseId -or $setup.schemaVersion -ne '50') { throw 'SETUP_RESULT_NOT_ACCREDITED_KEEP_CASE'; }
        $setupFile = Join-Path $privateRoot ('setup-verification-' + [Guid]::NewGuid().ToString() + '.json');
        Write-PrivateJson $setupFile $setup -CreateNew;
        $manifest['lastSetupResultFile'] = [IO.Path]::GetFileName($setupFile);
        $manifest['schemaVersion'] = '50'; Set-ManifestStage 'SCHEMA_READY_NO_DATASET_IMPLIED';
        Write-Output ('L08_CAP25_CASE_ID=' + $caseId);
        Write-Output 'L08_CAP25_INFRASTRUCTURE=PASS_ISOLATED_V50_READY_NO_LOAD_RESULT';
        Write-Output 'L08_CAP25_LEDGER_PRESERVED=true';
    } elseif ($Action -eq 'Run') {
        $phase = 'VERIFY_EXISTING_PREPARED_CAP25_CASE_BEFORE_DATASET';
        if (-not $manifest.Contains('schemaVersion') -or $manifest['schemaVersion'] -ne '50') { throw 'EXACT_SCHEMA_READY_MANIFEST_REQUIRED'; }
        foreach ($file in @('dataset-state.json','dataset.active','dataset.private')) {
            if ((Test-Path -LiteralPath (Join-Path $privateRoot $file)) -and -not $Resume) { throw 'EXISTING_DATASET_REQUIRES_EXACT_RUN_RESUME_NO_RESET'; }
        }
        Assert-CapServerIdentity;
        if (@(Get-NetTCPConnection -LocalPort 4314 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { throw 'DATASET_API_PORT_ALREADY_OCCUPIED_PRESERVE'; }
        foreach ($className in @('L08CapacityDatabaseSetup','L08CapacityTrial')) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot ('backend/target/test-classes/pe/rutafija/support/' + $className + '.class')))) { throw 'COMPILE_CURRENT_CAP25_RUNNER_BEFORE_RUN'; }
        }
        $dependencies = [IO.File]::ReadAllText((Join-Path $repoRoot 'backend/target/manual-otp-classpath.txt')).Trim();
        $classpath = (Join-Path $repoRoot 'backend/target/test-classes') + ';' + (Join-Path $repoRoot 'backend/target/classes') + ';' + $dependencies;
        $environment = New-CapEnvironment;
        try {
            $phase = 'VERIFY_V50_AND_PERMISSIONS_NO_MIGRATION_OR_CLEANUP';
            $verifiedText = @(Invoke-CapNative 'java' @('-Xms32m','-Xmx384m','-cp',$classpath,'pe.rutafija.support.L08CapacityDatabaseSetup','--verify-v50') $null $environment 180);
            $verified = ($verifiedText -join [Environment]::NewLine) | ConvertFrom-Json;
            if ($verified.result -ne 'PASS_CAP25_ISOLATED_V50_SCHEMA_AND_ROLE_BOUNDARIES' -or $verified.migrationsExecutedThisInvocation -ne 0) { throw 'READ_ONLY_SCHEMA_VALIDATION_REQUIRED_BEFORE_RUN'; }
            Write-PrivateJson (Join-Path $privateRoot ('pre-run-schema-' + [Guid]::NewGuid().ToString() + '.json')) $verified -CreateNew;
            $phase = 'RUN_EXACT_AUTHORIZED_SYNTHETIC_CAP25_DATASET';
            Set-ManifestStage 'RUNNING_DATASET';
            $runAction = if ($Resume) { '--resume-capacity' } else { '--run-capacity' };
            $runOutput = @(Invoke-CapNative 'java' @('-Xms64m','-Xmx768m','-cp',$classpath,'pe.rutafija.support.L08CapacityTrial',$runAction) $null $environment 1800);
            Set-ManifestStage 'RUN_EXIT_0_DATASET_EVIDENCE_REQUIRES_REVIEW';
            foreach ($line in $runOutput) {
                if ($line -match '^(L08_CAP25_[A-Z_]+=|CAP25_[A-Z_]+=|EVIDENCE_FILE=|EVIDENCE_SHA256=|TEST_CLEANUP_RELEASED=)[A-Za-z0-9_./\\:= -]{1,400}$') { Write-Output $line; }
            }
            Write-Output 'L08_CAP25_PROCESS=EXIT_0_REVIEW_DATASET_EVIDENCE_NOT_AUTOMATIC_PASS';
        } finally { foreach ($name in @($environment.Keys)) { $environment[$name] = $null; }; $environment.Clear(); }
    } elseif ($Action -eq 'Inspect') {
        $phase='READ_ONLY_POSTFLIGHT_OF_SAME_RETIRED_DATASET';
        Assert-CapServerIdentity;
        if (Test-Path -LiteralPath (Join-Path $privateRoot 'dataset.active')) { throw 'DATASET_MARKER_NOT_RELEASED_KEEP_CASE'; }
        if (@(Get-NetTCPConnection -LocalPort 4314 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { throw 'DATASET_API_MUST_BE_CLOSED_BEFORE_POSTFLIGHT'; }
        $dependencies=[IO.File]::ReadAllText((Join-Path $repoRoot 'backend/target/manual-otp-classpath.txt')).Trim();
        $classpath=(Join-Path $repoRoot 'backend/target/test-classes')+';'+(Join-Path $repoRoot 'backend/target/classes')+';'+$dependencies;
        $environment=New-CapEnvironment;
        try { $inspection=@(Invoke-CapNative 'java' @('-Xms32m','-Xmx256m','-cp',$classpath,'pe.rutafija.support.L08CapacityPostflight') $null $environment 90); }
        finally { foreach($name in @($environment.Keys)){$environment[$name]=$null;};$environment.Clear(); }
        foreach($line in $inspection){if($line -match '^(CAP25_POSTFLIGHT=|EVIDENCE_FILE=|EVIDENCE_SHA256=)'){Write-Output $line;}}
    } elseif ($Action -eq 'Stop') {
        $phase = 'ONLY_OWNED_CAP25_CLUSTER_STOP_WITH_DATA_PRESERVED';
        if (Test-Path -LiteralPath (Join-Path $privateRoot 'dataset.active')) { throw 'DATASET_PROTECTION_ACTIVE_DO_NOT_FORCE_STOP'; }
        if (@(Get-NetTCPConnection -LocalPort 4314 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { throw 'CAP25_API_STILL_RUNNING_DO_NOT_FORCE_STOP'; }
        $clusterState = Get-CapClusterState;
        if ($clusterState -eq 'RUNNING_SAME_CLUSTER') {
            Assert-CapServerIdentity;
            $clients = @(Invoke-CapSql "select count(*) from pg_stat_activity where backend_type='client backend' and pid<>pg_backend_pid();");
            if ($clients.Count -ne 1 -or $clients[0] -ne '0') { throw 'CAP25_CLIENTS_STILL_ACTIVE_DO_NOT_STOP'; }
            $ignored = @(Invoke-CapNative (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'stop','-m','fast','-w','-t','30') $null @{} 45);
            if ((Get-CapClusterState) -eq 'RUNNING_SAME_CLUSTER') { throw 'OWNED_CLUSTER_STOP_NOT_CONFIRMED'; }
            Set-ManifestStage 'STOPPED_DATA_AND_HISTORY_PRESERVED';
            Write-Output 'L08_CAP25_CLUSTER=STOPPED_OWNED_55439_DATA_PRESERVED';
        } else {
            Write-Output 'L08_CAP25_CLUSTER=ALREADY_STOPPED_SAME_DATA_PRESERVED';
        }
    }
} catch {
    Write-Output 'L08_CAP25_INFRASTRUCTURE=STOPPED_KEEP_CASE_NO_RESET';
    Write-Output ('L08_CAP25_PHASE=' + $phase);
    Write-Output ('L08_CAP25_NATIVE_CHECK=' + $nativeCheck);
    Write-Output ('L08_CAP25_SQLSTATE=' + $nativeSqlState);
    Write-Output ('L08_CAP25_FAILURE_TYPE=' + $_.Exception.GetType().Name);
    Write-Output ('L08_CAP25_SCRIPT_LINE=' + $_.InvocationInfo.ScriptLineNumber);
    if ($_.Exception.Message -match '^[A-Z][A-Z0-9_]{2,100}$') { Write-Output ('L08_CAP25_CHECK=' + $_.Exception.Message); }
    exit 1;
} finally {
    if ($null -ne $passwordFile -and (Test-Path -LiteralPath $passwordFile)) {
        $expected = [IO.Path]::GetFullPath((Join-Path $clusterRoot 'bootstrap-password.txt'));
        if ([IO.Path]::GetFullPath($passwordFile) -eq $expected -and $null -ne $credentials -and
            [IO.File]::ReadAllText($passwordFile) -ceq $credentials['rf_cap_owner']) {
            Assert-PhysicalAncestors $passwordFile;
            Remove-Item -LiteralPath $passwordFile -ErrorAction Stop;
        } else { Write-Output 'L08_CAP25_BOOTSTRAP_FILE=KEEP_EXACT_FILE_REQUIRES_DIAGNOSIS'; }
    }
    if ($null -ne $jwtBytes) { [Array]::Clear($jwtBytes,0,$jwtBytes.Length); }
    if ($null -ne $credentials) { foreach ($name in @($credentials.Keys)) { $credentials[$name] = $null; }; $credentials.Clear(); }
    if ($null -ne $launcherLock) { $launcherLock.Dispose(); }
}
