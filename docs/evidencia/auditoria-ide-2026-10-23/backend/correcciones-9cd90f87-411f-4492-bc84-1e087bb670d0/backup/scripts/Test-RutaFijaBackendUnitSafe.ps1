[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$backend=Join-Path $repoRoot 'backend'
$names=@(
 'AdminTermsContentHashTest','AdminTermsDocumentTest','AdminTermsServiceTest','ApiOriginValidationFilterTest',
 'ApprovedAdminTermsPublicationTest','AuditChangesTest','AuditMetadataPolicyTest','AuditOutcomeTest',
 'AuthenticatedBackupArchiveTest','AvailabilityErrorHttpTest','AvailabilityMonitorCliTest','AvailabilityMonitorHttpTest',
 'AvailabilityPolicyTest','BackupCliGuardTest','ConductorRecoveryRequestTest','CorrelationIdFilterTest',
 'CurrentUserProviderTest','EmailOtpChallengeTest','GlobalExceptionHandlerTest','HttpAvailabilityProbeTest',
 'IncidentClosureContractTest','InvitationCodeHasherTest','InvitationServiceBoundaryTest','L08CapacityMeasurementTest',
 'L08ConnectedServicePreparationTest','L08PhysicalComplementPolicyTest','L08Rest03TargetGuardTest','LoginRateLimiterTest',
 'MobileOfflineRegistrationTest','MobilePreferencesContractTest','MonitorConfigurationTest','OfferResponseWindowTest',
 'OpaqueTokenServiceTest','OperationStreamBroadcasterTest','PersonalDataProjectionTest','PersonIdentityGateAuthorizationManagerTest',
 'RefreshTokenTest','RestoreIntegrityComparisonTest','RetentionCalendarTest','RetentionProcedureSecurityTest',
 'SafeLoggingDefaultsTest','SecurityConfigTest','SmtpOtpEmailSenderTest','SqlLiteralArrayCastTest',
 'SuperAdminRecoveryCliTest','TerminalCorroborationPolicyTest','TrustedOriginValidatorTest'
)
if(-not $Execute){Write-Output ('BACKEND_SAFE_UNITS=PLAN_'+$names.Count+'_EXPLICIT_CLASSES_NO_DATABASE_NO_BUILD');return}
$id=[Guid]::NewGuid().ToString()
$evidence=Join-Path $repoRoot ('docs/evidencia/auditoria-ide-2026-10-23/backend/run-'+$id)
$output=Join-Path $backend ('target/auditoria-ide-'+$id)
$mavenCandidates=@(Get-ChildItem -LiteralPath (Join-Path $backend '.maven-user-home/wrapper/dists/apache-maven-3.9.15') -Filter 'mvn.cmd' -File -Recurse)
if($mavenCandidates.Count -ne 1){throw 'EXACT_CACHED_MAVEN_3_9_15_REQUIRED_NO_DOWNLOAD'}
$maven=$mavenCandidates[0].FullName
$jdk='C:/Program Files/Java/jdk-21.0.10'
if(-not (Test-Path -LiteralPath (Join-Path $jdk 'bin/javac.exe'))){throw 'EXISTING_JDK21_REQUIRED'}
New-Item -ItemType Directory -Path $evidence | Out-Null
New-Item -ItemType Directory -Path $output | Out-Null
$selected=@(foreach($name in $names){
 $matches=@(Get-ChildItem -LiteralPath (Join-Path $backend 'src/test/java') -File -Filter ($name+'.java') -Recurse)
 if($matches.Count -ne 1){throw 'EXACT_REVIEWED_TEST_CLASS_REQUIRED'}
 $text=[IO.File]::ReadAllText($matches[0].FullName)
 if($text -match '@SpringBootTest|@DataJpaTest|extends\s+\w*IntegrationTest|NativePostgresTestDatabase|DriverManager|getConnection\('){throw ('DATABASE_OR_CONTEXT_TEST_REFUSED_'+$name)}
 [ordered]@{class=$name;source=$matches[0].FullName.Substring($repoRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $matches[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$selected | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'selected-tests.json') -Encoding UTF8
$pomPath=Join-Path $backend 'pom.xml'
$pomSha=(Get-FileHash -LiteralPath $pomPath -Algorithm SHA256).Hash.ToLowerInvariant()
[xml]$pom=[IO.File]::ReadAllText($pomPath)
$ns='http://maven.apache.org/POM/4.0.0'
$manager=[Xml.XmlNamespaceManager]::new($pom.NameTable)
$manager.AddNamespace('m',$ns)
function Set-Element($parent,[string]$name,[string]$value){
 $node=$parent.SelectSingleNode('m:'+$name,$manager)
 if($null -eq $node){$node=$pom.CreateElement($name,$ns);[void]$parent.AppendChild($node)}
 $node.InnerText=$value
 return $node
}
function Forward([string]$path){return $path.Replace('\','/')}
$build=$pom.SelectSingleNode('/m:project/m:build',$manager)
[void](Set-Element $build 'directory' (Forward $output))
[void](Set-Element $build 'sourceDirectory' (Forward (Join-Path $backend 'src/main/java')))
[void](Set-Element $build 'testSourceDirectory' (Forward (Join-Path $backend 'src/test/java')))
$resources=$build.SelectSingleNode('m:resources',$manager)
if($null -eq $resources){
 $parentPom=Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom'
 [xml]$parentXml=[IO.File]::ReadAllText($parentPom)
 $parentNs=[Xml.XmlNamespaceManager]::new($parentXml.NameTable);$parentNs.AddNamespace('m',$ns)
 $resources=$pom.ImportNode($parentXml.SelectSingleNode('/m:project/m:build/m:resources',$parentNs),$true)
 [void]$build.AppendChild($resources)
}
foreach($resource in $resources.ChildNodes){[void](Set-Element $resource 'directory' (Forward (Join-Path $backend 'src/main/resources')))}
$testResources=$pom.CreateElement('testResources',$ns)
$testResource=$pom.CreateElement('testResource',$ns)
[void](Set-Element $testResource 'directory' (Forward (Join-Path $backend 'src/test/resources')))
[void]$testResources.AppendChild($testResource);[void]$build.AppendChild($testResources)
$compiler=$build.SelectSingleNode('m:plugins/m:plugin[m:artifactId="maven-compiler-plugin"]/m:configuration',$manager)
[void](Set-Element $compiler 'showWarnings' 'true')
[void](Set-Element $compiler 'showDeprecation' 'true')
$compilerArgs=$pom.CreateElement('compilerArgs',$ns);[void](Set-Element $compilerArgs 'arg' '-Xlint:all');[void]$compiler.AppendChild($compilerArgs)
$surefire=$pom.CreateElement('plugin',$ns)
[void](Set-Element $surefire 'groupId' 'org.apache.maven.plugins');[void](Set-Element $surefire 'artifactId' 'maven-surefire-plugin')
$surefireConfig=$pom.CreateElement('configuration',$ns)
[void](Set-Element $surefireConfig 'workingDirectory' (Forward $backend))
[void](Set-Element $surefireConfig 'reportsDirectory' (Forward (Join-Path $output 'surefire-reports')))
[void](Set-Element $surefireConfig 'argLine' '@{argLine} -Xmx512m -XX:MaxMetaspaceSize=384m')
[void]$surefire.AppendChild($surefireConfig);[void]$build.SelectSingleNode('m:plugins',$manager).AppendChild($surefire)
$auditPom=Join-Path $evidence 'audit-pom.xml'
$pom.Save($auditPom)
$pomScope=[ordered]@{original='backend/pom.xml';originalSha256=$pomSha;auditPom='audit-pom.xml';auditPomSha256=(Get-FileHash -LiteralPath $auditPom -Algorithm SHA256).Hash.ToLowerInvariant();changes=@('Absolute original source/test/resource paths','New isolated build and report directories','Original backend working directory for tests','Show all javac warnings/deprecations without suppression','Bounded test JVM preserving JaCoCo agent');dependenciesOrVersionsChanged=$false;springBootResourceFilteringPreserved=$true;output=$output.Substring($repoRoot.Length+1).Replace('\','/')}
$pomScope | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'pom-scope.json') -Encoding UTF8
$variables=@('JAVA_HOME','MAVEN_OPTS','RF_TEST_DATASOURCE_URL','RF_TEST_MIGRATOR_URL','RF_L08_PHYSICAL_PROFILE')
$saved=@{};foreach($name in $variables){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$commands=[Collections.Generic.List[object]]::new()
function Run-Maven([string]$label,[string[]]$arguments){
 $log=Join-Path $evidence ($label+'.log')
 $preference=$ErrorActionPreference
 try{$ErrorActionPreference='Continue';& $maven @arguments *> $log;$code=$LASTEXITCODE}finally{$ErrorActionPreference=$preference}
 $commands.Add([ordered]@{label=$label;arguments=$arguments;exitCode=$code;log=$label+'.log'})
 $commands | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'commands.json') -Encoding UTF8
 if($code -ne 0){throw ('MAVEN_SAFE_AUDIT_FAILED_'+$label+'_PRESERVE_LOG')}
}
try{
 [Environment]::SetEnvironmentVariable('JAVA_HOME',$jdk,'Process')
 [Environment]::SetEnvironmentVariable('MAVEN_OPTS','-Xmx512m -XX:MaxMetaspaceSize=384m','Process')
 [Environment]::SetEnvironmentVariable('RF_TEST_DATASOURCE_URL','jdbc:postgresql://127.0.0.1:1/ruta_fija_unit_forbidden','Process')
 [Environment]::SetEnvironmentVariable('RF_TEST_MIGRATOR_URL','jdbc:postgresql://127.0.0.1:1/ruta_fija_unit_forbidden','Process')
 [Environment]::SetEnvironmentVariable('RF_L08_PHYSICAL_PROFILE',$null,'Process')
 Run-Maven '00-toolchain' @('--version')
 $common=@('--batch-mode','--no-transfer-progress','--offline','-f',$auditPom,('-Djacoco.destFile='+ (Join-Path $output 'jacoco-auditoria.exec')))
 Run-Maven '01-compile-test-compile' ($common+@('compile','test-compile'))
 Run-Maven '02-safe-unit-tests' ($common+@('test',('-Dtest='+($names -join ','))))
}finally{foreach($name in $variables){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
if($pomSha -ne (Get-FileHash -LiteralPath $pomPath -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'LIVE_POM_CHANGED_DURING_AUDIT'}
$totals=@{tests=0;failures=0;errors=0;skipped=0}
$reports=@(Get-ChildItem -LiteralPath (Join-Path $output 'surefire-reports') -Filter 'TEST-*.xml' -File)
foreach($report in $reports){[xml]$xml=[IO.File]::ReadAllText($report.FullName);foreach($key in @('tests','failures','errors','skipped')){$totals[$key]+=[int]$xml.testsuite.GetAttribute($key)}}
if($reports.Count -ne $names.Count -or $totals.tests -eq 0 -or $totals.failures -gt 0 -or $totals.errors -gt 0 -or $totals.skipped -gt 0){throw 'COMPLETE_SELECTED_SUITE_PASS_REQUIRED'}
$result=[ordered]@{recordedAt=[DateTimeOffset]::UtcNow.ToString('o');result='PASS_FRESH_REAL_BACKEND_COMPILE_TEST_COMPILE_AND_SAFE_UNIT_SUITE';runId=$id;selectedClasses=$names.Count;xmlReports=$reports.Count;totals=$totals;livePomUnchanged=$true;databaseConnectionsAllowed=$false;backendApplicationStarted=$false;sharedTargetCachesOverwritten=$false;integrationTestsExecuted=$false;localHttpFixturesAllowed=$true;externalEmailSent=$false;gitCommandsExecuted=$false;outputDirectory=$output.Substring($repoRoot.Length+1).Replace('\','/');commands='commands.json';selection='selected-tests.json';pomScope='pom-scope.json'}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'result.json') -Encoding UTF8
Write-Output ('BACKEND_SAFE_UNITS=PASS_'+$totals.tests+'_TESTS_'+$names.Count+'_CLASSES_NO_SHARED_DB')
Write-Output ('BACKEND_SAFE_UNITS_EVIDENCE='+$evidence.Substring($repoRoot.Length+1).Replace('\','/'))
exit 0
