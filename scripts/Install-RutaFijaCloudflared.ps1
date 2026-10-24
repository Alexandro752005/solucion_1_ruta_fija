[CmdletBinding()]
param(
    [string]$DestinationPath,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DestinationPath)) {
    $DestinationPath = Join-Path $repoRoot '.tools\cloudflared.exe'
}

$releaseUri = 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest'
$assetName = 'cloudflared-windows-amd64.exe'

function Get-CloudflaredVersion {
    param([Parameter(Mandatory)][string]$Executable)

    $version = & $Executable --version 2>&1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$version)) {
        throw 'El ejecutable cloudflared no respondió con una versión válida.'
    }
    return ([string]$version).Trim()
}

function Assert-CloudflareSignature {
    param([Parameter(Mandatory)][string]$Executable)

    $signature = Get-AuthenticodeSignature -FilePath $Executable
    if ($signature.Status -ne [Management.Automation.SignatureStatus]::Valid -or
        $null -eq $signature.SignerCertificate -or
        $signature.SignerCertificate.Subject -notmatch 'O=.Cloudflare, Inc\.') {
        throw 'La firma Authenticode del ejecutable no pertenece válidamente a Cloudflare, Inc.'
    }
}

$resolvedDestination = [IO.Path]::GetFullPath($DestinationPath)
if (Test-Path -LiteralPath $resolvedDestination -PathType Leaf) {
    if (-not $Force) {
        Assert-CloudflareSignature -Executable $resolvedDestination
        $actualHash = (Get-FileHash -LiteralPath $resolvedDestination -Algorithm SHA256).Hash.ToUpperInvariant()
        $version = Get-CloudflaredVersion -Executable $resolvedDestination
        Write-Output (
            'F5_2_CLOUDFLARED_INSTALL=PASS state=existing signature=VALID sha256={0} version={1}' -f
            $actualHash,
            $version
        )
        return
    }
}

$destinationDirectory = Split-Path -Parent $resolvedDestination
[IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
$downloadPath = $resolvedDestination + '.download'

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $release = Invoke-RestMethod -Uri $releaseUri -Headers @{ 'User-Agent' = 'Ruta-Fija-F5.2' }
    $asset = @($release.assets | Where-Object { $_.name -eq $assetName })
    if ($asset.Count -ne 1) {
        throw 'La versión oficial más reciente no contiene el ejecutable Windows amd64 esperado.'
    }
    $checksumPattern = '(?im)^\s*' + [regex]::Escape($assetName) + ':\s*([a-f0-9]{64})\s*$'
    if ([string]$release.body -notmatch $checksumPattern) {
        throw 'La versión oficial no publica un checksum SHA-256 reconocible para Windows amd64.'
    }
    $expectedHash = $Matches[1].ToUpperInvariant()
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($null -ne $curl) {
        & $curl.Source --fail --location --silent --show-error --retry 3 --output $downloadPath ([string]$asset[0].browser_download_url)
        if ($LASTEXITCODE -ne 0) {
            throw 'curl no pudo descargar el ejecutable oficial de cloudflared.'
        }
    } else {
        Invoke-WebRequest -Uri $asset[0].browser_download_url -OutFile $downloadPath -UseBasicParsing
    }
    $actualHash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($actualHash -ne $expectedHash) {
        throw 'El checksum SHA-256 de cloudflared no coincide con el publicado por Cloudflare.'
    }

    Move-Item -LiteralPath $downloadPath -Destination $resolvedDestination -Force
    Assert-CloudflareSignature -Executable $resolvedDestination
    $version = Get-CloudflaredVersion -Executable $resolvedDestination
    Write-Output (
        'F5_2_CLOUDFLARED_INSTALL=PASS state=downloaded signature=VALID sha256={0} version={1}' -f
        $actualHash,
        $version
    )
} finally {
    Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
}
