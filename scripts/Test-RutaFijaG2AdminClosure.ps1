[CmdletBinding()]
param(
    [switch]$SkipExecutionChecks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendMainRoot = Join-Path $repoRoot 'backend\src\main\java'
$frontendAuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF23AdminUi.ps1'
$schemaAuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaMigratedSchemaF13.ps1'
$securityAuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaNativeSecurityF16.ps1'
$nativeTestScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeTest.ps1'
$proxySmokeScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaRealtimeProxySmoke.ps1'
$userRolePath = Join-Path $backendMainRoot 'pe\rutafija\identity\domain\UserRole.java'
$historyRepositoryPath = Join-Path $backendMainRoot 'pe\rutafija\fleet\infrastructure\GroupCoordinatorHistoryRepository.java'
$historyEntityPath = Join-Path $backendMainRoot 'pe\rutafija\fleet\domain\GroupCoordinator.java'
$historyIdPath = Join-Path $backendMainRoot 'pe\rutafija\fleet\domain\GroupCoordinatorId.java'
$fleetIntegrationTestPath = Join-Path $repoRoot 'backend\src\test\java\pe\rutafija\fleet\api\FleetManagementIT.java'
$authIntegrationTestPath = Join-Path $repoRoot 'backend\src\test\java\pe\rutafija\identity\api\AuthFlowIT.java'
$operationIntegrationTestPath = Join-Path $repoRoot 'backend\src\test\java\pe\rutafija\operation\api\OperationFlowIT.java'

function Assert-PathExists {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "G2 no encontro el archivo requerido: $Path"
    }
}

function Get-RelativePath {
    param([Parameter(Mandatory)][string]$Path)

    $rootPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $rootPath + [IO.Path]::DirectorySeparatorChar
    if ($fullPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        return $fullPath.Substring($prefix.Length).Replace('\\', '/')
    }
    return $fullPath.Replace('\\', '/')
}

foreach ($path in @(
        $frontendAuditScript,
        $schemaAuditScript,
        $securityAuditScript,
        $nativeTestScript,
        $proxySmokeScript,
        $userRolePath,
        $historyRepositoryPath,
        $historyEntityPath,
        $historyIdPath,
        $fleetIntegrationTestPath,
        $authIntegrationTestPath,
        $operationIntegrationTestPath
    )) {
    Assert-PathExists -Path $path
}

$userRoleSource = Get-Content -LiteralPath $userRolePath -Raw
if ($userRoleSource -notmatch '(?s)public\s+enum\s+UserRole\s*\{\s*SUPER_ADMIN,\s*ADMIN,\s*CONDUCTOR\s*\}') {
    throw 'G2 no encontro exactamente los roles activos SUPER_ADMIN, ADMIN y CONDUCTOR.'
}
if ($userRoleSource -cmatch '\b(ADMINISTRADOR|COORDINADOR)\b') {
    throw 'G2 detecto un rol retirado dentro del enum activo.'
}

$backendSourceFiles = @(
    Get-ChildItem -LiteralPath $backendMainRoot -Recurse -File -Filter '*.java'
)
if ($backendSourceFiles.Count -eq 0) {
    throw 'G2 no encontro fuentes Java para auditar.'
}

$retiredRoleMatches = @(
    Select-String -LiteralPath $backendSourceFiles.FullName -Pattern '\b(ADMINISTRADOR|COORDINADOR)\b' -CaseSensitive
)
if ($retiredRoleMatches.Count -gt 0) {
    $locations = ($retiredRoleMatches | Select-Object -First 8 | ForEach-Object {
        "$(Get-RelativePath -Path $_.Path):$($_.LineNumber)"
    }) -join ', '
    throw "G2 detecto roles retirados en Java productivo: $locations"
}

$retiredRouteMatches = @(
    Select-String -LiteralPath $backendSourceFiles.FullName -Pattern '/[^"''\s]*(?:coordinator|coordinators)' -CaseSensitive:$false
)
if ($retiredRouteMatches.Count -gt 0) {
    $locations = ($retiredRouteMatches | Select-Object -First 8 | ForEach-Object {
        "$(Get-RelativePath -Path $_.Path):$($_.LineNumber)"
    }) -join ', '
    throw "G2 detecto una ruta productiva de coordinadores: $locations"
}

$allowedHistoryPaths = @(
    [IO.Path]::GetFullPath($historyRepositoryPath),
    [IO.Path]::GetFullPath($historyEntityPath),
    [IO.Path]::GetFullPath($historyIdPath)
)
$groupHistoryMatches = @(
    Select-String -LiteralPath $backendSourceFiles.FullName -Pattern '\bGroupCoordinator\b|\bgroup_coordinator\b'
)
$unexpectedHistoryMatches = @(
    $groupHistoryMatches | Where-Object {
        [IO.Path]::GetFullPath($_.Path) -notin $allowedHistoryPaths
    }
)
if ($unexpectedHistoryMatches.Count -gt 0) {
    $locations = ($unexpectedHistoryMatches | Select-Object -First 8 | ForEach-Object {
        "$(Get-RelativePath -Path $_.Path):$($_.LineNumber)"
    }) -join ', '
    throw "G2 detecto uso funcional de group_coordinator: $locations"
}

$historyRepositorySource = Get-Content -LiteralPath $historyRepositoryPath -Raw
if ($historyRepositorySource -notmatch 'extends\s+Repository\s*<' -or
    $historyRepositorySource -notmatch 'findAllByGroup_Id') {
    throw 'G2 no pudo confirmar el repositorio historico de solo lectura.'
}
if ($historyRepositorySource -match '(?m)^\s*(?:void|[A-Za-z0-9_<>?, ]+)\s+(?:save|delete|remove)[A-Za-z0-9_]*\s*\(') {
    throw 'G2 detecto una operacion de escritura en el repositorio historico.'
}

$fleetIntegrationTest = Get-Content -LiteralPath $fleetIntegrationTestPath -Raw
$authIntegrationTest = Get-Content -LiteralPath $authIntegrationTestPath -Raw
$operationIntegrationTest = Get-Content -LiteralPath $operationIntegrationTestPath -Raw
if ($fleetIntegrationTest -notmatch 'everyAdminSeesAndManagesTheEntireTenantWithoutGroupMembership' -or
    $fleetIntegrationTest -notmatch 'status\(\)\.isNotFound\(\)' -or
    $fleetIntegrationTest -notmatch 'coordinators"\)\.doesNotExist') {
    throw 'G2 no encontro la regresion de tenant ni la retirada de respuesta de coordinadores.'
}
if ($authIntegrationTest -notmatch 'openApiReflectsTheAdminRoleContractAndRetiresCoordinatorEndpoints' -or
    $authIntegrationTest -notmatch 'isMissingNode\(\)') {
    throw 'G2 no encontro la regresion OpenAPI del contrato ADMIN.'
}
if ($operationIntegrationTest -notmatch 'AUTH_TOKEN_INVALID') {
    throw 'G2 no encontro la regresion de invalidacion de token por cambio de rol.'
}

$operationalEntrypoints = @(
    (Join-Path $repoRoot 'iniciar_ruta_fija.bat'),
    (Join-Path $repoRoot 'finalizar_ruta_fija.bat'),
    (Join-Path $repoRoot 'verificar_ruta_fija.bat'),
    (Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeRuntime.ps1')
)
foreach ($entrypoint in $operationalEntrypoints) {
    Assert-PathExists -Path $entrypoint
    $content = Get-Content -LiteralPath $entrypoint -Raw
    if ($content -match '(?im)\b(docker\s+compose|docker\.exe|docker\s+desktop|testcontainers)\b') {
        throw "G2 detecto una dependencia Docker operativa en $(Get-RelativePath -Path $entrypoint)."
    }
}

$verificationScope = 'estructural'
if (-not $SkipExecutionChecks) {
    & $frontendAuditScript
    & $schemaAuditScript
    & $securityAuditScript
    & $nativeTestScript -Action Verify
    & $proxySmokeScript -Action Verify
    $verificationScope = 'completa'
}

Write-Output "G2_ADMIN_CONSOLIDATION=PASS roles=SUPER_ADMIN,ADMIN,CONDUCTOR group_coordinator=history tenant=isolation legacy_sessions=rejected docker=0 checks=$verificationScope"
