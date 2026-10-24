[CmdletBinding()]
param([switch]$Publish)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskSettings = & (Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1') -Scope All -PassThru
if ($taskSettings['SPRING_DATASOURCE_URL'] -cne 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1' -or
    $taskSettings['SPRING_FLYWAY_URL'] -cne 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1' -or
    $taskSettings['SPRING_FLYWAY_USERNAME'] -cne 'rf_migrator' -or
    $taskSettings['RF_TEST_DATASOURCE_URL'] -cne 'jdbc:postgresql://127.0.0.1:5432/ruta_fija_test') {
    throw 'ADMIN_TERMS_LOCAL_TARGET_REFUSED'
}
$taskPrevious = @{}
$taskVariables = @{
    RF_PUBLICATION_URL = $taskSettings['SPRING_FLYWAY_URL']
    RF_PUBLICATION_PASSWORD = $taskSettings['SPRING_FLYWAY_PASSWORD']
    RF_PUBLICATION_TEST_PASSWORD = $(if ($taskSettings.Contains('RF_TEST_MIGRATOR_PASSWORD')) {
        $taskSettings['RF_TEST_MIGRATOR_PASSWORD']
    } else { $taskSettings['SPRING_FLYWAY_PASSWORD'] })
}
try {
    foreach ($taskName in $taskVariables.Keys) {
        $taskPrevious[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
        [Environment]::SetEnvironmentVariable($taskName, $taskVariables[$taskName], 'Process')
    }
    $taskMode = if ($Publish) { '--publish' } else { '--check' }
    & python (Join-Path $PSScriptRoot 'publish_ruta_fija_admin_terms_local.py') $taskMode
    if ($LASTEXITCODE -ne 0) { throw 'ADMIN_TERMS_LOCAL_PUBLICATION_FAILED; revisar evidencia local si fue creada.' }
}
finally {
    foreach ($taskName in $taskPrevious.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskPrevious[$taskName], 'Process')
    }
}
