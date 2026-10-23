@echo off
setlocal EnableExtensions
title DISP-EMS Windows Server 2016 Offline Installer

net session >nul 2>&1
if not "%errorlevel%"=="0" (
  echo Requesting administrator permission...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b 0
)

set "HOST_ADDRESS=%~1"
if "%HOST_ADDRESS%"=="" set /p "HOST_ADDRESS=Enter the server IPv4 address reachable by client browsers: "
if "%HOST_ADDRESS%"=="" (
  echo A server IPv4 address is required.
  pause
  exit /b 2
)

echo.
echo Starting DISP-EMS offline deployment on %HOST_ADDRESS%...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -HostAddress "%HOST_ADDRESS%"
set "EXIT_CODE=%errorlevel%"
echo.
if not "%EXIT_CODE%"=="0" (
  echo Deployment failed with exit code %EXIT_CODE%.
) else (
  echo Deployment command completed.
)
pause
exit /b %EXIT_CODE%