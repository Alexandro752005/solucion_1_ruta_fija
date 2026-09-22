[CmdletBinding()]
param(
    [string]$BootstrapConfigPath,
    [string]$ServiceName = 'postgresql-x64-16'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}

$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$expectedListenAddresses = '127.0.0.1,::1'

function Assert-WindowsAdministrator {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw 'F1.6 solo administra el servicio PostgreSQL local de Windows.'
    }

    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Abra PowerShell o VS Code como Administrador para reiniciar PostgreSQL y activar el loopback F1.6.'
    }
}

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }

    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontro psql.exe de PostgreSQL 16.'
    }
    return $psql
}

function Invoke-BootstrapSql {
    param(
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Settings,
        [Parameter(Mandatory)][string]$Psql
    )

    $arguments = @(
        '-X', '-w', '-v', 'ON_ERROR_STOP=1',
        '-h', $Settings['RUTA_FIJA_BOOTSTRAP_HOST'],
        '-p', $Settings['RUTA_FIJA_BOOTSTRAP_PORT'],
        '-U', $Settings['RUTA_FIJA_BOOTSTRAP_USERNAME'],
        '-d', 'postgres', '-At', '-c', $Query
    )
    $output = & $Psql @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo la configuracion de loopback F1.6.'
    }
    return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
}

Assert-WindowsAdministrator
if (-not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf)) {
    throw 'No existe el bootstrap privado requerido para F1.6.'
}

$service = Get-Service -Name $ServiceName -ErrorAction Stop
$settings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $settings) {
    throw 'No se pudo leer el bootstrap privado de PostgreSQL.'
}

$psql = Get-PsqlPath
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable(
        'PGPASSWORD',
        $settings['RUTA_FIJA_BOOTSTRAP_PASSWORD'],
        'Process'
    )

    $currentSetting = (@(Invoke-BootstrapSql -Psql $psql -Settings $settings -Query "
        SELECT current_setting('listen_addresses');
    "))[0].Trim()

    if ($currentSetting -ne $expectedListenAddresses) {
        $null = Invoke-BootstrapSql -Psql $psql -Settings $settings -Query "
            ALTER SYSTEM SET listen_addresses = '$expectedListenAddresses';
        "
    }

    Restart-Service -Name $service.Name -Force
    $service.WaitForStatus(
        [System.ServiceProcess.ServiceControllerStatus]::Running,
        [TimeSpan]::FromSeconds(30)
    )

    $verification = (@(Invoke-BootstrapSql -Psql $psql -Settings $settings -Query "
        SELECT current_setting('listen_addresses') || '|' ||
               current_setting('port') || '|' ||
               current_setting('password_encryption');
    "))[0].Trim()
    if ($verification -ne "$expectedListenAddresses|5432|scram-sha-256") {
        throw 'PostgreSQL no quedo restringido a loopback con SCRAM despues del reinicio F1.6.'
    }

    Write-Output 'F1_6_LOOPBACK=PASS listener=127.0.0.1,::1 port=5432 auth=scram-sha-256'
    Write-Output 'Rollback: ALTER SYSTEM RESET listen_addresses; luego reinicie el servicio PostgreSQL.'
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}
