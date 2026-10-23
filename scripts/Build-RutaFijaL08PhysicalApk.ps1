[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if(-not $Execute){Write-Output 'L08_FIS02_APK=PLAN_NO_BUILD_NO_INSTALL';return}
$network=Get-Content -LiteralPath (Join-Path $repoRoot 'backend/.local/l08-fis02-network.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($network.runId -ne '3f0ba758-6eb9-4d2f-b948-dd1c7de3c430' -or $network.bindIpv4 -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)[0-9.]+$'){throw 'EXACT_AUTHORIZED_PRIVATE_TARGET_REQUIRED'}
$artifactRoot=Join-Path $repoRoot 'artifacts/L-08-fis02-2026-10-22/correccion-paquete'
if(Test-Path -LiteralPath $artifactRoot){throw 'EXISTING_ARTIFACT_KEEP_AND_REVIEW'}
$log=Join-Path $repoRoot ('backend/target/l08-fis02-package-correction-'+[Guid]::NewGuid().ToString()+'.log')
$mobile=Join-Path $repoRoot 'mobile'
if(-not (Test-Path -LiteralPath (Join-Path $mobile 'pubspec.yaml'))){throw 'EXACT_MOBILE_WORKSPACE_REQUIRED'}
Push-Location -LiteralPath $mobile
try{
    $savedPreference=$ErrorActionPreference
    try{
        $ErrorActionPreference='Continue'
        & flutter build apk --debug --no-pub --android-project-arg=rutaFijaL08Isolated=true ('--dart-define=RF_API_BASE_URL=http://{0}:4311/api/v1' -f $network.bindIpv4) *> $log
        $buildExit=$LASTEXITCODE
    }finally{$ErrorActionPreference=$savedPreference}
}finally{Pop-Location}
if($buildExit -ne 0){throw 'BUILD_FAILED_KEEP_LOG_NO_INSTALL'}
$apk=Join-Path $mobile 'build/app/outputs/flutter-apk/app-debug.apk'
. (Join-Path $PSScriptRoot 'RutaFijaL08ApkIdentity.ps1')
$identity=Get-RutaFijaL08VerifiedApk -LiteralPath $apk
New-Item -ItemType Directory -Path $artifactRoot -ErrorAction Stop | Out-Null
$destination=Join-Path $artifactRoot 'ruta-fija-l08-fis02-debug.apk'
Copy-Item -LiteralPath $apk -Destination $destination -ErrorAction Stop
$manifest=[ordered]@{runId=$network.runId;artifact='ruta-fija-l08-fis02-debug.apk';actualPackage=$identity.package;packageVerifiedBeforeAnyInstallation=$true;sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant();buildType='debug';gradleIsolationArgument='--android-project-arg=rutaFijaL08Isolated=true';apiTarget='EXACT_AUTHORIZED_PRIVATE_PC_INTERFACE_4311';buildExitCode=$buildExit;installedByBuilder=$false;pilotEnabled=$false}
[IO.File]::WriteAllText((Join-Path $artifactRoot 'manifest.json'),($manifest | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output 'L08_FIS02_APK=BUILT_AND_PACKAGE_VERIFIED_NOT_INSTALLED'
Write-Output ('L08_FIS02_APK_SHA256='+$manifest.sha256)
