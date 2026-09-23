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
    throw 'F4.2 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F4.2 no encontro el archivo requerido: $RelativePath"
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
        throw "F4.2 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "F4.2 incorpora fuera de alcance: $Description"
    }
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*http:\s*\^1\.6\.0\s*$' -Description 'cliente HTTP versionado'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*flutter_secure_storage:\s*\^10\.3\.4\s*$' -Description 'almacenamiento seguro Android compatible con API 36'
Assert-NotContains -Text $pubspec -Pattern '(?m)^\s*(dio|shared_preferences|geolocator|permission_handler|firebase_)\s*:' -Description 'dependencia adelantada de M4-M8'

$tokenStore = Get-Utf8Text 'mobile\lib\core\session\mobile_secure_token_store.dart'
Assert-Contains -Text $tokenStore -Pattern 'FlutterSecureStorage' -Description 'refresh token en almacenamiento seguro del SO'
Assert-Contains -Text $tokenStore -Pattern 'storageNamespace' -Description 'espacio de almacenamiento aislado'
Assert-NotContains -Text $tokenStore -Pattern 'SharedPreferences' -Description 'refresh token en preferencias planas'

$session = Get-Utf8Text 'mobile\lib\core\session\mobile_session_controller.dart'
Assert-Contains -Text $session -Pattern 'refreshAccessToken' -Description 'rotacion centralizada de refresh'
Assert-Contains -Text $session -Pattern '_refreshInFlight' -Description 'serializacion de refresh concurrente'
Assert-Contains -Text $session -Pattern '_clearLocalSession' -Description 'cierre local al invalidar sesion'

$apiClient = Get-Utf8Text 'mobile\lib\core\network\mobile_api_client.dart'
Assert-Contains -Text $apiClient -Pattern 'x-correlation-id' -Description 'correlacion de solicitudes moviles'
Assert-Contains -Text $apiClient -Pattern 'authorization' -Description 'Bearer solo en solicitudes protegidas'
Assert-Contains -Text $apiClient -Pattern 'allowRefresh' -Description 'reintento controlado despues de refresh'
Assert-NotContains -Text $apiClient -Pattern '(?m)\b(print|debugPrint|log)\s*\(' -Description 'registro de tokens o cuerpos HTTP'

$authRepository = Get-Utf8Text 'mobile\lib\features\auth\data\mobile_auth_repository.dart'
Assert-Contains -Text $authRepository -Pattern 'mobile/auth/login' -Description 'login JSON de conductor'
Assert-Contains -Text $authRepository -Pattern 'mobile/auth/refresh' -Description 'refresh JSON separado del CRM'
Assert-Contains -Text $authRepository -Pattern 'mobile/auth/logout' -Description 'revocacion de familia refresh'

$driverRepository = Get-Utf8Text 'mobile\lib\features\profile\data\mobile_driver_repository.dart'
Assert-Contains -Text $driverRepository -Pattern "'mobile/me'" -Description 'perfil propio del conductor'
Assert-Contains -Text $driverRepository -Pattern "'mobile/availability'" -Description 'disponibilidad propia'
Assert-Contains -Text $driverRepository -Pattern 'canBeChosenByDriver' -Description 'bloqueo cliente de RESERVADO y EN_SERVICIO'
Assert-NotContains -Text $driverRepository -Pattern 'mobile/(assignments|incidents|announcements|location)' -Description 'operacion movil posterior a F4.2'

$environment = Get-Utf8Text 'mobile\lib\app\ruta_fija_environment.dart'
Assert-Contains -Text $environment -Pattern 'dart\.vm\.product' -Description 'deteccion de compilacion release'
Assert-Contains -Text $environment -Pattern "uri\.scheme\s*!=\s*'https'" -Description 'HTTPS obligatorio en release'

$mainManifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
Assert-Contains -Text $mainManifest -Pattern 'android\.permission\.INTERNET' -Description 'permiso de red Android'
Assert-Contains -Text $mainManifest -Pattern 'android:allowBackup="false"' -Description 'backup Android deshabilitado para secretos'
Assert-NotContains -Text $mainManifest -Pattern 'usesCleartextTraffic\s*=\s*"true"' -Description 'trafico HTTP global en release'
$debugManifest = Get-Utf8Text 'mobile\android\app\src\debug\AndroidManifest.xml'
Assert-Contains -Text $debugManifest -Pattern 'usesCleartextTraffic\s*=\s*"true"' -Description 'HTTP limitado al build debug local'

$gradleProperties = Get-Utf8Text 'mobile\android\gradle.properties'
Assert-Contains -Text $gradleProperties -Pattern '-Xmx1024m' -Description 'limite Gradle compatible con equipo de 8 GB'
Assert-Contains -Text $gradleProperties -Pattern 'org\.gradle\.workers\.max=1' -Description 'un worker Gradle para equipo limitado'
Assert-Contains -Text $gradleProperties -Pattern 'org\.gradle\.daemon=false' -Description 'sin daemon Gradle persistente'

$androidSdkRoot = 'D:\Android\Sdk'
$ndkSourceProperties = Join-Path $androidSdkRoot 'ndk\28.2.13676358\source.properties'
if (-not (Test-Path -LiteralPath $ndkSourceProperties -PathType Leaf)) {
    throw 'F4.2 requiere Android NDK 28.2.13676358 para los activos nativos de Flutter.'
}
$env:ANDROID_HOME = $androidSdkRoot

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
$version = @(& $flutter --version 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'Flutter no pudo informar su version durante la auditoria F4.2.'
}
$versionLine = @($version | Where-Object { $_ -match '^Flutter\s' })[0]
if ([string]::IsNullOrWhiteSpace($versionLine)) {
    throw 'F4.2 no pudo identificar una version estable de Flutter.'
}

Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo F4.2.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo F4.2.'
    }
    & $flutter build apk --debug
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter build apk --debug no aprobo F4.2.'
    }
    $doctorOutput = @(& $flutter doctor -v 2>&1)
}
finally {
    Pop-Location
}

$doctorText = $doctorOutput -join "`n"
if ($doctorText -notmatch 'Android toolchain' -or $doctorText -notmatch 'Android SDK at') {
    throw 'flutter doctor no confirma el Android SDK para F4.2.'
}
$apkPath = Join-Path $mobileRoot 'build\app\outputs\flutter-apk\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw 'F4.2 no encontro el APK debug despues de compilar.'
}
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
if ($apkBytes -le 0) {
    throw 'F4.2 produjo un APK debug vacio.'
}

Write-Output "F4_2_MOBILE_SESSION_PROFILE_AVAILABILITY=PASS flutter='$versionLine' m1=8/80 m2=12/80 m3=8/80 total=28/80 android=READY apk_bytes=$apkBytes docker=0"
