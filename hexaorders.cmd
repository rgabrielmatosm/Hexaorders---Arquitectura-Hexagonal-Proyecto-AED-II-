@echo off
rem Alias de compatibilidad: el lanzador del proyecto ahora es polymer.cmd (mismos comandos).
rem Se conserva para que los accesos directos y la documentacion antigua sigan funcionando.
echo Aviso: hexaorders.cmd es un alias. Usa polymer.cmd %*
call "%~dp0polymer.cmd" %*
exit /b %ERRORLEVEL%
