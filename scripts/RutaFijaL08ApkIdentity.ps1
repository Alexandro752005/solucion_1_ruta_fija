function Get-RutaFijaL08VerifiedApk {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$LiteralPath)
    $file=(Resolve-Path -LiteralPath $LiteralPath -ErrorAction Stop).Path
    if(-not $file.EndsWith('.apk',[StringComparison]::OrdinalIgnoreCase)){throw 'APK_FILE_REQUIRED'}
    $tool=Get-ChildItem -LiteralPath 'D:\Android\Sdk\build-tools' -Filter 'aapt2.exe' -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
    $metadata=& $tool.FullName dump badging $file
    if($LASTEXITCODE -ne 0){throw 'APK_METADATA_FAILED_NO_INSTALL'}
    $match=[regex]::Match(($metadata -join "`n"),"package: name='([^']+)'")
    if(-not $match.Success -or $match.Groups[1].Value -ne 'pe.rutafija.conductor.l08'){throw 'WRONG_APPLICATION_ID_REFUSED_NO_INSTALL'}
    return [pscustomobject]@{path=$file;package=$match.Groups[1].Value;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()}
}
