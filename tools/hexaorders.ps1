<#
.SYNOPSIS
    Comandos de HexaOrders para Windows (PowerShell 5.1+) y para GitHub Actions / Linux (pwsh 7+).

.DESCRIPTION
    Un único punto de entrada para arrancar, detener y verificar el proyecto. En Windows se
    puede ejecutar mediante hexaorders.cmd (raíz del repositorio), que funciona aunque la
    política de ejecución bloquee los scripts .ps1. El workflow de GitHub ejecuta este mismo
    script, así que lo que pasa en GitHub es lo que se ejecuta en tu equipo.

    Modos (v5, perfiles de Docker Compose; parámetro -Mode):
      full   (por defecto) 13 contenedores: los 5 originales, el gateway Nginx (frontend) y
             redis, rabbitmq, mailpit, loki, tempo, otel-collector y alloy. Límites: ~4,5 GB.
      basic  6 contenedores: db, risk-engine, order-api, prometheus, grafana y frontend. Límites: ~2,5 GB.

    Comandos de uso diario (proyecto Compose "hexaorders", carpeta del repositorio):
      start        Genera secretos si faltan, valida, construye y arranca los servicios del modo.
                   Se detiene sin tocar nada si hay datos de PostgreSQL pero falta su secreto.
      status       Estado de los contenedores.
      logs         Últimas 100 líneas de log de cada servicio.
      stop         Detiene los servicios conservando los datos (-RemoveData los elimina).
      secrets      Solo genera secrets\db_password.txt, grafana_password.txt, demo_password.txt y rabbitmq_password.txt.
      repair-db-password
                   Si secrets\db_password.txt ya no coincide con la contraseña con la que se creó el
                   volumen de PostgreSQL (order-api no arranca: "password authentication failed"),
                   cambia la contraseña de PostgreSQL al valor del archivo. Pide confirmación y
                   NO borra datos.
      k8s-secrets  Crea el Secret app-secrets de Kubernetes a partir de secrets\*.txt.

    Comandos de comprobación:
      check        Entregable generado al día + validación Compose/Kubernetes, observabilidad
                   conservada y sitio multipágina coherente (Python, sin Docker).
      test         Tests unitarios locales: pytest (risk-engine) y Maven (order-api).
      smoke        Pruebas HTTP contra TU entorno en marcha: smoke, plataforma y (modo full)
                   integraciones Redis, RabbitMQ, Mailpit, Loki, Tempo y OpenTelemetry.
      verify       Prueba integral aislada, igual que GitHub: build con tests, validación de
                   configuración, servicios sanos, smoke, plataforma, integraciones, degradación
                   sin Redis/RabbitMQ, persistencia tras reiniciar y HTTP 503 sin risk-engine.
      diagnose     Estado y logs del entorno de verify (nunca falla).
      cleanup      Elimina contenedores y volúmenes del entorno de verify (nunca falla).

.EXAMPLE
    .\hexaorders.cmd start
.EXAMPLE
    .\hexaorders.cmd start -Mode basic
.EXAMPLE
    .\hexaorders.cmd stop -RemoveData
.EXAMPLE
    pwsh ./tools/hexaorders.ps1 verify -WorkDir "$env:RUNNER_TEMP/hexaorders-delivery" -KeepRunning
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'start', 'status', 'logs', 'stop', 'secrets', 'k8s-secrets', 'repair-db-password',
                 'check', 'test', 'smoke', 'verify', 'diagnose', 'cleanup')]
    [string]$Command = 'help',

    # v5: full = 13 contenedores (perfil "full" de Compose); basic = 6 contenedores esenciales.
    [ValidateSet('full', 'basic')]
    [string]$Mode = 'full',

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
# Perfiles de Compose: start/verify usan el modo pedido; status, logs, stop, diagnose y cleanup
# incluyen siempre el perfil "full" para ver o detener TODOS los contenedores del proyecto.
$UsesMode = @('start', 'verify') -contains $Command
if ($UsesMode -and $Mode -eq 'basic') { $env:COMPOSE_PROFILES = '' } else { $env:COMPOSE_PROFILES = 'full' }
$FullMode = $env:COMPOSE_PROFILES -eq 'full'

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
    Write-Host '  Sitio web        : http://127.0.0.1:8080  (catálogo, portales por rol, monitoreo)'
    Write-Host '  Laboratorio      : http://127.0.0.1:8080/laboratorio.html'
    Write-Host "  Cuentas demo     : admin, operador, logistica, observador, cliente · contraseña en $(Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt')"
    Write-Host '  Grafana          : http://127.0.0.1:3000/d/hexaorders  (usuario admin)'
    Write-Host "  Contraseña       : $grafanaSecret"
    Write-Host '  Prometheus       : http://127.0.0.1:9091/targets'
    if ($FullMode) {
        $rabbitSecret = Join-Path (Join-Path $WorkDir 'secrets') 'rabbitmq_password.txt'
        Write-Host '  Mailpit (correos): http://127.0.0.1:8025'
        Write-Host "  RabbitMQ         : http://127.0.0.1:15672  (usuario hexaorders, contraseña en $rabbitSecret)"
        Write-Host '  Logs y trazas    : Grafana -> Explore -> Loki / Tempo · dashboard /d/hexaorders-integrations'
    } else {
        Write-Host '  Modo básico: Redis, RabbitMQ, Mailpit, Loki, Tempo y OpenTelemetry no están en marcha.'
    }
}

# ---------------------------------------------------------------- contraseña de PostgreSQL

# PostgreSQL guarda la contraseña al crear su volumen. Si después secrets\db_password.txt cambia
# (otra carpeta, secretos regenerados), order-api muere al arrancar con "password authentication
# failed" y Compose solo dice "dependency order-api failed to start". Estas funciones lo detectan
# antes de arrancar y permiten repararlo sin borrar datos. La contraseña viaja por stdin, nunca en
# la línea de comandos; tr elimina el CR que añade Windows PowerShell al canalizar texto.
$DbCheckScript = 'tr -d \\r | { IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -X -q -t -A -o /dev/null -h 127.0.0.1 -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1; }'
# Dentro del contenedor el socket local usa "trust" (imagen oficial), así que se puede corregir la contraseña.
$DbRepairScript = 'tr -d \\r | { IFS= read -r p; export p; exec psql -X -q -U $POSTGRES_USER -d $POSTGRES_DB -v ON_ERROR_STOP=1; }'
if ($IsVerifyCommand) { $DbVolume = "$($VerifyProject)_postgres_data" } else { $DbVolume = 'hexaorders_postgres_data' }

function Get-DbSecretValue {
    $path = Join-Path (Join-Path $WorkDir 'secrets') 'db_password.txt'
    return ([System.IO.File]::ReadAllText($path)).TrimEnd([char[]]@([char]13, [char]10))
}

function Invoke-ComposeWithInput([string]$InputText, [string[]]$Arguments) {
    # Como Invoke-Compose, pero envía $InputText por stdin y devuelve el código de salida.
    $base = @('compose', '-f', $ComposeFile)
    if ($IsVerifyCommand) { $base += @('-p', $VerifyProject) }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    try { $InputText | & docker @($base + $Arguments) | Out-Host } finally { $ErrorActionPreference = $previous }
    return $LASTEXITCODE
}

function Test-DbVolumeExists {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker volume inspect $DbVolume *> $null } finally { $ErrorActionPreference = $previous }
    return ($LASTEXITCODE -eq 0)
}

function Test-DatabasePassword {
    Write-Step 'Comprobar que secrets\db_password.txt coincide con la contraseña guardada en el volumen de PostgreSQL'
    [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout", 'db'))
    $code = Invoke-ComposeWithInput ((Get-DbSecretValue) + "`nSELECT 1;`n") @('exec', '-T', 'db', 'sh', '-c', $DbCheckScript)
    return ($code -eq 0)
}

function Set-DatabasePassword([string]$Value) {
    # Cambia la contraseña del usuario de PostgreSQL (POSTGRES_USER) sin tocar ningún dato.
    $sql = $Value + "`n\getenv pw p`n\getenv u POSTGRES_USER`nALTER ROLE :""u"" PASSWORD :'pw';`n"
    $code = Invoke-ComposeWithInput $sql @('exec', '-T', 'db', 'sh', '-c', $DbRepairScript)
    if ($code -ne 0) { throw "No se pudo cambiar la contraseña de PostgreSQL (código $code)." }
}

function Get-DbPasswordHelp {
    $secret = Join-Path (Join-Path $WorkDir 'secrets') 'db_password.txt'
    return ("$secret no coincide con la contraseña con la que se creó el volumen $DbVolume (de una instalación anterior). " +
            'Por eso order-api no puede conectarse a PostgreSQL. Tus pedidos siguen intactos. Soluciones: ' +
            '(1) copia el db_password.txt original (por ejemplo, de la carpeta secrets de la versión anterior) y vuelve a ejecutar start; ' +
            '(2) o ejecuta .\hexaorders.cmd repair-db-password, que cambia la contraseña de PostgreSQL al valor actual del archivo sin borrar datos. ' +
            'No uses docker compose down -v: borraría los datos.')
}

function Show-StartFailure {
    Write-Host ''
    Write-Host 'Algún servicio no llegó a estar sano. Estado de los contenedores:' -ForegroundColor Yellow
    [void](Invoke-Compose @('ps', '-a') -AllowFailure)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $logs = (& docker compose -f $ComposeFile logs --no-color --tail=60 order-api 2>&1 | Out-String) } finally { $ErrorActionPreference = $previous }
    Write-Host 'Últimas líneas de order-api:' -ForegroundColor Yellow
    Write-Host $logs
    if ($logs -match 'password authentication failed') {
        Write-Host (Get-DbPasswordHelp) -ForegroundColor Yellow
    } else {
        Write-Host 'Para ver otro servicio: docker compose logs --no-color --tail=80 NOMBRE' -ForegroundColor Yellow
    }
}

function Invoke-RepairDbPassword {
    Assert-Docker
    Initialize-WorkDir
    if (-not (Test-DbVolumeExists)) {
        Write-Host "No existe el volumen ${DbVolume}: PostgreSQL se creará con secrets\db_password.txt. No hay nada que reparar." -ForegroundColor Green
        return
    }
    if (Test-DatabasePassword) {
        Write-Host 'La contraseña ya coincide con la del volumen. No hay nada que reparar.' -ForegroundColor Green
        return
    }
    Write-Host ''
    Write-Host 'La contraseña de PostgreSQL no coincide con secrets\db_password.txt.' -ForegroundColor Yellow
    Write-Host 'Si conservas el archivo original, es preferible copiarlo y no continuar.'
    Write-Host 'Si continúas, la contraseña del usuario de PostgreSQL pasará a ser la del archivo actual.'
    Write-Host 'Los pedidos, usuarios y demás datos NO se borran.'
    $answer = Read-Host 'Escribe SI para continuar'
    if ($answer -cne 'SI') { Write-Host 'Cancelado: no se cambió nada.'; return }
    Set-DatabasePassword (Get-DbSecretValue)
    if (-not (Test-DatabasePassword)) { throw 'La contraseña se cambió, pero la comprobación sigue fallando.' }
    Write-Host 'Contraseña de PostgreSQL reparada sin perder datos. Ahora ejecuta: .\hexaorders.cmd start' -ForegroundColor Green
}

# ---------------------------------------------------------------- comandos

function Invoke-Help { Get-Help $PSCommandPath -Detailed }

function Assert-DatabaseSecretMatchesVolume {
    # PostgreSQL, Grafana y RabbitMQ guardan su contraseña al inicializar su volumen. Si el volumen
    # existe y falta el archivo de secreto, generar uno nuevo dejaría el servicio sin acceso a sus
    # datos. En ese caso se detiene con instrucciones: nunca se borran volúmenes ni se regeneran secretos.
    if ($IsVerifyCommand) { return }
    $pairs = @(
        @{ Secret = 'db_password.txt'; Volume = 'hexaorders_postgres_data'; Variable = 'DB_PASSWORD'; Data = 'los pedidos' },
        @{ Secret = 'grafana_password.txt'; Volume = 'hexaorders_grafana_data'; Variable = 'GRAFANA_PASSWORD'; Data = 'el usuario admin de Grafana' },
        @{ Secret = 'rabbitmq_password.txt'; Volume = 'hexaorders_rabbitmq_data'; Variable = 'RABBITMQ_PASSWORD'; Data = 'los mensajes de RabbitMQ' }
    )
    foreach ($pair in $pairs) {
        $secret = Join-Path (Join-Path $WorkDir 'secrets') $pair.Secret
        if (Test-Path -LiteralPath $secret -PathType Leaf) { continue }
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { & docker volume inspect $pair.Volume *> $null } finally { $ErrorActionPreference = $previous }
        if ($LASTEXITCODE -eq 0) {
            throw ("Existe el volumen $($pair.Volume) pero falta $secret. " +
                   "Restaura ese archivo con la contraseña original (copia de seguridad o variable $($pair.Variable)) para conservar $($pair.Data). " +
                   'Solo si quieres empezar de cero y PERDER los datos: .\hexaorders.cmd stop -RemoveData')
        }
    }
}

function Invoke-IntegrationSmoke([string[]]$Arguments) {
    $env:SECRETS_DIR = Join-Path $WorkDir 'secrets'
    $env:PROMETHEUS_URL = 'http://127.0.0.1:9091'
    Invoke-Python (@((Join-Path (Join-Path $RepoRoot 'tools') 'integration_smoke.py')) + $Arguments)
}

function Invoke-Start {
    Assert-Docker
    Write-Step 'Secretos'
    Assert-DatabaseSecretMatchesVolume
    Initialize-WorkDir
    Write-Step 'Validar docker-compose.yml'
    [void](Invoke-Compose @('config', '--quiet'))
    Write-Step 'Construir imágenes (incluye los tests de Java y Python)'
    [void](Invoke-Compose @('build', '--pull'))
    if ((Test-DbVolumeExists) -and -not (Test-DatabasePassword)) { throw (Get-DbPasswordHelp) }
    Write-Step "Arrancar los servicios del modo $Mode y esperar a que estén sanos"
    try { [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout")) }
    catch { Show-StartFailure; throw }
    [void](Invoke-Compose @('ps'))
    Show-Urls
}

function Invoke-Smoke {
    Assert-Docker
    $env:GRAFANA_PASSWORD = Get-GrafanaPassword
    $env:PROMETHEUS_URL = 'http://127.0.0.1:9091'
    Write-Step 'Smoke HTTP (pedidos, persistencia, métricas y dashboards)'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'smoke.py'))
    Write-Step 'Plataforma (portal, roles, ciclo de vida, logística)'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'platform_smoke.py'),
                    '--demo-password-file', (Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt'))
    Write-Step 'Tienda (catálogo, filtros, promociones, carrito, checkout y administración comercial)'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'store_smoke.py'),
                    '--demo-password-file', (Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt'))
    $running = & docker ps -q --filter 'label=com.docker.compose.project=hexaorders' --filter 'label=com.docker.compose.service=rabbitmq'
    if ($running) {
        Write-Step 'Integraciones del modo full (Redis, RabbitMQ, Mailpit, Loki, Tempo, OpenTelemetry)'
        Invoke-IntegrationSmoke @('check')
    } else {
        Write-Host 'Modo básico: se omiten las pruebas de integración del perfil full.'
    }
    Write-Host 'Pruebas HTTP correctas.' -ForegroundColor Green
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
        "--from-file=demo_password=$(Join-Path $secrets 'demo_password.txt')" `
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
    Write-Step 'Preservación de la observabilidad (dashboards, paneles, alertas, jobs y métricas previas)'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'check_observability.py'))
    Write-Step 'Sitio multipágina: páginas HTML al día, rutas, enlaces, CSP y reglas de seguridad por rol'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_pages.py'), '--check')
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'check_frontend.py'))
    Write-Step 'Catálogo tecnológico: la migración V7 coincide con tools/catalog_v6_data.py'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_catalog.py'), '--check')
    Write-Step 'Marca POLYMER: los SVG del logotipo coinciden con tools/generate_brand.py'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_brand.py'), '--check')
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
    Write-Step '[1/10] Entregable generado al día'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'generate_delivery.py'), '--check')

    Assert-Docker
    if (-not $InGitHub) {
        # verify publica los mismos puertos que tu entorno diario: no pueden coexistir.
        $running = & docker ps -q --filter 'label=com.docker.compose.project=hexaorders'
        if ($running) { throw 'El entorno "hexaorders" está en marcha y usa los mismos puertos. Detenlo con: .\hexaorders.cmd stop' }
    }

    Write-Step "[2/10] Copiar el YAML entregable a una carpeta aislada ($WorkDir) y generar secretos"
    Initialize-WorkDir -CopyFromRepo

    $succeeded = $false
    $state = Join-Path $WorkDir 'integration-state.json'
    try {
        Write-Step "[3/10] Validar y construir en modo $Mode (los builds ejecutan los tests de Java y Python)"
        [void](Invoke-Compose @('config', '--quiet'))
        [void](Invoke-Compose @('build', '--pull'))

        Write-Step '[4/10] Validar configuraciones: Prometheus (promtool) y OpenTelemetry Collector'
        [void](Invoke-Compose @('run', '--rm', '--no-deps', '--entrypoint', '/bin/promtool', 'prometheus',
                                'check', 'config', '/etc/prometheus/prometheus.yml'))
        if ($FullMode) {
            [void](Invoke-Compose @('run', '--rm', '--no-deps', '--entrypoint', '/otelcol-contrib', 'otel-collector',
                                    'validate', '--config=/etc/otelcol-contrib/config.yaml'))
        }

        Write-Step "[5/10] Arrancar los servicios del modo $Mode y esperar a que estén sanos"
        [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout"))

        Write-Step '[6/10] Smoke HTTP a través del gateway: pedidos, persistencia, validación, métricas y dashboards'
        $env:GRAFANA_PASSWORD = Get-GrafanaPassword
        Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'smoke.py'))
        Write-Step '[6b/10] Plataforma: portal, roles, ciclo de vida, logística, auditoría y métricas nuevas'
        $env:PROMETHEUS_URL = 'http://127.0.0.1:9091'
        Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'platform_smoke.py'),
                        '--demo-password-file', (Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt'))
        Write-Step '[6c/10] Tienda: 300+ productos, filtros, promociones, carrito guardado, checkout y administración comercial'
        Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'store_smoke.py'),
                        '--demo-password-file', (Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt'))

        if ($FullMode) {
            Write-Step '[7/10] Integraciones reales: Redis, RabbitMQ, Mailpit, Tempo, OpenTelemetry, Loki y Grafana'
            Invoke-IntegrationSmoke @('check')
            Write-Step '[7b/10] Degradación: sin Redis ni RabbitMQ el catálogo y los pedidos siguen funcionando'
            [void](Invoke-Compose @('stop', 'redis', 'rabbitmq'))
            Invoke-IntegrationSmoke @('degraded', '--state', $state)
            [void](Invoke-Compose @('start', 'redis', 'rabbitmq'))
            [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '180'))
            Invoke-IntegrationSmoke @('recovered', '--state', $state)
        } else {
            Write-Step '[7/10] Modo básico: se omiten las integraciones del perfil full'
        }

        Write-Step '[8/10] Persistencia: reiniciar PostgreSQL y order-api conserva los pedidos'
        Invoke-IntegrationSmoke @('persist-write', '--state', $state)
        [void](Invoke-Compose @('restart', 'db', 'order-api'))
        [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '180'))
        Invoke-IntegrationSmoke @('persist-read', '--state', $state)
        Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'store_smoke.py'), '--quick')

        Write-Step '[8b/10] Contraseña de PostgreSQL distinta del secreto: se detecta y repair-db-password la corrige sin perder datos'
        if (-not (Test-DatabasePassword)) { throw 'La contraseña de PostgreSQL debería coincidir con secrets\db_password.txt' }
        Set-DatabasePassword ('distinta-' + [guid]::NewGuid().ToString('N'))
        if (Test-DatabasePassword) { throw 'No se detectó una contraseña de PostgreSQL distinta del secreto' }
        Set-DatabasePassword (Get-DbSecretValue)
        if (-not (Test-DatabasePassword)) { throw 'La reparación no restauró el acceso a PostgreSQL' }
        [void](Invoke-Compose @('restart', 'order-api'))
        [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '180'))
        Invoke-IntegrationSmoke @('persist-read', '--state', $state)
        Write-Host 'OK: contraseña distinta detectada y reparada; los pedidos siguen en PostgreSQL.' -ForegroundColor Green

        Write-Step '[9/10] Si risk-engine cae, la API responde HTTP 503 (también a través del gateway)'
        [void](Invoke-Compose @('stop', 'risk-engine'))
        $code = Get-HttpStatus 'http://127.0.0.1:8080/api/orders' '{"customerId":"demo-001","amount":125.50}'
        if ($code -ne 503) { throw "Se esperaba HTTP 503 con risk-engine detenido y se obtuvo $code" }
        Write-Host 'HTTP 503 recibido correctamente.'

        Write-Step '[10/10] Recuperación: risk-engine vuelve y todo queda sano'
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
    Write-Host "VERIFICACIÓN COMPLETA (modo $Mode): todos los servicios funcionan de extremo a extremo." -ForegroundColor Green
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
        'repair-db-password' { Invoke-RepairDbPassword }
        'k8s-secrets' { Invoke-K8sSecrets }
        'check'       { Invoke-Check; Write-Host 'Comprobaciones correctas.' -ForegroundColor Green }
        'test'        { Invoke-Test }
        'smoke'       { Invoke-Smoke }
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
