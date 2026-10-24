[CmdletBinding()]
param([switch]$Execute,[switch]$VerifyOnly,[string]$SignatureDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if(-not $Execute){Write-Output 'L08_CLOSURE_SIGNATURE=PLAN_NO_SIGNATURE';return}
$acta=Join-Path $repoRoot 'docs/actas/L-08-cierre-funcional-global.md'
$releaseRoot=Join-Path $repoRoot 'docs/evidencia/L-08/incremento-5-candidato-final/release'
$runtime=Join-Path $repoRoot ('backend/target/l08-closure-signature-'+[Guid]::NewGuid().ToString())
$keyRoot=Join-Path $repoRoot 'backend/.local/l08-local-release-key'
$java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe'
$javac='C:/Program Files/Java/jdk-21.0.10/bin/javac.exe'
if(-not (Test-Path -LiteralPath $acta)){throw 'FINAL_ACTA_REQUIRED_BEFORE_SIGNATURE'}
if((Get-Item -LiteralPath $acta).Length -eq 0){throw 'NONEMPTY_FINAL_ACTA_REQUIRED'}
if($VerifyOnly){
 if(-not $SignatureDirectory){throw 'VERIFY_ONLY_REQUIRES_EXISTING_SIGNATURE_DIRECTORY'}
 $destination=[IO.Path]::GetFullPath($SignatureDirectory)
 if(-not $destination.StartsWith($releaseRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'EXACT_LOCAL_RELEASE_SIGNATURE_FOLDER_REQUIRED'}
}else{
 if($SignatureDirectory){throw 'AUTOMATIC_CREATE_NEW_SIGNATURE_FOLDER_REQUIRED'}
 $destination=Join-Path $releaseRoot ('closure-signature-'+[Guid]::NewGuid().ToString())
 New-Item -ItemType Directory -Path $destination | Out-Null
}
New-Item -ItemType Directory -Path $runtime | Out-Null
$source=Join-Path $repoRoot 'scripts/support/L08ClosureSigner.java'
& $javac -d $runtime $source
if($LASTEXITCODE -ne 0){throw 'CLOSURE_SIGNATURE_HELPER_COMPILE_FAILED'}
$signature=Join-Path $destination 'acta.sig'
$certificate=Join-Path $destination 'candidate-public-cert.der'
if(-not $VerifyOnly){
 & $java -cp $runtime L08ClosureSigner sign $acta (Join-Path $keyRoot 'candidato-local.p12') (Join-Path $keyRoot 'store-password.private') $signature $certificate
 if($LASTEXITCODE -ne 0){throw 'CLOSURE_SIGNATURE_FAILED'}
}
$verified=& $java -cp $runtime L08ClosureSigner verify $acta $signature $certificate
if($LASTEXITCODE -ne 0){throw 'CLOSURE_PUBLIC_SIGNATURE_VERIFICATION_FAILED'}
$verifiedText=$verified -join "`n"
if($verifiedText -notmatch 'L08_CLOSURE_CERTIFICATE_SHA256=([a-f0-9]{64})'){throw 'PUBLIC_CERTIFICATE_DIGEST_REQUIRED'}
$certificateSha=$Matches[1]
$manifest=Get-Content -LiteralPath (Join-Path $repoRoot 'artifacts/L-08-entrega-final/manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($manifest.android.signerSha256 -ne $certificateSha){throw 'ACTA_SIGNATURE_MUST_USE_SAME_LOCAL_CANDIDATE_APK_CERTIFICATE'}
$bytes=[IO.File]::ReadAllBytes($acta)
if($bytes.Length -eq 0){throw 'NONEMPTY_FINAL_ACTA_REQUIRED'}
$bytes[0]=$bytes[0] -bxor 1
$tampered=Join-Path $runtime 'one-byte-modified-acta.private'
[IO.File]::WriteAllBytes($tampered,$bytes)
$rejected=& $java -cp $runtime L08ClosureSigner verify $tampered $signature $certificate
$tamperedExit=$LASTEXITCODE
if($tamperedExit -ne 3 -or ($rejected -join "`n") -notmatch 'L08_CLOSURE_SIGNATURE_VALID=false'){throw 'MODIFIED_ONE_BYTE_MUST_BE_REJECTED'}
$proof=[ordered]@{
 recordedAt=[DateTimeOffset]::UtcNow.ToString('o')
 result='PASS_PUBLIC_CERTIFICATE_VERIFICATION_AND_ONE_BYTE_REJECTION'
 classification='FIRMA_TECNICA_AUTOMATIZADA_CANDIDATO_LOCAL'
 algorithm='SHA256withRSA'
 document='docs/actas/L-08-cierre-funcional-global.md'
 documentSha256=(Get-FileHash -LiteralPath $acta -Algorithm SHA256).Hash.ToLowerInvariant()
 signatureSha256=(Get-FileHash -LiteralPath $signature -Algorithm SHA256).Hash.ToLowerInvariant()
 certificateSha256=$certificateSha
 sameAsCandidateApkSigner=$true
 verifiedWithPublicCertificateOnly=$true
 modifiedOneByteRejected=$true
 modifiedOneByteExitCode=$tamperedExit
 legalTitleholderSignature=$false
 productionCustodyCeremony=$false
 privateKeyPubliclyIncluded=$false
 command='powershell.exe -NoProfile -File scripts/Sign-RutaFijaL08Closure.ps1 -Execute'
 helperSourceSha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
 runtimeDirectory=$runtime.Substring($repoRoot.Length+1).Replace('\','/')
 compiledHelperSha256=(Get-FileHash -LiteralPath (Join-Path $runtime 'L08ClosureSigner.class') -Algorithm SHA256).Hash.ToLowerInvariant()
}
$proofName=if($VerifyOnly){'independent-verification-'+[Guid]::NewGuid().ToString()+'.json'}else{'signature-verification.json'}
$proof | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $destination $proofName) -Encoding UTF8
Write-Output 'L08_CLOSURE_SIGNATURE=VERIFIED_LOCAL_TECHNICAL_SIGNATURE_NOT_LEGAL_TITLEHOLDER'
Write-Output ('L08_CLOSURE_SIGNATURE_DIRECTORY='+$destination.Substring($repoRoot.Length+1).Replace('\','/'))
# The expected negative Java test exits 3. Do not leak that value as the
# successful PowerShell process exit code after all assertions passed.
exit 0
