[CmdletBinding()]
param(
    [string]$FlutterRoot,
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$mobileRoot = Join-Path $repoRoot 'mobile'
$m8AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaM8OfflineMinimum.ps1'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Get-Utf8Text {
    param([Parameter(Mandatory)][string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "M9 no encontro el archivo requerido: $RelativePath"
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
        throw "M9 no satisface: $Description"
    }
}

function Assert-NotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )

    if ($Text -match $Pattern) {
        throw "M9 incorpora fuera de alcance: $Description"
    }
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
    throw 'M9 requiere Flutter estable en D:\dev\flutter o mediante -FlutterRoot.'
}

function Resolve-ApkSigner {
    $sdkCandidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        'D:\Android\Sdk'
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) } |
        Select-Object -Unique

    foreach ($sdkRoot in $sdkCandidates) {
        $candidate = Join-Path $sdkRoot 'build-tools\36.0.0\apksigner.bat'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw 'M9 requiere Android Build-Tools 36.0.0 y apksigner.bat.'
}

if (-not (Test-Path -LiteralPath $m8AuditScript -PathType Leaf) -or
    -not (Test-Path -LiteralPath $mobileRoot -PathType Container)) {
    throw 'M9 no encontro la linea base M8 o el proyecto Flutter.'
}

& $m8AuditScript -FlutterRoot $FlutterRoot -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'M9 requiere M8 aprobado como linea base tecnica.'
}

$pubspec = Get-Utf8Text 'mobile\pubspec.yaml'
Assert-Contains -Text $pubspec -Pattern '(?m)^version:\s*0\.1\.0\+2\s*$' -Description 'version identificable de APK M9'
Assert-NotContains -Text $pubspec -Pattern '(?m)^\s*(firebase_|workmanager|connectivity_plus|image_picker|camera|sqflite|hive)\w*\s*:' -Description 'dependencia diferida fuera de M9'

$loginPage = Get-Utf8Text 'mobile\lib\features\auth\presentation\mobile_login_page.dart'
Assert-Contains -Text $loginPage -Pattern 'header:\s*true' -Description 'encabezado semantico de acceso'
Assert-Contains -Text $loginPage -Pattern 'Error de acceso' -Description 'mensaje accesible de error de acceso'
Assert-Contains -Text $loginPage -Pattern 'liveRegion:\s*true' -Description 'anuncio de error al lector de pantalla'

$shell = Get-Utf8Text 'mobile\lib\features\bootstrap\presentation\conductor_navigation_shell.dart'
Assert-Contains -Text $shell -Pattern '_pendingSyncLabel' -Description 'etiqueta accesible del contador offline'
Assert-Contains -Text $shell -Pattern 'No hay acciones pendientes' -Description 'estado cero explicito para pendientes'
Assert-Contains -Text $shell -Pattern 'ExcludeSemantics' -Description 'contador sin anuncio duplicado'

$pendingSync = Get-Utf8Text 'mobile\lib\features\sync\presentation\pending_sync_page.dart'
Assert-Contains -Text $pendingSync -Pattern 'Estado de sincronizaci\u00f3n: no hay acciones pendientes' -Description 'estado accesible de cola vacia'
Assert-Contains -Text $pendingSync -Pattern 'Primera acci\u00f3n de sincronizaci\u00f3n' -Description 'resumen accesible de primer pendiente'
Assert-Contains -Text $pendingSync -Pattern 'liveRegion:\s*true' -Description 'aviso accesible de cola vacia'

$app = Get-Utf8Text 'mobile\lib\app\ruta_fija_app.dart'
Assert-Contains -Text $app -Pattern "label: 'Verificando sesi\u00f3n protegida'" -Description 'estado accesible de restauracion de sesion'

$appTests = Get-Utf8Text 'mobile\test\app\ruta_fija_app_test.dart'
foreach ($scenario in @(
    'keeps login controls reachable with 140 percent text scaling',
    'announces a login error through a live semantic region',
    'announces the pending-sync state to assistive technology'
)) {
    Assert-Contains -Text $appTests -Pattern ([regex]::Escape($scenario)) -Description "prueba critica M9: $scenario"
}
Assert-Contains -Text $appTests -Pattern 'flagsCollection\s*\.\s*isLiveRegion' -Description 'verificacion de region viva en prueba'

$androidManifest = Get-Utf8Text 'mobile\android\app\src\main\AndroidManifest.xml'
Assert-Contains -Text $androidManifest -Pattern 'android:allowBackup="false"' -Description 'backup Android deshabilitado'
Assert-NotContains -Text $androidManifest -Pattern 'ACCESS_BACKGROUND_LOCATION|FOREGROUND_SERVICE_LOCATION|CAMERA|POST_NOTIFICATIONS|usesCleartextTraffic\s*=\s*"true"' -Description 'permiso o transporte fuera del alcance final'

$flutter = Resolve-FlutterCommand -RequestedRoot $FlutterRoot
Push-Location $mobileRoot
try {
    & $flutter analyze
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter analyze no aprobo M9.'
    }
    & $flutter test
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter test no aprobo M9.'
    }
    & $flutter build apk --debug
    if ($LASTEXITCODE -ne 0) {
        throw 'flutter build apk --debug no aprobo M9.'
    }
}
finally {
    Pop-Location
}

$apkPath = Join-Path $mobileRoot 'build\app\outputs\flutter-apk\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf) -or
    (Get-Item -LiteralPath $apkPath).Length -le 0) {
    throw 'M9 no produjo una APK debug valida.'
}

$apksigner = Resolve-ApkSigner
$signatureOutput = @(& $apksigner verify --verbose --print-certs $apkPath 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'M9 no pudo verificar la firma de la APK debug.'
}
$signatureText = $signatureOutput -join "`n"
Assert-Contains -Text $signatureText -Pattern '(?m)^Verifies\s*$' -Description 'APK verificable'
Assert-Contains -Text $signatureText -Pattern 'Verified using v2 scheme \(APK Signature Scheme v2\): true' -Description 'firma v2 valida'

$apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$gitHeadOutput = @(& git -C $repoRoot rev-parse --verify HEAD 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'M9 no pudo identificar el commit Git de referencia.'
}
$gitHead = ($gitHeadOutput -join '').Trim()
$gitStatus = @(& git -C $repoRoot status --porcelain 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw 'M9 no pudo caracterizar el estado del repositorio.'
}
$worktreeState = if (@($gitStatus | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -eq 0) {
    'CLEAN'
}
else {
    'DIRTY'
}
$testCaseCount = @(
    Get-ChildItem -LiteralPath (Join-Path $mobileRoot 'test') -Recurse -File -Filter '*.dart' |
        Select-String -Pattern '^\s*test(?:Widgets)?\('
).Count

Write-Output "M9_QUALITY_EVIDENCE_AUDIT=PASS m9=9/80 total=80/80 accessibility=PASS android=PASS apk_v2=PASS apk_sha256=$apkHash git_head=$gitHead git_worktree=$worktreeState flutter_test_cases=$testCaseCount"
