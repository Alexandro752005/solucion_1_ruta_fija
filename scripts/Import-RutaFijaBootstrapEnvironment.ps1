[CmdletBinding()]
param(
    [string]$ConfigPath,
    [switch]$PassThru
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}

if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "No existe la configuracion privada de bootstrap: $ConfigPath"
}

$allowedKeys = @(
    'RUTA_FIJA_BOOTSTRAP_HOST',
    'RUTA_FIJA_BOOTSTRAP_PORT',
    'RUTA_FIJA_BOOTSTRAP_USERNAME',
    'RUTA_FIJA_BOOTSTRAP_PASSWORD'
)
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

foreach ($requiredKey in $allowedKeys) {
    if (-not $settings.Contains($requiredKey)) {
        throw "Falta la variable obligatoria $requiredKey."
    }
}

if ($settings['RUTA_FIJA_BOOTSTRAP_HOST'] -ne '127.0.0.1' -or $settings['RUTA_FIJA_BOOTSTRAP_PORT'] -ne '5432') {
    throw 'El bootstrap F1.2 solo admite PostgreSQL local en 127.0.0.1:5432.'
}

if ($PassThru) {
    Write-Output -NoEnumerate $settings
    return
}

foreach ($entry in $settings.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

Write-Output 'RUTA_FIJA_BOOTSTRAP_ENV=LOADED'
