[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Test')]
    [string]$Action = 'Test',

    [string]$ConfigPath,

    [ValidateRange(15, 180)]
    [int]$StartupTimeoutSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

$nativeImportScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$f13AuditScript = Join-Path $PSScriptRoot 'Test-RutaFijaMigratedSchemaF13.ps1'
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$runtimeDirectory = Join-Path $repoRoot '.runtime'
$statePath = Join-Path $runtimeDirectory 'ruta-fija-native-runtime.json'

$backendPort = 8080
$frontendPort = 4200
$backendHealthUri = 'http://127.0.0.1:8080/actuator/health'
$frontendUri = 'http://localhost:4200/'
$expectedDevelopmentUrl = 'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1'

function Get-ProcessByIdSafely {
    param([int]$ProcessId)

    try {
        return Get-Process -Id $ProcessId -ErrorAction Stop
    } catch [System.ArgumentException] {
        return $null
    } catch [Microsoft.PowerShell.Commands.ProcessCommandException] {
        return $null
    }
}

function Get-ListeningProcessId {
    param([ValidateRange(1, 65535)][int]$Port)

    $listeners = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -eq $Port }
    )
    $owners = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($owners.Count -gt 1) {
        throw "El puerto $Port tiene más de un proceso en escucha; F1.1B no puede administrarlo con seguridad."
    }
    if ($owners.Count -eq 0) {
        return $null
    }
    return [int]$owners[0]
}

function Get-ProcessCommandLine {
    param([int]$ProcessId)

    $record = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if ($null -eq $record -or $null -eq $record.CommandLine) {
        return ''
    }
    return [string]$record.CommandLine
}

function Get-JavaExecutable {
    $launcher = (Get-Command java.exe -ErrorAction Stop).Source
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $details = & $launcher -XshowSettings:properties -version 2>&1
        $javaExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($javaExitCode -ne 0) {
        throw 'No se pudo resolver la instalación real del JDK.'
    }

    $javaHome = $null
    foreach ($detail in $details) {
        if ([string]$detail -match '^\s*java\.home = (.+?)\s*$') {
            $javaHome = $Matches[1]
            break
        }
    }
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        throw 'Java no informó java.home; F1.1B no registrará un PID del lanzador del PATH.'
    }

    $javaPath = Join-Path $javaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) {
        throw 'No se encontró java.exe dentro del JDK resuelto.'
    }
    return $javaPath
}

function Get-RuntimeState {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        return $null
    }

    $raw = (Get-Content -LiteralPath $statePath -Raw -Encoding UTF8).Trim()
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw 'El estado de ejecución nativa está vacío. Ejecute finalizar_ruta_fija.bat y vuelva a iniciar.'
    }

    try {
        $state = $raw | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw 'El estado de ejecución nativa no es JSON válido. F1.1B no administrará procesos no verificables.'
    }

    if ([int]$state.schemaVersion -ne 1 -or
        [string]$state.projectRoot -ne [IO.Path]::GetFullPath($repoRoot)) {
        throw 'El estado de ejecución nativa no pertenece a este proyecto o versión.'
    }

    foreach ($serviceName in @('backend', 'frontend')) {
        $serviceProperty = $state.PSObject.Properties[$serviceName]
        if ($null -eq $serviceProperty) {
            throw "El estado nativo no contiene el servicio $serviceName."
        }
        $service = $serviceProperty.Value
        foreach ($propertyName in @('processId', 'port', 'commandMarker')) {
            if ($null -eq $service.PSObject.Properties[$propertyName] -or
                [string]::IsNullOrWhiteSpace([string]$service.$propertyName)) {
                throw "El estado nativo de $serviceName está incompleto."
            }
        }
    }

    return $state
}

function Remove-RuntimeState {
    if (Test-Path -LiteralPath $statePath -PathType Leaf) {
        Remove-Item -LiteralPath $statePath -Force
    }
}

function Write-RuntimeState {
    param([Parameter(Mandatory)]$State)

    [IO.Directory]::CreateDirectory($runtimeDirectory) | Out-Null
    $State | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8 -NoNewline
}

function Get-ManagedServiceState {
    param(
        [Parameter(Mandatory)]$State,
        [ValidateSet('backend', 'frontend')]
        [string]$ServiceName
    )

    $service = $State.PSObject.Properties[$ServiceName].Value
    $processId = [int]$service.processId
    $port = [int]$service.port
    $commandMarker = [string]$service.commandMarker
    $process = Get-ProcessByIdSafely -ProcessId $processId
    $portOwner = Get-ListeningProcessId -Port $port
    $commandLine = if ($null -ne $process) { Get-ProcessCommandLine -ProcessId $processId } else { '' }

    $expectedProcessName = if ($ServiceName -eq 'backend') { 'java' } else { 'node' }
    $processNameMatches = $null -ne $process -and $process.ProcessName -ieq $expectedProcessName
    $commandLineMatches = -not [string]::IsNullOrWhiteSpace($commandLine) -and
        $commandLine.IndexOf($commandMarker, [StringComparison]::OrdinalIgnoreCase) -ge 0
    $portMatches = $null -ne $portOwner -and $portOwner -eq $processId

    return [pscustomobject]@{
        ServiceName = $ServiceName
        ProcessId = $processId
        Port = $port
        ProcessExists = $null -ne $process
        PortOwner = $portOwner
        IsManaged = $processNameMatches -and $commandLineMatches -and $portMatches
        Reason = if ($null -eq $process) {
            'el proceso ya no existe'
        } elseif (-not $processNameMatches) {
            "el PID no corresponde a $expectedProcessName"
        } elseif (-not $commandLineMatches) {
            'la línea de comando no coincide con el proceso iniciado por Ruta Fija'
        } elseif (-not $portMatches) {
            'el PID no controla el puerto esperado'
        } else {
            'válido'
        }
    }
}

function Invoke-Endpoint {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$Label,
        [ValidateRange(1, 180)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][scriptblock]$Validator
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastFailure = 'sin respuesta'
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if (& $Validator $response) {
                return $response
            }
            $lastFailure = "HTTP $($response.StatusCode) con contenido no esperado"
        } catch {
            $lastFailure = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "$Label no respondió correctamente en $Uri antes de $TimeoutSeconds segundos: $lastFailure"
}

function Get-HttpResponseText {
    param([Parameter(Mandatory)]$Response)

    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString([byte[]]$Response.Content)
    }
    return [string]$Response.Content
}

function Test-BackendHealth {
    param([ValidateRange(1, 180)][int]$TimeoutSeconds = 10)

    $validator = {
        param($response)
        try {
            $payload = (Get-HttpResponseText -Response $response) | ConvertFrom-Json -ErrorAction Stop
            return $response.StatusCode -eq 200 -and [string]$payload.status -eq 'UP'
        } catch {
            return $false
        }
    }
    return Invoke-Endpoint -Uri $backendHealthUri -Label 'El backend' -TimeoutSeconds $TimeoutSeconds -Validator $validator
}

function Test-FrontendPage {
    param([ValidateRange(1, 180)][int]$TimeoutSeconds = 10)

    $validator = {
        param($response)
        return $response.StatusCode -eq 200 -and (Get-HttpResponseText -Response $response) -match '<rf-root'
    }
    return Invoke-Endpoint -Uri $frontendUri -Label 'El CRM Angular' -TimeoutSeconds $TimeoutSeconds -Validator $validator
}

function Test-RutaFijaRuntime {
    param(
        $State = (Get-RuntimeState),
        [switch]$Quiet
    )

    if ($null -eq $State) {
        throw 'No existe un estado nativo activo. Ejecute iniciar_ruta_fija.bat primero.'
    }

    $backend = Get-ManagedServiceState -State $State -ServiceName 'backend'
    $frontend = Get-ManagedServiceState -State $State -ServiceName 'frontend'
    foreach ($service in @($backend, $frontend)) {
        if (-not $service.IsManaged) {
            throw "El estado de $($service.ServiceName) no es seguro: $($service.Reason)."
        }
    }

    $null = Test-BackendHealth -TimeoutSeconds 10
    $null = Test-FrontendPage -TimeoutSeconds 10

    if (-not $Quiet) {
        Write-Output 'F1_1B_RUNTIME=PASS backend=UP frontend=UP ports=8080,4200'
        Write-Output 'CRM=http://localhost:4200 API=http://127.0.0.1:8080'
    }
}

function Get-ValidatedRuntimeSettings {
    $settings = & $nativeImportScript -ConfigPath $ConfigPath -PassThru -Scope Runtime
    if ($null -eq $settings) {
        throw 'No se pudo leer la configuración nativa local.'
    }

    $expected = [ordered]@{
        SPRING_PROFILES_ACTIVE = 'local'
        SPRING_DATASOURCE_URL = $expectedDevelopmentUrl
        SPRING_DATASOURCE_USERNAME = 'rf_app'
        SPRING_FLYWAY_URL = $expectedDevelopmentUrl
        SPRING_FLYWAY_USERNAME = 'rf_migrator'
        APP_SEED_ENABLED = 'false'
        APP_CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
        REFRESH_COOKIE_SECURE = 'false'
    }
    foreach ($entry in $expected.GetEnumerator()) {
        if (-not $settings.Contains($entry.Key) -or $settings[$entry.Key] -ne $entry.Value) {
            throw "La configuración nativa no cumple $($entry.Key) para F1.1B."
        }
    }
    return $settings
}

function Set-RuntimeEnvironment {
    param([Parameter(Mandatory)]$Settings)

    $previousValues = [ordered]@{}
    foreach ($entry in $Settings.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    return $previousValues
}

function Restore-RuntimeEnvironment {
    param([Parameter(Mandatory)]$PreviousValues)

    foreach ($entry in $PreviousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

function Assert-StartCanProceed {
    $state = Get-RuntimeState
    if ($null -ne $state) {
        $backend = Get-ManagedServiceState -State $state -ServiceName 'backend'
        $frontend = Get-ManagedServiceState -State $state -ServiceName 'frontend'
        if ($backend.IsManaged -and $frontend.IsManaged) {
            Test-RutaFijaRuntime -State $state -Quiet
            Write-Host 'F1_1B_ALREADY_RUNNING=PASS backend=UP frontend=UP'
            return $false
        }

        $hasLiveProcess = @(@($backend, $frontend) | Where-Object {
                $_.ProcessExists -or $null -ne $_.PortOwner
            }).Count -gt 0
        if ($hasLiveProcess) {
            throw 'Existe un estado nativo parcial o no verificable. Ejecute finalizar_ruta_fija.bat; no se iniciará otro proceso.'
        }
        Remove-RuntimeState
    }

    foreach ($port in @($backendPort, $frontendPort)) {
        $owner = Get-ListeningProcessId -Port $port
        if ($null -ne $owner) {
            throw "El puerto $port ya está ocupado por PID $owner. F1.1B no detendrá procesos ajenos."
        }
    }
    return $true
}

function Stop-ProcessAndWait {
    param(
        [int]$ProcessId,
        [ValidateRange(1, 60)][int]$TimeoutSeconds = 20
    )

    $process = Get-ProcessByIdSafely -ProcessId $ProcessId
    if ($null -eq $process) {
        return
    }

    Stop-Process -Id $ProcessId -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($null -eq (Get-ProcessByIdSafely -ProcessId $ProcessId)) {
            return
        }
        Start-Sleep -Milliseconds 250
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    $forceDeadline = [DateTime]::UtcNow.AddSeconds(5)
    while ([DateTime]::UtcNow -lt $forceDeadline) {
        if ($null -eq (Get-ProcessByIdSafely -ProcessId $ProcessId)) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw "No fue posible detener el PID administrado $ProcessId."
}

function Stop-BackendControlled {
    param([int]$ProcessId)

    # Windows no ofrece una señal POSIX equivalente a SIGTERM para un proceso
    # desacoplado. El control consiste en verificar PID, ejecutable, comando y
    # puerto antes de detenerlo; server.shutdown mantiene el comportamiento
    # graceful para mecanismos de cierre compatibles en despliegues posteriores.
    Stop-ProcessAndWait -ProcessId $ProcessId -TimeoutSeconds 20
}

function Assert-PortIsFree {
    param([ValidateRange(1, 65535)][int]$Port)

    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        if ($null -eq (Get-ListeningProcessId -Port $Port)) {
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    $owner = Get-ListeningProcessId -Port $Port
    throw "El puerto $Port sigue ocupado por PID $owner después del cierre controlado."
}

function Invoke-NativeStart {
    $previousEnvironment = [ordered]@{}
    $startedProcesses = [System.Collections.Generic.List[object]]::new()
    $stateWritten = $false

    try {
        $settings = Get-ValidatedRuntimeSettings
        $previousEnvironment = Set-RuntimeEnvironment -Settings $settings
        if (-not (Assert-StartCanProceed)) {
            return
        }

        & $f13AuditScript -ConfigPath $ConfigPath
        if ($LASTEXITCODE -ne 0) {
            throw 'La auditoría F1.3 no fue aprobada; no se iniciará el sistema.'
        }

        if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules\@angular\cli\bin\ng.js') -PathType Leaf)) {
            throw 'Faltan dependencias del CRM. Ejecute npm.cmd ci dentro de frontend antes de iniciar.'
        }
        [IO.Directory]::CreateDirectory($runtimeDirectory) | Out-Null

        Push-Location $backendRoot
        try {
            & .\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests package
            if ($LASTEXITCODE -ne 0) {
                throw 'No se pudo empaquetar el backend para el arranque nativo.'
            }
        } finally {
            Pop-Location
        }

        $backendJar = @(
            Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target') -Filter '*.jar' -File |
                Where-Object { $_.Name -notlike '*.original' } |
                Sort-Object LastWriteTimeUtc -Descending |
                Select-Object -First 1
        )
        if ($backendJar.Count -ne 1) {
            throw 'No se encontró un JAR ejecutable del backend después del empaquetado.'
        }

        $javaPath = Get-JavaExecutable
        $backendOutput = Join-Path $runtimeDirectory 'backend.stdout.log'
        $backendError = Join-Path $runtimeDirectory 'backend.stderr.log'
        Set-Content -LiteralPath $backendOutput -Value $null -Encoding UTF8
        Set-Content -LiteralPath $backendError -Value $null -Encoding UTF8
        $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"{0}"' -f $backendJar[0].FullName), '--spring.profiles.active=local', '--server.address=127.0.0.1') -WorkingDirectory $backendRoot -RedirectStandardOutput $backendOutput -RedirectStandardError $backendError -WindowStyle Hidden -PassThru
        $startedProcesses.Add($backendProcess)

        $null = Test-BackendHealth -TimeoutSeconds $StartupTimeoutSeconds
        if ((Get-ListeningProcessId -Port $backendPort) -ne $backendProcess.Id) {
            throw 'El backend saludable no coincide con el PID iniciado por F1.1B.'
        }

        $node = Get-Command node.exe -ErrorAction Stop
        $angularCli = Join-Path $frontendRoot 'node_modules\@angular\cli\bin\ng.js'
        $frontendOutput = Join-Path $runtimeDirectory 'frontend.stdout.log'
        $frontendError = Join-Path $runtimeDirectory 'frontend.stderr.log'
        Set-Content -LiteralPath $frontendOutput -Value $null -Encoding UTF8
        Set-Content -LiteralPath $frontendError -Value $null -Encoding UTF8
        $frontendProcess = Start-Process -FilePath $node.Source -ArgumentList @(('"{0}"' -f $angularCli), 'serve', '--host', 'localhost', '--port', '4200', '--configuration', 'development') -WorkingDirectory $frontendRoot -RedirectStandardOutput $frontendOutput -RedirectStandardError $frontendError -WindowStyle Hidden -PassThru
        $startedProcesses.Add($frontendProcess)

        $null = Test-FrontendPage -TimeoutSeconds $StartupTimeoutSeconds
        if ((Get-ListeningProcessId -Port $frontendPort) -ne $frontendProcess.Id) {
            throw 'El CRM saludable no coincide con el PID iniciado por F1.1B.'
        }

        $state = [ordered]@{
            schemaVersion = 1
            projectRoot = [IO.Path]::GetFullPath($repoRoot)
            startedAtUtc = [DateTime]::UtcNow.ToString('o')
            backend = [ordered]@{
                processId = $backendProcess.Id
                port = $backendPort
                commandMarker = $backendJar[0].Name
            }
            frontend = [ordered]@{
                processId = $frontendProcess.Id
                port = $frontendPort
                commandMarker = 'ng.js'
            }
        }
        Write-RuntimeState -State $state
        $stateWritten = $true
        Test-RutaFijaRuntime -State ($state | ConvertTo-Json -Depth 4 | ConvertFrom-Json)
        Write-Output 'F1_1B_START=PASS procesos=controlados docker=0'
    } catch {
        $startupFailure = $_
        for ($index = $startedProcesses.Count - 1; $index -ge 0; $index--) {
            $process = $startedProcesses[$index]
            if ($null -ne (Get-ProcessByIdSafely -ProcessId $process.Id)) {
                try {
                    Stop-ProcessAndWait -ProcessId $process.Id -TimeoutSeconds 5
                } catch {
                    Write-Warning "No se pudo limpiar el PID $($process.Id) después de un inicio fallido."
                }
            }
        }
        if ($stateWritten) {
            Remove-RuntimeState
        }
        throw $startupFailure
    } finally {
        if ($previousEnvironment.Count -gt 0) {
            Restore-RuntimeEnvironment -PreviousValues $previousEnvironment
        }
    }
}

function Invoke-NativeStop {
    $state = Get-RuntimeState
    if ($null -eq $state) {
        foreach ($port in @($backendPort, $frontendPort)) {
            $owner = Get-ListeningProcessId -Port $port
            if ($null -ne $owner) {
                throw "El puerto $port está ocupado por PID $owner sin estado administrado. F1.1B no lo detendrá."
            }
        }
        Write-Output 'F1_1B_STOP=PASS state=absent'
        return
    }

    $backend = Get-ManagedServiceState -State $state -ServiceName 'backend'
    $frontend = Get-ManagedServiceState -State $state -ServiceName 'frontend'
    $services = @($frontend, $backend)

    foreach ($service in $services) {
        if ($service.ProcessExists -or $null -ne $service.PortOwner) {
            if (-not $service.IsManaged) {
                throw "No se puede detener $($service.ServiceName): $($service.Reason)."
            }
        }
    }

    if ($frontend.IsManaged) {
        Stop-ProcessAndWait -ProcessId $frontend.ProcessId -TimeoutSeconds 20
    }
    if ($backend.IsManaged) {
        Stop-BackendControlled -ProcessId $backend.ProcessId
    }

    Assert-PortIsFree -Port $frontendPort
    Assert-PortIsFree -Port $backendPort
    Remove-RuntimeState
    Write-Output 'F1_1B_STOP=PASS backend=stopped frontend=stopped'
}

switch ($Action) {
    'Start' { Invoke-NativeStart }
    'Stop' { Invoke-NativeStop }
    'Test' { Test-RutaFijaRuntime }
}
