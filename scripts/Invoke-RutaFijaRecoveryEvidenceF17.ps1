[CmdletBinding()]
param(
    [string]$NativeConfigPath,
    [string]$BootstrapConfigPath,
    [string]$RecoveryDatabase,
    [string]$BackupDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}
if ([string]::IsNullOrWhiteSpace($RecoveryDatabase)) {
    $RecoveryDatabase = 'ruta_fija_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '_f17'
}
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot 'backups\f1-7'
}

$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$expectedDevelopmentUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'
$expectedTables = 'announcement,app_user,assignment,audit_event,driver,driver_vehicle_link,group_coordinator,incident,organization,refresh_token,transport_group,vehicle'

function Get-PgToolPath {
    param([Parameter(Mandatory)][string]$Name)

    $extension = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $candidates += Join-Path $env:ProgramFiles ('PostgreSQL\16\bin\' + $Name + $extension)
    }
    $commandWithExtension = Get-Command ($Name + $extension) -ErrorAction SilentlyContinue
    if ($null -ne $commandWithExtension) {
        $candidates += $commandWithExtension.Source
    }
    $commandWithoutExtension = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -ne $commandWithoutExtension) {
        $candidates += $commandWithoutExtension.Source
    }

    $tool = @($candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }) | Select-Object -First 1
    if (-not $tool) {
        throw "No se encontro $Name de PostgreSQL 16."
    }
    return $tool
}

function Test-SecretPathIsPrivate {
    param([Parameter(Mandatory)][string]$Path)

    $fullRepositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $fullSecretPath = [IO.Path]::GetFullPath($Path)
    $repositoryPrefix = $fullRepositoryPath + [IO.Path]::DirectorySeparatorChar
    if (-not $fullSecretPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El archivo privado debe permanecer dentro del repositorio local.'
    }

    $relativePath = $fullSecretPath.Substring($repositoryPrefix.Length).Replace('\', '/')
    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relativePath
        if ($LASTEXITCODE -ne 0) {
            throw "El archivo privado no esta ignorado por Git: $relativePath"
        }
        if (@(& git ls-files -- $relativePath).Count -gt 0) {
            throw "El archivo privado esta versionado: $relativePath"
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-PostgresQuery {
    param(
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][string]$Psql
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $arguments = @(
            '-X', '-w', '-v', 'ON_ERROR_STOP=1',
            '-h', '127.0.0.1', '-p', '5432',
            '-U', $Username, '-d', $Database,
            '-At', '-F', '|', '-c', $Query
        )
        $output = & $Psql @arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazo una comprobacion de recuperación F1.7.'
        }
        return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Get-SingleRow {
    param(
        [Parameter(Mandatory)][string[]]$Rows,
        [Parameter(Mandatory)][string]$Operation
    )

    if ($Rows.Count -ne 1) {
        throw "F1.7 no obtuvo una evidencia única para $Operation."
    }
    return $Rows[0].Trim()
}

function Invoke-ExternalTool {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Executable @Arguments 2>&1
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = @($output)
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Assert-BackupDirectory {
    param([Parameter(Mandatory)][string]$Path)

    $repositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $expectedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    if ((-not $resolvedPath.StartsWith($expectedRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) -and
        $resolvedPath -ne $expectedRoot) {
        throw 'F1.7 solo permite guardar dumps dentro de backups del repositorio.'
    }
    if (-not $resolvedPath.StartsWith($repositoryPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La ruta de backup no pertenece al repositorio actual.'
    }
    return $resolvedPath
}

function Assert-RuntimeStopped {
    $listeners = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in 8080, 4200 }
    )
    if ($listeners.Count -gt 0) {
        throw 'F1.7 exige API y CRM detenidos antes de capturar el backup.'
    }
}

$manifestSql = @'
SELECT current_setting('TimeZone'),
       current_setting('server_encoding'),
       (SELECT string_agg(tablename, ',' ORDER BY tablename)
          FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename <> 'flyway_schema_history'),
       (SELECT string_agg(
                   installed_rank::text || ':' || coalesce(version::text, '') || ':' ||
                   coalesce(checksum::text, '') || ':' || success::text,
                   ';' ORDER BY installed_rank)
          FROM flyway_schema_history),
       (SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist')),
       (SELECT string_agg(
                   conname || ':' ||
                   CASE
                       WHEN conrelid = 'public.assignment'::regclass
                        AND array_length(conexclop, 1) = 2
                        AND pg_get_constraintdef(oid) LIKE '%tstzrange(scheduled_at, scheduled_end_at%'
                        AND pg_get_constraintdef(oid) LIKE '%SCHEDULED%'
                        AND pg_get_constraintdef(oid) LIKE '%EN_SERVICIO%'
                       THEN 'valid'
                       ELSE 'invalid'
                   END,
                   ';' ORDER BY conname)
          FROM pg_constraint
         WHERE contype = 'x'
           AND conname IN (
               'ex_assignment_driver_schedule_no_overlap',
               'ex_assignment_vehicle_schedule_no_overlap'
           )),
       (SELECT string_agg(
                   tgname || ':' || pg_get_triggerdef(oid),
                   ';' ORDER BY tgname)
          FROM pg_trigger
         WHERE NOT tgisinternal
           AND tgname = 'trg_audit_event_append_only'),
       (SELECT count(*) FROM organization),
       (SELECT count(*) FROM app_user),
       (SELECT count(*) FROM refresh_token),
       (SELECT count(*) FROM transport_group),
       (SELECT count(*) FROM group_coordinator),
       (SELECT count(*) FROM driver),
       (SELECT count(*) FROM vehicle),
       (SELECT count(*) FROM driver_vehicle_link),
       (SELECT count(*) FROM assignment),
       (SELECT count(*) FROM incident),
       (SELECT count(*) FROM announcement),
       (SELECT count(*) FROM audit_event);
'@

if ($RecoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f17(_r[1-9][0-9]*)?$') {
    throw 'F1.7 solo permite un destino nuevo con formato ruta_fija_recovery_YYYYMMDD_f17 o sufijo _rN.'
}
if ((-not (Test-Path -LiteralPath $NativeConfigPath -PathType Leaf)) -or
    (-not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf))) {
    throw 'F1.7 requiere las configuraciones privadas nativa y bootstrap.'
}

Assert-RuntimeStopped
Test-SecretPathIsPrivate -Path $NativeConfigPath
Test-SecretPathIsPrivate -Path $BootstrapConfigPath

$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuración privada requerida por F1.7.'
}
if ($nativeSettings['SPRING_DATASOURCE_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $nativeSettings['SPRING_FLYWAY_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuración local no autoriza usar desarrollo como fuente F1.7.'
}

$backupDirectoryResolved = Assert-BackupDirectory -Path $BackupDirectory
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupPath = Join-Path $backupDirectoryResolved ($developmentDatabase + '_' + $timestamp + '_f17.dump')
$partialBackupPath = $backupPath + '.partial'
if (Test-Path -LiteralPath $backupPath -PathType Leaf) {
    throw 'Ya existe un dump F1.7 con el mismo nombre; no sera sobrescrito.'
}
if (Test-Path -LiteralPath $partialBackupPath -PathType Leaf) {
    throw 'Ya existe un dump parcial F1.7 con el mismo nombre; no sera sobrescrito.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgDump = Get-PgToolPath -Name 'pg_dump'
$pgRestore = Get-PgToolPath -Name 'pg_restore'

$sourceSessionRows = Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query @'
SELECT count(*)
  FROM pg_stat_activity
 WHERE datname = current_database()
   AND pid <> pg_backend_pid()
   AND (state IS DISTINCT FROM 'idle' OR backend_xid IS NOT NULL OR backend_xmin IS NOT NULL);
'@
$sourceSessions = Get-SingleRow -Operation 'sesiones de desarrollo' -Rows $sourceSessionRows
if ($sourceSessions -ne '0') {
    throw 'F1.7 detectó una sesión activa o con transacción en desarrollo; el backup fue cancelado.'
}

$sourceManifestRows = Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql
$sourceManifestBefore = Get-SingleRow -Operation 'catálogo fuente previo' -Rows $sourceManifestRows
$sourceParts = $sourceManifestBefore.Split('|')
if ($sourceParts.Count -ne 19 -or
    $sourceParts[0] -ne 'UTC' -or
    $sourceParts[1] -ne 'UTF8' -or
    $sourceParts[2] -ne $expectedTables -or
    $sourceParts[3] -notmatch '^1:1:.*:true;2:2:.*:true;3:3:.*:true;4:4:.*:true;5:5:.*:true$' -or
    $sourceParts[4] -ne 't' -or
    $sourceParts[5] -ne 'ex_assignment_driver_schedule_no_overlap:valid;ex_assignment_vehicle_schedule_no_overlap:valid' -or
    $sourceParts[6] -notmatch 'trg_audit_event_append_only') {
    throw 'El catálogo fuente no satisface la evidencia post-migración requerida por F1.7.'
}

$recoveryExistsRows = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$RecoveryDatabase');"
$recoveryExists = Get-SingleRow -Operation 'existencia del destino' -Rows $recoveryExistsRows
if ($recoveryExists -ne 'f') {
    throw "La base de recuperación $RecoveryDatabase ya existe; F1.7 se niega a sobrescribirla."
}

New-Item -ItemType Directory -Path $backupDirectoryResolved -Force | Out-Null
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $dumpArguments = @(
        '--format=custom', '--no-owner', '--no-privileges', '--verbose',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        ('--file=' + $partialBackupPath), $developmentDatabase
    )
    $dumpInvocation = Invoke-ExternalTool -Executable $pgDump -Arguments $dumpArguments
    if ($dumpInvocation.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $partialBackupPath -PathType Leaf)) {
        throw 'pg_dump no pudo generar el backup F1.7.'
    }
    if ((Get-Item -LiteralPath $partialBackupPath).Length -le 0) {
        throw 'El dump F1.7 fue creado vacío y no se usará para recuperación.'
    }

    $dumpListInvocation = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $partialBackupPath)
    if ($dumpListInvocation.ExitCode -ne 0 -or @($dumpListInvocation.Output | Where-Object { $_ -match 'TABLE public organization' }).Count -ne 1) {
        throw 'pg_restore no pudo leer el dump F1.7 o falta la tabla organization.'
    }
    Move-Item -LiteralPath $partialBackupPath -Destination $backupPath

    $createRecoverySql = "CREATE DATABASE $RecoveryDatabase OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';"
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query $createRecoverySql
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "ALTER DATABASE $RecoveryDatabase SET TimeZone TO 'UTC';"

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $restoreArguments = @(
        '--exit-on-error', '--no-owner', '--no-privileges',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        '--dbname', $RecoveryDatabase, $backupPath
    )
    $restoreInvocation = Invoke-ExternalTool -Executable $pgRestore -Arguments $restoreArguments
    if ($restoreInvocation.ExitCode -ne 0) {
        throw 'pg_restore no pudo restaurar el dump F1.7.'
    }
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}

$sourceManifestAfterRows = Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql
$sourceManifestAfter = Get-SingleRow -Operation 'catálogo fuente posterior' -Rows $sourceManifestAfterRows
if ($sourceManifestAfter -ne $sourceManifestBefore) {
    throw 'La fuente cambió durante F1.7; la recuperación no se certifica.'
}

$recoveryManifestRows = Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql
$recoveryManifest = Get-SingleRow -Operation 'catálogo restaurado' -Rows $recoveryManifestRows
if ($recoveryManifest -ne $sourceManifestBefore) {
    throw 'La recuperación no coincide con Flyway, tablas, extensión, constraints, auditoría o filas fuente.'
}

$backupHash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
$relativeBackupPath = $backupPath.Substring(([IO.Path]::GetFullPath($repoRoot).Length)).TrimStart(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
)
Write-Output "F1_7_RECOVERY=PASS database=$RecoveryDatabase backup=$relativeBackupPath sha256=$backupHash"
Write-Output 'flyway=V1-V5 tables=12 btree_gist=activo constraints_v5=iguales audit=append-only'
