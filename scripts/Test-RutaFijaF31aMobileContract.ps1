[CmdletBinding()]
param(
    [switch]$RequirePreImplementation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$adrPath = Join-Path $repoRoot 'docs\decisiones\ADR-004-contrato-movil-estados-y-ubicacion.md'
$assignmentStatusPath = Join-Path $repoRoot 'backend\src\main\java\pe\rutafija\operation\domain\AssignmentStatus.java'
$driverAvailabilityPath = Join-Path $repoRoot 'backend\src\main\java\pe\rutafija\fleet\domain\DriverAvailabilityStatus.java'
$vehicleStatusPath = Join-Path $repoRoot 'backend\src\main\java\pe\rutafija\fleet\domain\VehicleStatus.java'
$migrationRoot = Join-Path $repoRoot 'backend\src\main\resources\db\migration'
$backendSourceRoot = Join-Path $repoRoot 'backend\src\main\java'

function Assert-PathExists {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "F3.1A no encontro el archivo requerido: $Path"
    }
}

foreach ($path in @($adrPath, $assignmentStatusPath, $driverAvailabilityPath, $vehicleStatusPath)) {
    Assert-PathExists -Path $path
}
if (-not (Test-Path -LiteralPath $migrationRoot -PathType Container)) {
    throw "F3.1A no encontro las migraciones Flyway: $migrationRoot"
}
if (-not (Test-Path -LiteralPath $backendSourceRoot -PathType Container)) {
    throw "F3.1A no encontro el codigo Java: $backendSourceRoot"
}

$adr = Get-Content -LiteralPath $adrPath -Raw
foreach ($requiredText in @(
    'ADMIN_DIRECT',
    'MOBILE_CONFIRMATION',
    'PENDING_RESPONSE',
    'accepted_at',
    'rejected_at',
    'driver_current_location',
    'No se crea una tabla de historial',
    'location_consent',
    'PostgreSQL seguirá privado en `127.0.0.1:5432`',
    'no se introduce Docker'
)) {
    if (-not $adr.Contains($requiredText)) {
        throw "F3.1A no encontro la decision contractual requerida: $requiredText"
    }
}

$vehicleStatus = Get-Content -LiteralPath $vehicleStatusPath -Raw
if ($vehicleStatus -notmatch '(?s)enum\s+VehicleStatus\s*\{\s*DISPONIBLE,\s*EN_SERVICIO,\s*MANTENIMIENTO,\s*INACTIVO;') {
    throw 'F3.1A no pudo confirmar los cuatro estados permitidos del vehiculo.'
}
if ($vehicleStatus -match '\bRESERVADO\b') {
    throw 'F3.1A detecto RESERVADO como estado de vehiculo.'
}

$driverAvailability = Get-Content -LiteralPath $driverAvailabilityPath -Raw
if ($driverAvailability -notmatch '(?s)enum\s+DriverAvailabilityStatus\s*\{\s*DISPONIBLE,\s*RESERVADO,\s*EN_SERVICIO,\s*DESCANSO,\s*NO_DISPONIBLE') {
    throw 'F3.1A no pudo confirmar el ciclo vigente de disponibilidad del conductor.'
}

$phase = 'contract_only'
if ($RequirePreImplementation) {
    $expectedMigrationNames = @(
        'V1__identity_organization_audit.sql',
        'V2__fleet_administration.sql',
        'V3__operation_assignments_incidents_announcements.sql',
        'V4__remove_mobile_only_driver_status.sql',
        'V5__assignment_overlap_exclusion_constraints.sql',
        'V6__unify_administrative_roles_to_admin.sql'
    )
    $actualMigrationNames = @(
        Get-ChildItem -LiteralPath $migrationRoot -File -Filter 'V*__*.sql' |
            Sort-Object Name |
            Select-Object -ExpandProperty Name
    )
    if ((Compare-Object -ReferenceObject $expectedMigrationNames -DifferenceObject $actualMigrationNames)) {
        throw 'F3.1A exige que V7 y V8 aun no esten aplicadas ni presentes como archivos Flyway.'
    }

    $assignmentStatus = Get-Content -LiteralPath $assignmentStatusPath -Raw
    if ($assignmentStatus -notmatch '(?s)enum\s+AssignmentStatus\s*\{\s*SCHEDULED,\s*EN_SERVICIO,\s*COMPLETED,\s*CANCELLED') {
        throw 'F3.1A exige el estado de codigo previo a V7 para evitar una implementacion prematura.'
    }

    $backendSources = @(Get-ChildItem -LiteralPath $backendSourceRoot -Recurse -File -Filter '*.java')
    $mobileRouteMatches = @(
        Select-String -LiteralPath $backendSources.FullName -Pattern '[''\"]/mobile/' -ErrorAction Stop
    )
    if ($mobileRouteMatches.Count -gt 0) {
        throw 'F3.1A detecto una ruta /mobile/ antes de F3.2/F3.3.'
    }

    $operationalEntrypoints = @(
        (Join-Path $repoRoot 'iniciar_ruta_fija.bat'),
        (Join-Path $repoRoot 'finalizar_ruta_fija.bat'),
        (Join-Path $repoRoot 'verificar_ruta_fija.bat')
    )
    foreach ($entrypoint in $operationalEntrypoints) {
        Assert-PathExists -Path $entrypoint
        if ((Get-Content -LiteralPath $entrypoint -Raw) -match '(?im)\b(docker\s+compose|docker\.exe|docker\s+desktop)\b') {
            throw "F3.1A detecto dependencia Docker operativa en $entrypoint."
        }
    }
    $phase = 'pre_implementation'
}

Write-Output "F3_1A_CONTRACT=PASS phase=$phase response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only consent=required vehicle_reserved=0 docker=0"
