<#
.SYNOPSIS
    Comandos de POLYMER para Windows (PowerShell 5.1+) y para GitHub Actions / Linux (pwsh 7+).

.DESCRIPTION
    Un único punto de entrada para arrancar, detener y verificar el proyecto. En Windows se
    puede ejecutar mediante polymer.cmd (raíz del repositorio), que funciona aunque la
    política de ejecución bloquee los scripts .ps1. El workflow de GitHub ejecuta este mismo
    script, así que lo que pasa en GitHub es lo que se ejecuta en tu equipo.

    Modos (v5, perfiles de Docker Compose; parámetro -Mode):
      full   (por defecto) 13 contenedores: los 5 originales, el gateway Nginx (frontend) y
             redis, rabbitmq, mailpit, loki, tempo, otel-collector y alloy. Límites: ~4,5 GB.
      basic  6 contenedores: db, risk-engine, order-api, prometheus, grafana y frontend. Límites: ~2,5 GB.

    Comandos de uso diario (proyecto Compose "polymer", carpeta del repositorio):
      start        Arranca POLYMER (por defecto los 13 contenedores). Si encuentra datos de una
                   instalación anterior (volúmenes hexaorders_*) y faltan sus contraseñas, busca la
                   carpeta secrets\ anterior junto al proyecto y pide confirmación para copiarla;
                   después copia y verifica los volúmenes a polymer_* (los originales se conservan)
                   y genera contraseñas solo para lo que sea nuevo. Valida, construye y arranca.
                   Se detiene sin tocar nada si hay datos pero no encuentra su contraseña.
      import-secrets
                   Solo copia las contraseñas que falten desde la instalación anterior
                   (-SecretsFrom "C:\ruta\HexaOrders-v6" o búsqueda automática). Nunca sobrescribe.
      migrate      Solo copia y verifica los volúmenes hexaorders_* → polymer_* (lo hace start).
                   Idempotente; los volúmenes originales nunca se borran.
      status       Estado de los contenedores.
      health       Salud real: estado y healthcheck de cada contenedor y respuesta HTTP de la tienda,
                   la API, Grafana, Prometheus, Mailpit y RabbitMQ. Falla si algo no está sano.
      rebuild      Reconstruye imágenes (con sus tests) y recrea solo esos servicios, sin tocar datos:
                   .\polymer.cmd rebuild -Services risk-engine   (sin -Services: todos).
      restart      Reinicia servicios conservando los datos: .\polymer.cmd restart -Services order-api
      demo         Escenarios de la exposición contra tu entorno en marcha (docs/final/GUIA_EXPOSICION_PROFESOR.md):
                   .\polymer.cmd demo -Scenario preparar|arquitectura|redis|redis-caida|rabbitmq|rabbitmq-caida|
                   postgres|riesgo|riesgo-caida|adaptador|observabilidad|todo   [-NoPause]
      demo-on      Modo demostración (solo configuración de order-api): TTL corto de la caché
                   (-DemoTtl 20) y, con -FixedRisk, el adaptador de riesgo fijo. demo-off lo desactiva.
      logs         Últimas 100 líneas de log de cada servicio.
      stop         Detiene los servicios conservando los datos (-RemoveData los elimina).
      secrets      Solo crea en secrets\ las contraseñas que falten (aleatorias). Nunca sobrescribe.
                   Con -Memorable (instalación NUEVA): elige por servicio una frase memorable generada,
                   escribirla tú (oculta, dos veces) o aleatoria. Distinta para cada servicio.
      change-password -Service db|grafana|rabbitmq
                   Cambia una contraseña existente: pide confirmación, guarda la anterior en
                   secrets\backup\, la cambia en el servicio, actualiza el archivo y comprueba que
                   todo sigue funcionando (si falla, restaura la anterior). Nunca la muestra.
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
    .\polymer.cmd start
.EXAMPLE
    .\polymer.cmd import-secrets -SecretsFrom "$env:USERPROFILE\Downloads\HexaOrders-v6"
.EXAMPLE
    .\polymer.cmd start -Mode basic
.EXAMPLE
    .\polymer.cmd stop -RemoveData
.EXAMPLE
    pwsh ./tools/polymer.ps1 verify -WorkDir "$env:RUNNER_TEMP/polymer-delivery" -KeepRunning
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'start', 'status', 'logs', 'stop', 'secrets', 'k8s-secrets', 'repair-db-password',
                 'import-secrets', 'migrate', 'change-password', 'check', 'test', 'smoke', 'verify', 'diagnose', 'cleanup',
                 'health', 'rebuild', 'restart', 'demo', 'demo-on', 'demo-off')]
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
    [int]$WaitTimeout = 240,

    # import-secrets / start: carpeta secrets\ (o la carpeta del proyecto anterior que la contiene)
    # de la que copiar las contraseñas originales. Sin este parámetro se buscan junto al proyecto.
    [string]$SecretsFrom,

    # Responde «sí» a las confirmaciones (uso no interactivo, por ejemplo el CI).
    [switch]$AssumeYes,

    # secrets: en una instalación NUEVA, permite elegir frases memorables (generadas o escritas por ti)
    # en lugar de cadenas aleatorias. Nunca se aplica a servicios que ya tienen datos.
    [switch]$Memorable,

    # change-password: servicio cuya contraseña cambiar (db, grafana, rabbitmq o demo).
    [ValidateSet('db', 'grafana', 'rabbitmq', 'demo')]
    [string]$Service,

    # change-password: archivo con la nueva contraseña (automatización). Sin él se pide de forma oculta.
    [string]$NewPasswordFile,

    # rebuild / restart: servicios de Compose (por ejemplo -Services risk-engine o -Services order-api,frontend).
    [string[]]$Services,

    # demo: escenario de la exposición (preparar, arquitectura, redis, redis-caida, rabbitmq, rabbitmq-caida,
    # postgres, riesgo, riesgo-caida, adaptador, observabilidad, todo). Ver tools/demo/polymer-demo.ps1.
    [string]$Scenario = 'preparar',

    # demo: sin pausas entre pasos.
    [switch]$NoPause,

    # demo-on: TTL corto (segundos) de la caché del catálogo y, con -FixedRisk, adaptador de riesgo fijo.
    [int]$DemoTtl = 20,
    [switch]$FixedRisk,
    [int]$FixedScore = 10
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path $PSScriptRoot -Parent
$OnWindows = ($PSVersionTable.PSEdition -eq 'Desktop') -or ((Test-Path variable:IsWindows) -and $IsWindows)
$InGitHub = [bool]$env:GITHUB_ACTIONS
# El entorno de verify usa su propio nombre de proyecto: sus volúmenes nunca son los tuyos.
$VerifyProject = 'polymer-verify'
$IsVerifyCommand = @('verify', 'diagnose', 'cleanup') -contains $Command

if (-not $WorkDir) {
    if ($IsVerifyCommand) { $WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) 'polymer-verify' }
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
    Write-Host 'POLYMER está en marcha:' -ForegroundColor Green
    Write-Host '  Sitio web        : http://127.0.0.1:8080  (catálogo, portales por rol, monitoreo)'
    Write-Host '  Laboratorio      : http://127.0.0.1:8080/laboratorio.html'
    Write-Host "  Cuentas demo     : admin, operador, logistica, observador, cliente · contraseña en $(Join-Path (Join-Path $WorkDir 'secrets') 'demo_password.txt')"
    Write-Host '  Grafana          : http://127.0.0.1:3000/d/polymer  (usuario admin)'
    Write-Host "  Contraseña       : $grafanaSecret"
    Write-Host '  Prometheus       : http://127.0.0.1:9091/targets'
    if ($FullMode) {
        $rabbitSecret = Join-Path (Join-Path $WorkDir 'secrets') 'rabbitmq_password.txt'
        Write-Host '  Mailpit (correos): http://127.0.0.1:8025'
        Write-Host "  RabbitMQ         : http://127.0.0.1:15672  (usuario polymer, contraseña en $rabbitSecret)"
        Write-Host '  Logs y trazas    : Grafana -> Explore -> Loki / Tempo · dashboard /d/polymer-integrations'
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
if ($IsVerifyCommand) { $DbVolume = "$($VerifyProject)_postgres_data" } else { $DbVolume = 'polymer_postgres_data' }

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

function Test-DatabasePassword([string]$Value) {
    Write-Step 'Comprobar que secrets\db_password.txt coincide con la contraseña guardada en el volumen de PostgreSQL'
    [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout", 'db'))
    if (-not $Value) { $Value = Get-DbSecretValue }
    $code = Invoke-ComposeWithInput ($Value + "`nSELECT 1;`n") @('exec', '-T', 'db', 'sh', '-c', $DbCheckScript)
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
            '(2) o ejecuta .\polymer.cmd repair-db-password, que cambia la contraseña de PostgreSQL al valor actual del archivo sin borrar datos. ' +
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
    Write-Host 'Contraseña de PostgreSQL reparada sin perder datos. Ahora ejecuta: .\polymer.cmd start' -ForegroundColor Green
}


# ---------------------------------------------------------------- migración desde HexaOrders

# Hasta la v6 el proyecto de Compose se llamaba «hexaorders» y sus volúmenes, hexaorders_*. POLYMER
# usa el proyecto «polymer» (volúmenes polymer_*). Docker no permite renombrar un volumen, y usar el
# nombre nuevo sin más crearía volúmenes VACÍOS. Por eso, la primera vez se COPIA cada volumen
# antiguo al nuevo, se verifica la copia archivo por archivo (SHA-256) y los volúmenes antiguos se
# conservan intactos como copia de seguridad. Nada de esto borra datos. Detalle: docs/MIGRACION_POLYMER.md.
$LegacyProject = 'hexa' + 'orders'
$VolumeKeys = @('postgres_data', 'prometheus_data', 'grafana_data', 'redis_data', 'rabbitmq_data', 'loki_data', 'tempo_data', 'alloy_data')
$SecretVolumes = @(
    @{ Secret = 'db_password.txt'; Key = 'postgres_data'; Data = 'los pedidos, usuarios y productos' },
    @{ Secret = 'grafana_password.txt'; Key = 'grafana_data'; Data = 'el usuario admin de Grafana' },
    @{ Secret = 'rabbitmq_password.txt'; Key = 'rabbitmq_data'; Data = 'los mensajes de RabbitMQ' }
)
$SecretFiles = @('db_password.txt', 'grafana_password.txt', 'demo_password.txt', 'rabbitmq_password.txt')
# Imagen ya usada por el servicio db (incluye cp, find, sha256sum y cmp).
$CopyImage = 'postgres:17.11-bookworm'

function Test-VolumeExists([string]$Name) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker volume inspect $Name *> $null } finally { $ErrorActionPreference = $previous }
    return ($LASTEXITCODE -eq 0)
}

function Confirm-Action([string]$Question) {
    if ($AssumeYes) { Write-Host "$Question SI (-AssumeYes)"; return $true }
    $answer = Read-Host "$Question Escribe SI para continuar"
    return ($answer -ceq 'SI')
}

function Get-MissingSecretFiles {
    $dir = Join-Path $WorkDir 'secrets'
    return @($SecretFiles | Where-Object { -not (Test-Path -LiteralPath (Join-Path $dir $_) -PathType Leaf) })
}

function Get-SecretsCandidates {
    # Carpetas secrets\ con db_password.txt: la indicada con -SecretsFrom o, si no, las que estén junto
    # a este proyecto (carpetas hermanas y sus subcarpetas, p. ej. Descargas\HexaOrders-v6\HexaOrders-v6).
    $own = [System.IO.Path]::GetFullPath((Join-Path $WorkDir 'secrets')).TrimEnd('\', '/')
    if ($SecretsFrom) {
        $root = [System.IO.Path]::GetFullPath($SecretsFrom)
        if (Test-Path -LiteralPath (Join-Path $root 'db_password.txt') -PathType Leaf) { return @($root) }
        if (Test-Path -LiteralPath (Join-Path (Join-Path $root 'secrets') 'db_password.txt') -PathType Leaf) { return @((Join-Path $root 'secrets')) }
        throw "No se encontró db_password.txt en '$SecretsFrom' ni en '$SecretsFrom\secrets'."
    }
    $parent = Split-Path $WorkDir -Parent
    if (-not $parent) { return @() }
    $found = Get-ChildItem -LiteralPath $parent -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        Get-ChildItem -LiteralPath $_.FullName -Filter 'db_password.txt' -Recurse -Depth 3 -File -ErrorAction SilentlyContinue
    } | Where-Object { $_.Directory.Name -eq 'secrets' } | ForEach-Object { $_.Directory.FullName.TrimEnd('\', '/') }
    return @($found | Where-Object { $_ -ne $own } | Sort-Object -Unique)
}

function Import-LegacySecrets([switch]$Explicit) {
    # Copia SOLO los archivos que faltan; nunca sobrescribe ni muestra contraseñas.
    $missing = Get-MissingSecretFiles
    if (-not $missing.Count) {
        if ($Explicit) { Write-Host 'secrets\ ya contiene todas las contraseñas: no se copia nada.' -ForegroundColor Green }
        return
    }
    $needed = $Explicit -or [bool](@($VolumeKeys | Where-Object { (Test-VolumeExists "polymer_$_") -or (Test-VolumeExists "$($LegacyProject)_$_") }).Count)
    if (-not $needed) { return }  # instalación nueva: init-env.ps1 generará contraseñas aleatorias
    $candidates = @(Get-SecretsCandidates)
    if (-not $candidates.Count) { return }  # Assert-DatabaseSecretMatchesVolume explicará qué hacer
    Write-Step 'Recuperar las contraseñas originales de tu instalación anterior'
    Write-Host 'Hay datos de una instalación anterior, pero en esta carpeta faltan sus contraseñas:' -ForegroundColor Yellow
    Write-Host ("  " + ($missing -join ', '))
    $index = 0
    foreach ($candidate in $candidates) {
        $index++
        $stamp = (Get-Item -LiteralPath (Join-Path $candidate 'db_password.txt')).LastWriteTime.ToString('yyyy-MM-dd HH:mm')
        Write-Host "  [$index] $candidate  (db_password.txt del $stamp)"
    }
    $choice = $candidates[0]
    if ($candidates.Count -gt 1) {
        if ($AssumeYes) { throw 'Hay varias carpetas secrets\ candidatas: indica cuál con -SecretsFrom.' }
        $number = Read-Host 'Número de la carpeta de la que copiar (Enter para cancelar)'
        if (-not ($number -match '^\d+$') -or [int]$number -lt 1 -or [int]$number -gt $candidates.Count) { throw 'Cancelado: no se copió nada.' }
        $choice = $candidates[[int]$number - 1]
    }
    if (-not (Confirm-Action "Se copiarán los archivos que faltan desde '$choice' (no se muestra su contenido).")) {
        throw 'Cancelado: no se copió nada.'
    }
    $target = Join-Path $WorkDir 'secrets'
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($file in $missing) {
        $source = Join-Path $choice $file
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $target $file)
            Write-Host "  copiado $file"
        }
    }
}

function Stop-LegacyStack {
    # Detiene (sin borrar) los contenedores del proyecto anterior: comparten puertos con POLYMER y
    # sus volúmenes deben estar quietos para copiarlos. Se pueden volver a arrancar desde la v6.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $running = @(& docker ps -q --filter "label=com.docker.compose.project=$LegacyProject") } finally { $ErrorActionPreference = $previous }
    if ($running.Count) {
        Write-Step "Detener (sin borrar) los $($running.Count) contenedores del proyecto anterior «$LegacyProject»"
        Invoke-Native 'docker' (@('stop') + $running) | Out-Null
    }
}

function Invoke-VolumeMigration([switch]$Explicit) {
    if ($IsVerifyCommand) { return }
    $pending = @($VolumeKeys | Where-Object { (Test-VolumeExists "$($LegacyProject)_$_") -and -not (Test-VolumeExists "polymer_$_") })
    if (-not $pending.Count) {
        if ($Explicit) { Write-Host 'No hay volúmenes pendientes de migrar.' -ForegroundColor Green }
        return
    }
    Stop-LegacyStack
    Write-Step "Migrar $($pending.Count) volúmenes de «$LegacyProject» a «polymer» (copia verificada; los originales se conservan)"
    $log = Join-Path $WorkDir 'polymer-migration.log'
    # Copia con permisos y propietarios, y compara la lista de sumas SHA-256 de origen y destino.
    $script = 'set -e; cp -a /from/. /to/; (cd /from && find . -type f -print0 | xargs -0 -r sha256sum | sort) > /tmp/a; ' +
              '(cd /to && find . -type f -print0 | xargs -0 -r sha256sum | sort) > /tmp/b; cmp -s /tmp/a /tmp/b; ' +
              'echo $(wc -l < /tmp/a) archivos, $(du -sh /to | cut -f1)'
    # Sin comillas dobles: Windows PowerShell 5.1 las elimina al pasar argumentos a programas nativos.
    foreach ($key in $pending) {
        $old = "$($LegacyProject)_$key"
        $new = "polymer_$key"
        Invoke-Native 'docker' @('volume', 'create', '--label', 'com.docker.compose.project=polymer', '--label', "com.docker.compose.volume=$key",
                                 '--label', "polymer.migrated-from=$old", $new) | Out-Null
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { $result = (& docker run --rm --network none -v "${old}:/from:ro" -v "${new}:/to" $CopyImage sh -c $script 2>&1 | Out-String).Trim() }
        finally { $ErrorActionPreference = $previous }
        if ($LASTEXITCODE -ne 0) {
            # Solo se elimina la copia INCOMPLETA recién creada; el volumen original no se toca.
            & docker volume rm $new *> $null
            throw "La copia de $old a $new no se pudo verificar ($result). $old sigue intacto; no se arrancó nada."
        }
        Write-Host "  $old → $new  verificado: $result" -ForegroundColor Green
        Add-Content -LiteralPath $log -Value "$(Get-Date -Format s)  $old -> $new  OK ($result)" -Encoding UTF8
    }
    Write-Host "Migración completada. Los volúmenes $($LegacyProject)_* se conservan intactos como copia de seguridad (registro: $log)." -ForegroundColor Green
}

# ---------------------------------------------------------------- credenciales locales

# Servicios que REALMENTE usan credenciales (auditoría en docs/SECRETOS.md). Redis, Loki, Tempo,
# OpenTelemetry y Alloy no tienen inicio de sesión y no publican puertos; Prometheus y Mailpit solo
# escuchan en 127.0.0.1. No se les añaden contraseñas: romperían healthchecks y exportadores sin
# aportar seguridad real frente al aislamiento de red actual.
$CredentialFiles = [ordered]@{
    db       = @{ File = 'db_password.txt';       Volume = 'postgres_data'; Label = 'PostgreSQL (usuario orders)' }
    grafana  = @{ File = 'grafana_password.txt';  Volume = 'grafana_data';  Label = 'Grafana (usuario admin)' }
    rabbitmq = @{ File = 'rabbitmq_password.txt'; Volume = 'rabbitmq_data'; Label = 'RabbitMQ (usuario hexaorders, heredado)' }
    demo     = @{ File = 'demo_password.txt';     Volume = 'postgres_data'; Label = 'Cuentas de demostración de la tienda' }
}
# Caracteres seguros para todos los consumidores (rabbitmq.conf, variables, archivos): sin espacios,
# comillas, barras invertidas ni $.
$SecretPattern = '^[A-Za-z0-9._~!@#%^*+=:,-]{16,64}$'
$MemorableWords = @('acero','agua','aire','alba','ancla','arena','arpa','astro','atlas','balsa','barco','bosque','brisa','bronce',
    'cable','cactus','calma','campo','canal','cobre','cometa','coral','cosmos','cumbre','delta','duna','eco','eje','faro','fibra',
    'flecha','foco','fuego','gema','globo','grano','granito','halcon','helio','hierro','hoja','horno','iman','isla','jade','jaguar',
    'kayak','lago','lanza','laser','lienzo','lima','lince','lirio','lluvia','lobo','lucero','luna','madera','magma','malla','mango',
    'mapa','marea','metal','mirlo','molino','monte','motor','muelle','nexo','niebla','nieve','nodo','norte','nube','oasis','ocaso',
    'olivo','onda','orbita','oro','oso','palma','panal','pixel','planeta','plata','pluma','polen','prisma','puente','pulso','puma',
    'quasar','radar','rayo','red','reloj','rio','roble','roca','rubi','ruta','salto','satelite','selva','senda','sierra','silicio',
    'sol','sonar','surco','taller','tango','tejo','tigre','titan','torre','trigo','trueno','tunel','turbo','valle','vapor','vector',
    'vela','viento','volcan','yunque','zafiro','zenit','zorro')

function Get-RandomIndex([int]$Max) {
    $bytes = New-Object byte[] 4
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [int]([System.BitConverter]::ToUInt32($bytes, 0) % [uint32]$Max)
}

function New-MemorablePassphrase {
    # Cinco palabras al azar (criptográfico) + número + símbolo: fácil de recordar, ~45 bits.
    $words = 1..5 | ForEach-Object {
        $word = $MemorableWords[(Get-RandomIndex $MemorableWords.Count)]
        $word.Substring(0, 1).ToUpperInvariant() + $word.Substring(1)
    }
    $symbols = @('!', '#', '%', '+', '=', '@')
    return ($words -join '-') + '-' + (10 + (Get-RandomIndex 90)) + $symbols[(Get-RandomIndex $symbols.Count)]
}

function ConvertFrom-Secure([System.Security.SecureString]$Secure) {
    $pointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Read-NewSecret([string]$Label) {
    # Pide la contraseña dos veces sin mostrarla (o la lee de -NewPasswordFile). Nunca la imprime.
    if ($NewPasswordFile) { return ([System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $NewPasswordFile))).TrimEnd([char[]]@([char]13, [char]10)) }
    $first = ConvertFrom-Secure (Read-Host "Nueva contraseña para $Label" -AsSecureString)
    $second = ConvertFrom-Secure (Read-Host 'Repítela' -AsSecureString)
    if ($first -cne $second) { throw 'Las contraseñas no coinciden. No se cambió nada.' }
    return $first
}

function Assert-ServiceSecret([string]$Value, [string]$Key) {
    if ($Value -cnotmatch $SecretPattern) {
        throw ('La contraseña debe tener entre 16 y 64 caracteres, sin espacios ni comillas: letras, números y . _ ~ ! @ # % ^ * + = : , -. ' +
               'Una frase con guiones es ideal (por ejemplo, cinco palabras unidas por guiones).')
    }
    $dir = Join-Path $WorkDir 'secrets'
    foreach ($other in $CredentialFiles.Keys) {
        if ($other -eq $Key) { continue }
        $path = Join-Path $dir $CredentialFiles[$other].File
        if ((Test-Path -LiteralPath $path -PathType Leaf) -and ([System.IO.File]::ReadAllText($path).Trim() -ceq $Value)) {
            throw "No reutilices la contraseña de otro servicio ($($CredentialFiles[$other].Label)). Usa una distinta para cada uno."
        }
    }
}

function Write-SecretFile([string]$Key, [string]$Value) {
    $dir = Join-Path $WorkDir 'secrets'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $path = Join-Path $dir $CredentialFiles[$Key].File
    [System.IO.File]::WriteAllText($path, $Value, $utf8NoBom)
    if (-not $OnWindows) { & chmod 644 $path }
}

function Invoke-Secrets {
    if (-not $Memorable) { Initialize-WorkDir -SecretsOnly; return }
    # Solo para lo que es realmente nuevo: archivo ausente y SIN datos persistentes del servicio.
    Write-Step 'Contraseñas memorables para una instalación nueva'
    $dockerAvailable = [bool](Get-Command docker -ErrorAction SilentlyContinue)
    foreach ($key in $CredentialFiles.Keys) {
        $item = $CredentialFiles[$key]
        $path = Join-Path (Join-Path $WorkDir 'secrets') $item.File
        if (Test-Path -LiteralPath $path -PathType Leaf) { Write-Host "  $($item.Label): ya existe; se conserva."; continue }
        $hasData = $false
        if ($dockerAvailable) {
            $hasData = (Test-VolumeExists "polymer_$($item.Volume)") -or (Test-VolumeExists "$($LegacyProject)_$($item.Volume)")
        }
        if ($hasData) {
            Write-Host "  $($item.Label): HAY DATOS y falta su contraseña. No se genera una nueva; usa import-secrets (docs/SECRETOS.md)." -ForegroundColor Yellow
            continue
        }
        $choice = '1'
        if (-not $AssumeYes) {
            Write-Host ''
            Write-Host "  $($item.Label):"
            $choice = Read-Host '    [1] frase memorable generada  [2] escribirla yo  [Enter] aleatoria de 48 caracteres'
        }
        if ($choice -eq '1') { $value = New-MemorablePassphrase }
        elseif ($choice -eq '2') { $value = Read-NewSecret $item.Label }
        else { continue }  # init-env.ps1 la generará aleatoria
        Assert-ServiceSecret $value $key
        Write-SecretFile $key $value
        Write-Host "    Guardada en secrets\$($item.File) (ábrela en VS Code para verla; nunca se muestra aquí)." -ForegroundColor Green
    }
    Initialize-WorkDir -SecretsOnly  # completa con aleatorias las que hayas dejado en blanco
}

function Get-SecretValue([string]$Key) {
    $path = Join-Path (Join-Path $WorkDir 'secrets') $CredentialFiles[$Key].File
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "No existe secrets\$($CredentialFiles[$Key].File)." }
    return ([System.IO.File]::ReadAllText($path)).TrimEnd([char[]]@([char]13, [char]10))
}

function Get-BasicAuthHeader([string]$User, [string]$Password) {
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("${User}:$Password")) }
}

function Test-ServiceRunning([string]$Name) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $id = (& docker compose -f $ComposeFile ps -q --status running $Name 2>$null | Out-String).Trim() } finally { $ErrorActionPreference = $previous }
    return [bool]$id
}

function Restart-OrderApi {
    Write-Step 'Reiniciar order-api para que use la nueva credencial y esperar a que esté sano'
    [void](Invoke-Compose @('restart', 'order-api'))
    [void](Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout", 'order-api', 'frontend'))
}

function Set-GrafanaAdminPassword([string]$Current, [string]$Value) {
    # API de Grafana (127.0.0.1:3000) autenticada con la contraseña actual: nada pasa por la línea de comandos.
    $body = @{ oldPassword = $Current; newPassword = $Value; confirmNew = $Value } | ConvertTo-Json -Compress
    [void](Invoke-RestMethod -UseBasicParsing -Method Put -Uri 'http://127.0.0.1:3000/api/user/password' `
        -Headers (Get-BasicAuthHeader 'admin' $Current) -ContentType 'application/json' -Body $body -TimeoutSec 15)
}

function Test-GrafanaPassword([string]$Value) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:3000/api/user' -Headers (Get-BasicAuthHeader 'admin' $Value) -TimeoutSec 15
        return ($response.StatusCode -eq 200)
    } catch { return $false }
}

function Set-RabbitPassword([string]$Current, [string]$Value) {
    # API de gestión (127.0.0.1:15672): la contraseña viaja en el cuerpo, nunca en la línea de comandos.
    $user = 'hexaorders'   # usuario heredado guardado en el volumen de RabbitMQ (docs/MIGRACION_POLYMER.md)
    $uri = "http://127.0.0.1:15672/api/users/$user"
    $info = Invoke-RestMethod -UseBasicParsing -Uri $uri -Headers (Get-BasicAuthHeader $user $Current) -TimeoutSec 15
    $tags = @($info.tags) -join ','
    $body = @{ password = $Value; tags = $tags } | ConvertTo-Json -Compress
    [void](Invoke-RestMethod -UseBasicParsing -Method Put -Uri $uri -Headers (Get-BasicAuthHeader $user $Current) -ContentType 'application/json' -Body $body -TimeoutSec 15)
}

function Test-RabbitPassword([string]$Value) {
    try {
        $who = Invoke-RestMethod -UseBasicParsing -Uri 'http://127.0.0.1:15672/api/whoami' -Headers (Get-BasicAuthHeader 'hexaorders' $Value) -TimeoutSec 15
        return [bool]$who.name
    } catch { return $false }
}

function Invoke-ChangePassword {
    if (-not $Service) { throw 'Indica el servicio: .\polymer.cmd change-password -Service db|grafana|rabbitmq|demo' }
    $item = $CredentialFiles[$Service]
    if ($Service -eq 'demo') {
        Write-Host ('demo_password.txt solo se usa la PRIMERA vez para crear las seis cuentas de demostración; cambiarlo no ' +
                    'cambia las cuentas existentes. Cada cuenta cambia su contraseña desde «Mi cuenta → Perfil y seguridad», ' +
                    'con la política de contraseñas de la tienda.') -ForegroundColor Yellow
        return
    }
    Assert-Docker
    $required = @{ db = 'db'; grafana = 'grafana'; rabbitmq = 'rabbitmq' }[$Service]
    if (-not (Test-ServiceRunning $required)) { throw "El servicio $required no está en marcha. Arranca primero con: .\polymer.cmd start" }
    $current = Get-SecretValue $Service
    $value = Read-NewSecret $item.Label
    Assert-ServiceSecret $value $Service
    if ($value -ceq $current) { throw 'La nueva contraseña es igual a la actual. No se cambió nada.' }
    Write-Host ''
    Write-Host "Vas a cambiar la contraseña de $($item.Label)." -ForegroundColor Yellow
    Write-Host 'Se guardará una copia de la credencial actual en secrets\backup\ y los datos NO se tocan.'
    if (-not (Confirm-Action '¿Continuar?')) { Write-Host 'Cancelado: no se cambió nada.'; return }

    $backupDir = Join-Path (Join-Path $WorkDir 'secrets') 'backup'
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
    $backup = Join-Path $backupDir ("$($item.File).$(Get-Date -Format 'yyyyMMdd-HHmmss').bak")
    Copy-Item -LiteralPath (Join-Path (Join-Path $WorkDir 'secrets') $item.File) -Destination $backup
    Write-Host "Copia de la credencial actual: $backup"

    Write-Step "Cambiar la contraseña de $($item.Label)"
    switch ($Service) {
        'db' {
            Set-DatabasePassword $value
            if (-not (Test-DatabasePassword $value)) { Set-DatabasePassword $current; throw 'PostgreSQL no aceptó la nueva contraseña; se restauró la anterior.' }
            Write-SecretFile $Service $value
            try { Restart-OrderApi } catch { Set-DatabasePassword $current; Write-SecretFile $Service $current; Restart-OrderApi; throw "order-api no arrancó con la nueva contraseña; se restauró la anterior. $($_.Exception.Message)" }
        }
        'grafana' {
            Set-GrafanaAdminPassword $current $value
            if (-not (Test-GrafanaPassword $value)) { throw 'Grafana no confirmó la nueva contraseña; la anterior sigue en secrets\backup\ por si la necesitas.' }
            Write-SecretFile $Service $value
        }
        'rabbitmq' {
            Set-RabbitPassword $current $value
            if (-not (Test-RabbitPassword $value)) { Set-RabbitPassword $value $current; throw 'RabbitMQ no aceptó la nueva contraseña; se restauró la anterior.' }
            Write-SecretFile $Service $value
            try { Restart-OrderApi } catch { Set-RabbitPassword $value $current; Write-SecretFile $Service $current; Restart-OrderApi; throw "order-api no arrancó con la nueva contraseña; se restauró la anterior. $($_.Exception.Message)" }
        }
    }
    Write-Host "Contraseña de $($item.Label) cambiada y comprobada. secrets\$($item.File) actualizado (no se muestra)." -ForegroundColor Green
}

# ---------------------------------------------------------------- comandos

function Invoke-Help { Get-Help $PSCommandPath -Detailed }

function Assert-DatabaseSecretMatchesVolume {
    # PostgreSQL, Grafana y RabbitMQ guardan su contraseña al inicializar su volumen. Si el volumen
    # existe y falta el archivo de secreto, generar uno nuevo dejaría el servicio sin acceso a sus
    # datos. En ese caso se detiene con instrucciones: nunca se borran volúmenes ni se regeneran secretos.
    if ($IsVerifyCommand) { return }
    foreach ($pair in $SecretVolumes) {
        $secret = Join-Path (Join-Path $WorkDir 'secrets') $pair.Secret
        if (Test-Path -LiteralPath $secret -PathType Leaf) { continue }
        foreach ($volume in @("polymer_$($pair.Key)", "$($LegacyProject)_$($pair.Key)")) {
            if (Test-VolumeExists $volume) {
                throw ("Existe el volumen $volume (con $($pair.Data)) pero falta $secret. " +
                       'Tus datos están intactos. Copia la carpeta secrets\ de tu instalación anterior a esta carpeta, o ejecuta: ' +
                       '.\polymer.cmd import-secrets -SecretsFrom "C:\ruta\de\HexaOrders-v6". ' +
                       'No generes contraseñas nuevas ni borres volúmenes: perderías el acceso a esos datos.')
            }
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
    Import-LegacySecrets
    Assert-DatabaseSecretMatchesVolume
    Initialize-WorkDir
    Stop-LegacyStack
    Invoke-VolumeMigration
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
    $running = & docker ps -q --filter 'label=com.docker.compose.project=polymer' --filter 'label=com.docker.compose.service=rabbitmq'
    if ($running) {
        Write-Step 'Integraciones del modo full (Redis, RabbitMQ, Mailpit, Loki, Tempo, OpenTelemetry)'
        Invoke-IntegrationSmoke @('check')
    } else {
        Write-Host 'Modo básico: se omiten las pruebas de integración del perfil full.'
    }
    Write-Host 'Pruebas HTTP correctas.' -ForegroundColor Green
}

function Invoke-Status { Assert-Docker; [void](Invoke-Compose @('ps', '-a')) }

function Get-ComposeServiceNames {
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $names = & docker compose -f $ComposeFile config --services 2>$null } finally { $ErrorActionPreference = $previous }
    return @($names | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
}

function Assert-ServiceNames([string[]]$Names) {
    $known = Get-ComposeServiceNames
    foreach ($name in $Names) {
        if ($known -notcontains $name) { throw "Servicio desconocido: $name. Servicios: $($known -join ', ')" }
    }
}

function Get-HttpGetStatus([string]$Uri) {
    # GET que devuelve el código HTTP (0 si no hay respuesta) en PowerShell 5.1 y 7.
    try {
        return [int](Invoke-WebRequest -Uri $Uri -Method Get -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop).StatusCode
    } catch {
        $exception = $_.Exception
        if ($exception.PSObject.Properties['Response'] -and $exception.Response) { return [int]$exception.Response.StatusCode }
        return 0
    }
}

function Invoke-Health {
    # Salud real: estado y healthcheck de cada contenedor + respuesta HTTP de las interfaces publicadas.
    Assert-Docker
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $rows = & docker compose -f $ComposeFile ps -a --format '{{.Service}}|{{.State}}|{{.Health}}' 2>$null } finally { $ErrorActionPreference = $previous }
    $problems = @()
    Write-Host ''
    Write-Host ('{0,-16} {1,-10} {2}' -f 'SERVICIO', 'ESTADO', 'SALUD') -ForegroundColor Cyan
    $expected = if ($FullMode) { 13 } else { 6 }
    $count = 0
    foreach ($row in @($rows)) {
        $parts = "$row".Split('|')
        if ($parts.Count -lt 3) { continue }
        $count++
        $ok = $parts[1] -eq 'running' -and $parts[2] -ne 'unhealthy' -and $parts[2] -ne 'starting'
        Write-Host ('{0,-16} {1,-10} {2}' -f $parts[0], $parts[1], $parts[2]) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
        if (-not $ok) { $problems += "$($parts[0]): $($parts[1]) $($parts[2])" }
    }
    if ($count -lt $expected) { $problems += "Hay $count contenedores; se esperaban $expected (modo $Mode)" }
    Write-Host ''
    $checks = [ordered]@{ 'Tienda (gateway)' = 'http://127.0.0.1:8080/'; 'API (gateway)' = 'http://127.0.0.1:8080/api/catalog/categories';
                          'Grafana' = 'http://127.0.0.1:3000/api/health'; 'Prometheus' = 'http://127.0.0.1:9091/-/ready' }
    if ($FullMode) { $checks['Mailpit'] = 'http://127.0.0.1:8025/'; $checks['RabbitMQ (consola)'] = 'http://127.0.0.1:15672/' }
    foreach ($name in $checks.Keys) {
        $code = Get-HttpGetStatus $checks[$name]
        $ok = $code -ge 200 -and $code -lt 400
        Write-Host ('{0,-20} {1,-45} HTTP {2}' -f $name, $checks[$name], $code) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
        if (-not $ok) { $problems += "$name respondió HTTP $code" }
    }
    if ($problems) { throw "Servicios con problemas:`n  - $($problems -join "`n  - ")`nDiagnóstico: .\polymer.cmd logs" }
    Write-Host "`nTodo sano: $count contenedores y las interfaces responden." -ForegroundColor Green
}

function Invoke-Rebuild {
    # Reconstruye las imágenes (los Dockerfiles ejecutan los tests) y recrea SOLO esos servicios.
    # Nunca borra volúmenes: los datos de PostgreSQL, RabbitMQ, Grafana… se conservan.
    Assert-Docker
    Assert-DatabaseSecretMatchesVolume
    $targets = @($Services | Where-Object { $_ })
    if ($targets) { Assert-ServiceNames $targets }
    Write-Step "Reconstruir $(if ($targets) { $targets -join ', ' } else { 'todas las imágenes' }) (incluye los tests de la imagen)"
    [void](Invoke-Compose (@('build') + $targets))
    Write-Step 'Recrear los servicios reconstruidos y esperar a que estén sanos (los volúmenes se conservan)'
    [void](Invoke-Compose (@('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout") + $targets))
    [void](Invoke-Compose @('ps'))
}

function Invoke-Restart {
    Assert-Docker
    $targets = @($Services | Where-Object { $_ })
    if (-not $targets) { throw 'Indica qué reiniciar, por ejemplo: .\polymer.cmd restart -Services order-api' }
    Assert-ServiceNames $targets
    Write-Step "Reiniciar $($targets -join ', ') (sin borrar datos)"
    [void](Invoke-Compose (@('restart') + $targets))
    [void](Invoke-Compose (@('up', '-d', '--wait', '--wait-timeout', "$WaitTimeout") + $targets))
    [void](Invoke-Compose @('ps'))
}

function Invoke-DemoScript([string]$Name, [hashtable]$Extra = @{}) {
    Assert-Docker
    $arguments = @{ Scenario = $Name; ComposeFile = $ComposeFile; NoPause = [bool]$NoPause }
    foreach ($key in $Extra.Keys) { $arguments[$key] = $Extra[$key] }
    & (Join-Path (Join-Path $PSScriptRoot 'demo') 'polymer-demo.ps1') @arguments
}

function Invoke-Logs { Assert-Docker; [void](Invoke-Compose @('logs', '--no-color', '--tail=100')) }

function Invoke-Stop {
    Assert-Docker
    if ($RemoveData) {
        Write-Host 'Deteniendo y ELIMINANDO volúmenes (pedidos, métricas y Grafana)...' -ForegroundColor Yellow
        [void](Invoke-Compose @('down', '-v', '--remove-orphans'))
    } else {
        [void](Invoke-Compose @('down'))
        Write-Host 'Servicios detenidos. Los datos se conservan; vuelve a arrancar con: .\polymer.cmd start'
    }
}


function Invoke-K8sSecrets {
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) { throw 'No se encontró kubectl.' }
    Initialize-WorkDir -SecretsOnly
    $secrets = Join-Path $WorkDir 'secrets'
    [void](Invoke-Native 'kubectl' @('apply', '-f', (Join-Path (Join-Path $RepoRoot 'k8s') '00-namespace.yaml')))
    $yaml = & kubectl -n polymer create secret generic app-secrets `
        "--from-file=db_password=$(Join-Path $secrets 'db_password.txt')" `
        "--from-file=grafana_password=$(Join-Path $secrets 'grafana_password.txt')" `
        "--from-file=demo_password=$(Join-Path $secrets 'demo_password.txt')" `
        --dry-run=client -o yaml
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el Secret app-secrets.' }
    $yaml | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo aplicar el Secret app-secrets.' }
    Write-Host 'Secret polymer/app-secrets aplicado desde secrets\*.txt.'
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
    Write-Step 'Idiomas: catálogos en, fr, it, pt y de completos y con marcadores coherentes'
    Invoke-Python @((Join-Path (Join-Path $RepoRoot 'tools') 'check_i18n.py'))
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
        $running = & docker ps -q --filter 'label=com.docker.compose.project=polymer'
        if ($running) { throw 'El entorno "polymer" está en marcha y usa los mismos puertos. Detenlo con: .\polymer.cmd stop' }
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
        'import-secrets' { Assert-Docker; Import-LegacySecrets -Explicit }
        'change-password' { Invoke-ChangePassword }
        'migrate'     { Assert-Docker; Import-LegacySecrets; Assert-DatabaseSecretMatchesVolume; Initialize-WorkDir; Invoke-VolumeMigration -Explicit }
        'k8s-secrets' { Invoke-K8sSecrets }
        'check'       { Invoke-Check; Write-Host 'Comprobaciones correctas.' -ForegroundColor Green }
        'test'        { Invoke-Test }
        'smoke'       { Invoke-Smoke }
        'verify'      { Invoke-Verify }
        'diagnose'    { Invoke-Diagnose }
        'cleanup'     { Invoke-Cleanup }
        'health'      { Invoke-Health }
        'rebuild'     { Invoke-Rebuild }
        'restart'     { Invoke-Restart }
        'demo'        { Invoke-DemoScript $Scenario }
        'demo-on'     { Invoke-DemoScript 'activar-demo' @{ DemoTtl = $DemoTtl; FixedRisk = [bool]$FixedRisk; FixedScore = $FixedScore } }
        'demo-off'    { Invoke-DemoScript 'desactivar-demo' }
    }
    if ($InGitHub -and $Command -ne 'verify') { Write-Host '::endgroup::' }
    exit 0
} catch {
    if ($InGitHub) { Write-Host '::endgroup::'; Write-Host "::error::$($_.Exception.Message)" }
    else { Write-Host ''; Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red }
    exit 1
}
