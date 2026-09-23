[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Verify', 'Clean')]
    [string]$Action = 'Verify',
    [string]$ConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

$importScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$mavenWrapper = Join-Path $repoRoot 'backend\mvnw.cmd'
$f33AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaF33MobileOperations.ps1'

function Get-PsqlPath {
    $command = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $postgres16 = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
    if (Test-Path -LiteralPath $postgres16 -PathType Leaf) {
        return $postgres16
    }

    throw 'No se encontró psql.exe de PostgreSQL 16 para verificar ruta_fija_test.'
}

function Import-TestSettings {
    $settings = & $importScript -ConfigPath $ConfigPath -PassThru -Scope Test
    if ($null -eq $settings) {
        throw 'No se pudo leer la configuración local de pruebas.'
    }

    foreach ($entry in $settings.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    return $settings
}

function Invoke-TestPsql {
    param(
        [Parameter(Mandatory)]
        [string]$Sql,
        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$Settings
    )

    $psql = Get-PsqlPath
    $previousPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Settings['SPRING_FLYWAY_PASSWORD']
        $arguments = @(
            '-X',
            '-v', 'ON_ERROR_STOP=1',
            '-At',
            '-h', '127.0.0.1',
            '-p', '5432',
            '-U', 'rf_migrator',
            '-d', 'ruta_fija_test',
            '-c', $Sql
        )
        $output = & $psql @arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'PostgreSQL rechazó la operación protegida de F1.4.'
        }
        return @($output)
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:PGPASSWORD = $previousPassword
        }
    }
}

function Assert-TestDatabase {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Settings)

    $result = Invoke-TestPsql -Settings $Settings -Sql @'
select current_database() || '|' || current_user || '|' || current_setting('server_version_num');
'@
    $line = @($result | Where-Object { $_ -match '^ruta_fija_test\|rf_migrator\|16' })[0]
    if ([string]::IsNullOrWhiteSpace($line)) {
        throw 'F1.4 no confirmó ruta_fija_test, rf_migrator y PostgreSQL 16.'
    }
}

function Clear-TestData {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Settings)

    $null = Invoke-TestPsql -Settings $Settings -Sql @'
DO $cleanup$
DECLARE
    target_tables text;
BEGIN
    IF current_database() <> 'ruta_fija_test' THEN
        RAISE EXCEPTION 'La limpieza F1.4 solo permite ruta_fija_test';
    END IF;

    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)
    INTO target_tables
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename <> 'flyway_schema_history';

    IF target_tables IS NOT NULL THEN
        EXECUTE 'TRUNCATE TABLE ' || target_tables || ' RESTART IDENTITY CASCADE';
    END IF;
END
$cleanup$;
'@
}

$settings = Import-TestSettings
Assert-TestDatabase -Settings $settings

switch ($Action) {
    'Preflight' {
        Write-Output 'F1_4_PREFLIGHT=PASS database=ruta_fija_test postgresql=16 docker=0'
    }
    'Clean' {
        Clear-TestData -Settings $settings
        Write-Output 'F1_4_CLEAN=PASS database=ruta_fija_test'
    }
    'Verify' {
        try {
            $jacocoExecutionData = Join-Path (Split-Path -Parent $mavenWrapper) 'target\jacoco.exec'
            if (Test-Path -LiteralPath $jacocoExecutionData -PathType Leaf) {
                # Generated coverage data from an earlier class version can corrupt only the report.
                # The exact file is inside backend/target; source code and database data are untouched.
                Remove-Item -LiteralPath $jacocoExecutionData -Force
            }
            Push-Location (Split-Path -Parent $mavenWrapper)
            try {
                & $mavenWrapper --batch-mode --no-transfer-progress verify
                if ($LASTEXITCODE -ne 0) {
                    throw 'mvn verify no superó las pruebas nativas F1.4.'
                }
            }
            finally {
                Pop-Location
            }

            $migration = Invoke-TestPsql -Settings $settings -Sql @'
select coalesce(max(version), '') from flyway_schema_history where success = true;
'@
            if (@($migration | Where-Object { $_ -eq '10' }).Count -ne 1) {
                throw 'F3.3 no confirmo Flyway V1-V10 en ruta_fija_test.'
            }
            & $f33AuditScript -ConfigPath $ConfigPath
            Write-Output 'F3_3_NATIVE_VERIFY=PASS flyway=V1-V10 mobile_operations=PASS docker=0'
        }
        finally {
            Clear-TestData -Settings $settings
        }
    }
}
