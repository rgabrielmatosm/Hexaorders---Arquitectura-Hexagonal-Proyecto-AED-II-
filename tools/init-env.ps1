<#
.SYNOPSIS
    Genera los secretos de HexaOrders como archivos en <Root>\secrets\ (excluido de Git).

.DESCRIPTION
    Compose monta cada archivo de secrets\ como secreto de solo lectura en /run/secrets:
      db_password.txt       contraseña de PostgreSQL
      grafana_password.txt  contraseña del usuario admin de Grafana
      demo_password.txt     contraseña común de las cuentas de demostración de HexaOrders
                            (admin, operador, logistica, logistica2, observador, cliente)

    Un archivo existente NUNCA se sobrescribe: PostgreSQL y Grafana guardan la contraseña
    inicial en sus volúmenes y cambiarla rompería un entorno ya inicializado.

    Problema conocido de Windows: si se arranca Docker antes de que exista el archivo, Docker
    Desktop crea un DIRECTORIO con ese nombre. Este script lo detecta (-PathType Container):
    un directorio vacío se reemplaza por el archivo; uno con contenido detiene el script con
    instrucciones, sin borrar nada.

    Origen de cada valor nuevo, en este orden:
      1. Un .env de una versión anterior (DB_PASSWORD, GRAFANA_PASSWORD; migración).
      2. Variables de entorno DB_PASSWORD / GRAFANA_PASSWORD / DEMO_PASSWORD (uso en CI).
      3. Valor aleatorio criptográfico de 48 caracteres hexadecimales.

    Compatible con Windows PowerShell 5.1 y PowerShell 7 (Windows, Linux, macOS).

.EXAMPLE
    .\tools\init-env.ps1
.EXAMPLE
    .\tools\init-env.ps1 -Root C:\despliegue\hexaorders
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
    @{ File = 'demo_password.txt'; Name = 'DEMO_PASSWORD' }
)

foreach ($item in $secrets) {
    $path = Join-Path $secretsDir $item.File

    # Directorio creado por Docker Desktop en lugar del archivo.
    if (Test-Path -LiteralPath $path -PathType Container) {
        $content = @(Get-ChildItem -LiteralPath $path -Force)
        if ($content.Count -gt 0) {
            throw ("'$path' es un DIRECTORIO con contenido, no un archivo de secreto. No se ha borrado nada. " +
                   "Detén los contenedores (.\hexaorders.cmd stop), revisa su contenido, muévelo fuera de secrets\ y vuelve a ejecutar este script.")
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

if (-not $onWindows) {
    # Linux/macOS: carpeta privada (700); archivos legibles (644) porque los contenedores
    # leen el secreto montado con usuarios sin privilegios.
    & chmod 700 $secretsDir
    $files = @($secrets | ForEach-Object { Join-Path $secretsDir $_.File })
    & chmod 644 @files
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron ajustar los permisos de secrets/' }
}

Write-Host "Secretos listos en $secretsDir (excluido de Git; no compartas su contenido)."
Write-Host "Contraseña de Grafana (usuario admin): $(Join-Path $secretsDir 'grafana_password.txt')"
Write-Host "Contraseña de las cuentas de demostración de HexaOrders: $(Join-Path $secretsDir 'demo_password.txt')"
