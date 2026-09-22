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
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$candidateDirectory = Join-Path $repoRoot 'scripts\flyway\f3-1b'
$candidateFiles = @(
    (Join-Path $candidateDirectory 'V7__mobile_assignment_workflow.sql'),
    (Join-Path $candidateDirectory 'V8__mobile_current_location.sql')
)
$fixtureFile = Join-Path $PSScriptRoot 'sql\F3_1B_SeedV6Fixture.sql'
$probeFile = Join-Path $PSScriptRoot 'sql\F3_1B_VerifyMobileMigrations.sql'
$runnerSource = Join-Path $PSScriptRoot 'RunRutaFijaF31bRehearsalFlyway.java'
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'

if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}
if ([string]::IsNullOrWhiteSpace($RecoveryDatabase)) {
    $RecoveryDatabase = 'ruta_fija_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '_f31b'
}
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot 'backups\f3-1b'
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
    $tool = @($candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }) | Select-Object -First 1
    if (-not $tool) {
        throw "No se encontro $Name de PostgreSQL 16."
    }
    return $tool
}

function Test-SecretPathIsPrivate {
    param([Parameter(Mandatory)][string]$Path)

    $root = [IO.Path]::GetFullPath($repoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $root + [IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La configuracion privada debe permanecer dentro del repositorio local.'
    }
    $relative = $fullPath.Substring($prefix.Length).Replace('\', '/')
    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relative
        if ($LASTEXITCODE -ne 0 -or @(& git ls-files -- $relative).Count -gt 0) {
            throw 'La configuracion privada no esta correctamente ignorada por Git.'
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-RuntimeStopped {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 8080, 4200 })
    if ($listeners.Count -gt 0) {
        throw 'F3.1B exige API y CRM detenidos antes del backup y ensayo.'
    }
}

function Get-RepositoryRelativePath {
    param([Parameter(Mandatory)][string]$Path)

    $root = [IO.Path]::GetFullPath($repoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $prefix = $root + [IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La evidencia F3.1B debe permanecer dentro del repositorio.'
    }
    return $fullPath.Substring($prefix.Length).Replace('\', '/')
}

function Assert-BackupDirectory {
    param([Parameter(Mandatory)][string]$Path)

    $backupRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    if ($fullPath -ne $backupRoot -and -not $fullPath.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'F3.1B solo permite backups dentro de backups del repositorio.'
    }
    return $fullPath
}

function Invoke-PostgresQuery {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Query
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazo una comprobacion de F3.1B.'
        }
        return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Invoke-PostgresSqlFile {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$File
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -f $File 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL rechazo $([IO.Path]::GetFileName($File)) en F3.1B."
        }
        return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Invoke-ExternalTool {
    param([Parameter(Mandatory)][string]$Executable, [Parameter(Mandatory)][string[]]$Arguments)

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Executable @Arguments 2>&1
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = @($output) }
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Get-SingleRow {
    param([Parameter(Mandatory)][string[]]$Rows, [Parameter(Mandatory)][string]$Operation)

    if ($Rows.Count -ne 1) {
        throw "F3.1B no obtuvo una unica fila para $Operation."
    }
    return $Rows[0].Trim()
}

function Get-SourceManifest {
    param([Parameter(Mandatory)][string]$Psql, [Parameter(Mandatory)]$Settings)

    return Get-SingleRow -Operation 'manifiesto de origen' -Rows (Invoke-PostgresQuery -Psql $Psql -Database $developmentDatabase -Username 'rf_migrator' -Password $Settings['SPRING_FLYWAY_PASSWORD'] -Query @'
SELECT
    (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '') FROM flyway_schema_history WHERE success),
    (SELECT count(*) FROM organization),
    (SELECT count(*) FROM app_user),
    (SELECT count(*) FROM transport_group),
    (SELECT count(*) FROM driver),
    (SELECT count(*) FROM vehicle),
    (SELECT count(*) FROM driver_vehicle_link),
    (SELECT count(*) FROM assignment),
    (SELECT count(*) FROM incident),
    (SELECT count(*) FROM announcement),
    (SELECT count(*) FROM audit_event),
    (SELECT COALESCE(string_agg(status || ':' || total::text, ',' ORDER BY status), 'NO_ASSIGNMENTS')
       FROM (SELECT status, count(*) AS total FROM assignment GROUP BY status) status_counts);
'@)
}

if ($RecoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f31b(_r[1-9][0-9]*)?$') {
    throw 'F3.1B solo permite una recuperacion nueva con formato ruta_fija_recovery_YYYYMMDD_f31b o sufijo _rN.'
}
foreach ($path in @($candidateFiles + @($fixtureFile, $probeFile, $runnerSource))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "F3.1B requiere el artefacto: $path"
    }
}
if ((-not (Test-Path -LiteralPath $NativeConfigPath -PathType Leaf)) -or (-not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf))) {
    throw 'F3.1B requiere las configuraciones privadas nativa y bootstrap.'
}

Assert-RuntimeStopped
Test-SecretPathIsPrivate -Path $NativeConfigPath
Test-SecretPathIsPrivate -Path $BootstrapConfigPath
$backupDirectory = Assert-BackupDirectory -Path $BackupDirectory
$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings -or
    $nativeSettings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $nativeSettings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuracion local no autoriza el ensayo protegido F3.1B.'
}

$activeMigrationNames = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend\src\main\resources\db\migration') -File | Sort-Object Name | Select-Object -ExpandProperty Name)
$expectedActiveMigrationNames = @(
    'V1__identity_organization_audit.sql',
    'V2__fleet_administration.sql',
    'V3__operation_assignments_incidents_announcements.sql',
    'V4__remove_mobile_only_driver_status.sql',
    'V5__assignment_overlap_exclusion_constraints.sql',
    'V6__unify_administrative_roles_to_admin.sql'
)
if (($activeMigrationNames -join '|') -ne ($expectedActiveMigrationNames -join '|')) {
    throw 'F3.1B exige que V7 y V8 sigan fuera del classpath operativo durante el ensayo.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgDump = Get-PgToolPath -Name 'pg_dump'
$pgRestore = Get-PgToolPath -Name 'pg_restore'
$sourceManifest = Get-SourceManifest -Psql $psql -Settings $nativeSettings
if (-not $sourceManifest.StartsWith('1,2,3,4,5,6|', [StringComparison]::Ordinal)) {
    throw 'F3.1B requiere desarrollo exactamente en V1-V6 antes de ensayar.'
}

$recoveryExists = Get-SingleRow -Operation 'destino de recuperacion' -Rows (Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$RecoveryDatabase');")
if ($recoveryExists -ne 'f') {
    throw "La recuperacion $RecoveryDatabase ya existe; F3.1B no la sobrescribira."
}

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_f31b_v6.dump')
$partialBackupPath = $backupPath + '.partial'
$manifestPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_f31b-rehearsal.json')
foreach ($path in @($backupPath, $partialBackupPath, $manifestPath)) {
    if (Test-Path -LiteralPath $path) {
        throw "F3.1B se niega a sobrescribir evidencia existente: $path"
    }
}

$previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
$previousEnvironment = @{}
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $dump = Invoke-ExternalTool -Executable $pgDump -Arguments @(
        '--format=custom', '--no-owner', '--no-privileges', '--verbose',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        ('--file=' + $partialBackupPath), $developmentDatabase
    )
    if ($dump.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $partialBackupPath -PathType Leaf) -or (Get-Item -LiteralPath $partialBackupPath).Length -le 0) {
        throw 'pg_dump no pudo generar el backup pre-V7/V8 de F3.1B.'
    }
    $dumpList = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $partialBackupPath)
    if ($dumpList.ExitCode -ne 0 -or @($dumpList.Output | Where-Object { $_ -match 'TABLE public assignment' }).Count -ne 1) {
        throw 'pg_restore no pudo leer el backup F3.1B o falta assignment.'
    }
    Move-Item -LiteralPath $partialBackupPath -Destination $backupPath

    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "CREATE DATABASE $RecoveryDatabase OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';"
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "ALTER DATABASE $RecoveryDatabase SET TimeZone TO 'UTC';"

    $restore = Invoke-ExternalTool -Executable $pgRestore -Arguments @(
        '--exit-on-error', '--no-owner', '--no-privileges',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        '--dbname', $RecoveryDatabase, $backupPath
    )
    if ($restore.ExitCode -ne 0) {
        throw 'pg_restore no pudo restaurar la copia V6 aislada de F3.1B.'
    }

    $recoveryManifest = Get-SingleRow -Operation 'copia restaurada' -Rows (Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query @'
SELECT
    (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '') FROM flyway_schema_history WHERE success),
    (SELECT count(*) FROM organization),
    (SELECT count(*) FROM app_user),
    (SELECT count(*) FROM transport_group),
    (SELECT count(*) FROM driver),
    (SELECT count(*) FROM vehicle),
    (SELECT count(*) FROM driver_vehicle_link),
    (SELECT count(*) FROM assignment),
    (SELECT count(*) FROM incident),
    (SELECT count(*) FROM announcement),
    (SELECT count(*) FROM audit_event),
    (SELECT COALESCE(string_agg(status || ':' || total::text, ',' ORDER BY status), 'NO_ASSIGNMENTS')
       FROM (SELECT status, count(*) AS total FROM assignment GROUP BY status) status_counts);
'@)
    if ($recoveryManifest -ne $sourceManifest) {
        throw 'La recuperacion V6 no conserva el manifiesto de desarrollo previo.'
    }

    $null = Invoke-PostgresSqlFile -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -File $fixtureFile

    foreach ($key in @('RF_F31B_FLYWAY_URL', 'RF_F31B_FLYWAY_USERNAME', 'RF_F31B_FLYWAY_PASSWORD')) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    [Environment]::SetEnvironmentVariable('RF_F31B_FLYWAY_URL', "jdbc:postgresql://127.0.0.1:5432/$RecoveryDatabase", 'Process')
    [Environment]::SetEnvironmentVariable('RF_F31B_FLYWAY_USERNAME', 'rf_migrator', 'Process')
    [Environment]::SetEnvironmentVariable('RF_F31B_FLYWAY_PASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f3-1b-rehearsal-flyway-classpath.txt'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo preparar el classpath Flyway para F3.1B.'
        }
        $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($classpath)) {
            throw 'El classpath Flyway de F3.1B esta vacio.'
        }
        & java --class-path "$classpath;src\main\resources" '..\scripts\RunRutaFijaF31bRehearsalFlyway.java' $candidateDirectory
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aprobo V7 y V8 en la recuperacion aislada.'
        }
    }
    finally {
        Pop-Location
    }

    $probeOutput = Invoke-PostgresSqlFile -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -File $probeFile
    if (@($probeOutput | Where-Object { $_.Trim() -eq 'F3_1B_MIGRATION_PROBE=PASS' }).Count -ne 1) {
        throw 'Las restricciones V7/V8 no aprobaron el probe transaccional de F3.1B.'
    }

    $sourceAfter = Get-SourceManifest -Psql $psql -Settings $nativeSettings
    if ($sourceAfter -ne $sourceManifest) {
        throw 'Desarrollo cambio durante el ensayo aislado; F3.1B no continuara.'
    }

    $manifest = [ordered]@{
        schemaVersion = 1
        phase = 'F3.1B'
        completedAtUtc = [DateTime]::UtcNow.ToString('o')
        sourceDatabase = $developmentDatabase
        recoveryDatabase = $RecoveryDatabase
        sourceBackup = Get-RepositoryRelativePath -Path $backupPath
        sourceBackupSha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceDataManifest = $sourceManifest
        v7Sha256 = (Get-FileHash -LiteralPath $candidateFiles[0] -Algorithm SHA256).Hash.ToLowerInvariant()
        v8Sha256 = (Get-FileHash -LiteralPath $candidateFiles[1] -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 3), [Text.UTF8Encoding]::new($false))

    Write-Output "F3_1B_REHEARSAL=PASS recovery=$RecoveryDatabase backup=$(Get-RepositoryRelativePath -Path $backupPath) manifest=$(Get-RepositoryRelativePath -Path $manifestPath) flyway=V7,V8 docker=0"
}
finally {
    foreach ($key in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
}
