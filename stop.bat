@echo off
rem Stop the full stack started by run.bat: edge, web, sidecar, database.
rem Data survives in docker volumes.
setlocal

cd /d "%~dp0"

echo stopping edge
taskkill /FI "WINDOWTITLE eq VI-edge*" /T >nul 2>&1
if errorlevel 1 (echo   edge already down) else (echo   edge stopped)

echo stopping web
taskkill /FI "WINDOWTITLE eq VI-web*" /T >nul 2>&1
if errorlevel 1 (echo   web already down) else (echo   web stopped)

echo stopping containers
cd infra
docker compose --profile narrator stop
echo all down ^(volumes kept^)
exit /b 0
