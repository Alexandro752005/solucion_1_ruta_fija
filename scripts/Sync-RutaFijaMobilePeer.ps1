[CmdletBinding()]
param([switch]$Execute, [switch]$FunctionsOnly)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RutaFijaAdbExecutable {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    $candidates = @('D:/Android/Sdk/platform-tools/adb.exe')
    foreach ($name in @('ANDROID_SDK_ROOT', 'ANDROID_HOME')) {
        $sdk = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($sdk) { $candidates += Join-Path $sdk 'platform-tools/adb.exe' }
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    return $null
}

function Get-RutaFijaPhoneAddress {
    param([string[]]$DeviceRows, [string[]]$AddressRows, [string]$BindIpv4)
    $devices = @($DeviceRows | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
    if ($devices.Count -ne 1) { throw 'MOBILE_PEER_REQUIRES_ONE_AUTHORIZED_ADB_DEVICE' }
    $addresses = @([regex]::Matches(($AddressRows -join "`n"), '\binet\s+(\d+\.\d+\.\d+\.\d+)/\d+') |
        ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    if ($addresses.Count -ne 1) { throw 'MOBILE_PEER_REQUIRES_ONE_WIFI_IPV4' }
    $phone = $addresses[0]
    $parsed = $null
    if (-not [Net.IPAddress]::TryParse($phone, [ref]$parsed) -or
        $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'MOBILE_PEER_WIFI_IPV4_INVALID'
    }
    $bytes = $parsed.GetAddressBytes()
    $private = $bytes[0] -eq 10 -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
    if (-not $private -or $phone -eq $BindIpv4 -or
        (($phone -split '\.')[0..2] -join '.') -ne (($BindIpv4 -split '\.')[0..2] -join '.')) {
        throw 'MOBILE_PEER_REQUIRES_DISTINCT_PRIVATE_SAME_SUBNET_WIFI'
    }
    return $phone
}

function Sync-RutaFijaConnectedPhone {
    $repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
    $privateConfig = Join-Path $repositoryRoot 'backend/.local/ruta-fija-mobile-bridge.json'
    if (-not (Test-Path -LiteralPath $privateConfig)) {
        Write-Output 'MOBILE_PEER=NO_PRIVATE_BRIDGE_CONFIGURATION'; return
    }
    $config = Get-Content -LiteralPath $privateConfig -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($config.schemaVersion -ne 1 -or $config.enabled -isnot [bool] -or $config.port -ne 4311 -or
        $config.apiBaseUrl -cne ('http://' + $config.bindIpv4 + ':4311/api/v1')) {
        throw 'MOBILE_PEER_BRIDGE_CONFIGURATION_INVALID'
    }
    if (-not $config.enabled) { Write-Output 'MOBILE_PEER=DISABLED_BY_PRIVATE_CONFIGURATION'; return }
    $adb = Get-RutaFijaAdbExecutable
    if (-not $adb) { Write-Output 'MOBILE_PEER=ADB_UNAVAILABLE_PRIVATE_CONFIGURATION_UNCHANGED'; return }
    $rows = @(& $adb devices)
    if ($LASTEXITCODE -ne 0) { throw 'MOBILE_PEER_ADB_DEVICE_QUERY_FAILED' }
    $devices = @($rows | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
    if ($devices.Count -eq 0) {
        if (@($rows | Where-Object { $_ -match '\s+(unauthorized|offline)\s*$' }).Count) {
            throw 'MOBILE_PEER_AUTHORIZE_OR_RECONNECT_DEVICE_ON_PHONE'
        }
        Write-Output 'MOBILE_PEER=NO_ADB_DEVICE_PRIVATE_CONFIGURATION_UNCHANGED'; return
    }
    if ($devices.Count -ne 1) { throw 'MOBILE_PEER_REQUIRES_ONE_AUTHORIZED_ADB_DEVICE' }
    $serial = ($devices[0] -split '\s+')[0]
    $addressRows = @(& $adb -s $serial shell ip -o -4 addr show wlan0)
    if ($LASTEXITCODE -ne 0) { throw 'MOBILE_PEER_WIFI_QUERY_FAILED' }
    $phone = Get-RutaFijaPhoneAddress -DeviceRows $devices -AddressRows $addressRows -BindIpv4 $config.bindIpv4
    if ($phone -ceq $config.phoneIpv4) { Write-Output 'MOBILE_PEER=MATCH_AUTHORIZED_DEVICE'; return }

    # Stop only the bridge whose persisted instance identity is verified by its owner script.
    & (Join-Path $PSScriptRoot 'Invoke-RutaFijaMobileLocalBridge.ps1') -Action Stop -Execute
    $backup = Join-Path (Split-Path -Parent $privateConfig) ('ruta-fija-mobile-bridge.before-peer-' + [Guid]::NewGuid().ToString('N') + '.json')
    Copy-Item -LiteralPath $privateConfig -Destination $backup -ErrorAction Stop
    $config.phoneIpv4 = $phone
    $pending = $privateConfig + '.pending-' + [Guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllText($pending, ($config | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    # Passing $null to this .NET string argument becomes an invalid empty path
    # in Windows PowerShell 5.1; the explicit backup path also preserves recovery.
    [IO.File]::Replace($pending, $privateConfig, $backup)
    Write-Output 'MOBILE_PEER=UPDATED_FROM_AUTHORIZED_DEVICE_PRIVATE_BACKUP_SAVED'
}

if ($FunctionsOnly) { return }
if (-not $Execute) { Write-Output 'MOBILE_PEER=PLAN_NO_ADB_FILES_OR_PROCESSES'; return }
Sync-RutaFijaConnectedPhone
