[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $repoRoot 'mobile'
$f34AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF34ContractReports.ps1'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

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
    throw 'F4.3 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F4.3 no encontro el archivo requerido: $RelativePath"
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
        throw "F4.3 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "F4.3 incorpora fuera de alcance: $Description"
    }
}

if (-not (Test-Path -LiteralPath $f34AuditScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $mobileRoot -PathType Container)) {
    throw 'F4.3 no encontro los artefactos previos de contrato o Flutter.'
}

# Conserva la evidencia PostgreSQL nativa, privacidad y frontera CRM antes de
# verificar que Flutter consuma las rutas propias del conductor.
& $f34AuditScript -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'F3.4 no aprobo; F4.3 no puede consumir un contrato no validado.'
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*http:\s*\^1\.6\.0\s*$' -Description 'cliente HTTP actual'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*flutter_secure_storage:\s*\^10\.3\.4\s*$' -Description 'persistencia protegida ya aprobada'
Assert-NotContains -Text $pubspec -Pattern '(?m)^\s*(sqflite|hive|workmanager|connectivity_plus|geolocator|permission_handler|firebase_)\s*:' -Description 'dependencia anticipada de M5-M8'

$apiClient = Get-Utf8Text 'mobile\lib\core\network\mobile_api_client.dart'
Assert-Contains -Text $apiClient -Pattern 'MobileApiObjectResponse' -Description 'metadatos HTTP seguros para replay'
Assert-Contains -Text $apiClient -Pattern 'queryParameters' -Description 'paginacion y filtros sin concatenar autoridad'
Assert-Contains -Text $apiClient -Pattern 'x-correlation-id' -Description 'correlacion de solicitudes'
Assert-NotContains -Text $apiClient -Pattern '(?m)\b(print|debugPrint|log)\s*\(' -Description 'registro de cuerpos o tokens'

$models = Get-Utf8Text 'mobile\lib\features\assignments\domain\mobile_assignment_models.dart'
foreach ($value in @('PENDING_RESPONSE', 'SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')) {
    Assert-Contains -Text $models -Pattern $value -Description "modelo de estado $value"
}
foreach ($command in @("accept", "reject", "start", "complete")) {
    Assert-Contains -Text $models -Pattern "'$command'" -Description "comando movil $command"
}
$commandBody = [regex]::Match(
    $models,
    'Map<String, Object\?> toRequestBody\(\) \{(?<body>.*?)return body;',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)
if (-not $commandBody.Success) {
    throw 'F4.3 no encontro el cuerpo idempotente del comando movil.'
}
Assert-Contains -Text $commandBody.Groups['body'].Value -Pattern 'clientEventId' -Description 'evento global del comando'
Assert-Contains -Text $commandBody.Groups['body'].Value -Pattern 'occurredAt' -Description 'instante durable del comando'
Assert-Contains -Text $commandBody.Groups['body'].Value -Pattern "'version'" -Description 'version optimista del comando'
Assert-NotContains -Text $commandBody.Groups['body'].Value -Pattern 'organizationId|userId|driverId' -Description 'autoridad ajena en el body movil'

$store = Get-Utf8Text 'mobile\lib\features\assignments\data\mobile_assignment_command_store.dart'
Assert-Contains -Text $store -Pattern 'FlutterSecureStorage' -Description 'comando pendiente protegido por Android'
Assert-Contains -Text $store -Pattern 'pe\.rutafija\.conductor\.operations' -Description 'espacio de operaciones separado del refresh'
Assert-Contains -Text $store -Pattern 'readForDriver' -Description 'aislamiento de comando por conductor'
Assert-NotContains -Text $store -Pattern 'SharedPreferences' -Description 'comando en preferencias planas'

$repository = Get-Utf8Text 'mobile\lib\features\assignments\data\mobile_assignment_repository.dart'
Assert-Contains -Text $repository -Pattern "'mobile/assignments'" -Description 'lista exclusiva del conductor'
Assert-Contains -Text $repository -Pattern 'await\s+_commandStore\.write\(pending\);\s*return\s+_deliver\(pending\);' -Description 'persistencia previa al envio'
Assert-Contains -Text $repository -Pattern 'x-idempotent-replay' -Description 'lectura de replay durable'
Assert-Contains -Text $repository -Pattern 'MobileNetworkException' -Description 'conservacion ante resultado desconocido'
Assert-Contains -Text $repository -Pattern 'MobileAssignmentPendingCommandException' -Description 'bloqueo de evento paralelo'
Assert-NotContains -Text $repository -Pattern 'mobile/(incidents|announcements|location)' -Description 'modulos posteriores mezclados con M4'

$assignmentUi = Get-Utf8Text 'mobile\lib\features\assignments\presentation\assignment_detail_page.dart'
Assert-Contains -Text $assignmentUi -Pattern 'Reintentar exactamente' -Description 'reintento explicito sin nuevo evento'
Assert-Contains -Text $assignmentUi -Pattern 'showDialog' -Description 'confirmacion humana antes de mutar'
Assert-Contains -Text $assignmentUi -Pattern 'assignment-command-accept' -Description 'aceptacion visible solo en movil'
Assert-NotContains -Text $assignmentUi -Pattern 'location|announcement|incident' -Description 'capacidad M5-M7 adelantada'

$assignmentSources = @(
    Get-ChildItem -LiteralPath (Join-Path $mobileRoot 'lib\features\assignments') -Recurse -File -Filter '*.dart' |
        ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }
) -join "`n"
Assert-NotContains -Text $assignmentSources -Pattern 'FakeMobile|MockClient|testAssignment' -Description 'datos simulados en produccion M4'
Assert-NotContains -Text $assignmentSources -Pattern 'Timer\(|workmanager|background\s*(service|task|fetch)' -Description 'cola offline o trabajo en segundo plano M8'

$androidManifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
Assert-Contains -Text $androidManifest -Pattern 'android:allowBackup="false"' -Description 'backup Android deshabilitado'
Assert-NotContains -Text $androidManifest -Pattern 'usesCleartextTraffic\s*=\s*"true"' -Description 'HTTP global de release'

$dockerArtifacts = @(Get-ChildItem -LiteralPath $mobileRoot -Recurse -Force -File |
        Where-Object { $_.Name -match '(?i)docker' })
if ($dockerArtifacts.Count -gt 0) {
    throw 'F4.3 detecto artefactos Docker dentro del cliente movil.'
}

$androidSdkRoot = 'D:\Android\Sdk'
$ndkSourceProperties = Join-Path $androidSdkRoot 'ndk\28.2.13676358\source.properties'
if (-not (Test-Path -LiteralPath $ndkSourceProperties -PathType Leaf)) {
    throw 'F4.3 requiere Android NDK 28.2.13676358 para los activos nativos de Flutter.'
}
$env:ANDROID_HOME = $androidSdkRoot

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
$version = @(& $flutter --version 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'Flutter no pudo informar su version durante la auditoria F4.3.'
}
$versionLine = @($version | Where-Object { $_ -match '^Flutter\s' })[0]
if ([string]::IsNullOrWhiteSpace($versionLine)) {
    throw 'F4.3 no pudo identificar una version estable de Flutter.'
}

Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo F4.3.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo F4.3.'
    }
    & $flutter build apk --debug
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter build apk --debug no aprobo F4.3.'
    }
    $doctorOutput = @(& $flutter doctor -v 2>&1)
}
finally {
    Pop-Location
}

$doctorText = $doctorOutput -join "`n"
if ($doctorText -notmatch 'Android toolchain' -or $doctorText -notmatch 'Android SDK at') {
    throw 'flutter doctor no confirma el Android SDK para F4.3.'
}
$apkPath = Join-Path $mobileRoot 'build\app\outputs\flutter-apk\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw 'F4.3 no encontro el APK debug despues de compilar.'
}
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
if ($apkBytes -le 0) {
    throw 'F4.3 produjo un APK debug vacio.'
}

Write-Output "F4_3_MOBILE_ASSIGNMENTS=PASS flutter='$versionLine' m1=8/80 m2=12/80 m3=8/80 m4=18/80 total=46/80 android=READY apk_bytes=$apkBytes docker=0"
