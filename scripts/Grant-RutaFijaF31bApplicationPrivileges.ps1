[CmdletBinding()]
param(
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$developmentDatabase = 'solucion_ruta_fija_1'
$developmentUrl = "jdbc:postgresql://127.0.0.1:5432/$developmentDatabase"
$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$grantScript = Join-Path $PSScriptRoot 'sql\F3_1B_GrantCurrentLocationPrivileges.sql'

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
        throw 'No se encontro psql de PostgreSQL 16.'
    }
    return $psql
}

if (-not (Test-Path -LiteralPath $grantScript -PathType Leaf)) {
    throw 'No existe el script de privilegios de F3.1B.'
}

$settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
if ($null -eq $settings -or
    $settings['SPRING_PROFILES_ACTIVE'] -ne 'local' -or
    $settings['SPRING_DATASOURCE_URL'] -ne $developmentUrl -or
    $settings['SPRING_DATASOURCE_USERNAME'] -ne 'rf_app' -or
    $settings['SPRING_FLYWAY_URL'] -ne $developmentUrl -or
    $settings['SPRING_FLYWAY_USERNAME'] -ne 'rf_migrator') {
    throw 'La configuracion local no autoriza los privilegios F3.1B.'
}

$psql = Get-PsqlPath
$previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $settings['SPRING_FLYWAY_PASSWORD'], 'Process')
    $state = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c @'
SELECT (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'),
       (SELECT string_agg(version::text, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success),
       to_regclass('public.driver_current_location') IS NOT NULL;
'@
    if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne '13|1,2,3,4,5,6,7,8|t') {
        throw 'El esquema no esta exactamente en V1-V8 antes de otorgar DML de ubicacion.'
    }

    & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -f $grantScript
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo los privilegios de ubicacion F3.1B.'
    }

    $privileges = & $psql -X -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U rf_migrator -d $developmentDatabase -At -F '|' -c @'
SELECT has_table_privilege('rf_app', 'public.driver_current_location', 'SELECT')
   AND has_table_privilege('rf_app', 'public.driver_current_location', 'INSERT')
   AND has_table_privilege('rf_app', 'public.driver_current_location', 'UPDATE')
   AND has_table_privilege('rf_app', 'public.driver_current_location', 'DELETE'),
       has_schema_privilege('rf_app', 'public', 'CREATE');
'@
    if ($LASTEXITCODE -ne 0 -or $privileges.Trim() -ne 't|f') {
        throw 'rf_app no conserva DML selectivo y sin DDL para ubicacion vigente.'
    }
    Write-Output 'F3_1B_LOCATION_GRANTS=PASS rf_app=DML_current_location_without_DDL'
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
}
