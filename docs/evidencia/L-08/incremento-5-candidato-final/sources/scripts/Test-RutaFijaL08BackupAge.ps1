[CmdletBinding()]
param(
    [ValidateSet('Plan','Check')][string]$Action='Plan',
    [switch]$Execute,
    [string]$BackupManifest,
    [string]$ArchiveDirectory,
    [string]$DailyVerificationManifest,
    [string]$EvidenceFile
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($Action-eq'Plan'){
    [ordered]@{action='PLAN_NO_FILES';snapshotMaximumAgeSeconds=3600;authenticationMaximumAgeSeconds=86400;reject='Missing archive, mismatched SHA256, future timestamp, stale snapshot or stale authenticated verification';independentMediumAccredited=$false}|ConvertTo-Json;return
}
if(-not$Execute-or-not$BackupManifest-or-not$ArchiveDirectory-or-not$EvidenceFile){throw 'EXPLICIT_EXECUTE_AND_PATHS_REQUIRED'}
if(Test-Path -LiteralPath $EvidenceFile){throw 'CREATE_NEW_EVIDENCE_ONLY'}
$now=[DateTimeOffset]::UtcNow;$reasons=[Collections.Generic.List[string]]::new()
try{
    $backup=Get-Content -LiteralPath $BackupManifest -Raw -Encoding UTF8|ConvertFrom-Json
    if($backup.result-ne'PASS_ENCRYPTED_COPY_ONLY'-or$backup.action-ne'Backup'){throw 'VALID_BACKUP_MANIFEST_REQUIRED'}
    if([IO.Path]::GetFileName($backup.archiveFile)-cne$backup.archiveFile){throw 'BASENAME_ONLY_REQUIRED'}
    $archive=Join-Path $ArchiveDirectory $backup.archiveFile
    if(-not(Test-Path -LiteralPath $archive -PathType Leaf)){$reasons.Add('ARCHIVE_MISSING')}
    elseif((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $backup.sha256){$reasons.Add('SHA256_MISMATCH')}
    $snapshot=[DateTimeOffset]::Parse($backup.sourceSnapshotUpperBoundAt)
    $verified=[DateTimeOffset]::Parse($backup.finishedAt)
    if($DailyVerificationManifest){
        $daily=Get-Content -LiteralPath $DailyVerificationManifest -Raw -Encoding UTF8|ConvertFrom-Json
        if($daily.result-ne'PASS_GCM_VERIFIED'-or$daily.sha256-ine$backup.sha256){throw 'DAILY_VERIFICATION_MUST_MATCH_EXACT_ARCHIVE'}
        $verified=[DateTimeOffset]::Parse($daily.finishedAt)
    }
    $age=($now-$snapshot).TotalSeconds;$verificationAge=($now-$verified).TotalSeconds
    if($age-lt0-or$verificationAge-lt0){$reasons.Add('FUTURE_TIMESTAMP')}
    if($age-gt3600){$reasons.Add('RPO_AGE_EXCEEDED_3600_SECONDS')}
    if($verificationAge-gt86400){$reasons.Add('DAILY_AUTHENTICATION_STALE')}
    $record=[ordered]@{result=if($reasons.Count){'INCIDENT_BACKUP_NOT_CURRENT'}else{'PASS_LOCAL_ARCHIVE_AGE_GATE'};checkedAt=$now.ToString('o');snapshotAgeSeconds=$age;authenticationAgeSeconds=$verificationAge;reasons=@($reasons);independentMediumAccredited=$false;schedulingAccredited=$false;operationalRpoAccredited=$false}
}catch{
    $record=[ordered]@{result='INCIDENT_BACKUP_NOT_CURRENT';checkedAt=$now.ToString('o');reasons=@('MALFORMED_OR_UNREADABLE_EVIDENCE');failureType=$_.Exception.GetType().Name;independentMediumAccredited=$false;schedulingAccredited=$false;operationalRpoAccredited=$false}
}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidenceFile),($record|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
$record|ConvertTo-Json -Depth 6
