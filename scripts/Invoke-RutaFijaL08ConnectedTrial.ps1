[CmdletBinding()]
param([switch]$Serve,[Guid]$ResumeRunId)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
if(-not $Serve){Write-Output 'L08_CONNECTED_PLAN=NO_FILES_NO_DATABASE_NO_ACCOUNTS';Write-Output 'AUTHORIZED_RUN_ID=a999ef52-d87f-498d-9251-1581cbb72562';Write-Output 'MAX_NEW_CONDUCTOR=1_HISTORICAL_6_TO_7';return}
if([Console]::IsInputRedirected -or [Console]::IsOutputRedirected){throw 'Terminal interactiva privada requerida, sin grabación ni redirección.'}
if($PSBoundParameters.ContainsKey('ResumeRunId') -and $ResumeRunId.ToString() -ne 'a999ef52-d87f-498d-9251-1581cbb72562'){throw 'Solo se admite el mismo RUN autorizado.'}
$backend=Join-Path $repoRoot 'backend'
if(-not (Test-Path -LiteralPath (Join-Path $backend 'target/test-classes/pe/rutafija/support/L08ConnectedTrial.class'))){throw 'Arnés no compilado: conservar el caso y solicitar diagnóstico.'}
$deps=[IO.File]::ReadAllText((Join-Path $backend 'target/manual-otp-classpath.txt')).Trim()
$cp=(Join-Path $backend 'target/test-classes')+';'+(Join-Path $backend 'target/classes')+';'+$deps
Push-Location -LiteralPath $backend
try{
 if($PSBoundParameters.ContainsKey('ResumeRunId')){& java -Xms64m -Xmx384m -cp $cp pe.rutafija.support.L08ConnectedTrial --serve --resume-case $ResumeRunId.ToString()}
 else{& java -Xms64m -Xmx384m -cp $cp pe.rutafija.support.L08ConnectedTrial --serve}
 if($LASTEXITCODE -ne 0){throw 'Caso pendiente. No repetir alta, borrar el marcador ni reiniciar contadores: comunicar solo PHASE y CHECK saneados.'}
}finally{Pop-Location}
