[CmdletBinding()]
param(
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Parent $PSScriptRoot
}

function Get-NonBlankLineCount {
    param([System.IO.FileInfo[]]$Files)

    if ($null -eq $Files -or $Files.Count -eq 0) {
        return 0
    }

    $lineCounts = foreach ($file in $Files) {
        (Get-Content -LiteralPath $file.FullName | Where-Object { $_.Trim().Length -gt 0 }).Count
    }
    return ($lineCounts | Measure-Object -Sum).Sum
}

function Get-SourceFiles {
    param(
        [string]$Path,
        [string]$Filter
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return @()
    }

    return @(Get-ChildItem -LiteralPath $Path -Recurse -File -Filter $Filter |
        Where-Object {
            $_.FullName -notmatch '\\(node_modules|dist|build|target|coverage|\.angular|generated)\\'
        })
}

$javaFiles = @(Get-SourceFiles -Path (Join-Path $ProjectRoot 'backend/src/main/java') -Filter '*.java')
$typeScriptFiles = @(Get-SourceFiles -Path (Join-Path $ProjectRoot 'frontend/src') -Filter '*.ts' |
    Where-Object { $_.Name -notlike '*.spec.ts' })
$dartFiles = @(Get-SourceFiles -Path $ProjectRoot -Filter '*.dart')

$javaLines = Get-NonBlankLineCount -Files $javaFiles
$typeScriptLines = Get-NonBlankLineCount -Files $typeScriptFiles
$dartLines = Get-NonBlankLineCount -Files $dartFiles
$totalLines = $javaLines + $typeScriptLines + $dartLines
$javaPercentage = if ($totalLines -eq 0) { 0 } else { [Math]::Round((100 * $javaLines / $totalLines), 2) }

[pscustomobject]@{
    JavaFiles = $javaFiles.Count
    JavaNonBlankLines = $javaLines
    TypeScriptFiles = $typeScriptFiles.Count
    TypeScriptNonBlankLines = $typeScriptLines
    DartFiles = $dartFiles.Count
    DartNonBlankLines = $dartLines
    TotalNonBlankLines = $totalLines
    JavaPercentage = $javaPercentage
} | Format-List
