[CmdletBinding()]
param(
    [ValidateSet('Verify', 'Stop')]
    [string]$Action = 'Verify',
    [string]$ConfigPath,
    [ValidateRange(20, 120)]
    [int]$StartupTimeoutSeconds = 75
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot 'backend\.local\ruta-fija-native.env'
}

$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$runtimeDirectory = Join-Path $repoRoot '.runtime'
$statePath = Join-Path $runtimeDirectory 'ruta-fija-f1-5-proxy-smoke.json'
$importScript = Join-Path $PSScriptRoot 'Import-RutaFijaNativeEnvironment.ps1'
$nativeTestScript = Join-Path $PSScriptRoot 'Invoke-RutaFijaNativeTest.ps1'

function Import-TestSettings {
    $settings = & $importScript -ConfigPath $ConfigPath -PassThru -Scope Test
    if ($null -eq $settings) {
        throw 'No se pudo leer la configuración aislada de F1.5.'
    }
    foreach ($entry in $settings.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    return $settings
}

function Assert-PortAvailable {
    param([Parameter(Mandatory)][int]$Port)

    $listeners = @(
        Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -eq $Port }
    )
    if ($listeners.Count -gt 0) {
        throw "F1.5 no inicia porque el puerto $Port pertenece a otro proceso. Cierra el runtime normal antes de verificar."
    }
}

function Resolve-JavaPath {
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $settingsOutput = & $javaCommand.Source -XshowSettings:properties -version 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) {
            throw 'java no pudo informar la ruta del JDK.'
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $match = [regex]::Match($settingsOutput, '(?m)^\s*java\.home\s*=\s*(.+?)\s*$')
    if (-not $match.Success) {
        throw 'No se pudo resolver el JDK real para F1.5.'
    }
    $javaPath = Join-Path $match.Groups[1].Value.Trim() 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaPath -PathType Leaf)) {
        throw 'El JDK resuelto no contiene java.exe.'
    }
    return $javaPath
}

function Wait-ForHttp {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )

    $limit = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 4
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $response
            }
        }
        catch {
            # El proceso sigue inicializándose.
        }
        Start-Sleep -Milliseconds 750
    } while ((Get-Date) -lt $limit)

    throw "F1.5 no recibió una respuesta sana de $Uri."
}

function Stop-ExpectedProcess {
    param(
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][string]$CommandMarker
    )

    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($process.CommandLine) -or $process.CommandLine -notlike "*$CommandMarker*") {
        throw "F1.5 se niega a cerrar el proceso $ProcessId porque su identidad no coincide."
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction Stop
}

function Stop-VerificationRuntime {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        return
    }

    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Stop-ExpectedProcess -ProcessId ([int]$state.frontendPid) -CommandMarker $state.frontendMarker
    Stop-ExpectedProcess -ProcessId ([int]$state.backendPid) -CommandMarker $state.backendMarker
    Remove-Item -LiteralPath $statePath -Force
}

function Start-VerificationRuntime {
    Assert-PortAvailable -Port 8080
    Assert-PortAvailable -Port 4200
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null

    $mavenWrapper = Join-Path $backendRoot 'mvnw.cmd'
    Push-Location $backendRoot
    try {
        $null = & $mavenWrapper --batch-mode --no-transfer-progress -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw 'F1.5 no pudo empaquetar el backend nativo.'
        }
    }
    finally {
        Pop-Location
    }

    $backendJar = @(
        Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target') -Filter 'ruta-fija-backend-*.jar' -File |
            Where-Object { $_.Name -notlike '*.original' } |
            Sort-Object LastWriteTime -Descending
    )[0]
    if ($null -eq $backendJar) {
        throw 'F1.5 no encontró el JAR del backend.'
    }

    $javaPath = Resolve-JavaPath
    $nodePath = (Get-Command node.exe -ErrorAction Stop).Source
    $angularCli = Join-Path $frontendRoot 'node_modules\@angular\cli\bin\ng.js'
    if (-not (Test-Path -LiteralPath $angularCli -PathType Leaf)) {
        throw 'F1.5 no encontró Angular CLI en frontend/node_modules.'
    }

    $backendOut = Join-Path $runtimeDirectory 'f1-5-backend.stdout.log'
    $backendErr = Join-Path $runtimeDirectory 'f1-5-backend.stderr.log'
    $frontendOut = Join-Path $runtimeDirectory 'f1-5-frontend.stdout.log'
    $frontendErr = Join-Path $runtimeDirectory 'f1-5-frontend.stderr.log'
    Set-Content -LiteralPath $backendOut -Value $null -Encoding UTF8
    Set-Content -LiteralPath $backendErr -Value $null -Encoding UTF8
    Set-Content -LiteralPath $frontendOut -Value $null -Encoding UTF8
    Set-Content -LiteralPath $frontendErr -Value $null -Encoding UTF8

    $env:SPRING_PROFILES_ACTIVE = 'test,dev'
    $env:APP_SEED_ENABLED = 'true'
    $env:DEMO_USER_PASSWORD = ('Rf!' + [Guid]::NewGuid().ToString('N') + '9a')
    $env:SERVER_PORT = '8080'
    $env:APP_CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
    $env:REFRESH_COOKIE_SECURE = 'false'

    $frontend = $null
    $backend = Start-Process -FilePath $javaPath -ArgumentList @(
        '-jar',
        ('"{0}"' -f $backendJar.FullName),
        '--spring.profiles.active=test,dev',
        '--server.address=127.0.0.1',
        '--server.port=8080'
    ) -WorkingDirectory $backendRoot -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr -WindowStyle Hidden -PassThru

    try {
        $null = Wait-ForHttp -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSeconds $StartupTimeoutSeconds
        $frontend = Start-Process -FilePath $nodePath -ArgumentList @(
            ('"{0}"' -f $angularCli),
            'serve',
            '--host', 'localhost',
            '--port', '4200',
            '--configuration', 'development'
        ) -WorkingDirectory $frontendRoot -RedirectStandardOutput $frontendOut -RedirectStandardError $frontendErr -WindowStyle Hidden -PassThru
        $page = Wait-ForHttp -Uri 'http://localhost:4200/' -TimeoutSeconds $StartupTimeoutSeconds
        if ($page.Content -notmatch '<rf-root') {
            throw 'F1.5 no recibió la aplicación Angular esperada.'
        }

        [ordered]@{
            backendPid = $backend.Id
            frontendPid = $frontend.Id
            backendMarker = '--spring.profiles.active=test,dev'
            frontendMarker = 'ng.js'
        } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8

        return [pscustomobject]@{
            DemoPassword = $env:DEMO_USER_PASSWORD
        }
    }
    catch {
        if ($null -ne $frontend) {
            Stop-ExpectedProcess -ProcessId $frontend.Id -CommandMarker 'ng.js'
        }
        Stop-ExpectedProcess -ProcessId $backend.Id -CommandMarker '--spring.profiles.active=test,dev'
        throw
    }
}

function Receive-StreamReady {
    param([Parameter(Mandatory)][string]$Ticket)

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $timeout = [System.Threading.CancellationTokenSource]::new()
    $timeout.CancelAfter([TimeSpan]::FromSeconds(12))
    try {
        $socket.Options.SetRequestHeader('Origin', 'http://localhost:4200')
        $uri = [Uri]('ws://localhost:4200/ws/operations?ticket=' + [Uri]::EscapeDataString($Ticket))
        $null = $socket.ConnectAsync($uri, $timeout.Token).GetAwaiter().GetResult()
        $buffer = [byte[]]::new(4096)
        $segment = [System.ArraySegment[byte]]::new($buffer)
        $result = $socket.ReceiveAsync($segment, $timeout.Token).GetAwaiter().GetResult()
        $payload = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count) | ConvertFrom-Json
        if ($payload.event -ne 'stream.ready') {
            throw 'F1.5 no recibió stream.ready del WebSocket.'
        }
        $null = $socket.CloseAsync(
            [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
            'verified',
            $timeout.Token
        ).GetAwaiter().GetResult()
    }
    finally {
        $socket.Dispose()
        $timeout.Dispose()
    }
}

function Assert-ReplayedTicketRejected {
    param([Parameter(Mandatory)][string]$Ticket)

    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $timeout = [System.Threading.CancellationTokenSource]::new()
    $timeout.CancelAfter([TimeSpan]::FromSeconds(8))
    $rejected = $false
    try {
        $socket.Options.SetRequestHeader('Origin', 'http://localhost:4200')
        $uri = [Uri]('ws://localhost:4200/ws/operations?ticket=' + [Uri]::EscapeDataString($Ticket))
        $null = $socket.ConnectAsync($uri, $timeout.Token).GetAwaiter().GetResult()
    }
    catch {
        $rejected = $true
    }
    finally {
        $socket.Dispose()
        $timeout.Dispose()
    }
    if (-not $rejected) {
        throw 'F1.5 aceptó la reutilización de un ticket WebSocket.'
    }
}

if ($Action -eq 'Stop') {
    Stop-VerificationRuntime
    Write-Output 'F1_5_STOP=PASS runtime=absent'
    exit 0
}

$settings = Import-TestSettings
& $nativeTestScript -Action Clean -ConfigPath $ConfigPath
if ($LASTEXITCODE -ne 0) {
    throw 'F1.5 no pudo preparar ruta_fija_test.'
}

try {
    $runtime = Start-VerificationRuntime
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $headers = @{ Origin = 'http://localhost:4200' }
    $loginParameters = @{
        Uri = 'http://localhost:4200/api/v1/auth/login'
        Method = 'Post'
        WebSession = $session
        Headers = $headers
        ContentType = 'application/json'
        Body = (@{ email = 'admin.norte@rutafija.local'; password = $runtime.DemoPassword } | ConvertTo-Json -Compress)
    }
    $login = Invoke-RestMethod @loginParameters
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
        throw 'F1.5 no recibió access token a través del proxy REST.'
    }

    $authorization = @{ Authorization = ('Bearer ' + $login.accessToken); Origin = 'http://localhost:4200' }
    $me = Invoke-RestMethod -Uri 'http://localhost:4200/api/v1/auth/me' -Method Get -Headers $authorization
    if ($me.role -ne 'ADMIN') {
        throw 'F1.5 no confirmó el usuario administrativo de prueba.'
    }

    $refreshParameters = @{
        Uri = 'http://localhost:4200/api/v1/auth/refresh'
        Method = 'Post'
        WebSession = $session
        Headers = $headers
    }
    $refresh = Invoke-RestMethod @refreshParameters
    if ([string]::IsNullOrWhiteSpace($refresh.accessToken)) {
        throw 'F1.5 no pudo renovar la sesión a través del proxy REST.'
    }

    $ticketParameters = @{
        Uri = 'http://localhost:4200/api/v1/operations/stream-ticket'
        Method = 'Post'
        Headers = @{ Authorization = ('Bearer ' + $refresh.accessToken); Origin = 'http://localhost:4200' }
        ContentType = 'application/json'
        Body = '{}'
    }
    $ticketResponse = Invoke-RestMethod @ticketParameters
    if ([string]::IsNullOrWhiteSpace($ticketResponse.ticket)) {
        throw 'F1.5 no recibió un ticket operativo.'
    }

    Receive-StreamReady -Ticket $ticketResponse.ticket
    Assert-ReplayedTicketRejected -Ticket $ticketResponse.ticket
    Write-Output 'F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0'
}
finally {
    Stop-VerificationRuntime
    & $nativeTestScript -Action Clean -ConfigPath $ConfigPath
}
