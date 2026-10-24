[CmdletBinding()]
param([switch]$Execute,[string]$ResumeIncidentEvidence,[string]$ResumeInitializedCaseEvidence)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
if(-not$Execute){[ordered]@{action='PLAN_NO_FILES_NO_CONNECTIONS';cluster='NEW loopback55441 only';httpFixtures='loopback56441/56442';sourceDatabase='NEW l08_ops_source';restoreDatabase='NEW l08_ops_restore';shared5432Touched=$false;independentMedium=$false;historicalClustersTouched=$false}|ConvertTo-Json;return}
$caseId=[Guid]::NewGuid().ToString()
$privateRoot=Join-Path $repoRoot ('backend/.local/l08-ops-'+$caseId)
$initializedCase=$null
if($ResumeInitializedCaseEvidence){
    $resumePath=[IO.Path]::GetFullPath($ResumeInitializedCaseEvidence)
    $opsRoot=[IO.Path]::GetFullPath((Join-Path $repoRoot 'docs/evidencia/L-08/incremento-5-candidato-final/ops')).TrimEnd('\')+'\'
    if(-not$resumePath.StartsWith($opsRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'PRIOR_OPS_CASE_REQUIRED'}
    $initializedCase=Get-Content -LiteralPath $resumePath -Raw -Encoding UTF8|ConvertFrom-Json
    if($initializedCase.result-ne'STOPPED_KEEP_CASE_AND_DIAGNOSE'-or$initializedCase.failurePhase-ne'START_OWNED_CLUSTER'-or$initializedCase.caseId-notmatch'^[a-f0-9-]{36}$'){throw 'EXACT_INITIALIZED_FAILED_CASE_REQUIRED'}
    $privateRoot=Join-Path $repoRoot ('backend/.local/l08-ops-'+$initializedCase.caseId)
    if(-not$ResumeIncidentEvidence){$ResumeIncidentEvidence=$ResumeInitializedCaseEvidence}
}
$dataRoot=Join-Path $privateRoot 'cluster/data'
$destination=Join-Path $privateRoot 'copy-same-pc'
$evidenceRoot=Join-Path $repoRoot ('docs/evidencia/L-08/incremento-5-candidato-final/ops/'+$caseId)
$pgBin='C:\Program Files\PostgreSQL\16\bin'
$toolRoot=Join-Path $repoRoot 'backend/.local/l08-ops-tools'
$java='C:\Program Files\Java\jdk-21.0.10\bin\java.exe'
$passwordFile=Join-Path $privateRoot 'bootstrap-password.txt'
$keyFile=Join-Path $privateRoot 'backup-key.dpapi'
$oldPassword=$env:PGPASSWORD;$oldKey=$env:RF_L08_BACKUP_KEY_BASE64
$clusterStarted=$false;$httpProcess=$null;$key=$null;$phase='PRECONDITIONS';$exit=1
$start=[DateTimeOffset]::UtcNow;$clock=[Diagnostics.Stopwatch]::StartNew()
$previousFailure=$null
if($ResumeIncidentEvidence){
    $previousPath=[IO.Path]::GetFullPath($ResumeIncidentEvidence)
    $allowedRoot=[IO.Path]::GetFullPath((Join-Path $repoRoot 'docs/evidencia/L-08/incremento-5-candidato-final/ops')).TrimEnd('\')+'\'
    if(-not$previousPath.StartsWith($allowedRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'PRIOR_OPS_EVIDENCE_REQUIRED'}
    $previousFailure=Get-Content -LiteralPath $previousPath -Raw -Encoding UTF8|ConvertFrom-Json
    if($previousFailure.result-ne'STOPPED_KEEP_CASE_AND_DIAGNOSE'){throw 'PRIOR_FAILURE_REQUIRED'}
    $start=[DateTimeOffset]::Parse($previousFailure.startedAt)
    if($start-gt[DateTimeOffset]::UtcNow-or([DateTimeOffset]::UtcNow-$start).TotalHours-gt24){throw 'INVALID_OR_STALE_INCIDENT_CLOCK'}
}
$result=[ordered]@{caseId=$caseId;startedAt=$start.ToString('o');sourcePort=55441;shared5432Touched=$false;historicalClustersTouched=$false;physicalIndependentMedium=$false;totalServerLossRecreated=$false;human24x7Coverage=$false;costSoles=0}
if($null-ne$previousFailure){$result.previousFailedCaseId=$previousFailure.caseId;$result.previousFailureEvidenceSha256=(Get-FileHash -LiteralPath $previousPath -Algorithm SHA256).Hash.ToLowerInvariant();$result.clockIncludesPreviousFailureAndIntervention=$true}
if($null-ne$initializedCase){$result.reusedInitializedCaseId=$initializedCase.caseId;$result.credentialReset='Offline single-user mode, same stopped empty isolated cluster; no HBA weakening'}
function New-Secret([int]$Size){$bytes=New-Object byte[] $Size;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes);return ,$bytes}finally{$rng.Dispose()}}
function Run-Native([string]$Binary,[string[]]$Arguments,[string]$Log,[int]$Timeout=60,[string]$StandardInput){
    # All arguments here are controlled paths or SQL-free native flags. Reject embedded quotes.
    foreach($argument in $Arguments){if($argument.Contains('"')){throw 'NATIVE_QUOTE_REFUSED'}}
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$Binary;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.Arguments=(@($Arguments|ForEach-Object{'"'+$_+'"'})-join' ')
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    if($StandardInput){$info.RedirectStandardInput=$true}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if($StandardInput){$process.StandardInput.Write($StandardInput);$process.StandardInput.Close()}
        if(-not$process.WaitForExit($Timeout*1000)){$process.Kill();throw 'OWNED_NATIVE_TIMEOUT'}
        $outputReady=$stdout.Wait(3000);$errorReady=$stderr.Wait(3000)
        if(-not$outputReady-or-not$errorReady){
            if([IO.Path]::GetFileName($Binary)-ine'pg_ctl.exe'-or$Arguments-notcontains'start'-or$process.ExitCode-ne0){throw 'NATIVE_STREAM_DRAIN_TIMEOUT'}
            [IO.File]::WriteAllText($Log,'PG_CTL_EXIT_0_CHILD_OWNS_OUTPUT_HANDLES; SERVER_IDENTITY_READINESS_CHECK_REQUIRED',[Text.UTF8Encoding]::new($false))
        }else{[IO.File]::WriteAllText($Log,$stdout.Result+$stderr.Result,[Text.UTF8Encoding]::new($false))}
        if($process.ExitCode-ne0){throw 'OWNED_NATIVE_NONZERO'}
    }finally{$process.Dispose()}
}
function Sql([string]$Database,[string]$Query){
    $output=@(& (Join-Path $pgBin 'psql.exe') -X --no-password '--host=127.0.0.1' '--port=55441' '--username=l08_ops_owner' ('--dbname='+$Database) -v ON_ERROR_STOP=1 -At -c $Query 2>&1)
    if($LASTEXITCODE-ne0){throw 'ISOLATED_SQL_FAILED'}
    return ($output -join "`n").Trim()
}
function Save-Json([string]$Name,$Data){$path=Join-Path $evidenceRoot $Name;if(Test-Path -LiteralPath $path){throw 'EVIDENCE_EXISTS'};[IO.File]::WriteAllText($path,($Data|ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))}
try{
    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    foreach($port in @(55441,56441,56442)){if(@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count){throw 'OWNED_PORT_ALREADY_OCCUPIED'}}
    if((Test-Path -LiteralPath $privateRoot)-and$null-eq$initializedCase){throw 'NEW_PRIVATE_ROOT_REQUIRED'}
    if(-not(Test-Path -LiteralPath $privateRoot)){New-Item -ItemType Directory -Path $privateRoot | Out-Null}
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetOwner($identity);$acl.SetAccessRuleProtection($true,$false)
    $flags=[Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl',$flags,'None','Allow'))
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new('S-1-5-18'),'FullControl',$flags,'None','Allow'))
    if($null-eq$initializedCase){Set-Acl -LiteralPath $privateRoot -AclObject $acl}
    else{
        $existingAcl=Get-Acl -LiteralPath $privateRoot
        if(-not$existingAcl.AreAccessRulesProtected){throw 'EXISTING_PRIVATE_ACL_MUST_REMAIN_PROTECTED'}
        foreach($rule in $existingAcl.Access){if($rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value-notin@($identity.Value,'S-1-5-18')){throw 'EXISTING_PRIVATE_ACL_HAS_OTHER_IDENTITY'}}
    }
    New-Item -ItemType Directory -Path $evidenceRoot,$destination,(Split-Path -Parent $dataRoot) -Force | Out-Null
    Add-Type -AssemblyName System.Security
    $secret=New-Secret 32;$env:PGPASSWORD=[Convert]::ToBase64String($secret);[Array]::Clear($secret,0,$secret.Length)
    [IO.File]::WriteAllText($passwordFile,$env:PGPASSWORD,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllBytes((Join-Path $privateRoot 'owner-credentials.dpapi'),[Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($env:PGPASSWORD),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
    if($null-eq$initializedCase){
        $key=New-Secret 32
        [IO.File]::WriteAllBytes($keyFile,[Security.Cryptography.ProtectedData]::Protect($key,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
        $phase='INIT_NEW_ISOLATED_CLUSTER'
        Run-Native (Join-Path $pgBin 'initdb.exe') @('-D',$dataRoot,'-U','l08_ops_owner',('--pwfile='+$passwordFile),'--auth=scram-sha-256','--encoding=UTF8','--locale=C') (Join-Path $evidenceRoot 'initdb.log') 600
        [IO.File]::AppendAllText((Join-Path $dataRoot 'postgresql.conf'),"`nlisten_addresses='127.0.0.1'`nport=55441`nmax_connections=20`nshared_buffers='32MB'`nlogging_collector=off`nlog_statement='none'`nlog_min_error_statement='panic'`ntimezone='UTC'`n",[Text.UTF8Encoding]::new($false))
    }else{
        $phase='RECOVER_SAME_STOPPED_EMPTY_CLUSTER'
        if(Test-Path -LiteralPath (Join-Path $dataRoot 'postmaster.pid')){throw 'OWNED_CLUSTER_MUST_ALREADY_BE_STOPPED'}
        if([IO.File]::ReadAllText((Join-Path $dataRoot 'PG_VERSION')).Trim()-ne'16'){throw 'SAME_PG16_CLUSTER_REQUIRED'}
        $key=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyFile),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        Run-Native (Join-Path $pgBin 'postgres.exe') @('--single','-D',$dataRoot,'postgres') (Join-Path $privateRoot 'single-user-credential-recovery.private.log') 60 ("ALTER ROLE l08_ops_owner PASSWORD '"+$env:PGPASSWORD+"';`n")
    }
    $phase='START_OWNED_CLUSTER'
    Run-Native (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'-l',(Join-Path $privateRoot 'postgresql.log'),'start','-w','-t','30') (Join-Path $evidenceRoot 'cluster-start.log') 45
    $clusterStarted=$true
    Remove-Item -LiteralPath $passwordFile
    $actualDataPath=Sql 'postgres' 'SHOW data_directory;'
    if([IO.Path]::GetFullPath($actualDataPath)-ine[IO.Path]::GetFullPath($dataRoot)){throw 'OWNED_CLUSTER_SQL_IDENTITY_MISMATCH'}
    if((Sql 'postgres' "SELECT count(*) FROM pg_database WHERE datname IN ('l08_ops_source','l08_ops_restore');")-ne'0'){throw 'SAME_EMPTY_SYNTHETIC_CLUSTER_REQUIRED'}
    $phase='SYNTHETIC_FIXTURE'
    [void](Sql 'postgres' 'CREATE DATABASE l08_ops_source;');[void](Sql 'postgres' 'CREATE DATABASE l08_ops_restore;')
    [void](Sql 'l08_ops_source' "CREATE TABLE ops_parent(id integer PRIMARY KEY, name text NOT NULL); INSERT INTO ops_parent VALUES(1,'synthetic'); CREATE TABLE ops_records(id integer PRIMARY KEY, parent_id integer REFERENCES ops_parent(id), payload text, captured_at timestamptz NOT NULL DEFAULT now()); INSERT INTO ops_records(id,parent_id,payload) SELECT n,1,'registro-'||n FROM generate_series(1,100) n; CREATE INDEX ix_ops_payload ON ops_records(payload);")
    $fingerprintSql="SELECT (SELECT count(*) FROM ops_parent)||'|'||count(*)||'|'||md5(string_agg(id::text||':'||parent_id::text||':'||payload||':'||captured_at::text,',' ORDER BY id)) FROM ops_records;"
    $sourceFingerprint=Sql 'l08_ops_source' $fingerprintSql
    $phase='DUMP_ENCRYPT_COPY_VERIFY'
    $backupOutput=& (Join-Path $PSScriptRoot 'Invoke-RutaFijaL08IndependentBackup.ps1') -Action Backup -Execute -SourcePort 55441 -DestinationDirectory $destination -KeyFile $keyFile -EvidenceFile (Join-Path $evidenceRoot 'backup.json') -AllowLocalCandidateCopy
    $backup=Get-Content -LiteralPath (Join-Path $evidenceRoot 'backup.json') -Raw -Encoding UTF8|ConvertFrom-Json
    $archive=Join-Path $destination $backup.archiveFile
    $env:RF_L08_BACKUP_KEY_BASE64=[Convert]::ToBase64String($key)
    $phase='AUTHENTICATION_NEGATIVE_TESTS'
    $tampered=Join-Path $destination 'tampered-negative-only.rfdb';$bytes=[IO.File]::ReadAllBytes($archive);$bytes[$bytes.Length-1]=$bytes[$bytes.Length-1]-bxor1;[IO.File]::WriteAllBytes($tampered,$bytes);[Array]::Clear($bytes,0,$bytes.Length)
    $tamperOutput=@(& $java -Xmx256m -cp $toolRoot L08ContinuityTools verify $tampered 2>&1);$tamperRejected=$LASTEXITCODE-ne0
    $wrongKey=New-Secret 32;$env:RF_L08_BACKUP_KEY_BASE64=[Convert]::ToBase64String($wrongKey);[Array]::Clear($wrongKey,0,$wrongKey.Length)
    $wrongOutput=@(& $java -Xmx256m -cp $toolRoot L08ContinuityTools verify $archive 2>&1);$wrongKeyRejected=$LASTEXITCODE-ne0
    $env:RF_L08_BACKUP_KEY_BASE64=[Convert]::ToBase64String($key)
    if(-not$tamperRejected-or-not$wrongKeyRejected){throw 'GCM_NEGATIVE_TEST_FAILED'}
    Save-Json 'gcm-negative-tests.json' ([ordered]@{alteredTagRejected=$tamperRejected;wrong256BitKeyRejected=$wrongKeyRejected;plaintextPassedToRestore=$false;originalArchiveSha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()})
    [void](Sql 'l08_ops_source' "INSERT INTO ops_records(id,parent_id,payload) VALUES(101,1,'after-snapshot'),(102,1,'after-snapshot');")
    $phase='RESTORE_AUTHENTICATED_ARCHIVE'
    $restoreOutput=@(& $java -Xmx256m -cp $toolRoot L08ContinuityTools restore-rehearsal (Join-Path $pgBin 'pg_restore.exe') $archive '55441' 'l08_ops_restore' 2>&1)
    if($LASTEXITCODE-ne0){throw 'ISOLATED_RESTORE_FAILED'}
    $restoredFingerprint=Sql 'l08_ops_restore' $fingerprintSql
    if($sourceFingerprint-cne$restoredFingerprint){throw 'RESTORED_CONTENT_MISMATCH'}
    $integrity=Sql 'l08_ops_restore' "SELECT (SELECT count(*) FROM pg_constraint WHERE conrelid IN ('ops_parent'::regclass,'ops_records'::regclass)),(SELECT count(*) FROM pg_indexes WHERE tablename IN ('ops_parent','ops_records'));"
    if($integrity-ne'3|3'){throw 'RESTORED_CONSTRAINT_INDEX_MISMATCH'}
    $recoveredAt=[DateTimeOffset]::UtcNow;$rtoSeconds=($recoveredAt-$start).TotalSeconds
    $snapshotAgeSeconds=($recoveredAt-[DateTimeOffset]::Parse($backup.sourceSnapshotUpperBoundAt)).TotalSeconds
    Save-Json 'restore.json' ([ordered]@{result='PASS_SYNTHETIC_SQL_INTEGRITY';startedAt=$start.ToString('o');recoveredAt=$recoveredAt.ToString('o');rtoSecondsNoClockReset=$rtoSeconds;rtoLimitSeconds=14400;rtoPass=$rtoSeconds-le14400;tablesCompared=2;rowsRestored=101;fingerprintsEqual=$true;sourceFingerprint=$sourceFingerprint;restoredFingerprint=$restoredFingerprint;constraints=3;indexes=3;sourceRowsAddedAfterSnapshot=2;observedSnapshotAgeSeconds=$snapshotAgeSeconds;rpoObservedLocalAgeWithinHour=$snapshotAgeSeconds-le3600;designScheduleIntervalSeconds=1800;designJobCeilingSeconds=900;designMaximumSnapshotAgeSeconds=2700;hourlyExecutionAccredited=$false;clientsAndWholeServerRecoveryAccredited=$false})
    $phase='BACKUP_AGE_GATE'
    $ageOutput=& (Join-Path $PSScriptRoot 'Test-RutaFijaL08BackupAge.ps1') -Action Check -Execute -BackupManifest (Join-Path $evidenceRoot 'backup.json') -ArchiveDirectory $destination -EvidenceFile (Join-Path $evidenceRoot 'backup-age-fresh.json')
    $age=Get-Content -LiteralPath (Join-Path $evidenceRoot 'backup-age-fresh.json') -Raw|ConvertFrom-Json
    if($age.result-ne'PASS_LOCAL_ARCHIVE_AGE_GATE'){throw 'FRESH_BACKUP_NOT_ACCEPTED'}
    $backup.sourceSnapshotUpperBoundAt=[DateTimeOffset]::UtcNow.AddSeconds(-6000).ToString('o')
    Save-Json 'synthetic-stale-timestamp-fixture.json' $backup
    $staleOutput=& (Join-Path $PSScriptRoot 'Test-RutaFijaL08BackupAge.ps1') -Action Check -Execute -BackupManifest (Join-Path $evidenceRoot 'synthetic-stale-timestamp-fixture.json') -ArchiveDirectory $destination -EvidenceFile (Join-Path $evidenceRoot 'backup-age-stale.json')
    $stale=Get-Content -LiteralPath (Join-Path $evidenceRoot 'backup-age-stale.json') -Raw|ConvertFrom-Json
    if($stale.result-ne'INCIDENT_BACKUP_NOT_CURRENT'-or$stale.reasons-notcontains'RPO_AGE_EXCEEDED_3600_SECONDS'){throw 'STALE_BACKUP_NOT_REJECTED'}
    $phase='SYNTHETIC_MONITOR_HEALTHY'
    $httpInfo=[Diagnostics.ProcessStartInfo]::new();$httpInfo.FileName=$java;$httpInfo.UseShellExecute=$false;$httpInfo.CreateNoWindow=$true
    $httpInfo.Arguments='-cp "'+$toolRoot+'" L08ContinuityTools http-fixture 56441 56442'
    $httpProcess=[Diagnostics.Process]::Start($httpInfo)
    $ready=$false;for($attempt=0;$attempt-lt30;$attempt++){if(@(Get-NetTCPConnection -LocalPort 56441 -State Listen -ErrorAction SilentlyContinue).Count){$ready=$true;break};Start-Sleep -Milliseconds 200}
    if(-not$ready){throw 'HTTP_FIXTURE_NOT_READY'}
    $monitorArgs=@{Action='Probe';Execute=$true;ApiHealthUrl='http://127.0.0.1:56441/actuator/health/readiness';AngularUrl='http://127.0.0.1:56442/';ProxyHealthUrl='http://127.0.0.1:56442/actuator/health/readiness';AngularMarker='RF_L08_SYNTHETIC_PROXY';PostgreSqlPort=55441;SyntheticFixtures=$true}
    $healthyOutput=& (Join-Path $PSScriptRoot 'Watch-RutaFijaL08Health.ps1') @monitorArgs -EvidenceFile (Join-Path $evidenceRoot 'monitor-healthy.json')
    $healthy=Get-Content -LiteralPath (Join-Path $evidenceRoot 'monitor-healthy.json') -Raw|ConvertFrom-Json
    if($healthy.result-ne'ALL_PROBES_HEALTHY'){throw 'HEALTHY_FIXTURE_NOT_HEALTHY'}
    $phase='REAL_STOP_CONTROLLED_FIXTURES'
    $httpProcess.Kill();$httpProcess.WaitForExit();$httpProcess.Dispose();$httpProcess=$null
    Run-Native (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'stop','-m','fast','-w','-t','30') (Join-Path $evidenceRoot 'cluster-stop.log') 45
    $clusterStarted=$false;$failureAt=[DateTimeOffset]::UtcNow
    $failedOutput=& (Join-Path $PSScriptRoot 'Watch-RutaFijaL08Health.ps1') @monitorArgs -EvidenceFile (Join-Path $evidenceRoot 'monitor-failed.json')
    $failure=Get-Content -LiteralPath (Join-Path $evidenceRoot 'monitor-failed.json') -Raw|ConvertFrom-Json
    if($failure.result-ne'INCIDENT_RECORDED_LOCALLY'){throw 'FAILURE_NOT_DETECTED'}
    $failedSample=$failure.observations[0]
    if($failedSample.api.healthy-or$failedSample.angular.healthy-or$failedSample.proxy.healthy-or$failedSample.postgresAcceptsConnections){throw 'STOPPED_FIXTURES_MUST_ALL_BE_UNHEALTHY'}
    foreach($port in @(55441,56441,56442)){if(@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count){throw 'OWNED_PORT_STILL_LISTENING_AFTER_STOP'}}
    $detectedAt=[DateTimeOffset]::UtcNow
    $result.result='PASS_LOCAL_CONTINUITY_CANDIDATE_WITH_EXTERNAL_PREREQUISITES'
    $result.rtoSecondsNoClockReset=$rtoSeconds;$result.observedSnapshotAgeSeconds=$snapshotAgeSeconds
    $result.httpAndPgStoppedFailureDetectedSeconds=($detectedAt-$failureAt).TotalSeconds
    $result.configuredWorstDetectionSeconds=$failure.configuredDetectionUpperBoundSeconds
    $result.gcmTamperAndWrongKeyRejected=$true;$result.ownedClusterStopped=$true;$result.privateCaseDirectory=$privateRoot.Substring($repoRoot.Length+1).Replace('\','/')
    $exit=0
}catch{$result.result='STOPPED_KEEP_CASE_AND_DIAGNOSE';$result.failurePhase=$phase;$result.failureType=$_.Exception.GetType().Name;Write-Output ('OPS_FAILURE_PHASE='+$phase);Write-Output ('OPS_FAILURE_TYPE='+$_.Exception.GetType().Name)}finally{
    if($null-ne$httpProcess){try{if(-not$httpProcess.HasExited){$httpProcess.Kill();$httpProcess.WaitForExit()}}catch{}}
    $ownedPidFile=Join-Path $dataRoot 'postmaster.pid'
    if($clusterStarted-or(Test-Path -LiteralPath $ownedPidFile)){
        try{
            if(-not(Test-Path -LiteralPath $ownedPidFile)){throw 'OWNED_SERVER_IDENTITY_FILE_REQUIRED_BEFORE_STOP'}
            $ownedPid=Get-Content -LiteralPath $ownedPidFile
            if([IO.Path]::GetFullPath($ownedPid[1])-ine[IO.Path]::GetFullPath($dataRoot)-or$ownedPid[3]-ne'55441'){throw 'OWNED_CLUSTER_IDENTITY_MISMATCH_NO_STOP'}
            Run-Native (Join-Path $pgBin 'pg_ctl.exe') @('-D',$dataRoot,'stop','-m','fast','-w','-t','30') (Join-Path $evidenceRoot 'cluster-finally-stop.log') 45
            $result.ownedClusterStopped=$true
        }catch{$result.ownedClusterStopped=$false;$exit=1}
    }
    if(Test-Path -LiteralPath $passwordFile){Remove-Item -LiteralPath $passwordFile}
    if($null-ne$key){[Array]::Clear($key,0,$key.Length)};$env:PGPASSWORD=$oldPassword;$env:RF_L08_BACKUP_KEY_BASE64=$oldKey
    $result.finishedAt=[DateTimeOffset]::UtcNow.ToString('o');$result.totalElapsedSeconds=([DateTimeOffset]::UtcNow-$start).TotalSeconds;$result.thisInvocationElapsedSeconds=$clock.Elapsed.TotalSeconds
    if(Test-Path -LiteralPath $evidenceRoot){Save-Json 'result.json' $result}
}
$result|ConvertTo-Json -Depth 10
exit $exit
