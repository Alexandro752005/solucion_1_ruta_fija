[CmdletBinding()]
param(
    [string]$ConfigPath,
    [string]$BackupDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$runnerSource = Join-Path $PSScriptRoot 'RunRutaFijaM8Flyway.java'
$migrationDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    $BackupDirectory = Join-Path $repoRoot 'backups\m8-v12'
}

function Get-PgToolPath {
    param([Parameter(Mandatory)][string]$Name)

    $candidates = @(
        (Join-Path $env:ProgramFiles "PostgreSQL\16\bin\$Name.exe"),
        (Get-Command "$Name.exe" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $tool = $candidates | Select-Object -First 1
    if (-not $tool) {
        throw "M8 no encontro $Name de PostgreSQL 16."
    }
    return $tool
}

function Assert-PrivateConfig {
    param([Parameter(Mandatory)][string]$Path)

    $root = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
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
        throw 'M8 exige API y CRM detenidos antes de respaldar y migrar PostgreSQL.'
    }
}

function Assert-BackupDirectory {
    param([Parameter(Mandatory)][string]$Path)

    $backupRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'backups')).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    if ($fullPath -ne $backupRoot -and -not $fullPath.StartsWith(
            $backupRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase
        )) {
        throw 'M8 solo permite almacenar backups dentro de backups del repositorio.'
    }
    return $fullPath
}

function Invoke-ExternalTool {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments
    )

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

function Invoke-PsqlOneRow {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][hashtable]$Settings,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][string]$Operation
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable(
            'PGPASSWORD',
            $Settings['SPRING_FLYWAY_PASSWORD'],
            'Process'
        )
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 `
            -U rf_migrator -d $developmentDatabase -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL rechazo $Operation."
        }
        $rows = @($output | Where-Object {
                $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_)
            })
        if ($rows.Count -ne 1) {
            throw "M8 no obtuvo una unica fila para $Operation."
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Get-DataManifest {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][hashtable]$Settings
    )

    return Invoke-PsqlOneRow -Psql $Psql -Settings $Settings `
        -Operation 'el manifiesto de desarrollo' -Query @'
SELECT
    (SELECT COALESCE(string_agg(version::text, ',' ORDER BY installed_rank), '')
       FROM flyway_schema_history WHERE success AND version IS NOT NULL),
    (SELECT count(*) FROM organization),
    (SELECT count(*) FROM app_user),
    (SELECT count(*) FROM transport_group),
    (SELECT count(*) FROM driver),
    (SELECT count(*) FROM vehicle),
    (SELECT count(*) FROM assignment),
    (SELECT count(*) FROM incident),
    (SELECT count(*) FROM announcement),
    (SELECT count(*) FROM audit_event);
'@
}

$expectedMigrationNames = @(
    'V1__identity_organization_audit.sql',
    'V2__fleet_administration.sql',
    'V3__operation_assignments_incidents_announcements.sql',
    'V4__remove_mobile_only_driver_status.sql',
    'V5__assignment_overlap_exclusion_constraints.sql',
    'V6__unify_administrative_roles_to_admin.sql',
    'V7__mobile_assignment_workflow.sql',
    'V8__mobile_current_location.sql',
    'V9__mobile_incidents_and_announcement_receipts.sql',
    'V10__mobile_command_receipts.sql',
    'V11__driver_closed_mobile_incidents.sql',
    'V12__mobile_offline_availability_receipts.sql'
)
foreach ($requiredPath in @($nativeImportScript, $runnerSource)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "M8 requiere el artefacto: $requiredPath"
    }
}
$actualMigrationNames = @(Get-ChildItem -LiteralPath $migrationDirectory -File |
        Sort-Object { [int]([regex]::Match($_.Name, '^V(\d+)__').Groups[1].Value) }, Name |
        Select-Object -ExpandProperty Name)
if (($actualMigrationNames -join '|') -ne ($expectedMigrationNames -join '|')) {
    throw 'M8 requiere exactamente Flyway V1-V12, sin migraciones no revisadas.'
}

Assert-RuntimeStopped
Assert-PrivateConfig -Path $ConfigPath
$backupDirectory = Assert-BackupDirectory -Path $BackupDirectory
$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope All
if ($null -eq $settings -or
    $settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $settings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'La configuracion nativa no autoriza la migracion M8.'
}

$psql = Get-PgToolPath -Name 'psql'
$pgDump = Get-PgToolPath -Name 'pg_dump'
$pgRestore = Get-PgToolPath -Name 'pg_restore'
$javac = Get-Command javac.exe -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue
$java = Get-Command java.exe -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue
if (-not $javac -or -not $java) {
    throw 'M8 requiere Java 21 (java y javac) para ejecutar Flyway protegido.'
}

$beforeVersions = '1,2,3,4,5,6,7,8,9,10,11'
$afterVersions = '1,2,3,4,5,6,7,8,9,10,11,12'
$sourceBefore = Get-DataManifest -Psql $psql -Settings $settings
if ($sourceBefore.StartsWith($afterVersions + '|', [StringComparison]::Ordinal)) {
    $physical = Invoke-PsqlOneRow -Psql $psql -Settings $settings `
        -Operation 'el contrato fisico V12 existente' -Query @'
SELECT
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_type'
        AND pg_get_constraintdef(oid) LIKE '%AVAILABILITY_CHANGE%'),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_assignment_scope');
'@
    if ($physical -ne 't|t') {
        throw 'V12 figura aplicada, pero no satisface sus restricciones fisicas.'
    }
    Write-Output 'M8_V12_MIGRATION=PASS state=already_applied flyway=V1-V12 receipt_scope=PASS'
    return
}
if (-not $sourceBefore.StartsWith($beforeVersions + '|', [StringComparison]::Ordinal)) {
    throw 'M8 requiere desarrollo exactamente en V1-V11 antes de aplicar V12.'
}

New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$backupPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_m8_v11.dump')
$partialBackupPath = $backupPath + '.partial'
$manifestPath = Join-Path $backupDirectory ($developmentDatabase + '_' + $timestamp + '_m8-v12-migration.json')
foreach ($path in @($backupPath, $partialBackupPath, $manifestPath)) {
    if (Test-Path -LiteralPath $path) {
        throw "M8 se niega a sobrescribir evidencia existente: $path"
    }
}

$previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
$previousEnvironment = @{}
try {
    [Environment]::SetEnvironmentVariable(
        'PGPASSWORD',
        $settings['SPRING_FLYWAY_PASSWORD'],
        'Process'
    )
    $dump = Invoke-ExternalTool -Executable $pgDump -Arguments @(
        '--format=custom', '--no-owner', '--no-privileges', '--verbose',
        '--host=127.0.0.1', '--port=5432', '--username=rf_migrator',
        ('--file=' + $partialBackupPath), $developmentDatabase
    )
    if ($dump.ExitCode -ne 0 -or
        -not (Test-Path -LiteralPath $partialBackupPath -PathType Leaf) -or
        (Get-Item -LiteralPath $partialBackupPath).Length -le 0) {
        throw 'pg_dump no pudo crear el backup previo V12 de M8.'
    }
    $dumpList = Invoke-ExternalTool -Executable $pgRestore -Arguments @('--list', $partialBackupPath)
    if ($dumpList.ExitCode -ne 0 -or
        @($dumpList.Output | Where-Object { $_ -match 'TABLE.*public mobile_command_receipt' }).Count -lt 1) {
        throw 'pg_restore no pudo validar el backup M8 de mobile_command_receipt.'
    }
    Move-Item -LiteralPath $partialBackupPath -Destination $backupPath

    foreach ($key in @('SPRING_FLYWAY_URL', 'SPRING_FLYWAY_USERNAME', 'SPRING_FLYWAY_PASSWORD')) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\m8-flyway-classpath.txt'
    $runnerOutput = Join-Path $backendRoot 'target\m8-flyway-runner'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath `
            "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo preparar el classpath Flyway de M8.'
        }
        $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($classpath)) {
            throw 'El classpath Flyway de M8 esta vacio.'
        }
        New-Item -ItemType Directory -Path $runnerOutput -Force | Out-Null
        & $javac --class-path "$classpath;src\main\resources" -d $runnerOutput '..\scripts\RunRutaFijaM8Flyway.java'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo compilar el runner Flyway de M8.'
        }
        & $java --class-path "$classpath;src\main\resources;$runnerOutput" RunRutaFijaM8Flyway
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aprobo V12 sobre desarrollo local.'
        }
    }
    finally {
        Pop-Location
    }

    $physical = Invoke-PsqlOneRow -Psql $psql -Settings $settings `
        -Operation 'el contrato fisico V12' -Query @'
SELECT
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank)
       FROM flyway_schema_history WHERE success AND version IS NOT NULL),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_type'
        AND pg_get_constraintdef(oid) LIKE '%AVAILABILITY_CHANGE%'),
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_mobile_command_receipt_assignment_scope');
'@
    if ($physical -ne ($afterVersions + '|t|t')) {
        throw 'Desarrollo no satisface el contrato fisico M8 V12.'
    }
    $sourceAfter = Get-DataManifest -Psql $psql -Settings $settings
    $separator = $sourceBefore.IndexOf('|')
    if ($separator -lt 0 -or
        $sourceAfter -ne ($afterVersions + '|' + $sourceBefore.Substring($separator + 1))) {
        throw 'M8 no preservo el manifiesto de datos al aplicar V12.'
    }

    $evidence = [ordered]@{
        schemaVersion = 1
        phase = 'M8'
        completedAtUtc = [DateTime]::UtcNow.ToString('o')
        sourceDatabase = $developmentDatabase
        backup = ('backups/m8-v12/' + [IO.Path]::GetFileName($backupPath))
        backupSha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash
        manifestBefore = $sourceBefore
        manifestAfter = $sourceAfter
        migration = 'V12__mobile_offline_availability_receipts.sql'
        physicalProbe = $physical
    }
    [IO.File]::WriteAllText(
        $manifestPath,
        (($evidence | ConvertTo-Json -Depth 4) + [Environment]::NewLine),
        [Text.UTF8Encoding]::new($false)
    )
    Write-Output "M8_V12_MIGRATION=PASS backup=$([IO.Path]::GetFileName($backupPath)) evidence=$([IO.Path]::GetFileName($manifestPath)) flyway=V1-V12 receipt_scope=PASS"
}
finally {
    foreach ($key in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
}
