$ErrorActionPreference = 'Stop'
# Genera los secretos como archivos en .\secrets\ (excluido de Git), junto a docker-compose.yml.
# Compose los monta como archivos de solo lectura en /run/secrets de cada contenedor.
$root = Split-Path $PSScriptRoot -Parent
$secretsDir = Join-Path $root 'secrets'
$legacyEnv = Join-Path $root '.env'
New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

# Migración: si existe un .env de una versión anterior, conservar sus valores, porque
# PostgreSQL y Grafana guardan la contraseña inicial en sus volúmenes.
$legacy = @{}
if (Test-Path -LiteralPath $legacyEnv) {
    foreach ($line in Get-Content -LiteralPath $legacyEnv) {
        if ($line -match '^(DB_PASSWORD|GRAFANA_PASSWORD)=(.+)$') { $legacy[$Matches[1]] = $Matches[2] }
    }
}

function New-RandomSecret {
    $bytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [System.BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

foreach ($item in @(@{ File = 'db_password.txt'; Legacy = 'DB_PASSWORD' },
                    @{ File = 'grafana_password.txt'; Legacy = 'GRAFANA_PASSWORD' })) {
    $path = Join-Path $secretsDir $item.File
    if (Test-Path -LiteralPath $path) { continue }
    $value = if ($legacy.ContainsKey($item.Legacy)) { $legacy[$item.Legacy] } else { New-RandomSecret }
    [System.IO.File]::WriteAllText($path, $value, [System.Text.UTF8Encoding]::new($false))
}
foreach ($file in 'db_password.txt', 'grafana_password.txt') {
    $content = (Get-Content -LiteralPath (Join-Path $secretsDir $file) -Raw)
    if (-not $content -or -not $content.Trim()) { throw "Secreto vacío: secrets\$file" }
}
Write-Host 'Secretos listos en .\secrets\ (excluido de Git; no compartas su contenido).'
Write-Host 'Contraseña de Grafana (usuario admin): secrets\grafana_password.txt'
