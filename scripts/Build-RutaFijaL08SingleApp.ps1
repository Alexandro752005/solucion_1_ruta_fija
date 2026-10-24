[CmdletBinding()]
param([switch]$Execute,[ValidateRange(3,99)][int]$BuildNumber=3)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if(-not $Execute){Write-Output 'L08_SINGLE_APP=PLAN_NO_BUILD_NO_INSTALL';return}
$approval=Get-Content -LiteralPath (Join-Path $repoRoot 'docs/evidencia/L-08/incremento-4-cierre-fisico/autorizacion-app-unica.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if(-not $approval.singleCurrentPrincipalAppAuthorized -or $approval.principalPackage -ne 'pe.rutafija.conductor'){throw 'EXPLICIT_SINGLE_PRINCIPAL_APP_APPROVAL_REQUIRED'}
$network=Get-Content -LiteralPath (Join-Path $repoRoot 'backend/.local/l08-fis02-network.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($network.runId -ne '3f0ba758-6eb9-4d2f-b948-dd1c7de3c430' -or $network.port -ne 4311 -or $network.bindIpv4 -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)[0-9.]+$'){throw 'EXACT_PRIVATE_LAB_ENDPOINT_REQUIRED'}
$artifactRoot=Join-Path $repoRoot ('artifacts/L-08-app-unica-local-2026-10-22'+$(if($BuildNumber -eq 3){''}else{'-v'+$BuildNumber}))
if(Test-Path -LiteralPath $artifactRoot){throw 'KEEP_EXISTING_SINGLE_APP_ARTIFACT'}
$log=Join-Path $repoRoot ('backend/target/l08-single-app-build-'+[Guid]::NewGuid().ToString()+'.log')
Push-Location -LiteralPath (Join-Path $repoRoot 'mobile')
try{
 $savedPreference=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  & flutter build apk --debug --no-pub ('--build-number='+$BuildNumber) --android-project-arg=rutaFijaL08Isolated=false ('--dart-define=RF_API_BASE_URL=http://{0}:4311/api/v1' -f $network.bindIpv4) *> $log
  $buildExit=$LASTEXITCODE
 }finally{$ErrorActionPreference=$savedPreference}
}finally{Pop-Location}
if($buildExit -ne 0){throw 'SINGLE_APP_BUILD_FAILED_KEEP_LOG_NO_INSTALL'}
$apk=Join-Path $repoRoot 'mobile/build/app/outputs/flutter-apk/app-debug.apk'
$aapt=Get-ChildItem -LiteralPath 'D:\Android\Sdk\build-tools' -Filter 'aapt2.exe' -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
$metadata=& $aapt.FullName dump badging $apk
if($LASTEXITCODE -ne 0){throw 'SINGLE_APP_METADATA_FAILED_NO_INSTALL'}
$joined=$metadata -join "`n"
if($joined -notmatch ("package: name='pe\.rutafija\.conductor' versionCode='"+$BuildNumber+"'") -or $joined -notmatch "application-label:'Ruta Fija Conductor'"){throw 'EXACT_PRINCIPAL_PACKAGE_VERSION_AND_LABEL_REQUIRED_NO_INSTALL'}
$signer=& 'D:\Android\Sdk\build-tools\36.0.0\apksigner.bat' verify --print-certs $apk
if($LASTEXITCODE -ne 0 -or ($signer -join "`n") -notmatch 'certificate SHA-256 digest: 7ad9b72e98103572c0d83e04271c4cbbba9d9cf71cef6f314bb58f2a58855d20'){throw 'EXISTING_DEVICE_SIGNER_REQUIRED_NO_INSTALL'}
New-Item -ItemType Directory -Path $artifactRoot -ErrorAction Stop | Out-Null
$destination=Join-Path $artifactRoot 'Ruta-Fija-Conductor.apk'
Copy-Item -LiteralPath $apk -Destination $destination -ErrorAction Stop
$sourceHashes=@(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'mobile/lib') -Filter '*.dart' -File -Recurse | Sort-Object FullName | ForEach-Object {[ordered]@{path=$_.FullName.Substring($repoRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
$manifest=[ordered]@{builtAt=[DateTimeOffset]::UtcNow.ToString('o');artifact='Ruta-Fija-Conductor.apk';package='pe.rutafija.conductor';label='Ruta Fija Conductor';versionCode=$BuildNumber;buildType='debug';signer='EXISTING_ANDROID_DEBUG_CERTIFICATE';packageVerifiedBeforeInstallation=$true;sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant();apiBaseUrl=('http://{0}:4311/api/v1' -f $network.bindIpv4);apiScope='LOCAL_PRIVATE_LAB_REQUIRES_PC_SERVER';productionRelease=$false;exactPreviousPrincipalBinaryRestitution=$false;singleCurrentPrincipalAppAuthorized=$true;installedByBuilder=$false;pilotEnabled=$false;buildLog=$log.Substring($repoRoot.Length+1).Replace('\','/');sourceHashes=$sourceHashes}
[IO.File]::WriteAllText((Join-Path $artifactRoot 'manifest.json'),($manifest | ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
Write-Output 'L08_SINGLE_APP=BUILT_PRINCIPAL_ID_AND_EXISTING_SIGNER_VERIFIED_NOT_INSTALLED'
Write-Output ('L08_SINGLE_APP_SHA256='+$manifest.sha256)
