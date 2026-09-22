[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$testDatabase = 'ruta_fija_test'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$assignmentCreateRequestPath = Join-Path $repoRoot 'backend\src\main\java\pe\rutafija\operation\api\dto\AssignmentCreateRequest.java'
$vehicleStatusPath = Join-Path $repoRoot 'backend\src\main\java\pe\rutafija\fleet\domain\VehicleStatus.java'
$backendSourceRoot = Join-Path $repoRoot 'backend\src\main\java'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
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

function Test-SecretPathIsPrivate {
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
            throw 'La configuracion privada no esta correctamente ignorada por Git.'
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-DatabaseQuery {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][string]$Operation
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL rechazo $Operation en F3.1B."
        }
        $rows = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
        if ($rows.Count -ne 1) {
            throw "F3.1B no obtuvo una unica fila para $Operation."
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

foreach ($path in @($assignmentCreateRequestPath, $vehicleStatusPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F3.1B no encontro la fuente requerida: $path"
    }
}
if (-not (Test-Path -LiteralPath $backendSourceRoot -PathType Container)) {
    throw 'F3.1B no encontro el arbol de fuentes backend.'
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw 'F3.1B requiere la configuracion nativa privada.'
}

Test-SecretPathIsPrivate -Path $ConfigPath
$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope All
if ($null -eq $settings -or
    $settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $settings['RF_TEST_DATASOURCE_URL'] -ne "jdbc:postgresql://127.0.0.1:5432/$testDatabase" -or
    $settings['RF_TEST_DATASOURCE_USERNAME'] -ne 'rf_test') {
    throw 'La configuracion nativa no cumple el contrato F3.1B.'
}

$vehicleStatus = Get-Content -LiteralPath $vehicleStatusPath -Raw
if ($vehicleStatus -match '\bRESERVADO\b' -or
    $vehicleStatus -notmatch '(?s)enum\s+VehicleStatus\s*\{\s*DISPONIBLE,\s*EN_SERVICIO,\s*MANTENIMIENTO,\s*INACTIVO;') {
    throw 'F3.1B detecto un estado de vehiculo incompatible con la reserva futura.'
}
$assignmentCreateRequest = Get-Content -LiteralPath $assignmentCreateRequestPath -Raw
if ($assignmentCreateRequest -match 'responseMode|responseDeadlineAt') {
    throw 'F3.1B no permite abrir el flujo MOBILE_CONFIRMATION desde el CRM antes de F3.3.'
}
$backendSources = @(Get-ChildItem -LiteralPath $backendSourceRoot -Recurse -File -Filter '*.java')
$mobileRoutes = @(Select-String -LiteralPath $backendSources.FullName -Pattern '[''\"]/mobile/' -ErrorAction Stop)
if ($mobileRoutes.Count -gt 0) {
    throw 'F3.1B detecto rutas /mobile/ antes de F3.2/F3.3.'
}

$psql = Get-PsqlPath
$developmentCatalog = Invoke-DatabaseQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Operation 'catalogo de desarrollo V1-V8' -Query @'
SELECT current_database(),
       current_user,
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT string_agg(tablename, ',' ORDER BY tablename) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT count(*) = 8
                 AND bool_and(success)
                 AND array_agg(version::text ORDER BY installed_rank) = ARRAY['1','2','3','4','5','6','7','8']::text[]
          FROM flyway_schema_history),
       EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist'),
       (SELECT count(*) = 2 AND bool_and(
                    pg_get_constraintdef(oid) LIKE '%PENDING_RESPONSE%'
                AND pg_get_constraintdef(oid) LIKE '%SCHEDULED%'
                AND pg_get_constraintdef(oid) LIKE '%EN_SERVICIO%'
            )
          FROM pg_constraint
         WHERE contype = 'x'
           AND conname IN ('ex_assignment_driver_schedule_no_overlap', 'ex_assignment_vehicle_schedule_no_overlap')),
       EXISTS (
           SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_assignment_status'
              AND pg_get_constraintdef(oid) LIKE '%PENDING_RESPONSE%'
              AND pg_get_constraintdef(oid) LIKE '%REJECTED%'
              AND pg_get_constraintdef(oid) LIKE '%EXPIRED%'
       ),
       EXISTS (
           SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_assignment_response_lifecycle'
              AND pg_get_constraintdef(oid) LIKE '%ADMIN_DIRECT%'
              AND pg_get_constraintdef(oid) LIKE '%MOBILE_CONFIRMATION%'
              AND pg_get_constraintdef(oid) LIKE '%accepted_at%'
              AND pg_get_constraintdef(oid) LIKE '%rejected_at%'
              AND pg_get_constraintdef(oid) LIKE '%expired_at%'
       ),
       (SELECT count(*) = 6
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'assignment'
           AND column_name IN ('response_mode', 'response_deadline_at', 'accepted_at', 'rejected_at', 'rejection_reason', 'expired_at')),
       (to_regclass('public.driver_current_location') IS NOT NULL
          AND (SELECT count(*) = 9
                 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'driver_current_location')),
       (EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_driver_id_organization' AND contype = 'u')
          AND EXISTS (SELECT 1 FROM pg_constraint
                       WHERE conname = 'fk_driver_current_location_driver_organization'
                         AND contype = 'f'
                         AND confrelid = 'public.driver'::regclass)),
       (SELECT count(*) = 6 AND bool_and(convalidated)
          FROM pg_constraint
         WHERE conname IN (
             'ck_driver_current_location_latitude',
             'ck_driver_current_location_longitude',
             'ck_driver_current_location_accuracy',
             'ck_driver_current_location_captured_at',
             'ck_driver_current_location_expiry',
             'ck_driver_current_location_source'
         )),
       (to_regclass('public.ix_driver_current_location_organization_expires') IS NOT NULL),
       (has_table_privilege('rf_app', 'public.driver_current_location', 'SELECT')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'INSERT')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'UPDATE')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'DELETE')),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_audit_event_append_only' AND NOT tgisinternal),
       EXISTS (
           SELECT 1 FROM pg_constraint
            WHERE conname = 'ck_app_user_role'
              AND pg_get_constraintdef(oid) LIKE '%SUPER_ADMIN%'
              AND pg_get_constraintdef(oid) LIKE '%ADMIN%'
              AND pg_get_constraintdef(oid) LIKE '%CONDUCTOR%'
              AND pg_get_constraintdef(oid) NOT LIKE '%ADMINISTRADOR%'
              AND pg_get_constraintdef(oid) NOT LIKE '%COORDINADOR%'
       );
'@

$expectedTables = 'announcement,app_user,assignment,audit_event,driver,driver_current_location,driver_vehicle_link,group_coordinator,incident,organization,refresh_token,transport_group,vehicle'
$expectedDevelopment = @(
    $developmentDatabase, 'rf_migrator', 'UTC', 'UTF8', '13', $expectedTables,
    't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 't', 'f', 't', 't'
) -join '|'
if ($developmentCatalog -ne $expectedDevelopment) {
    throw 'El catalogo de desarrollo no satisface el contrato V7/V8 de F3.1B.'
}

$testCatalog = Invoke-DatabaseQuery -Psql $psql -Database $testDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Operation 'aislamiento de pruebas V1-V8' -Query @'
WITH history AS (
    SELECT installed_rank, version::text AS version, description, type, success
      FROM flyway_schema_history
),
state AS (
    SELECT count(*) FILTER (WHERE version IS NOT NULL) AS versioned_count,
           bool_and(success) AS all_successful,
           array_agg(version ORDER BY installed_rank) FILTER (WHERE version IS NOT NULL) AS versions,
           count(*) FILTER (WHERE version IS NULL
                             AND description = 'native test role privileges'
                             AND type = 'SQL'
                             AND success) AS valid_repeatable_count,
           count(*) FILTER (WHERE version IS NULL) AS repeatable_count
      FROM history
)
SELECT current_database(),
       current_user,
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       CASE
           WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'empty'
           WHEN (SELECT versioned_count = 6
                        AND all_successful
                        AND versions = ARRAY['1','2','3','4','5','6']::text[]
                        AND repeatable_count = valid_repeatable_count
                   FROM state) THEN 'legacy_v6'
           WHEN (SELECT versioned_count = 8
                        AND all_successful
                        AND versions = ARRAY['1','2','3','4','5','6','7','8']::text[]
                        AND repeatable_count = valid_repeatable_count
                   FROM state) THEN 'migrated'
           ELSE 'unexpected'
       END;
'@
if ($testCatalog -notin @(
    "$testDatabase|rf_migrator|UTC|UTF8|empty",
    "$testDatabase|rf_migrator|UTC|UTF8|legacy_v6",
    "$testDatabase|rf_migrator|UTC|UTF8|migrated"
)) {
    throw 'ruta_fija_test no conserva un aislamiento vacio o migrado V1-V8 permitido.'
}

Write-Output 'F3_1B_SCHEMA_AUDIT=PASS flyway=V1-V8 response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only privileges=DML_without_DDL mobile_endpoints=0 docker=0'
