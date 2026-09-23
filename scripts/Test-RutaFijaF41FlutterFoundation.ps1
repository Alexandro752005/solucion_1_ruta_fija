[CmdletBinding()]
param(
    [string]$FlutterRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $repoRoot 'mobile'

function Resolve-FlutterCommand {
    param([string]$RequestedRoot)

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($RequestedRoot)) {
        $candidates += (Join-Path $RequestedRoot 'bin\flutter.bat')
    }
    if (-not [string]::IsNullOrWhiteSpace($env:FLUTTER_ROOT)) {
        $candidates += (Join-Path $env:FLUTTER_ROOT 'bin\flutter.bat')
    }
    $candidates += 'D:\dev\flutter\bin\flutter.bat'

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    $fromPath = Get-Command flutter.bat -ErrorAction SilentlyContinue
    if ($null -eq $fromPath) {
        $fromPath = Get-Command flutter -ErrorAction SilentlyContinue
    }
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    throw 'F4.1 requiere Flutter. Instale el SDK estable y agregue su carpeta bin al PATH del usuario.'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F4.1 no encontro el archivo requerido: $RelativePath"
    }
    return (Get-Content -LiteralPath $path -Raw -Encoding UTF8).TrimStart([char]0xFEFF)
}

function Assert-Contains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -notmatch $Pattern) {
        throw "F4.1 no satisface: $Description"
    }
}

if (-not (Test-Path -LiteralPath $mobileRoot -PathType Container)) {
    throw 'F4.1 no encontro la aplicacion mobile/.'
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^name:\s*ruta_fija_conductor\r?$' -Description 'nombre de paquete Flutter estable'
Assert-Contains -Text $pubspec -Pattern '(?m)^publish_to:\s*''none''\r?$' -Description 'paquete no publicable'
if ($pubspec -match '(?m)^\s*(dio|geolocator|permission_handler|firebase_)\s*:') {
    throw 'La base Flutter incorpora una dependencia posterior a F4.2.'
}

$environment = Get-Utf8Text 'mobile\lib\app\ruta_fija_environment.dart'
Assert-Contains -Text $environment -Pattern 'RF_API_BASE_URL' -Description 'configuracion de URL por dart-define'
Assert-Contains -Text $environment -Pattern '10\.0\.2\.2:8080/api/v1' -Description 'ruta de desarrollo del emulador'
Assert-Contains -Text $environment -Pattern 'uri\.userInfo\.isEmpty' -Description 'rechazo de credenciales en la URL'

$router = Get-Utf8Text 'mobile\lib\app\ruta_fija_router.dart'
Assert-Contains -Text $router -Pattern 'onGenerateRoute' -Description 'navegacion centralizada'
$shell = Get-Utf8Text 'mobile\lib\features\bootstrap\presentation\conductor_navigation_shell.dart'
Assert-Contains -Text $shell -Pattern 'Pendiente de construc' -Description 'frontera honesta de modulos no implementados'
Assert-Contains -Text $shell -Pattern 'datos locales ni respuestas simuladas|no muestran datos simulados' -Description 'ausencia de datos simulados en modulos futuros'
$androidBuild = Get-Utf8Text 'mobile\android\app\build.gradle.kts'
Assert-Contains -Text $androidBuild -Pattern 'applicationId\s*=\s*"pe\.rutafija\.conductor"' -Description 'identificador Android de Ruta Fija'
$manifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
if ($manifest -match 'usesCleartextTraffic\s*=\s*"true"') {
    throw 'F4.1 no permite trafico HTTP global en el manifiesto Android.'
}

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
$version = @(& $flutter --version 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'Flutter no pudo informar su version durante la auditoria F4.1.'
}
$versionLine = @($version | Where-Object { $_ -match '^Flutter\s' })[0]
if ([string]::IsNullOrWhiteSpace($versionLine)) {
    throw 'F4.1 no pudo identificar una version estable de Flutter.'
}

Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo F4.1.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo F4.1.'
    }

    $doctorOutput = @(& $flutter doctor -v 2>&1)
    $androidToolchain = if (($doctorOutput -join "`n") -match 'Android toolchain.*\r?\n\s+• Android SDK at') {
        'READY'
    }
    else {
        'PENDING_OWNER_LICENSE'
    }

    $doctorText = $doctorOutput -join "`n"
    if ($doctorText -match 'Android toolchain' -and $doctorText -match 'Android SDK at') {
        $androidToolchain = 'READY'
    }
}
finally {
    Pop-Location
}

Write-Output "F4_1_FLUTTER_FOUNDATION=PASS flutter='$versionLine' m1=8/80 android=$androidToolchain manifest=PASS docker=0"
