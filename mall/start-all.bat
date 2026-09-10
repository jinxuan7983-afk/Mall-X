@echo off
setlocal
title Mall-X Startup
cd /d "%~dp0"

echo ==================================================
echo   Mall-X  -  start backend + frontend
echo   Gateway :10010   Mgr :10020   Alipay :10030
echo   Frontend : http://localhost:5173
echo ==================================================
echo.

rem ---- optional arg: mock = frontend uses built-in mock data ----
set "FRONTEND_MOCK=false"
if /i "%~1"=="mock" set "FRONTEND_MOCK=true"

rem ---- MySQL port: if a native MySQL already occupies 3306, container uses 3307 ----
set "MYSQL_HOST_PORT=3306"
where docker >nul 2>nul
if %errorlevel%==0 (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>nul
    if errorlevel 1 (
        echo [1/4] Starting middleware containers, MySQL on default port 3306 ...
    ) else (
        echo [1/4] Port 3306 busy - native MySQL detected, container MySQL will use 3307.
        set "MYSQL_HOST_PORT=3307"
    )
    pushd docker
    docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker
    popd
    echo       Waiting for middleware, 15 s ...
    timeout /t 15 /nobreak >nul
) else (
    echo [1/4] Docker not found, skip middleware startup.
    echo       Make sure MySQL / Redis / Nacos / RocketMQ are running, or backend will fail.
)

rem backend connects to the container MySQL port chosen above (empty = native default 3306)
set "MYSQL_PORT=%MYSQL_HOST_PORT%"
if "%MYSQL_PORT%"=="" set "MYSQL_PORT=3306"

rem ---- 2. backend build check ----
set "NEED_BUILD=0"
if not exist "mall-gateway\target\mall-gateway-1.0.0.jar" set "NEED_BUILD=1"
if not exist "mall-mgr\target\mall-mgr-1.0.0.jar" set "NEED_BUILD=1"
if not exist "mall-alipay\target\mall-alipay-1.0.0.jar" set "NEED_BUILD=1"

if "%NEED_BUILD%"=="1" (
    echo [2/4] Jars not found, running: mvn clean package -DskipTests ...
    where mvn >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] mvn not found. Install Maven or build in IDEA first.
        pause
        exit /b 1
    )
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Backend build failed. Fix errors and retry.
        pause
        exit /b 1
    )
) else (
    echo [2/4] Backend jars ready, skip build.
)

rem ---- 3. backend services ----
netstat -ano | findstr ":10010" | findstr "LISTENING" >nul 2>nul
if %errorlevel%==0 (
    echo [3/4] Port 10010 already in use, backend may be running. Skip.
) else (
    echo [3/4] Starting backend services in separate windows, MySQL port %MYSQL_PORT% ...
    start "MallX-Gateway-10010" cmd /k "chcp 65001 >nul && java -jar mall-gateway\target\mall-gateway-1.0.0.jar"
    timeout /t 3 /nobreak >nul
    start "MallX-Mgr-10020" cmd /k "chcp 65001 >nul && java -jar mall-mgr\target\mall-mgr-1.0.0.jar"
    start "MallX-Alipay-10030" cmd /k "chcp 65001 >nul && java -jar mall-alipay\target\mall-alipay-1.0.0.jar"
    echo       Waiting for services to register, 10 s ...
    timeout /t 10 /nobreak >nul
)

rem ---- 4. frontend ----
if not exist "mall-vue\node_modules" (
    echo [4/4] First run: installing frontend deps via npmmirror ...
    pushd mall-vue
    call npm install --no-audit --no-fund --registry=https://registry.npmmirror.com
    popd
) else (
    echo [4/4] Frontend deps ready.
)

set "VITE_USE_MOCK=%FRONTEND_MOCK%"
start "MallX-Frontend-5173" /D "%~dp0mall-vue" cmd /k "npm run dev"

echo.
echo ==================================================
echo   All start commands issued!
echo   Frontend : http://localhost:5173
echo   Gateway  : http://localhost:10010
echo   Accounts : admin / shop1 / shop2 / user1   pwd: 123456
echo   Stop     : close the corresponding cmd windows
echo   Mock mode: start-all.bat mock  -  frontend only, no backend needed
echo ==================================================
pause
