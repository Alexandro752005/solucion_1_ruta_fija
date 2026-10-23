[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskFrontend = Join-Path $taskProjectRoot 'frontend'
$taskBackend = Join-Path $taskProjectRoot 'backend'
$taskPreviousConnected = $env:RF_L01_CONNECTED_CLIENTS

# Explicit opt-in: actual browser + Flutter UI + real test backend/database.
# No real mail, actual-owner keys, new dependencies, deployment or non-test migration.
try {
    Push-Location -LiteralPath $taskFrontend
    try {
        $ErrorActionPreference = 'Continue'
        & npm.cmd run build
        $taskBuildExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($taskBuildExit -ne 0) { throw 'No se completó el build del CRM para el recorrido conectado.' }
    } finally { Pop-Location }
    . (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -Scope Test
    $env:RF_L01_CONNECTED_CLIENTS = 'true'
    Push-Location -LiteralPath $taskBackend
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd --batch-mode --no-transfer-progress verify '-Dit.test=ConnectedL01ClientsIT,SuperAdminRecoveryBoundariesIT,SuperAdminRecoveryCliBackendIT,ManualOtpValidationHarnessIT' '-Djacoco.destFile=target/jacoco-l01-connected.exec' '-Djacoco.dataFile=target/jacoco-l01-connected.exec'
        $taskVerifyExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($taskVerifyExit -ne 0) { throw 'Falló una comprobación dirigida. Revisar evidencia saneada sin reintentar SMTP.' }
    } finally { Pop-Location }
} finally { $env:RF_L01_CONNECTED_CLIENTS = $taskPreviousConnected }
