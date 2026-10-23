[CmdletBinding()]
param(
    [ValidateSet('Plan','Backup','Verify')][string]$Action='Plan',
    [switch]$Execute,
    [string]$DestinationDirectory,
    [string]$KeyFile,
    [string]$ArchiveFile,
    [string]$EvidenceFile,
    [ValidateSet('5432','55441')][string]$SourcePort='5432',
    [switch]$AllowLocalCandidateCopy
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
$pgBin='C:\Program Files\PostgreSQL\16\bin'
$toolRoot=Join-Path $repoRoot 'backend/.local/l08-ops-tools'
$java='C:\Program Files\Java\jdk-21.0.10\bin\java.exe'
$javac='C:\Program Files\Java\jdk-21.0.10\bin\javac.exe'
$sourceDatabase=if($SourcePort -eq '5432'){'ruta_fija_test'}else{'l08_ops_source'}
$sourceUser=if($SourcePort -eq '5432'){'rf_migrator'}else{'l08_ops_owner'}
function Assert-PhysicalPath([string]$Path) {
    $cursor=[IO.Path]::GetFullPath($Path)
    while($cursor){
        if(Test-Path -LiteralPath $cursor){if(((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'REPARSE_POINT_REFUSED'}}
        $parent=Split-Path -Parent $cursor; if($parent -eq $cursor){break};$cursor=$parent
    }
}
function Write-CreateNewJson([string]$Path,$Value){
    $resolved=[IO.Path]::GetFullPath($Path);Assert-PhysicalPath $resolved
    if(Test-Path -LiteralPath $resolved){throw 'EVIDENCE_ALREADY_EXISTS_PRESERVE'}
    [IO.File]::WriteAllText($resolved,($Value|ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
}
if($Action -eq 'Plan'){
    [ordered]@{action='PLAN_NO_CONNECTION_NO_FILES';source=('127.0.0.1:'+$SourcePort+'/'+$sourceDatabase);algorithm='AES-256/GCM';nonceBits=96;tagBits=128;destination='Required external path; distinct physical medium must be attested, cannot be inferred from drive letter';key='Existing CurrentUser DPAPI file, outside backup destination';schedule='At least hourly; proposed every30min, job ceiling15min, actual age gate<=3600sec';daily='Verify every archive daily; weekly isolated restore';automaticTaskInstalled=$false;costSoles=0;maximumAuthenticatedPlaintextMiB=64}|ConvertTo-Json -Depth 5
    return
}
if(-not $Execute){throw 'EXPLICIT_EXECUTE_REQUIRED'}
if([string]::IsNullOrWhiteSpace($KeyFile)-or[string]::IsNullOrWhiteSpace($EvidenceFile)){throw 'KEY_AND_EVIDENCE_PATHS_REQUIRED'}
Assert-PhysicalPath $KeyFile;Assert-PhysicalPath $EvidenceFile
if(-not(Test-Path -LiteralPath $KeyFile -PathType Leaf)){throw 'EXISTING_DPAPI_KEY_REQUIRED'}
Add-Type -AssemblyName System.Security
$key=$null;$oldKey=[Environment]::GetEnvironmentVariable('RF_L08_BACKUP_KEY_BASE64','Process');$oldPgOptions=$env:PGOPTIONS
$start=[DateTimeOffset]::UtcNow
try{
    $key=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($KeyFile),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    if($key.Length -ne 32){throw 'AES256_KEY_REQUIRED'}
    $env:RF_L08_BACKUP_KEY_BASE64=[Convert]::ToBase64String($key)
    if(-not(Test-Path -LiteralPath $toolRoot)){New-Item -ItemType Directory -Path $toolRoot | Out-Null}
    $compilation=@(& $javac --release 21 -encoding UTF-8 -d $toolRoot (Join-Path $repoRoot 'backend/src/main/java/pe/rutafija/backup/AuthenticatedBackupArchive.java') (Join-Path $PSScriptRoot 'L08ContinuityTools.java') 2>&1)
    if($LASTEXITCODE -ne 0){throw 'CONTINUITY_TOOL_COMPILATION_FAILED'}
    $destinationInfo=$null
    if($Action -eq 'Backup'){
        if([string]::IsNullOrWhiteSpace($DestinationDirectory)){throw 'DESTINATION_REQUIRED'}
        $dest=[IO.Path]::GetFullPath($DestinationDirectory).TrimEnd('\');Assert-PhysicalPath $dest
        if(-not(Test-Path -LiteralPath $dest -PathType Container)){throw 'DESTINATION_MUST_ALREADY_EXIST'}
        $repoFull=[IO.Path]::GetFullPath($repoRoot).TrimEnd('\')
        $sameDrive=[IO.Path]::GetPathRoot($dest) -ieq [IO.Path]::GetPathRoot($repoFull)
        $insideRepo=($dest -ieq $repoFull)-or$dest.StartsWith($repoFull+'\',[StringComparison]::OrdinalIgnoreCase)
        if(($sameDrive-or$insideRepo)-and-not$AllowLocalCandidateCopy){throw 'LOCAL_COPY_REFUSED_WITHOUT_EXPLICIT_CANDIDATE_FLAG'}
        $keyFull=[IO.Path]::GetFullPath($KeyFile)
        if($keyFull.StartsWith($dest+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'KEY_MUST_BE_OUTSIDE_ARCHIVE_DESTINATION'}
        if([string]::IsNullOrWhiteSpace($env:PGPASSWORD)){throw 'PGPASSWORD_PROCESS_ENV_REQUIRED_NO_SECRET_ARGUMENT'}
        if($SourcePort -eq '5432'){
            foreach($localRoot in @((Join-Path $repoRoot '.local'),(Join-Path $repoRoot 'backend/.local'))){
                if((Test-Path -LiteralPath $localRoot)-and@(Get-ChildItem -LiteralPath $localRoot -File -Filter '*.active').Count -gt 0){throw 'PROTECTED_CASE_ACTIVE_REFUSED'}
            }
        }
        $ArchiveFile=Join-Path $dest ('ruta-fija-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N')+'.rfdb')
        $env:PGOPTIONS='-c default_transaction_read_only=on'
        $output=@(& $java -Xmx256m -cp $toolRoot L08ContinuityTools backup (Join-Path $pgBin 'pg_dump.exe') '127.0.0.1' $SourcePort $sourceDatabase $sourceUser $ArchiveFile 'READ_ONLY_DUMP' 2>&1)
        if($LASTEXITCODE -ne 0){throw 'ENCRYPTED_BACKUP_FAILED_KEEP_PARTIAL_AND_REVIEW'}
        $destinationInfo=[ordered]@{sameDriveAsServerWorkspace=$sameDrive;insideWorkspace=$insideRepo;physicalIndependentMediumAccredited=$false;localCandidateCopy=[bool]$AllowLocalCandidateCopy}
    }else{
        if([string]::IsNullOrWhiteSpace($ArchiveFile)){throw 'ARCHIVE_REQUIRED'}
        Assert-PhysicalPath $ArchiveFile
        $output=@(& $java -Xmx256m -cp $toolRoot L08ContinuityTools verify $ArchiveFile 2>&1)
        if($LASTEXITCODE -ne 0){throw 'GCM_AUTHENTICATION_FAILED_ARCHIVE_NOT_RESTORABLE'}
    }
    $end=[DateTimeOffset]::UtcNow
    $result=[ordered]@{result=if($Action -eq 'Backup'){'PASS_ENCRYPTED_COPY_ONLY'}else{'PASS_GCM_VERIFIED'};action=$Action;sourcePort=$SourcePort;sourceDatabase=$sourceDatabase;startedAt=$start.ToString('o');finishedAt=$end.ToString('o');elapsedSeconds=($end-$start).TotalSeconds;sourceSnapshotUpperBoundAt=$start.ToString('o');archiveFile=[IO.Path]::GetFileName($ArchiveFile);sha256=(Get-FileHash -LiteralPath $ArchiveFile -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $ArchiveFile).Length;algorithm='AES-256/GCM';plaintextDumpWritten=$false;destination=$destinationInfo;readOnlySource=$true;hourlyTaskInstalled=$false;dailyTaskInstalled=$false;independentCustodyAccredited=$false}
    Write-CreateNewJson $EvidenceFile $result
    $result|ConvertTo-Json -Depth 8
}finally{
    if($null-ne$key){[Array]::Clear($key,0,$key.Length)}
    [Environment]::SetEnvironmentVariable('RF_L08_BACKUP_KEY_BASE64',$oldKey,'Process')
    $env:PGOPTIONS=$oldPgOptions
}
