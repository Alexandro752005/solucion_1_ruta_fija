[CmdletBinding()]
param(
    [string]$NativeConfigPath,
    [string]$BootstrapConfigPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($NativeConfigPath)) {
    $NativeConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}
if ([string]::IsNullOrWhiteSpace($BootstrapConfigPath)) {
    $BootstrapConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-bootstrap.env'
}

$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$bootstrapImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaBootstrapEnvironment.ps1'
$expectedListenAddresses = @('127.0.0.1', '::1')

function Get-PsqlPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'PostgreSQL\16\bin\psql.exe'),
        (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }

    $psql = $candidates | Select-Object -First 1
    if (-not $psql) {
        throw 'No se encontro psql.exe de PostgreSQL 16.'
    }
    return $psql
}

function Test-SecretPathIsPrivate {
    param([Parameter(Mandatory)][string]$Path)

    $fullRepositoryPath = [IO.Path]::GetFullPath($repoRoot).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $fullSecretPath = [IO.Path]::GetFullPath($Path)
    $repositoryPrefix = $fullRepositoryPath + [IO.Path]::DirectorySeparatorChar
    if (-not $fullSecretPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El archivo de secretos debe permanecer dentro del repositorio local.'
    }

    $relativePath = $fullSecretPath.Substring($repositoryPrefix.Length).Replace('\', '/')
    Push-Location $repoRoot
    try {
        & git check-ignore -q -- $relativePath
        if ($LASTEXITCODE -ne 0) {
            throw "El archivo privado no esta ignorado por Git: $relativePath"
        }
        $tracked = @(& git ls-files -- $relativePath)
        if ($tracked.Count -gt 0) {
            throw "El archivo privado esta versionado: $relativePath"
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-BootstrapQuery {
    param(
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Settings,
        [Parameter(Mandatory)][string]$Psql
    )

    $arguments = @(
        '-X', '-w', '-v', 'ON_ERROR_STOP=1',
        '-h', $Settings['RUTA_FIJA_BOOTSTRAP_HOST'],
        '-p', $Settings['RUTA_FIJA_BOOTSTRAP_PORT'],
        '-U', $Settings['RUTA_FIJA_BOOTSTRAP_USERNAME'],
        '-d', 'postgres', '-At', '-F', '|', '-c', $Query
    )
    $output = & $Psql @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL rechazo la auditoria de seguridad F1.6.'
    }
    return @($output | Where-Object { $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_) })
}

function Assert-NoTrackedSecrets {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$NativeSettings,
        [Parameter(Mandatory)][System.Collections.IDictionary]$BootstrapSettings
    )

    Push-Location $repoRoot
    try {
        $trackedPaths = @(& git ls-files)
        $forbiddenPaths = @(
            $trackedPaths | Where-Object {
                $_ -eq '.env' -or
                ($_ -like '.env.*' -and $_ -ne '.env.example') -or
                $_ -like 'backend/.local/*' -or
                $_ -like '.runtime/*' -or
                $_ -match '(?i)(^|/).+\.(pem|key|p12|pfx)$'
            }
        )
        if ($forbiddenPaths.Count -gt 0) {
            throw 'Git contiene una ruta reservada para secretos, certificados o runtime.'
        }

        $secretKeys = @(
            'SPRING_DATASOURCE_PASSWORD',
            'SPRING_FLYWAY_PASSWORD',
            'RF_TEST_DATASOURCE_PASSWORD',
            'RF_TEST_MIGRATOR_PASSWORD',
            'JWT_SECRET_BASE64'
        )
        $secretValues = @(
            foreach ($key in $secretKeys) {
                if ($NativeSettings.Contains($key) -and $NativeSettings[$key].Length -ge 12) {
                    $NativeSettings[$key]
                }
            }
            if ($BootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'].Length -ge 12) {
                $BootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD']
            }
        ) | Select-Object -Unique

        $leakedFiles = [System.Collections.Generic.HashSet[string]]::new(
            [StringComparer]::OrdinalIgnoreCase
        )
        foreach ($secretValue in $secretValues) {
            $matches = @(& git grep -l -F -- $secretValue 2>$null)
            $exitCode = $LASTEXITCODE
            if ($exitCode -eq 0) {
                foreach ($match in $matches) {
                    $null = $leakedFiles.Add($match)
                }
            }
            elseif ($exitCode -ne 1) {
                throw 'No se pudo comprobar higiene de secretos sobre los archivos versionados.'
            }
        }
        if ($leakedFiles.Count -gt 0) {
            throw 'Se detecto un valor secreto local dentro de Git.'
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $NativeConfigPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $BootstrapConfigPath -PathType Leaf)) {
    throw 'F1.6 requiere las configuraciones privadas local y bootstrap.'
}

Test-SecretPathIsPrivate -Path $NativeConfigPath
Test-SecretPathIsPrivate -Path $BootstrapConfigPath

$nativeSettings = & $nativeImportScript -ConfigPath $NativeConfigPath -PassThru -Scope All
$bootstrapSettings = & $bootstrapImportScript -ConfigPath $BootstrapConfigPath -PassThru
if ($null -eq $nativeSettings -or $null -eq $bootstrapSettings) {
    throw 'No se pudo leer la configuracion privada para F1.6.'
}

Assert-NoTrackedSecrets -NativeSettings $nativeSettings -BootstrapSettings $bootstrapSettings

$psql = Get-PsqlPath
$previousPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable(
        'PGPASSWORD',
        $bootstrapSettings['RUTA_FIJA_BOOTSTRAP_PASSWORD'],
        'Process'
    )

    $settingsRow = (@(Invoke-BootstrapQuery -Psql $psql -Settings $bootstrapSettings -Query "
        SELECT current_setting('listen_addresses') || '|' ||
               current_setting('port') || '|' ||
               current_setting('password_encryption');
    "))[0].Trim()
    $settingsParts = $settingsRow.Split('|')
    if ($settingsParts.Count -ne 3 -or
        $settingsParts[1] -ne '5432' -or
        $settingsParts[2] -ne 'scram-sha-256') {
        throw 'PostgreSQL no conserva puerto 5432 y autenticacion SCRAM para F1.6.'
    }
    $listenAddresses = @(
        $settingsParts[0].Split(',') |
            ForEach-Object { $_.Trim().Trim('"', '''') } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    if ($listenAddresses.Count -ne $expectedListenAddresses.Count -or
        @($listenAddresses | Where-Object { $_ -notin $expectedListenAddresses }).Count -gt 0 -or
        @($expectedListenAddresses | Where-Object { $_ -notin $listenAddresses }).Count -gt 0) {
        throw 'PostgreSQL no esta restringido exclusivamente a 127.0.0.1 y ::1.'
    }

    $hbaRules = Invoke-BootstrapQuery -Psql $psql -Settings $bootstrapSettings -Query "
        SELECT type || '|' || coalesce(address::text, '') || '|' || auth_method
          FROM pg_hba_file_rules()
         WHERE error IS NULL
           AND type IN ('host', 'hostssl', 'hostnossl')
         ORDER BY line_number;
    "
    if ($hbaRules.Count -eq 0) {
        throw 'PostgreSQL no devolvio reglas HBA de red para F1.6.'
    }
    $hbaAddresses = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase
    )
    foreach ($rule in $hbaRules) {
        $parts = $rule.Trim().Split('|')
        if ($parts.Count -ne 3 -or
            $parts[1] -notin $expectedListenAddresses -or
            $parts[2] -ne 'scram-sha-256') {
            throw 'pg_hba.conf contiene una regla de red no local o sin SCRAM.'
        }
        $null = $hbaAddresses.Add($parts[1])
    }
    foreach ($expectedAddress in $expectedListenAddresses) {
        if (-not $hbaAddresses.Contains($expectedAddress)) {
            throw 'pg_hba.conf no conserva todas las rutas loopback esperadas.'
        }
    }

    $roleRows = Invoke-BootstrapQuery -Psql $psql -Settings $bootstrapSettings -Query "
        SELECT rolname || '|' || rolsuper || '|' || rolcreatedb || '|' ||
               rolcreaterole || '|' || rolreplication || '|' || rolbypassrls || '|' || rolcanlogin
          FROM pg_roles
         WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test')
         ORDER BY rolname;
    "
    $expectedRoles = @(
        'rf_app|f|f|f|f|f|t',
        'rf_migrator|f|f|f|f|f|t',
        'rf_test|f|f|f|f|f|t'
    )
    if ((@($roleRows | ForEach-Object { $_.Trim() }) -join ';') -ne ($expectedRoles -join ';')) {
        throw 'Los roles de Ruta Fija no cumplen minimo privilegio F1.6.'
    }

    $listeners = @(& netstat -ano -p tcp | Where-Object {
            $_ -match '^\s*TCP\s+' -and $_ -match ':5432\s+'
        })
    if ($listeners.Count -eq 0 -or
        @($listeners | Where-Object { $_ -match '(0\.0\.0\.0|\[::\]|:::):5432' }).Count -gt 0 -or
        @($listeners | Where-Object { $_ -match '127\.0\.0\.1:5432' }).Count -eq 0) {
        throw 'La escucha efectiva de PostgreSQL no esta limitada a loopback.'
    }
}
finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPgPassword, 'Process')
}

$operationalDocumentation = @(
    (Join-Path $repoRoot 'README.md'),
    (Join-Path $repoRoot 'backend\README.md'),
    (Join-Path $repoRoot 'docs\Uso del Sistema.md')
)
foreach ($document in $operationalDocumentation) {
    if (-not (Test-Path -LiteralPath $document -PathType Leaf)) {
        throw "Falta la documentacion operacional F1.6: $document"
    }
    $content = Get-Content -LiteralPath $document -Raw -Encoding UTF8
    if ($content -match '(?im)^.*docker compose.*$' -or
        $content -match '(?im)^.*docker desktop.*$' -or
        $content -match '(?im)^.*testcontainers.*$') {
        throw 'La documentacion operacional todavia instruye usar Docker o Testcontainers.'
    }
}

$ciPath = Join-Path $repoRoot '.github\workflows\ci.yml'
$ciContent = Get-Content -LiteralPath $ciPath -Raw -Encoding UTF8
if ($ciContent -match '(?im)^.*docker compose.*$' -or
    $ciContent -match '(?im)^.*docker info.*$' -or
    $ciContent -match '(?im)^.*testcontainers.*$') {
    throw 'La CI todavia exige Docker o Testcontainers como ruta de pruebas.'
}

$tasksPath = Join-Path $repoRoot '.vscode\tasks.json'
$tasks = Get-Content -LiteralPath $tasksPath -Raw -Encoding UTF8 | ConvertFrom-Json
$requiredTaskLabels = @(
    'Ruta Fija: verificar pruebas nativas (F1.4)',
    'Ruta Fija: smoke proxy REST y WebSocket (F1.5)',
    'Ruta Fija: auditar seguridad local (F1.6)',
    'Ruta Fija: backup y recuperación post-migración (F1.7)'
)
$actualTaskLabels = @($tasks.tasks | ForEach-Object { $_.label })
foreach ($label in $requiredTaskLabels) {
    if ($label -notin $actualTaskLabels) {
        throw "Falta la tarea VS Code F1.6: $label"
    }
}

Write-Output 'F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native'
