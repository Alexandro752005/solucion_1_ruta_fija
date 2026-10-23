# Run from the repository root after reviewing the staged diff.
# This file is a prepared command; generating it does not execute a commit.
$ErrorActionPreference = 'Stop'
$expectedRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$actualRepo = git rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($actualRepo) -ne $expectedRepo) {
    throw 'Run this prepared command from the intended Ruta Fija repository root.'
}
$commitSubject = 'release(core): cierre funcional candidato local de entrega v3.1 (L-00 a L-08 verificado)'
$commitBody = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'commit-body.txt')
git add .
if ($LASTEXITCODE -ne 0) { throw 'Staging failed; commit was not attempted.' }
git commit -m $commitSubject -m $commitBody
exit $LASTEXITCODE
