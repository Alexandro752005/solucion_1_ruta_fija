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
$backendSourceRoot = Join-Path $repoRoot 'backend\src\main\java'
$mobileControllerPath = Join-Path $backendSourceRoot 'pe\rutafija\identity\api\MobileAuthController.java'
$authServicePath = Join-Path $backendSourceRoot 'pe\rutafija\identity\application\AuthService.java'
$opaqueTokenPath = Join-Path $backendSourceRoot 'pe\rutafija\identity\application\OpaqueTokenService.java'
$refreshRepositoryPath = Join-Path $backendSourceRoot 'pe\rutafija\identity\infrastructure\RefreshTokenRepository.java'
$securityConfigPath = Join-Path $backendSourceRoot 'pe\rutafija\shared\config\SecurityConfig.java'
$errorCodePath = Join-Path $backendSourceRoot 'pe\rutafija\shared\exception\ErrorCode.java'
$mobileResponsePath = Join-Path $backendSourceRoot 'pe\rutafija\identity\api\dto\MobileAuthTokenResponse.java'
$mobileRefreshPath = Join-Path $backendSourceRoot 'pe\rutafija\identity\api\dto\MobileRefreshTokenRequest.java'
$mobileIntegrationTestPath = Join-Path $repoRoot 'backend\src\test\java\pe\rutafija\identity\api\MobileAuthFlowIT.java'

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
            throw "PostgreSQL rechazo $Operation en F3.2."
        }
        $rows = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
        if ($rows.Count -ne 1) {
            throw "F3.2 no obtuvo una unica fila para $Operation."
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

foreach ($path in @(
        $mobileControllerPath,
        $authServicePath,
        $opaqueTokenPath,
        $refreshRepositoryPath,
        $securityConfigPath,
        $errorCodePath,
        $mobileResponsePath,
        $mobileRefreshPath,
        $mobileIntegrationTestPath
    )) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F3.2 no encontro el artefacto requerido: $path"
    }
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw 'F3.2 requiere la configuracion nativa privada.'
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
    throw 'La configuracion nativa no cumple el contrato F3.2.'
}

$mobileController = Get-Content -LiteralPath $mobileControllerPath -Raw
$authService = Get-Content -LiteralPath $authServicePath -Raw
$opaqueTokenService = Get-Content -LiteralPath $opaqueTokenPath -Raw
$refreshRepository = Get-Content -LiteralPath $refreshRepositoryPath -Raw
$securityConfig = Get-Content -LiteralPath $securityConfigPath -Raw
$errorCodes = Get-Content -LiteralPath $errorCodePath -Raw
$mobileResponse = Get-Content -LiteralPath $mobileResponsePath -Raw
$mobileRefreshRequest = Get-Content -LiteralPath $mobileRefreshPath -Raw
$mobileIntegrationTest = Get-Content -LiteralPath $mobileIntegrationTestPath -Raw

if ($mobileController -notmatch '@RequestMapping\("/api/v1/mobile/auth"\)' -or
    $mobileController -notmatch '@PostMapping\("/login"\)' -or
    $mobileController -notmatch '@PostMapping\("/refresh"\)' -or
    $mobileController -notmatch '@PostMapping\("/logout"\)' -or
    $mobileController -match 'RefreshCookieService|HttpServletRequest|HttpHeaders\.SET_COOKIE|import\s+jakarta\.servlet\.http\.Cookie;') {
    throw 'El controlador de F3.2 no conserva el transporte movil JSON separado de la cookie web.'
}
if ($authService -notmatch 'mobileLogin\(' -or
    $authService -notmatch 'refreshMobile\(' -or
    $authService -notmatch 'logoutMobile\(' -or
    $authService -notmatch 'requireMobileDriver\(' -or
    $authService -notmatch 'generateMobile\(' -or
    $authService -notmatch 'MOBILE_USER_NOT_DRIVER' -or
    $authService -notmatch 'DRIVER_INACTIVE') {
    throw 'El servicio de identidad no exige conductor vinculado y activo para F3.2.'
}
if ($opaqueTokenService -notmatch 'MOBILE_TOKEN_PREFIX' -or
    $opaqueTokenService -notmatch 'isWebRefreshToken' -or
    $opaqueTokenService -notmatch 'isMobileRefreshToken') {
    throw 'F3.2 no separa criptograficamente los canales de refresh opaco.'
}
if ($refreshRepository -notmatch '(?is)select\s+\*\s+from\s+refresh_token\s+where\s+token_hash\s*=\s*:tokenHash\s+for\s+update') {
    throw 'F3.2 requiere SELECT FOR UPDATE directo para serializar refresh concurrente.'
}
foreach ($route in @('/api/v1/mobile/auth/login', '/api/v1/mobile/auth/refresh', '/api/v1/mobile/auth/logout')) {
    if ($securityConfig -notmatch [regex]::Escape('"' + $route + '"')) {
        throw "SecurityConfig no publica la ruta de sesion movil requerida: $route"
    }
}
if ($errorCodes -notmatch '\bMOBILE_USER_NOT_DRIVER\b' -or $errorCodes -notmatch '\bDRIVER_INACTIVE\b' -or
    $mobileResponse -notmatch 'refreshToken' -or $mobileResponse -notmatch 'refreshExpiresIn' -or
    $mobileRefreshRequest -notmatch '@NotBlank' -or
    $mobileIntegrationTest -notmatch 'concurrentNativeRefreshesSerializeAndRevokeOnReuse' -or
    $mobileIntegrationTest -notmatch 'MOBILE_LOGIN_SUCCESS' -or
    $mobileIntegrationTest -notmatch 'doesNotContain\(body\.path\("refreshToken"\)\.asText\(\)\)') {
    throw 'El contrato o las pruebas de errores/sesion movil de F3.2 estan incompletos.'
}

$backendSources = @(Get-ChildItem -LiteralPath $backendSourceRoot -Recurse -File -Filter '*.java')
$operationalMobileMappings = @(
    Select-String -LiteralPath $backendSources.FullName -Pattern '"/api/v1/mobile/(?!auth(?:/|"))' -ErrorAction Stop
)
if ($operationalMobileMappings.Count -ne 0) {
    throw 'F3.2 detecto endpoints operativos /mobile fuera de la sesion autorizada.'
}
$mobileAuthMappings = @(
    Select-String -LiteralPath $backendSources.FullName -Pattern '"/api/v1/mobile/auth(?:/|")' -ErrorAction Stop
)
if ($mobileAuthMappings.Count -ne 4 -or
    @($mobileAuthMappings | Where-Object { $_.Path -eq $mobileControllerPath }).Count -ne 1 -or
    @($mobileAuthMappings | Where-Object { $_.Path -eq $securityConfigPath }).Count -ne 3) {
    throw 'F3.2 no conserva exclusivamente el contrato de sesion movil autorizado.'
}

$psql = Get-PsqlPath
$developmentCatalog = Invoke-DatabaseQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $settings['SPRING_FLYWAY_PASSWORD'] -Operation 'catalogo de desarrollo V1-V8' -Query @'
SELECT current_database(),
       current_user,
       current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT count(*) = 8
                 AND bool_and(success)
                 AND array_agg(version::text ORDER BY installed_rank) = ARRAY['1','2','3','4','5','6','7','8']::text[]
          FROM flyway_schema_history),
       EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assignment_response_lifecycle'),
       to_regclass('public.driver_current_location') IS NOT NULL,
       (has_table_privilege('rf_app', 'public.driver_current_location', 'SELECT')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'INSERT')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'UPDATE')
          AND has_table_privilege('rf_app', 'public.driver_current_location', 'DELETE')),
       has_schema_privilege('rf_app', 'public', 'CREATE'),
       (SELECT count(*) = 0 FROM flyway_schema_history WHERE version = '9');
'@
$expectedDevelopment = @(
    $developmentDatabase, 'rf_migrator', 'UTC', 'UTF8', '13',
    't', 't', 't', 't', 'f', 't'
) -join '|'
if ($developmentCatalog -ne $expectedDevelopment) {
    throw 'El catalogo de desarrollo no conserva el limite V1-V8 de F3.2.'
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

Write-Output 'F3_2_SESSION_AUDIT=PASS flyway=V1-V8 mobile_auth=login,refresh,logout conductor_bound=1 refresh=body_rotating_for_update errors=code,correlationId operational_mobile_endpoints=0 docker=0'
