[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status')]
    [string]$Action = 'Status',

    [switch]$Elevated
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$serviceName = 'postgresql-x64-16'

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-PostgresqlService {
    $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if ($null -eq $service) {
        throw "No se encontro el servicio $serviceName. Verifique PostgreSQL 16."
    }
    return $service
}

function Invoke-ElevatedAction {
    $scriptArgument = '"{0}"' -f $PSCommandPath
    $arguments = @(
        '-NoLogo'
        '-NoProfile'
        '-ExecutionPolicy'
        'Bypass'
        '-File'
        $scriptArgument
        '-Action'
        $Action
        '-Elevated'
    )

    try {
        $process = Start-Process `
            -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" `
            -ArgumentList $arguments `
            -Verb RunAs `
            -WindowStyle Hidden `
            -Wait `
            -PassThru
    } catch {
        throw 'Se cancelo o fallo la autorizacion de administrador para PostgreSQL.'
    }

    if ($process.ExitCode -ne 0) {
        throw "La administracion de PostgreSQL termino con codigo $($process.ExitCode)."
    }
}

function Wait-ServiceState {
    param(
        [Parameter(Mandatory)]
        [System.ServiceProcess.ServiceController]$Service,

        [Parameter(Mandatory)]
        [System.ServiceProcess.ServiceControllerStatus]$ExpectedStatus
    )

    $Service.WaitForStatus($ExpectedStatus, [TimeSpan]::FromSeconds(30))
    $Service.Refresh()
    if ($Service.Status -ne $ExpectedStatus) {
        throw "El servicio $serviceName no alcanzo el estado $ExpectedStatus."
    }
}

$service = Get-PostgresqlService

if ($Action -eq 'Status') {
    Write-Output "RF_POSTGRESQL_STATUS=$($service.Status) startup=$($service.StartType)"
    return
}

if (-not (Test-IsAdministrator)) {
    if ($Elevated) {
        throw 'La elevacion solicitada no obtuvo permisos de administrador.'
    }
    Invoke-ElevatedAction
    $service = Get-PostgresqlService
    $expected = if ($Action -eq 'Start') { 'Running' } else { 'Stopped' }
    if ([string]$service.Status -ne $expected) {
        throw "PostgreSQL no quedo en estado $expected."
    }
    Write-Output "RF_POSTGRESQL_$($Action.ToUpperInvariant())=PASS status=$($service.Status) startup=Manual"
    return
}

# Manual evita que PostgreSQL consuma memoria desde el arranque de Windows.
Set-Service -Name $serviceName -StartupType Manual

if ($Action -eq 'Start') {
    if ($service.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Running) {
        Start-Service -Name $serviceName
        $service = Get-PostgresqlService
        Wait-ServiceState -Service $service -ExpectedStatus Running
    }
    Write-Output 'RF_POSTGRESQL_START=PASS status=Running startup=Manual'
    return
}

if ($service.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Stopped) {
    Stop-Service -Name $serviceName -Force
    $service = Get-PostgresqlService
    Wait-ServiceState -Service $service -ExpectedStatus Stopped
}
Write-Output 'RF_POSTGRESQL_STOP=PASS status=Stopped startup=Manual'
