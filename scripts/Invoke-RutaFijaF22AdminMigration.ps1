[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$expectedV6Hash = '9BE06C6D0267E055E4DB75C16AA15774F6DAD7CBDFC0C69D0282F1AAC6F8B622'
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$runnerSource = Join-Path $PSScriptRoot 'RunRutaFijaF22Flyway.java'
$activeV6 = Join-Path $repoRoot 'backend\src\main\resources\db\migration\V6__unify_administrative_roles_to_admin.sql'
$candidateV6 = Join-Path $repoRoot 'scripts\flyway\f2-1b\V6__unify_administrative_roles_to_admin.sql'
$backupRoot = Join-Path $repoRoot 'backups\f2-1a'

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontró psql.exe de PostgreSQL 16.'
    }
    return $psql
}

function Test-SecretPathIsPrivate {
    param([Parameter(Mandatory)][string]$Path)

    $repositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $secretPath = [IO.Path]::GetFullPath($Path)
    $prefix = $repositoryPath + [IO.Path]::DirectorySeparatorChar
    if (-not $secretPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'La configuración privada debe permanecer dentro del repositorio local.'
    }
    $relativePath = $secretPath.Substring($prefix.Length).Replace('\', '/')
    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relativePath
        if ($LASTEXITCODE -ne 0) {
            throw "La configuración privada no está ignorada por Git: $relativePath"
        }
        if (@(& git ls-files -- $relativePath).Count -gt 0) {
            throw 'La configuración privada no puede estar versionada.'
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
        throw 'F2.2 exige API y CRM detenidos antes de aplicar V6.'
    }
}

function Invoke-PostgresQuery {
    param(
        [Parameter(Mandatory)][string]$Psql,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Settings
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Settings['SPRING_FLYWAY_PASSWORD'], 'Process')
        $output = & $Psql -X -w -v 'ON_ERROR_STOP=1' -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazó una comprobación protegida de F2.2.'
        }
        $rows = @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
        if ($rows.Count -ne 1) {
            throw 'F2.2 requiere una única fila de evidencia PostgreSQL.'
        }
        return $rows[0].Trim()
    }
    finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
}

function Assert-CertifiedV6 {
    foreach ($path in @($candidateV6, $activeV6)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw 'No existe la migración V6 certificada requerida por F2.2.'
        }
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($hash -ne $expectedV6Hash) {
            throw 'La migración V6 no conserva la huella certificada de F2.1B.'
        }
    }
}

function Assert-PreV6Backup {
    $dumps = @(Get-ChildItem -LiteralPath $backupRoot -File -Filter '*_f21a.dump' -ErrorAction Stop)
    if ($dumps.Count -ne 1 -or $dumps[0].Length -le 0) {
        throw 'F2.2 requiere exactamente un respaldo F2.1A no vacío antes de V6.'
    }
    return $dumps[0]
}

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
if ($null -eq $settings) {
    throw 'No se pudo leer la configuración nativa local.'
}
if ($settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['APP_SEED_ENABLED'] -ne 'false') {
    throw 'La configuración local no autoriza la migración protegida F2.2.'
}

Test-SecretPathIsPrivate -Path $ConfigPath
Assert-RuntimeStopped
Assert-CertifiedV6
$backup = Assert-PreV6Backup
$psql = Get-PsqlPath

$before = Invoke-PostgresQuery -Psql $psql -Settings $settings -Query @'
SELECT
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank)
       FROM flyway_schema_history
      WHERE success),
    (SELECT count(*) FROM app_user WHERE role IN ('ADMINISTRADOR', 'COORDINADOR')),
    (SELECT count(*) FROM app_user),
    (SELECT count(*) FROM group_coordinator);
'@
if ($before -ne '1,2,3,4,5|0|0|0') {
    throw 'F2.2 solo admite la línea base certificada V1-V5 sin datos de negocio.'
}

$previousEnvironment = @{}
try {
    foreach ($entry in $settings.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }

    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target\f2-2-flyway-classpath.txt'
    Push-Location $backendRoot
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo preparar el classpath de Flyway para F2.2.'
        }
        $classpath = (Get-Content -LiteralPath $classpathFile -Raw -Encoding UTF8).Trim()
        if ([string]::IsNullOrWhiteSpace($classpath)) {
            throw 'El classpath de Flyway para F2.2 está vacío.'
        }
        & java --class-path "$classpath;src\main\resources" '..\scripts\RunRutaFijaF22Flyway.java'
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no aplicó V6 de forma aprobada en F2.2.'
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

$after = Invoke-PostgresQuery -Psql $psql -Settings $settings -Query @'
SELECT
    (SELECT string_agg(version::text, ',' ORDER BY installed_rank)
       FROM flyway_schema_history
      WHERE success),
    (SELECT COALESCE(string_agg(role || ':' || total::text, ',' ORDER BY role), 'SIN_USUARIOS')
       FROM (SELECT role, count(*) AS total FROM app_user GROUP BY role) role_counts),
    (SELECT count(*) FROM app_user WHERE role IN ('ADMINISTRADOR', 'COORDINADOR')),
    (SELECT count(*) FROM group_coordinator),
    (SELECT POSITION('ADMINISTRADOR' IN pg_get_constraintdef(oid)) = 0
            AND POSITION('COORDINADOR' IN pg_get_constraintdef(oid)) = 0
            AND POSITION('ADMIN' IN pg_get_constraintdef(oid)) > 0
       FROM pg_constraint
      WHERE conname = 'ck_app_user_role');
'@
if ($after -ne '1,2,3,4,5,6|SIN_USUARIOS|0|0|t') {
    throw 'La evidencia posterior a V6 no cumple el contrato ADMIN de F2.2.'
}

$backupHash = (Get-FileHash -LiteralPath $backup.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "F2_2_ADMIN_MIGRATION=PASS backup=$($backup.Name) sha256=$backupHash flyway=V1-V6 roles=SUPER_ADMIN,ADMIN,CONDUCTOR group_coordinator=history docker=0"
