@echo off
rem HexaOrders - lanzador para Windows. Ejecuta tools\hexaorders.ps1 con PowerShell 7 si
rem esta instalado (pwsh) o con Windows PowerShell 5.1 incluido en Windows.
rem -ExecutionPolicy Bypass afecta solo a este proceso: no cambia la politica del equipo.
rem Uso: hexaorders.cmd start ^| status ^| logs ^| stop [-RemoveData] ^| verify ^| help
setlocal
set "HEXA_PS=powershell"
where pwsh >nul 2>nul && set "HEXA_PS=pwsh"
if "%~1"=="" (
    "%HEXA_PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\hexaorders.ps1" help
    echo.
    echo Ejemplo: hexaorders.cmd start
    pause
    exit /b 0
)
"%HEXA_PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\hexaorders.ps1" %*
exit /b %ERRORLEVEL%
