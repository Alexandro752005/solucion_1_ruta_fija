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
$testDatabase = 'ruta_fija_test'
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$developmentRunner = Join-Path $PSScriptRoot 'RunRutaFijaF33Flyway.java'
$rehearsalRunner = Join-Path $PSScriptRoot 'RunRutaFijaF33RehearsalFlyway.java'
$migrationDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'

if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}
if ([string]::IsNullOrWhiteSpace($RecoveryDatabase)) {
    $RecoveryDatabase = 'ruta_fija_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '_f33'
}
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot 'backups\f3-3'
}

function Get-PgToolPath {
    param([Parameter(Mandatory)][string]$Name)

    $extension = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $candidates += Join-Path $env:ProgramFiles ('PostgreSQL\16\bin\' + $Name + $extension)
    }
    foreach ($candidateName in @($Name + $extension, $Name)) {
        $command = Get-Command $candidateName -ErrorAction SilentlyContinue
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

function Assert-PrivateConfig {
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
            throw 'La configuracion privada no esta ignorada de forma segura por Git.'
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-RuntimeStopped {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in 8080, 4200 })
    if ($listeners.Count -gt 0) {
        throw 'F3.3 exige API y CRM detenidos antes del backup, ensayo y migracion.'
    }
}

function Assert-BackupDirectory {
    param([Parameter(Mandatory)][string]$Path)

    $backupRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    if ($fullPath -ne $backupRoot -and -not $fullPath.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'F3.3 solo permite almacenar backups dentro de backups del repositorio.'
    }
    return $fullPath
}

function Invoke-PsqlQuery {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][string]$Operation
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U $Username -d $Database -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL rechazo $Operation."
        }
        return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Get-OneRow {
    param([Parameter(Mandatory)][string[]]$Rows, [Parameter(Mandatory)][string]$Operation)

    if ($Rows.Count -ne 1) {
        throw "F3.3 no obtuvo una unica fila para $Operation."
    }
    return $Rows[0].Trim()
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

function Get-DataManifest {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password
    )

    return Get-OneRow -Operation "manifiesto de $Database" -Rows (Invoke-PsqlQuery -Psql $Psql -Database $Database -Username $Username -Password $Password -Operation "manifiesto de $Database" -Query @'
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
    (SELECT count(*) FROM audit_event);
'@)
}

if ($RecoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f33(_r[1-9][0-9]*)?$') {
    throw 'El nombre de recuperacion debe ser ruta_fija_recovery_YYYYMMDD_f33 o sufijo _rN.'
}
foreach ($requiredPath in @($NativeConfigPath, $BootstrapConfigPath, $developmentRunner, $rehearsalRunner)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "F3.3 requiere el artefacto: $requiredPath"
    }
}
$expectedMigrations = @(
    'V1__identity_organization_audit.sql',
    'V2__fleet_administration.sql',
    'V3__operation_assignments_incidents_announcements.sql',
    'V4__remove_mobile_only_driver_status.sql',
    'V5__assignment_overlap_exclusion_constraints.sql',
    'V6__unify_administrative_roles_to_admin.sql',
    'V7__mobile_assignment_workflow.sql',
    'V8__mobile_current_location.sql',
    'V9__mobile_incidents_and_announcement_receipts.sql',
    'V10__mobile_command_receipts.sql'
)
$actualMigrations = @(Get-ChildItem -LiteralPath $migrationDirectory -File |
        Sort-Object { [int]([regex]::Match($_.Name, '^V(\d+)__').Groups[1].Value) }, Name |
        Select-Object -ExpandProperty Name)
if (($actualMigrations -join '|') -ne ($expectedMigrations -join '|')) {
    throw 'El classpath Flyway no contiene exactamente V1-V10 para F3.3.'
}

Assert-RuntimeStopped
Assert-PrivateConfig -Path $NativeConfigPath
Assert-PrivateConfig -Path $BootstrapConfigPath
$backupDirectory = Assert-BackupDirectory -Path $BackupDirectory
$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings -or
    $nativeSettings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $nativeSettings['RF_TEST_DATASOURCE_URL'] -ne "jdbc:postgresql://127.0.0.1:5432/$testDatabase" -or
    $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_HOST'] -ne '127.0.0.1' -or
    $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PORT'] -ne '5432') {
    throw 'La configuracion nativa no autoriza la migracion protegida F3.3.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgDump = Get-PgToolPath -Name 'pg_dump'
$pgRestore = Get-PgToolPath -Name 'pg_restore'
$javac = (Get-Command javac.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
$java = (Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
if (-not $javac -or -not $java) {
    throw 'F3.3 requiere Java 21 (java y javac) para ejecutar Flyway protegido.'
}

$sourceBefore = Get-DataManifest -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD']
if (-not $sourceBefore.StartsWith('1,2,3,4,5,6,7,8|', [StringComparison]::Ordinal)) {
    throw 'F3.3 requiere desarrollo exactamente en V1-V8 antes de migrar.'
}
$recoveryExists = Get-OneRow -Operation 'destino de recuperacion' -Rows (Invoke-PsqlQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Operation 'destino de recuperacion' -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$RecoveryDatabase');")
if ($recoveryExists -ne 'f') {
    throw "La recuperacion $RecoveryDatabase ya existe; F3.3 no sobrescribira evidencia."
}

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_f33_v8.dump')
$partialBackupPath = $backupPath + '.partial'
$manifestPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_f33-migration.json')
foreach ($path in @($backupPath, $partialBackupPath, $manifestPath)) {
    if (Test-Path -LiteralPath $path) {
        throw "F3.3 se niega a sobrescribir evidencia existente: $path"
    }
}

$previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
$previousEnvironment = @{}
try {
    # The development runner deliberately reads only Flyway variables from its
    # process. Set precisely those values for this process and restore them in
    # the finally block; no secret is written to disk or printed.
    foreach ($key in @('SPRING_FLYWAY_URL', 'SPRING_FLYWAY_USERNAME', 'SPRING_FLYWAY_PASSWORD')) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $nativeSettings[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $dump = Invoke-ExternalTool -Executable $pgDump -Arguments @(
        '--format=custom', '--no-owner', '--no-privileges', '--verbose',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        ('--file=' + $partialBackupPath), $developmentDatabase
    )
    if ($dump.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $partialBackupPath -PathType Leaf) -or (Get-Item -LiteralPath $partialBackupPath).Length -le 0) {
        throw 'pg_dump no pudo crear el backup previo V1-V8 de F3.3.'
    }
    $dumpList = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $partialBackupPath)
    if ($dumpList.ExitCode -ne 0 -or @($dumpList.Output | Where-Object { $_ -match 'TABLE public assignment' }).Count -ne 1) {
        throw 'pg_restore no pudo validar el backup de F3.3.'
    }
    Move-Item -LiteralPath $partialBackupPath -Destination $backupPath

    $null = Invoke-PsqlQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Operation 'crear recuperacion aislada' -Query "CREATE DATABASE $RecoveryDatabase OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';"
    $null = Invoke-PsqlQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Operation 'configurar recuperacion aislada' -Query "ALTER DATABASE $RecoveryDatabase SET TimeZone TO 'UTC';"

    $restore = Invoke-ExternalTool -Executable $pgRestore -Arguments @(
        '--exit-on-error', '--no-owner', '--no-privileges',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        '--dbname', $RecoveryDatabase, $backupPath
    )
    if ($restore.ExitCode -ne 0) {
        throw 'pg_restore no pudo restaurar la copia V1-V8 aislada de F3.3.'
    }
    $recoveryBefore = Get-DataManifest -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD']
    if ($recoveryBefore -ne $sourceBefore) {
        throw 'La recuperacion aislada no conserva el manifiesto de origen.'
    }

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f3-3-flyway-classpath.txt'
    $runnerOutput = Join-Path $backendRoot 'target\f3-3-flyway-runner'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo preparar el classpath Flyway de F3.3.'
        }
        $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($classpath)) {
            throw 'El classpath Flyway de F3.3 esta vacio.'
        }
        New-Item -ItemType Directory -Path $runnerOutput -Force | Out-Null
        & $javac --class-path "$classpath;src\main\resources" -d $runnerOutput '..\scripts\RunRutaFijaF33Flyway.java' '..\scripts\RunRutaFijaF33RehearsalFlyway.java'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo compilar los runners Flyway de F3.3.'
        }
        foreach ($key in @('RF_F33_FLYWAY_URL', 'RF_F33_FLYWAY_USERNAME', 'RF_F33_FLYWAY_PASSWORD')) {
            $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        }
        [Environment]::SetEnvironmentVariable('RF_F33_FLYWAY_URL', "jdbc:postgresql://127.0.0.1:5432/$RecoveryDatabase", 'Process')
        [Environment]::SetEnvironmentVariable('RF_F33_FLYWAY_USERNAME', 'rf_migrator', 'Process')
        [Environment]::SetEnvironmentVariable('RF_F33_FLYWAY_PASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
        & $java --class-path "$classpath;src\main\resources;$runnerOutput" RunRutaFijaF33RehearsalFlyway
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aprobo V9/V10 sobre la recuperacion aislada.'
        }

        $rehearsalProbe = Get-OneRow -Operation 'probe de recuperacion V9/V10' -Rows (Invoke-PsqlQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Operation 'probe de recuperacion V9/V10' -Query @'
SELECT
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success),
    to_regclass('public.announcement_receipt') IS NOT NULL,
    to_regclass('public.mobile_command_receipt') IS NOT NULL,
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'incident' AND column_name = 'source'),
    NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_announcement_read_ack_web_only'),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_hash');
'@)
        if ($rehearsalProbe -ne '1,2,3,4,5,6,7,8,9,10|t|t|t|t|t') {
            throw 'La recuperacion no satisface el contrato fisico V9/V10.'
        }
        $sourceDuringRehearsal = Get-DataManifest -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD']
        if ($sourceDuringRehearsal -ne $sourceBefore) {
            throw 'Desarrollo cambio durante el ensayo aislado; F3.3 no aplicara cambios.'
        }

        & $java --class-path "$classpath;src\main\resources;$runnerOutput" RunRutaFijaF33Flyway
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aprobo V9/V10 sobre desarrollo local.'
        }
    }
    finally {
        Pop-Location
    }

    $null = Invoke-PsqlQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Operation 'privilegios DML F3.3' -Query 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE announcement_receipt, mobile_command_receipt TO rf_app;'
    $developmentProbe = Get-OneRow -Operation 'probe de desarrollo V9/V10' -Rows (Invoke-PsqlQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Operation 'probe de desarrollo V9/V10' -Query @'
SELECT
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success),
    to_regclass('public.announcement_receipt') IS NOT NULL,
    to_regclass('public.mobile_command_receipt') IS NOT NULL,
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'incident' AND column_name = 'source'),
    NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_announcement_read_ack_web_only'),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_hash'),
    has_table_privilege('rf_app', 'public.announcement_receipt', 'SELECT')
      AND has_table_privilege('rf_app', 'public.announcement_receipt', 'INSERT')
      AND has_table_privilege('rf_app', 'public.announcement_receipt', 'UPDATE')
      AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'SELECT')
      AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'INSERT')
      AND has_table_privilege('rf_app', 'public.mobile_command_receipt', 'UPDATE'),
    has_schema_privilege('rf_app', 'public', 'CREATE');
'@)
    if ($developmentProbe -ne '1,2,3,4,5,6,7,8,9,10|t|t|t|t|t|t|f') {
        throw 'Desarrollo no satisface el contrato V9/V10 o minimo privilegio de F3.3.'
    }
    $sourceAfter = Get-DataManifest -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD']
    $separator = $sourceBefore.IndexOf('|')
    if ($separator -lt 0 -or -not $sourceAfter.StartsWith('1,2,3,4,5,6,7,8,9,10|', [StringComparison]::Ordinal) -or
        $sourceAfter.Substring($sourceAfter.IndexOf('|') + 1) -ne $sourceBefore.Substring($separator + 1)) {
        throw 'F3.3 no preservo el manifiesto de datos al aplicar V9/V10.'
    }

    $manifest = [ordered]@{
        schemaVersion = 1
        phase = 'F3.3'
        completedAtUtc = [DateTime]::UtcNow.ToString('o')
        sourceDatabase = $developmentDatabase
        recoveryDatabase = $RecoveryDatabase
        sourceBackup = $backupPath.Substring($repoRoot.Length + 1).Replace('\', '/')
        sourceBackupSha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceDataManifestBefore = $sourceBefore
        sourceDataManifestAfter = $sourceAfter
        v9Sha256 = (Get-FileHash -LiteralPath (Join-Path $migrationDirectory 'V9__mobile_incidents_and_announcement_receipts.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
        v10Sha256 = (Get-FileHash -LiteralPath (Join-Path $migrationDirectory 'V10__mobile_command_receipts.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 3), [Text.UTF8Encoding]::new($false))
    Write-Output "F3_3_MIGRATION=PASS backup=$($manifest.sourceBackup) recovery=$RecoveryDatabase flyway=V1-V10 v9=receipts v10=idempotency privileges=DML_without_DDL docker=0"
}
finally {
    foreach ($key in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
}
