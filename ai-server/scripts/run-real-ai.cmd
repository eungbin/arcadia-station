@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-real-ai.ps1" %*
exit /b %ERRORLEVEL%
