[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

$developmentDatabase = 'solucion_ruta_fija_1'
$testDatabase = 'ruta_fija_test'
$recoveryDatabase = 'ruta_fija_recovery_20260919_f04'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$testUrl = "jdbc:postgresql://127.0.0.1:5432/$testDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$bootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
$applicationDmlScript = Join-Path $PSScriptRoot 'sql\F1_3_VerifyApplicationDml.sql'
$businessRulesScript = Join-Path $PSScriptRoot 'sql\F1_3_VerifyBusinessRules.sql'

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
    param([string]$Path)

    $fullRepositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullSecretPath = [IO.Path]::GetFullPath($Path)
    $repositoryPrefix = $fullRepositoryPath + [IO.Path]::DirectorySeparatorChar
    if (-not $fullSecretPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El archivo de secretos debe permanecer dentro del repositorio local.'
    }
    $relativePath = $fullSecretPath.Substring($repositoryPrefix.Length).Replace('\', '/')

    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relativePath
        if ($LASTEXITCODE -ne 0) {
            throw "El archivo local de secretos no esta ignorado por Git: $relativePath"
        }
        $tracked = & git ls-files -- $relativePath
        if (@($tracked).Count -gt 0) {
            throw "El archivo local de secretos esta versionado: $relativePath"
        }
    } finally {
        Pop-Location
    }
}

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $bootstrapConfigPath -PassThru
if ($null -eq $settings -or $null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuracion privada F1.3.'
}

$requiredConfiguration = @{
    SPRING_PROFILES_ACTIVE = 'local'
    SPRING_DATASOURCE_URL = $developmentUrl
    SPRING_DATASOURCE_USERNAME = 'rf_app'
    SPRING_FLYWAY_URL = $developmentUrl
    SPRING_FLYWAY_USERNAME = 'rf_migrator'
    RF_TEST_DATASOURCE_URL = $testUrl
    RF_TEST_DATASOURCE_USERNAME = 'rf_test'
    APP_SEED_ENABLED = 'false'
}
foreach ($entry in $requiredConfiguration.GetEnumerator()) {
    if (-not $settings.Contains($entry.Key) -or $settings[$entry.Key] -ne $entry.Value) {
        throw "La configuracion F1.3 no cumple $($entry.Key)."
    }
}
if ($settings['SPRING_DATASOURCE_PASSWORD'] -eq $settings['SPRING_FLYWAY_PASSWORD'] -or
    $settings['SPRING_DATASOURCE_PASSWORD'] -eq $settings['RF_TEST_DATASOURCE_PASSWORD'] -or
    $settings['SPRING_FLYWAY_PASSWORD'] -eq $settings['RF_TEST_DATASOURCE_PASSWORD']) {
    throw 'Las credenciales de F1.3 deben permanecer separadas.'
}
foreach ($file in @($applicationDmlScript, $businessRulesScript)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "No existe el script de verificacion F1.3: $file"
    }
}
Test-SecretPathIsPrivate -Path $ConfigPath
Test-SecretPathIsPrivate -Path $bootstrapConfigPath

$applicationYaml = Get-Content -LiteralPath (Join-Path $repoRoot 'backend\src\main\resources\application.yml') -Raw
if ($applicationYaml -notmatch '(?m)^\s*ddl-auto:\s*validate\s*$') {
    throw 'Hibernate debe conservar ddl-auto=validate.'
}
$localProperties = Get-Content -LiteralPath (Join-Path $repoRoot 'backend\src\main\resources\application-local.properties')
foreach ($property in @(
    'spring.datasource.username=${SPRING_DATASOURCE_USERNAME}',
    'spring.flyway.url=${SPRING_FLYWAY_URL}',
    'spring.flyway.user=${SPRING_FLYWAY_USERNAME}',
    'spring.flyway.password=${SPRING_FLYWAY_PASSWORD}'
)) {
    if ($localProperties -notcontains $property) {
        throw "El perfil local no conserva la separacion Flyway/API: $property"
    }
}

$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 8080, 4200 }
if (@($listeners).Count -gt 0) {
    throw 'F1.3 exige que API y CRM permanezcan detenidos.'
}

$psql = Get-PsqlPath
function Invoke-DatabaseOperation {
    param(
        [string]$Database,
        [string]$Username,
        [string]$Password,
        [string]$Query,
        [string]$SqlFile
    )

    $previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $ErrorActionPreference = 'Continue'
        if ([string]::IsNullOrWhiteSpace($SqlFile)) {
            $output = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -c $Query 2>&1
        } else {
            $output = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -f $SqlFile 2>&1
        }
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{
            ExitCode = $exitCode
            Output = @($output)
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
    }
}

function Get-SuccessLastRow {
    param(
        [object]$Result,
        [string]$Operation
    )

    if ($Result.ExitCode -ne 0) {
        throw "PostgreSQL rechazo la verificacion F1.3: $Operation"
    }
    $row = @($Result.Output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) }) | Select-Object -Last 1
    if ($null -eq $row) {
        throw "PostgreSQL no devolvio evidencia para: $Operation"
    }
    return $row.ToString().Trim()
}

function Assert-DdlDenied {
    $result = Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_app' -Password $settings['SPRING_DATASOURCE_PASSWORD'] -Query 'CREATE TABLE public._rf_f13_app_ddl_probe (id integer);' -SqlFile $null
    if ($result.ExitCode -eq 0) {
        $cleanup = Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query 'DROP TABLE IF EXISTS public._rf_f13_app_ddl_probe;' -SqlFile $null
        if ($cleanup.ExitCode -ne 0) {
            throw 'rf_app creo una tabla y no fue posible limpiar el probe.'
        }
        throw 'rf_app conserva permiso DDL, lo cual viola F1.3.'
    }
}

$developmentCatalogQuery = @"
SELECT current_database(),
       current_user,
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT string_agg(tablename, ',' ORDER BY tablename) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT count(*) FROM flyway_schema_history),
       (SELECT count(*) = 5
                 AND bool_and(success)
                 AND array_agg(version::text ORDER BY installed_rank) = ARRAY['1', '2', '3', '4', '5']::text[]
          FROM flyway_schema_history),
       EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist'),
       (SELECT string_agg(conname, ',' ORDER BY conname)
          FROM pg_constraint
         WHERE contype = 'x'
           AND conname IN ('ex_assignment_driver_schedule_no_overlap', 'ex_assignment_vehicle_schedule_no_overlap')),
       EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_audit_event_append_only' AND NOT tgisinternal),
       POSITION('DESCONECTADO' IN (SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_driver_availability_status')) = 0,
       pg_get_userbyid((SELECT datdba FROM pg_database WHERE datname = current_database())),
       (SELECT count(*)
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind = 'r'
           AND c.relname <> 'flyway_schema_history'
           AND pg_get_userbyid(c.relowner) = 'rf_migrator'),
       pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'public.flyway_schema_history'::regclass)),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       (SELECT count(*) = 11
                 AND bool_and(
                    has_table_privilege('rf_app', format('%I.%I', schemaname, tablename), 'SELECT')
                    AND has_table_privilege('rf_app', format('%I.%I', schemaname, tablename), 'INSERT')
                    AND has_table_privilege('rf_app', format('%I.%I', schemaname, tablename), 'UPDATE')
                    AND has_table_privilege('rf_app', format('%I.%I', schemaname, tablename), 'DELETE')
                 )
          FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename IN (
                'organization', 'app_user', 'refresh_token', 'transport_group',
                'group_coordinator', 'driver', 'vehicle', 'driver_vehicle_link',
                'assignment', 'incident', 'announcement'
           )),
       has_table_privilege('rf_app', 'public.audit_event', 'SELECT')
           AND has_table_privilege('rf_app', 'public.audit_event', 'INSERT')
           AND NOT has_table_privilege('rf_app', 'public.audit_event', 'UPDATE')
           AND NOT has_table_privilege('rf_app', 'public.audit_event', 'DELETE'),
       NOT has_table_privilege('rf_app', 'public.flyway_schema_history', 'SELECT')
           AND NOT has_table_privilege('rf_app', 'public.flyway_schema_history', 'INSERT')
           AND NOT has_table_privilege('rf_app', 'public.flyway_schema_history', 'UPDATE')
           AND NOT has_table_privilege('rf_app', 'public.flyway_schema_history', 'DELETE'),
       NOT has_function_privilege('rf_app', 'public.prevent_audit_event_mutation()', 'EXECUTE'),
       NOT EXISTS (
           SELECT 1
             FROM pg_proc p
             CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) AS a
            WHERE p.oid = 'public.prevent_audit_event_mutation()'::regprocedure
              AND a.grantee = 0
              AND a.privilege_type = 'EXECUTE'
       ),
       (SELECT count(*) FROM organization);
"@
$developmentCatalog = Get-SuccessLastRow -Operation 'catalogo migrado de desarrollo' -Result (Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query $developmentCatalogQuery -SqlFile $null)
$expectedBusinessTables = 'announcement,app_user,assignment,audit_event,driver,driver_vehicle_link,group_coordinator,incident,organization,refresh_token,transport_group,vehicle'
$expectedConstraints = 'ex_assignment_driver_schedule_no_overlap,ex_assignment_vehicle_schedule_no_overlap'
$developmentExpected = @(
    $developmentDatabase, 'rf_migrator', 'UTC', 'UTF8', '12', $expectedBusinessTables, '5', 't', 't',
    $expectedConstraints, 't', 't', 'rf_migrator', '12', 'rf_migrator', 'f', 't', 't', 't', 't', 't', '0'
) -join '|'
if ($developmentCatalog -ne $developmentExpected) {
    throw 'El catalogo de desarrollo no satisface la evidencia estructural F1.3.'
}

$testCatalog = Get-SuccessLastRow -Operation 'aislamiento de pruebas tras F1.3' -Result (Invoke-DatabaseOperation -Database $testDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query "SELECT current_database(), current_user, current_setting('TimeZone'), current_setting('server_encoding'), (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')), (to_regclass('public.flyway_schema_history') IS NULL);" -SqlFile $null)
if ($testCatalog -ne "$testDatabase|rf_migrator|UTC|UTF8|0|t") {
    throw 'F1.3 modifico indebidamente la base de pruebas.'
}

$recoveryCatalog = Get-SuccessLastRow -Operation 'preservacion de recuperacion' -Result (Invoke-DatabaseOperation -Database $recoveryDatabase -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "SELECT current_database(), current_setting('server_encoding'), (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')), (to_regclass('public.flyway_schema_history') IS NULL);" -SqlFile $null)
if ($recoveryCatalog -ne "$recoveryDatabase|UTF8|0|t") {
    throw 'La base de recuperacion no conserva el estado previo esperado.'
}

$applicationProbe = Get-SuccessLastRow -Operation 'DML transaccional de rf_app' -Result (Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_app' -Password $settings['SPRING_DATASOURCE_PASSWORD'] -Query $null -SqlFile $applicationDmlScript)
if ($applicationProbe -ne 'F1_3_APP_DML=PASS') {
    throw 'rf_app no aprobo la prueba transaccional DML.'
}

$businessRulesProbe = Get-SuccessLastRow -Operation 'reglas de auditoria, V4 y V5' -Result (Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query $null -SqlFile $businessRulesScript)
if ($businessRulesProbe -ne 'F1_3_BUSINESS_RULES=PASS') {
    throw 'Las reglas de negocio de base de datos no fueron aprobadas.'
}

Assert-DdlDenied

$businessDataCount = Get-SuccessLastRow -Operation 'ausencia de datos persistentes de prueba' -Result (Invoke-DatabaseOperation -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query 'SELECT count(*) FROM organization;' -SqlFile $null)
if ($businessDataCount -ne '0') {
    throw 'Las pruebas F1.3 dejaron datos de negocio persistentes.'
}

Write-Output 'F1_3_SCHEMA_AUDIT=PASS'
Write-Output 'flyway=V1-V5 btree_gist=activo audit=append-only exclusion_constraints=activas'
Write-Output 'rf_app=DML_selectivo_sin_DDL historia_Flyway=protegida'
Write-Output 'F1.3 no inicio Spring Boot, API, CRM ni Docker.'
