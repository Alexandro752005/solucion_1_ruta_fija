[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $repoRoot 'mobile'
$backendRoot = Join-Path $repoRoot 'backend'
$m7AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaM7ForegroundLocation.ps1'
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "M8 no encontro el archivo requerido: $RelativePath"
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
        throw "M8 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "M8 incorpora fuera de alcance: $Description"
    }
}

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'M8 no encontro psql de PostgreSQL 16.'
    }
    return $psql
}

function Assert-V12Database {
    param([Parameter(Mandatory)][hashtable]$Settings)

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable(
            'PGPASSWORD',
            $Settings['SPRING_FLYWAY_PASSWORD'],
            'Process'
        )
        $output = & (Get-PsqlPath) -X -w -v ON_ERROR_STOP=1 -At -F '|' `
            -h 127.0.0.1 -p 5432 -U rf_migrator -d solucion_ruta_fija_1 -c @'
SELECT
    current_database(),
    current_user,
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank)
       FROM flyway_schema_history WHERE success AND version IS NOT NULL),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_type'
        AND pg_get_constraintdef(oid) LIKE '%AVAILABILITY_CHANGE%'),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_assignment_scope');
'@ 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazo la comprobacion fisica M8.'
        }
        $rows = @($output | Where-Object {
                $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_)
            })
        if ($rows.Count -ne 1 -or
            $rows[0].Trim() -ne 'solucion_ruta_fija_1|rf_migrator|1,2,3,4,5,6,7,8,9,10,11,12|t|t') {
            throw 'M8 no confirmo Flyway V1-V12 y las restricciones de recibo en desarrollo.'
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
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
    throw 'M8 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

if (-not (Test-Path -LiteralPath $m7AuditScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $nativeImportScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $mobileRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $backendRoot -PathType Container)) {
    throw 'M8 no encontro los artefactos base requeridos.'
}

# M8 cannot weaken the approved foreground privacy boundary or previous mobile
# modules. The predecessor already runs Flutter, API, database and APK checks.
& $m7AuditScript -FlutterRoot $FlutterRoot -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'M8 requiere M7 aprobado como linea base.'
}

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope All
if ($null -eq $settings) {
    throw 'M8 no pudo cargar la configuracion nativa privada.'
}
Assert-V12Database -Settings $settings

$migration = Get-Utf8Text 'backend\src\main\resources\db\migration\V12__mobile_offline_availability_receipts.sql'
Assert-Contains -Text $migration -Pattern 'AVAILABILITY_CHANGE' -Description 'recibo durable de disponibilidad'
Assert-Contains -Text $migration -Pattern 'ck_mobile_command_receipt_assignment_scope' -Description 'alcance nulo/no nulo de assignmentId'

$syncController = Get-Utf8Text 'backend\src\main\java\pe\rutafija\operation\api\MobileOfflineSyncController.java'
Assert-Contains -Text $syncController -Pattern 'hasRole\(''CONDUCTOR''\)' -Description 'rol conductor para conciliacion'
Assert-Contains -Text $syncController -Pattern '"/events"' -Description 'endpoint de conciliacion M8'
$syncService = Get-Utf8Text 'backend\src\main\java\pe\rutafija\operation\application\MobileOfflineSyncService.java'
foreach ($type in @('AVAILABILITY_CHANGE', 'SERVICE_START', 'SERVICE_COMPLETE')) {
    Assert-Contains -Text $syncService -Pattern $type -Description "tipo M8 permitido: $type"
}
Assert-Contains -Text $syncService -Pattern 'CONFLICT' -Description 'detencion por conflicto'
Assert-Contains -Text $syncService -Pattern 'REJECTED' -Description 'detencion por rechazo'
$driverService = Get-Utf8Text 'backend\src\main\java\pe\rutafija\operation\application\MobileDriverService.java'
Assert-Contains -Text $driverService -Pattern 'expectedAvailabilityUpdatedAt' -Description 'token de concurrencia de disponibilidad'
Assert-Contains -Text $driverService -Pattern 'lockOwnDriver' -Description 'bloqueo del conductor propio'

$mobileSource = @(Get-ChildItem -LiteralPath (Join-Path $mobileRoot 'lib') -Recurse -File -Filter '*.dart' |
        ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"
foreach ($marker in @(
    'mobile/sync/events',
    'AVAILABILITY_CHANGE',
    'SERVICE_START',
    'SERVICE_COMPLETE',
    'flutter_secure_storage',
    'maximumEvents = 10',
    'pending-sync-open',
    'pending-sync-now',
    'discardBlocked',
    'clearOwner'
)) {
    Assert-Contains -Text $mobileSource -Pattern ([regex]::Escape($marker)) -Description "capacidad M8: $marker"
}
Assert-NotContains -Text $mobileSource `
    -Pattern 'workmanager|connectivity_plus|Timer\(|background\s*(service|task|fetch)|getPositionStream' `
    -Description 'ejecucion automatica o fuera de primer plano'

$m8Tests = Get-Utf8Text 'mobile\test\core\offline\mobile_offline_sync_controller_test.dart'
foreach ($scenario in @(
    'keeps the exact event body after a temporary failure then replays it',
    'blocks the FIFO head and never sends its following event'
)) {
    Assert-Contains -Text $m8Tests -Pattern ([regex]::Escape($scenario)) -Description "prueba M8: $scenario"
}

Push-Location $backendRoot
try {
    # `verify` is deliberate: Surefire runs unit tests and Failsafe executes
    # every native PostgreSQL *IT, including MobileOfflineSyncIT.
    & .\mvnw.cmd --batch-mode --no-transfer-progress verify
    if ($LASTEXITCODE -ne 0) {
        throw 'mvn verify no aprobo M8.'
    }
}
finally {
    Pop-Location
}

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo M8.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo M8.'
    }
    & $flutter build apk --debug
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter build apk --debug no aprobo M8.'
    }
}
finally {
    Pop-Location
}

Write-Output 'M8_OFFLINE_MINIMUM_AUDIT=PASS m8=5/80 total=71/80 fifo=PASS idempotency=PASS foreground_only=PASS'
