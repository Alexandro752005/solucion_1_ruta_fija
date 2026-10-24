[CmdletBinding()]
param(
    [ValidateSet('Plan','Start','Stop','Test')][string]$Action='Plan',
    [switch]$Execute,
    [switch]$FunctionsOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$configPath=Join-Path $root 'backend/.local/ruta-fija-mobile-bridge.json'
$statePath=Join-Path $root '.runtime/ruta-fija-mobile-bridge.json'
$sourcePath=Join-Path $PSScriptRoot 'RutaFijaMobileLocalBridge.java'

function Assert-MobileBridgeConfiguration($Config) {
    if($Config.schemaVersion-ne1-or$Config.enabled-isnot[bool]-or$Config.port-ne4311){throw 'MOBILE_BRIDGE_CONFIG_INVALID'}
    foreach($value in @($Config.bindIpv4,$Config.phoneIpv4)){
        $parsed=$null
        if($value-notmatch'^[0-9.]+$'-or-not[Net.IPAddress]::TryParse($value,[ref]$parsed)-or$parsed.AddressFamily-ne[Net.Sockets.AddressFamily]::InterNetwork){throw 'MOBILE_BRIDGE_PRIVATE_IPV4_REQUIRED'}
        $bytes=$parsed.GetAddressBytes()
        if(-not($bytes[0]-eq10-or($bytes[0]-eq172-and$bytes[1]-ge16-and$bytes[1]-le31)-or($bytes[0]-eq192-and$bytes[1]-eq168))){throw 'MOBILE_BRIDGE_PRIVATE_IPV4_REQUIRED'}
    }
    if($Config.bindIpv4-eq$Config.phoneIpv4-or(($Config.bindIpv4-split'\.')[0..2]-join'.')-ne(($Config.phoneIpv4-split'\.')[0..2]-join'.')){throw 'MOBILE_BRIDGE_DISTINCT_SAME_SUBNET_PEER_REQUIRED'}
    if($Config.apiBaseUrl-cne('http://'+$Config.bindIpv4+':4311/api/v1')){throw 'MOBILE_BRIDGE_APK_ENDPOINT_MISMATCH'}
}
function Get-BridgeOwner([int]$Port) {
    $owners=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue|Where-Object LocalPort -eq $Port|Select-Object -ExpandProperty OwningProcess -Unique)
    if($owners.Count-gt1){throw 'MOBILE_BRIDGE_AMBIGUOUS_PORT_OWNERSHIP'}
    if($owners.Count-eq0){return $null};return [int]$owners[0]
}
function Get-ExactBridgeProcess($State) {
    if($State.schemaVersion-ne1-or$State.projectRoot-cne$root-or$State.source-cne$sourcePath-or$State.port-ne4311){throw 'MOBILE_BRIDGE_STATE_IDENTITY_MISMATCH'}
    if($State.status-eq'STOPPED'){return $null}
    $p=Get-Process -Id ([int]$State.processId) -ErrorAction SilentlyContinue
    if($null-eq$p){return $null}
    $facts=Get-CimInstance Win32_Process -Filter ('ProcessId='+$p.Id) -ErrorAction Stop
    $p.Refresh();if($p.HasExited){return $null}
    $command=if($null-ne$facts){[string]$facts.CommandLine}else{''}
    try{$matches=$p.Path-ieq$State.executable-and$p.StartTime.ToUniversalTime().ToString('o')-ceq$State.processStartedAtUtc-and$command.Contains($sourcePath)}
    catch{if($p.WaitForExit(500)){return $null};throw}
    if(-not$matches){
        if($p.WaitForExit(500)){return $null}
        throw 'MOBILE_BRIDGE_PROCESS_IDENTITY_MISMATCH_NO_STOP'
    }
    return $p
}
function Write-BridgeState($State) {
    [IO.File]::WriteAllText($statePath,($State|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
}
function Stop-ExactBridgeProcess($Process) {
    if($Process.WaitForExit(1500)){return}
    try{$owner=Get-BridgeOwner 4311}
    catch{$Process.Refresh();if($Process.HasExited){return};throw}
    # The bridge exits itself after its upstream stops. Port inspection may
    # outlive that exit; only this exact process object can prove it finished.
    $Process.Refresh();if($Process.HasExited){return}
    if($owner-ne$Process.Id){throw 'MOBILE_BRIDGE_PORT_IDENTITY_MISMATCH_NO_STOP'}
    try{Stop-Process -InputObject $Process -ErrorAction Stop}
    catch{$Process.Refresh();if($Process.HasExited){return};throw}
    $null=$Process.WaitForExit(5000);$Process.Refresh()
    if(-not$Process.HasExited){throw 'MOBILE_BRIDGE_STOP_TIMEOUT'}
}
if($FunctionsOnly){return}
if($Action-eq'Plan'){Write-Output 'MOBILE_BRIDGE=PLAN_NO_FILES_NO_NETWORK_NO_PROCESSES';return}
if(-not$Execute){throw 'MOBILE_BRIDGE_EXPLICIT_EXECUTE_REQUIRED'}
if($Action-eq'Stop'){
    if(-not(Test-Path -LiteralPath $statePath)){Write-Output 'MOBILE_BRIDGE=NO_OWNED_PROCESS';return}
    $state=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8|ConvertFrom-Json
    $p=Get-ExactBridgeProcess $state
    if($null-ne$p){Stop-ExactBridgeProcess $p}
    $state.status='STOPPED';Write-BridgeState $state
    Write-Output 'MOBILE_BRIDGE=STOPPED_OWN_PROCESS_ONLY';return
}
if(-not(Test-Path -LiteralPath $configPath)){Write-Output 'MOBILE_BRIDGE=NOT_CONFIGURED_API_CRM_ONLY';return}
$config=Get-Content -LiteralPath $configPath -Raw -Encoding UTF8|ConvertFrom-Json
Assert-MobileBridgeConfiguration $config
if(-not$config.enabled){Write-Output 'MOBILE_BRIDGE=DISABLED_BY_PRIVATE_CONFIGURATION';return}
$native=Get-Content -LiteralPath (Join-Path $root '.runtime/ruta-fija-native-runtime.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($native.mode-cne'NORMAL_LOCAL'-or$native.projectRoot-cne$root){throw 'MOBILE_BRIDGE_NORMAL_LOCAL_RUNTIME_REQUIRED'}
$backend=Get-Process -Id ([int]$native.backend.processId) -ErrorAction Stop
$backendFacts=Get-CimInstance Win32_Process -Filter ('ProcessId='+$backend.Id) -ErrorAction Stop
if($backend.Path-ine$native.backend.executable-or$backend.StartTime.ToUniversalTime().ToString('o')-cne$native.backend.processStartedAtUtc-or-not([string]$backendFacts.CommandLine).Contains($native.backend.commandMarker)-or(Get-BridgeOwner 8080)-ne$backend.Id){throw 'MOBILE_BRIDGE_BACKEND_IDENTITY_MISMATCH'}
$ip=@(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop|Where-Object IPAddress -eq $config.bindIpv4)
if($ip.Count-ne1-or$ip[0].PrefixLength-ne24){throw 'MOBILE_BRIDGE_EXACT_LOCAL_INTERFACE_REQUIRED'}
$profile=@(Get-NetConnectionProfile -InterfaceIndex $ip[0].InterfaceIndex -ErrorAction Stop)
if($profile.Count-ne1-or$profile[0].NetworkCategory.ToString()-ne'Private'){throw 'MOBILE_BRIDGE_PRIVATE_NETWORK_PROFILE_REQUIRED'}
$sourceHash=(Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
if(Test-Path -LiteralPath $statePath){
    $state=Get-Content -LiteralPath $statePath -Raw -Encoding UTF8|ConvertFrom-Json
    $existing=Get-ExactBridgeProcess $state
    if($null-ne$existing){
        if($state.backendProcessId-ne$backend.Id-or$state.backendStartedAtUtc-cne$native.backend.processStartedAtUtc-or$state.bindIpv4-cne$config.bindIpv4-or$state.phoneIpv4-cne$config.phoneIpv4-or$state.sourceSha256-cne$sourceHash-or(Get-BridgeOwner 4311)-ne$existing.Id){throw 'MOBILE_BRIDGE_EXISTING_PROCESS_CONFIGURATION_MISMATCH'}
        Write-Output 'MOBILE_BRIDGE=PASS_EXISTING_MANAGED_PROCESS';return
    }
}
if($Action-eq'Test'){throw 'MOBILE_BRIDGE_NOT_RUNNING'}
if($null-ne(Get-BridgeOwner 4311)){throw 'MOBILE_BRIDGE_FOREIGN_PORT_IN_USE_NO_PROCESS_STOPPED'}
if((Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 5).status-ne'UP'){throw 'MOBILE_BRIDGE_UPSTREAM_NOT_HEALTHY'}
$out=Join-Path $root '.runtime/mobile-bridge.stdout.log';$err=Join-Path $root '.runtime/mobile-bridge.stderr.log'
$saved=@{};$started=$null
try{
    foreach($name in @('SPRING_DATASOURCE_PASSWORD','SPRING_FLYWAY_PASSWORD','SPRING_MAIL_PASSWORD','JWT_SECRET_BASE64','RUTA_FIJA_BOOTSTRAP_PASSWORD','PGPASSWORD')){
        $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    $arguments=@('-Xms16m','-Xmx64m',('"{0}"'-f$sourcePath),$config.bindIpv4,$config.phoneIpv4,[string]$backend.Id,[string]$native.backend.processStartedAtUtc,'4311')
    $started=Start-Process -FilePath $native.backend.executable -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
}finally{foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
$ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(20)
try{
    do{
        $started.Refresh();if($started.HasExited){throw 'MOBILE_BRIDGE_START_FAILED_CHECK_STDERR'}
        $owner=Get-BridgeOwner 4311
        if($owner-eq$started.Id){$ready=$true;break}
        if($null-ne$owner){throw 'MOBILE_BRIDGE_FOREIGN_PORT_CLAIM_DURING_START'}
        Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow-lt$deadline)
    if(-not$ready-or(Get-BridgeOwner 4311)-ne$started.Id){throw 'MOBILE_BRIDGE_START_TIMEOUT_OR_PORT_MISMATCH'}
    Write-BridgeState ([ordered]@{schemaVersion=1;projectRoot=$root;status='RUNNING';processId=$started.Id;executable=$native.backend.executable;processStartedAtUtc=$started.StartTime.ToUniversalTime().ToString('o');source=$sourcePath;sourceSha256=$sourceHash;port=4311;bindIpv4=$config.bindIpv4;phoneIpv4=$config.phoneIpv4;backendProcessId=$backend.Id;backendStartedAtUtc=$native.backend.processStartedAtUtc})
}catch{
    $started.Refresh();if(-not$started.HasExited){Stop-Process -Id $started.Id -ErrorAction Stop};throw
}
Write-Output 'MOBILE_BRIDGE=PASS_PRIVATE_PHONE_PORT_4311_TO_API_8080'
