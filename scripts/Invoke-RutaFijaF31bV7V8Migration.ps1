[CmdletBinding()]
param(
    [string]$ConfigPath,
    [string]$RehearsalManifestPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$runnerSource = Join-Path $PSScriptRoot 'RunRutaFijaF31bFlyway.java'
$grantScript = Join-Path $PSScriptRoot 'Grant-RutaFijaF31bApplicationPrivileges.ps1'
$schemaAuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF31bMobileSchema.ps1'
$candidateDirectory = Join-Path $repoRoot 'scripts\flyway\f3-1b'
$activeMigrationDirectory = Join-Path $repoRoot 'backend\src\main\resources\db\migration'
$candidateV7 = Join-Path $candidateDirectory 'V7__mobile_assignment_workflow.sql'
$candidateV8 = Join-Path $candidateDirectory 'V8__mobile_current_location.sql'
$activeV7 = Join-Path $activeMigrationDirectory 'V7__mobile_assignment_workflow.sql'
$activeV8 = Join-Path $activeMigrationDirectory 'V8__mobile_current_location.sql'
$rehearsalRoot = Join-Path $repoRoot 'backups\f3-1b'
$expectedV7Hash = '2AC09AE19D515E4E35E0DF9D8CB06F7FE9A4D955B824908A269FD2EA012ED357'
$expectedV8Hash = 'C1D969DECD45CC9C6EC441A79F8B516A65A5D512BD38F9174D32F04A79750AA1'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Assert-PathExists {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "F3.1B no encontro el artefacto requerido: $Path"
    }
}

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontro psql de PostgreSQL 16.'
    }
    return $psql
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
        throw 'F3.1B exige API y CRM detenidos antes de aplicar V7/V8.'
    }
}

function Invoke-PostgresQuery {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)]$Settings
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Settings['SPRING_FLYWAY_PASSWORD'], 'Process')
        $output = & $Psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazo una comprobacion protegida de F3.1B.'
        }
        $rows = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
        if ($rows.Count -ne 1) {
            throw 'F3.1B requiere una unica fila de evidencia PostgreSQL.'
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Get-SourceManifest {
    param([Parameter(Mandatory)][string]$Psql, [Parameter(Mandatory)]$Settings)

    return Invoke-PostgresQuery -Psql $Psql -Settings $Settings -Query @'
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
'@
}

function Resolve-RehearsalManifest {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        $candidates = @(Get-ChildItem -LiteralPath $rehearsalRoot -File -Filter '*_f31b-rehearsal.json' -ErrorAction Stop)
        if ($candidates.Count -ne 1) {
            throw 'F3.1B requiere exactamente un manifiesto de ensayo o RehearsalManifestPath explicito.'
        }
        $Path = $candidates[0].FullName
    }
    $root = [IO.Path]::GetFullPath($rehearsalRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw 'F3.1B solo acepta un manifiesto dentro de backups/f3-1b.'
    }
    try {
        return [pscustomobject]@{ Path = $fullPath; Value = (Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop) }
    }
    catch {
        throw 'El manifiesto de ensayo F3.1B no es JSON valido.'
    }
}

foreach ($path in @($candidateV7, $candidateV8, $activeV7, $activeV8, $runnerSource, $grantScript, $schemaAuditScript)) {
    Assert-PathExists -Path $path
}
if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw 'F3.1B requiere la configuracion nativa privada.'
}

Assert-RuntimeStopped
Test-SecretPathIsPrivate -Path $ConfigPath
$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
if ($null -eq $settings -or
    $settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $settings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'La configuracion local no autoriza la migracion protegida F3.1B.'
}

$manifestRecord = Resolve-RehearsalManifest -Path $RehearsalManifestPath
$manifest = $manifestRecord.Value
foreach ($property in @('schemaVersion', 'phase', 'sourceDatabase', 'recoveryDatabase', 'sourceBackup', 'sourceBackupSha256', 'sourceDataManifest', 'v7Sha256', 'v8Sha256')) {
    if ($null -eq $manifest.PSObject.Properties[$property] -or [string]::IsNullOrWhiteSpace([string]$manifest.$property)) {
        throw "El manifiesto F3.1B no contiene $property."
    }
}
if ([int]$manifest.schemaVersion -ne 1 -or $manifest.phase -ne 'F3.1B' -or
    $manifest.sourceDatabase -ne $developmentDatabase -or
    $manifest.recoveryDatabase -notmatch '^ruta_fija_recovery_[0-9]{8}_f31b(_r[1-9][0-9]*)?$') {
    throw 'El manifiesto F3.1B no corresponde al ensayo autorizado.'
}
if ($manifest.sourceBackup -notmatch '^backups/f3-1b/[^/]+_f31b_v6\.dump$') {
    throw 'El manifiesto F3.1B no referencia un backup pre-V7/V8 permitido.'
}
$backupPath = Join-Path $repoRoot ($manifest.sourceBackup.Replace('/', '\'))
if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf) -or
    (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sourceBackupSha256.ToLowerInvariant()) {
    throw 'El backup certificado por el ensayo F3.1B no existe o no conserva su huella.'
}

foreach ($entry in @(
    [pscustomobject]@{ Candidate = $candidateV7; Active = $activeV7; Expected = $expectedV7Hash; Manifest = $manifest.v7Sha256 },
    [pscustomobject]@{ Candidate = $candidateV8; Active = $activeV8; Expected = $expectedV8Hash; Manifest = $manifest.v8Sha256 }
)) {
    $candidateHash = (Get-FileHash -LiteralPath $entry.Candidate -Algorithm SHA256).Hash.ToUpperInvariant()
    $activeHash = (Get-FileHash -LiteralPath $entry.Active -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($candidateHash -ne $entry.Expected -or $activeHash -ne $entry.Expected -or
        $entry.Manifest.ToUpperInvariant() -ne $entry.Expected) {
        throw 'La candidata, la copia activa y la evidencia de ensayo no conservan la misma huella SHA-256.'
    }
}

$expectedMigrationNames = @(
    'V1__identity_organization_audit.sql',
    'V2__fleet_administration.sql',
    'V3__operation_assignments_incidents_announcements.sql',
    'V4__remove_mobile_only_driver_status.sql',
    'V5__assignment_overlap_exclusion_constraints.sql',
    'V6__unify_administrative_roles_to_admin.sql',
    'V7__mobile_assignment_workflow.sql',
    'V8__mobile_current_location.sql'
)
$actualMigrationNames = @(Get-ChildItem -LiteralPath $activeMigrationDirectory -File | Sort-Object Name | Select-Object -ExpandProperty Name)
if (($actualMigrationNames -join '|') -ne ($expectedMigrationNames -join '|')) {
    throw 'El classpath activo no contiene exactamente V1-V8 para F3.1B.'
}

$psql = Get-PsqlPath
$sourceBefore = Get-SourceManifest -Psql $psql -Settings $settings
if ($sourceBefore -ne [string]$manifest.sourceDataManifest) {
    throw 'Desarrollo cambio desde el ensayo F3.1B; la aplicacion fue cancelada.'
}

$previousEnvironment = @{}
try {
    foreach ($entry in $settings.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f3-1b-flyway-classpath.txt'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo preparar el classpath Flyway para aplicar F3.1B.'
        }
        $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($classpath)) {
            throw 'El classpath Flyway de aplicacion F3.1B esta vacio.'
        }
        & java --class-path "$classpath;src\main\resources" '..\scripts\RunRutaFijaF31bFlyway.java'
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aplico V7/V8 de forma aprobada en F3.1B.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($key in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
}

& $grantScript -ConfigPath $ConfigPath
if (-not $?) {
    throw 'Los privilegios de ubicacion F3.1B no fueron aprobados.'
}
& $schemaAuditScript -ConfigPath $ConfigPath
if (-not $?) {
    throw 'La auditoria de esquema V1-V8 no fue aprobada despues de F3.1B.'
}

$sourceAfter = Get-SourceManifest -Psql $psql -Settings $settings
$separator = $sourceBefore.IndexOf('|')
if (-not $sourceAfter.StartsWith('1,2,3,4,5,6,7,8|', [StringComparison]::Ordinal) -or
    $separator -lt 0 -or
    $sourceAfter.Substring($sourceAfter.IndexOf('|') + 1) -ne $sourceBefore.Substring($separator + 1)) {
    throw 'F3.1B no preservo el manifiesto de datos al aplicar V7/V8.'
}

Write-Output "F3_1B_MIGRATION=PASS manifest=$([IO.Path]::GetFileName($manifestRecord.Path)) backup=$([IO.Path]::GetFileName($backupPath)) flyway=V1-V8 location=current_only docker=0"
