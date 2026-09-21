[CmdletBinding()]
param(
    [string]$NativeConfigPath,
    [string]$BootstrapConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}

$developmentDatabase = 'solucion_ruta_fija_1'
$testDatabase = 'ruta_fija_test'
$recoveryDatabase = 'ruta_fija_recovery_20260919_f04'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$testUrl = "jdbc:postgresql://127.0.0.1:5432/$testDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$sqlDirectory = Join-Path $PSScriptRoot 'sql'

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

function Write-PrivateEnvironmentFile {
    param(
        [string]$Path,
        [string[]]$Content,
        [switch]$AllowReplace
    )

    if ((Test-Path -LiteralPath $Path) -and -not $AllowReplace) {
        throw "El archivo privado ya existe y no se sobrescribira: $Path"
    }

    $directory = Split-Path -Parent $Path
    $null = New-Item -ItemType Directory -Path $directory -Force
    $temporaryPath = Join-Path $directory ('.' + [IO.Path]::GetFileName($Path) + '.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllLines($temporaryPath, $Content, [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporaryPath -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function New-RolePassword {
    $bytes = [byte[]]::new(36)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
        return ([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())
    } finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
        $generator.Dispose()
    }
}

$psql = Get-PsqlPath
$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
if ($null -eq $nativeSettings) {
    throw 'No se pudo leer la configuracion nativa local.'
}

if ($nativeSettings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $nativeSettings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $nativeSettings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'La configuracion nativa no cumple el punto de partida aprobado para F1.2.'
}

$isF12Configuration = $nativeSettings.Contains('SPRING_FLYWAY_URL')
$bootstrapExists = Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf
if ($isF12Configuration -and -not $bootstrapExists) {
    throw 'Se detecto una configuracion F1.2 sin su bootstrap privado; no se continuara.'
}
if (-not $isF12Configuration -and $bootstrapExists) {
    $existingBootstrap = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
    if ($existingBootstrap['RUTA_FIJA_BOOTSTRAP_USERNAME'] -ne $nativeSettings['SPRING_DATASOURCE_USERNAME'] -or
        $existingBootstrap['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -ne $nativeSettings['SPRING_DATASOURCE_PASSWORD']) {
        throw 'Se detecto un bootstrap privado que no corresponde a la configuracion F1.1A; no se continuara.'
    }
}

Test-SecretPathIsPrivate -Path $NativeConfigPath
if ($bootstrapExists) {
    Test-SecretPathIsPrivate -Path $BootstrapConfigPath
}

$bootstrapSettings = $null
$migratorPassword = $null
$applicationPassword = $null
$testPassword = $null
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
$previousMigratorPassword = [Environment]::GetEnvironmentVariable('RUTA_FIJA_MIGRATOR_PASSWORD', 'Process')
$previousApplicationPassword = [Environment]::GetEnvironmentVariable('RUTA_FIJA_APP_PASSWORD', 'Process')
$previousTestPassword = [Environment]::GetEnvironmentVariable('RUTA_FIJA_TEST_PASSWORD', 'Process')

function Invoke-PostgresSqlFile {
    param(
        [string]$Database,
        [string]$File
    )

    $output = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -d $Database -At -F '|' -f $File 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL rechazo la operacion controlada F1.2: $([IO.Path]::GetFileName($File))"
    }
    return @($output)
}

function Invoke-PostgresQuery {
    param(
        [string]$Database,
        [string]$Query
    )

    $output = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -d $Database -At -F '|' -c $Query 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo una comprobacion de estado F1.2.'
    }
    $row = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) }) | Select-Object -Last 1
    if ($null -eq $row) {
        throw 'PostgreSQL no devolvio una respuesta para la comprobacion F1.2.'
    }
    return $row.ToString().Trim()
}

try {
    if ($isF12Configuration) {
        $bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
        $migratorPassword = $nativeSettings['SPRING_FLYWAY_PASSWORD']
        $applicationPassword = $nativeSettings['SPRING_DATASOURCE_PASSWORD']
        $testPassword = $nativeSettings['RF_TEST_DATASOURCE_PASSWORD']
        if ($nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
            $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
            $nativeSettings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
            $nativeSettings['RF_TEST_DATASOURCE_URL'] -ne $testUrl -or
            $nativeSettings['RF_TEST_DATASOURCE_USERNAME'] -ne 'rf_test') {
            throw 'La configuracion F1.2 existente no coincide con los destinos o roles autorizados.'
        }
    } else {
        $bootstrapSettings = [ordered]@{
            RUTA_FIJA_BOOTSTRAP_HOST = '127.0.0.1'
            RUTA_FIJA_BOOTSTRAP_PORT = '5432'
            RUTA_FIJA_BOOTSTRAP_USERNAME = $nativeSettings['SPRING_DATASOURCE_USERNAME']
            RUTA_FIJA_BOOTSTRAP_PASSWORD = $nativeSettings['SPRING_DATASOURCE_PASSWORD']
        }
        $migratorPassword = New-RolePassword
        $applicationPassword = New-RolePassword
        $testPassword = New-RolePassword
    }

    if ($migratorPassword -eq $applicationPassword -or
        $migratorPassword -eq $testPassword -or
        $applicationPassword -eq $testPassword) {
        throw 'Las claves de roles F1.2 deben ser distintas.'
    }

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'], 'Process')
    if (-not $isF12Configuration) {
        $baselineOutput = Invoke-PostgresSqlFile -Database $developmentDatabase -File (Join-Path $sqlDirectory 'F1_2_AssertBaseline.sql')
        $baselineRow = @($baselineOutput | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) }) | Select-Object -Last 1
        if ($null -eq $baselineRow -or $baselineRow.ToString().Trim() -ne "$developmentDatabase|0|t|0|t|t") {
            throw 'El catalogo inicial no cumple el baseline protegido requerido para F1.2.'
        }
    }
    $state = Invoke-PostgresQuery -Database $developmentDatabase -Query "SELECT current_database(), (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')), (to_regclass('public.flyway_schema_history') IS NULL), (EXISTS (SELECT 1 FROM pg_database WHERE datname = '$recoveryDatabase')), (SELECT count(*) FROM pg_roles WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test')), (EXISTS (SELECT 1 FROM pg_database WHERE datname = '$testDatabase'));"
    $stateParts = $state.Split('|')
    if ($stateParts.Count -ne 6 -or $stateParts[0] -ne $developmentDatabase -or $stateParts[1] -ne '0' -or $stateParts[2] -ne 't' -or $stateParts[3] -ne 't') {
        throw 'Desarrollo o recuperacion no estan en el estado protegido requerido para F1.2.'
    }

    if (-not $isF12Configuration) {
        if ($stateParts[4] -ne '0' -or $stateParts[5] -ne 'f') {
            throw 'Ya existen roles F1.2 o ruta_fija_test; el bootstrap inicial fue cancelado para no sobrescribir estado.'
        }

        $bootstrapContent = @(
            '# Credenciales administrativas privadas de F1.2. No cargar en Spring Boot ni versionar.',
            'RUTA_FIJA_BOOTSTRAP_HOST=127.0.0.1',
            'RUTA_FIJA_BOOTSTRAP_PORT=5432',
            "RUTA_FIJA_BOOTSTRAP_USERNAME=$($bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'])",
            "RUTA_FIJA_BOOTSTRAP_PASSWORD=$($bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'])"
        )
        $nativeContent = @(
            '# Configuracion privada F1.2. No versionar ni compartir.',
            'SPRING_PROFILES_ACTIVE=local',
            "SPRING_DATASOURCE_URL=$developmentUrl",
            'SPRING_DATASOURCE_USERNAME=rf_app',
            "SPRING_DATASOURCE_PASSWORD=$applicationPassword",
            "SPRING_FLYWAY_URL=$developmentUrl",
            'SPRING_FLYWAY_USERNAME=rf_migrator',
            "SPRING_FLYWAY_PASSWORD=$migratorPassword",
            "RF_TEST_DATASOURCE_URL=$testUrl",
            'RF_TEST_DATASOURCE_USERNAME=rf_test',
            "RF_TEST_DATASOURCE_PASSWORD=$testPassword",
            "JWT_SECRET_BASE64=$($nativeSettings['JWT_SECRET_BASE64'])",
            'APP_SEED_ENABLED=false',
            'APP_CORS_ALLOWED_ORIGINS=http://localhost:4200',
            'REFRESH_COOKIE_SECURE=false'
        )

        if (-not $bootstrapExists) {
            Write-PrivateEnvironmentFile -Path $BootstrapConfigPath -Content $bootstrapContent
        }
        Write-PrivateEnvironmentFile -Path $NativeConfigPath -Content $nativeContent -AllowReplace
        Test-SecretPathIsPrivate -Path $BootstrapConfigPath
        Test-SecretPathIsPrivate -Path $NativeConfigPath
    }

    [Environment]::SetEnvironmentVariable('RUTA_FIJA_MIGRATOR_PASSWORD', $migratorPassword, 'Process')
    [Environment]::SetEnvironmentVariable('RUTA_FIJA_APP_PASSWORD', $applicationPassword, 'Process')
    [Environment]::SetEnvironmentVariable('RUTA_FIJA_TEST_PASSWORD', $testPassword, 'Process')

    $roleCount = [int](Invoke-PostgresQuery -Database $developmentDatabase -Query "SELECT count(*) FROM pg_roles WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test');")
    if ($roleCount -eq 0) {
        $null = Invoke-PostgresSqlFile -Database $developmentDatabase -File (Join-Path $sqlDirectory 'F1_2_CreateRoles.sql')
    } elseif ($roleCount -ne 3) {
        throw 'Se detecto una creacion parcial de roles; no se intentara adivinar ni reparar privilegios.'
    }

    $testDatabaseExists = Invoke-PostgresQuery -Database $developmentDatabase -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$testDatabase');"
    if ($testDatabaseExists -eq 'f') {
        $null = Invoke-PostgresSqlFile -Database 'postgres' -File (Join-Path $sqlDirectory 'F1_2_CreateTestDatabase.sql')
    } elseif ($testDatabaseExists -ne 't') {
        throw 'No se pudo determinar el estado de ruta_fija_test.'
    }

    $null = Invoke-PostgresSqlFile -Database $developmentDatabase -File (Join-Path $sqlDirectory 'F1_2_ConfigureDevelopmentDatabase.sql')
    $null = Invoke-PostgresSqlFile -Database $testDatabase -File (Join-Path $sqlDirectory 'F1_2_ConfigureTestDatabase.sql')

    Write-Output 'F1_2_BOOTSTRAP=PASS'
    Write-Output 'roles=rf_migrator,rf_app,rf_test'
    Write-Output 'databases=solucion_ruta_fija_1,ruta_fija_test'
    Write-Output 'F1.2 no ejecuto Flyway, Spring Boot, API, CRM ni Docker.'
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
    [Environment]::SetEnvironmentVariable('RUTA_FIJA_MIGRATOR_PASSWORD', $previousMigratorPassword, 'Process')
    [Environment]::SetEnvironmentVariable('RUTA_FIJA_APP_PASSWORD', $previousApplicationPassword, 'Process')
    [Environment]::SetEnvironmentVariable('RUTA_FIJA_TEST_PASSWORD', $previousTestPassword, 'Process')
    $migratorPassword = $null
    $applicationPassword = $null
    $testPassword = $null
}
