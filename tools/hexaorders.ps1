<#
.SYNOPSIS
    Comandos de HexaOrders para Windows (PowerShell 5.1+) y para GitHub Actions / Linux (pwsh 7+).

.DESCRIPTION
    Un único punto de entrada para arrancar, detener y verificar el proyecto. En Windows se
    puede ejecutar mediante hexaorders.cmd (raíz del repositorio), que funciona aunque la
    política de ejecución bloquee los scripts .ps1. El workflow de GitHub ejecuta este mismo
    script, así que lo que pasa en GitHub es lo que se ejecuta en tu equipo.

    Comandos de uso diario (proyecto Compose "hexaorders", carpeta del repositorio):
      start        Genera secretos si faltan, valida, construye y arranca los 5 servicios.
      status       Estado de los contenedores.
      logs         Últimas 100 líneas de log de cada servicio.
      stop         Detiene los servicios conservando los datos (-RemoveData los elimina).
      secrets      Solo genera secrets\db_password.txt y secrets\grafana_password.txt.
      k8s-secrets  Crea el Secret app-secrets de Kubernetes a partir de secrets\*.txt.

    Comandos de comprobación:
      check        Entregable generado al día + validación Compose/Kubernetes (Python).
      test         Tests unitarios locales: pytest (risk-engine) y Maven (order-api).
      verify       Prueba integral aislada, igual que GitHub: build con tests, promtool,
                   5 servicios sanos, smoke HTTP y HTTP 503 cuando cae risk-engine.
      diagnose     Estado y logs del entorno de verify (nunca falla).
      cleanup      Elimina contenedores y volúmenes del entorno de verify (nunca falla).

.EXAMPLE
    .\hexaorders.cmd start
.EXAMPLE
    .\hexaorders.cmd stop -RemoveData
.EXAMPLE
    pwsh ./tools/hexaorders.ps1 verify -WorkDir "$env:RUNNER_TEMP/hexaorders-delivery" -KeepRunning
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'start', 'status', 'logs', 'stop', 'secrets', 'k8s-secrets',
                 'check', 'test', 'verify', 'diagnose', 'cleanup')]
    [string]$Command = 'help',

    # Carpeta con docker-compose.yml y secrets\. Por defecto: la raíz del repositorio para los
    # comandos diarios, y una carpeta temporal aislada para verify/diagnose/cleanup.
    [string]$WorkDir,

    # stop: elimina también los volúmenes (pedidos, métricas y datos de Grafana).
    [switch]$RemoveData,

    # verify: no limpiar al terminar (GitHub ejecuta diagnose/cleanup en pasos propios).
    [switch]$KeepRunning,

    # Segundos máximos de espera a que los servicios estén sanos.
    [int]$WaitTimeout = 240
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path $PSScriptRoot -Parent
$OnWindows = ($PSVersionTable.PSEdition -eq 'Desktop') -or ((Test-Path variable:IsWindows) -and $IsWindows)
$InGitHub = [bool]$env:GITHUB_ACTIONS
# El entorno de verify usa su propio nombre de proyecto: sus volúmenes nunca son los tuyos.
$VerifyProject = 'hexaorders-verify'
$IsVerifyCommand = @('verify', 'diagnose', 'cleanup') -contains $Command

if (-not $WorkDir) {
    if ($IsVerifyCommand) { $WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) 'hexaorders-verify' }
    else { $WorkDir = $RepoRoot }
}
if (-not [System.IO.Path]::IsPathRooted($WorkDir)) { $WorkDir = Join-Path (Get-Location).Path $WorkDir }
$WorkDir = [System.IO.Path]::GetFullPath($WorkDir)
$ComposeFile = Join-Path $WorkDir 'docker-compose.yml'

# ---------------------------------------------------------------- utilidades

function Write-Step([string]$Message) {
    if ($InGitHub) { Write-Host "::endgroup::"; Write-Host "::group::$Message" }
    else { Write-Host ''; Write-Host "==> $Message" -ForegroundColor Cyan }
}

function Invoke-Native {
    # Ejecuta un programa externo y falla si su código de salida no es 0. Windows PowerShell
    # 5.1 no detiene el script por códigos de salida, así que se comprueba explícitamente.
    # La salida va siempre a la consola (Out-Host), nunca al valor de retorno de la función.
    param([Parameter(Mandatory)][string]$FilePath, [string[]]$Arguments = @(), [switch]$AllowFailure)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $FilePath @Arguments | Out-Host } finally { $ErrorActionPreference = $previous }
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        throw "Falló ($code): $FilePath $($Arguments -join ' ')"
    }
}

function Get-PythonCommand {
    # 1) "python" del PATH, salvo el alias de Microsoft Store (carpeta WindowsApps), que no
    #    es un Python real; 2) el lanzador "py -3" de Windows; 3) "python3" (Linux/macOS).
    $python = Get-Command python -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($python -and $python.Source -notmatch '[\\/]WindowsApps[\\/]') { return @($python.Source) }
    if ($OnWindows -and (Get-Command py -ErrorAction SilentlyContinue)) { return @('py', '-3') }
    if (Get-Command python3 -ErrorAction SilentlyContinue) { return @('python3') }
    throw 'No se encontró Python 3. Instálalo desde https://www.python.org/downloads/ (marca "Add python.exe to PATH").'
}

function Invoke-Python([string[]]$Arguments) {
    $python = @(Get-PythonCommand)
    $prefix = @()
    if ($python.Count -gt 1) { $prefix = $python[1..($python.Count - 1)] }
    [void](Invoke-Native $python[0] ($prefix + $Arguments))
}

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'No se encontró Docker. Instala Docker Desktop (https://docs.docker.com/desktop/) con contenedores Linux.'
    }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker info *> $null } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker no responde. Abre Docker Desktop, espera a que indique "Engine running" y usa contenedores Linux.'
    }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker compose version *> $null } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) { throw 'Falta Docker Compose v2 (docker compose). Actualiza Docker Desktop.' }
}

function Invoke-Compose {
    param([string[]]$Arguments, [switch]$AllowFailure)
    $base = @('compose', '-f', $ComposeFile)
    if ($IsVerifyCommand) { $base += @('-p', $VerifyProject) }
    return Invoke-Native 'docker' ($base + $Arguments) -AllowFailure:$AllowFailure
}

function Initialize-WorkDir([switch]$CopyFromRepo, [switch]$SecretsOnly) {
    # -SecretsOnly: solo genera secrets\ (la carpeta puede no tener todavía docker-compose.yml).
    New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
    if (-not $SecretsOnly) {
        $source = Join-Path $RepoRoot 'docker-compose.yml'
        $separators = [char[]]@([char]'\', [char]'/')
        $sameFolder = [string]::Equals($WorkDir.TrimEnd($separators), $RepoRoot.TrimEnd($separators), [StringComparison]::OrdinalIgnoreCase)
        if ($CopyFromRepo -and -not $sameFolder) { Copy-Item -LiteralPath $source -Destination $ComposeFile -Force }
        if (-not (Test-Path -LiteralPath $ComposeFile)) { throw "No existe $ComposeFile" }
    }
    & (Join-Path $PSScriptRoot 'init-env.ps1') -Root $WorkDir
}

function Get-GrafanaPassword {
    $path = Join-Path (Join-Path $WorkDir 'secrets') 'grafana_password.txt'
    return ([System.IO.File]::ReadAllText($path)).Trim()
}

function Get-HttpStatus([string]$Uri, [string]$Body) {
    # Funciona en Windows PowerShell 5.1 (WebException) y en PowerShell 7 (HttpResponseException).
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Post -ContentType 'application/json' -Body $Body -UseBasicParsing -TimeoutSec 30
        return [int]$response.StatusCode
    } catch {
        $exception = $_.Exception
        if ($exception.PSObject.Properties['Response'] -and $exception.Response) { return [int]$exception.Response.StatusCode }
        throw
    }
}

function Show-Urls {
    $grafanaSecret = Join-Path (Join-Path $WorkDir 'secrets') 'grafana_password.txt'
    Write-Host ''
    Write-Host 'HexaOrders está en marcha:' -ForegroundColor Green
    Write-Host '  Aplicación y API : http://127.0.0.1:8080'
    Write-Host '  Grafana          : http://127.0.0.1:3000/d/hexaorders  (usuario admin)'
    Write-Host "  Contraseña       : $grafanaSecret"
    Write-Host '  Prometheus       : http://127.0.0.1:9091/targets'
}

# ---------------------------------------------------------------- comandos

function Invoke-Help { Get-Help $PSCommandPath -Detailed }

function Invoke-Start {
    Assert-Docker
    Write-Step 'Secretos'
    Initialize-WorkDir
    Write-Step 'Validar docker-compose.yml'
    [void](Invoke-Compose @('config', '--quiet'))
    Write-Step 'Construir imágenes (incluye los tests de Java y Python)'
    [void](Invoke-Compose @('build', '--pull'))
    Write-Step 'Arrancar los cinco servicios y esperar a que estén sanos'
    [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout"))
    [void](Invoke-Compose @('ps'))
    Show-Urls
}

function Invoke-Status { Assert-Docker; [void](Invoke-Compose @('ps', '-a')) }

function Invoke-Logs { Assert-Docker; [void](Invoke-Compose @('logs', '--no-color', '--tail=100')) }

function Invoke-Stop {
    Assert-Docker
    if ($RemoveData) {
        Write-Host 'Deteniendo y ELIMINANDO volúmenes (pedidos, métricas y Grafana)...' -ForegroundColor Yellow
        [void](Invoke-Compose @('down', '-v', '--remove-orphans'))
    } else {
        [void](Invoke-Compose @('down'))
        Write-Host 'Servicios detenidos. Los datos se conservan; vuelve a arrancar con: .\hexaorders.cmd start'
    }
}

function Invoke-Secrets { Initialize-WorkDir -SecretsOnly }

function Invoke-K8sSecrets {
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) { throw 'No se encontró kubectl.' }
    Initialize-WorkDir -SecretsOnly
    $secrets = Join-Path $WorkDir 'secrets'
    [void](Invoke-Native 'kubectl' @('apply', '-f', (Join-Path (Join-Path $RepoRoot 'k8s') '00-namespace.yaml')))
    $yaml = & kubectl -n hexaorders create secret generic app-secrets `
        "--from-file=db_password=$(Join-Path $secrets 'db_password.txt')" `
        "--from-file=grafana_password=$(Join-Path $secrets 'grafana_password.txt')" `
        --dry-run=client -o yaml
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el Secret app-secrets.' }
    $yaml | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo aplicar el Secret app-secrets.' }
    Write-Host 'Secret hexaorders/app-secrets aplicado desde secrets\*.txt.'
}

function Invoke-Check {
    Write-Step 'Entregable generado al día (docker-compose.yml y ConfigMap de Kubernetes)'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_delivery.py'), '--check')
    Write-Step 'Validación del entregable Compose y de los 14 objetos Kubernetes'
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $python = @(Get-PythonCommand)
    $prefix = @(); if ($python.Count -gt 1) { $prefix = $python[1..($python.Count - 1)] }
    try { & $python[0] @($prefix + @('-c', 'import yaml, jsonschema')) *> $null } finally { $ErrorActionPreference = $previous }
    if ($LASTEXITCODE -ne 0) {
        throw "Faltan dependencias de validación. Ejecuta: $($python -join ' ') -m pip install -r requirements-tools.txt"
    }
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'validate_delivery.py'))
}

function Invoke-Test {
    Write-Step 'Tests Python (risk-engine)'
    Push-Location (Join-Path (Join-Path $RepoRoot 'services') 'risk-engine')
    try { Invoke-Python @('-m', 'pytest', '-q', '-p', 'no:cacheprovider') } finally { Pop-Location }
    Write-Step 'Tests Java (order-api)'
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw 'No se encontró Maven (mvn). Instala Java 21 y Maven, o usa "verify", que ejecuta los tests dentro de Docker.'
    }
    Push-Location (Join-Path (Join-Path $RepoRoot 'services') 'order-api')
    try { [void](Invoke-Native 'mvn' @('-B', '-q', 'test')) } finally { Pop-Location }
    Write-Host 'Tests unitarios correctos.' -ForegroundColor Green
}

function Invoke-Diagnose {
    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        Write-Host "Sin diagnósticos de contenedores: no existe $ComposeFile (falló un paso previo)."
        return
    }
    [void](Invoke-Compose @('ps', '-a') -AllowFailure)
    [void](Invoke-Compose @('logs', '--no-color', '--tail=100') -AllowFailure)
}

function Invoke-Cleanup {
    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        Write-Host 'Nada que limpiar: no se creó ningún recurso de Compose.'
        return
    }
    [void](Invoke-Compose @('down', '-v', '--remove-orphans') -AllowFailure)
}

function Invoke-Verify {
    Write-Step '[1/8] Entregable generado al día'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_delivery.py'), '--check')

    Assert-Docker
    if (-not $InGitHub) {
        # verify publica los mismos puertos que tu entorno diario: no pueden coexistir.
        $running = & docker ps -q --filter 'label=com.docker.compose.project=hexaorders'
        if ($running) { throw 'El entorno "hexaorders" está en marcha y usa los mismos puertos. Detenlo con: .\hexaorders.cmd stop' }
    }

    Write-Step "[2/8] Copiar el YAML entregable a una carpeta aislada ($WorkDir) y generar secretos"
    Initialize-WorkDir -CopyFromRepo

    $succeeded = $false
    try {
        Write-Step '[3/8] Validar y construir (los builds ejecutan los tests de Java y Python)'
        [void](Invoke-Compose @('config', '--quiet'))
        [void](Invoke-Compose @('build', '--pull'))

        Write-Step '[4/8] Validar configuración y reglas de Prometheus con promtool'
        [void](Invoke-Compose @('run', '--rm', '--no-deps', '--entrypoint', '/bin/promtool', 'prometheus',
                                'check', 'config', '/etc/prometheus/prometheus.yml'))

        Write-Step '[5/8] Arrancar los cinco servicios y esperar a que estén sanos'
        [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout"))

        Write-Step '[6/8] Smoke HTTP: pedidos, persistencia, validación, métricas y dashboard'
        $env:GRAFANA_PASSWORD = Get-GrafanaPassword
        Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'smoke.py'))

        Write-Step '[7/8] Si risk-engine cae, la API responde HTTP 503'
        [void](Invoke-Compose @('stop', 'risk-engine'))
        $code = Get-HttpStatus 'http://127.0.0.1:8080/api/orders' '{"customerId":"demo-001","amount":125.50}'
        if ($code -ne 503) { throw "Se esperaba HTTP 503 con risk-engine detenido y se obtuvo $code" }
        Write-Host 'HTTP 503 recibido correctamente.'

        Write-Step '[8/8] Recuperación: risk-engine vuelve y todo queda sano'
        [void](Invoke-Compose @('start', 'risk-engine'))
        [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '120'))
        $succeeded = $true
    } finally {
        if (-not $KeepRunning) {
            if (-not $succeeded) { Write-Step 'Diagnóstico del fallo'; Invoke-Diagnose }
            Write-Step 'Limpieza del entorno de verificación'
            Invoke-Cleanup
        }
    }
    if ($InGitHub) { Write-Host '::endgroup::' }
    Write-Host ''
    Write-Host 'VERIFICACIÓN COMPLETA: los cinco servicios funcionan de extremo a extremo.' -ForegroundColor Green
}

# ---------------------------------------------------------------- main

try {
    switch ($Command) {
        'help'        { Invoke-Help }
        'start'       { Invoke-Start }
        'status'      { Invoke-Status }
        'logs'        { Invoke-Logs }
        'stop'        { Invoke-Stop }
        'secrets'     { Invoke-Secrets }
        'k8s-secrets' { Invoke-K8sSecrets }
        'check'       { Invoke-Check; Write-Host 'Comprobaciones correctas.' -ForegroundColor Green }
        'test'        { Invoke-Test }
        'verify'      { Invoke-Verify }
        'diagnose'    { Invoke-Diagnose }
        'cleanup'     { Invoke-Cleanup }
    }
    if ($InGitHub -and $Command -ne 'verify') { Write-Host '::endgroup::' }
    exit 0
} catch {
    if ($InGitHub) { Write-Host '::endgroup::'; Write-Host "::error::$($_.Exception.Message)" }
    else { Write-Host ''; Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red }
    exit 1
}
