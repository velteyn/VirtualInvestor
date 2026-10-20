@echo off
rem Start the full stack: database, newsroom sidecar, edge, web client.
rem Idempotent — re-running only starts what is down.
rem Usage: run.bat [--no-newsroom]
setlocal EnableDelayedExpansion

cd /d "%~dp0"
set "NEWSROOM=1"
if /i "%~1"=="--no-newsroom" set "NEWSROOM=0"

docker info >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker Desktop is not running. Start it and re-run.
    exit /b 1
)

echo ==^> database
cd infra
docker compose up -d db
if errorlevel 1 exit /b 1
if "%NEWSROOM%"=="1" (
    echo ==^> newsroom sidecar
    docker compose --profile narrator up -d narrator
    if errorlevel 1 exit /b 1
)
cd ..

if "%VI_ADMIN_PASSWORD%"=="" (
    set "VI_ADMIN_PASSWORD=changeme"
    echo WARNING: VI_ADMIN_PASSWORD unset, using 'changeme' ^(local dev only^)
)
if "%NEWSROOM%"=="1" (
    if "%VI_NARRATOR%"=="" set "VI_NARRATOR=llama"
)

tasklist /FI "WINDOWTITLE eq VI-edge*" 2>nul | findstr /i "java.exe" >nul
if not errorlevel 1 (
    echo ==^> edge already running
) else (
    echo ==^> edge
    start "VI-edge" /min /D "%~dp0server" cmd /c mvn -pl edge spring-boot:run
)

tasklist /FI "WINDOWTITLE eq VI-web*" 2>nul | findstr /i "node.exe" >nul
if not errorlevel 1 (
    echo ==^> web already running
) else (
    echo ==^> web
    start "VI-web" /min /D "%~dp0web" cmd /c npm run dev
)

echo waiting for the edge ...
for /l %%i in (1,1,30) do (
    timeout /t 10 /nobreak >nul
    for /f %%c in ('curl -sm 3 -o nul -w "%%{http_code}" -X POST http://localhost:8080/api/players/login -H "Content-Type: application/json" -H "X-API-Version: 1" -d "{\"u\":\"x\"}" 2^>nul') do (
        if "%%c"=="200" goto :edge_up
        if "%%c"=="400" goto :edge_up
        if "%%c"=="401" goto :edge_up
    )
)
echo WARNING: edge did not answer in time — check the VI-edge window.
:edge_up
echo     edge up

echo.
echo Stack running:
echo   play:     http://localhost:5173/
echo   sidecar:  http://127.0.0.1:18080/health  ^(when newsroom on^)
echo Stop everything with stop.bat
exit /b 0
