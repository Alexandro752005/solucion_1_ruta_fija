[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$f44AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF44MobileIncidentsAnnouncements.ps1'

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "M7 no encontro el archivo requerido: $RelativePath"
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
        throw "M7 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "M7 incorpora fuera de alcance: $Description"
    }
}

if (-not (Test-Path -LiteralPath $f44AuditScript -PathType Leaf)) {
    throw 'M7 requiere la auditoria F4.4 aprobada como linea base.'
}

# Reejecuta la regresion completa de M1-M6, incluidos analyze, test, APK y firma.
& $f44AuditScript -FlutterRoot $FlutterRoot -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'M7 no puede extender una base movil no aprobada.'
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^\s*geolocator:\s*\^14\.0\.3\s*$' -Description 'adaptador de ubicacion aprobado'
Assert-NotContains -Text $pubspec -Pattern '(?m)^\s*(permission_handler|workmanager|connectivity_plus|sqflite|hive|image_picker|camera|firebase_)\s*:' -Description 'permisos o capacidades diferidas'

$androidManifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
Assert-Contains -Text $androidManifest -Pattern 'ACCESS_COARSE_LOCATION' -Description 'permiso aproximado Android'
Assert-Contains -Text $androidManifest -Pattern 'ACCESS_FINE_LOCATION' -Description 'permiso preciso opcional Android'
Assert-NotContains -Text $androidManifest -Pattern 'ACCESS_BACKGROUND_LOCATION|FOREGROUND_SERVICE_LOCATION|FOREGROUND_SERVICE' -Description 'seguimiento fuera de primer plano'
Assert-NotContains -Text $androidManifest -Pattern 'CAMERA|POST_NOTIFICATIONS' -Description 'permisos no autorizados'

$deviceGateway = Get-Utf8Text 'mobile\lib\features\location\data\geolocator_device_location_gateway.dart'
Assert-Contains -Text $deviceGateway -Pattern 'Geolocator\.getCurrentPosition' -Description 'captura puntual de ubicacion'
Assert-Contains -Text $deviceGateway -Pattern 'timeLimit:\s*_captureTimeout' -Description 'limite de tiempo de captura'
Assert-Contains -Text $deviceGateway -Pattern 'Geolocator\.(isLocationServiceEnabled|checkPermission|requestPermission)' -Description 'verificacion de servicio y permiso'
Assert-NotContains -Text $deviceGateway -Pattern 'getPositionStream|ForegroundNotificationConfig|AndroidSettings|workmanager|Timer\(' -Description 'rastreo continuo o ejecucion diferida'

$locationPage = Get-Utf8Text 'mobile\lib\features\location\presentation\foreground_location_page.dart'
foreach ($marker in @('location-share', 'location-consent-confirm', 'location-clear-confirm', 'location-revoke-confirm', 'changeLocationConsent', 'upsertCurrentLocation', 'clearCurrentLocation')) {
    Assert-Contains -Text $locationPage -Pattern $marker -Description "accion visible y controlada: $marker"
}
Assert-Contains -Text $locationPage -Pattern 'No se realiza seguimiento continuo' -Description 'aviso transparente de alcance'
Assert-NotContains -Text $locationPage -Pattern 'latitude|longitude|accuracyM|getPositionStream|workmanager|background\s*(service|task|fetch)' -Description 'coordenadas visibles o seguimiento continuo'

$driverRepository = Get-Utf8Text 'mobile\lib\features\profile\data\mobile_driver_repository.dart'
Assert-Contains -Text $driverRepository -Pattern "'mobile/location/consent'" -Description 'consentimiento propio por API'
Assert-Contains -Text $driverRepository -Pattern "'mobile/location/current'" -Description 'ubicacion vigente propia por API'
Assert-NotContains -Text $driverRepository -Pattern 'organizationId|tenantId|userId|driverId' -Description 'identidad ajena aportada por cliente'

$backendService = Get-Utf8Text 'backend\src\main\java\pe\rutafija\operation\application\MobileDriverService.java'
foreach ($guard in @('LOCATION_CONSENT_REQUIRED', 'LOCATION_PERMISSION_NOT_REPORTED', 'LOCATION_STATE_NOT_ALLOWED', 'currentLocationStore.deleteByDriverId', 'allowsCurrentLocation')) {
    Assert-Contains -Text $backendService -Pattern $guard -Description "proteccion existente del servidor: $guard"
}

$locationTests = Get-Utf8Text 'mobile\test\features\location\foreground_location_page_test.dart'
foreach ($scenario in @('requests foreground permission in context', 'does not grant consent or send a point', 'lets the conductor clear a point or revoke consent')) {
    Assert-Contains -Text $locationTests -Pattern $scenario -Description "prueba de privacidad M7: $scenario"
}

Write-Output 'M7_FOREGROUND_LOCATION_AUDIT=PASS m7=8/80 total=66/80 permission=foreground_only current_point=single privacy=no_history regression=M1-M6'
