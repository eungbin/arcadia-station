@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0show-case-generation-logs.ps1" %*
exit /b %ERRORLEVEL%
