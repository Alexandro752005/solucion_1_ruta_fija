param(
    [ValidateSet('Plan', 'Validate', 'RestorePrevious', 'RestoreDelivered')]
    [string]$Action = 'Plan',
    [string]$ArchivePath = ''
)

# Reversion funcional del main ADMIN. No migra ni restaura bases de datos.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $ArchivePath) {
    $ArchivePath = Join-Path $repoRoot 'artifacts/dashboard-admin-entrega-final-2026-10-26/ruta-fija-dashboard-admin-20261026.zip'
}
$ArchivePath = [IO.Path]::GetFullPath($ArchivePath)
$manifestPath = Join-Path (Split-Path $ArchivePath -Parent) 'manifest-SHA256.json'
$expectedPaths = @(
    'frontend/src/app/features/dashboard/dashboard.page.ts',
    'frontend/src/app/features/dashboard/dashboard.page.html',
    'frontend/src/app/features/dashboard/dashboard.page.spec.ts'
)

function Get-BytesHash([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Get-ScopedPath([string]$RelativePath) {
    $full = [IO.Path]::GetFullPath((Join-Path $repoRoot $RelativePath))
    if (-not $full.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'DASHBOARD_RESTORE_PATH_OUTSIDE_PROJECT'
    }
    # Do not follow a redirected source directory when writing.
    $ancestor = Split-Path $full -Parent
    while ($ancestor -and $ancestor -ne $repoRoot) {
        if (Test-Path -LiteralPath $ancestor) {
            if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'DASHBOARD_RESTORE_REPARSE_POINT_REJECTED'
            }
        }
        $ancestor = Split-Path $ancestor -Parent
    }
    if ((Test-Path -LiteralPath $full) -and ((Get-Item -LiteralPath $full -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'DASHBOARD_RESTORE_REPARSE_POINT_REJECTED'
    }
    return $full
}

if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw 'DASHBOARD_DELIVERY_ARCHIVE_OR_MANIFEST_MISSING'
}
$external = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($external.archive.file -ne [IO.Path]::GetFileName($ArchivePath) -or
    [long]$external.archive.bytes -ne (Get-Item -LiteralPath $ArchivePath).Length -or
    $external.archive.sha256 -ne (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()) {
    throw 'DASHBOARD_DELIVERY_ARCHIVE_HASH_MISMATCH'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
try {
    function Read-EntryBytes([string]$Name) {
        $entry = $zip.GetEntry($Name)
        if ($null -eq $entry) { throw "DASHBOARD_DELIVERY_ENTRY_MISSING: $Name" }
        $stream = $entry.Open()
        $buffer = New-Object IO.MemoryStream
        try { $stream.CopyTo($buffer); return ,$buffer.ToArray() }
        finally { $stream.Dispose(); $buffer.Dispose() }
    }
    $names = @($zip.Entries | ForEach-Object { $_.FullName })
    if (($names | Sort-Object -Unique).Count -ne $names.Count) { throw 'DASHBOARD_DELIVERY_DUPLICATE_ENTRY' }
    foreach ($name in $names) {
        if ($name -match '(^/|\\|(^|/)\.\.(/|$)|:)') { throw 'DASHBOARD_DELIVERY_UNSAFE_ENTRY' }
    }
    $inventory = [Text.Encoding]::UTF8.GetString((Read-EntryBytes 'delivery-manifest.json')) | ConvertFrom-Json
    if (@($inventory.files).Count -ne $names.Count - 1) { throw 'DASHBOARD_DELIVERY_INVENTORY_MISMATCH' }
    foreach ($file in $inventory.files) {
        $entry = $zip.GetEntry([string]$file.path)
        if ($null -eq $entry -or $entry.Length -ne [long]$file.bytes -or
            (Get-BytesHash (Read-EntryBytes $file.path)) -ne $file.sha256) {
            throw "DASHBOARD_DELIVERY_ENTRY_HASH_MISMATCH: $($file.path)"
        }
    }
    $rollback = [Text.Encoding]::UTF8.GetString((Read-EntryBytes 'rollback/manifest.json')) | ConvertFrom-Json
    if (@($rollback.files).Count -ne 3 -or
        @(Compare-Object $expectedPaths @($rollback.files | ForEach-Object { $_.path })).Count -ne 0) {
        throw 'DASHBOARD_RESTORE_WHITELIST_MISMATCH'
    }
    $states = @()
    $replacement = @{}
    foreach ($file in $rollback.files) {
        $full = Get-ScopedPath $file.path
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { throw 'DASHBOARD_RESTORE_SOURCE_MISSING' }
        $previous = Read-EntryBytes ('rollback/previous/' + $file.path)
        $delivered = Read-EntryBytes ('source/' + $file.path)
        if ((Get-BytesHash $previous) -ne $file.previousSha256 -or
            (Get-BytesHash $delivered) -ne $file.deliveredSha256) { throw 'DASHBOARD_RESTORE_PAYLOAD_MISMATCH' }
        $actual = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
        $state = if ($actual -eq $file.deliveredSha256) { 'delivered' }
                 elseif ($actual -eq $file.previousSha256) { 'previous' }
                 else { throw "DASHBOARD_SOURCE_CHANGED_SINCE_DELIVERY: $($file.path)" }
        $states += [pscustomobject]@{ path = $file.path; state = $state }
        $replacement[$file.path] = if ($Action -eq 'RestorePrevious') { $previous } else { $delivered }
    }
    foreach ($file in $rollback.compatibilityFiles) {
        $full = Get-ScopedPath $file.path
        if (-not (Test-Path -LiteralPath $full -PathType Leaf) -or
            (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256) {
            throw "DASHBOARD_COMPATIBILITY_SOURCE_CHANGED: $($file.path)"
        }
    }

    if ($Action -in @('Plan', 'Validate')) {
        [pscustomobject]@{
            result = 'PASS_DELIVERY_HASHES_AND_RESTORE_PLAN'; action = $Action
            writes = 0; sourceFiles = $states; archive = [IO.Path]::GetFileName($ArchivePath)
            scope = 'ADMIN main only; additive API and database retained'
        } | ConvertTo-Json -Depth 5
        return
    }

    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -in @(8080, 4300) })
    if ($listeners.Count -gt 0 -or (Test-Path -LiteralPath (Join-Path $repoRoot '.runtime/ruta-fija-native-runtime.json'))) {
        throw 'DASHBOARD_RESTORE_REQUIRES_STOPPED_RUNTIME: use finalizar_ruta_fija.bat first'
    }
    $backupDirectory = Join-Path $repoRoot ('backend/.local/accesos/dashboard-admin-restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($backupDirectory)
    $original = @{}
    foreach ($file in $rollback.files) {
        $full = Get-ScopedPath $file.path
        $original[$file.path] = [IO.File]::ReadAllBytes($full)
        [IO.File]::WriteAllBytes((Join-Path $backupDirectory ([IO.Path]::GetFileName($full))), $original[$file.path])
    }
    $written = @()
    try {
        foreach ($file in $rollback.files) {
            $full = Get-ScopedPath $file.path
            # Recheck immediately before the write to avoid overwriting a new edit.
            if ((Get-BytesHash ([IO.File]::ReadAllBytes($full))) -ne (Get-BytesHash $original[$file.path])) {
                throw 'DASHBOARD_SOURCE_CHANGED_DURING_RESTORE'
            }
            $written += $file.path
            [IO.File]::WriteAllBytes($full, $replacement[$file.path])
            if ((Get-BytesHash ([IO.File]::ReadAllBytes($full))) -ne (Get-BytesHash $replacement[$file.path])) {
                throw 'DASHBOARD_RESTORE_WRITE_VERIFICATION_FAILED'
            }
        }
    } catch {
        foreach ($name in $written) { [IO.File]::WriteAllBytes((Get-ScopedPath $name), $original[$name]) }
        throw
    }
    [pscustomobject]@{
        result = 'PASS_DASHBOARD_SOURCE_RESTORED'; action = $Action; files = $written
        backupDirectory = $backupDirectory; databaseChanged = $false
        next = 'Run frontend typecheck and build; then iniciar_ruta_fija.bat'
    } | ConvertTo-Json -Depth 4
} finally { $zip.Dispose() }
