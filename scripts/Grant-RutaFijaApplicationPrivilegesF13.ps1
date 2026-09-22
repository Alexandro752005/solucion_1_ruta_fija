[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$grantScript = Join-Path $PSScriptRoot 'sql\F1_3_GrantDevelopmentApplicationPrivileges.sql'

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

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
if ($null -eq $settings -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuracion F1.3 no autoriza cambios de privilegio.'
}
if (-not (Test-Path -LiteralPath $grantScript -PathType Leaf)) {
    throw 'No existe el script SQL de privilegios F1.3.'
}

$psql = Get-PsqlPath
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $settings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $schemaState = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c "SELECT (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'), (SELECT count(*) FROM flyway_schema_history WHERE success), (SELECT count(*) FROM flyway_schema_history);"
    if ($LASTEXITCODE -ne 0 -or $schemaState.Trim() -ne '13|8|8') {
        throw 'El esquema no esta en el estado exacto requerido antes de otorgar DML.'
    }

    & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -f $grantScript
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo los privilegios selectivos de F1.3.'
    }

    Write-Output 'F1_3_DML_GRANTS=PASS'
    Write-Output 'rf_app recibe DML sobre tablas de negocio; flyway_schema_history permanece protegida.'
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}
