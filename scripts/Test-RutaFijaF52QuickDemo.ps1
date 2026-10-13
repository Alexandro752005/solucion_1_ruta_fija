[CmdletBinding()]
param(
    [switch]$RequireLive
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$demoScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaQuickDemo.ps1'
$installerScript = Join-Path $PSScriptRoot 'Install-RutaFijaCloudflared.ps1'
$runtimeScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeRuntime.ps1'
$cloudflared = Join-Path $repoRoot '.tools\cloudflared.exe'
$demoState = Join-Path $repoRoot '.runtime\ruta-fija-quick-demo.json'

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw ('F5.2 no encontró el archivo requerido: {0}' -f $RelativePath)
    }
    return (Get-Content -LiteralPath $path -Raw -Encoding UTF8).TrimStart([char]0xFEFF)
}

function Assert-Contains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -notmatch $Pattern) {
        throw ('F5.2 no satisface: {0}' -f $Description)
    }
}

foreach ($script in @($demoScript, $installerScript, $runtimeScript)) {
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) {
        throw ('F5.2 no encontró {0}.' -f $script)
    }
    $tokens = $null
    $parseErrors = $null
    [Management.Automation.Language.Parser]::ParseFile(
        $script,
        [ref]$tokens,
        [ref]$parseErrors
    ) | Out-Null
    if ($parseErrors.Count -gt 0) {
        throw ('F5.2 detectó sintaxis PowerShell inválida en {0}: {1}' -f $script, $parseErrors[0].Message)
    }
}

$demo = Get-Utf8Text 'scripts\Invoke-RutaFijaQuickDemo.ps1'
foreach ($rule in @(
    @('--url.+\$localCrmOrigin', 'el túnel apunta solo al CRM local'),
    @('commandLine -match .5432\|postgres.', 'el túnel rechaza referencias a PostgreSQL'),
    @('Assert-LoopbackListener -Port 5432', 'PostgreSQL debe escuchar solo en loopback'),
    @('Assert-LoopbackListener -Port 8080', 'backend debe escuchar solo en loopback'),
    @('Assert-LoopbackListener -Port 4300', 'CRM debe escuchar solo en loopback'),
    @('Test-PublicApiRoute', 'la ruta API pública se comprueba'),
    @('Stop-ManagedCloudflared', 'el túnel tiene cierre controlado')
)) {
    Assert-Contains -Text $demo -Pattern $rule[0] -Description $rule[1]
}

$runtime = Get-Utf8Text 'scripts\Invoke-RutaFijaNativeRuntime.ps1'
foreach ($rule in @(
    @('PublicDemoOrigin', 'origen temporal explícito'),
    @('trycloudflare\.com', 'solo se permite el dominio Quick Tunnel'),
    @('REFRESH_COOKIE_SECURE.\] = .true.', 'cookie de renovación Secure durante HTTPS'),
    @('frontendConfiguration', 'la demo selecciona configuración Angular de producción'),
    @('--server\.address=127\.0\.0\.1', 'backend enlazado a loopback'),
    @('.--host., .127\.0\.0\.1.', 'CRM enlazado a loopback')
)) {
    Assert-Contains -Text $runtime -Pattern $rule[0] -Description $rule[1]
}

$proxy = Get-Utf8Text 'frontend\proxy.conf.json'
Assert-Contains -Text $proxy -Pattern '/api' -Description 'proxy de API por mismo origen'
Assert-Contains -Text $proxy -Pattern '/ws' -Description 'proxy WebSocket por mismo origen'
Assert-Contains -Text $proxy -Pattern 'ws.\s*:\s*true' -Description 'upgrade WebSocket habilitado'
$angular = Get-Utf8Text 'frontend\angular.json' | ConvertFrom-Json
if ($angular.projects.'ruta-fija-crm'.architect.serve.configurations.production.proxyConfig -ne 'proxy.conf.json') {
    throw 'F5.2 requiere el proxy API/WebSocket también en la configuración Angular de producción.'
}

$mobileEnvironment = Get-Utf8Text 'mobile\lib\app\ruta_fija_environment.dart'
Assert-Contains -Text $mobileEnvironment -Pattern '_isReleaseBuild && uri\.scheme != .https.' -Description 'Flutter release exige HTTPS'

foreach ($batchFile in @('iniciar_demo_temporal.bat', 'finalizar_demo_temporal.bat')) {
    $batch = Get-Utf8Text $batchFile
    Assert-Contains -Text $batch -Pattern 'Invoke-RutaFijaQuickDemo\.ps1' -Description ('control F5.2 desde {0}' -f $batchFile)
}

if (-not (Test-Path -LiteralPath $cloudflared -PathType Leaf)) {
    throw 'F5.2 requiere el cloudflared portátil verificado.'
}
$signature = Get-AuthenticodeSignature -FilePath $cloudflared
if ($signature.Status -ne [Management.Automation.SignatureStatus]::Valid -or
    $null -eq $signature.SignerCertificate -or
    $signature.SignerCertificate.Subject -notmatch 'O=.Cloudflare, Inc\.') {
    throw 'F5.2 rechazó la firma del cloudflared portátil.'
}
$version = (& $cloudflared --version 2>&1).Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'F5.2 no pudo leer la versión de cloudflared.'
}

$live = 'NOT_RUNNING'
if (Test-Path -LiteralPath $demoState -PathType Leaf) {
    $null = & $demoScript -Action Test
    $live = 'PASS'
} elseif ($RequireLive) {
    throw 'F5.2 requiere una demo activa para aprobar la comprobación pública.'
}

Write-Output (
    'F5_2_QUICK_DEMO_AUDIT=PASS design=CRM_API_ONLY postgresql=PRIVATE ' +
    'origin=LOOPBACK angular=PRODUCTION websocket=SUPPORTED_CONFIGURED ' +
    'cloudflared_signature=VALID live={0}' -f
    $live
)
Write-Output ('CLOUDFLARED_VERSION={0}' -f $version)
