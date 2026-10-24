# Shared pure observation policy. Loading this file performs no I/O.
function Get-RuntimeSchemaObservationQuery {
    return "SELECT current_database()||'|'||(SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version IS NOT NULL)||'|'||(SELECT count(*) FROM flyway_schema_history WHERE success)||'|'||(SELECT count(*) FROM flyway_schema_history WHERE NOT success)||'|'||coalesce((SELECT string_agg(script,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version IS NULL),'');"
}

function Assert-RuntimeSchemaObservation {
    param(
        [Parameter(Mandatory)][string]$Database,
        [AllowEmptyCollection()][string[]]$Rows,
        [int]$ExitCode,
        [bool]$TestDatabase
    )
    $expectedDatabase = if ($TestDatabase) { 'ruta_fija_test' } else { 'solucion_ruta_fija_1' }
    # Native V51 adds controlled identity-review correction. The historical test
    # database remains V1..V50 plus its one exact repeatable; Start never migrates.
    $expectedVersions = if ($TestDatabase) { (1..50) -join ',' } else { (1..51) -join ',' }
    $expectedCount = 51
    $expectedRepeatable = if ($TestDatabase) { 'R__native_test_role_privileges.sql' } else { '' }
    $expected = $expectedDatabase + '|' + $expectedVersions + '|' + $expectedCount + '|0|' + $expectedRepeatable
    if ($Database -cne $expectedDatabase -or $ExitCode -ne 0 -or @($Rows).Count -ne 1 -or [string]$Rows[0] -cne $expected) {
        throw 'RUNTIME_SCHEMA_NOT_NATIVE_V51_OR_HISTORICAL_TEST_V50; no migration is executed by Start'
    }
}
