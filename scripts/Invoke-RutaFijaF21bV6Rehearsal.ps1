[CmdletBinding()]
param(
    [string]$NativeConfigPath,
    [string]$BootstrapConfigPath,
    [string]$RecoveryDatabase,
    [string]$SourceBackupPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$expectedDevelopmentUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'
$candidateMigrationDirectory = Join-Path $repoRoot 'scripts\flyway\f2-1b'
$candidateMigrationFile = Join-Path $candidateMigrationDirectory 'V6__unify_administrative_roles_to_admin.sql'
$fixtureSqlFile = Join-Path $PSScriptRoot 'sql\F2_1B_SeedLegacyFixture.sql'
$flywayRunnerSource = Join-Path $PSScriptRoot 'RunRutaFijaF21bFlyway.java'

if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}
if ([string]::IsNullOrWhiteSpace($RecoveryDatabase)) {
    $RecoveryDatabase = 'ruta_fija_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '_f21b'
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

function Assert-RuntimeStopped {
    $listeners = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in 8080, 4200 }
    )
    if ($listeners.Count -gt 0) {
        throw 'F2.1B exige API y CRM detenidos antes de ensayar V6.'
    }
}

function Get-SingleRow {
    param(
        [Parameter(Mandatory)][string[]]$Rows,
        [Parameter(Mandatory)][string]$Operation
    )

    if ($Rows.Count -ne 1) {
        throw "F2.1B no obtuvo una evidencia única para $Operation."
    }
    return $Rows[0].Trim()
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
        $output = & $Psql -X -w -v 'ON_ERROR_STOP=1' -h '127.0.0.1' -p '5432' -U $Username -d $Database -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazó una comprobación de F2.1B.'
        }
        return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Invoke-PostgresSqlFile {
    param(
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$File,
        [Parameter(Mandatory)][string]$Psql
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $null = & $Psql -X -w -v 'ON_ERROR_STOP=1' -h '127.0.0.1' -p '5432' -U $Username -d $Database -f $File 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazó el fixture legacy controlado de F2.1B.'
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
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

function Resolve-BaselineBackup {
    param([string]$Path)

    $backupRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups\f2-1a')).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    if ([string]::IsNullOrWhiteSpace($Path)) {
        $dumps = @(Get-ChildItem -LiteralPath $backupRoot -File -Filter '*_f21a.dump' -ErrorAction Stop)
        if ($dumps.Count -ne 1) {
            throw 'F2.1B requiere exactamente un dump F2.1A o un SourceBackupPath explícito.'
        }
        $Path = $dumps[0].FullName
    }

    $resolvedPath = [IO.Path]::GetFullPath($Path)
    if (-not $resolvedPath.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'F2.1B solo acepta un dump dentro de backups\f2-1a.'
    }
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf) -or
        (Get-Item -LiteralPath $resolvedPath).Length -le 0) {
        throw 'No existe un dump F2.1A no vacío para el ensayo.'
    }
    return $resolvedPath
}

function Assert-ActiveMigrationDirectory {
    $activeDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'
    $actual = @(
        Get-ChildItem -LiteralPath $activeDirectory -File |
            Sort-Object Name |
            Select-Object -ExpandProperty Name
    )
    $expected = @(
        'V1__identity_organization_audit.sql',
        'V2__fleet_administration.sql',
        'V3__operation_assignments_incidents_announcements.sql',
        'V4__remove_mobile_only_driver_status.sql',
        'V5__assignment_overlap_exclusion_constraints.sql'
    )
    if (($actual -join '|') -ne ($expected -join '|')) {
        throw 'F2.1B requiere que la candidata V6 permanezca fuera del classpath operativo hasta F2.2.'
    }
}

$developmentManifestSql = @'
WITH role_counts AS (
    SELECT COALESCE(string_agg(role || ':' || total::text, ',' ORDER BY role), 'SIN_USUARIOS') AS value
      FROM (
          SELECT role, count(*) AS total
            FROM app_user
           GROUP BY role
      ) grouped
)
SELECT (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '')
          FROM flyway_schema_history
         WHERE success),
       (SELECT count(*) FROM app_user),
       (SELECT value FROM role_counts),
       (SELECT count(*) FROM refresh_token),
       (SELECT count(*) FROM group_coordinator);
'@

$emptyFixtureSql = @'
SELECT (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '')
          FROM flyway_schema_history
         WHERE success),
       (SELECT count(*) FROM organization),
       (SELECT count(*) FROM app_user),
       (SELECT count(*) FROM refresh_token),
       (SELECT count(*) FROM transport_group),
       (SELECT count(*) FROM group_coordinator),
       (SELECT count(*) FROM audit_event);
'@

$fixtureBeforeSql = @'
WITH role_counts AS (
    SELECT string_agg(role || ':' || total::text, ',' ORDER BY role) AS value
      FROM (
          SELECT role, count(*) AS total
            FROM app_user
           GROUP BY role
      ) grouped
)
SELECT (SELECT value FROM role_counts),
       (SELECT count(*) FROM app_user),
       (SELECT count(*) FROM app_user WHERE role IN ('ADMINISTRADOR', 'COORDINADOR')),
       (SELECT count(*)
          FROM refresh_token token
          JOIN app_user user_account ON user_account.id = token.user_id
         WHERE user_account.role IN ('ADMINISTRADOR', 'COORDINADOR')
           AND token.revoked_at IS NULL),
       (SELECT count(*)
          FROM refresh_token token
          JOIN app_user user_account ON user_account.id = token.user_id
         WHERE user_account.role IN ('SUPER_ADMIN', 'CONDUCTOR')
           AND token.revoked_at IS NULL),
       (SELECT count(*) FROM group_coordinator),
       (SELECT metadata_json ->> 'role'
          FROM audit_event
         WHERE id = '00000000-0000-4000-8000-000000005001');
'@

$fixtureAfterSql = @'
WITH role_counts AS (
    SELECT string_agg(role || ':' || total::text, ',' ORDER BY role) AS value
      FROM (
          SELECT role, count(*) AS total
            FROM app_user
           GROUP BY role
      ) grouped
)
SELECT (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '')
          FROM flyway_schema_history
         WHERE success),
       (SELECT value FROM role_counts),
       (SELECT count(*) FROM app_user),
       (SELECT count(*) FROM app_user WHERE role IN ('ADMINISTRADOR', 'COORDINADOR')),
       (SELECT count(*) FROM app_user WHERE role = 'ADMIN'),
       (SELECT count(*) FROM group_coordinator),
       (SELECT count(*)
          FROM refresh_token
         WHERE id IN (
             '00000000-0000-4000-8000-000000003001',
             '00000000-0000-4000-8000-000000003002',
             '00000000-0000-4000-8000-000000003003',
             '00000000-0000-4000-8000-000000003004'
         )
           AND revoked_at IS NOT NULL),
       (SELECT count(*)
          FROM refresh_token
         WHERE id IN (
             '00000000-0000-4000-8000-000000003005',
             '00000000-0000-4000-8000-000000003006'
         )
           AND revoked_at IS NULL),
       (SELECT revoked_at = TIMESTAMPTZ '2026-09-20 00:00:00+00'
          FROM refresh_token
         WHERE id = '00000000-0000-4000-8000-000000003007'),
       (SELECT metadata_json ->> 'role'
          FROM audit_event
         WHERE id = '00000000-0000-4000-8000-000000005001'),
       (SELECT CASE
                   WHEN EXISTS (
                       SELECT 1
                         FROM pg_constraint
                        WHERE conname = 'ck_app_user_role'
                          AND pg_get_constraintdef(oid) LIKE '%''SUPER_ADMIN''%'
                          AND pg_get_constraintdef(oid) LIKE '%''ADMIN''%'
                          AND pg_get_constraintdef(oid) LIKE '%''CONDUCTOR''%'
                          AND pg_get_constraintdef(oid) NOT LIKE '%''ADMINISTRADOR''%'
                          AND pg_get_constraintdef(oid) NOT LIKE '%''COORDINADOR''%'
                   ) THEN 'target'
                   ELSE 'unexpected'
               END);
'@

$legacyRoleRejectionSql = @'
DO $rejection$
BEGIN
    BEGIN
        INSERT INTO app_user (
            id, organization_id, email, password_hash, full_name, phone, role, active, last_login_at, created_at, updated_at
        ) VALUES (
            '00000000-0000-4000-8000-000000009999',
            '00000000-0000-4000-8000-000000000101',
            'rejected.legacy.f21b@rutafija.fixture',
            'fixture-not-for-login',
            'Rol legacy rechazado',
            NULL,
            'COORDINADOR',
            TRUE,
            NULL,
            TIMESTAMPTZ '2026-09-22 00:00:00+00',
            TIMESTAMPTZ '2026-09-22 00:00:00+00'
        );
        RAISE EXCEPTION 'F2.1B aceptó un rol legacy después de V6.';
    EXCEPTION
        WHEN check_violation THEN
            NULL;
    END;
END
$rejection$;
'@

if ($RecoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f21b(_r[1-9][0-9]*)?$') {
    throw 'F2.1B solo permite un destino nuevo con formato ruta_fija_recovery_YYYYMMDD_f21b o sufijo _rN.'
}
foreach ($requiredFile in @($candidateMigrationFile, $fixtureSqlFile, $flywayRunnerSource)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "F2.1B requiere el artefacto: $requiredFile"
    }
}
if ((-not (Test-Path -LiteralPath $NativeConfigPath -PathType Leaf)) -or
    (-not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf))) {
    throw 'F2.1B requiere las configuraciones privadas nativa y bootstrap.'
}

Assert-RuntimeStopped
Test-SecretPathIsPrivate -Path $NativeConfigPath
Test-SecretPathIsPrivate -Path $BootstrapConfigPath
Assert-ActiveMigrationDirectory

$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuración privada requerida por F2.1B.'
}
if ($nativeSettings['SPRING_DATASOURCE_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $nativeSettings['SPRING_FLYWAY_URL'] -ne $expectedDevelopmentUrl -or
    $nativeSettings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuración local no autoriza comprobar desarrollo en F2.1B.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgRestore = Get-PgToolPath -Name 'pg_restore'
$sourceBackup = Resolve-BaselineBackup -Path $SourceBackupPath
$sourceBackupHash = (Get-FileHash -LiteralPath $sourceBackup -Algorithm SHA256).Hash.ToLowerInvariant()

$developmentBefore = Get-SingleRow -Operation 'manifiesto de desarrollo previo' -Rows (Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $developmentManifestSql)
$developmentParts = $developmentBefore.Split('|')
if ($developmentParts.Count -ne 5 -or
    $developmentParts[0] -ne '1,2,3,4,5' -or
    $developmentParts[2] -match '(^|,)ADMIN:') {
    throw 'Desarrollo no está en la línea base V1–V5 esperada para F2.1B.'
}

$dumpList = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $sourceBackup)
if ($dumpList.ExitCode -ne 0 -or
    @($dumpList.Output | Where-Object { $_ -match 'TABLE public app_user' }).Count -ne 1) {
    throw 'El dump F2.1A no es legible o no contiene app_user.'
}

$recoveryExists = Get-SingleRow -Operation 'existencia del destino' -Rows (Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$RecoveryDatabase');")
if ($recoveryExists -ne 'f') {
    throw "La base de recuperación $RecoveryDatabase ya existe; F2.1B se niega a sobrescribirla."
}

$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "CREATE DATABASE $RecoveryDatabase OWNER rf_migrator TEMPLATE template0 ENCODING 'UTF8';"
    $null = Invoke-PostgresQuery -Psql $psql -Database 'postgres' -Username $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_USERNAME'] -Password $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'] -Query "ALTER DATABASE $RecoveryDatabase SET TimeZone TO 'UTC';"

    [Environment]::SetEnvironmentVariable('PGPASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $restoreInvocation = Invoke-ExternalTool -Executable $pgRestore -Arguments @(
        '--exit-on-error', '--no-owner', '--no-privileges',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        '--dbname', $RecoveryDatabase, $sourceBackup
    )
    if ($restoreInvocation.ExitCode -ne 0) {
        throw 'pg_restore no pudo crear la copia V5 de F2.1B.'
    }

    $restoredEmptyState = Get-SingleRow -Operation 'estado V5 restaurado' -Rows (Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $emptyFixtureSql)
    if ($restoredEmptyState -ne '1,2,3,4,5|0|0|0|0|0|0') {
        throw 'F2.1B exige una restauración V5 vacía antes de insertar el fixture controlado.'
    }

    Invoke-PostgresSqlFile -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -File $fixtureSqlFile
    $fixtureBefore = Get-SingleRow -Operation 'fixture legacy previo a V6' -Rows (Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $fixtureBeforeSql)
    if ($fixtureBefore -ne 'ADMINISTRADOR:2,CONDUCTOR:1,COORDINADOR:2,SUPER_ADMIN:1|6|4|4|2|1|COORDINADOR') {
        throw 'El fixture legacy no contiene los datos de caracterización esperados.'
    }

    $environmentKeys = @('RF_F21B_FLYWAY_URL', 'RF_F21B_FLYWAY_USERNAME', 'RF_F21B_FLYWAY_PASSWORD')
    $previousEnvironment = @{}
    foreach ($key in $environmentKeys) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    try {
        [Environment]::SetEnvironmentVariable('RF_F21B_FLYWAY_URL', "jdbc:postgresql://127.0.0.1:5432/$RecoveryDatabase", 'Process')
        [Environment]::SetEnvironmentVariable('RF_F21B_FLYWAY_USERNAME', 'rf_migrator', 'Process')
        [Environment]::SetEnvironmentVariable('RF_F21B_FLYWAY_PASSWORD', $nativeSettings['SPRING_FLYWAY_PASSWORD'], 'Process')

        $backendRoot = Join-Path $repoRoot 'backend'
        $classpathFile = Join-Path $backendRoot 'target\f2-1b-flyway-classpath.txt'
        Push-Location $backendRoot
        try {
            & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
            if ($LASTEXITCODE -ne 0) {
                throw 'No se pudo resolver el classpath de Flyway para F2.1B.'
            }
            $dependencyClasspath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
            if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) {
                throw 'El classpath de Flyway F2.1B está vacío.'
            }
            & java --class-path "$dependencyClasspath;src\main\resources" '..\scripts\RunRutaFijaF21bFlyway.java' $candidateMigrationDirectory
            if ($LASTEXITCODE -ne 0) {
                throw 'Flyway no aprobó la candidata V6 en F2.1B.'
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        foreach ($key in $environmentKeys) {
            [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
        }
    }

    $null = Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $legacyRoleRejectionSql
    $fixtureAfter = Get-SingleRow -Operation 'fixture posterior a V6' -Rows (Invoke-PostgresQuery -Psql $psql -Database $RecoveryDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $fixtureAfterSql)
    if ($fixtureAfter -ne '1,2,3,4,5,6|ADMIN:4,CONDUCTOR:1,SUPER_ADMIN:1|6|0|4|1|4|2|t|COORDINADOR|target') {
        throw 'V6 no preservó cuentas, tokens, auditoría o constraint conforme a F2.1B.'
    }
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}

$developmentAfter = Get-SingleRow -Operation 'manifiesto de desarrollo posterior' -Rows (Invoke-PostgresQuery -Psql $psql -Database $developmentDatabase -Username 'rf_migrator' -Password $nativeSettings['SPRING_FLYWAY_PASSWORD'] -Query $developmentManifestSql)
if ($developmentAfter -ne $developmentBefore) {
    throw 'F2.1B detectó cambios en desarrollo; el ensayo no se certifica.'
}

$relativeBackupPath = $sourceBackup.Substring(([IO.Path]::GetFullPath($repoRoot).Length)).TrimStart(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
)
Write-Output "F2_1B_V6_REHEARSAL=PASS fixture=$RecoveryDatabase backup=$relativeBackupPath sha256=$sourceBackupHash"
Write-Output 'candidate=V6 active_migrations=V1-V5 fixture_migrations=V1-V6 accounts=preserved legacy_roles=0 refresh_revoked=4 group_coordinator=preserved audit_history=preserved docker=0'
