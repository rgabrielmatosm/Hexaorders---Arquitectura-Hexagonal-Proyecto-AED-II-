<#
.SYNOPSIS
    Genera los secretos de POLYMER como archivos en <Root>\secrets\ (excluido de Git).

.DESCRIPTION
    Compose monta cada archivo de secrets\ como secreto de solo lectura en /run/secrets:
      db_password.txt       contraseña de PostgreSQL
      grafana_password.txt  contraseña del usuario admin de Grafana
      demo_password.txt     contraseña común de las cuentas de demostración de POLYMER
                            (admin, operador, logistica, logistica2, observador, cliente)
      rabbitmq_password.txt v5: contraseña del usuario polymer de RabbitMQ (perfil "full")

    Un archivo existente NUNCA se sobrescribe: PostgreSQL, Grafana y RabbitMQ guardan la
    contraseña inicial en sus volúmenes y cambiarla rompería un entorno ya inicializado.

    Problema conocido de Windows: si se arranca Docker antes de que exista el archivo, Docker
    Desktop crea un DIRECTORIO con ese nombre. Este script lo detecta (-PathType Container):
    un directorio vacío se reemplaza por el archivo; uno con contenido detiene el script con
    instrucciones, sin borrar nada.

    Origen de cada valor nuevo, en este orden:
      1. Un .env de una versión anterior (DB_PASSWORD, GRAFANA_PASSWORD; migración).
      2. Variables de entorno DB_PASSWORD / GRAFANA_PASSWORD / DEMO_PASSWORD / RABBITMQ_PASSWORD (CI).
      3. Valor aleatorio criptográfico de 48 caracteres hexadecimales.

    Compatible con Windows PowerShell 5.1 y PowerShell 7 (Windows, Linux, macOS).

.EXAMPLE
    .\tools\init-env.ps1
.EXAMPLE
    .\tools\init-env.ps1 -Root C:\despliegue\polymer
#>
[CmdletBinding()]
param(
    # Carpeta que contiene (o contendrá) docker-compose.yml. Por defecto, la raíz del repositorio.
    [string]$Root = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'

$onWindows = ($PSVersionTable.PSEdition -eq 'Desktop') -or ((Test-Path variable:IsWindows) -and $IsWindows)
if (-not [System.IO.Path]::IsPathRooted($Root)) { $Root = Join-Path (Get-Location).Path $Root }
$secretsDir = Join-Path $Root 'secrets'
$legacyEnv = Join-Path $Root '.env'

if (Test-Path -LiteralPath $secretsDir -PathType Leaf) {
    throw "Se esperaba una carpeta en '$secretsDir' pero existe un ARCHIVO con ese nombre. Renómbralo y vuelve a ejecutar el script."
}
New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

# Migración: conservar los valores de un .env anterior.
$legacy = @{}
if (Test-Path -LiteralPath $legacyEnv -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $legacyEnv) {
        if ($line -match '^(DB_PASSWORD|GRAFANA_PASSWORD)=(.+)$') { $legacy[$Matches[1]] = $Matches[2].Trim() }
    }
}

function New-RandomSecret {
    $bytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$secrets = @(
    @{ File = 'db_password.txt'; Name = 'DB_PASSWORD' },
    @{ File = 'grafana_password.txt'; Name = 'GRAFANA_PASSWORD' },
    @{ File = 'demo_password.txt'; Name = 'DEMO_PASSWORD' },
    @{ File = 'rabbitmq_password.txt'; Name = 'RABBITMQ_PASSWORD' }
)

foreach ($item in $secrets) {
    $path = Join-Path $secretsDir $item.File

    # Directorio creado por Docker Desktop en lugar del archivo.
    if (Test-Path -LiteralPath $path -PathType Container) {
        $content = @(Get-ChildItem -LiteralPath $path -Force)
        if ($content.Count -gt 0) {
            throw ("'$path' es un DIRECTORIO con contenido, no un archivo de secreto. No se ha borrado nada. " +
                   "Detén los contenedores (.\polymer.cmd stop), revisa su contenido, muévelo fuera de secrets\ y vuelve a ejecutar este script.")
        }
        Write-Warning "'$path' era un directorio vacío (Docker Desktop lo crea si el archivo no existía al arrancar). Se reemplaza por el archivo de secreto."
        Remove-Item -LiteralPath $path
    }

    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $existing = [System.IO.File]::ReadAllText($path)
        if (-not $existing.Trim()) {
            throw "'$path' existe pero está vacío. No se sobrescribe automáticamente: escribe una contraseña en él o bórralo si el entorno es nuevo."
        }
        continue  # nunca se sobrescribe una contraseña existente
    }

    $fromEnv = [Environment]::GetEnvironmentVariable($item.Name)
    if ($legacy.ContainsKey($item.Name)) { $value = $legacy[$item.Name] }
    elseif ($fromEnv) { $value = $fromEnv.Trim() }
    else { $value = New-RandomSecret }
    # Sin salto de línea final ni BOM: los contenedores leen el archivo tal cual.
    [System.IO.File]::WriteAllText($path, $value, $utf8NoBom)
}

# v7: integraciones opcionales. Se crean VACÍAS (vacío = desactivada) para que Docker Compose pueda montarlas;
# nunca se sobrescriben. Google: secrets\google-oauth.json (v8: el JSON descargado de Google Cloud) o
# google_client_id.txt y google_client_secret.txt (v7); Gmail/SMTP: smtp_password.txt.
$optional = @('google-oauth.json', 'google_client_id.txt', 'google_client_secret.txt', 'smtp_password.txt')
foreach ($file in $optional) {
    $path = Join-Path $secretsDir $file
    if (Test-Path -LiteralPath $path -PathType Container) {
        if (@(Get-ChildItem -LiteralPath $path -Force).Count -gt 0) { throw "'$path' es un DIRECTORIO con contenido. No se ha borrado nada." }
        Remove-Item -LiteralPath $path
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { [System.IO.File]::WriteAllText($path, '', $utf8NoBom) }
}

if (-not $onWindows) {
    # Linux/macOS: carpeta privada (700); archivos legibles (644) porque los contenedores
    # leen el secreto montado con usuarios sin privilegios.
    & chmod 700 $secretsDir
    $files = @($secrets | ForEach-Object { Join-Path $secretsDir $_.File }) + @($optional | ForEach-Object { Join-Path $secretsDir $_ })
    & chmod 644 @files
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron ajustar los permisos de secrets/' }
}

Write-Host "Secretos listos en $secretsDir (excluido de Git; no compartas su contenido)."
Write-Host "Contraseña de Grafana (usuario admin): $(Join-Path $secretsDir 'grafana_password.txt')"
Write-Host "Contraseña de las cuentas de demostración de POLYMER: $(Join-Path $secretsDir 'demo_password.txt')"
Write-Host "Contraseña de RabbitMQ (usuario hexaorders, heredado; consola http://127.0.0.1:15672): $(Join-Path $secretsDir 'rabbitmq_password.txt')"
