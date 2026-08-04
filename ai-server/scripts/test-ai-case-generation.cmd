@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-ai-case-generation.ps1" %*
exit /b %ERRORLEVEL%
