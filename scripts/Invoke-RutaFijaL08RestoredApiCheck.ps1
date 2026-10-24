[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $Execute) {
    Write-Output 'L08_RESTORED_API=PLAN_ONLY_NO_CONNECTION_NO_FILES';
    Write-Output 'DESTINATION=ruta_fija_test_l08_locale@127.0.0.1:55438';
    Write-Output 'BINARY=L07_PRESERVED_V48_READ_ONLY_HEALTH_ONLY';
    return;
}

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));
$privateRoot = Join-Path $repoRoot 'backend/.local/l08-restore-c748e5ab-c20a-43d6-9c17-af5d0a703dab';
$jar = Join-Path $repoRoot 'artifacts/L-07-cierre-2026-10-21/ruta-fija-backend.jar';
$expectedJarHash = '23a949ace14161e7e3d7110be31836cc03e26c1df59549fa78e303738d5abaa1';
$output = Join-Path $repoRoot 'docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/arranque-v48-solo-lectura.json';
$phase = 'PREFLIGHT_NO_CONNECTION';
$plain = $null; $credential = $null; $jwtBytes = $null; $process = $null; $launcherLock = $null;
$childEnvironment = @{};

function ConvertTo-NativeArgument([string]$Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value; }
    $quoted = [regex]::Replace($Value, '(\\*)"', '$1$1\"');
    $quoted = [regex]::Replace($quoted, '(\\+)$', '$1$1');
    return '"' + $quoted + '"';
}

function Invoke-BoundedNative([string]$Executable, [string[]]$Arguments, [string]$LogPath, [int]$TimeoutSeconds, [hashtable]$ExtraEnvironment) {
    $info = [Diagnostics.ProcessStartInfo]::new();
    $info.FileName = $Executable;
    $info.Arguments = (($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ');
    $info.WorkingDirectory = $repoRoot;
    $info.UseShellExecute = $false;
    $info.CreateNoWindow = $true;
    $info.RedirectStandardOutput = $true;
    $info.RedirectStandardError = $true;
    foreach ($name in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH')) { $info.EnvironmentVariables.Remove($name); }
    foreach ($name in @($info.EnvironmentVariables.Keys)) {
        if ([string]$name -match '^(SPRING_MAIL_|RF_L08_RESTORED_API_)') { $info.EnvironmentVariables.Remove([string]$name); }
    }
    foreach ($name in $ExtraEnvironment.Keys) { $info.EnvironmentVariables[$name] = [string]$ExtraEnvironment[$name]; }
    $native = [Diagnostics.Process]::new(); $native.StartInfo = $info;
    try {
        if (-not $native.Start()) { throw 'NATIVE_START_REFUSED'; }
        $stdout = $native.StandardOutput.ReadToEndAsync();
        $stderr = $native.StandardError.ReadToEndAsync();
        if (-not $native.WaitForExit($TimeoutSeconds * 1000)) {
            try { $native.Kill(); $native.WaitForExit(10000) | Out-Null; } catch { }
            throw 'OWNED_PROCESS_TIMEOUT_KEEP_CASE';
        }
        $safeOutput = $stdout.GetAwaiter().GetResult();
        $safeError = $stderr.GetAwaiter().GetResult();
        $encoded = [Text.UTF8Encoding]::new($false);
        $stream = [IO.File]::Open($LogPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read);
        try {
            $writer = [IO.StreamWriter]::new($stream, $encoded);
            try { $writer.Write($safeOutput); $writer.Write($safeError); $writer.Flush(); } finally { $writer.Dispose(); }
        } finally { $stream.Dispose(); }
        if ($native.ExitCode -ne 0) { throw 'NATIVE_NONZERO_KEEP_CASE'; }
        return $safeOutput;
    } finally {
        foreach ($name in $ExtraEnvironment.Keys) { $info.EnvironmentVariables.Remove($name); }
        $native.Dispose();
    }
}

try {
    if (Test-Path -LiteralPath $output) { throw 'EVIDENCE_ALREADY_EXISTS_KEEP_RESULT'; }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ine $expectedJarHash) { throw 'PRESERVED_JAR_MISMATCH'; }
    foreach ($path in @($privateRoot, (Join-Path $privateRoot 'cluster/data'), (Join-Path $privateRoot 'target-credentials.dpapi'), (Join-Path $privateRoot 'l08-locale-isolated-restore.json'))) {
        if (-not (Test-Path -LiteralPath $path)) { throw 'EXISTING_APPROVED_CASE_REQUIRED'; }
        if (((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'REPARSE_TARGET_REFUSED'; }
    }
    $manifest = Get-Content -LiteralPath (Join-Path $privateRoot 'l08-locale-isolated-restore.json') -Raw -Encoding UTF8 | ConvertFrom-Json;
    if ($manifest.caseId -ne 'c748e5ab-c20a-43d6-9c17-af5d0a703dab' -or $manifest.database -ne 'ruta_fija_test_l08_locale') { throw 'APPROVED_TARGET_MISMATCH'; }
    $dataRoot = [IO.Path]::GetFullPath((Join-Path $privateRoot 'cluster/data'));
    $pidLines = [IO.File]::ReadAllLines((Join-Path $dataRoot 'postmaster.pid'));
    if ($pidLines.Length -lt 8 -or [IO.Path]::GetFullPath($pidLines[1]) -ine $dataRoot -or $pidLines[3].Trim() -ne '55438' -or $pidLines[7].Trim() -ne 'ready') { throw 'OWNED_RUNNING_CLUSTER_REQUIRED'; }
    $ownedProcess = Get-Process -Id ([int]$pidLines[0]) -ErrorAction Stop;
    if ($ownedProcess.ProcessName -ne 'postgres') { throw 'OWNED_POSTGRES_PROCESS_REQUIRED'; }
    $expectedStart = [DateTimeOffset]::FromUnixTimeSeconds([long]$pidLines[2]).UtcDateTime;
    if ([Math]::Abs(($ownedProcess.StartTime.ToUniversalTime() - $expectedStart).TotalSeconds) -gt 15) { throw 'OWNED_POSTGRES_START_MISMATCH'; }
    $proof = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/reconciliacion-snapshot-collation.json') -Raw -Encoding UTF8 | ConvertFrom-Json;
    if ($proof.result -ne 'PASS_READ_ONLY_RECONCILED_SQL_SNAPSHOT_INTEGRITY_NOT_SERVICE_RTO') { throw 'SQL_INTEGRITY_PROOF_REQUIRED'; }
    $launcherLock = [IO.File]::Open((Join-Path $privateRoot 'api-readonly.launcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None);
    $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 4313);
    try { $socket.Start(); } finally { $socket.Stop(); }

    $phase = 'EXTRACT_EXACT_PRESERVED_BINARY_ONLY';
    $work = Join-Path $repoRoot ('backend/target/l08-restored-api-v48-' + [Guid]::NewGuid().ToString());
    New-Item -ItemType Directory -Path $work -ErrorAction Stop | Out-Null;
    $appClasses = Join-Path $work 'application-classes';
    $probeClasses = Join-Path $work 'probe-classes';
    $libRoot = Join-Path $work 'lib';
    foreach ($path in @($appClasses, $probeClasses, $libRoot)) { New-Item -ItemType Directory -Path $path -ErrorAction Stop | Out-Null; }
    Add-Type -AssemblyName System.IO.Compression.FileSystem;
    $zip = [IO.Compression.ZipFile]::OpenRead($jar);
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue; }
            $destination = $null;
            if ($entry.FullName.StartsWith('BOOT-INF/classes/')) {
                $relative = $entry.FullName.Substring('BOOT-INF/classes/'.Length);
                $destination = Join-Path $appClasses $relative;
            } elseif ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
                $destination = Join-Path $libRoot ([IO.Path]::GetFileName($entry.FullName));
            }
            if ($null -eq $destination) { continue; }
            $absolute = [IO.Path]::GetFullPath($destination);
            if (-not $absolute.StartsWith($work + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'ARCHIVE_PATH_ESCAPE_REFUSED'; }
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($absolute)) | Out-Null;
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $absolute, $false);
        }
    } finally { $zip.Dispose(); }
    $classpath = $appClasses + ';' + $libRoot + '/*';
    $phase = 'COMPILE_ISOLATED_PROBE_AND_INTEGRITY_HELPER';
    $null = Invoke-BoundedNative 'javac' @('--release', '21', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $probeClasses,
        (Join-Path $repoRoot 'backend/src/test/java/pe/rutafija/support/L08RestoredApiReadOnlyProbe.java'),
        (Join-Path $repoRoot 'backend/src/main/java/pe/rutafija/backup/DatabaseIntegrity.java')) (Join-Path $work 'compile.log') 120 @{};

    $phase = 'LOAD_CASE_CREDENTIALS_WITHOUT_PARENT_ENV_OR_ARGUMENTS';
    Add-Type -AssemblyName System.Security;
    $plain = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $privateRoot 'target-credentials.dpapi')), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser);
    $credential = [Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json;
    if ([string]::IsNullOrWhiteSpace($credential.rf_test) -or [string]::IsNullOrWhiteSpace($credential.rf_migrator)) { throw 'EXACT_CASE_CREDENTIALS_REQUIRED'; }
    $jwtBytes = New-Object byte[] 32;
    $random = [Security.Cryptography.RandomNumberGenerator]::Create();
    try { $random.GetBytes($jwtBytes); } finally { $random.Dispose(); }
    $childEnvironment = @{
        RF_L08_RESTORED_API_AUTHORIZATION = 'READ_ONLY_V48_API_EXISTING_LOCALE_TARGET';
        RF_L08_RESTORED_API_URL = 'jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale';
        RF_L08_RESTORED_API_ROOT = $repoRoot;
        RF_L08_RESTORED_API_APPLICATION_CLASSES = $appClasses;
        RF_L08_RESTORED_API_PROBE_CLASSES = $probeClasses;
        RF_L08_RESTORED_API_TEST_PASSWORD = [string]$credential.rf_test;
        RF_L08_RESTORED_API_MIGRATOR_PASSWORD = [string]$credential.rf_migrator;
        RF_L08_RESTORED_API_EPHEMERAL_JWT = [Convert]::ToBase64String($jwtBytes);
    };
    $phase = 'READ_ONLY_API_ON_EXISTING_RESTORE_ONLY';
    $result = Invoke-BoundedNative 'java' @('-Xms32m', '-Xmx384m', '-cp', ($probeClasses + ';' + $classpath), 'pe.rutafija.support.L08RestoredApiReadOnlyProbe') (Join-Path $work 'execution.log') 240 $childEnvironment;
    foreach ($line in ($result -split "`r?`n")) {
        if ($line -match '^(L08_RESTORED_API=|EVIDENCE_FILE=|EVIDENCE_SHA256=)') { Write-Output $line; }
    }
    if (-not (Test-Path -LiteralPath $output)) { throw 'EXPECTED_NEW_EVIDENCE_MISSING'; }
    Write-Output 'L08_RESTORED_API_PROCESS=EXIT_0_CONTEXT_CLOSED';
} catch {
    Write-Output 'L08_RESTORED_API=STOPPED_KEEP_EXISTING_RESTORE';
    Write-Output ('L08_RESTORED_API_LAUNCHER_PHASE=' + $phase);
    exit 1;
} finally {
    if ($null -ne $plain) { [Array]::Clear($plain, 0, $plain.Length); }
    if ($null -ne $jwtBytes) { [Array]::Clear($jwtBytes, 0, $jwtBytes.Length); }
    $credential = $null;
    foreach ($name in @($childEnvironment.Keys)) { $childEnvironment[$name] = $null; }
    $childEnvironment.Clear();
    if ($null -ne $launcherLock) { $launcherLock.Dispose(); }
}
