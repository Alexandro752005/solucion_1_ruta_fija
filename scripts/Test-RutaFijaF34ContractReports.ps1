[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendSourceRoot = Join-Path $repoRoot 'backend\src\main\java'
$frontendSourceRoot = Join-Path $repoRoot 'frontend\src\app'
$f33AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF33MobileOperations.ps1'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Get-SourceText {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F3.4 no encontro la fuente requerida: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Assert-Contains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -notmatch $Pattern) {
        throw "F3.4 no satisface: $Description"
    }
}

if (-not (Test-Path -LiteralPath $f33AuditScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $backendSourceRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $frontendSourceRoot -PathType Container)) {
    throw 'F3.4 no encontro los artefactos base de contrato y auditoria.'
}

# Conserva las comprobaciones PostgreSQL nativas de V1-V10, permisos y privacidad
# de F3.3 antes de validar el contrato y los reportes que se apoyan en ellas.
& $f33AuditScript -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'La auditoria base F3.3 no fue aprobada; F3.4 no puede continuar.'
}

$openApi = Get-SourceText 'backend\src\main\java\pe\rutafija\shared\config\OpenApiConfig.java'
Assert-Contains -Text $openApi -Pattern 'CRM nunca simula esa respuesta' -Description 'frontera explicita entre CRM y respuesta autentica'
Assert-Contains -Text $openApi -Pattern 'X-Idempotent-Replay' -Description 'contrato de reintentos moviles'

$crmAssignments = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\api\AssignmentController.java'
Assert-Contains -Text $crmAssignments -Pattern 'MOBILE_CONFIRMATION' -Description 'compatibilidad CRM para solicitud movil'
if ($crmAssignments -match 'PostMapping\("/\{assignmentId\}/(accept|reject)"\)') {
    throw 'F3.4 detecto una accion CRM que simula respuesta del conductor.'
}

$mobileAssignments = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\api\MobileAssignmentController.java'
Assert-Contains -Text $mobileAssignments -Pattern '"/\{assignmentId\}/accept"' -Description 'aceptacion solo en ruta movil propia'
Assert-Contains -Text $mobileAssignments -Pattern '"/\{assignmentId\}/reject"' -Description 'rechazo solo en ruta movil propia'
Assert-Contains -Text $mobileAssignments -Pattern 'X-Idempotent-Replay' -Description 'cabecera de repeticion idempotente'

$reportService = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\application\ReportService.java'
Assert-Contains -Text $reportService -Pattern 'ASSIGNMENT_REPORT_STATUS_ORDER' -Description 'orden estable de estados de reporte'
foreach ($status in @('PENDING_RESPONSE', 'REJECTED', 'EXPIRED')) {
    Assert-Contains -Text $reportService -Pattern $status -Description "conteo real de $status"
}
Assert-Contains -Text $reportService -Pattern 'assignmentStatusResponse' -Description 'normalizacion de ceros reales para estados ausentes'
Assert-Contains -Text $reportService -Pattern 'actualTotals\.getOrDefault\(status, 0L\)' -Description 'cero explicito sin inventar filas'

$reportExport = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\application\ReportExportService.java'
Assert-Contains -Text $reportExport -Pattern 'reportService\.assignments\(from, to\)' -Description 'exportacion basada en el mismo reporte persistido'
Assert-Contains -Text $reportExport -Pattern 'appendTotals\(rows, report\.totalsByStatus\(\)\)' -Description 'exportacion de totales reales por estado'

$assignmentDto = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\api\dto\AssignmentResponse.java'
foreach ($field in @('responseMode', 'responseDeadlineAt', 'acceptedAt', 'rejectedAt', 'expiredAt')) {
    Assert-Contains -Text $assignmentDto -Pattern $field -Description "DTO explicito de $field"
}
$mobileCommandDto = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\api\dto\mobile\MobileAssignmentCommandRequest.java'
foreach ($field in @('clientEventId', 'version', 'occurredAt')) {
    Assert-Contains -Text $mobileCommandDto -Pattern $field -Description "DTO movil explicito de $field"
}

$frontendModels = Get-SourceText 'frontend\src\app\core\operations\operations.models.ts'
foreach ($status in @('PENDING_RESPONSE', 'REJECTED', 'EXPIRED')) {
    Assert-Contains -Text $frontendModels -Pattern $status -Description "CRM reconoce el estado $status"
}
Assert-Contains -Text $frontendModels -Pattern 'ASSIGNMENT_RESPONSE_MODES' -Description 'CRM distingue los modos de respuesta'
$assignmentPage = Get-SourceText 'frontend\src\app\features\assignments\assignments.page.ts'
Assert-Contains -Text $assignmentPage -Pattern 'assignment\.responseMode === ''ADMIN_DIRECT''' -Description 'CRM solo reserva directamente ADMIN_DIRECT'
Assert-Contains -Text $assignmentPage -Pattern 'assignment\.status === ''PENDING_RESPONSE''' -Description 'CRM permite cancelar una solicitud pendiente sin aceptarla'
$reportsPage = Get-SourceText 'frontend\src\app\features\reports\reports.page.ts'
foreach ($status in @('PENDING_RESPONSE', 'REJECTED', 'EXPIRED')) {
    Assert-Contains -Text $reportsPage -Pattern $status -Description "etiqueta CRM de reporte para $status"
}

$migrations = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend\src\main\resources\db\migration') -File -Filter 'V*__*.sql' |
        ForEach-Object {
            if ($_.Name -match '^V(\d+)__') { [int]$Matches[1] } else { -1 }
        } | Sort-Object -Unique)
if (($migrations -join ',') -ne '1,2,3,4,5,6,7,8,9,10') {
    throw 'F3.4 requiere conservar exactamente Flyway V1-V10; no debe introducir una migracion innecesaria.'
}

Write-Output 'F3_4_CONTRACT_REPORTS_AUDIT=PASS flyway=V1-V10 contract=openapi_crm_mobile reports=persisted privacy=current_only docker=0'
