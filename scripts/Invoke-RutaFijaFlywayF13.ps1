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
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$bootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
$f12AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaRoleIsolation.ps1'
$runnerSource = Join-Path $PSScriptRoot 'RunRutaFijaFlyway.java'

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

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
if ($null -eq $settings) {
    throw 'No se pudo leer la configuracion nativa local.'
}

if ($settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $settings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'La configuracion F1.3 no autoriza una migracion controlada.'
}
Test-SecretPathIsPrivate -Path $ConfigPath
if (-not (Test-Path -LiteralPath $bootstrapConfigPath -PathType Leaf)) {
    throw 'No existe la configuracion privada de bootstrap requerida por F1.3.'
}
Test-SecretPathIsPrivate -Path $bootstrapConfigPath
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $bootstrapConfigPath -PassThru
if ($null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuracion privada de bootstrap.'
}

$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 8080, 4200 }
if (@($listeners).Count -gt 0) {
    throw 'F1.3 exige que API y CRM permanezcan detenidos antes de migrar.'
}

# La auditoria de F1.2 comprueba el baseline vacio, roles, aislamiento y ausencia de Docker.
& $f12AuditScript -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'El baseline F1.2 no fue aprobado; Flyway no se ejecutara.'
}

$psql = Get-PsqlPath
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
$previousValues = @{}
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'], 'Process')
    $maintenanceSessions = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -d $developmentDatabase -At -F '|' -c "SELECT count(*), count(*) FILTER (WHERE usename = 'soporte' AND application_name = 'pgAdmin 4 - DB:solucion_ruta_fija_1' AND state = 'idle' AND backend_xid IS NULL AND backend_xmin IS NULL) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid();"
    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo inspeccionar las sesiones antes de Flyway.'
    }
    $maintenanceParts = $maintenanceSessions.Trim().Split('|')
    if ($maintenanceParts.Count -ne 2 -or
        $maintenanceParts[0] -notin @('0', '1') -or
        $maintenanceParts[0] -ne $maintenanceParts[1]) {
        throw 'F1.3 solo permite cero o una sesion pgAdmin inactiva y sin transaccion.'
    }
    $allowedOtherSessions = $maintenanceParts[0]

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $settings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $state = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c "SELECT current_database(), current_user, current_setting('TimeZone'), current_setting('server_encoding'), (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')), (to_regclass('public.flyway_schema_history') IS NULL), has_schema_privilege(current_user, 'public', 'CREATE'), (SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid());"
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo el preflight del migrador.'
    }
    $stateParts = $state.Trim().Split('|')
    if ($stateParts.Count -ne 8 -or
        $stateParts[0] -ne $developmentDatabase -or
        $stateParts[1] -ne 'rf_migrator' -or
        $stateParts[2] -ne 'UTC' -or
        $stateParts[3] -ne 'UTF8' -or
        $stateParts[4] -ne '0' -or
        $stateParts[5] -ne 't' -or
        $stateParts[6] -ne 't' -or
        $stateParts[7] -ne $allowedOtherSessions) {
        throw 'El catalogo o las sesiones de desarrollo no cumplen el preflight F1.3.'
    }

    foreach ($entry in $settings.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f1-3-flyway-classpath.txt'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo resolver el classpath de Flyway.'
        }
        $dependencyClasspath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) {
            throw 'El classpath de Flyway esta vacio.'
        }
        & java --class-path "$dependencyClasspath;src\main\resources" '..\scripts\RunRutaFijaFlyway.java'
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aprobo las migraciones F1.3.'
        }
    } finally {
        Pop-Location
    }

    Write-Output 'F1_3_MIGRATION_EXECUTION=PASS'
    Write-Output 'F1.3 no inicio Spring Boot, API, CRM ni Docker.'
} finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}
