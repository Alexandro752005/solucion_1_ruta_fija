[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not $Execute){Write-Output 'L08_BATTERY=PLAN_NO_DEVICE_NO_FILES';return}
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$statePath=Join-Path $repoRoot 'backend/.local/l08-fis02-state.json'
$state=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
if($state.runId -ne '3f0ba758-6eb9-4d2f-b948-dd1c7de3c430' -or -not ($state.PSObject.Properties.Name -contains 'batteryStartedAt') -or ($state.PSObject.Properties.Name -contains 'batteryFinishedAt') -or -not $state.batteryNoChargingPersonallyConfirmed -or $state.TEST_CLEANUP_RELEASED){throw 'SAME_PERSONALLY_STARTED_PROTECTED_MEASUREMENT_REQUIRED'}
$network=Get-Content -LiteralPath (Join-Path $repoRoot 'backend/.local/l08-fis02-network.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$device=$network.phoneIpv4+':45108'
$adb='D:\Android\Sdk\platform-tools\adb.exe'
$folder=Join-Path $repoRoot 'docs/evidencia/L-08/incremento-4-cierre-fisico'
$samplesPath=Join-Path $folder 'battery-real-samples.jsonl'
$summaryPath=Join-Path $folder 'battery-real-telemetry.json'
if((Test-Path -LiteralPath $samplesPath) -or (Test-Path -LiteralPath $summaryPath)){throw 'KEEP_EXISTING_BATTERY_OBSERVATION'}
$started=[DateTimeOffset]::Parse($state.batteryStartedAt)
$samples=[Collections.Generic.List[object]]::new()
function Read-BatteryValue([string]$Raw,[string]$Name){
 $m=[regex]::Match($Raw,('(?m)^\s*'+[regex]::Escape($Name)+':\s*(\S+)'))
 if(-not $m.Success){throw 'REAL_BATTERY_FIELD_MISSING'}
 return $m.Groups[1].Value
}
while($true){
 $raw=(& $adb -s $device shell dumpsys battery) -join "`n"
 if($LASTEXITCODE -ne 0 -or $raw -match 'UPDATES STOPPED'){throw 'REAL_UNSIMULATED_BATTERY_REQUIRED_KEEP_EVIDENCE'}
 $ac=(Read-BatteryValue $raw 'AC powered') -eq 'true'
 $usb=(Read-BatteryValue $raw 'USB powered') -eq 'true'
 $wireless=(Read-BatteryValue $raw 'Wireless powered') -eq 'true'
 $percent=[int](Read-BatteryValue $raw 'level')
 $temperature=[int](Read-BatteryValue $raw 'temperature')
 $power=(& $adb -s $device shell dumpsys power) -join "`n"
 $activity=(& $adb -s $device shell dumpsys activity activities) -join "`n"
 $thermal=(& $adb -s $device shell dumpsys thermalservice) -join "`n"
 $severity=[regex]::Match($thermal,'(?m)^\s*Thermal Status:\s*(\d+)')
 $now=[DateTimeOffset]::UtcNow
 $sample=[ordered]@{observedAt=$now.ToString('o');elapsedSeconds=($now-$started).TotalSeconds;level=$percent;scale=[int](Read-BatteryValue $raw 'scale');acPowered=$ac;usbPowered=$usb;wirelessPowered=$wireless;temperatureTenthsCelsius=$temperature;awake=($power -match 'mWakefulness=Awake');principalAppResumed=($activity -match 'topResumedActivity=.*pe\.rutafija\.conductor/');thermalSeverity=$(if($severity.Success){[int]$severity.Groups[1].Value}else{$null});batteryServiceSimulationUsed=$false}
 [IO.File]::AppendAllText($samplesPath,(($sample | ConvertTo-Json -Compress)+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
 $samples.Add($sample)
 if($ac -or $usb -or $wireless){throw 'ACTUAL_CHARGING_DETECTED_KEEP_MEASUREMENT'}
 Write-Output ('L08_BATTERY_SAMPLE seconds={0} level={1} awake={2} principal={3}' -f [int]$sample.elapsedSeconds,$percent,$sample.awake,$sample.principalAppResumed)
 if($sample.elapsedSeconds -ge 900){break}
 Start-Sleep -Seconds ([int][Math]::Min(30,[Math]::Ceiling(900-$sample.elapsedSeconds)))
}
$first=$samples[0];$last=$samples[$samples.Count-1]
$summary=[ordered]@{runId=$state.runId;startedAt=$state.batteryStartedAt;finishedTelemetryAt=$last.observedAt;elapsedSeconds=$last.elapsedSeconds;personalInitialPercent=$state.batteryInitialPercent;firstTelemetryPercent=$first.level;lastTelemetryPercent=$last.level;dropFromPersonalInitial=$state.batteryInitialPercent-$last.level;samples=$samples.Count;allSamplesWithoutPower=(@($samples | Where-Object {$_.acPowered -or $_.usbPowered -or $_.wirelessPowered}).Count -eq 0);allSamplesAwake=(@($samples | Where-Object {-not $_.awake}).Count -eq 0);allSamplesPrincipalResumed=(@($samples | Where-Object {-not $_.principalAppResumed}).Count -eq 0);maximumTemperatureTenthsCelsius=($samples | ForEach-Object { $_['temperatureTenthsCelsius'] } | Measure-Object -Maximum).Maximum;severeThermalStatusObserved=(@($samples | Where-Object {$null -ne $_.thermalSeverity -and $_.thermalSeverity -ge 3}).Count -gt 0);result='OBSERVED_REAL_UNPOWERED_INTERVAL_GPS_AND_PERSONAL_FINAL_CHECK_STILL_REQUIRED';coordinatesOrSecretsRecorded=$false;pilotEnabled=$false}
[IO.File]::WriteAllText($summaryPath,($summary | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$summary | ConvertTo-Json
