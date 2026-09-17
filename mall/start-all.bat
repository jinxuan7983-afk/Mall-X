@echo off
setlocal EnableExtensions
title Mall-X Launcher
chcp 65001 >nul
cd /d "%~dp0"

rem =====================================================================
rem  Mall-X one-click launcher (Windows)
rem
rem  Usage: start-all.bat [options]
rem
rem    (no option)  middleware + backend + frontend    [default]
rem    mock         frontend only, built-in mock data, no docker and no backend
rem    no-docker    skip the middleware step (containers are already running)
rem    rebuild      force "mvn clean package -DskipTests" before starting backend
rem    dashboard    also start the RocketMQ console at http://localhost:8180
rem    help         print this help
rem
rem  Options can be combined, e.g.  start-all.bat rebuild no-docker
rem =====================================================================

set "MODE=full"
set "FRONTEND_MOCK=false"
set "WITH_DASHBOARD=0"
set "SKIP_DOCKER=0"
set "FORCE_BUILD=0"
set "NEED_BUILD=0"
set "JARS_MISSING=0"
set "MYSQL_HOST_PORT="
set "MVN_CMD="

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="mock"      (set "MODE=mock" & set "FRONTEND_MOCK=true")
if /i "%~1"=="no-docker" set "SKIP_DOCKER=1"
if /i "%~1"=="rebuild"   set "FORCE_BUILD=1"
if /i "%~1"=="dashboard" set "WITH_DASHBOARD=1"
if /i "%~1"=="full"      set "MODE=full"
if /i "%~1"=="help"      goto usage
if /i "%~1"=="-h"        goto usage
shift
goto parse_args

:args_done
if /i "%MODE%"=="mock" set "SKIP_DOCKER=1"

echo ==================================================
echo   Mall-X launcher    mode=%MODE%  frontendMock=%FRONTEND_MOCK%
echo   Gateway :10010   Mgr :10020   Alipay :10030
echo   Frontend: http://localhost:5173
echo ==================================================
echo.

rem ---------------- environment check ----------------
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] "java" not found in PATH. Install JDK 17+ first.
    goto fail
)
where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] "node" not found in PATH. Install Node.js 18+ first.
    goto fail
)

if /i "%MODE%"=="mock" (
    echo [1/4] mock mode: middleware and backend are skipped.
    goto step_frontend
)

rem ======================== [1/4] middleware ========================
if "%SKIP_DOCKER%"=="1" (
    echo [1/4] Middleware step skipped on request.
    echo       Make sure MySQL, Redis, Nacos and RocketMQ are already running.
    goto step_build
)

where docker >nul 2>nul
if errorlevel 1 (
    echo [1/4] [WARN] "docker" not found - middleware step skipped.
    echo       Start MySQL, Redis, Nacos and RocketMQ by yourself, or install Docker Desktop.
    goto step_build
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [1/4] Docker engine is not running - starting Docker Desktop ...
    if not exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
        echo       [ERROR] Docker Desktop not found in "%ProgramFiles%\Docker\Docker".
        echo               Start Docker Desktop manually, then run this script again.
        goto fail
    )
    start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
    call :wait_docker 240
    if errorlevel 1 (
        echo       [ERROR] Docker engine is still not ready after 240s.
        goto fail
    )
    echo       Docker engine is ready.
)

rem ---- decide the host port of the container MySQL ----
rem (1) a running mallx-mysql container: keep the port it is already published on.
rem     "docker port" prints TWO lines (IPv4 + IPv6):
rem         0.0.0.0:3307
rem         [::]:3307
rem     for /f collapses consecutive delimiters, so splitting the IPv6 line on ":"
rem     gives "[" , "]" , "3307" - its 2nd token is "]" and would overwrite the good
rem     value, making compose fail with "invalid hostPort: ]". Accept only the IPv4 line.
for /f "tokens=1,2 delims=:" %%A in ('docker port mallx-mysql 3306/tcp 2^>nul') do (
    if "%%A"=="0.0.0.0" set "MYSQL_HOST_PORT=%%B"
)
if defined MYSQL_HOST_PORT echo       mallx-mysql is running, keep host port %MYSQL_HOST_PORT%.
if not defined MYSQL_HOST_PORT (
    rem (2) otherwise: 3306 unless a native MySQL already occupies it
    set "MYSQL_HOST_PORT=3306"
    call :port_listening 3306
    if not errorlevel 1 (
        echo       Host port 3306 is busy - the container MySQL will use 3307.
        set "MYSQL_HOST_PORT=3307"
    )
)

set "MW_SERVICES=mysql redis nacos rocketmq-namesrv rocketmq-broker"
set "PROFILE_ARG="
if "%WITH_DASHBOARD%"=="1" set "MW_SERVICES=%MW_SERVICES% rocketmq-dashboard"
if "%WITH_DASHBOARD%"=="1" set "PROFILE_ARG=--profile dashboard"

echo [1/4] Starting middleware containers  (MySQL host port %MYSQL_HOST_PORT%) ...
pushd docker
docker compose %PROFILE_ARG% up -d %MW_SERVICES%
if errorlevel 1 (
    echo.
    echo       [WARN] "docker compose up" failed.
    echo              If the project folder has been MOVED, the old containers still bind
    echo              absolute paths of their previous location - recreating them now.
    docker compose %PROFILE_ARG% up -d --force-recreate %MW_SERVICES%
    if errorlevel 1 (
        echo       [ERROR] Middleware failed to start. Inspect the reason with:
        echo                 cd docker
        echo                 docker compose logs --tail 50
        popd
        goto fail
    )
)
popd

echo       Waiting for middleware to be ready ...
call :wait_health mallx-mysql 180
if errorlevel 1 echo       [WARN] mallx-mysql is not healthy yet - see "docker logs mallx-mysql".
call :wait_health mallx-redis 60
if errorlevel 1 echo       [WARN] mallx-redis is not healthy yet.
call :wait_health mallx-nacos 240
if errorlevel 1 echo       [WARN] mallx-nacos is not healthy yet - backend may fail to register.
call :wait_port 9876 60
if errorlevel 1 echo       [WARN] RocketMQ name server port 9876 is not listening.
call :wait_port 10911 60
if errorlevel 1 echo       [WARN] RocketMQ broker port 10911 is not listening.
echo       Middleware is ready.

rem ======================== [2/4] backend jars ========================
:step_build
for %%J in (mall-gateway mall-mgr mall-alipay) do if not exist "%%J\target\%%J-1.0.0.jar" set "JARS_MISSING=1"
if "%JARS_MISSING%"=="1" set "NEED_BUILD=1"
if "%FORCE_BUILD%"=="1" set "NEED_BUILD=1"

if "%NEED_BUILD%"=="0" (
    call :jars_stale
    if errorlevel 1 (
        echo [2/4] [WARN] Some sources are newer than the jars - they may be outdated.
        echo             Run "start-all.bat rebuild" or build the project in your IDE
        echo             to refresh them.
    ) else (
        echo [2/4] Backend jars are up to date - build skipped.
    )
    goto step_backend
)

echo [2/4] Building backend jars: mvn clean package -DskipTests
call :find_mvn
if errorlevel 1 (
    if "%JARS_MISSING%"=="1" (
        echo       [ERROR] Maven not found and the jars are missing.
        echo               Install Maven, or build the project in IntelliJ IDEA first.
        goto fail
    )
    echo       [WARN] Maven not found - starting with the existing jars, they may be outdated.
    goto step_backend
)

call "%MVN_CMD%" clean package -DskipTests
if errorlevel 1 (
    echo       [ERROR] Build failed. Fix the compile errors and run this script again.
    goto fail
)
echo       Build finished.

rem ======================== [3/4] backend services ========================
:step_backend
set "MYSQL_PORT=%MYSQL_HOST_PORT%"
if "%MYSQL_PORT%"=="" set "MYSQL_PORT=3306"

echo [3/4] Starting backend services  (MySQL port %MYSQL_PORT%) ...
call :port_listening 10010
if errorlevel 1 (
    start "MallX-Gateway-10010" cmd /k "java -jar mall-gateway\target\mall-gateway-1.0.0.jar"
) else (
    echo       Port 10010 is busy - assuming mall-gateway is running, skip.
)
call :port_listening 10020
if errorlevel 1 (
    start "MallX-Mgr-10020" cmd /k "java -jar mall-mgr\target\mall-mgr-1.0.0.jar"
) else (
    echo       Port 10020 is busy - assuming mall-mgr is running, skip.
)
call :port_listening 10030
if errorlevel 1 (
    start "MallX-Alipay-10030" cmd /k "java -jar mall-alipay\target\mall-alipay-1.0.0.jar"
) else (
    echo       Port 10030 is busy - assuming mall-alipay is running, skip.
)

echo       Waiting for the backend services to open their ports ...
call :wait_port 10010 150
if errorlevel 1 echo       [WARN] mall-gateway is not listening on 10010 - check its window.
call :wait_port 10020 150
if errorlevel 1 echo       [WARN] mall-mgr is not listening on 10020 - check its window.
call :wait_port 10030 150
if errorlevel 1 echo       [WARN] mall-alipay is not listening on 10030 - check its window.

rem ======================== [4/4] frontend ========================
:step_frontend
if not exist "mall-vue\node_modules" (
    echo [4/4] First run - installing frontend dependencies via npmmirror ...
    pushd mall-vue
    call npm install --no-audit --no-fund --registry=https://registry.npmmirror.com
    if errorlevel 1 (
        echo       [ERROR] npm install failed.
        popd
        goto fail
    )
    popd
) else (
    echo [4/4] Frontend dependencies are ready.
)

set "VITE_USE_MOCK=%FRONTEND_MOCK%"
echo       Starting the Vite dev server  (VITE_USE_MOCK=%VITE_USE_MOCK%) ...
start "MallX-Frontend-5173" /D "%~dp0mall-vue" cmd /k "npm run dev"
call :wait_port 5173 90
if errorlevel 1 echo       [WARN] Vite is not listening on 5173 - check its window.

ping -n 3 127.0.0.1 >nul 2>&1
echo.
echo ==================================================
echo   Mall-X launcher finished
echo   Frontend : http://localhost:5173
echo   Gateway  : http://localhost:10010
echo   Accounts : admin / shop1 / shop2 / user1   pwd 123456
echo   Mode     : %MODE%   VITE_USE_MOCK=%VITE_USE_MOCK%
echo --------------------------------------------------
echo   Stop the stack: close the spawned command windows.
echo ==================================================
pause
exit /b 0

rem =====================================================================
rem  failure / help
rem =====================================================================
:fail
echo.
echo ==================================================
echo   Mall-X launcher stopped with an error.
echo   Fix the problem above, then run this script again.
echo ==================================================
pause
exit /b 1

:usage
echo.
echo Usage: start-all.bat [mock] [no-docker] [rebuild] [dashboard]
echo.
echo   mock        frontend only with built-in mock data, no docker and no backend
echo   no-docker   skip middleware startup, use when the containers are already up
echo   rebuild     force "mvn clean package -DskipTests" before starting the backend
echo   dashboard   also start the RocketMQ console on http://localhost:8180
echo.
exit /b 0

rem =====================================================================
rem  helpers
rem =====================================================================

:port_listening
rem %1 = tcp port. exit 0 when it is LISTENING, exit 1 otherwise.
setlocal
netstat -ano -p tcp | findstr /c:":%~1 " | findstr /i "LISTENING" >nul 2>nul
if errorlevel 1 (endlocal & exit /b 1)
endlocal & exit /b 0

:wait_port
rem %1 = tcp port, %2 = timeout in seconds. exit 0 on success, 1 on timeout.
setlocal EnableDelayedExpansion
set /a "_t=0"
:wait_port_loop
call :port_listening %1
if not errorlevel 1 (endlocal & exit /b 0)
set /a "_t+=2"
if !_t! geq %2 (endlocal & exit /b 1)
ping -n 3 127.0.0.1 >nul 2>&1
goto wait_port_loop

:wait_health
rem %1 = container name, %2 = timeout in seconds. exit 0 on healthy, 1 on timeout.
setlocal EnableDelayedExpansion
set /a "_t=0"
:wait_health_loop
set "_h=none"
for /f "delims=" %%H in ('docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" %1 2^>nul') do set "_h=%%H"
if /i "!_h!"=="healthy" (endlocal & exit /b 0)
set /a "_t+=2"
if !_t! geq %2 (endlocal & exit /b 1)
ping -n 3 127.0.0.1 >nul 2>&1
goto wait_health_loop

:wait_docker
rem %1 = timeout in seconds. exit 0 when the docker engine answers, 1 on timeout.
setlocal EnableDelayedExpansion
set /a "_t=0"
:wait_docker_loop
docker info >nul 2>&1
if not errorlevel 1 (endlocal & exit /b 0)
set /a "_t+=5"
if !_t! geq %2 (endlocal & exit /b 1)
echo       ... waiting for the Docker engine  %_t%s
ping -n 6 127.0.0.1 >nul 2>&1
goto wait_docker_loop

:jars_stale
rem exit 0 when the jars are up to date, exit 1 when any source is newer.
rem The inline PowerShell below deliberately uses no pipeline: inside a for /f
rem command string an escaped pipe is not unescaped and would reach PowerShell.
setlocal
set "_r=0"
for /f "delims=" %%S in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "$m='mall-gateway','mall-mgr','mall-alipay';$r=0;foreach($x in $m){$j=$x+'\target\'+$x+'-1.0.0.jar';if(-not(Test-Path $j)){$r=1;break};$t=(Get-Item $j).LastWriteTimeUtc;$n=[datetime]'2000-01-01';foreach($i in (Get-ChildItem ($x+'\src') -Recurse -File)){if($i.LastWriteTimeUtc -gt $n){$n=$i.LastWriteTimeUtc}};foreach($i in (Get-Item 'pom.xml','mall-common\pom.xml')){if($i.LastWriteTimeUtc -gt $n){$n=$i.LastWriteTimeUtc}};if($n -gt $t){$r=1;break}};if($r -eq 0){$t=(Get-Item 'mall-gateway\target\mall-gateway-1.0.0.jar').LastWriteTimeUtc;$n=[datetime]'2000-01-01';foreach($i in (Get-ChildItem 'mall-common\src' -Recurse -File)){if($i.LastWriteTimeUtc -gt $n){$n=$i.LastWriteTimeUtc}};if($n -gt $t){$r=1}};$r" 2^>nul') do set "_r=%%S"
if "%_r%"=="1" (endlocal & exit /b 1)
endlocal & exit /b 0

:find_mvn
rem sets MVN_CMD. exit 0 when a maven launcher was found, 1 otherwise.
set "MVN_CMD="
for /f "delims=" %%M in ('where mvn 2^>nul') do if not defined MVN_CMD set "MVN_CMD=%%M"
if defined MVN_CMD exit /b 0
rem fall back to a standalone maven unpacked under the user profile
for /d %%D in ("%USERPROFILE%\tools\apache-maven-*") do if not defined MVN_CMD if exist "%%D\bin\mvn.cmd" set "MVN_CMD=%%D\bin\mvn.cmd"
if defined MVN_CMD exit /b 0
rem fall back to the maven bundled with IntelliJ IDEA
for /d %%D in ("%ProgramFiles%\JetBrains\*") do if not defined MVN_CMD if exist "%%D\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN_CMD=%%D\plugins\maven\lib\maven3\bin\mvn.cmd"
if defined MVN_CMD exit /b 0
exit /b 1
