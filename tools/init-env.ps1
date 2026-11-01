<#
.SYNOPSIS
    Genera los secretos de HexaOrders como archivos en <Root>\secrets\ (excluido de Git).

.DESCRIPTION
    Compose monta secrets\db_password.txt y secrets\grafana_password.txt como archivos de
    solo lectura en /run/secrets de cada contenedor. Si un archivo ya existe se conserva:
    PostgreSQL y Grafana guardan la contraseña inicial en sus volúmenes.

    Origen de cada valor, en este orden:
      1. Archivo existente (no se modifica).
      2. Un .env de una versión anterior del proyecto (migración).
      3. Variables de entorno DB_PASSWORD / GRAFANA_PASSWORD (uso en CI).
      4. Valor aleatorio criptográfico de 48 caracteres hexadecimales.

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
New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

# Migración: conservar los valores de un .env anterior.
$legacy = @{}
if (Test-Path -LiteralPath $legacyEnv) {
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
foreach ($item in @(@{ File = 'db_password.txt'; Name = 'DB_PASSWORD' },
                    @{ File = 'grafana_password.txt'; Name = 'GRAFANA_PASSWORD' })) {
    $path = Join-Path $secretsDir $item.File
    if (Test-Path -LiteralPath $path) { continue }
    $fromEnv = [Environment]::GetEnvironmentVariable($item.Name)
    if ($legacy.ContainsKey($item.Name)) { $value = $legacy[$item.Name] }
    elseif ($fromEnv) { $value = $fromEnv.Trim() }
    else { $value = New-RandomSecret }
    # Sin salto de línea final ni BOM: los contenedores leen el archivo tal cual.
    [System.IO.File]::WriteAllText($path, $value, $utf8NoBom)
}

foreach ($file in 'db_password.txt', 'grafana_password.txt') {
    $content = [System.IO.File]::ReadAllText((Join-Path $secretsDir $file))
    if (-not $content.Trim()) { throw "Secreto vacío: $(Join-Path $secretsDir $file)" }
}

if (-not $onWindows) {
    # Linux/macOS: carpeta privada (700); archivos legibles (644) porque los contenedores
    # leen el secreto montado con usuarios sin privilegios.
    & chmod 700 $secretsDir
    & chmod 644 (Join-Path $secretsDir 'db_password.txt') (Join-Path $secretsDir 'grafana_password.txt')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron ajustar los permisos de secrets/' }
}

Write-Host "Secretos listos en $secretsDir (excluido de Git; no compartas su contenido)."
Write-Host "Contraseña de Grafana (usuario admin): $(Join-Path $secretsDir 'grafana_password.txt')"
