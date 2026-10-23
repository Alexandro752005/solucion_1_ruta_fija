[CmdletBinding(DefaultParameterSetName = 'Inspect')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Prepare')][switch]$Prepare,
    [Parameter(Mandatory, ParameterSetName = 'Serve')][switch]$Serve,
    [Parameter(Mandatory, ParameterSetName = 'Smoke')][switch]$Smoke
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskBackend = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
if ($Serve -and ([Console]::IsInputRedirected -or [Console]::IsOutputRedirected)) {
    throw 'El ensayo exige terminal interactiva sin transcripción, grabación ni redirección.'
}
Push-Location -LiteralPath $taskBackend
try {
    if ($Prepare) {
        & .\mvnw.cmd --batch-mode --no-transfer-progress test-compile dependency:build-classpath '-DskipTests' '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/manual-otp-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Preparación no completada; no se inició el ensayo.' }
        return
    }
    $taskClass = Join-Path $taskBackend 'target/test-classes/pe/rutafija/support/ManualL01HumanTrialServer.class'
    if (-not (Test-Path -LiteralPath $taskClass)) { throw 'Ejecutar primero con -Prepare; no conecta a la base ni envía correo.' }
    $taskDeps = [IO.File]::ReadAllText((Join-Path $taskBackend 'target/manual-otp-classpath.txt')).Trim()
    $taskClasspath = @((Join-Path $taskBackend 'target/test-classes'), (Join-Path $taskBackend 'target/classes'), $taskDeps) -join [IO.Path]::PathSeparator
    $taskMode = if ($Serve) { '--serve' } elseif ($Smoke) { '--smoke' } else { '--inspect' }
    & java -cp $taskClasspath pe.rutafija.support.ManualL01HumanTrial $taskMode
    if ($LASTEXITCODE -ne 0) { throw 'Paso no completado. No borrar datos, reiniciar cupos o reenviar correo para desbloquear.' }
}
finally { Pop-Location }
