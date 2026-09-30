@echo off
REM Wrapper cho bootstrap_platforms.ps1.
REM Dung duoc tu cmd.exe, Windows PowerShell 5.1, PowerShell 7, hoac bam dup trong Explorer
REM (khong can quan tam ExecutionPolicy). Vi du:  tool\bootstrap_platforms.cmd -RunChecks
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bootstrap_platforms.ps1" %*
exit /b %ERRORLEVEL%
