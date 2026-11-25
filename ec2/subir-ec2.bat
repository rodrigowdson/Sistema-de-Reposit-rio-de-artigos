@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-ec2.ps1" %*
exit /b %ERRORLEVEL%
