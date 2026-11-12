<#
.SYNOPSIS
    Escenarios de la exposición de POLYMER (arquitectura hexagonal, Redis, RabbitMQ, PostgreSQL,
    microservicios y resiliencia) contra el stack REAL de Docker. Windows PowerShell 5.1 y pwsh 7.

.DESCRIPTION
    Normalmente se usa a través de polymer.cmd:
        .\polymer.cmd demo -Scenario preparar
        .\polymer.cmd demo -Scenario redis
    Cada escenario imprime qué hace, las evidencias (claves de Redis, filas de PostgreSQL, colas de
    RabbitMQ, correos de Mailpit, métricas) y FALLA si una evidencia no se cumple: así el mismo
    script sirve para ensayar la exposición y para comprobarla en el CI.

    Seguridad de los datos:
      - Solo crea datos FICTICIOS nuevos (clientes demo_* con correos @example.test y sus pedidos).
      - Las consultas SQL son de solo lectura. Nunca borra pedidos, usuarios ni volúmenes.
      - En Redis solo borra, en el escenario «redis», UNA clave de caché del catálogo (nunca FLUSHALL).
      - Los escenarios «*-caida» detienen un contenedor y lo vuelven a arrancar (docker compose stop/up).
      - Nunca imprime contraseñas: la de RabbitMQ se lee de secrets\ solo para su API de gestión.

    Escenarios: preparar, arquitectura, redis, redis-caida, rabbitmq, rabbitmq-caida, postgres,
                riesgo, riesgo-caida, adaptador, observabilidad, todo
    Modo demostración (solo configuración de order-api, nunca datos):
                activar-demo [-DemoTtl 20] [-FixedRisk [-FixedScore 10]]   ·   desactivar-demo
#>
[CmdletBinding()]
param(
    [ValidateSet('preparar', 'arquitectura', 'redis', 'redis-caida', 'rabbitmq', 'rabbitmq-caida', 'postgres',
                 'riesgo', 'riesgo-caida', 'adaptador', 'observabilidad', 'todo', 'activar-demo', 'desactivar-demo')]
    [string]$Scenario = 'preparar',
    # docker-compose.yml del entorno (por defecto, el de la carpeta del proyecto).
    [string]$ComposeFile,
    # Proyecto de Compose (vacío = el del archivo, «polymer»; el CI usa «polymer-verify»).
    [string]$Project,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$PrometheusUrl = 'http://127.0.0.1:9091',
    [string]$MailpitUrl = 'http://127.0.0.1:8025',
    [string]$RabbitUrl = 'http://127.0.0.1:15672',
    # Sin pausas entre pasos (CI). En la exposición, cada paso espera Enter.
    [switch]$NoPause,
    # «arquitectura»: no ejecutar las pruebas en un contenedor Maven (solo mostrar el mapa).
    [switch]$SkipMavenTests,
    # activar-demo: TTL de la caché del catálogo en segundos (solo en modo demostración).
    [ValidateRange(5, 3600)]
    [int]$DemoTtl = 20,
    # activar-demo: usar FixedRiskAssessmentAdapter en lugar del adaptador HTTP real.
    [switch]$FixedRisk,
    [ValidateRange(0, 100)]
    [int]$FixedScore = 10
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
if (-not $ComposeFile) { $ComposeFile = Join-Path $RepoRoot 'docker-compose.yml' }
$ComposeFile = (Resolve-Path $ComposeFile).Path
$SecretsDir = Join-Path (Split-Path -Parent $ComposeFile) 'secrets'
if (-not $env:COMPOSE_PROFILES) { $env:COMPOSE_PROFILES = 'full' }
$script:Evidence = 0

# ------------------------------------------------------------------ utilidades de presentación

function Show-Title([string]$Text) {
    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor DarkCyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ('=' * 78) -ForegroundColor DarkCyan
}

function Show-Step([string]$Text) { Write-Host ''; Write-Host "▶ $Text" -ForegroundColor Yellow }
function Show-Info([string]$Text) { Write-Host "  $Text" }
function Show-Code([string]$Text) { Write-Host "  $Text" -ForegroundColor DarkGray }

function Assert-Evidence($Condition, [string]$Text) {
    # Acepta un booleano o un valor (id, filas…): vacío, $null, 0 o $false = no cumplida.
    if (-not [bool]$Condition) { throw "EVIDENCIA NO CUMPLIDA: $Text" }
    $script:Evidence++
    Write-Host "  ✔ $Text" -ForegroundColor Green
}

function Wait-Presenter([string]$Text = 'Pulsa Enter para continuar') {
    if (-not $NoPause) { [void](Read-Host "  ⏸  $Text") }
}

# ------------------------------------------------------------------ Docker

function Invoke-Dc {
    # docker compose con el archivo y el proyecto del entorno; devuelve la salida como texto.
    param([string[]]$Arguments, [switch]$AllowFailure, [string]$InputText, [string]$ExtraFile)
    $base = @('compose', '-f', $ComposeFile)
    if ($ExtraFile) { $base += @('-f', $ExtraFile) }
    if ($Project) { $base += @('-p', $Project) }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($InputText) { $output = $InputText | & docker @($base + $Arguments) 2>&1 }
        else { $output = & docker @($base + $Arguments) 2>&1 }
    } finally { $ErrorActionPreference = $previous }
    # stdout es el resultado; stderr (avisos de Compose, progreso) solo se muestra si el comando falla.
    $stdout = @($output | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
    $stderr = @($output | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
    if ($LASTEXITCODE -ne 0 -and -not $AllowFailure) { throw "docker compose $($Arguments -join ' ') falló:`n$(($stdout + $stderr) -join "`n")" }
    return ($stdout -join "`n")
}

function Get-RunningServices { return @((Invoke-Dc @('ps', '--status', 'running', '--services')) -split "`n" | Where-Object { $_ }) }

function Assert-Running([string[]]$Services) {
    $running = Get-RunningServices
    foreach ($service in $Services) {
        if ($running -notcontains $service) { throw "El servicio $service no está en marcha. Ejecuta: .\polymer.cmd start" }
    }
}

function Wait-Healthy([string[]]$Services) {
    [void](Invoke-Dc (@('up', '-d', '--wait', '--wait-timeout', '180') + $Services))
}

function Invoke-RedisCli([string[]]$Arguments) { return (Invoke-Dc (@('exec', '-T', 'redis', 'redis-cli', '--no-raw') + $Arguments)).Trim() }

function Invoke-RedisRaw([string[]]$Arguments) { return (Invoke-Dc (@('exec', '-T', 'redis', 'redis-cli', '--raw') + $Arguments)).Trim() }

function Invoke-Sql([string]$Query) {
    # Solo lectura: psql dentro del contenedor db (socket local, sin publicar el puerto).
    return (Invoke-Dc @('exec', '-T', 'db', 'psql', '-U', 'orders', '-d', 'orders', '-v', 'ON_ERROR_STOP=1', '-P', 'pager=off', '-c', $Query)).TrimEnd()
}

function Invoke-SqlValue([string]$Query) {
    return (Invoke-Dc @('exec', '-T', 'db', 'psql', '-U', 'orders', '-d', 'orders', '-v', 'ON_ERROR_STOP=1', '-tA', '-c', $Query)).Trim()
}

function Get-ApiMetrics {
    # Métricas de order-api en su puerto de gestión (9090, solo red interna): el mismo texto que lee Prometheus.
    $text = Invoke-Dc @('exec', '-T', 'order-api', 'curl', '-s', '--max-time', '5', 'http://127.0.0.1:9090/actuator/prometheus')
    $values = @{}
    foreach ($line in ($text -split "`n")) {
        if ($line -match '^(polymer_cache_requests_total)\{[^}]*result="(\w+)"[^}]*\}\s+([0-9.eE+-]+)') { $values["cache_$($Matches[2])"] = [double]$Matches[3] }
        elseif ($line -match '^(polymer_outbox_pending)(\{[^}]*\})?\s+([0-9.eE+-]+|NaN)') { $values['outbox_pending'] = $Matches[3] }
        elseif ($line -match '^(polymer_events_published_total)(\{[^}]*\})?\s+([0-9.eE+-]+)') { $values['events_published'] = [double]$values['events_published'] + [double]$Matches[3] }
    }
    foreach ($key in 'cache_hit', 'cache_miss', 'cache_error', 'cache_bypass') { if (-not $values.ContainsKey($key)) { $values[$key] = 0.0 } }
    return $values
}

function Show-CacheMetrics($Metrics) {
    Show-Info ("polymer_cache_requests_total: hit={0} miss={1} error={2} bypass={3}" -f $Metrics.cache_hit, $Metrics.cache_miss, $Metrics.cache_error, $Metrics.cache_bypass)
}

function Get-OrderApiEnv([string]$Name) {
    $id = (Invoke-Dc @('ps', '-q', 'order-api')).Trim()
    if (-not $id) { return $null }
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $envLines = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $id 2>$null } finally { $ErrorActionPreference = $previous }
    foreach ($line in @($envLines)) { if ("$line" -like "$Name=*") { return "$line".Substring($Name.Length + 1) } }
    return $null
}

# ------------------------------------------------------------------ HTTP (sesión de navegador simulada)

function Invoke-Api {
    param($Session, [string]$Method = 'GET', [string]$Path, $Body, [hashtable]$Headers = @{}, [int[]]$Expect = @(200))
    $uri = if ($Path -like 'http*') { $Path } else { "$BaseUrl$Path" }
    $all = @{}
    foreach ($key in $Headers.Keys) { $all[$key] = $Headers[$key] }
    if ($Session -and $Session.Csrf -and $Method -ne 'GET') { $all[$Session.CsrfHeader] = $Session.Csrf }
    $params = @{ Uri = $uri; Method = $Method; Headers = $all; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($Session) { $params.WebSession = $Session.Web }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
    }
    $status = 0; $content = ''; $responseHeaders = @{}
    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $content = $response.Content
        $responseHeaders = $response.Headers
    } catch {
        $raw = $_.Exception.Response
        if ($null -eq $raw) { throw }
        $status = [int]$raw.StatusCode
    }
    if ($Expect -notcontains $status) { throw "$Method $Path respondió $status (se esperaba $($Expect -join ' o '))" }
    $data = $null
    if ($content) {
        $trimmed = "$content".TrimStart()
        if ($trimmed.StartsWith('{') -or $trimmed.StartsWith('[')) { $data = $content | ConvertFrom-Json }
    }
    return [pscustomobject]@{ Status = $status; Data = $data; Headers = $responseHeaders }
}

function Update-Csrf($Session) {
    $csrf = (Invoke-Api -Session $Session -Path '/api/auth/csrf').Data
    $Session.Csrf = $csrf.token
    $Session.CsrfHeader = $csrf.headerName
}

function New-DemoCustomer {
    # Cliente FICTICIO nuevo: usuario demo_xxxx, correo @example.test (dominio reservado, nunca real).
    $suffix = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $username = "demo_$suffix"
    $email = "demo.$suffix@example.test"
    # Frase de contraseña generada para este cliente ficticio (no se imprime ni se guarda). No contiene el
    # usuario ni el correo: la política del servidor (PasswordPolicy) los rechazaría.
    $password = "faro cobre lluvia norte $(Get-Random -Minimum 1000 -Maximum 9999)"
    $session = [pscustomobject]@{ Web = New-Object Microsoft.PowerShell.Commands.WebRequestSession; Csrf = $null; CsrfHeader = 'X-CSRF-TOKEN'; Username = $username; Email = $email; Password = $password }
    Update-Csrf $session
    [void](Invoke-Api -Session $session -Method 'POST' -Path '/api/auth/register' -Expect @(201) -Body @{
        username = $username; displayName = 'Cliente de demostración'; email = $email; password = $password })
    Connect-DemoCustomer $session
    return $session
}

function Connect-DemoCustomer($Session) {
    $Session.Web = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Update-Csrf $Session
    [void](Invoke-Api -Session $Session -Method 'POST' -Path '/api/auth/login' -Body @{ username = $Session.Username; password = $Session.Password })
    Update-Csrf $Session
}

function Get-Product([decimal]$Min, [decimal]$Max) {
    $products = (Invoke-Api -Path '/api/catalog/products').Data
    $candidates = @($products | Where-Object { [decimal]$_.price -ge $Min -and [decimal]$_.price -le $Max -and [int]$_.stock -ge 3 } | Sort-Object { [decimal]$_.price })
    if (-not $candidates) { throw "No hay productos con stock entre S/ $Min y S/ $Max" }
    return $candidates[0]
}

function New-DemoOrder($Session, $Product, [int[]]$Expect = @(201)) {
    $key = 'demo-' + ([guid]::NewGuid().ToString('N')).Substring(0, 24)
    return Invoke-Api -Session $Session -Method 'POST' -Path '/api/shop/orders' -Expect $Expect -Headers @{ 'Idempotency-Key' = $key } -Body @{
        items = @(@{ productId = $Product.id; quantity = 1 })
        shipping = @{ recipient = 'Cliente de demostración'; address = 'Av. Demostración 123'; city = 'Lima' } }
}

function Get-MailpitMessages([string]$Email) {
    $query = [uri]::EscapeDataString("to:`"$Email`"")
    return @((Invoke-RestMethod -UseBasicParsing -Uri "$MailpitUrl/api/v1/search?query=$query&limit=50" -TimeoutSec 15).messages)
}

function Wait-Until([string]$Description, [scriptblock]$Probe, [int]$TimeoutSeconds = 90, [int]$Interval = 3) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $value = & $Probe
        if ($value) { return $value }
        Start-Sleep -Seconds $Interval
    }
    throw "Tiempo agotado esperando: $Description"
}

function Get-RabbitAuth {
    $file = Join-Path $SecretsDir 'rabbitmq_password.txt'
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    $password = ([System.IO.File]::ReadAllText($file)).Trim()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("hexaorders:$password")
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($bytes) }
}

# ------------------------------------------------------------------ escenarios

function Invoke-Preparar {
    Show-Title 'Preparación: estado de los 13 contenedores y accesos'
    $lines = (Invoke-Dc @('ps', '-a', '--format', '{{.Service}}|{{.State}}|{{.Health}}')) -split "`n" | Where-Object { $_ -match '\|' }
    $expected = 'db', 'risk-engine', 'order-api', 'frontend', 'prometheus', 'grafana', 'redis', 'rabbitmq', 'mailpit', 'loki', 'tempo', 'otel-collector', 'alloy'
    foreach ($service in $expected) {
        $row = $lines | Where-Object { $_.Split('|')[0] -eq $service } | Select-Object -First 1
        $state = if ($row) { $row.Split('|')[1] } else { 'no existe' }
        $health = if ($row) { $row.Split('|')[2] } else { '' }
        Show-Info ("{0,-15} {1,-10} {2}" -f $service, $state, $health)
    }
    $healthy = @($lines | Where-Object { $_ -match '\|running\|(healthy)?$' -and $_ -notmatch 'unhealthy' }).Count
    Assert-Evidence ($healthy -eq 13) "13 contenedores en marcha y sanos ($healthy)"
    Show-Step 'Interfaces de la exposición'
    Show-Info "Tienda ............ $BaseUrl"
    Show-Info "Grafana ........... http://127.0.0.1:3000   (admin; contraseña en secrets\grafana_password.txt)"
    Show-Info "Prometheus ........ $PrometheusUrl"
    Show-Info "RabbitMQ .......... $RabbitUrl   (hexaorders; secrets\rabbitmq_password.txt)"
    Show-Info "Mailpit ........... $MailpitUrl"
    $adapter = Get-OrderApiEnv 'POLYMER_RISK_ADAPTER'
    $ttl = Get-OrderApiEnv 'POLYMER_CACHE_CATALOG_TTL'
    if ($adapter -or $ttl) { Show-Info "Modo demostración ACTIVO: adaptador de riesgo=$adapter, TTL de caché=$ttl (.\polymer.cmd demo-off lo desactiva)" }
    else { Show-Info 'Configuración normal (sin override de demostración).' }
}

function Invoke-Arquitectura {
    Show-Title 'Escenario A — Arquitectura hexagonal: del controlador al adaptador'
    $base = 'services/order-api/src/main/java/com/polymer'
    $map = @(
        @('Adaptador de ENTRADA (REST)', "$base/adapter/in/web/ShopController.java", 'place(...) → POST /api/shop/orders'),
        @('Puerto de ENTRADA', "$base/application/port/in/CommerceUseCases.java", 'placeOrder(...)'),
        @('Caso de uso', "$base/application/service/CommerceService.java", 'placeOrder(...): riesgo → persistencia → historial'),
        @('Dominio', "$base/domain/OrderStatus.java", 'fromRiskScore(score): REVIEW si score >= 70'),
        @('Puerto de SALIDA', "$base/application/port/out/RiskAssessmentPort.java", 'int assess(OrderDraft)'),
        @('Adaptador de SALIDA (HTTP)', "$base/adapter/out/http/HttpRiskAssessmentAdapter.java", 'POST http://risk-engine:8000/score'),
        @('Adaptador de SALIDA (demo)', "$base/adapter/out/fixed/FixedRiskAssessmentAdapter.java", 'puntaje fijo, sin red'),
        @('Puerto de SALIDA', "$base/application/port/out/CommerceOrderRepository.java", 'placeOrder(...)'),
        @('Adaptador de SALIDA (JDBC)', "$base/adapter/out/persistence/JdbcCommerceOrderRepository.java", 'INSERT orders / order_lines / order_status_history'),
        @('Raíz de composición', "$base/config/HexagonalConfiguration.java", 'elige el adaptador del puerto de riesgo'))
    foreach ($row in $map) {
        $exists = Test-Path -LiteralPath (Join-Path $RepoRoot $row[1])
        Assert-Evidence $exists ("{0,-28} {1}  — {2}" -f $row[0], $row[1], $row[2])
    }
    Show-Step 'Pruebas automáticas de arquitectura (ArchUnit) y de sustitución de adaptadores'
    Show-Code 'services/order-api/src/test/java/com/polymer/architecture/HexagonalArchitectureTest.java (8 reglas)'
    Show-Code 'services/order-api/src/test/java/com/polymer/adapter/AdapterSubstitutionTest.java'
    if ($SkipMavenTests) { Show-Info 'Omitidas (-SkipMavenTests). También se ejecutan al construir la imagen de order-api.'; return }
    Wait-Presenter 'Pulsa Enter para ejecutarlas en un contenedor Maven (la primera vez descarga dependencias)'
    $source = Join-Path $RepoRoot 'services/order-api'
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try {
        $output = & docker run --rm -v "${source}:/src" -v 'polymer_demo_maven:/root/.m2' -w /src maven:3.9.11-eclipse-temurin-21 `
            mvn -B -q test '-Dtest=HexagonalArchitectureTest,AdapterSubstitutionTest' '-Dsurefire.failIfNoSpecifiedTests=false' 2>&1
    } finally { $ErrorActionPreference = $previous }
    $code = $LASTEXITCODE
    @($output) | Select-Object -Last 15 | ForEach-Object { Show-Code "$_" }
    Assert-Evidence ($code -eq 0) 'ArchUnit (8 reglas) y AdapterSubstitutionTest (4 pruebas) en verde'
}

function Invoke-Redis {
    Show-Title 'Escenario C — Redis: cache miss, cache hit, TTL, expiración y nueva carga'
    Assert-Running @('redis', 'order-api')
    Show-Code 'Puerto: application/port/out/CatalogCache.java · Adaptador: adapter/out/cache/RedisCatalogCache.java'
    Show-Code 'Clave: polymer:catalog6:v<versión>:<nombre> · Versión: polymer:catalog:version · TTL: polymer.cache.catalog-ttl'
    $version = Invoke-RedisRaw @('GET', 'polymer:catalog:version')
    if (-not $version) { $version = '0' }
    $key = "polymer:catalog6:v${version}:categories"
    Show-Info "Versión actual del catálogo en caché: $version → clave del árbol de categorías: $key"
    $configured = Get-OrderApiEnv 'POLYMER_CACHE_CATALOG_TTL'
    Show-Info ("TTL configurado: {0}" -f $(if ($configured) { "$configured (modo demostración)" } else { '60s (valor normal, application.yml)' }))

    Show-Step '1. Partir de un cache miss: se borra SOLO esta clave de caché (no datos; equivale a que expire)'
    Show-Code "docker compose exec redis redis-cli DEL $key"
    [void](Invoke-RedisCli @('DEL', $key))
    Assert-Evidence ((Invoke-RedisRaw @('EXISTS', $key)) -eq '0') 'La clave no existe en Redis'
    $before = Get-ApiMetrics; Show-CacheMetrics $before
    Wait-Presenter

    Show-Step '2. Primera consulta del catálogo → MISS: lee PostgreSQL y guarda el resultado en Redis con TTL'
    Show-Code "GET $BaseUrl/api/catalog/categories"
    [void](Invoke-Api -Path '/api/catalog/categories')
    $afterMiss = Get-ApiMetrics; Show-CacheMetrics $afterMiss
    Assert-Evidence ($afterMiss.cache_miss -gt $before.cache_miss) 'polymer_cache_requests_total{result="miss"} aumentó'
    $ttl = [int](Invoke-RedisRaw @('TTL', $key))
    Assert-Evidence ($ttl -gt 0) "La clave existe ahora en Redis con TTL restante de $ttl s"
    $size = Invoke-RedisRaw @('STRLEN', $key)
    Show-Info "Tamaño del valor JSON en Redis: $size bytes"
    Wait-Presenter

    Show-Step '3. Segunda consulta → HIT: se sirve desde Redis sin tocar PostgreSQL'
    [void](Invoke-Api -Path '/api/catalog/categories')
    $afterHit = Get-ApiMetrics; Show-CacheMetrics $afterHit
    Assert-Evidence ($afterHit.cache_hit -gt $afterMiss.cache_hit) 'polymer_cache_requests_total{result="hit"} aumentó'
    Show-Code "docker compose exec redis redis-cli TTL $key   →   $(Invoke-RedisRaw @('TTL', $key)) s"
    Wait-Presenter "Pulsa Enter para esperar a que expire la clave (hasta $ttl s)"

    Show-Step '4. Expiración: Redis elimina la clave al agotarse el TTL'
    $expired = Wait-Until 'expiración de la clave' -TimeoutSeconds ($ttl + 15) -Interval 5 -Probe {
        $left = [int](Invoke-RedisRaw @('TTL', $key))
        Show-Info "TTL restante: $left s"
        if ($left -eq -2) { return $true } else { return $false }
    }
    Assert-Evidence $expired 'TTL = -2: la clave ya no existe'

    Show-Step '5. Nueva consulta → MISS otra vez: PostgreSQL responde y Redis guarda una entrada nueva'
    $beforeReload = Get-ApiMetrics
    [void](Invoke-Api -Path '/api/catalog/categories')
    $afterReload = Get-ApiMetrics; Show-CacheMetrics $afterReload
    Assert-Evidence ($afterReload.cache_miss -gt $beforeReload.cache_miss) 'Nuevo miss tras la expiración'
    $newTtl = [int](Invoke-RedisRaw @('TTL', $key))
    Assert-Evidence ($newTtl -gt 0) "Entrada nueva en Redis con TTL completo ($newTtl s)"

    Show-Step '6. Las mismas métricas en Prometheus (y en Grafana: dashboard «POLYMER — Platform Integrations»)'
    Show-Code 'sum by (result) (polymer_cache_requests_total)'
    try {
        $series = (Invoke-RestMethod -UseBasicParsing -Uri "$PrometheusUrl/api/v1/query?query=sum%20by%20(result)%20(polymer_cache_requests_total)" -TimeoutSec 10).data.result
        foreach ($s in $series) { Show-Info ("Prometheus: result={0} → {1}" -f $s.metric.result, $s.value[1]) }
        Assert-Evidence (@($series).Count -ge 2) 'Prometheus recopila polymer_cache_requests_total por resultado'
    } catch { Show-Info "Prometheus no respondió en $PrometheusUrl ($($_.Exception.Message))" }
}

function Invoke-RedisCaida {
    Show-Title 'Escenario H1 — Resiliencia: Redis detenido, el catálogo sigue desde PostgreSQL'
    Assert-Running @('redis', 'order-api')
    $before = Get-ApiMetrics; Show-CacheMetrics $before
    Show-Step '1. Detener Redis (dependencia NO crítica)'
    Show-Code 'docker compose stop redis'
    [void](Invoke-Dc @('stop', 'redis'))
    Wait-Presenter
    Show-Step '2. Consultar el catálogo: responde desde PostgreSQL; la caché cuenta error y bypass'
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $products = (Invoke-Api -Path '/api/catalog/products').Data
    $watch.Stop()
    [void](Invoke-Api -Path '/api/catalog/categories')
    Assert-Evidence (@($products).Count -gt 0) ("Catálogo servido sin Redis: {0} productos en {1} ms" -f @($products).Count, $watch.ElapsedMilliseconds)
    $during = Get-ApiMetrics; Show-CacheMetrics $during
    Assert-Evidence (($during.cache_error + $during.cache_bypass) -gt ($before.cache_error + $before.cache_bypass)) 'Aumentan error/bypass: RedisCatalogCache degrada sin romper la tienda'
    Show-Info 'Tras un error, RedisCatalogCache omite Redis 30 s (polymer.cache.backoff) para no penalizar cada petición.'
    Wait-Presenter
    Show-Step '3. Arrancar Redis y esperar a que vuelva a usarse (sin reiniciar order-api)'
    Show-Code 'docker compose up -d --wait redis'
    Wait-Healthy @('redis')
    $recovered = Wait-Until 'aciertos de Redis tras la recuperación' -TimeoutSeconds 90 -Interval 5 -Probe {
        $start = Get-ApiMetrics
        [void](Invoke-Api -Path '/api/catalog/categories'); [void](Invoke-Api -Path '/api/catalog/categories')
        $now = Get-ApiMetrics
        return ($now.cache_hit -gt $start.cache_hit)
    }
    Assert-Evidence $recovered 'El catálogo vuelve a servirse desde Redis sin intervención manual'
}

function Show-OrderTrail([string]$OrderId) {
    Show-Code "SELECT ... FROM orders / order_status_history WHERE order_id = '$OrderId'"
    Write-Host (Invoke-Sql "SELECT order_number, channel, amount, currency, risk_score, status AS decision, fulfillment_status, created_at FROM orders WHERE id = '$OrderId';")
    Write-Host (Invoke-Sql "SELECT id AS history_id, from_status, to_status, actor, changed_at FROM order_status_history WHERE order_id = '$OrderId' ORDER BY id;")
}

function Invoke-RabbitMq {
    Show-Title 'Escenario D — RabbitMQ: pedido → outbox → exchange → cola → consumidor → Mailpit'
    Assert-Running @('rabbitmq', 'mailpit', 'order-api', 'db')
    Show-Code 'Caso de uso: application/service/CommerceService.placeOrder → escribe order_status_history (misma transacción)'
    Show-Code 'Relay: adapter/in/scheduling/EventRelayScheduler → application/service/EventRelayService.relayPending (cada 2 s)'
    Show-Code 'Publicador: adapter/out/messaging/RabbitMqEventPublisher (persistente + publisher confirms + mandatory)'
    Show-Code 'Consumidor: adapter/in/messaging/BusinessEventListener → application/service/NotificationService (idempotente)'
    Show-Step '1. Un cliente ficticio registra un pedido'
    $customer = New-DemoCustomer
    $order = (New-DemoOrder $customer (Get-Product 20 450)).Data
    Assert-Evidence ($order.id) "Pedido $($order.id) creado (estado $($order.status), riesgo $($order.riskScore))"
    Wait-Presenter
    Show-Step '2. PostgreSQL guarda el pedido y su historial (el historial ES el outbox, ADR 0012)'
    Show-OrderTrail $order.id
    $historyId = Invoke-SqlValue "SELECT min(id) FROM order_status_history WHERE order_id = '$($order.id)';"
    $eventId = "order-status-$historyId"
    Show-Info "Evento correspondiente: $eventId"
    Wait-Presenter
    Show-Step '3. El relay publica el evento en RabbitMQ y, con el ack del broker, lo marca en outbox_published'
    $published = Wait-Until 'publicación del evento' -TimeoutSeconds 60 -Probe { (Invoke-SqlValue "SELECT count(*) FROM outbox_published WHERE event_id = '$eventId';") -eq '1' }
    Assert-Evidence $published "outbox_published contiene $eventId"
    Write-Host (Invoke-Sql "SELECT event_id, published_at FROM outbox_published WHERE event_id = '$eventId';")
    Show-Step '4. El consumidor procesa el evento una sola vez (processed_events) y envía el correo'
    $processed = Wait-Until 'procesamiento del evento' -TimeoutSeconds 60 -Probe { [int](Invoke-SqlValue "SELECT count(*) FROM processed_events WHERE event_id = '$eventId';") -ge 1 }
    Assert-Evidence $processed "processed_events contiene $eventId"
    Write-Host (Invoke-Sql "SELECT event_id, consumer, processed_at FROM processed_events WHERE event_id = '$eventId';")
    $mails = @(Wait-Until 'correo en Mailpit' -TimeoutSeconds 60 -Probe { $m = Get-MailpitMessages $customer.Email; if ($m.Count -ge 1) { $m } else { $null } })
    foreach ($m in $mails) { Show-Info "Mailpit: «$($m.Subject)» → $($customer.Email)" }
    Assert-Evidence ($mails.Count -ge 1) "Correo de notificación en Mailpit ($MailpitUrl)"
    Show-Step '5. Topología durable en RabbitMQ (API de gestión)'
    $auth = Get-RabbitAuth
    if ($auth) {
        $queue = Invoke-RestMethod -UseBasicParsing -Uri "$RabbitUrl/api/queues/%2F/hexaorders.notifications" -Headers $auth -TimeoutSec 15
        $dlq = Invoke-RestMethod -UseBasicParsing -Uri "$RabbitUrl/api/queues/%2F/hexaorders.notifications.dlq" -Headers $auth -TimeoutSec 15
        $exchange = Invoke-RestMethod -UseBasicParsing -Uri "$RabbitUrl/api/exchanges/%2F/hexaorders.events" -Headers $auth -TimeoutSec 15
        Show-Info ("Cola hexaorders.notifications: durable={0}, consumidores={1}, listos={2}, DLX={3}" -f $queue.durable, $queue.consumers, $queue.messages_ready, $queue.arguments.'x-dead-letter-exchange')
        Show-Info ("DLQ hexaorders.notifications.dlq: durable={0}, mensajes={1}" -f $dlq.durable, $dlq.messages)
        Assert-Evidence ($queue.durable -and $queue.consumers -ge 1 -and $exchange.durable -and $exchange.type -eq 'topic') 'Exchange topic durable, cola durable con consumidor y DLQ configurada'
    } else { Show-Info "Sin secrets\rabbitmq_password.txt junto a ${ComposeFile}: se omite la API de gestión." }
}

function Invoke-RabbitMqCaida {
    Show-Title 'Escenario H2 — RabbitMQ detenido: el pedido se guarda y el evento espera en el outbox'
    Assert-Running @('rabbitmq', 'mailpit', 'order-api', 'db')
    $customer = New-DemoCustomer
    $product = Get-Product 20 450
    Show-Step '1. Detener RabbitMQ'
    Show-Code 'docker compose stop rabbitmq'
    [void](Invoke-Dc @('stop', 'rabbitmq'))
    Wait-Presenter
    Show-Step '2. Registrar un pedido: PostgreSQL lo guarda aunque el broker no esté'
    $order = (New-DemoOrder $customer $product).Data
    Assert-Evidence ($order.id) "Pedido $($order.id) creado con RabbitMQ detenido"
    $historyId = Invoke-SqlValue "SELECT min(id) FROM order_status_history WHERE order_id = '$($order.id)';"
    $eventId = "order-status-$historyId"
    Show-OrderTrail $order.id
    Start-Sleep -Seconds 6
    Assert-Evidence ((Invoke-SqlValue "SELECT count(*) FROM outbox_published WHERE event_id = '$eventId';") -eq '0') "El evento $eventId sigue PENDIENTE (no está en outbox_published)"
    $pending = Wait-Until 'polymer_outbox_pending > 0' -TimeoutSeconds 40 -Interval 5 -Probe {
        $v = (Get-ApiMetrics).outbox_pending; if ($v -and $v -ne 'NaN' -and [double]$v -gt 0) { $v } else { $null } }
    Assert-Evidence ([double]$pending -gt 0) "polymer_outbox_pending = $pending"
    Wait-Presenter
    Show-Step '3. Arrancar RabbitMQ: el relay reintenta y publica los pendientes'
    Show-Code 'docker compose up -d --wait rabbitmq'
    Wait-Healthy @('rabbitmq')
    $published = Wait-Until 'publicación tras la recuperación' -TimeoutSeconds 150 -Interval 5 -Probe { (Invoke-SqlValue "SELECT count(*) FROM outbox_published WHERE event_id = '$eventId';") -eq '1' }
    Assert-Evidence $published "El evento $eventId se publicó al volver RabbitMQ"
    $mails = @(Wait-Until 'correo del pedido retenido' -TimeoutSeconds 120 -Interval 5 -Probe { $m = Get-MailpitMessages $customer.Email; if ($m.Count -ge 1) { $m } else { $null } })
    Assert-Evidence ($mails.Count -ge 1) "El cliente recibió el correo del pedido creado durante la caída ($($mails.Count))"
    Assert-Evidence ((Invoke-SqlValue "SELECT count(*) FROM orders WHERE id = '$($order.id)';") -eq '1') 'El pedido no se perdió'
    Show-Info 'Garantía real: entrega AL MENOS UNA VEZ (un evento puede reenviarse; processed_events descarta duplicados). No es «exactamente una vez».'
}

function Invoke-Postgres {
    Show-Title 'Escenario E — PostgreSQL: pedido, consultas SQL y persistencia tras reiniciar'
    Assert-Running @('db', 'order-api')
    Show-Step '1. Crear un pedido desde la tienda (cliente ficticio)'
    $customer = New-DemoCustomer
    $order = (New-DemoOrder $customer (Get-Product 20 450)).Data
    Assert-Evidence ($order.id) "Pedido $($order.id) (n.º $($order.orderNumber))"
    Wait-Presenter
    Show-Step '2. Consultas de SOLO LECTURA'
    $queries = @(
        "SELECT o.order_number, o.amount, o.currency, o.risk_score, o.status AS decision, o.fulfillment_status, u.username FROM orders o JOIN app_users u ON u.id = o.user_id WHERE o.id = '$($order.id)';",
        "SELECT line_no, product_id, product_name, unit_price, quantity, line_total FROM order_lines WHERE order_id = '$($order.id)' ORDER BY line_no;",
        "SELECT from_status, to_status, actor, note, changed_at FROM order_status_history WHERE order_id = '$($order.id)' ORDER BY id;",
        "SELECT recipient, address, city FROM order_shipping WHERE order_id = '$($order.id)';")
    foreach ($q in $queries) { Show-Code $q; Write-Host (Invoke-Sql $q) }
    $snapshot = Invoke-SqlValue "SELECT o.order_number || '|' || o.amount || '|' || o.fulfillment_status || '|' || (SELECT count(*) FROM order_lines l WHERE l.order_id = o.id) || '|' || (SELECT count(*) FROM order_status_history h WHERE h.order_id = o.id) FROM orders o WHERE o.id = '$($order.id)';"
    Show-Info "Huella del pedido antes del reinicio: $snapshot"
    Wait-Presenter
    Show-Step '3. Reiniciar PostgreSQL y order-api (los volúmenes se conservan)'
    Show-Code 'docker compose restart db order-api'
    [void](Invoke-Dc @('restart', 'db', 'order-api'))
    Wait-Healthy @('db', 'order-api', 'frontend')
    Show-Step '4. Volver a consultar: los datos siguen ahí'
    $after = Invoke-SqlValue "SELECT o.order_number || '|' || o.amount || '|' || o.fulfillment_status || '|' || (SELECT count(*) FROM order_lines l WHERE l.order_id = o.id) || '|' || (SELECT count(*) FROM order_status_history h WHERE h.order_id = o.id) FROM orders o WHERE o.id = '$($order.id)';"
    Show-Info "Huella después del reinicio: $after"
    Assert-Evidence ($after -eq $snapshot -and $after) 'El pedido, sus líneas y su historial son idénticos tras el reinicio'
    Connect-DemoCustomer $customer
    $again = (Invoke-Api -Session $customer -Path "/api/shop/orders/$($order.id)").Data
    Assert-Evidence ($again.order.id -eq $order.id -or $again.id -eq $order.id) 'La tienda vuelve a mostrar el pedido al cliente tras iniciar sesión de nuevo'
    $volume = (Invoke-Dc @('volumes', '--format', '{{.Name}}') -AllowFailure)
    if ($volume) { Show-Info "Volúmenes del proyecto: $(($volume -split "`n" | Where-Object { $_ -match 'postgres' }) -join ', ')" }
}

function Invoke-Riesgo {
    Show-Title 'Escenario F — Microservicios: order-api (Java) consulta a risk-engine (Python)'
    Assert-Running @('order-api', 'risk-engine')
    $adapter = Get-OrderApiEnv 'POLYMER_RISK_ADAPTER'
    if ($adapter -eq 'fixed') { throw 'El modo demostración usa el adaptador fijo: ejecuta .\polymer.cmd demo-off antes de este escenario.' }
    Show-Code 'Contrato: POST http://risk-engine:8000/score {customerId, amount} → {score, decision}'
    Show-Code 'Java: adapter/out/http/HttpRiskAssessmentAdapter (timeout 2 s conexión / 3 s lectura, sin reintentos)'
    Show-Code 'Python: services/risk-engine/risk_engine/domain/risk.py → assess(): 75 si importe ≥ 1000, 40 si ≥ 500, 10 si menos'
    Show-Code 'Decisión: la toma el dominio Java (OrderStatus.fromRiskScore: REVIEW si ≥ 70)'
    $customer = New-DemoCustomer
    Show-Step '1. Pedido de importe bajo (< S/ 500) → puntaje 10 → aprobado'
    $low = New-DemoOrder $customer (Get-Product 20 450)
    $trace = $low.Headers['X-Trace-Id']; if ($trace -is [array]) { $trace = $trace[0] }
    Show-Info "Respuesta: riesgo=$($low.Data.riskScore), decisión=$($low.Data.riskDecision), estado=$($low.Data.status), X-Trace-Id=$trace"
    Assert-Evidence ($low.Data.riskScore -eq 10 -and $low.Data.riskDecision -eq 'APPROVED') 'risk-engine devolvió 10 y el dominio Java aprobó'
    Show-Step '2. Pedido de importe alto (≥ S/ 1000) → puntaje 75 → revisión'
    $high = New-DemoOrder $customer (Get-Product 1000 4000)
    Show-Info "Respuesta: riesgo=$($high.Data.riskScore), decisión=$($high.Data.riskDecision), estado=$($high.Data.status)"
    Assert-Evidence ($high.Data.riskScore -eq 75 -and $high.Data.riskDecision -eq 'REVIEW') 'risk-engine devolvió 75 y el pedido quedó EN REVISIÓN (visible en Operaciones → Revisión de riesgo)'
    Show-Step '3. Evidencia en los logs de risk-engine (y traza completa en Grafana → Explore → Tempo)'
    $logs = Invoke-Dc @('logs', '--no-color', '--since', '5m', 'risk-engine') -AllowFailure
    $scoreLines = @($logs -split "`n" | Where-Object { $_ -match '/score' } | Select-Object -Last 3)
    foreach ($l in $scoreLines) { Show-Code $l.Trim() }
    Assert-Evidence ($scoreLines.Count -ge 1) 'risk-engine registró las llamadas POST /score de order-api'
    if ($trace) { Show-Info "Busca la traza $trace en Grafana (Explore → Tempo): incluye CommerceUseCases.placeOrder, risk-assessment-port y ScoreRisk.execute." }
}

function Invoke-RiesgoCaida {
    Show-Title 'Escenario H3 — risk-engine detenido: order-api rechaza el pedido (503) sin inventar un puntaje'
    Assert-Running @('order-api', 'risk-engine')
    if ((Get-OrderApiEnv 'POLYMER_RISK_ADAPTER') -eq 'fixed') { throw 'Desactiva el modo demostración (.\polymer.cmd demo-off) para este escenario.' }
    $customer = New-DemoCustomer
    $product = Get-Product 20 450
    $before = Invoke-SqlValue "SELECT count(*) FROM orders o JOIN app_users u ON u.id = o.user_id WHERE u.username = '$($customer.Username)';"
    Show-Step '1. Detener risk-engine (dependencia CRÍTICA para crear pedidos)'
    Show-Code 'docker compose stop risk-engine'
    [void](Invoke-Dc @('stop', 'risk-engine'))
    Wait-Presenter
    Show-Step '2. Intentar comprar: la API responde 503 y no se guarda nada'
    $response = New-DemoOrder $customer $product -Expect @(503)
    Assert-Evidence ($response.Status -eq 503) 'POST /api/shop/orders → 503 Service Unavailable (RiskUnavailableException)'
    $after = Invoke-SqlValue "SELECT count(*) FROM orders o JOIN app_users u ON u.id = o.user_id WHERE u.username = '$($customer.Username)';"
    Assert-Evidence ($after -eq $before) 'Ningún pedido sin evaluación de riesgo en PostgreSQL'
    Show-Info 'Es un fallo explícito (fail-closed): no hay circuit breaker ni puntaje por defecto que apruebe pedidos sin evaluar.'
    Wait-Presenter
    Show-Step '3. Arrancar risk-engine: los pedidos vuelven a funcionar'
    Show-Code 'docker compose up -d --wait risk-engine'
    Wait-Healthy @('risk-engine')
    $ok = New-DemoOrder $customer $product
    Assert-Evidence ($ok.Status -eq 201) 'Tras la recuperación el pedido se crea (201)'
}

function Invoke-Adaptador {
    Show-Title 'Escenario B2 — Inversión de dependencias: otro adaptador para el MISMO puerto'
    Assert-Running @('order-api', 'risk-engine')
    $adapter = Get-OrderApiEnv 'POLYMER_RISK_ADAPTER'
    if ($adapter -ne 'fixed') { throw 'Activa antes el adaptador fijo: .\polymer.cmd demo-on -FixedRisk' }
    $score = Get-OrderApiEnv 'POLYMER_RISK_FIXED_SCORE'
    Show-Code 'config/HexagonalConfiguration: @ConditionalOnProperty(polymer.risk.adapter=fixed) → FixedRiskAssessmentAdapter'
    Show-Code 'Sin cambios en: CommerceService, OrderService, RiskAssessmentPort ni el dominio'
    $customer = New-DemoCustomer
    Show-Step '1. Con risk-engine DETENIDO, el pedido se crea igualmente: el puerto lo atiende el adaptador fijo'
    [void](Invoke-Dc @('stop', 'risk-engine'))
    try {
        $order = New-DemoOrder $customer (Get-Product 1000 4000)
        Show-Info "Respuesta: riesgo=$($order.Data.riskScore), decisión=$($order.Data.riskDecision) (con el adaptador HTTP habría sido 75 / REVIEW)"
        Assert-Evidence ($order.Status -eq 201 -and "$($order.Data.riskScore)" -eq "$score") "Pedido creado con el puntaje fijo $score sin consultar risk-engine"
        $log = Invoke-Dc @('logs', '--no-color', 'order-api') -AllowFailure
        Assert-Evidence ($log -match 'RiskAssessmentPort usa FixedRiskAssessmentAdapter') 'El log de order-api confirma qué adaptador conectó la raíz de composición'
    } finally {
        Show-Step '2. Restaurar risk-engine'
        Wait-Healthy @('risk-engine')
    }
    Show-Info 'Para volver al adaptador real: .\polymer.cmd demo-off'
}

function Invoke-Observabilidad {
    Show-Title 'Escenario G — Métricas reales en Prometheus y Grafana'
    Assert-Running @('prometheus', 'grafana', 'order-api')
    $queries = [ordered]@{
        'Pedidos de la tienda por decisión'   = 'sum by (risk_decision) (polymer_shop_orders_placed_total)'
        'Pedidos del laboratorio por estado'  = 'sum by (status) (orders_created_total)'
        'Peticiones HTTP por estado'          = 'sum by (status) (http_server_requests_seconds_count{application="order-api"})'
        'Latencia p95 HTTP (s, 5 min)'         = 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="order-api"}[5m])))'
        'Caché del catálogo por resultado'    = 'sum by (result) (polymer_cache_requests_total)'
        'Eventos publicados en RabbitMQ'      = 'sum by (type) (polymer_events_published_total)'
        'Eventos pendientes en el outbox'     = 'polymer_outbox_pending'
        'Correos enviados (notificaciones)'   = 'sum(polymer_notifications_sent_total)'
        'Targets UP'                          = 'sum by (job) (up)'
    }
    foreach ($name in $queries.Keys) {
        $q = $queries[$name]
        Show-Step $name
        Show-Code $q
        try {
            $result = (Invoke-RestMethod -UseBasicParsing -Uri ("$PrometheusUrl/api/v1/query?query=" + [uri]::EscapeDataString($q)) -TimeoutSec 10).data.result
            if (-not $result) { Show-Info 'sin datos todavía (sin tráfico reciente en esa serie)' }
            foreach ($r in $result) {
                $labels = ($r.metric.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', '
                Show-Info ("{0} → {1}" -f $labels, $r.value[1])
            }
        } catch { Show-Info "Prometheus no respondió: $($_.Exception.Message)" }
    }
    $targets = @((Invoke-RestMethod -UseBasicParsing -Uri "$PrometheusUrl/api/v1/targets?state=active" -TimeoutSec 10).data.activeTargets)
    foreach ($t in $targets) { Show-Info ("Target {0,-15} {1}" -f $t.labels.job, $t.health) }
    $critical = @($targets | Where-Object { @('order-api', 'risk-engine', 'prometheus') -contains $_.labels.job -and $_.health -eq 'up' })
    Assert-Evidence ($critical.Count -ge 3) "Prometheus recopila $($targets.Count) targets; order-api, risk-engine y prometheus en UP"
    Show-Info 'Grafana: http://127.0.0.1:3000 → Dashboards → carpeta POLYMER (negocio, API, integraciones, riesgo, logística, seguridad, infraestructura).'
}

function Set-DemoMode([bool]$On) {
    # Recrea SOLO order-api (--no-deps) con o sin infra/demo/compose.demo.yml. No toca datos ni volúmenes.
    $override = Join-Path $RepoRoot 'infra/demo/compose.demo.yml'
    if ($On) {
        Show-Title 'Activar el modo demostración (solo configuración de order-api)'
        $env:POLYMER_DEMO_CACHE_TTL = "${DemoTtl}s"
        $env:POLYMER_DEMO_RISK_ADAPTER = if ($FixedRisk) { 'fixed' } else { 'http' }
        $env:POLYMER_DEMO_FIXED_SCORE = "$FixedScore"
        Show-Code "docker compose -f docker-compose.yml -f infra/demo/compose.demo.yml up -d --no-deps order-api"
        [void](Invoke-Dc @('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'order-api') -ExtraFile $override)
        Assert-Evidence ((Get-OrderApiEnv 'POLYMER_CACHE_CATALOG_TTL') -eq "${DemoTtl}s") "TTL de la caché del catálogo: ${DemoTtl} s"
        Assert-Evidence ((Get-OrderApiEnv 'POLYMER_RISK_ADAPTER') -eq $env:POLYMER_DEMO_RISK_ADAPTER) "Adaptador de riesgo: $($env:POLYMER_DEMO_RISK_ADAPTER)"
        Show-Info 'Desactívalo al terminar con: .\polymer.cmd demo-off'
    } else {
        Show-Title 'Desactivar el modo demostración (configuración normal de order-api)'
        Show-Code 'docker compose up -d --no-deps --force-recreate order-api'
        [void](Invoke-Dc @('up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '180', 'order-api'))
        Assert-Evidence (-not (Get-OrderApiEnv 'POLYMER_RISK_ADAPTER') -and -not (Get-OrderApiEnv 'POLYMER_CACHE_CATALOG_TTL')) 'order-api vuelve a la configuración de docker-compose.yml (TTL 60 s, adaptador HTTP)'
    }
}

# ------------------------------------------------------------------ main

$started = Get-Date
# POLYMER_DEMO_TRANSCRIPT: guarda toda la salida (el CI la muestra si un escenario falla).
if ($env:POLYMER_DEMO_TRANSCRIPT) { Start-Transcript -Path $env:POLYMER_DEMO_TRANSCRIPT -Append | Out-Null }
try {
switch ($Scenario) {
    'preparar'       { Invoke-Preparar }
    'arquitectura'   { Invoke-Arquitectura }
    'redis'          { Invoke-Redis }
    'redis-caida'    { Invoke-RedisCaida }
    'rabbitmq'       { Invoke-RabbitMq }
    'rabbitmq-caida' { Invoke-RabbitMqCaida }
    'postgres'       { Invoke-Postgres }
    'riesgo'         { Invoke-Riesgo }
    'riesgo-caida'   { Invoke-RiesgoCaida }
    'adaptador'      { Invoke-Adaptador }
    'observabilidad' { Invoke-Observabilidad }
    'activar-demo'   { Set-DemoMode $true }
    'desactivar-demo' { Set-DemoMode $false }
    'todo' {
        Invoke-Preparar; Invoke-Arquitectura; Invoke-Redis; Invoke-RabbitMq; Invoke-Postgres; Invoke-Riesgo
        Invoke-Observabilidad; Invoke-RedisCaida; Invoke-RabbitMqCaida; Invoke-RiesgoCaida
    }
}
} catch {
    Write-Host ''
    Write-Host "ERROR en el escenario «$Scenario»: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray
    throw
} finally {
    if ($env:POLYMER_DEMO_TRANSCRIPT) { Stop-Transcript | Out-Null }
}
Write-Host ''
Write-Host ("OK demostración «{0}»: {1} evidencias comprobadas en {2:N0} s" -f $Scenario, $script:Evidence, ((Get-Date) - $started).TotalSeconds) -ForegroundColor Green
