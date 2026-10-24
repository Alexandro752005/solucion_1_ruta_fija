[CmdletBinding()]
param([ValidateSet('Plan','PrepareNetwork','Serve','CleanupNetwork')][string]$Action='Plan',[Guid]$ResumeRunId)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$runId='3f0ba758-6eb9-4d2f-b948-dd1c7de3c430'
$ruleName='RutaFija-L08-FIS02-'+$runId
$networkFile=Join-Path $repoRoot 'backend/.local/l08-fis02-network.json'
$adb='D:\Android\Sdk\platform-tools\adb.exe'
if($Action -eq 'Plan'){
    Write-Output 'L08_FIS02=PLAN_NO_FILES_NO_DATABASE_NO_FIREWALL'
    Write-Output ('L08_FIS02_RUN_ID='+$runId)
    Write-Output 'MAX_NEW_CONDUCTOR=1_HISTORICAL_7_TO_8'
    return
}
$approval=Get-Content -LiteralPath (Join-Path $repoRoot 'docs/evidencia/L-08/complementos-02-03-autorizacion.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($approval.'L08-FIS-02'.maximumNewSyntheticConductor -ne 1 -or -not $approval.'L08-FIS-02'.samePrivateWifiAnd15MinuteSessionAvailable -or (Get-FileHash -LiteralPath (Join-Path $repoRoot $approval.proposal) -Algorithm SHA256).Hash -ine $approval.proposalSha256){throw 'EXACT_RECORDED_FIS02_APPROVAL_REQUIRED'}
if($Action -eq 'PrepareNetwork'){
    $profiles=@(Get-NetConnectionProfile | Where-Object NetworkCategory -eq Private)
    if($profiles.Count -ne 1){throw 'ONE_PRIVATE_NETWORK_REQUIRED'}
    $addresses=@(Get-NetIPAddress -InterfaceIndex $profiles[0].InterfaceIndex -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '169.254.*'})
    if($addresses.Count -ne 1 -or $addresses[0].PrefixLength -ne 24){throw 'ONE_PRIVATE_IPV4_24_REQUIRED_NO_INFERENCE'}
    $devices=@(& $adb devices | Where-Object {$_ -match '\sdevice$'})
    if($devices.Count -ne 1){throw 'ONE_AUTHORIZED_USB_PHONE_REQUIRED'}
    $route=& $adb shell ip -4 route get 1.1.1.1
    $match=[regex]::Match(($route -join ' '),'\bsrc\s+(\d+\.\d+\.\d+\.\d+)')
    if(-not $match.Success){throw 'PHONE_PRIVATE_WIFI_REQUIRED'}
    $phone=$match.Groups[1].Value;$bind=$addresses[0].IPAddress
    if((($phone -split '\.')[0..2] -join '.') -ne (($bind -split '\.')[0..2] -join '.')){throw 'SAME_PRIVATE_SUBNET_REQUIRED'}
    if($bind -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)'){throw 'RFC1918_PRIVATE_INTERFACE_REQUIRED'}
    $existing=@(Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)
    if($existing.Count -gt 0){throw 'EXISTING_RULE_KEEP_AND_REVIEW_NO_OVERWRITE'}
    $principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
        Write-Output 'L08_FIS02_NETWORK=NEEDS_ADMIN_PRIVILEGE_NO_RULE_CREATED'
        return
    }
    if(Test-Path -LiteralPath $networkFile){throw 'EXISTING_NETWORK_CASE_KEEP_AND_REVIEW'}
    $java=(Get-Command java -CommandType Application | Select-Object -First 1).Source
    $record=[ordered]@{runId=$runId;bindIpv4=$bind;phoneIpv4=$phone;prefixLength=24;ruleName=$ruleName;javaExecutable=$java;port=4311;profile='Private';createdAt=[DateTimeOffset]::UtcNow.ToString('o')}
    [IO.File]::WriteAllText($networkFile,($record | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    New-NetFirewallRule -Name $ruleName -DisplayName 'Ruta Fija L08 FIS02 temporal' -Direction Inbound -Action Allow -Profile Private -Protocol TCP -LocalAddress $bind -LocalPort 4311 -RemoteAddress $phone -Program $java -ErrorAction Stop | Out-Null
    Write-Output 'L08_FIS02_NETWORK=READY_ONE_PHONE_PRIVATE_PROFILE_ONLY'
    return
}
if(-not (Test-Path -LiteralPath $networkFile)){throw 'EXACT_PREPARED_NETWORK_REQUIRED'}
$network=Get-Content -LiteralPath $networkFile -Raw -Encoding UTF8 | ConvertFrom-Json
if($network.runId -ne $runId -or $network.port -ne 4311 -or $network.ruleName -ne $ruleName -or $network.profile -ne 'Private'){throw 'NETWORK_MANIFEST_MISMATCH'}
if($Action -eq 'CleanupNetwork'){
    $rule=Get-NetFirewallRule -Name $ruleName -ErrorAction Stop
    $portFilter=$rule | Get-NetFirewallPortFilter
    $addressFilter=$rule | Get-NetFirewallAddressFilter
    $appFilter=$rule | Get-NetFirewallApplicationFilter
    if($rule.Profile.ToString() -ne 'Private' -or $rule.Direction.ToString() -ne 'Inbound' -or $rule.Action.ToString() -ne 'Allow' -or $portFilter.LocalPort -ne '4311' -or $addressFilter.LocalAddress -ne $network.bindIpv4 -or $addressFilter.RemoteAddress -ne $network.phoneIpv4 -or $appFilter.Program -ine $network.javaExecutable){throw 'EXACT_NARROW_FIREWALL_RULE_REQUIRED'}
}else{
    # Serve runs without elevation. Windows restricts firewall inspection here.
    # Require a separately archived real phone-to-PC transport proof bound to this
    # exact manifest, plus the user's elevated PrepareNetwork result. The Java
    # proxy additionally rejects all peers except that exact phone address.
    $proofRelative='docs/evidencia/L-08/incremento-3-escritorio/fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/network-preflight.json'
    if($network.PSObject.Properties.Name -contains 'networkPreflightEvidence'){
        $proofRelative=$network.networkPreflightEvidence
        $proofFull=[IO.Path]::GetFullPath((Join-Path $repoRoot $proofRelative))
        $evidenceRoot=[IO.Path]::GetFullPath((Join-Path $repoRoot 'docs/evidencia/L-08'))+[IO.Path]::DirectorySeparatorChar
        if(-not $proofFull.StartsWith($evidenceRoot,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($proofFull) -ne '.json'){throw 'OWN_L08_NETWORK_PROOF_REQUIRED'}
    }
    $proof=Get-Content -LiteralPath (Join-Path $repoRoot $proofRelative) -Raw -Encoding UTF8 | ConvertFrom-Json
    if($proof.result -ne 'PASS_REAL_PHONE_TCP_ONE_PEER_ONLY' -or $proof.runId -ne $runId -or $proof.networkManifestSha256 -ine (Get-FileHash -LiteralPath $networkFile -Algorithm SHA256).Hash -or $proof.userReportedElevatedPreparation -ne 'READY_ONE_PHONE_PRIVATE_PROFILE_ONLY'){throw 'REAL_PHONE_NETWORK_PROOF_REQUIRED'}
}
if($Action -eq 'CleanupNetwork'){
    if(Test-Path -LiteralPath (Join-Path $repoRoot 'backend/.local/l08-connected.active')){throw 'CASE_PROTECTED_KEEP_NETWORK_UNTIL_SELECTIVE_RETIREMENT'}
    if(@(Get-NetTCPConnection -LocalPort 4311 -State Listen -ErrorAction SilentlyContinue).Count -ne 0){throw 'SERVER_MUST_BE_STOPPED_BEFORE_NETWORK_RETIREMENT'}
    Remove-NetFirewallRule -Name $ruleName -ErrorAction Stop
    Write-Output 'L08_FIS02_NETWORK=EXACT_RULE_REMOVED_MANIFEST_PRESERVED'
    return
}
if([Console]::IsInputRedirected -or [Console]::IsOutputRedirected){throw 'INTERACTIVE_PRIVATE_CONSOLE_REQUIRED'}
if($PSBoundParameters.ContainsKey('ResumeRunId') -and $ResumeRunId.ToString() -ne $runId){throw 'SAME_AUTHORIZED_CASE_ONLY'}
$backend=Join-Path $repoRoot 'backend'
$deps=[IO.File]::ReadAllText((Join-Path $backend 'target/manual-otp-classpath.txt')).Trim()
$cp=(Join-Path $backend 'target/test-classes')+';'+(Join-Path $backend 'target/classes')+';'+$deps
$saved=@{}
try{
    foreach($entry in @{RF_L08_PHYSICAL_PROFILE='FIS02';RF_L08_FIS02_BIND_IPV4=$network.bindIpv4;RF_L08_FIS02_PHONE_IPV4=$network.phoneIpv4}.GetEnumerator()){
        $saved[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process');[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')
    }
    Push-Location -LiteralPath $backend
    try{
        if($PSBoundParameters.ContainsKey('ResumeRunId')){& $network.javaExecutable -Xms64m -Xmx384m -cp $cp pe.rutafija.support.L08ConnectedTrial --serve --resume-case $runId}
        else{& $network.javaExecutable -Xms64m -Xmx384m -cp $cp pe.rutafija.support.L08ConnectedTrial --serve}
        if($LASTEXITCODE -ne 0){throw 'CASE_PENDING_KEEP_STATE_AND_PROTECTION'}
    }finally{Pop-Location}
}finally{foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
