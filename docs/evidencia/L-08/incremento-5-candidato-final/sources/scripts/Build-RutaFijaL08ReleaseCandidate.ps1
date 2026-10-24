[CmdletBinding()]
param(
 [switch]$Execute,
 [switch]$VerifyRebuild,
 [ValidateRange(5,999)][int]$BuildNumber=5,
 [string]$ApiBaseUrl='https://api.rutafija.invalid/api/v1'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$endpoint=$null
if(-not [Uri]::TryCreate($ApiBaseUrl,[UriKind]::Absolute,[ref]$endpoint) -or $endpoint.Scheme -ne 'https' -or $endpoint.AbsolutePath -ne '/api/v1' -or $endpoint.UserInfo -or $endpoint.Query -or $endpoint.Fragment -or $endpoint.Port -ne 443){throw 'HTTPS_API_WITHOUT_CREDENTIALS_QUERY_FRAGMENT_REQUIRED'}
$ip=$null
if($endpoint.Host -notmatch '\.' -or $endpoint.Host -match '(^|\.)(localhost|local|test)$' -or [Net.IPAddress]::TryParse($endpoint.Host,[ref]$ip)){throw 'LAB_LOCALHOST_OR_LITERAL_IP_NOT_DELIVERY_ENDPOINT'}
if(-not $Execute){Write-Output 'L08_RELEASE=PLAN_NO_BUILD_NO_KEY_NO_INSTALL';return}
$artifactRoot=Join-Path $repoRoot 'artifacts/L-08-entrega-final'
$evidenceRoot=Join-Path $repoRoot 'docs/evidencia/L-08/incremento-5-candidato-final/release'
$keyRoot=Join-Path $repoRoot 'backend/.local/l08-local-release-key'
$keyFile=Join-Path $keyRoot 'candidato-local.p12'
$passwordFile=Join-Path $keyRoot 'store-password.private'
$keytool='C:/Program Files/Java/jdk-21.0.10/bin/keytool.exe'
$aapt='D:/Android/Sdk/build-tools/36.0.0/aapt2.exe'
$apksigner='D:/Android/Sdk/build-tools/36.0.0/apksigner.bat'
if((Test-Path -LiteralPath $artifactRoot) -and -not $VerifyRebuild){throw 'PRESERVE_FINAL_ARTIFACTS_USE_VERIFY_REBUILD_ONLY'}
if($VerifyRebuild -and -not (Test-Path -LiteralPath (Join-Path $artifactRoot 'manifest.json'))){throw 'REBUILD_REQUIRES_EXISTING_VERIFIED_CANDIDATE'}
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
New-Item -ItemType Directory -Path $keyRoot -Force | Out-Null
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
$expectedSids=@($identity.Value,'S-1-5-18')
$existingAcl=Get-Acl -LiteralPath $keyRoot
$existingSids=@($existingAcl.Access | ForEach-Object {$_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value})
$aclAlreadyCorrect=$existingAcl.AreAccessRulesProtected -and $existingSids.Count -eq 2 -and @($existingSids | Where-Object {$_ -notin $expectedSids}).Count -eq 0
if(-not $aclAlreadyCorrect){
 # Apply only the discretionary ACL. Set-Acl may request SACL privileges that
 # a normal local Windows user does not possess and are not needed here.
 $acl=[Security.AccessControl.DirectorySecurity]::new()
 $dacl='D:P(A;OICI;FA;;;'+$identity.Value+')(A;OICI;FA;;;S-1-5-18)'
 $acl.SetSecurityDescriptorSddlForm($dacl,[Security.AccessControl.AccessControlSections]::Access)
 [IO.Directory]::SetAccessControl($keyRoot,$acl)
}
$actualAcl=Get-Acl -LiteralPath $keyRoot
$allowed=@($actualAcl.Access | Where-Object {$_.AccessControlType -eq 'Allow'})
if(-not $actualAcl.AreAccessRulesProtected -or $actualAcl.Access.Count -ne 2 -or $allowed.Count -ne 2 -or @($allowed | Where-Object {$_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $expectedSids -or $_.FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl}).Count -gt 0){throw 'PRIVATE_KEY_DIRECTORY_ACL_REQUIRED'}
if(-not (Test-Path -LiteralPath $keyFile)){
 if($VerifyRebuild){throw 'REBUILD_MUST_NOT_GENERATE_A_NEW_KEY'}
 $random=[Security.Cryptography.RandomNumberGenerator]::Create()
 try{$bytes=New-Object byte[] 36;$random.GetBytes($bytes);$password=[Convert]::ToBase64String($bytes)}finally{$random.Dispose()}
 [IO.File]::WriteAllText($passwordFile,$password,[Text.UTF8Encoding]::new($false))
 $generationPreference=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  & $keytool -genkeypair -keystore $keyFile -storetype PKCS12 -alias 'rutafija-candidato-local' -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Ruta Fija Candidato Local,OU=APF3 Validacion Local,O=Ruta Fija,L=Lima,C=PE' '-storepass:file' $passwordFile '-keypass:file' $passwordFile *> (Join-Path $evidenceRoot ('key-generation-'+[Guid]::NewGuid().ToString()+'.log'))
  $generationExit=$LASTEXITCODE
 }finally{$ErrorActionPreference=$generationPreference}
 if($generationExit -ne 0){throw 'CANDIDATE_LOCAL_KEY_GENERATION_FAILED'}
}
if(-not (Test-Path -LiteralPath $passwordFile)){throw 'PRIVATE_KEY_PASSWORD_REQUIRED'}
$password=[IO.File]::ReadAllText($passwordFile)
$runId=[Guid]::NewGuid().ToString()
$buildLog=Join-Path $evidenceRoot ('flutter-release-'+$runId+'.log')
$saved=@{}
foreach($name in @('RF_ANDROID_CANDIDATE_STORE','RF_ANDROID_CANDIDATE_STORE_PASSWORD','RF_ANDROID_CANDIDATE_KEY_PASSWORD','GRADLE_OPTS')){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 [Environment]::SetEnvironmentVariable('RF_ANDROID_CANDIDATE_STORE',$keyFile,'Process')
 [Environment]::SetEnvironmentVariable('RF_ANDROID_CANDIDATE_STORE_PASSWORD',$password,'Process')
 [Environment]::SetEnvironmentVariable('RF_ANDROID_CANDIDATE_KEY_PASSWORD',$password,'Process')
 # Release lint needs more class metadata than debug in this 8 GB machine.
 # Keep lint enabled and heap/workers unchanged; this override is process-only.
 [Environment]::SetEnvironmentVariable('GRADLE_OPTS',('-Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=768m -XX:ReservedCodeCacheSize=192m"'),'Process')
 Push-Location -LiteralPath (Join-Path $repoRoot 'mobile')
 try{
  $previousPreference=$ErrorActionPreference
  try{
   $ErrorActionPreference='Continue'
   & flutter build apk --release --no-pub ('--build-number='+$BuildNumber) --android-project-arg=rutaFijaL08Isolated=false --android-project-arg=rutaFijaLocalRelease=true ('--dart-define=RF_API_BASE_URL='+$ApiBaseUrl) *> $buildLog
   $buildExit=$LASTEXITCODE
  }finally{$ErrorActionPreference=$previousPreference}
 }finally{Pop-Location}
 if((Test-Path -LiteralPath $buildLog) -and [IO.File]::ReadAllText($buildLog).Contains($password)){
  # Never publish a tool error that echoed signing credentials.
  $privateLog=Join-Path $keyRoot ('redacted-build-original-'+$runId+'.private')
  Move-Item -LiteralPath $buildLog -Destination $privateLog
  [IO.File]::WriteAllText($buildLog,'BUILD_LOG_WITHHELD_SIGNING_CREDENTIAL_ECHO',[Text.UTF8Encoding]::new($false))
  throw 'BUILD_LOG_WITHHELD_FOR_PRIVATE_CREDENTIAL_ECHO'
 }
}finally{
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
 $password=$null
}
if($buildExit -ne 0){throw 'RELEASE_BUILD_FAILED_KEEP_LOG_NO_INSTALL'}
$builtApk=Join-Path $repoRoot 'mobile/build/app/outputs/flutter-apk/app-release.apk'
$badging=& $aapt dump badging $builtApk
if($LASTEXITCODE -ne 0){throw 'RELEASE_METADATA_FAILED'}
$metadata=$badging -join "`n"
if($metadata -notmatch ("package: name='pe\.rutafija\.conductor' versionCode='"+$BuildNumber+"'") -or $metadata -notmatch "application-label:'Ruta Fija Conductor'" -or $metadata -match 'application-debuggable'){throw 'EXACT_NONDEBUG_PRINCIPAL_RELEASE_METADATA_REQUIRED'}
$certs=& $apksigner verify --verbose --print-certs $builtApk
if($LASTEXITCODE -ne 0){throw 'RELEASE_APKSIGNER_VERIFY_FAILED'}
$certText=$certs -join "`n"
if($certText -notmatch 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})'){throw 'RELEASE_CERTIFICATE_REQUIRED'}
$signerSha=$Matches[1].ToLowerInvariant()
if($signerSha -eq '7ad9b72e98103572c0d83e04271c4cbbba9d9cf71cef6f314bb58f2a58855d20' -or $certText -match 'CN=Android Debug'){throw 'DEBUG_SIGNER_FORBIDDEN'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($builtApk)
try{
 $appLib=$zip.GetEntry('lib/arm64-v8a/libapp.so')
 if($null -eq $appLib){throw 'ARM64_AOT_APP_LIBRARY_REQUIRED'}
 $stream=$appLib.Open();$memory=[IO.MemoryStream]::new()
 try{$stream.CopyTo($memory);$compiledStrings=[Text.Encoding]::UTF8.GetString($memory.ToArray())}finally{$stream.Dispose();$memory.Dispose()}
 if(-not $compiledStrings.Contains($ApiBaseUrl) -or $compiledStrings.Contains('http://192.168.18.4:4311/api/v1')){throw 'COMPILED_API_MUST_MATCH_HTTPS_CANDIDATE_AND_EXCLUDE_CURRENT_LAB'}
}finally{$zip.Dispose()}
$apkHash=(Get-FileHash -LiteralPath $builtApk -Algorithm SHA256).Hash.ToLowerInvariant()
$verification=[ordered]@{recordedAt=[DateTimeOffset]::UtcNow.ToString('o');runId=$runId;result='PASS_RELEASE_SIGNED_CANDIDATE_LOCAL_NOT_INSTALLED';buildNumber=$BuildNumber;apiBaseUrl=$ApiBaseUrl;reservedNonResolvingEndpoint=$endpoint.Host.EndsWith('.invalid');apkSha256=$apkHash;signerSha256=$signerSha;debuggable=$false;debugCertificate=$false;compiledApiStringVerified=$true;labEndpointExcluded=$true;keyAclProtected=$actualAcl.AreAccessRulesProtected;keyAllowedIdentities=2;keyCustody='LOCAL_CANDIDATE_CURRENT_WINDOWS_USER_AND_SYSTEM_ONLY_NOT_REAL_TITLEHOLDER_CEREMONY';installed=$false;productionDeployment=$false;gradleProcessOnlyJvmArgs='-Xmx1024m -XX:MaxMetaspaceSize=768m -XX:ReservedCodeCacheSize=192m';releaseLintDisabled=$false;sharedGradlePropertiesChanged=$false;buildLog=$buildLog.Substring($repoRoot.Length+1).Replace('\','/')}
$badging | Set-Content -LiteralPath (Join-Path $evidenceRoot ('apk-metadata-'+$runId+'.txt')) -Encoding UTF8
$certs | Set-Content -LiteralPath (Join-Path $evidenceRoot ('apk-signature-'+$runId+'.txt')) -Encoding UTF8
if($VerifyRebuild){
 $existing=Get-Content -LiteralPath (Join-Path $artifactRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
 $verification['sameCertificate']=$existing.android.signerSha256 -eq $signerSha
 $verification['byteIdenticalApk']=$existing.android.sha256 -eq $apkHash
 if(-not $verification.sameCertificate){throw 'REBUILD_CERTIFICATE_CHANGED'}
 $verification | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $evidenceRoot ('rebuild-'+$runId+'.json')) -Encoding UTF8
 Write-Output ('L08_RELEASE_REBUILD=VERIFIED_SAME_SIGNER_BYTE_IDENTICAL_'+$verification.byteIdenticalApk)
 return
}
$sourceJar=Join-Path $repoRoot 'artifacts/L-08-post-cap25-2026-10-22/ruta-fija-l08-local.jar'
$jarHash=(Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
if($jarHash -ne 'eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f'){throw 'PRESERVED_VERIFIED_BACKEND_JAR_REQUIRED'}
$jarZip=[IO.Compression.ZipFile]::OpenRead($sourceJar)
$entriesCompared=0
try{
 foreach($entry in $jarZip.Entries | Where-Object {$_.FullName.StartsWith('BOOT-INF/classes/') -and -not $_.FullName.EndsWith('/')}){
  $relative=$entry.FullName.Substring('BOOT-INF/classes/'.Length)
  $compiledFile=Join-Path (Join-Path $repoRoot 'backend/target/classes') $relative
  if(-not (Test-Path -LiteralPath $compiledFile)){throw 'CURRENT_BACKEND_COMPILED_ENTRY_MISSING'}
  $hashAlgorithm=[Security.Cryptography.SHA256]::Create();$entryStream=$entry.Open()
  try{$entryHash=([BitConverter]::ToString($hashAlgorithm.ComputeHash($entryStream))).Replace('-','').ToLowerInvariant()}finally{$hashAlgorithm.Dispose();$entryStream.Dispose()}
  if($entryHash -ne (Get-FileHash -LiteralPath $compiledFile -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'CURRENT_BACKEND_COMPILED_ENTRY_DIFFERS_FROM_VERIFIED_JAR'}
  $entriesCompared++
 }
}finally{$jarZip.Dispose()}
if($entriesCompared -lt 600){throw 'BACKEND_ENTRY_PROOF_INCOMPLETE'}
New-Item -ItemType Directory -Path $artifactRoot | Out-Null
Copy-Item -LiteralPath $builtApk -Destination (Join-Path $artifactRoot 'Ruta-Fija-Conductor-candidato-local-release.apk')
Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $artifactRoot 'ruta-fija-backend-candidato-local.jar')
$sourceHashes=@(foreach($folder in @('mobile/lib','backend/src/main')){Get-ChildItem -LiteralPath (Join-Path $repoRoot $folder) -File -Recurse | Sort-Object FullName | ForEach-Object {[ordered]@{path=$_.FullName.Substring($repoRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}}})
$manifest=[ordered]@{schemaVersion='1.0';recordedAt=[DateTimeOffset]::UtcNow.ToString('o');classification='FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL';productionRelease=$false;pilotEnabled=$false;android=[ordered]@{artifact='Ruta-Fija-Conductor-candidato-local-release.apk';sha256=$apkHash;signerSha256=$signerSha;signing='NEW_RSA3072_LOCAL_CANDIDATE_NOT_DEBUG_NOT_TITLEHOLDER_FINAL_CUSTODY';package='pe.rutafija.conductor';versionName='0.1.0';versionCode=$BuildNumber;buildType='release';apiBaseUrl=$ApiBaseUrl;apiDeploymentConfigured=-not $endpoint.Host.EndsWith('.invalid');installed=$false};backend=[ordered]@{artifact='ruta-fija-backend-candidato-local.jar';sha256=$jarHash;reusedVerifiedV50Jar=$true;schemaVersion='50';successfulMigrationsV0ThroughV50=51;currentCompiledEntriesMatch=$entriesCompared;historicalRuntimeEvidence='docs/evidencia/L-08/incremento-2-mision-y-controles/candidato-v50-solo-lectura.json'};buildCommand='powershell.exe -NoProfile -File scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute';rebuildCommand='powershell.exe -NoProfile -File scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute -VerifyRebuild';keyPubliclyIncluded=$false;sourceHashes=$sourceHashes}
$manifest | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $artifactRoot 'manifest.json') -Encoding UTF8
@(($apkHash+'  Ruta-Fija-Conductor-candidato-local-release.apk'),($jarHash+'  ruta-fija-backend-candidato-local.jar')) | Set-Content -LiteralPath (Join-Path $artifactRoot 'SHA256SUMS.txt') -Encoding ASCII
$verification['backendCompiledEntriesCompared']=$entriesCompared
$verification | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $evidenceRoot ('release-verification-'+$runId+'.json')) -Encoding UTF8
Write-Output 'L08_RELEASE=VERIFIED_SIGNED_LOCAL_CANDIDATE_NO_INSTALL_NO_PRODUCTION'
Write-Output ('L08_RELEASE_APK_SHA256='+$apkHash)
