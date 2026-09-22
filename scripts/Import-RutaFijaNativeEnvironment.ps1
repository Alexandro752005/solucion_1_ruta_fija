[CmdletBinding()]
param(
    [string]$ConfigPath,
    [switch]$PassThru,
    [ValidateSet('Runtime', 'Test', 'All')]
    [string]$Scope = 'Runtime'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "No existe la configuracion nativa local: $ConfigPath"
}

$f11aKeys = @(
    'SPRING_PROFILES_ACTIVE',
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD',
    'JWT_SECRET_BASE64',
    'APP_SEED_ENABLED',
    'APP_CORS_ALLOWED_ORIGINS',
    'REFRESH_COOKIE_SECURE'
)
$f12Keys = @(
    'SPRING_FLYWAY_URL',
    'SPRING_FLYWAY_USERNAME',
    'SPRING_FLYWAY_PASSWORD',
    'RF_TEST_DATASOURCE_URL',
    'RF_TEST_DATASOURCE_USERNAME',
    'RF_TEST_DATASOURCE_PASSWORD'
)
$f14Keys = @(
    'RF_TEST_MIGRATOR_URL',
    'RF_TEST_MIGRATOR_USERNAME',
    'RF_TEST_MIGRATOR_PASSWORD'
)
$allowedKeys = @($f11aKeys + $f12Keys + $f14Keys)
$settings = [ordered]@{}
$lineNumber = 0

foreach ($line in [IO.File]::ReadAllLines($ConfigPath, [Text.Encoding]::UTF8)) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) {
        throw "La linea $lineNumber no tiene el formato NOMBRE=VALOR."
    }

    $key = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1)
    if ($key -notmatch '^[A-Z][A-Z0-9_]*$' -or $key -notin $allowedKeys) {
        throw "La linea $lineNumber contiene una variable no permitida."
    }
    if ($settings.Contains($key)) {
        throw "La variable $key esta repetida."
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "La variable $key no puede estar vacia."
    }

    $settings[$key] = $value
}

foreach ($requiredKey in $f11aKeys) {
    if (-not $settings.Contains($requiredKey)) {
        throw "Falta la variable obligatoria $requiredKey."
    }
}

$presentF12Keys = @($f12Keys | Where-Object { $settings.Contains($_) })
if ($presentF12Keys.Count -gt 0 -and $presentF12Keys.Count -ne $f12Keys.Count) {
    throw 'La configuracion F1.2 esta incompleta: las variables de Flyway y ruta_fija_test deben declararse juntas.'
}

$presentF14Keys = @($f14Keys | Where-Object { $settings.Contains($_) })
if ($presentF14Keys.Count -gt 0 -and $presentF14Keys.Count -ne $f14Keys.Count) {
    throw 'La configuracion F1.4 esta incompleta: las variables del migrador de pruebas deben declararse juntas.'
}

if ($Scope -eq 'Runtime') {
    $runtimeSettings = [ordered]@{}
    foreach ($key in @($f11aKeys + @('SPRING_FLYWAY_URL', 'SPRING_FLYWAY_USERNAME', 'SPRING_FLYWAY_PASSWORD'))) {
        if ($settings.Contains($key)) {
            $runtimeSettings[$key] = $settings[$key]
        }
    }
    $settings = $runtimeSettings
}
elseif ($Scope -eq 'Test') {
    foreach ($requiredKey in $f12Keys) {
        if (-not $settings.Contains($requiredKey)) {
            throw "F1.4 requiere la variable $requiredKey para ejecutar pruebas nativas."
        }
    }

    $testUrl = $settings['RF_TEST_DATASOURCE_URL']
    if ($testUrl -ne 'jdbc:postgresql://127.0.0.1:5432/ruta_fija_test') {
        throw 'F1.4 solo autoriza ruta_fija_test en PostgreSQL local 127.0.0.1:5432.'
    }
    if ($settings['RF_TEST_DATASOURCE_USERNAME'] -ne 'rf_test') {
        throw 'F1.4 requiere RF_TEST_DATASOURCE_USERNAME=rf_test.'
    }

    $testMigratorUrl = if ($settings.Contains('RF_TEST_MIGRATOR_URL')) {
        $settings['RF_TEST_MIGRATOR_URL']
    }
    else {
        $testUrl
    }
    $testMigratorUsername = if ($settings.Contains('RF_TEST_MIGRATOR_USERNAME')) {
        $settings['RF_TEST_MIGRATOR_USERNAME']
    }
    else {
        $settings['SPRING_FLYWAY_USERNAME']
    }
    $testMigratorPassword = if ($settings.Contains('RF_TEST_MIGRATOR_PASSWORD')) {
        $settings['RF_TEST_MIGRATOR_PASSWORD']
    }
    else {
        $settings['SPRING_FLYWAY_PASSWORD']
    }

    if ($testMigratorUrl -ne $testUrl -or $testMigratorUsername -ne 'rf_migrator') {
        throw 'F1.4 exige rf_migrator y la misma ruta_fija_test para Flyway de pruebas.'
    }

    $testSettings = [ordered]@{
        SPRING_PROFILES_ACTIVE = 'test'
        SPRING_DATASOURCE_URL = $testUrl
        SPRING_DATASOURCE_USERNAME = $settings['RF_TEST_DATASOURCE_USERNAME']
        SPRING_DATASOURCE_PASSWORD = $settings['RF_TEST_DATASOURCE_PASSWORD']
        SPRING_FLYWAY_URL = $testMigratorUrl
        SPRING_FLYWAY_USER = $testMigratorUsername
        SPRING_FLYWAY_USERNAME = $testMigratorUsername
        SPRING_FLYWAY_PASSWORD = $testMigratorPassword
        JWT_SECRET_BASE64 = $settings['JWT_SECRET_BASE64']
        APP_SEED_ENABLED = 'false'
        APP_CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
        REFRESH_COOKIE_SECURE = 'false'
        RF_TEST_DATASOURCE_URL = $testUrl
        RF_TEST_DATASOURCE_USERNAME = $settings['RF_TEST_DATASOURCE_USERNAME']
        RF_TEST_DATASOURCE_PASSWORD = $settings['RF_TEST_DATASOURCE_PASSWORD']
        RF_TEST_MIGRATOR_URL = $testMigratorUrl
        RF_TEST_MIGRATOR_USERNAME = $testMigratorUsername
        RF_TEST_MIGRATOR_PASSWORD = $testMigratorPassword
    }
    $settings = $testSettings
}

if ($PassThru) {
    Write-Output -NoEnumerate $settings
    return
}

foreach ($entry in $settings.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

Write-Output 'RUTA_FIJA_NATIVE_ENV=LOADED'
