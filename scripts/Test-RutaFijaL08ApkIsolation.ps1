[CmdletBinding()]
param([switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not $Execute){Write-Output 'L08_APK_ISOLATION=PLAN_NO_BUILD_NO_INSTALL';return}
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'RutaFijaL08ApkIdentity.ps1')
$aapt=Get-ChildItem -LiteralPath 'D:\Android\Sdk\build-tools' -Filter 'aapt2.exe' -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
$samples=@(
    @{relative='artifacts/L-08-fis02-2026-10-22/ruta-fija-l08-fis02-debug.apk';expectedAccept=$false},
    @{relative='artifacts/L-08-fis02-2026-10-22/correccion-paquete/ruta-fija-l08-fis02-debug.apk';expectedAccept=$true}
)
$checks=@()
foreach($sample in $samples){
    $file=Join-Path $repoRoot $sample.relative
    $badging=& $aapt.FullName dump badging $file
    if($LASTEXITCODE -ne 0){throw 'APK_METADATA_FAILED'}
    $package=[regex]::Match(($badging -join "`n"),"package: name='([^']+)'")
    $accepted=$false
    try{$identity=Get-RutaFijaL08VerifiedApk -LiteralPath $file;$accepted=$true}catch{if($_.Exception.Message -ne 'WRONG_APPLICATION_ID_REFUSED_NO_INSTALL'){throw}}
    if($accepted -ne $sample.expectedAccept){throw 'APK_ISOLATION_REGRESSION'}
    $checks+=@{file=$sample.relative;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant();actualPackage=$package.Groups[1].Value;acceptedAsL08=$accepted;result='PASS'}
}
$builder=Join-Path $PSScriptRoot 'Build-RutaFijaL08PhysicalApk.ps1'
$builderText=[IO.File]::ReadAllText($builder)
if(-not $builderText.Contains('--android-project-arg=rutaFijaL08Isolated=true') -or -not $builderText.Contains('Get-RutaFijaL08VerifiedApk -LiteralPath $apk')){throw 'EXPLICIT_BUILD_AND_METADATA_GUARDS_REQUIRED'}
$astChecks=@()
foreach($script in @('Build-RutaFijaL08PhysicalApk.ps1','RutaFijaL08ApkIdentity.ps1','Invoke-RutaFijaL08PhysicalComplement.ps1','Invoke-RutaFijaL08DesktopReadOnly.ps1','Invoke-RutaFijaL08Rest03Database.ps1','Invoke-RutaFijaL08Rest03Business.ps1')){
    $tokens=$null;$parseErrors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $script),[ref]$tokens,[ref]$parseErrors)
    if($parseErrors.Count -ne 0){throw 'POWERSHELL_PARSE_ERRORS'}
    $astChecks+=@{script=$script;parseErrors=0}
}
$result=[ordered]@{observedAt=[DateTimeOffset]::UtcNow.ToString('o');result='PASS_SHARED_BUILDER_GUARD_REJECTS_WRONG_APK_AND_ACCEPTS_L08_NO_INSTALL';actualBuilderGuardInvoked=$true;apkChecks=$checks;scriptChecks=$astChecks;installationsExecuted=0;accountsCreated=0;databaseConnections=0;buildsExecuted=0;principalPreviousBinaryRestored=$false}
$output=Join-Path $repoRoot 'docs/evidencia/L-08/incremento-3-escritorio/pruebas/guardas-apk-compartida.json'
if(Test-Path -LiteralPath $output){throw 'KEEP_PRIOR_EVIDENCE'}
[IO.File]::WriteAllText($output,($result | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
Write-Output ('L08_APK_ISOLATION='+$result.result)
