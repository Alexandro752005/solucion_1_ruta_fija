param([string]$BridgeScript = (Join-Path $PSScriptRoot 'Invoke-RutaFijaMobileLocalBridge.ps1'))
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. $BridgeScript -FunctionsOnly
$script:checks=[Collections.Generic.List[string]]::new()
function Assert-Check([string]$Name,[bool]$Condition){if(-not$Condition){throw ('FAILED: '+$Name)};$script:checks.Add($Name)}
function New-FakeBridge {
    $script:stopCalls=0;$script:ownerCalls=0;$script:owner=43210;$script:exitDuringProbe=$false;$script:probeThrows=$false
    $script:fake=[pscustomobject]@{Id=43210;HasExited=$false;RefreshCalls=0}
    $script:fake|Add-Member ScriptMethod WaitForExit {param($Milliseconds);return $this.HasExited}
    $script:fake|Add-Member ScriptMethod Refresh {$this.RefreshCalls++}
    return $script:fake
}
function Get-BridgeOwner([int]$Port){
    $script:ownerCalls++
    if($script:exitDuringProbe){$script:fake.HasExited=$true}
    if($script:probeThrows){throw 'SYNTHETIC_PORT_PROBE_FAILURE'}
    return $script:owner
}
function Stop-Process {
    param($InputObject,[string]$ErrorAction)
    Assert-Check 'Termination receives the exact previously verified process object' ([object]::ReferenceEquals($InputObject,$script:fake))
    $script:stopCalls++;$InputObject.HasExited=$true
}
$p=New-FakeBridge;$p.HasExited=$true
Stop-ExactBridgeProcess $p
Assert-Check 'Already exited object returns without port probing or termination' ($script:ownerCalls-eq0-and$script:stopCalls-eq0)

$p=New-FakeBridge;$script:exitDuringProbe=$true;$script:owner=$null
Stop-ExactBridgeProcess $p
Assert-Check 'Exit during empty-port probe is accepted with zero terminations' ($script:stopCalls-eq0-and$p.RefreshCalls-eq1)

$p=New-FakeBridge;$script:exitDuringProbe=$true;$script:owner=99999
Stop-ExactBridgeProcess $p
Assert-Check 'Exit during foreign-owner probe never terminates any process' ($script:stopCalls-eq0)

$p=New-FakeBridge;$script:owner=99999;$rejected=$false
try{Stop-ExactBridgeProcess $p}catch{$rejected=$_.Exception.Message-eq'MOBILE_BRIDGE_PORT_IDENTITY_MISMATCH_NO_STOP'}
Assert-Check 'Live foreign port owner is refused with zero terminations' ($rejected-and$script:stopCalls-eq0-and-not$p.HasExited)

$p=New-FakeBridge;$script:owner=$null;$rejected=$false
try{Stop-ExactBridgeProcess $p}catch{$rejected=$_.Exception.Message-eq'MOBILE_BRIDGE_PORT_IDENTITY_MISMATCH_NO_STOP'}
Assert-Check 'Live process without port ownership is refused with zero terminations' ($rejected-and$script:stopCalls-eq0-and-not$p.HasExited)

$p=New-FakeBridge
Stop-ExactBridgeProcess $p
Assert-Check 'Matching live owner terminates only its exact object once' ($script:stopCalls-eq1-and$p.HasExited)

$p=New-FakeBridge;$script:exitDuringProbe=$true;$script:probeThrows=$true
Stop-ExactBridgeProcess $p
Assert-Check 'Exit proven after failing probe is accepted with zero terminations' ($script:stopCalls-eq0)

$p=New-FakeBridge;$script:probeThrows=$true;$rejected=$false
try{Stop-ExactBridgeProcess $p}catch{$rejected=$_.Exception.Message-eq'SYNTHETIC_PORT_PROBE_FAILURE'}
Assert-Check 'Failing probe on a live object remains fail-closed with zero terminations' ($rejected-and$script:stopCalls-eq0)

[pscustomobject]@{result='PASS';checks=$script:checks.Count;details=$script:checks}|ConvertTo-Json -Depth 4
