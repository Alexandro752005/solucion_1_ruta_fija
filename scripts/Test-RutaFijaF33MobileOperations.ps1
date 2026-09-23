[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$backendSourceRoot = Join-Path $repoRoot 'backend\src\main\java'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Assert-PrivateConfig {
    param([Parameter(Mandatory)][string]$Path)

    $root = [IO.Path]::GetFullPath($repoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $root + [IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La configuracion privada debe permanecer dentro del repositorio local.'
    }
    $relative = $fullPath.Substring($prefix.Length).Replace('\', '/')
    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relative
        if ($LASTEXITCODE -ne 0 -or @(& git ls-files -- $relative).Count -gt 0) {
            throw 'La configuracion privada no esta ignorada de forma segura por Git.'
        }
    }
    finally {
        Pop-Location
    }
}

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontro psql de PostgreSQL 16.'
    }
    return $psql
}

function Get-SourceText {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F3.3 no encontro la fuente requerida: $RelativePath"
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
        throw "F3.3 no satisface: $Description"
    }
}

function Invoke-PsqlOneRow {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][hashtable]$Settings,
        [Parameter(Mandatory)][string]$Query
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Settings['SPRING_FLYWAY_PASSWORD'], 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazo la auditoria F3.3.'
        }
        $rows = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
        if ($rows.Count -ne 1) {
            throw 'F3.3 requiere una unica fila de evidencia PostgreSQL.'
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

if (-not (Test-Path -LiteralPath $nativeImportScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $backendSourceRoot -PathType Container)) {
    throw 'F3.3 no encontro los artefactos base de auditoria.'
}

Assert-PrivateConfig -Path $ConfigPath
$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope All
if ($null -eq $settings -or
    $settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuracion nativa no satisface el contrato F3.3.'
}

$mobileContext = Get-SourceText 'backend\src\main\java\pe\rutafija\shared\security\MobileDriverContextService.java'
Assert-Contains -Text $mobileContext -Pattern 'sessionChannel' -Description 'sesion MOBILE separada'
Assert-Contains -Text $mobileContext -Pattern 'UserRole\.CONDUCTOR' -Description 'rol CONDUCTOR real para API movil'
Assert-Contains -Text $mobileContext -Pattern 'findByUser_Id' -Description 'vinculo actual usuario-conductor en servidor'

foreach ($relativePath in @(
    'backend\src\main\java\pe\rutafija\operation\api\MobileDriverController.java',
    'backend\src\main\java\pe\rutafija\operation\api\MobileAssignmentController.java',
    'backend\src\main\java\pe\rutafija\operation\api\MobileIncidentController.java',
    'backend\src\main\java\pe\rutafija\operation\api\MobileAnnouncementController.java'
)) {
    $controller = Get-SourceText $relativePath
    Assert-Contains -Text $controller -Pattern 'hasRole\(''CONDUCTOR''\)' -Description "proteccion CONDUCTOR de $relativePath"
    Assert-Contains -Text $controller -Pattern 'CacheControl\.noStore\(' -Description "respuesta no almacenable de $relativePath"
}

$assignmentService = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\application\MobileAssignmentService.java'
Assert-Contains -Text $assignmentService -Pattern 'noRollbackFor = ApplicationException\.class' -Description 'recibos idempotentes durables ante rechazo'
Assert-Contains -Text $assignmentService -Pattern 'ASSIGNMENT_RESPONSE_EXPIRED' -Description 'expiracion determinada por servidor'
Assert-Contains -Text $assignmentService -Pattern 'currentLocationStore\.deleteByDriverId' -Description 'limpieza de ubicacion al perder estado operativo'

$commandProcessor = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\application\MobileCommandProcessor.java'
Assert-Contains -Text $commandProcessor -Pattern 'eventLock\.lock\(eventId\)' -Description 'bloqueo de evento movil global'
Assert-Contains -Text $commandProcessor -Pattern 'requestHash' -Description 'huella de solicitud para reintento seguro'
Assert-Contains -Text $commandProcessor -Pattern 'MOBILE_EVENT_CONFLICT' -Description 'conflicto al reutilizar un evento distinto'

$locationStore = Get-SourceText 'backend\src\main\java\pe\rutafija\operation\infrastructure\DriverCurrentLocationStore.java'
Assert-Contains -Text $locationStore -Pattern 'ON CONFLICT \(driver_id\) DO UPDATE' -Description 'UPSERT de ubicacion vigente'
$allBackendSource = @(Get-ChildItem -LiteralPath $backendSourceRoot -Recurse -File -Filter '*.java' |
        ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"
if ($allBackendSource -match '(?i)location_history|driver_location_history') {
    throw 'F3.3 detecto una implementacion de historial de ubicaciones fuera de alcance.'
}

$psql = Get-PsqlPath
$catalog = Invoke-PsqlOneRow -Psql $psql -Settings $settings -Query @'
SELECT current_database(),
       current_user,
       current_setting('TimeZone'),
       (SELECT string_agg(version::text, ',' ORDER BY installed_rank)
          FROM flyway_schema_history WHERE success),
       to_regclass('public.announcement_receipt') IS NOT NULL,
       to_regclass('public.mobile_command_receipt') IS NOT NULL,
       EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'incident' AND column_name = 'source'),
       EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_incident_source'),
       NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_announcement_read_ack_web_only'),
       EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_announcement_receipt_read_after_delivery'),
       EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_hash'),
       EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_processed_after_occurrence'),
       (SELECT count(*) = 12 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'mobile_command_receipt'),
       to_regclass('public.driver_current_location') IS NOT NULL,
       to_regclass('public.driver_location_history') IS NULL,
       to_regclass('public.location_history') IS NULL,
       has_table_privilege('rf_app', 'public.announcement_receipt', 'SELECT')
         AND has_table_privilege('rf_app', 'public.announcement_receipt', 'INSERT')
         AND has_table_privilege('rf_app', 'public.announcement_receipt', 'UPDATE')
         AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'SELECT')
         AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'INSERT')
         AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'UPDATE'),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       NOT EXISTS (
           SELECT 1 FROM audit_event
            WHERE metadata_json::text ~* 'latitude|longitude|accuracy'
       );
'@

$expectedCatalog = @(
    $developmentDatabase, 'rf_migrator', 'UTC', '1,2,3,4,5,6,7,8,9,10',
    't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 'f', 't'
) -join '|'
if ($catalog -ne $expectedCatalog) {
    throw 'El catalogo PostgreSQL no satisface el contrato V9/V10 y de privacidad F3.3.'
}

Write-Output 'F3_3_MOBILE_OPERATIONS_AUDIT=PASS flyway=V1-V10 mobile=own_routes idempotency=durable location=current_only privacy=no_coordinate_audit privileges=DML_without_DDL docker=0'
