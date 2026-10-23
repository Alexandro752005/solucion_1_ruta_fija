[CmdletBinding()]
param(
 [ValidateSet('Plan','Check','Harden')][string]$Action='Plan',
 [switch]$Execute,
 [string]$ConfigPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if(-not$ConfigPath){$ConfigPath=Join-Path $repoRoot 'backend/.local/ruta-fija-native.env'}
if($Action-eq'Plan'){Write-Output 'LOCAL_SECRET_POLICY=PLAN_NO_FILE_READ_NO_GIT';return}
if(-not$Execute){throw 'EXPLICIT_EXECUTE_REQUIRED'}
$full=[IO.Path]::GetFullPath($ConfigPath)
$allowedRoot=[IO.Path]::GetFullPath((Join-Path $repoRoot 'backend/.local')).TrimEnd('\')+'\'
if(-not$full.StartsWith($allowedRoot,[StringComparison]::OrdinalIgnoreCase)-or-not(Test-Path -LiteralPath $full -PathType Leaf)){throw 'EXISTING_PRIVATE_CONFIG_PATH_REQUIRED'}
$cursor=$full
while($cursor){$item=Get-Item -LiteralPath $cursor -Force;if(($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'PRIVATE_CONFIG_REPARSE_REFUSED'};$next=Split-Path -Parent $cursor;if($next-eq$cursor){break};$cursor=$next}
$ignore=@(Get-Content -LiteralPath (Join-Path $repoRoot '.gitignore') -Encoding UTF8|ForEach-Object{$_.Trim()}|Where-Object{$_-and-not$_.StartsWith('#')})
if('backend/.local/'-notin$ignore){throw 'CANONICAL_PRIVATE_DIRECTORY_EXCLUSION_REQUIRED'}
$knownExceptions=@('!.env.example','!backend/.mvn/wrapper/maven-wrapper.jar','!.vscode/extensions.json','!.vscode/tasks.json')
if(@($ignore|Where-Object{$_.StartsWith('!')-and$_-notin$knownExceptions}).Count){throw 'UNREVIEWED_EXCLUSION_NEGATION_REFUSED'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$allowed=@($sid.Value,'S-1-5-18')
$acl=Get-Acl -LiteralPath $full
if($Action-eq'Harden'){
 if([IO.Path]::GetFileName($full)-notin@('ruta-fija-native.env','ruta-fija-bootstrap.env')){throw 'HARDEN_ONLY_EXACT_KNOWN_CONFIG_FILES'}
 $backupRoot=Join-Path $repoRoot 'docs/evidencia/auditoria-ide-2026-10-23/launchers/acl-descriptores'
 New-Item -ItemType Directory -Path $backupRoot -Force|Out-Null
 $proof=Join-Path $backupRoot ([IO.Path]::GetFileName($full)+'-'+[Guid]::NewGuid().ToString()+'.json')
 [ordered]@{recordedAt=[DateTimeOffset]::UtcNow.ToString('o');file=('backend/.local/'+[IO.Path]::GetFileName($full));originalSddl=$acl.Sddl;contentReadOrCopied=$false;reason='Protect only this config from other identities; descriptor only backup'}|ConvertTo-Json|Set-Content -LiteralPath $proof -Encoding UTF8
 $replacement=[Security.AccessControl.FileSecurity]::new()
 $replacement.SetAccessRuleProtection($true,$false)
 foreach($principal in $allowed){$replacement.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($principal),'FullControl','Allow'))}
 Set-Acl -LiteralPath $full -AclObject $replacement
 $acl=Get-Acl -LiteralPath $full
}
if(-not$acl.AreAccessRulesProtected){throw 'SECRET_FILE_INHERITED_ACL_REFUSED_USE_EXPLICIT_HARDEN'}
foreach($rule in $acl.Access){
 if($rule.AccessControlType-eq'Allow'-and$rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value-notin$allowed){throw 'SECRET_FILE_OTHER_IDENTITY_ALLOW_REFUSED'}
}
Write-Output 'LOCAL_SECRET_POLICY=PASS_LOCAL_EXCLUSION_AND_ACL_NO_GIT_NO_SECRET_OUTPUT'
