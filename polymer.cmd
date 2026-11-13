@echo off
rem POLYMER - lanzador para Windows. Ejecuta tools\polymer.ps1 con PowerShell 7 si
rem esta instalado (pwsh) o con Windows PowerShell 5.1 incluido en Windows.
rem -ExecutionPolicy Bypass afecta solo a este proceso: no cambia la politica del equipo.
rem Uso: polymer.cmd start ^| status ^| logs ^| stop [-RemoveData] ^| verify ^| help
setlocal
set "POLYMER_PS=powershell"
where pwsh >nul 2>nul && set "POLYMER_PS=pwsh"
if "%~1"=="" (
    "%POLYMER_PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\polymer.ps1" help
    echo.
    echo Ejemplo: polymer.cmd start
    pause
    exit /b 0
)
"%POLYMER_PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\polymer.ps1" %*
exit /b %ERRORLEVEL%
