[CmdletBinding()]
param(
    [string]$SnapshotDirectory = 'backups/L-00-2026-10-13-final3'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$snapshotRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $SnapshotDirectory))
$rootPrefix = $projectRoot + '\'
if (-not $snapshotRoot.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'La carpeta del snapshot debe permanecer dentro de la raíz del proyecto.'
}
if (Test-Path -LiteralPath $snapshotRoot) {
    throw "La carpeta de snapshot ya existe y no se sobrescribirá: $snapshotRoot"
}

$manifestPath = Join-Path $projectRoot 'docs/evidencia/L-00/source-manifest.sha256'
$baselineEvidencePath = Join-Path $projectRoot 'docs/evidencia/L-00/baseline.md'
$executionMapPath = Join-Path $projectRoot 'docs/repo-map.json'
$taskBoardPath = Join-Path $projectRoot 'docs/seguimiento/tareas.json'
$excludedDirectories = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@('.git', 'node_modules', 'target', 'build', 'dist', '.dart_tool', '.gradle', '.angular', 'coverage', '.idea', '.vscode', 'backups', '.cache', 'Pods', 'ephemeral', 'memory') | ForEach-Object { [void]$excludedDirectories.Add($_) }
$excludedFileNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@('local.properties', 'key.properties', '.npmrc', 'gradle.properties', 'settings-security.xml', 'credentials.xml', 'credentials.json', 'service-account.json') | ForEach-Object { [void]$excludedFileNames.Add($_) }
$excludedExtensions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@('.apk', '.aab', '.class', '.log', '.db', '.sqlite', '.sqlite3', '.pem', '.p12', '.pfx', '.jks', '.keystore', '.key') | ForEach-Object { [void]$excludedExtensions.Add($_) }

$snapshotFiles = [System.Collections.Generic.List[object]]::new()
function Add-SnapshotFiles([string]$directory) {
    foreach ($entry in Get-ChildItem -LiteralPath $directory -Force) {
        if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { continue }
        if ($entry.PSIsContainer) {
            if (-not $excludedDirectories.Contains($entry.Name)) { Add-SnapshotFiles $entry.FullName }
            continue
        }

        $name = $entry.Name
        $extension = $entry.Extension
        $fullPath = [System.IO.Path]::GetFullPath($entry.FullName)
        if ($excludedFileNames.Contains($name) -or $excludedExtensions.Contains($extension)) { continue }
        if ($name.StartsWith('.env', [System.StringComparison]::OrdinalIgnoreCase) -or $name.EndsWith('.env', [System.StringComparison]::OrdinalIgnoreCase)) { continue }
        if ($name -match '^application-(local|secret)\.') { continue }
        if ([string]::Equals($fullPath, $manifestPath, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
        if ([string]::Equals($fullPath, $baselineEvidencePath, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
        if ([string]::Equals($fullPath, $executionMapPath, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
        if ([string]::Equals($fullPath, $taskBoardPath, [System.StringComparison]::OrdinalIgnoreCase)) { continue }

        $relativePath = $fullPath.Substring($rootPrefix.Length).Replace('\', '/')
        $snapshotFiles.Add([pscustomobject]@{ FullName = $fullPath; RelativePath = $relativePath })
    }
}

Add-SnapshotFiles $projectRoot
if ($snapshotFiles.Count -eq 0) { throw 'No se encontraron archivos para incluir en el snapshot.' }
$snapshotFiles = @($snapshotFiles | Sort-Object -Property RelativePath)

New-Item -ItemType Directory -Path $snapshotRoot | Out-Null
$archivePath = Join-Path $snapshotRoot 'source-snapshot.zip'
$manifestRecords = [System.Collections.Generic.List[string]]::new()
$expectedHashes = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
foreach ($file in $snapshotFiles) {
    $sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    $manifestRecords.Add("$sha256  $($file.RelativePath)")
    $expectedHashes.Add($file.RelativePath, $sha256)
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($archivePath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $snapshotFiles) {
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.FullName,
            $file.RelativePath,
            [System.IO.Compression.CompressionLevel]::Optimal
        )
    }
}
finally {
    $archive.Dispose()
}

$manifestHeader = @(
    '# Ruta Fija APF3 L-00 source snapshot',
    '# Captured: 2026-10-13 America/Lima',
    '# Scope: source/methodology files excluding generated dependencies/builds, local secret/config files, memory/, backups/, repo-map/task-board state, and L-00 baseline/manifest evidence.',
    '# Each entry is SHA-256 followed by two spaces and a project-relative path.',
    '# This is a source snapshot, not a database backup or a production release.'
)
[System.IO.File]::WriteAllLines($manifestPath, ($manifestHeader + $manifestRecords.ToArray()), [System.Text.UTF8Encoding]::new($false))

$restoreRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('ruta-fija-l00-restore-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $restoreRoot | Out-Null
$restorePassed = $false
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($archivePath, $restoreRoot)
    foreach ($file in $snapshotFiles) {
        $restoredPath = Join-Path $restoreRoot ($file.RelativePath.Replace('/', '\'))
        if (-not (Test-Path -LiteralPath $restoredPath -PathType Leaf)) { throw "Falta archivo restaurado: $($file.RelativePath)" }
        $actualHash = (Get-FileHash -LiteralPath $restoredPath -Algorithm SHA256).Hash
        $expectedHash = $expectedHashes[$file.RelativePath]
        if ($actualHash -ne $expectedHash) { throw "Hash distinto después de extraer: $($file.RelativePath)" }
    }
    $restoredCount = (Get-ChildItem -LiteralPath $restoreRoot -File -Recurse).Count
    if ($restoredCount -ne $snapshotFiles.Count) { throw "Cantidad restaurada ($restoredCount) distinta a la capturada ($($snapshotFiles.Count))." }
    $restorePassed = $true
}
finally {
    $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolvedRestoreRoot = [System.IO.Path]::GetFullPath($restoreRoot)
    if (-not $resolvedRestoreRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -or -not (Split-Path -Leaf $resolvedRestoreRoot).StartsWith('ruta-fija-l00-restore-', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Se rechaza la limpieza porque la ruta no es el temporal creado por este proceso: $resolvedRestoreRoot"
    }
    Remove-Item -LiteralPath $resolvedRestoreRoot -Recurse -Force
}

if (-not $restorePassed) { throw 'La verificación de extracción no terminó.' }
$archiveInfo = Get-Item -LiteralPath $archivePath
$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
[pscustomobject]@{
    Status = 'PASS'
    SourceFiles = $snapshotFiles.Count
    RestoredAndHashMatched = $snapshotFiles.Count
    Archive = $archivePath.Substring($projectRoot.Length + 1)
    ArchiveBytes = $archiveInfo.Length
    ArchiveSha256 = $archiveHash
    Manifest = $manifestPath.Substring($projectRoot.Length + 1)
    ManifestSha256 = $manifestHash
    RestoreCheck = 'PASS; extracted to a unique temporary directory, compared every SHA-256, then removed that temporary directory'
}
