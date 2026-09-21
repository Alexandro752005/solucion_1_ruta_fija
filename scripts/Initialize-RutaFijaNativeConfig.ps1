[CmdletBinding()]
param(
    [string]$ConfigPath,
    [string]$DatabaseUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1',
    [string]$DatabaseUsername = 'soporte',
    [string]$DatabasePasswordEnvironmentVariable
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

if (Test-Path -LiteralPath $ConfigPath) {
    throw "La configuracion local ya existe y no sera sobrescrita: $ConfigPath"
}
if ($DatabaseUrl -ne 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1') {
    throw 'La inicializacion solo permite la base de desarrollo aprobada para F1.1A.'
}
if ([string]::IsNullOrWhiteSpace($DatabaseUsername)) {
    throw 'El usuario de PostgreSQL no puede estar vacio.'
}

$securePassword = $null
$plainPassword = $null
$jwtBytes = $null
try {
    if ([string]::IsNullOrWhiteSpace($DatabasePasswordEnvironmentVariable)) {
        $securePassword = Read-Host 'Clave de PostgreSQL (entrada oculta)' -AsSecureString
    } else {
        $providedPassword = [Environment]::GetEnvironmentVariable($DatabasePasswordEnvironmentVariable, 'Process')
        if ([string]::IsNullOrWhiteSpace($providedPassword)) {
            throw "No existe una clave en la variable de proceso $DatabasePasswordEnvironmentVariable."
        }
        $securePassword = ConvertTo-SecureString $providedPassword -AsPlainText -Force
        [Environment]::SetEnvironmentVariable($DatabasePasswordEnvironmentVariable, $null, 'Process')
    }

    $plainPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
    if ([string]::IsNullOrWhiteSpace($plainPassword) -or $plainPassword -match '[\r\n]') {
        throw 'La clave PostgreSQL no es valida para el archivo local.'
    }

    $jwtBytes = [byte[]]::new(32)
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($jwtBytes)
    } finally {
        $randomGenerator.Dispose()
    }
    $jwtSecret = [Convert]::ToBase64String($jwtBytes)
    $configDirectory = Split-Path -Parent $ConfigPath
    $null = New-Item -ItemType Directory -Path $configDirectory -Force

    $content = @(
        '# Generado localmente por F1.1A. No versionar ni compartir.',
        'SPRING_PROFILES_ACTIVE=local',
        "SPRING_DATASOURCE_URL=$DatabaseUrl",
        "SPRING_DATASOURCE_USERNAME=$DatabaseUsername",
        "SPRING_DATASOURCE_PASSWORD=$plainPassword",
        "JWT_SECRET_BASE64=$jwtSecret",
        'APP_SEED_ENABLED=false',
        'APP_CORS_ALLOWED_ORIGINS=http://localhost:4200',
        'REFRESH_COOKIE_SECURE=false'
    )
    [IO.File]::WriteAllLines($ConfigPath, $content, [Text.UTF8Encoding]::new($false))
    Write-Output "RUTA_FIJA_NATIVE_CONFIG=CREATED path=$ConfigPath"
    Write-Output 'El archivo contiene secretos locales y esta excluido de Git.'
} finally {
    if ($null -ne $jwtBytes) {
        [Array]::Clear($jwtBytes, 0, $jwtBytes.Length)
    }
    $plainPassword = $null
}
