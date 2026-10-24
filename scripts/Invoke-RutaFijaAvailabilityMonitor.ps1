[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('ValidationLoopback', 'ProductionCadence')]
    [string]$Mode,
    [Parameter(Mandatory)][string]$ReadinessUrl,
    [Parameter(Mandatory)][string]$LivenessUrl,
    [Parameter(Mandatory)][string]$ApplicationUrl,
    [string]$OutputDirectory,
    [ValidateRange(100, 60000)][int]$ValidationIntervalMilliseconds = 2000,
    [ValidateRange(0, 1000000)][int]$MaxSamples = 0,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# No private .env, credentials, database tools, Spring process, service installation or external alert transport.
# ProductionCadence means 60-second HTTPS samples; it does not certify independent infrastructure or attendance.
$monitorRepoRoot = Split-Path -Parent $PSScriptRoot
$monitorClasses = Join-Path $monitorRepoRoot 'backend\target\classes'
$monitorMain = Join-Path $monitorClasses 'pe\rutafija\availability\monitor\AvailabilityMonitorCli.class'
$monitorClasspathFile = Join-Path $monitorRepoRoot 'backend\target\manual-otp-classpath.txt'

try {
    if (-not (Test-Path -LiteralPath $monitorMain -PathType Leaf) -or
        -not (Test-Path -LiteralPath $monitorClasspathFile -PathType Leaf)) {
        throw 'MONITOR_COMPILED_CLASSES_OR_EXISTING_CLASSPATH_MISSING'
    }
    $monitorDependencies = [IO.File]::ReadAllText($monitorClasspathFile).Trim()
    if ([string]::IsNullOrWhiteSpace($monitorDependencies)) { throw 'MONITOR_CLASSPATH_EMPTY' }
    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path $monitorRepoRoot 'backend\target\l08-availability-monitor'
    }
    if ($Mode -eq 'ProductionCadence' -and $PSBoundParameters.ContainsKey('ValidationIntervalMilliseconds')) {
        throw 'MONITOR_PRODUCTION_INTERVAL_IS_FIXED_AT_60_SECONDS'
    }
    $monitorModeArgument = if ($Mode -eq 'ValidationLoopback') { 'validation-loopback' } else { 'production-cadence' }
    $monitorArguments = @(
        '-Xms32m', '-Xmx128m', '-cp', ($monitorClasses + [IO.Path]::PathSeparator + $monitorDependencies),
        'pe.rutafija.availability.monitor.AvailabilityMonitorCli',
        '--mode', $monitorModeArgument,
        '--readiness', $ReadinessUrl, '--liveness', $LivenessUrl, '--application', $ApplicationUrl,
        '--output', $OutputDirectory, '--max-samples', $MaxSamples.ToString([Globalization.CultureInfo]::InvariantCulture)
    )
    if ($Mode -eq 'ValidationLoopback') {
        $monitorArguments += @('--interval-millis', $ValidationIntervalMilliseconds.ToString([Globalization.CultureInfo]::InvariantCulture))
    }
    if ($ValidateOnly) { $monitorArguments += '--validate-only' }
    & java @monitorArguments
    if ($LASTEXITCODE -ne 0) { throw 'MONITOR_REFUSED_OR_STOPPED_WITH_FAILURE' }
} catch {
    # Do not print supplied URLs, dependency paths or exception messages: they can contain private data.
    Write-Output 'MONITOR_LAUNCHER=STOPPED_SAFE_FAILURE_KEEP_CASE_AND_LOGS'
    exit 1
}
