[CmdletBinding()]
param([switch]$Publish)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$taskBackend=Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
$taskClass=Join-Path $taskBackend 'target/test-classes/pe/rutafija/support/ApprovedAdminTermsPublication.class'
if (-not (Test-Path -LiteralPath $taskClass)) { throw 'Preparar las clases primero con Invoke-RutaFijaL01OtpManual.ps1 -Prepare.' }
$taskDependencies=[IO.File]::ReadAllText((Join-Path $taskBackend 'target/manual-otp-classpath.txt')).Trim()
$taskClasspath=@((Join-Path $taskBackend 'target/test-classes'),(Join-Path $taskBackend 'target/classes'),$taskDependencies) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $taskBackend
try {
    $taskMode=if ($Publish) { '--publish' } else { '--check' }
    & java -cp $taskClasspath pe.rutafija.support.ApprovedAdminTermsPublication $taskMode
    if ($LASTEXITCODE -ne 0) { throw 'Documento no registrado o correspondencia rechazada; no sobrescribir ni registrar aceptaciones.' }
}
finally { Pop-Location }
