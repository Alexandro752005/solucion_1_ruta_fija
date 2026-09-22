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
$expectedDevelopmentUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'

if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}
if ([string]::IsNullOrWhiteSpace($RecoveryDatabase)) {
    $RecoveryDatabase = 'ruta_fija_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '_f21a'
}
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot 'backups\f2-1a'
}

function Get-PgToolPath {
    param([Parameter(Mandatory)][string]$Name)

    $extension = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $candidates += Join-Path $env:ProgramFiles ('PostgreSQL\16\bin\' + $Name + $extension)
    }
    foreach ($commandName in @($Name + $extension, $Name)) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            $candidates += $command.Source
        }
    }

    $tool = @($candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }) |
        Select-Object -First 1
    if (-not $tool) {
        throw "No se encontró $Name de PostgreSQL 16."
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
            throw "El archivo privado no está ignorado por Git: $relativePath"
        }
        if (@(& git ls-files -- $relativePath).Count -gt 0) {
            throw "El archivo privado está versionado: $relativePath"
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-BackupDirectory {
    param([Parameter(Mandatory)][string]$Path)

    $repositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $backupRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )

    if ((-not $resolvedPath.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) -and
        $resolvedPath -ne $backupRoot) {
        throw 'F2.1A solo permite guardar dumps dentro de backups del repositorio.'
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
        throw 'F2.1A exige API y CRM detenidos antes de capturar el respaldo pre-V6.'
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
            throw 'PostgreSQL rechazó una comprobación de F2.1A.'
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
        throw "F2.1A no obtuvo una evidencia única para $Operation."
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

$manifestSql = @'
WITH role_counts AS (
    SELECT COALESCE(string_agg(role || ':' || total::text, ',' ORDER BY role), 'SIN_USUARIOS') AS value
      FROM (
          SELECT role, count(*) AS total
            FROM app_user
           GROUP BY role
      ) grouped
)
SELECT current_setting('TimeZone'),
       current_setting('server_encoding'),
       current_setting('server_version_num'),
       (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '')
          FROM flyway_schema_history
         WHERE success),
       (SELECT count(*) FROM app_user),
       (SELECT value FROM role_counts),
       (SELECT count(*) FROM refresh_token),
       (SELECT count(*) FROM group_coordinator),
       (SELECT count(*) FROM audit_event),
       (SELECT CASE
                   WHEN EXISTS (
                       SELECT 1
                         FROM pg_constraint
                        WHERE conname = 'ck_app_user_role'
                          AND pg_get_constraintdef(oid) LIKE '%''ADMINISTRADOR''%'
                          AND pg_get_constraintdef(oid) LIKE '%''COORDINADOR''%'
                          AND pg_get_constraintdef(oid) NOT LIKE '%''ADMIN''%'
                   ) THEN 'legacy'
                   ELSE 'unexpected'
               END);
'@

if ($RecoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f21a(_r[1-9][0-9]*)?$') {
    throw 'F2.1A solo permite un destino nuevo con formato ruta_fija_recovery_YYYYMMDD_f21a o sufijo _rN.'
}
if ((-not (Test-Path -LiteralPath $NativeConfigPath -PathType Leaf)) -or
    (-not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf))) {
    throw 'F2.1A requiere las configuraciones privadas nativa y bootstrap.'
}

Assert-RuntimeStopped
Test-SecretPathIsPrivate -Path $NativeConfigPath
Test-SecretPathIsPrivate -Path $BootstrapConfigPath

$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuración privada requerida por F2.1A.'
}
if ($nativeSettings['SPRING_DATASOURCE_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $nativeSettings['SPRING_FLYWAY_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuración local no autoriza usar desarrollo como fuente F2.1A.'
}

$backupDirectoryResolved = Assert-BackupDirectory -Path $BackupDirectory
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupPath = Join-Path $backupDirectoryResolved ($developmentDatabase + '_' + $timestamp + '_f21a.dump')
$partialBackupPath = $backupPath + '.partial'
if ((Test-Path -LiteralPath $backupPath -PathType Leaf) -or
    (Test-Path -LiteralPath $partialBackupPath -PathType Leaf)) {
    throw 'Ya existe un dump F2.1A con el mismo nombre; no será sobrescrito.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgDump = Get-PgToolPath -Name 'pg_dump'
$pgRestore = Get-PgToolPath -Name 'pg_restore'

$sourceSessions = Get-SingleRow -Operation 'sesiones de desarrollo' -Rows (Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query @'
SELECT count(*)
  FROM pg_stat_activity
 WHERE datname = current_database()
   AND pid <> pg_backend_pid()
   AND (state IS DISTINCT FROM 'idle' OR backend_xid IS NOT NULL OR backend_xmin IS NOT NULL);
'@)
if ($sourceSessions -ne '0') {
    throw 'F2.1A detectó una sesión activa o con transacción en desarrollo; el respaldo fue cancelado.'
}

$sourceManifestBefore = Get-SingleRow -Operation 'catálogo fuente previo' -Rows (Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql)
$sourceParts = $sourceManifestBefore.Split('|')
if ($sourceParts.Count -ne 10 -or
    $sourceParts[0] -ne 'UTC' -or
    $sourceParts[1] -ne 'UTF8' -or
    $sourceParts[2] -notmatch '^16' -or
    $sourceParts[3] -ne '1,2,3,4,5' -or
    $sourceParts[5] -match '(^|,)ADMIN:' -or
    $sourceParts[9] -ne 'legacy') {
    throw 'La fuente no está en la línea base pre-V6 esperada por F2.1A.'
}

$recoveryExists = Get-SingleRow -Operation 'existencia del destino' -Rows (Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$RecoveryDatabase');")
if ($recoveryExists -ne 'f') {
    throw "La base de recuperación $RecoveryDatabase ya existe; F2.1A se niega a sobrescribirla."
}

New-Item -ItemType Directory -Path $backupDirectoryResolved -Force | Out-Null
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $dumpInvocation = Invoke-ExternalTool -Executable $pgDump -Arguments @(
        '--format=custom', '--no-owner', '--no-privileges', '--verbose',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        ('--file=' + $partialBackupPath), $developmentDatabase
    )
    if ($dumpInvocation.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $partialBackupPath -PathType Leaf)) {
        throw 'pg_dump no pudo generar el respaldo F2.1A.'
    }
    if ((Get-Item -LiteralPath $partialBackupPath).Length -le 0) {
        throw 'El dump F2.1A fue creado vacío y no se usará para recuperación.'
    }

    $dumpListInvocation = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $partialBackupPath)
    if ($dumpListInvocation.ExitCode -ne 0 -or
        @($dumpListInvocation.Output | Where-Object { $_ -match 'TABLE public app_user' }).Count -ne 1) {
        throw 'pg_restore no pudo leer el dump F2.1A o falta la tabla app_user.'
    }
    Move-Item -LiteralPath $partialBackupPath -Destination $backupPath

    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "CREATE DATABASE $RecoveryDatabase OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';"
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "ALTER DATABASE $RecoveryDatabase SET TimeZone TO 'UTC';"

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $restoreInvocation = Invoke-ExternalTool -Executable $pgRestore -Arguments @(
        '--exit-on-error', '--no-owner', '--no-privileges',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        '--dbname', $RecoveryDatabase, $backupPath
    )
    if ($restoreInvocation.ExitCode -ne 0) {
        throw 'pg_restore no pudo restaurar el respaldo F2.1A.'
    }
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}

$sourceManifestAfter = Get-SingleRow -Operation 'catálogo fuente posterior' -Rows (Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql)
if ($sourceManifestAfter -ne $sourceManifestBefore) {
    throw 'La fuente cambió durante F2.1A; la recuperación no se certifica.'
}

$recoveryManifest = Get-SingleRow -Operation 'catálogo restaurado' -Rows (Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $manifestSql)
if ($recoveryManifest -ne $sourceManifestBefore) {
    throw 'La recuperación F2.1A no coincide con la línea base fuente.'
}

$backupHash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
$relativeBackupPath = $backupPath.Substring(([IO.Path]::GetFullPath($repoRoot).Length)).TrimStart(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
)
Write-Output "F2_1A_BASELINE=PASS source=$developmentDatabase recovery=$RecoveryDatabase backup=$relativeBackupPath sha256=$backupHash"
Write-Output "flyway=V1-V5 users=$($sourceParts[4]) roles=$($sourceParts[5]) refresh_tokens=$($sourceParts[6]) group_coordinator=$($sourceParts[7]) audit_events=$($sourceParts[8]) restore=identical docker=0"
