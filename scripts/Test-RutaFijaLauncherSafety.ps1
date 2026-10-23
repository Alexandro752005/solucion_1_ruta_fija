[CmdletBinding()]
param([switch]$Execute,[string]$EvidenceFile)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not$Execute){Write-Output 'LAUNCHER_SAFETY=PLAN_MOCKS_ONLY_NO_SERVICE_OR_DATABASE_ACTION';return}
$root=Split-Path -Parent $PSScriptRoot
if(-not$EvidenceFile){$EvidenceFile=Join-Path $root ('docs/evidencia/auditoria-ide-2026-10-23/launchers/qa-mocks-'+[Guid]::NewGuid().ToString()+'.json')}
if(Test-Path -LiteralPath $EvidenceFile){throw 'CREATE_NEW_EVIDENCE_REQUIRED'}
. (Join-Path $PSScriptRoot 'Invoke-RutaFijaPostgresqlLease.ps1') -FunctionsOnly
$checks=[Collections.Generic.List[object]]::new()
$script:runningFacts=[pscustomobject]@{name='postgresql-x64-16';state='Running';processId=12345;processStartUtc='2026-10-23T00:00:00.0000000Z';executable='synthetic-pg_ctl'}
$script:stoppedFacts=[pscustomobject]@{name='postgresql-x64-16';state='Stopped';processId=0;processStartUtc=$null;executable='synthetic-pg_ctl'}
function Reset-Fixture {
 $script:fakeFacts=$script:runningFacts;$script:fakeLease=$null;$script:fakeClients=0;$script:clientQueryFails=$false
 $script:actions=[Collections.Generic.List[string]]::new()
}
function Own-Lease {return [pscustomobject]@{schemaVersion=1;projectRoot=$root;serviceName='postgresql-x64-16';priorState='Stopped';processId=12345;processStartUtc='2026-10-23T00:00:00.0000000Z';executable='synthetic-pg_ctl'}}
function Get-PostgresqlFacts {return $script:fakeFacts}
function Read-PostgresqlLease {return $script:fakeLease}
function Save-PostgresqlLease($Lease){$script:fakeLease=$Lease}
function Remove-PostgresqlLease {$script:fakeLease=$null}
function Get-OtherPostgresqlClients {if($script:clientQueryFails){throw 'SYNTHETIC_CLIENT_QUERY_UNAVAILABLE'};return $script:fakeClients}
function Invoke-PostgresqlServiceAction([string]$RequestedAction){$script:actions.Add($RequestedAction);$script:fakeFacts=if($RequestedAction-eq'Stop'){$script:stoppedFacts}else{$script:runningFacts}}
function Check([string]$Name,[bool]$Passed){$checks.Add([ordered]@{name=$Name;passed=$Passed});if(-not$Passed){throw ('MOCK_FAILED_'+$Name)}}
Reset-Fixture;$null=Stop-OwnedPostgresql;Check 'No lease preserves existing service' ($script:actions.Count-eq0)
Reset-Fixture;$null=Start-OwnedPostgresql;Check 'Already-running service acquires no ownership' ($script:actions.Count-eq0-and$null-eq$script:fakeLease)
Reset-Fixture;$script:fakeFacts=$script:stoppedFacts;$null=Start-OwnedPostgresql;Check 'Previously stopped service creates own lease' ($script:actions.Count-eq1-and$script:actions[0]-eq'Start'-and$script:fakeLease.priorState-eq'Stopped')
Reset-Fixture;$script:fakeLease=Own-Lease;$script:fakeClients=1;$null=Stop-OwnedPostgresql;Check 'Any other client prevents automatic stop' ($script:actions.Count-eq0-and$null-ne$script:fakeLease)
Reset-Fixture;$script:fakeLease=Own-Lease;$null=Stop-OwnedPostgresql;Check 'Matching instance and zero clients permit owned stop' ($script:actions.Count-eq1-and$script:actions[0]-eq'Stop'-and$null-eq$script:fakeLease)
Reset-Fixture;$script:fakeLease=Own-Lease;$script:fakeLease.processStartUtc='2026-10-22T00:00:00.0000000Z';$rejected=$false;try{$null=Stop-OwnedPostgresql}catch{$rejected=$_.Exception.Message-eq'POSTGRESQL_LEASE_IDENTITY_CHANGED_NO_STOP'};Check 'Reused PID or restarted instance prevents stop' ($rejected-and$script:actions.Count-eq0)
Reset-Fixture;$script:fakeLease=Own-Lease;$script:fakeLease.executable='different-executable';$rejected=$false;try{$null=Stop-OwnedPostgresql}catch{$rejected=$_.Exception.Message-eq'POSTGRESQL_LEASE_IDENTITY_CHANGED_NO_STOP'};Check 'Different service executable prevents stop' ($rejected-and$script:actions.Count-eq0)
Reset-Fixture;$script:fakeLease=Own-Lease;$script:fakeFacts=[pscustomobject]@{name='postgresql-x64-16';state='Running';processId=12345;processStartUtc=$null;executable='synthetic-pg_ctl'};$rejected=$false;try{$null=Stop-OwnedPostgresql}catch{$rejected=$_.Exception.Message-eq'POSTGRESQL_INSTANCE_START_NOT_OBSERVABLE_NO_STOP'};Check 'Hidden process start time prevents ownership-based stop' ($rejected-and$script:actions.Count-eq0)
Reset-Fixture;$script:fakeFacts=[pscustomobject]@{name='postgresql-x64-16';state='Running';processId=12345;processStartUtc=$null;executable='synthetic-pg_ctl'};$null=Start-OwnedPostgresql;Check 'Already-running unowned service does not require hidden start time' ($script:actions.Count-eq0-and$null-eq$script:fakeLease)
Reset-Fixture;$script:fakeLease=Own-Lease;$script:clientQueryFails=$true;$rejected=$false;try{$null=Stop-OwnedPostgresql}catch{$rejected=$_.Exception.Message-eq'SYNTHETIC_CLIENT_QUERY_UNAVAILABLE'};Check 'Unavailable client inspection preserves service and lease' ($rejected-and$script:actions.Count-eq0-and$null-ne$script:fakeLease)
Reset-Fixture;$script:fakeLease=Own-Lease;$script:fakeFacts=$script:stoppedFacts;$null=Stop-OwnedPostgresql;Check 'Already-stopped owned instance releases lease without Stop' ($script:actions.Count-eq0-and$null-eq$script:fakeLease)
Check 'Foreign session with hidden NULL backend_type is still counted' ((Count-ConservativePostgresqlClients @([pscustomobject]@{hasDatabase=$true;hasUser=$true;hasClientEndpoint=$true;backendType=$null}))-eq1)
$rejected=$false;try{$null=Count-ConservativePostgresqlClients @([pscustomobject]@{hasDatabase=$false;hasUser=$false;hasClientEndpoint=$false;backendType=$null})}catch{$rejected=$_.Exception.Message-eq'CLIENT_OBSERVABILITY_INCOMPLETE_PRESERVE_POSTGRESQL'};Check 'Unknown hidden session prevents proof of zero clients' $rejected
Check 'Recognized internal checkpointer is not a client' ((Count-ConservativePostgresqlClients @([pscustomobject]@{hasDatabase=$false;hasUser=$false;hasClientEndpoint=$false;backendType='checkpointer'}))-eq0)
$parsed=@()
foreach($relative in @('scripts/Invoke-RutaFijaNativeRuntime.ps1','scripts/Invoke-RutaFijaQuickDemo.ps1','scripts/Invoke-RutaFijaNativeTest.ps1','scripts/Test-RutaFijaNativePreflight.ps1','scripts/Invoke-RutaFijaPostgresqlLease.ps1','scripts/Test-RutaFijaLocalSecretPolicy.ps1','scripts/Test-RutaFijaLauncherSafety.ps1','scripts/Set-RutaFijaPostgresqlService.ps1')){
 $path=Join-Path $root $relative;$tokens=$null;$errors=$null
 [void][Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
 Check ('PowerShell parse '+$relative) (@($errors).Count-eq0)
 $parsed+=[ordered]@{path=$relative;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
foreach($relative in @('iniciar_ruta_fija.bat','finalizar_ruta_fija.bat','iniciar_demo_temporal.bat','finalizar_demo_temporal.bat','verificar_ruta_fija.bat')){
 $path=Join-Path $root $relative;$bytes=[IO.File]::ReadAllBytes($path);$text=[Text.Encoding]::UTF8.GetString($bytes)
 Check ('BAT no invalid control bytes '+$relative) (@($bytes|Where-Object{$_-lt32-and$_-notin@(9,10,13)}).Count-eq0)
 Check ('BAT every CR belongs to CRLF '+$relative) (-not[regex]::IsMatch($text,"`r(?!`n)"))
 Check ('BAT absolute quoted PowerShell '+$relative) ($text.Contains('"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"'))
}
$demoPath=Join-Path $root 'scripts/Invoke-RutaFijaQuickDemo.ps1';$demoTokens=$null;$demoErrors=$null
$demoAst=[Management.Automation.Language.Parser]::ParseFile($demoPath,[ref]$demoTokens,[ref]$demoErrors)
$wanted=@('Assert-DemoPortsFree','Get-CloudflaredManagedState','Get-CloudflaredExecutable')
$demoDefinitions=@($demoAst.FindAll({param($node)$node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-in$wanted},$false))
foreach($definition in $demoDefinitions){Invoke-Expression $definition.Extent.Text}
$script:fakeBusyPort=$true
function Get-NetTCPConnection {if($script:fakeBusyPort){return [pscustomobject]@{LocalPort=4300;OwningProcess=99999}};return @()}
$rejected=$false;try{Assert-DemoPortsFree}catch{$rejected=$_.Exception.Message-eq'DEMO_REQUIRES_FREE_NATIVE_PORTS_BEFORE_ANY_PUBLIC_TUNNEL'};Check 'Occupied native port refuses public tunnel before network exposure' $rejected
$script:fakeBusyPort=$false;Assert-DemoPortsFree;Check 'Free ports pass demo precondition without starting anything' $true
$script:fakeCloudflared=[pscustomobject]@{ProcessName='cloudflared';Path='C:\synthetic\cloudflared.exe';StartTime=[DateTime]::SpecifyKind([DateTime]::Parse('2026-10-23T00:00:00'),[DateTimeKind]::Utc)}
function Get-ProcessByIdSafely {return $script:fakeCloudflared}
function Get-ProcessCommandLine {return 'cloudflared tunnel --url http://127.0.0.1:4300'}
$demoState=[pscustomobject]@{cloudflared=[pscustomobject]@{processId=77777;executable='C:\synthetic\cloudflared.exe';commandMarker='http://127.0.0.1:4300';processStartedAtUtc=$script:fakeCloudflared.StartTime.ToUniversalTime().ToString('o')}}
Check 'Matching exact demo process instance is managed' (Get-CloudflaredManagedState $demoState).IsManaged
$demoState.cloudflared.processStartedAtUtc='2026-10-22T00:00:00.0000000Z'
Check 'Reused cloudflared PID is rejected without Stop' (-not(Get-CloudflaredManagedState $demoState).IsManaged)
$toolDefinition=@($demoDefinitions|Where-Object{$_.Name-eq'Get-CloudflaredExecutable'})[0].Extent.Text
Check 'Missing cloudflared cannot trigger automatic installer' (-not$toolDefinition.Contains('& $installerScript')-and$toolDefinition.Contains('CLOUDFLARED_MISSING_NO_AUTOMATIC_DOWNLOAD'))
$record=[ordered]@{recordedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');result='PASS_LAUNCHER_STATIC_AND_OWNERSHIP_MOCKS';checks=@($checks);checksPassed=$checks.Count;actualServiceStartedOrStopped=$false;actualDatabaseReadOrChanged=$false;mockScope='Service facts/client count/lease persistence/action are synthetic; no real Stop called';sources=$parsed;liveBackendFrontendStartProven=$false}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidenceFile),($record|ConvertTo-Json -Depth 9),[Text.UTF8Encoding]::new($false))
$record|ConvertTo-Json -Depth 9
