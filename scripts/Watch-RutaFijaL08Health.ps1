[CmdletBinding()]
param(
    [ValidateSet('Plan','Probe','Watch')][string]$Action='Plan',
    [switch]$Execute,
    [uri]$ApiHealthUrl='http://127.0.0.1:8080/actuator/health/readiness',
    [uri]$AngularUrl='http://127.0.0.1:4300/',
    [uri]$ProxyHealthUrl='http://127.0.0.1:4300/actuator/health/readiness',
    [string]$AngularMarker='<app-root',
    [ValidateRange(1,65535)][int]$PostgreSqlPort=5432,
    [ValidateRange(10,120)][int]$IntervalSeconds=60,
    [ValidateRange(1,10)][int]$TimeoutSeconds=5,
    [ValidateRange(1,10080)][int]$Cycles=1,
    [string]$EvidenceFile,
    [switch]$SyntheticFixtures
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$maximumDetectionSeconds=$IntervalSeconds+8*$TimeoutSeconds+5
if($Action -eq 'Plan'){
    [ordered]@{action='PLAN_NO_NETWORK_NO_FILES';api=$ApiHealthUrl.AbsoluteUri;angular=$AngularUrl.AbsoluteUri;proxy=$ProxyHealthUrl.AbsoluteUri;postgresPort=$PostgreSqlPort;intervalSeconds=$IntervalSeconds;timeoutPerProbeSeconds=$TimeoutSeconds;conservativeDetectionBoundSeconds=$maximumDetectionSeconds;dns='Bound applies only when DNS resolution is independently verified bounded; loopback IP rehearsed';alert='Create local incident immediately, then operator acknowledgment<=5min and escalation documented';alertDelivery='Not installed or proven';coverage24x7='Requires independently hosted watcher and named human roster';tasksInstalled=$false}|ConvertTo-Json
    return
}
if(-not$Execute-or[string]::IsNullOrWhiteSpace($EvidenceFile)){throw 'EXPLICIT_EXECUTE_AND_EVIDENCE_REQUIRED'}
if(Test-Path -LiteralPath $EvidenceFile){throw 'CREATE_NEW_EVIDENCE_ONLY'}
$eventsFile=[IO.Path]::GetFullPath($EvidenceFile)+'.events.jsonl'
if(Test-Path -LiteralPath $eventsFile){throw 'EVENT_STREAM_ALREADY_EXISTS_PRESERVE'}
if($maximumDetectionSeconds -gt 300){throw 'DETECTION_BOUND_EXCEEDS_FIVE_MINUTES'}
foreach($url in @($ApiHealthUrl,$AngularUrl,$ProxyHealthUrl)){
    if($url.Scheme -notin @('http','https')-or$url.UserInfo-or$url.Query-or$url.Fragment){throw 'PUBLIC_HEALTH_URL_WITHOUT_SECRET_REQUIRED'}
}
$repoRoot=Split-Path -Parent $PSScriptRoot
$pgReady='C:\Program Files\PostgreSQL\16\bin\pg_isready.exe'
$observations=[Collections.Generic.List[object]]::new()
function Test-Http([uri]$Url,[string]$Kind){
    $watch=[Diagnostics.Stopwatch]::StartNew();$healthy=$false;$status=$null
    try{
        $reply=Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSeconds -MaximumRedirection 0
        $status=[int]$reply.StatusCode
        if($Kind -eq 'health'){$healthy=($status-eq200)-and(($reply.Content|ConvertFrom-Json).status-eq'UP')}
        else{$healthy=($status-eq200)-and$reply.Content.Contains($AngularMarker)}
    }catch{$healthy=$false}
    [ordered]@{healthy=$healthy;httpStatus=$status;elapsedSeconds=$watch.Elapsed.TotalSeconds}
}
$totalWatch=[Diagnostics.Stopwatch]::StartNew()
$iterations=if($Action-eq'Probe'){1}else{$Cycles}
for($cycle=0;$cycle-lt$iterations;$cycle++){
    $started=[DateTimeOffset]::UtcNow
    $api=Test-Http $ApiHealthUrl 'health';$angular=Test-Http $AngularUrl 'angular';$proxy=Test-Http $ProxyHealthUrl 'health'
    $pgWatch=[Diagnostics.Stopwatch]::StartNew()
    $pgOutput=@(& $pgReady '--host=127.0.0.1' ('--port='+$PostgreSqlPort) ('--timeout='+$TimeoutSeconds) 2>&1)
    $pgHealthy=$LASTEXITCODE-eq0
    $healthy=$api.healthy-and$angular.healthy-and$proxy.healthy-and$pgHealthy
    $observation=[ordered]@{startedAt=$started.ToString('o');finishedAt=[DateTimeOffset]::UtcNow.ToString('o');result=if($healthy){'HEALTHY'}else{'INCIDENT_REQUIRES_HUMAN_ACK'};api=$api;angular=$angular;proxy=$proxy;postgresAcceptsConnections=$pgHealthy;postgresElapsedSeconds=$pgWatch.Elapsed.TotalSeconds;incidentRecorded=-not$healthy;externalAlertDelivered=$false}
    [IO.File]::AppendAllText($eventsFile,($observation|ConvertTo-Json -Depth 7 -Compress)+"`n",[Text.UTF8Encoding]::new($false))
    $observations.Add($observation)
    if($cycle-lt($iterations-1)){Start-Sleep -Seconds $IntervalSeconds}
}
$record=[ordered]@{result=if(@($observations|Where-Object{$_.result-ne'HEALTHY'}).Count){'INCIDENT_RECORDED_LOCALLY'}else{'ALL_PROBES_HEALTHY'};syntheticHttpFixtures=[bool]$SyntheticFixtures;observations=@($observations);elapsedSeconds=$totalWatch.Elapsed.TotalSeconds;configuredDetectionUpperBoundSeconds=$maximumDetectionSeconds;independentHostAccredited=$false;human24x7Accredited=$false;externalAlertsAccredited=$false;postgresSqlIntegrityChecked=$false;productionEnabled=$false}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidenceFile),($record|ConvertTo-Json -Depth 9),[Text.UTF8Encoding]::new($false))
$record|ConvertTo-Json -Depth 9
