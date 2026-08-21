@echo off
setlocal enabledelayedexpansion
REM ──────────────────────────────────────────────────────────
REM  run_demo.bat — One-click launch for BorrowBox on Windows.
REM
REM  Usage:
REM    run_demo.bat            Start all services (build if needed)
REM    run_demo.bat --build    Force a full rebuild of all images
REM    run_demo.bat --stop     Stop and remove all containers
REM    run_demo.bat --clean    Stop containers and remove volumes
REM ──────────────────────────────────────────────────────────

echo.
echo ======================================
echo        BorrowBox Demo Launcher
echo ======================================
echo.

REM ── Pre-flight: Docker ──────────────────────────────────
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed or not in PATH.
    echo         Install Docker Desktop: https://docs.docker.com/get-docker/
    exit /b 1
)

docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker daemon is not running. Start Docker Desktop and retry.
    exit /b 1
)
echo [OK]    Docker is available and running.

REM ── Pre-flight: Compose ─────────────────────────────────
docker compose version >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK]    Docker Compose (plugin) detected.
    set "COMPOSE_CMD=docker compose"
) else (
    where docker-compose >nul 2>&1
    if %errorlevel% equ 0 (
        echo [OK]    docker-compose (standalone) detected.
        set "COMPOSE_CMD=docker-compose"
    ) else (
        echo [ERROR] Docker Compose is not installed.
        exit /b 1
    )
)

REM ── Route to action ─────────────────────────────────────
if "%~1"=="--stop"  goto :do_stop
if "%~1"=="--clean" goto :do_clean
if "%~1"=="--build" goto :do_start_build
if "%~1"==""        goto :do_start
echo Usage: %~nx0 [--build^|--stop^|--clean]
exit /b 1

:do_stop
echo [INFO]  Stopping BorrowBox containers...
%COMPOSE_CMD% -f docker-compose.yml down
echo [OK]    Containers stopped.
goto :eof

:do_clean
echo [INFO]  Stopping containers and removing volumes...
%COMPOSE_CMD% -f docker-compose.yml down -v
echo [OK]    Containers stopped and volumes removed.
goto :eof

:do_start_build
echo [INFO]  Building images from source (this may take a few minutes)...
%COMPOSE_CMD% -f docker-compose.yml up --build -d
goto :health_check

:do_start
%COMPOSE_CMD% -f docker-compose.yml up -d
goto :health_check

:health_check
echo.
echo [INFO]  Waiting for services to become healthy...

set /a max_wait=120
set /a elapsed=0
set /a interval=5

:wait_loop
if %elapsed% geq %max_wait% goto :timeout

REM Try hitting the health endpoint
curl -sf http://localhost:8080/api/health >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK]    Backend API is healthy.
    goto :ready
)

timeout /t %interval% /nobreak >nul
set /a elapsed+=interval
echo [INFO]    Waiting for backend... (%elapsed%s / %max_wait%s)
goto :wait_loop

:timeout
echo [WARN]  Backend did not respond within %max_wait%s.
echo [WARN]  Check logs with: %COMPOSE_CMD% -f docker-compose.yml logs backend

:ready
echo.
echo ────────────────────────────────────────────
echo [OK]    BorrowBox is running!
echo.
echo   Frontend:    http://localhost:3000
echo   Backend API: http://localhost:8080
echo   Health:      http://localhost:8080/api/health
echo.
echo   Stop:        %~nx0 --stop
echo   Full reset:  %~nx0 --clean
echo   View logs:   %COMPOSE_CMD% -f docker-compose.yml logs -f
echo ────────────────────────────────────────────

endlocal
