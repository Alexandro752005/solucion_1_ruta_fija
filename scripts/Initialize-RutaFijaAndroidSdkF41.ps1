[CmdletBinding()]
param(
    [string]$AndroidSdkRoot = 'D:\Android\Sdk',
    [string]$FlutterRoot = 'D:\dev\flutter'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-RequiredPath {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Description,
        [switch]$Leaf
    )

    $pathType = if ($Leaf) { 'Leaf' } else { 'Container' }
    if (-not (Test-Path -LiteralPath $Path -PathType $pathType)) {
        throw "No se encontro $Description en: $Path"
    }
}

function Invoke-SdkManager {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & $script:sdkManager @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "sdkmanager no pudo completar: $($Arguments -join ' ')"
    }
}

$AndroidSdkRoot = [System.IO.Path]::GetFullPath($AndroidSdkRoot)
$FlutterRoot = [System.IO.Path]::GetFullPath($FlutterRoot)
$sdkManager = Join-Path $AndroidSdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$flutter = Join-Path $FlutterRoot 'bin\flutter.bat'

Assert-RequiredPath -Path $AndroidSdkRoot -Description 'la carpeta raiz del Android SDK'
Assert-RequiredPath -Path $sdkManager -Description 'sdkmanager.bat' -Leaf
Assert-RequiredPath -Path $flutter -Description 'flutter.bat' -Leaf

Write-Host ''
Write-Host 'F4.1 instalara solo platform-tools, Android API 36 y Build-Tools 36.0.0.'
Write-Host 'No instala Android Studio ni un emulador.'
Write-Host 'Se abriran las licencias oficiales de Android. Lealas y responda personalmente.'
Write-Host ''
$confirmation = Read-Host 'Escriba ACEPTO para continuar o cualquier otra cosa para cancelar'
if ($confirmation -cne 'ACEPTO') {
    throw 'Instalacion cancelada por el titular antes de modificar el Android SDK.'
}

[Environment]::SetEnvironmentVariable('ANDROID_HOME', $AndroidSdkRoot, 'User')
$env:ANDROID_HOME = $AndroidSdkRoot
$env:FLUTTER_ROOT = $FlutterRoot
$flutterBin = Join-Path $FlutterRoot 'bin'
if (($env:Path -split ';') -notcontains $flutterBin) {
    $env:Path = "$flutterBin;$env:Path"
}

Write-Host ''
Write-Host 'Revise cada licencia Android y responda solo si esta de acuerdo:'
Invoke-SdkManager -Arguments @("--sdk_root=$AndroidSdkRoot", '--licenses')

$packages = @(
    'platform-tools',
    'platforms;android-36',
    'build-tools;36.0.0'
)
Write-Host ''
Write-Host 'Instalando los componentes minimos de Android para Ruta Fija...'
Invoke-SdkManager -Arguments (@("--sdk_root=$AndroidSdkRoot") + $packages)

Assert-RequiredPath -Path (Join-Path $AndroidSdkRoot 'platform-tools\adb.exe') -Description 'Android platform-tools\adb.exe' -Leaf
Assert-RequiredPath -Path (Join-Path $AndroidSdkRoot 'platforms\android-36\android.jar') -Description 'Android API 36' -Leaf
Assert-RequiredPath -Path (Join-Path $AndroidSdkRoot 'build-tools\36.0.0\aapt.exe') -Description 'Android Build-Tools 36.0.0' -Leaf

& $flutter config --android-sdk $AndroidSdkRoot
if ($LASTEXITCODE -ne 0) {
    throw 'Flutter no pudo registrar la ruta del Android SDK.'
}

$doctorOutput = @(& $flutter doctor -v 2>&1)
$doctorText = $doctorOutput -join "`n"
$androidReady = $doctorText -match 'Android toolchain' -and $doctorText -match 'Android SDK at'
$androidLine = @($doctorOutput | Where-Object { $_ -match 'Android toolchain' } | Select-Object -First 1)[0]

$doctorOutput | ForEach-Object { Write-Host $_ }
if (-not $androidReady) {
    throw 'Los paquetes se instalaron, pero flutter doctor aun no aprueba Android. Revise el resultado anterior antes de continuar.'
}

Write-Output "F4_1_ANDROID_SETUP=PASS sdk='$AndroidSdkRoot' packages='platform-tools,android-36,build-tools-36.0.0' doctor='$androidLine'"
