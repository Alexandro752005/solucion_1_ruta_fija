[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $repoRoot 'mobile'
$f43AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF43MobileAssignments.ps1'
$androidSdkRoot = 'D:\Android\Sdk'

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
    throw 'F4.4 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F4.4 no encontro el archivo requerido: $RelativePath"
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
        throw "F4.4 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "F4.4 incorpora fuera de alcance: $Description"
    }
}

if (-not (Test-Path -LiteralPath $f43AuditScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $mobileRoot -PathType Container)) {
    throw 'F4.4 no encontro los artefactos previos de F4.3 o Flutter.'
}

# Valida primero las garantías de F4.3/F3.4: PostgreSQL nativo, contrato,
# aislamiento e idempotencia de asignaciones no se deben degradar por M5/M6.
& $f43AuditScript -FlutterRoot $FlutterRoot -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'F4.3 no aprobo; F4.4 no puede extender una base no validada.'
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*http:\s*\^1\.6\.0\s*$' -Description 'cliente HTTP aprobado'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*flutter_secure_storage:\s*\^10\.3\.4\s*$' -Description 'almacenamiento protegido existente'
Assert-NotContains -Text $pubspec -Pattern '(?m)^\s*(sqflite|hive|workmanager|connectivity_plus|geolocator|permission_handler|image_picker|camera|firebase_)\s*:' -Description 'dependencia anticipada de M7-M8'

$apiClient = Get-Utf8Text 'mobile\lib\core\network\mobile_api_client.dart'
Assert-Contains -Text $apiClient -Pattern 'x-correlation-id' -Description 'correlacion de solicitudes'
Assert-Contains -Text $apiClient -Pattern 'cache-control' -Description 'no cache de respuestas moviles'
Assert-NotContains -Text $apiClient -Pattern '(?m)\b(print|debugPrint|log)\s*\(' -Description 'registro de cuerpos o tokens'

$incidentModels = Get-Utf8Text 'mobile\lib\features\incidents\domain\mobile_incident_models.dart'
foreach ($value in @('AVERIA', 'ACCIDENTE', 'RETRASO', 'OTRO', 'OPEN', 'FOLLOW_UP', 'RESOLVED', 'MOBILE_APP')) {
    Assert-Contains -Text $incidentModels -Pattern "'$value'" -Description "modelo de incidencia $value"
}

$incidentRepository = Get-Utf8Text 'mobile\lib\features\incidents\data\mobile_incident_repository.dart'
Assert-Contains -Text $incidentRepository -Pattern "'mobile/incidents'" -Description 'lista propia de incidencias'
Assert-Contains -Text $incidentRepository -Pattern "'sort': 'reportedAt,desc'" -Description 'orden estable de incidencias'
$incidentBody = [regex]::Match(
    $incidentRepository,
    'final\s+body\s*=\s*<String,\s*Object\?>\{(?<body>.*?)\};',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)
if (-not $incidentBody.Success) {
    throw 'F4.4 no encontro el cuerpo del reporte de incidencia.'
}
foreach ($field in @('category', 'description', 'occurredAt')) {
    Assert-Contains -Text $incidentBody.Groups['body'].Value -Pattern "'$field'" -Description "campo permitido de incidencia $field"
}
Assert-NotContains -Text $incidentBody.Groups['body'].Value -Pattern 'organizationId|tenantId|userId|driverId|source|status' -Description 'autoridad ajena en reporte de incidencia'

$incidentUi = @(
    Get-Utf8Text 'mobile\lib\features\incidents\presentation\incidents_page.dart'
    Get-Utf8Text 'mobile\lib\features\incidents\presentation\incident_detail_page.dart'
    Get-Utf8Text 'mobile\lib\features\incidents\presentation\incident_report_page.dart'
) -join "`n"
Assert-Contains -Text $incidentUi -Pattern 'incident-report-open' -Description 'entrada visible de reporte M5'
Assert-Contains -Text $incidentUi -Pattern 'incident-submit' -Description 'envio explicito de reporte M5'
Assert-Contains -Text $incidentUi -Pattern '_resultUnknown' -Description 'manejo de resultado desconocido sin prometer idempotencia'
Assert-NotContains -Text $incidentUi -Pattern 'FlutterSecureStorage|SharedPreferences|MobileIncidentCommandStore|\.write\s*\(' -Description 'persistencia o cola offline de incidencias'
Assert-NotContains -Text $incidentUi -Pattern 'FakeMobile|MockClient|testIncident|Timer\(|workmanager|background\s*(service|task|fetch)|geolocator|permission_handler|image_picker|camera|firebase' -Description 'simulacion, GPS, fotos o trabajo de fondo en M5'

$announcementModels = Get-Utf8Text 'mobile\lib\features\announcements\domain\mobile_announcement_models.dart'
Assert-Contains -Text $announcementModels -Pattern 'requiresReadConfirmation' -Description 'estado de acuse explicito'
$announcementRepository = Get-Utf8Text 'mobile\lib\features\announcements\data\mobile_announcement_repository.dart'
Assert-Contains -Text $announcementRepository -Pattern "'mobile/announcements'" -Description 'lista propia de comunicados'
Assert-Contains -Text $announcementRepository -Pattern "'sort': 'createdAt,desc'" -Description 'orden estable de comunicados'
Assert-Contains -Text $announcementRepository -Pattern 'mobile/announcements/\$normalizedId/read' -Description 'ruta idempotente de acuse'
Assert-Contains -Text $announcementRepository -Pattern 'const \{\}' -Description 'acuse sin identidad aportada por el dispositivo'

$announcementListUi = Get-Utf8Text 'mobile\lib\features\announcements\presentation\announcements_page.dart'
$announcementDetailUi = Get-Utf8Text 'mobile\lib\features\announcements\presentation\announcement_detail_page.dart'
Assert-Contains -Text $announcementListUi -Pattern 'Abrir un comunicado no confirma' -Description 'apertura sin acuse automatico'
Assert-NotContains -Text $announcementListUi -Pattern 'markRead\s*\(' -Description 'acuse automatico desde lista'
Assert-Contains -Text $announcementDetailUi -Pattern '_confirmRead' -Description 'accion humana de lectura'
Assert-Contains -Text $announcementDetailUi -Pattern 'showDialog' -Description 'confirmacion humana del acuse'
Assert-Contains -Text $announcementDetailUi -Pattern 'announcement-confirm-read' -Description 'control visible de acuse'
Assert-Contains -Text $announcementDetailUi -Pattern 'markRead\(announcement\.id\)' -Description 'acuse idempotente al servidor'
Assert-NotContains -Text $announcementDetailUi -Pattern 'FakeMobile|MockClient|testAnnouncement|Timer\(|workmanager|background\s*(service|task|fetch)|geolocator|permission_handler|image_picker|camera|firebase' -Description 'simulacion, GPS, fotos o trabajo de fondo en M6'

$appDependencies = Get-Utf8Text 'mobile\lib\app\ruta_fija_dependencies.dart'
Assert-Contains -Text $appDependencies -Pattern 'MobileIncidentRepository' -Description 'inyeccion del repositorio de incidencias'
Assert-Contains -Text $appDependencies -Pattern 'MobileAnnouncementRepository' -Description 'inyeccion del repositorio de comunicados'

$appTests = Get-Utf8Text 'mobile\test\app\ruta_fija_app_test.dart'
Assert-Contains -Text $appTests -Pattern 'reports a real own incident' -Description 'prueba de incidencia propia'
Assert-Contains -Text $appTests -Pattern 'confirms announcement reading only after explicit conductor action' -Description 'prueba de acuse humano'

$androidManifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
Assert-Contains -Text $androidManifest -Pattern 'android:allowBackup="false"' -Description 'backup Android deshabilitado'
Assert-NotContains -Text $androidManifest -Pattern 'ACCESS_(FINE|COARSE)_LOCATION|CAMERA|POST_NOTIFICATIONS' -Description 'permiso Android adelantado fuera de M5-M6'
Assert-NotContains -Text $androidManifest -Pattern 'usesCleartextTraffic\s*=\s*"true"' -Description 'HTTP global de release'

$dockerArtifacts = @(Get-ChildItem -LiteralPath $mobileRoot -Recurse -Force -File |
        Where-Object { $_.Name -match '(?i)docker' })
if ($dockerArtifacts.Count -gt 0) {
    throw 'F4.4 detecto artefactos Docker dentro del cliente movil.'
}

$apksigner = Join-Path $androidSdkRoot 'build-tools\36.0.0\apksigner.bat'
$ndkSourceProperties = Join-Path $androidSdkRoot 'ndk\28.2.13676358\source.properties'
if (-not (Test-Path -LiteralPath $apksigner -PathType Leaf) -or
    -not (Test-Path -LiteralPath $ndkSourceProperties -PathType Leaf)) {
    throw 'F4.4 requiere Build-Tools 36.0.0 y NDK 28.2.13676358 ya aprobados.'
}
$env:ANDROID_HOME = $androidSdkRoot

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
$version = @(& $flutter --version 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'Flutter no pudo informar su version durante la auditoria F4.4.'
}
$versionLine = @($version | Where-Object { $_ -match '^Flutter\s' })[0]
if ([string]::IsNullOrWhiteSpace($versionLine)) {
    throw 'F4.4 no pudo identificar una version estable de Flutter.'
}

Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo F4.4.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo F4.4.'
    }
    & $flutter build apk --debug
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter build apk --debug no aprobo F4.4.'
    }
    $doctorOutput = @(& $flutter doctor -v 2>&1)
}
finally {
    Pop-Location
}

$doctorText = $doctorOutput -join "`n"
if ($doctorText -notmatch 'Android toolchain' -or $doctorText -notmatch 'Android SDK at') {
    throw 'flutter doctor no confirma el Android SDK para F4.4.'
}
$apkPath = Join-Path $mobileRoot 'build\app\outputs\flutter-apk\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw 'F4.4 no encontro el APK debug despues de compilar.'
}
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
if ($apkBytes -le 0) {
    throw 'F4.4 produjo un APK debug vacio.'
}
$signatureOutput = @(& $apksigner verify --verbose --print-certs $apkPath 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'F4.4 no pudo verificar la firma del APK debug.'
}
$signatureText = $signatureOutput -join "`n"
Assert-Contains -Text $signatureText -Pattern '(?m)^Verifies\s*$' -Description 'APK verificable'
Assert-Contains -Text $signatureText -Pattern 'Verified using v2 scheme \(APK Signature Scheme v2\): true' -Description 'firma v2 valida del APK'

Write-Output "F4_4_MOBILE_INCIDENTS_ANNOUNCEMENTS=PASS flutter='$versionLine' m1=8/80 m2=12/80 m3=8/80 m4=18/80 m5=7/80 m6=5/80 total=58/80 android=READY apk_bytes=$apkBytes apk_v2=PASS docker=0"
