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
$importScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$settings = & $importScript -ConfigPath $ConfigPath -PassThru
if ($null -eq $settings) {
    throw 'No se pudo leer la configuracion nativa local.'
}

$expectedUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'
if ($settings['SPRING_PROFILES_ACTIVE'] -ne 'local') {
    throw 'F1.1A exige SPRING_PROFILES_ACTIVE=local.'
}
if ($settings['SPRING_DATASOURCE_URL'] -ne $expectedUrl) {
    throw 'El preflight rechazo un destino distinto a la base de desarrollo aprobada.'
}
if ($settings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'F1.1A exige semillas desactivadas hasta definir la politica de datos demo.'
}
if ($settings['REFRESH_COOKIE_SECURE'] -ne 'false') {
    throw 'La configuracion local HTTP exige REFRESH_COOKIE_SECURE=false.'
}
if ($settings['APP_CORS_ALLOWED_ORIGINS'] -ne 'http://localhost:4200') {
    throw 'El origen local aprobado es http://localhost:4200.'
}

$jwtBytes = $null
$previousValues = @{}
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    try {
        $jwtBytes = [Convert]::FromBase64String($settings['JWT_SECRET_BASE64'])
    } catch {
        throw 'JWT_SECRET_BASE64 no contiene Base64 valido.'
    }
    if ($jwtBytes.Length -lt 32) {
        throw 'JWT_SECRET_BASE64 debe representar al menos 32 bytes.'
    }

    foreach ($entry in $settings.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }

    $javaOutput = & cmd.exe /d /c "java -version 2>&1"
    $javaExitCode = $LASTEXITCODE
    $javaVersion = $javaOutput | Select-Object -First 1
    if ($javaExitCode -ne 0 -or $javaVersion -notmatch 'version "([0-9]+)') {
        throw 'Java no esta disponible.'
    }
    if ([int]$Matches[1] -lt 21) {
        throw 'Ruta Fija requiere Java 21 o posterior.'
    }

    $nodeVersion = (& node --version)
    if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v([0-9]+)') {
        throw 'Node.js no esta disponible.'
    }
    if ([int]$Matches[1] -lt 24) {
        throw 'Ruta Fija requiere Node.js 24 o posterior.'
    }

    $npmVersion = (& npm.cmd --version)
    if ($LASTEXITCODE -ne 0) {
        throw 'npm no esta disponible.'
    }

    $psqlCandidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $psql = $psqlCandidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontro psql de PostgreSQL 16.'
    }

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $settings['SPRING_DATASOURCE_PASSWORD'], 'Process')
    $databaseFacts = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $settings['SPRING_DATASOURCE_USERNAME'] -d solucion_ruta_fija_1 -At -F '|' -c "SELECT current_database(), current_setting('server_version'), current_setting('server_encoding'), current_setting('client_encoding');"
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL no aprobo la conexion de solo lectura de F1.1A.'
    }
    $facts = $databaseFacts.Trim().Split('|')
    if ($facts.Count -ne 4 -or $facts[0] -ne 'solucion_ruta_fija_1' -or -not $facts[1].StartsWith('16.') -or $facts[2] -ne 'UTF8' -or $facts[3] -ne 'UTF8') {
        throw 'El catalogo PostgreSQL no cumple el preflight esperado.'
    }

    Push-Location $repoRoot
    try {
        & git check-ignore -q -- 'backend/.local/ruta-fija-native.env'
        if ($LASTEXITCODE -ne 0) {
            throw 'El archivo de secretos local no esta ignorado por Git.'
        }
        $trackedSecretFile = & git ls-files -- 'backend/.local/ruta-fija-native.env'
        if (@($trackedSecretFile).Count -gt 0) {
            throw 'El archivo de secretos local esta versionado; preflight cancelado.'
        }
    } finally {
        Pop-Location
    }

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f1-1a-classpath.txt'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo resolver el controlador JDBC para el preflight.'
        }
        $jdbcClasspath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($jdbcClasspath)) {
            throw 'El classpath JDBC esta vacio.'
        }
        & java --class-path $jdbcClasspath '..\scripts\VerifyLocalPostgresql.java' 'src\main\resources\application-local.properties'
        if ($LASTEXITCODE -ne 0) {
            throw 'La comprobacion JDBC local no fue aprobada.'
        }
    } finally {
        Pop-Location
    }

    Write-Output "java=$javaVersion"
    Write-Output "node=$nodeVersion npm=$npmVersion"
    Write-Output "postgresql=$($facts[1]) encoding=$($facts[2])"
    Write-Output 'NATIVE_PREFLIGHT=PASS'
    Write-Output 'F1.1A no inicio Spring Boot, Flyway, API ni CRM.'
} finally {
    if ($null -ne $jwtBytes) {
        [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
    }
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}
