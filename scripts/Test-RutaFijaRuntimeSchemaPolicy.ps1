[CmdletBinding()]
param([switch]$Execute, [string]$EvidenceFile)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $Execute) { Write-Output 'RUNTIME_SCHEMA_POLICY=PLAN_SYNTHETIC_NO_DATABASE'; return }
$root = Split-Path -Parent $PSScriptRoot
if (-not $EvidenceFile -or (Test-Path -LiteralPath $EvidenceFile)) { throw 'CREATE_NEW_EVIDENCE_PATH_REQUIRED' }
$source = Join-Path $PSScriptRoot 'RutaFijaRuntimeSchemaPolicy.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if (@($errors).Count) { throw 'RUNTIME_SOURCE_PARSE_FAILED' }
$definition = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-RuntimeSchemaObservation' }, $false))
if ($definition.Count -ne 1) { throw 'EXACT_POLICY_FUNCTION_REQUIRED' }
# Extract only the pure policy: never dot-source the launcher or invoke psql.
Invoke-Expression $definition[0].Extent.Text
$versions = (1..50) -join ','
$normal = 'solucion_ruta_fija_1|' + $versions + '|50|0|'
$test = 'ruta_fija_test|' + $versions + '|51|0|R__native_test_role_privileges.sql'
$cases = @(
    @{name='Normal V50 accepts 50 runtime migrations without test grants';db='solucion_ruta_fija_1';rows=@($normal);code=0;test=$false;accept=$true},
    @{name='Test V50 accepts its exact role-privilege repeatable';db='ruta_fija_test';rows=@($test);code=0;test=$true;accept=$true},
    @{name='Native V12 remains rejected';db='solucion_ruta_fija_1';rows=@('solucion_ruta_fija_1|'+((1..12)-join',')+'|12|0|');code=0;test=$false;accept=$false},
    @{name='Normal database refuses test-only repeatable';db='solucion_ruta_fija_1';rows=@($normal.Replace('|50|0|','|51|0|R__native_test_role_privileges.sql'));code=0;test=$false;accept=$false},
    @{name='Test database requires exact repeatable script identity';db='ruta_fija_test';rows=@($test.Replace('R__native_test_role_privileges.sql','R__different.sql'));code=0;test=$true;accept=$false},
    @{name='Test database refuses missing role-privilege repeatable';db='ruta_fija_test';rows=@('ruta_fija_test|'+$versions+'|50|0|');code=0;test=$true;accept=$false},
    @{name='Failed migration refuses startup';db='ruta_fija_test';rows=@($test.Replace('|51|0|','|51|1|'));code=0;test=$true;accept=$false},
    @{name='Database identity cannot be swapped';db='ruta_fija_test';rows=@($normal);code=0;test=$false;accept=$false},
    @{name='Failed SELECT refuses startup even with plausible output';db='solucion_ruta_fija_1';rows=@($normal);code=1;test=$false;accept=$false},
    @{name='Empty SELECT result refuses startup';db='solucion_ruta_fija_1';rows=@();code=0;test=$false;accept=$false},
    @{name='Extra output refuses partial certification';db='solucion_ruta_fija_1';rows=@($normal,'extra');code=0;test=$false;accept=$false},
    @{name='Missing or unexpected version refuses startup';db='solucion_ruta_fija_1';rows=@($normal.Replace('49,50','49,51'));code=0;test=$false;accept=$false}
)
$results = @()
foreach ($case in $cases) {
    $accepted = $true
    try { Assert-RuntimeSchemaObservation -Database $case.db -Rows $case.rows -ExitCode $case.code -TestDatabase $case.test }
    catch {
        if ($_.Exception.Message -notlike 'RUNTIME_SCHEMA_NOT_V50_WITH_PROFILE_SPECIFIC_REPEATABLES*') { throw }
        $accepted = $false
    }
    if ($accepted -ne $case.accept) { throw ('RUNTIME_SCHEMA_POLICY_FAILED: ' + $case.name) }
    $results += [ordered]@{name=$case.name;passed=$true;accepted=$accepted}
}
$record = [ordered]@{checkedAt=[DateTimeOffset]::UtcNow.ToString('o');result='PASS_PROFILE_AWARE_V50_SCHEMA_POLICY';checksPassed=$results.Count;checks=$results;policySha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant();runtimeSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeRuntime.ps1') -Algorithm SHA256).Hash.ToLowerInvariant();databaseConnected=$false;serviceOrApplicationStarted=$false;sourceFunctionExtracted=$true}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($EvidenceFile), ($record | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
Write-Output ('RUNTIME_SCHEMA_POLICY=PASS_' + $results.Count + '_SYNTHETIC_CASES_NO_DATABASE')
