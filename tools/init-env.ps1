$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    $dbBytes = New-Object byte[] 24
    $grafanaBytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($dbBytes)
        $generator.GetBytes($grafanaBytes)
    } finally { $generator.Dispose() }
    $dbValue = [System.BitConverter]::ToString($dbBytes).Replace('-', '').ToLowerInvariant()
    $grafanaValue = [System.BitConverter]::ToString($grafanaBytes).Replace('-', '').ToLowerInvariant()
    [System.IO.File]::WriteAllText($envFile, "DB_PASSWORD=$dbValue`nGRAFANA_PASSWORD=$grafanaValue`n", [System.Text.UTF8Encoding]::new($false))
}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^(DB_PASSWORD|GRAFANA_PASSWORD)=(.+)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
if (-not $env:DB_PASSWORD -or -not $env:GRAFANA_PASSWORD) { throw 'Faltan DB_PASSWORD o GRAFANA_PASSWORD en .env' }
Write-Host 'Secretos cargados. .env está excluido de Git; no compartas su contenido.'

