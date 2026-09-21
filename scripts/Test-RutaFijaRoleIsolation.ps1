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
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$testUrl = "jdbc:postgresql://127.0.0.1:5432/$testDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$f11aPreflightScript = Join-Path $PSScriptRoot 'Test-RutaFijaNativePreflight.ps1'
$bootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'

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
if ($null -eq $settings) {
    throw 'No se pudo leer la configuracion nativa local.'
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
    APP_CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
    REFRESH_COOKIE_SECURE = 'false'
}
foreach ($entry in $requiredConfiguration.GetEnumerator()) {
    if (-not $settings.Contains($entry.Key) -or $settings[$entry.Key] -ne $entry.Value) {
        throw "La configuracion F1.2 no cumple $($entry.Key)."
    }
}
if ($settings['SPRING_DATASOURCE_PASSWORD'] -eq $settings['SPRING_FLYWAY_PASSWORD'] -or
    $settings['SPRING_DATASOURCE_PASSWORD'] -eq $settings['RF_TEST_DATASOURCE_PASSWORD'] -or
    $settings['SPRING_FLYWAY_PASSWORD'] -eq $settings['RF_TEST_DATASOURCE_PASSWORD']) {
    throw 'Las credenciales de aplicacion, migracion y prueba deben ser distintas.'
}

Test-SecretPathIsPrivate -Path $ConfigPath
if (-not (Test-Path -LiteralPath $bootstrapConfigPath -PathType Leaf)) {
    throw 'No existe la configuracion privada de bootstrap requerida por F1.2.'
}
Test-SecretPathIsPrivate -Path $bootstrapConfigPath
$localPropertiesPath = Join-Path $repoRoot 'backend\src\main\resources\application-local.properties'
$localProperties = Get-Content -LiteralPath $localPropertiesPath
foreach ($requiredProperty in @(
    'spring.datasource.username=${SPRING_DATASOURCE_USERNAME}',
    'spring.flyway.url=${SPRING_FLYWAY_URL}',
    'spring.flyway.user=${SPRING_FLYWAY_USERNAME}',
    'spring.flyway.password=${SPRING_FLYWAY_PASSWORD}'
)) {
    if ($localProperties -notcontains $requiredProperty) {
        throw "El perfil local no separa correctamente Flyway y datasource: $requiredProperty"
    }
}

# Conserva los controles de toolchain y JDBC de F1.1A. Es una conexion de solo lectura.
& $f11aPreflightScript -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'El preflight nativo anterior a la auditoria F1.2 no fue aprobado.'
}

$psql = Get-PsqlPath
function Invoke-RoleQuery {
    param(
        [string]$Database,
        [string]$Username,
        [string]$Password,
        [string]$Query
    )

    $previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $ErrorActionPreference = 'Continue'
        $output = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -c $Query 2>&1
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

function Get-SuccessRow {
    param(
        [object]$Result,
        [string]$Operation
    )

    if ($Result.ExitCode -ne 0) {
        throw "PostgreSQL rechazo la comprobacion F1.2: $Operation"
    }
    $row = @($Result.Output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) }) | Select-Object -Last 1
    if ($null -eq $row) {
        throw "PostgreSQL no devolvio datos para: $Operation"
    }
    return $row.ToString().Trim()
}

function Assert-ConnectionDenied {
    param(
        [string]$Database,
        [string]$Username,
        [string]$Password,
        [string]$Description
    )

    $result = Invoke-RoleQuery -Database $Database -Username $Username -Password $Password -Query 'SELECT current_database();'
    if ($result.ExitCode -eq 0) {
        throw "Fallo el aislamiento F1.2: $Description"
    }
}

function Assert-DdlDenied {
    param(
        [string]$Database,
        [string]$Username,
        [string]$Password,
        [string]$ProbeName
    )

    $result = Invoke-RoleQuery -Database $Database -Username $Username -Password $Password -Query "CREATE TABLE public.$ProbeName (id integer);"
    if ($result.ExitCode -eq 0) {
        $cleanup = Invoke-RoleQuery -Database $Database -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query "DROP TABLE IF EXISTS public.$ProbeName;"
        if ($cleanup.ExitCode -ne 0) {
            throw "Fallo el aislamiento F1.2: $Username creo DDL y la limpieza del probe no fue posible."
        }
        throw "Fallo el aislamiento F1.2: $Username tiene permiso DDL."
    }
}

$developmentCatalog = Get-SuccessRow -Operation 'catalogo de desarrollo con migrador' -Result (Invoke-RoleQuery -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query @"
SELECT current_database(),
       current_user,
       pg_get_userbyid((SELECT datdba FROM pg_database WHERE datname = current_database())),
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')),
       (to_regclass('public.flyway_schema_history') IS NULL),
       (SELECT count(*) FROM pg_roles WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test')),
       (SELECT bool_and(NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND rolcanlogin) FROM pg_roles WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test')),
       has_database_privilege('rf_app', 'solucion_ruta_fija_1', 'CONNECT'),
       has_database_privilege('rf_app', 'ruta_fija_test', 'CONNECT'),
       has_database_privilege('rf_test', 'solucion_ruta_fija_1', 'CONNECT'),
       has_database_privilege('rf_test', 'ruta_fija_test', 'CONNECT'),
       has_schema_privilege('rf_migrator', 'public', 'CREATE'),
       has_schema_privilege('rf_app', 'public', 'USAGE'),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       has_schema_privilege('rf_test', 'public', 'USAGE'),
       has_schema_privilege('rf_test', 'public', 'CREATE'),
       NOT EXISTS (
           SELECT 1
             FROM pg_database d
             CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl, acldefault('d', d.datdba))) AS a
            WHERE d.datname = current_database()
              AND a.grantee = 0
              AND a.privilege_type IN ('CONNECT', 'TEMPORARY', 'CREATE')
       ),
       NOT EXISTS (
           SELECT 1
             FROM pg_namespace n
             CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl, acldefault('n', n.nspowner))) AS a
            WHERE n.nspname = 'public'
              AND a.grantee = 0
              AND a.privilege_type IN ('USAGE', 'CREATE')
       );
"@)
$developmentExpected = @(
    $developmentDatabase, 'rf_migrator', 'rf_migrator', 'UTC', 'UTF8', '0', 't', '3', 't',
    't', 'f', 'f', 't', 't', 't', 'f', 'f', 'f', 't', 't'
) -join '|'
if ($developmentCatalog -ne $developmentExpected) {
    throw 'El catalogo de desarrollo no satisface los privilegios o aislamiento esperados de F1.2.'
}

$testCatalog = Get-SuccessRow -Operation 'catalogo de pruebas con migrador' -Result (Invoke-RoleQuery -Database $testDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query @"
SELECT current_database(),
       current_user,
       pg_get_userbyid((SELECT datdba FROM pg_database WHERE datname = current_database())),
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')),
       (to_regclass('public.flyway_schema_history') IS NULL),
       has_schema_privilege('rf_migrator', 'public', 'CREATE'),
       has_schema_privilege('rf_test', 'public', 'USAGE'),
       has_schema_privilege('rf_test', 'public', 'CREATE'),
       has_schema_privilege('rf_app', 'public', 'USAGE'),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       NOT EXISTS (
           SELECT 1
             FROM pg_database d
             CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl, acldefault('d', d.datdba))) AS a
            WHERE d.datname = current_database()
              AND a.grantee = 0
              AND a.privilege_type IN ('CONNECT', 'TEMPORARY', 'CREATE')
       ),
       NOT EXISTS (
           SELECT 1
             FROM pg_namespace n
             CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl, acldefault('n', n.nspowner))) AS a
            WHERE n.nspname = 'public'
              AND a.grantee = 0
              AND a.privilege_type IN ('USAGE', 'CREATE')
       );
"@)
$testExpected = @(
    $testDatabase, 'rf_migrator', 'rf_migrator', 'UTC', 'UTF8', '0', 't', 't', 't', 'f', 'f', 'f', 't', 't'
) -join '|'
if ($testCatalog -ne $testExpected) {
    throw 'El catalogo de pruebas no satisface los privilegios o aislamiento esperados de F1.2.'
}

$applicationConnection = Get-SuccessRow -Operation 'conexion de rf_app a desarrollo' -Result (Invoke-RoleQuery -Database $developmentDatabase -Username 'rf_app' -Password $settings['SPRING_DATASOURCE_PASSWORD'] -Query 'SELECT current_database(), current_user;')
if ($applicationConnection -ne "$developmentDatabase|rf_app") {
    throw 'rf_app no se conecto exclusivamente al entorno de desarrollo.'
}
$testConnection = Get-SuccessRow -Operation 'conexion de rf_test a pruebas' -Result (Invoke-RoleQuery -Database $testDatabase -Username 'rf_test' -Password $settings['RF_TEST_DATASOURCE_PASSWORD'] -Query 'SELECT current_database(), current_user;')
if ($testConnection -ne "$testDatabase|rf_test") {
    throw 'rf_test no se conecto exclusivamente al entorno de pruebas.'
}

Assert-ConnectionDenied -Database $testDatabase -Username 'rf_app' -Password $settings['SPRING_DATASOURCE_PASSWORD'] -Description 'rf_app alcanzo ruta_fija_test'
Assert-ConnectionDenied -Database $developmentDatabase -Username 'rf_test' -Password $settings['RF_TEST_DATASOURCE_PASSWORD'] -Description 'rf_test alcanzo solucion_ruta_fija_1'
Assert-DdlDenied -Database $developmentDatabase -Username 'rf_app' -Password $settings['SPRING_DATASOURCE_PASSWORD'] -ProbeName '_rf_f12_app_ddl_probe'
Assert-DdlDenied -Database $testDatabase -Username 'rf_test' -Password $settings['RF_TEST_DATASOURCE_PASSWORD'] -ProbeName '_rf_f12_test_ddl_probe'

$developmentAfterProbe = Get-SuccessRow -Operation 'ausencia de objetos de prueba en desarrollo' -Result (Invoke-RoleQuery -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');")
$testAfterProbe = Get-SuccessRow -Operation 'ausencia de objetos de prueba en pruebas' -Result (Invoke-RoleQuery -Database $testDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Query "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');")
if ($developmentAfterProbe -ne '0' -or $testAfterProbe -ne '0') {
    throw 'La verificacion F1.2 detecto objetos persistentes no autorizados.'
}

Write-Output 'F1_2_ROLE_ISOLATION=PASS'
Write-Output 'development=rf_app(sin-DDL; DML selectivo en F1.3) + rf_migrator(DDL-controlado)'
Write-Output 'test=rf_test(aislado; sin-DDL) + rf_migrator(DDL-controlado)'
Write-Output 'F1.2 no ejecuto Flyway, Spring Boot, API, CRM ni Docker.'
