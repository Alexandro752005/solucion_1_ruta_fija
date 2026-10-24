[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $Execute) {
    Write-Output 'L08_CANDIDATE_API=PLAN_ONLY_NO_CONNECTION_NO_FILES';
    Write-Output 'DESTINATION=ruta_fija_test@127.0.0.1:5432';
    Write-Output 'BINARY=PACKAGED_V50_READ_ONLY_HEALTH_ONLY';
    return;
}

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot));
$privateRoot = Join-Path $repoRoot 'backend/.local';
$jar = Join-Path $repoRoot 'artifacts/L-08-post-cap25-2026-10-22/ruta-fija-l08-local.jar';
$expectedJarHash = 'eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f';
$output = Join-Path $repoRoot 'docs/evidencia/L-08/incremento-2-mision-y-controles/candidato-v50-solo-lectura.json';
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
    $info.EnvironmentVariables.Clear();
    foreach($name in @('SystemRoot','WINDIR','TEMP','TMP','PATH','PATHEXT')){
        $value=[Environment]::GetEnvironmentVariable($name,'Process');
        if($null -ne $value){$info.EnvironmentVariables[$name]=$value;}
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
    foreach($marker in @('l08-connected.active','l01-human-trial.active','l07-connected.active')) {
        if(Test-Path -LiteralPath (Join-Path $privateRoot $marker)){throw 'SHARED_TEST_CASE_PROTECTED';}
    }
    $launcherLock = [IO.File]::Open((Join-Path $privateRoot 'candidate-readonly.launcher.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None);
    $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 4315);
    try { $socket.Start(); } finally { $socket.Stop(); }

    $phase = 'EXTRACT_EXACT_PRESERVED_BINARY_ONLY';
    $work = Join-Path $repoRoot ('backend/target/l08-candidate-api-v50-' + [Guid]::NewGuid().ToString());
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
        (Join-Path $repoRoot 'backend/src/test/java/pe/rutafija/support/L08CandidateApiReadOnlyProbe.java'),
        (Join-Path $repoRoot 'backend/src/main/java/pe/rutafija/backup/DatabaseIntegrity.java')) (Join-Path $work 'compile.log') 120 @{};

    $phase = 'LOAD_CASE_CREDENTIALS_WITHOUT_PARENT_ENV_OR_ARGUMENTS';
    $credential = & (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -Scope Test -PassThru;
    if($credential.RF_TEST_DATASOURCE_URL -ne 'jdbc:postgresql://127.0.0.1:5432/ruta_fija_test' -or $credential.RF_TEST_DATASOURCE_USERNAME -ne 'rf_test') { throw 'EXACT_SHARED_TEST_CONFIGURATION_REQUIRED'; }
    if($credential.RF_TEST_MIGRATOR_URL -ne $credential.RF_TEST_DATASOURCE_URL -or $credential.RF_TEST_MIGRATOR_USERNAME -ne 'rf_migrator'){throw 'EXACT_TEST_MIGRATOR_REQUIRED';}
    $jwtBytes = New-Object byte[] 32;
    $random = [Security.Cryptography.RandomNumberGenerator]::Create();
    try { $random.GetBytes($jwtBytes); } finally { $random.Dispose(); }
    $childEnvironment = @{
        RF_L08_CANDIDATE_API_AUTHORIZATION = 'READ_ONLY_V50_API_SHARED_TEST';
        RF_L08_CANDIDATE_API_URL = 'jdbc:postgresql://127.0.0.1:5432/ruta_fija_test';
        RF_L08_CANDIDATE_API_ROOT = $repoRoot;
        RF_L08_CANDIDATE_API_APPLICATION_CLASSES = $appClasses;
        RF_L08_CANDIDATE_API_PROBE_CLASSES = $probeClasses;
        RF_L08_CANDIDATE_API_TEST_PASSWORD = [string]$credential.RF_TEST_DATASOURCE_PASSWORD;
        RF_L08_CANDIDATE_API_MIGRATOR_PASSWORD = [string]$credential.RF_TEST_MIGRATOR_PASSWORD;
        RF_L08_CANDIDATE_API_EPHEMERAL_JWT = [Convert]::ToBase64String($jwtBytes);
    };
    $phase = 'READ_ONLY_API_ON_EXISTING_RESTORE_ONLY';
    $result = Invoke-BoundedNative 'java' @('-Xms32m', '-Xmx384m', '-cp', ($probeClasses + ';' + $classpath), 'pe.rutafija.support.L08CandidateApiReadOnlyProbe') (Join-Path $work 'execution.log') 240 $childEnvironment;
    foreach ($line in ($result -split "`r?`n")) {
        if ($line -match '^(L08_CANDIDATE_API=|EVIDENCE_FILE=|EVIDENCE_SHA256=)') { Write-Output $line; }
    }
    if (-not (Test-Path -LiteralPath $output)) { throw 'EXPECTED_NEW_EVIDENCE_MISSING'; }
    Write-Output 'L08_CANDIDATE_API_PROCESS=EXIT_0_CONTEXT_CLOSED';
} catch {
    Write-Output 'L08_CANDIDATE_API=STOPPED_KEEP_EXISTING_RESTORE';
    Write-Output ('L08_CANDIDATE_API_LAUNCHER_PHASE=' + $phase);
    exit 1;
} finally {
    if ($null -ne $plain) { [Array]::Clear($plain, 0, $plain.Length); }
    if ($null -ne $jwtBytes) { [Array]::Clear($jwtBytes, 0, $jwtBytes.Length); }
    if($null -ne $credential){foreach($key in @($credential.Keys)){$credential[$key]=$null;};$credential.Clear();};
    $credential = $null;
    foreach ($name in @($childEnvironment.Keys)) { $childEnvironment[$name] = $null; }
    $childEnvironment.Clear();
    if ($null -ne $launcherLock) { $launcherLock.Dispose(); }
}
