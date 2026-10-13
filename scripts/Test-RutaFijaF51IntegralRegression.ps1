[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$mobileRoot = Join-Path $repoRoot 'mobile'
$nativeTestScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeTest.ps1'
$postgresqlScript = Join-Path $PSScriptRoot 'Set-RutaFijaPostgresqlService.ps1'
$serviceName = 'postgresql-x64-16'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $backendRoot '.local\ruta-fija-native.env'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F5.1 no encontro el archivo requerido: $RelativePath"
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
        throw "F5.1 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "F5.1 detecto una automatizacion no autorizada: $Description"
    }
}

function Resolve-FlutterCommand {
    param([string]$RequestedRoot)

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($RequestedRoot)) {
        $candidates += (Join-Path $RequestedRoot 'bin\flutter.bat')
    }
    if (-not [string]::IsNullOrWhiteSpace($env:FLUTTER_ROOT)) {
        $candidates += (Join-Path $env:FLUTTER_ROOT 'bin\flutter.bat')
    }
    $candidates += 'D:\dev\flutter\bin\flutter.bat'
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw 'F5.1 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)][scriptblock]$Command,
        [Parameter(Mandatory)][string]$FailureMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

function Get-JunitSummary {
    param(
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][datetime]$NotBefore,
        [switch]$ExcludeIntegrationTests
    )

    $summary = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return $summary
    }
    foreach ($report in Get-ChildItem -LiteralPath $Directory -Filter 'TEST-*.xml' -File) {
        if ($report.LastWriteTime -lt $NotBefore) {
            continue
        }
        if ($ExcludeIntegrationTests -and $report.Name -match 'IT\.xml$') {
            continue
        }
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw -Encoding UTF8
        $suite = $document.testsuite
        foreach ($name in @('tests', 'failures', 'errors', 'skipped')) {
            if ($null -ne $suite.$name) {
                $summary[$name] += [int]$suite.$name
            }
        }
    }
    return $summary
}

foreach ($requiredPath in @(
    $nativeTestScript,
    $postgresqlScript,
    $backendRoot,
    $frontendRoot,
    $mobileRoot,
    $ConfigPath
)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "F5.1 no encontro el recurso requerido: $requiredPath"
    }
}

$service = Get-Service -Name $serviceName -ErrorAction Stop
$startedPostgresql = $service.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Running
$previousMavenOptions = [Environment]::GetEnvironmentVariable('MAVEN_OPTS', 'Process')
$previousNodeOptions = [Environment]::GetEnvironmentVariable('NODE_OPTIONS', 'Process')

try {
    if ($startedPostgresql) {
        & $postgresqlScript -Action Start
    }

    [Environment]::SetEnvironmentVariable(
        'MAVEN_OPTS',
        '-Xms96m -Xmx768m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC',
        'Process'
    )
    $backendRunStartedAt = Get-Date
    & $nativeTestScript -Action Verify -ConfigPath $ConfigPath

    $unit = Get-JunitSummary `
        -Directory (Join-Path $backendRoot 'target\surefire-reports') `
        -NotBefore $backendRunStartedAt `
        -ExcludeIntegrationTests
    $integration = Get-JunitSummary `
        -Directory (Join-Path $backendRoot 'target\failsafe-reports') `
        -NotBefore $backendRunStartedAt
    if (($unit.failures + $unit.errors + $integration.failures + $integration.errors) -ne 0) {
        throw 'F5.1 detecto fallos en los reportes JUnit del backend.'
    }

    [Environment]::SetEnvironmentVariable('NODE_OPTIONS', '--max-old-space-size=512', 'Process')
    Push-Location $frontendRoot
    try {
        Invoke-CheckedCommand -FailureMessage 'F5.1 no aprobo el typecheck Angular.' -Command {
            & npm.cmd run typecheck
        }
        Invoke-CheckedCommand -FailureMessage 'F5.1 no aprobo las pruebas Angular.' -Command {
            & npm.cmd run test:ci
        }
        Invoke-CheckedCommand -FailureMessage 'F5.1 no aprobo el bundle Angular.' -Command {
            & npm.cmd run build
        }
    } finally {
        Pop-Location
    }

    $flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
    Push-Location $mobileRoot
    try {
        Invoke-CheckedCommand -FailureMessage 'F5.1 no aprobo flutter analyze.' -Command {
            & $flutter analyze
        }
        Invoke-CheckedCommand -FailureMessage 'F5.1 no aprobo las pruebas Flutter.' -Command {
            & $flutter test
        }
    } finally {
        Pop-Location
    }

    $mobileFlow = Get-Utf8Text 'backend\src\test\java\pe\rutafija\operation\api\MobileOperationFlowIT.java'
    foreach ($requiredPattern in @(
        'post\("/api/v1/assignments"\)',
        'post\("/api/v1/mobile/assignments/\{id\}/accept"',
        'post\("/api/v1/mobile/assignments/\{id\}/start"',
        'post\("/api/v1/mobile/assignments/\{id\}/complete"',
        'get\("/api/v1/reports/assignments"\)',
        'ASSIGNMENT_MOBILE_COMPLETED',
        'get\("/api/v1/reports/incidents"\)',
        'INCIDENT_REPORTED_FROM_MOBILE',
        'RESOURCE_NOT_FOUND'
    )) {
        Assert-Contains -Text $mobileFlow -Pattern $requiredPattern -Description "flujo integral movil: $requiredPattern"
    }

    $webFlow = Get-Utf8Text 'backend\src\test\java\pe\rutafija\operation\api\OperationFlowIT.java'
    foreach ($requiredPattern in @(
        '/reports/assignments/export',
        'format", "xlsx"',
        '/reports/incidents/export',
        'format", "pdf"',
        '/api/v1/audit-events',
        'administratorB',
        'RESOURCE_NOT_FOUND'
    )) {
        Assert-Contains -Text $webFlow -Pattern $requiredPattern -Description "regresion CRM: $requiredPattern"
    }

    $csvTest = Get-Utf8Text 'frontend\src\app\features\reports\reports.page.spec.ts'
    Assert-Contains -Text $csvTest -Pattern 'neutraliza formulas antes de crear un CSV' -Description 'CSV seguro probado'
    $reportView = Get-Utf8Text 'frontend\src\app\features\reports\reports.page.html'
    foreach ($format in @('PDF', 'Excel', 'CSV')) {
        Assert-Contains -Text $reportView -Pattern ">$format<" -Description "accion visible de reporte $format"
    }

    $realtimeTest = Get-Utf8Text 'backend\src\test\java\pe\rutafija\operation\realtime\OperationStreamBroadcasterTest.java'
    Assert-Contains -Text $realtimeTest -Pattern 'otherTenantAdminSession' -Description 'WebSocket aislado por tenant'

    $scheduledFiles = @(Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src\main\java') -Recurse -File -Filter '*.java' |
        Select-String -Pattern '@Scheduled')
    if ($scheduledFiles.Count -ne 1) {
        throw "F5.1 esperaba una unica automatizacion programada controlada y encontro $($scheduledFiles.Count)."
    }
    $maintenance = Get-Utf8Text 'backend\src\main\java\pe\rutafija\operation\application\MobileOperationMaintenanceService.java'
    Assert-Contains -Text $maintenance -Pattern 'expireIfDue' -Description 'unica caducidad PENDING_RESPONSE controlada'
    Assert-NotContains -Text $maintenance -Pattern 'reserveForAssignment|beginService|completeService|finishService|changeAdministrativeAvailability' -Description 'cambio automatico de disponibilidad o servicio'

    $flutterTestCount = @(
        Get-ChildItem -LiteralPath (Join-Path $mobileRoot 'test') -Recurse -File -Filter '*.dart' |
            Select-String -Pattern '^\s*test(?:Widgets)?\('
    ).Count

    Write-Output (
        'F5_1_INTEGRAL_REGRESSION=PASS ' +
        "backend_unit=$($unit.tests) backend_integration=$($integration.tests) " +
        "frontend=typecheck,test,build mobile=analyze,$flutterTestCount-tests " +
        'flow=ADMIN-CONDUCTOR-DB-CRM reports=PDF,XLSX,CSV audit=PASS multitenancy=PASS ' +
        'automation=expiry_only_without_fleet_mutation'
    )
} finally {
    [Environment]::SetEnvironmentVariable('MAVEN_OPTS', $previousMavenOptions, 'Process')
    [Environment]::SetEnvironmentVariable('NODE_OPTIONS', $previousNodeOptions, 'Process')
    if ($startedPostgresql) {
        & $postgresqlScript -Action Stop
    }
}
