@echo off
rem First-timer setup for Windows: installs prerequisites via winget
rem (Docker Desktop, Java 21, Node LTS, Maven), starts the database and
rem builds the server and web client. Idempotent — re-running only fills gaps.
setlocal EnableDelayedExpansion

cd /d "%~dp0"
set "NEED_RERUN=0"

if /i "%~1"=="check" (
    call :check
    exit /b !ERRORLEVEL!
)
call :check
echo (missing pieces will be installed below)
echo.

where winget >nul 2>&1
if errorlevel 1 (
    echo ERROR: winget not found. Install "App Installer" from the Microsoft Store and re-run.
    exit /b 1
)

call :ensure Docker.DockerDesktop docker
call :ensure EclipseAdoptium.Temurin.21.JDK java
call :ensure OpenJS.NodeJS.LTS node
call :ensure OpenJS.NodeJS.LTS npm
call :ensure Apache.Maven mvn

if "%NEED_RERUN%"=="1" (
    echo.
    echo A tool was just installed. Close this window, open a NEW terminal, and run install.bat again.
    exit /b 0
)

for /f "tokens=1" %%v in ('node --version') do set "NODEV=%%v"
set "NODEV=%NODEV:v=%"
for /f "delims=. tokens=1" %%m in ("%NODEV%") do set "NODEMAJOR=%%m"
if %NODEMAJOR% LSS 20 (
    echo ERROR: node %NODEV% is too old, need 20+. Upgrade Node LTS and re-run.
    exit /b 1
)

echo ==^> starting Docker Desktop if needed
docker info >nul 2>&1
if errorlevel 1 (
    echo     launching Docker Desktop, waiting up to 2 minutes ...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    for /l %%i in (1,1,24) do (
        timeout /t 5 /nobreak >nul
        docker info >nul 2>&1
        if not errorlevel 1 goto :docker_up
    )
    echo ERROR: Docker Desktop did not start. Open it manually and re-run.
    exit /b 1
)
:docker_up

echo ==^> database
cd infra
docker compose up -d db
if errorlevel 1 exit /b 1
cd ..

echo ==^> newsroom weights
echo     The sidecar downloads its model on first boot automatically.
echo     (With Git Bash you can pre-fetch: ./infra/narrator/fetch-model.sh)

echo ==^> server build
cd server
call mvn -q install -DskipTests
if errorlevel 1 exit /b 1
cd ..

echo ==^> web client
cd web
call npm install
if errorlevel 1 exit /b 1
cd ..

echo.
echo Done. To play:
echo   1. Server:  cd server ^&^& set VI_ADMIN_PASSWORD=choose-a-password ^&^& mvn -pl edge spring-boot:run
echo   2. Web:     cd web ^&^& npm run dev        ^(open http://localhost:5173/^)
echo   3. Newsroom ^(optional^): cd infra ^&^& docker compose --profile narrator up -d
echo      then start the edge with VI_NARRATOR=llama
echo Approve players at http://localhost:5173/admin.html with your admin password.
exit /b 0

rem -- install a winget package if its probe command is missing --
:ensure
where %2 >nul 2>&1
if not errorlevel 1 exit /b 0
echo     installing %1 ...
winget install -e --id %1 --accept-package-agreements --accept-source-agreements
set "NEED_RERUN=1"
exit /b 0

rem -- sanity check: report only, install nothing --
:check
set "CHECK_FAIL=0"
echo ==^> sanity check
where docker >nul 2>&1
if errorlevel 1 (call :miss "docker binary") else (for /f "tokens=3" %%v in ('docker --version 2^>nul') do call :hit "docker %%v")
docker compose version >nul 2>&1
if errorlevel 1 (call :miss "docker compose plugin") else (call :hit "docker compose plugin")
docker info >nul 2>&1
if errorlevel 1 (call :miss "docker daemon reachable") else (call :hit "docker daemon reachable")
where java >nul 2>&1
if errorlevel 1 (call :miss "java") else (for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do call :hit "java %%v")
where mvn >nul 2>&1
if errorlevel 1 (call :miss "maven") else (call :hit "maven present")
where node >nul 2>&1
if errorlevel 1 (call :miss "node") else (for /f %%v in ('node --version 2^>nul') do call :hit "node %%v")
where npm >nul 2>&1
if errorlevel 1 (call :miss "npm") else (call :hit "npm present")
for %%p in (5432 8080 5173 18080) do (
    netstat -an ^| findstr /r /c:":%%p .*LISTENING" >nul 2>&1
    if errorlevel 1 (call :hit "port %%p free") else (call :miss "port %%p free")
)
echo   ---- check done, %CHECK_FAIL% problem^(s^) ----
exit /b %CHECK_FAIL%

:hit
echo   [ok] %~1
exit /b 0

:miss
echo   [MISSING] %~1
set /a CHECK_FAIL+=1
exit /b 0
