[CmdletBinding()]
param(
    [Parameter(Mandatory)][Guid]$CaseRunId,
    [Parameter(Mandatory)][Guid]$RunId,
    [Parameter(Mandatory)][string]$ObservedConsumedAtUtc
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if ($CaseRunId -eq [Guid]::Empty -or $RunId -eq [Guid]::Empty) { throw 'Se necesitan los dos identificadores exactos.' }
$taskBackend=Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
$taskClass=Join-Path $taskBackend 'target/test-classes/pe/rutafija/support/ManualOtpEvidenceVerification.class'
$taskDependencies=Join-Path $taskBackend 'target/manual-otp-classpath.txt'
if (-not (Test-Path -LiteralPath $taskClass) -or -not (Test-Path -LiteralPath $taskDependencies)) {
    throw 'Preparar primero Invoke-RutaFijaL01OtpManual.ps1 -Prepare; no inicia DB ni SMTP.'
}
$taskClasspath=@((Join-Path $taskBackend 'target/test-classes'),(Join-Path $taskBackend 'target/classes'),
    [IO.File]::ReadAllText($taskDependencies).Trim()) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $taskBackend
try {
    # No delivery or cleanup mode exists in this verifier.
    & java -cp $taskClasspath pe.rutafija.support.ManualOtpEvidenceVerification $CaseRunId.ToString() $RunId.ToString() $ObservedConsumedAtUtc
    if ($LASTEXITCODE -ne 0) { throw 'No se acredita la correspondencia. Revisar solo el resultado saneado.' }
}
finally { Pop-Location }
