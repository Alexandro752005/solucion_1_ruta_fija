[CmdletBinding()]
param(
    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'frontend'
$appRoot = Join-Path $frontendRoot 'src\app'
$authModelPath = Join-Path $appRoot 'core\auth\auth.models.ts'
$routesPath = Join-Path $appRoot 'app.routes.ts'
$managementApiPath = Join-Path $appRoot 'core\management\management-api.service.ts'

foreach ($path in @($frontendRoot, $appRoot, $authModelPath, $routesPath, $managementApiPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "F2.3 no encontrÃ³ el archivo o directorio requerido: $path"
    }
}

$activeSourceFiles = @(
    Get-ChildItem -LiteralPath $appRoot -Recurse -File -Include '*.ts', '*.html', '*.scss' |
        Where-Object { $_.Name -notlike '*.spec.ts' }
)
if ($activeSourceFiles.Count -eq 0) {
    throw 'F2.3 no encontrÃ³ fuentes activas de Angular para auditar.'
}

$retiredPattern = '(?i)(\bADMINISTRADOR\b|\bCOORDINADOR\b|\bcoordinators\b|\bassignCoordinator\b|\bremoveCoordinator\b|\bisAdministrator\b|\bisCoordinator\b)'
$retiredMatches = @(
    Select-String -LiteralPath $activeSourceFiles.FullName -Pattern $retiredPattern -ErrorAction Stop
)
if ($retiredMatches.Count -gt 0) {
    $locations = ($retiredMatches | Select-Object -First 8 | ForEach-Object {
        "$($_.Path):$($_.LineNumber)"
    }) -join ', '
    throw "F2.3 detectÃ³ roles o UI de coordinador retirados en Angular: $locations"
}

$roles = Get-Content -LiteralPath $authModelPath -Raw
foreach ($expectedRole in @('SUPER_ADMIN', 'ADMIN', 'CONDUCTOR')) {
    if ($roles -notmatch ("'" + [regex]::Escape($expectedRole) + "'")) {
        throw "F2.3 no encontrÃ³ el rol activo $expectedRole en auth.models.ts."
    }
}

$routes = Get-Content -LiteralPath $routesPath -Raw
$adminRoutePattern = "data:\s*\{\s*roles:\s*\[\s*'ADMIN'\s*\]\s*\}"
$adminRouteCount = [regex]::Matches($routes, $adminRoutePattern).Count
if ($adminRouteCount -ne 10) {
    throw "F2.3 esperaba 10 rutas de tenant exclusivas de ADMIN y encontrÃ³ $adminRouteCount."
}

$managementApi = Get-Content -LiteralPath $managementApiPath -Raw
if ($managementApi -match '(?i)coordinator') {
    throw 'F2.3 detectÃ³ una operaciÃ³n de coordinador en ManagementApiService.'
}

if (-not $SkipTests) {
    $npm = Get-Command npm.cmd -ErrorAction Stop
    Push-Location $frontendRoot
    try {
        & $npm.Source run typecheck
        if ($LASTEXITCODE -ne 0) {
            throw 'F2.3 no superÃ³ el typecheck de Angular.'
        }
        & $npm.Source test -- --watch=false
        if ($LASTEXITCODE -ne 0) {
            throw 'F2.3 no superÃ³ las pruebas unitarias de Angular.'
        }
    }
    finally {
        Pop-Location
    }
}

$testState = if ($SkipTests) { 'omitidos' } else { 'aprobados' }
Write-Output "F2_3_FRONTEND_AUDIT=PASS active_legacy_roles=0 coordinator_ui=0 admin_routes=$adminRouteCount tests=$testState docker=0"
