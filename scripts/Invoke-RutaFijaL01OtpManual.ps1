[CmdletBinding(DefaultParameterSetName = 'Check')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Prepare')]
    [switch]$Prepare,
    [Parameter(Mandatory, ParameterSetName = 'Execute')]
    [switch]$Execute,
    [Parameter(ParameterSetName = 'Execute')]
    [Guid]$ResumeRunId,
    [Parameter(Mandatory, ParameterSetName = 'Inspect')]
    [Guid]$InspectRunId,
    [Parameter(Mandatory, ParameterSetName = 'Input')]
    [switch]$InputSelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$taskRepoRoot = Split-Path -Parent $PSScriptRoot
$taskBackend = Join-Path $taskRepoRoot 'backend'
$taskClasspathFile = Join-Path $taskBackend 'target\manual-otp-classpath.txt'
$taskMainClass = Join-Path $taskBackend 'target\test-classes\pe\rutafija\support\ManualOtpDeliveryValidation.class'

if (($Execute -or $InputSelfTest) -and ([Console]::IsInputRedirected -or [Console]::IsOutputRedirected)) {
    throw 'La entrada oculta requiere una consola interactiva local, sin redireccionar entrada ni salida.'
}
if (($PSBoundParameters.ContainsKey('ResumeRunId') -and $ResumeRunId -eq [Guid]::Empty) -or
    ($PSBoundParameters.ContainsKey('InspectRunId') -and $InspectRunId -eq [Guid]::Empty)) {
    throw 'Se requiere el RUN_ID exacto y no vacío del caso; no se buscan ni limpian casos generales.'
}

Push-Location -LiteralPath $taskBackend
try {
    if ($Prepare) {
        Write-Output 'Preparando clases y classpath. Este paso no inicia Spring, pruebas, PostgreSQL ni SMTP.'
        # No verify, integration-test, Surefire execution or automatic delivery.
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd --batch-mode --no-transfer-progress test-compile dependency:build-classpath '-DskipTests' '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/manual-otp-classpath.txt'
        $taskPrepareExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($taskPrepareExit -ne 0) { throw 'No se completó la preparación del validador manual.' }
        Write-Output 'MANUAL_OTP_PREPARED=true; SMTP_ATTEMPTS=0; DB_CONNECTED=false'
        return
    }

    if (-not (Test-Path -LiteralPath $taskMainClass -PathType Leaf) -or
        -not (Test-Path -LiteralPath $taskClasspathFile -PathType Leaf)) {
        throw 'Faltan artefactos compilados. Ejecuta primero este script con -Prepare; ese paso no envía correo.'
    }
    $taskDependencies = [IO.File]::ReadAllText($taskClasspathFile).Trim()
    if ([string]::IsNullOrWhiteSpace($taskDependencies)) {
        throw 'El classpath está vacío. Vuelve a preparar los artefactos locales.'
    }
    $taskClasspath = @(
        (Join-Path $taskBackend 'target\test-classes'),
        (Join-Path $taskBackend 'target\classes'),
        $taskDependencies
    ) -join [IO.Path]::PathSeparator

    if ($Execute) {
        Write-Output 'Modo manual: se comprobará exclusivamente ruta_fija_test antes de pedir la confirmación del único envío.'
        Write-Output 'No aparecerán caracteres ni asteriscos al escribir el OTP. No lo compartas ni captures la entrada.'
        if ($PSBoundParameters.ContainsKey('ResumeRunId')) {
            Write-Output 'Continuación selectiva: mismo caso/identidad/ADMIN; conserva ledger y límites, sin otra creación ADMIN.'
            & java -cp $taskClasspath pe.rutafija.support.ManualOtpDeliveryValidation --resume-case $ResumeRunId.ToString()
        }
        else {
            Write-Output 'Un caso nuevo consume una creación ADMIN histórica. Si ya existe un caso fallido, inspeccionarlo primero.'
            & java -cp $taskClasspath pe.rutafija.support.ManualOtpDeliveryValidation
        }
    }
    elseif ($PSBoundParameters.ContainsKey('InspectRunId')) {
        Write-Output 'Inspección de solo lectura del caso exacto en ruta_fija_test; sin Spring, migraciones, HTTP ni SMTP.'
        & java -cp $taskClasspath pe.rutafija.support.ManualOtpDeliveryValidation --inspect-case $InspectRunId.ToString()
    }
    elseif ($InputSelfTest) {
        Write-Output 'Solo datos ficticios: captura oculta local. No lee configuración privada ni conecta a DB/SMTP/HTTP.'
        & java -cp $taskClasspath pe.rutafija.support.ManualOtpDeliveryValidation --input-self-test
    }
    else {
        # Default is a static guard check. It cannot start the backend or send mail.
        & java -cp $taskClasspath pe.rutafija.support.ManualOtpDeliveryValidation --check-only
    }
    $taskJavaExit = $LASTEXITCODE
    if ($taskJavaExit -ne 0) { throw "El validador terminó con código $taskJavaExit. No reintenta el envío." }
}
finally {
    Pop-Location
}
