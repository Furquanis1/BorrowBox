@echo off
setlocal enabledelayedexpansion
set "PROJECT_DIR=%~dp0"
set "CREDENTIALS_FILE=%PROJECT_DIR%.borrowbox-demo-credentials"
REM ──────────────────────────────────────────────────────────
REM  run_demo.bat — One-click launch for BorrowBox on Windows.
REM
REM  Usage:
REM    run_demo.bat            Start all services (build if needed)
REM    run_demo.bat --build    Force a full rebuild of all images
REM    run_demo.bat --stop     Stop and remove all containers
REM    run_demo.bat --clean    Stop containers, remove volumes, and clear demo credentials
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
    echo [OK]    Docker Compose ^(plugin^) detected.
    set "COMPOSE_CMD=docker compose"
) else (
    where docker-compose >nul 2>&1
    if %errorlevel% equ 0 (
        echo [OK]    docker-compose ^(standalone^) detected.
        set "COMPOSE_CMD=docker-compose"
    ) else (
        echo [ERROR] Docker Compose is not installed.
        exit /b 1
    )
)

REM ── JWT secret ──────────────────────────────────────────
call :ensure_jwt_secret
if errorlevel 1 exit /b 1

REM -- Compose interpolation values are required even for non-DB actions.
call :ensure_compose_interpolation_defaults
if errorlevel 1 exit /b 1

REM ── Route to action ─────────────────────────────────────
if "%~1"=="--stop"  goto :do_stop
if "%~1"=="--clean" goto :do_clean
if "%~1"=="--build" goto :do_start_build
if "%~1"==""        goto :do_start
echo Usage: %~nx0 [--build^|--stop^|--clean]
exit /b 1

REM ── JWT secret subroutine ───────────────────────────────
:ensure_jwt_secret
if defined APP_JWT_SECRET exit /b 0

where powershell >nul 2>&1
if errorlevel 1 (
    echo [ERROR] APP_JWT_SECRET is not set and PowerShell is unavailable.
    exit /b 1
)

for /f "usebackq delims=" %%S in (`powershell -NoProfile -NonInteractive -Command "$b = New-Object byte[] 48; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($b); [Convert]::ToBase64String($b)"`) do set "APP_JWT_SECRET=%%S"

if not defined APP_JWT_SECRET (
    echo [ERROR] Could not generate APP_JWT_SECRET.
    exit /b 1
)

echo [INFO]  Generated a temporary APP_JWT_SECRET for this demo run.
exit /b 0

REM ── Local demo credentials ──────────────────────────────
:ensure_seed_credentials
set "SEED_PASSWORD="
set "MYSQL_PASSWORD="
set "APP_DB_PASSWORD="
set "E2E_DB_PASSWORD="
set "NEED_WRITE=0"
call :ensure_compose_interpolation_defaults
if errorlevel 1 exit /b 1
if exist "%CREDENTIALS_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%CREDENTIALS_FILE%") do (
        if /I "%%A"=="BORROWBOX_SEED_PASSWORD" set "SEED_PASSWORD=%%B"
        if /I "%%A"=="MYSQL_ROOT_PASSWORD" set "MYSQL_PASSWORD=%%B"
        if /I "%%A"=="BORROWBOX_APP_DB_PASSWORD" set "APP_DB_PASSWORD=%%B"
        if /I "%%A"=="BORROWBOX_E2E_DB_PASSWORD" set "E2E_DB_PASSWORD=%%B"
    )
    if not defined SEED_PASSWORD (
        echo [ERROR] The local demo credential file is unreadable or does not contain
        echo         BORROWBOX_SEED_PASSWORD: %CREDENTIALS_FILE%
        echo         Restore it, or run: %~nx0 --clean
        echo         --clean deletes the demo database and all of its data.
        exit /b 1
    )
    if not defined MYSQL_PASSWORD (
        call :detect_demo_database
        if errorlevel 1 exit /b 1
        if "!DB_PRESENT!"=="1" (
            echo [ERROR] The local demo credential file does not contain MYSQL_ROOT_PASSWORD:
            echo         %CREDENTIALS_FILE%
            echo         An existing demo database keeps the root password it was created with;
            echo         changing the credential file alone does not rotate the database password.
            echo         If this database predates the credential-file change, set
            echo         MYSQL_ROOT_PASSWORD to the root password that database already uses.
            echo         Otherwise, run: %~nx0 --clean
            echo         --clean deletes the demo database and all of its data.
            exit /b 1
        )
        call :generate_mysql_password
        if errorlevel 1 exit /b 1
        if not defined MYSQL_PASSWORD (
            echo [ERROR] Could not generate a local MySQL root password.
            exit /b 1
        )
        set "NEED_WRITE=1"
    )
    if not defined APP_DB_PASSWORD (
        call :generate_password APP_DB_PASSWORD
        if errorlevel 1 exit /b 1
        if not defined APP_DB_PASSWORD (
            echo [ERROR] Could not generate a local app database password.
            exit /b 1
        )
        set "NEED_WRITE=1"
    )
    if not defined E2E_DB_PASSWORD (
        call :generate_password E2E_DB_PASSWORD
        if errorlevel 1 exit /b 1
        if not defined E2E_DB_PASSWORD (
            echo [ERROR] Could not generate a local E2E database password.
            exit /b 1
        )
        set "NEED_WRITE=1"
    )
    if "!NEED_WRITE!"=="1" (
        call :write_credentials_file
        if errorlevel 1 exit /b 1
        echo [OK]    Updated %CREDENTIALS_FILE% with the missing database credentials.
    )
    call :restrict_credentials_file
    if errorlevel 1 exit /b 1
    echo [OK]    Using existing local demo credentials file.
) else (
    call :detect_demo_database
    if errorlevel 1 exit /b 1
    if "!DB_PRESENT!"=="1" (
        echo [ERROR] A BorrowBox demo database exists, but the local credential file is missing:
        echo         %CREDENTIALS_FILE%
        echo         The file must contain BORROWBOX_SEED_PASSWORD, MYSQL_ROOT_PASSWORD,
        echo         BORROWBOX_APP_DB_PASSWORD, and BORROWBOX_E2E_DB_PASSWORD.
        echo         Restore the credential file, or run: %~nx0 --clean
        echo         --clean deletes the demo database and all of its data.
        exit /b 1
    )
    call :generate_password SEED_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined SEED_PASSWORD (
        echo [ERROR] Could not generate a local demo password.
        exit /b 1
    )
    call :generate_mysql_password
    if errorlevel 1 exit /b 1
    if not defined MYSQL_PASSWORD (
        echo [ERROR] Could not generate a local MySQL root password.
        exit /b 1
    )
    call :generate_password APP_DB_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined APP_DB_PASSWORD (
        echo [ERROR] Could not generate a local app database password.
        exit /b 1
    )
    call :generate_password E2E_DB_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined E2E_DB_PASSWORD (
        echo [ERROR] Could not generate a local E2E database password.
        exit /b 1
    )
    call :write_credentials_file
    if errorlevel 1 exit /b 1
    echo [OK]    Generated local demo credentials in %CREDENTIALS_FILE%
    echo         Open that file to retrieve the demo account and MySQL passwords.
)
set "BORROWBOX_SEED_ENABLED=true"
set "BORROWBOX_SEED_PASSWORD=!SEED_PASSWORD!"
set "MYSQL_ROOT_PASSWORD=!MYSQL_PASSWORD!"
set "BORROWBOX_APP_DB_PASSWORD=!APP_DB_PASSWORD!"
set "BORROWBOX_E2E_DB_PASSWORD=!E2E_DB_PASSWORD!"
exit /b 0

:detect_demo_database
set "DB_PRESENT=0"
set "COMPOSE_PROJECT="
for /f "tokens=2" %%P in ('%COMPOSE_CMD% -f docker-compose.yml config 2^>nul ^| findstr /B "name:"') do set "COMPOSE_PROJECT=%%P"
if not defined COMPOSE_PROJECT set "COMPOSE_PROJECT=borrowbox"
for /f "delims=" %%C in ('%COMPOSE_CMD% -f docker-compose.yml ps -a -q mysql 2^>nul') do set "DB_PRESENT=1"
for /f "delims=" %%V in ('docker volume ls --filter "label=com.docker.compose.project=%COMPOSE_PROJECT%" --filter "label=com.docker.compose.volume=mysql_data" --format "{{.Name}}" 2^>nul') do set "DB_PRESENT=1"
exit /b 0

:generate_password
set "%~1="
where powershell >nul 2>&1
if errorlevel 1 (
    echo [ERROR] PowerShell is unavailable; cannot generate a password.
    exit /b 1
)
for /f "usebackq delims=" %%S in (`powershell -NoProfile -NonInteractive -Command "$b = New-Object byte[] 32; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($b); [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')"`) do set "%~1=%%S"
if not defined %~1 (
    echo [ERROR] Failed to generate a password.
    exit /b 1
)
exit /b 0

:generate_demo_password
call :generate_password SEED_PASSWORD
exit /b %errorlevel%

:generate_mysql_password
call :generate_password MYSQL_PASSWORD
exit /b %errorlevel%

:ensure_compose_interpolation_defaults
if not defined MYSQL_ROOT_PASSWORD (
    call :generate_password MYSQL_ROOT_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined MYSQL_ROOT_PASSWORD (
        echo [ERROR] Could not generate a temporary MySQL credential for Compose.
        exit /b 1
    )
)
if not defined BORROWBOX_APP_DB_PASSWORD (
    call :generate_password BORROWBOX_APP_DB_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined BORROWBOX_APP_DB_PASSWORD (
        echo [ERROR] Could not generate a temporary app database credential for Compose.
        exit /b 1
    )
)
if not defined BORROWBOX_E2E_DB_PASSWORD (
    call :generate_password BORROWBOX_E2E_DB_PASSWORD
    if errorlevel 1 exit /b 1
    if not defined BORROWBOX_E2E_DB_PASSWORD (
        echo [ERROR] Could not generate a temporary E2E database credential for Compose.
        exit /b 1
    )
)
exit /b 0

:write_credentials_file
> "%CREDENTIALS_FILE%"
if errorlevel 1 (
    echo [ERROR] Could not create the local demo credential file: %CREDENTIALS_FILE%
    exit /b 1
)
call :restrict_credentials_file
if errorlevel 1 (
    del /q "%CREDENTIALS_FILE%" >nul 2>&1
    exit /b 1
)
(
    echo # BorrowBox local demo credentials.
    echo # Generated by run_demo; this file is ignored by Git.
    echo BORROWBOX_SEED_PASSWORD=!SEED_PASSWORD!
    echo MYSQL_ROOT_PASSWORD=!MYSQL_PASSWORD!
    echo BORROWBOX_APP_DB_PASSWORD=!APP_DB_PASSWORD!
    echo BORROWBOX_E2E_DB_PASSWORD=!E2E_DB_PASSWORD!
    echo.
    echo Demo users:
    echo   ahmed@example.com
    echo   salah@example.com
    echo   omar@example.com
    echo   youssef@example.com
    echo   karim@example.com
) > "%CREDENTIALS_FILE%"
if errorlevel 1 (
    echo [ERROR] Could not write credentials to %CREDENTIALS_FILE%
    exit /b 1
)
exit /b 0

:restrict_credentials_file
for /f "delims=" %%U in ('whoami 2^>nul') do set "CURRENT_USER=%%U"
if not defined CURRENT_USER set "CURRENT_USER=%USERNAME%"
icacls "%CREDENTIALS_FILE%" /inheritance:r /grant:r "%CURRENT_USER%:F" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Could not restrict permissions on %CREDENTIALS_FILE%
    echo         Refusing to keep a demo password readable by other users.
    exit /b 1
)
exit /b 0

:do_stop
echo [INFO]  Stopping BorrowBox containers...
call :ensure_compose_interpolation_defaults
if errorlevel 1 exit /b 1
%COMPOSE_CMD% -f docker-compose.yml down
echo [OK]    Containers stopped.
goto :eof

:do_clean
echo [INFO]  Stopping containers and removing volumes...
call :ensure_compose_interpolation_defaults
if errorlevel 1 exit /b 1
%COMPOSE_CMD% -f docker-compose.yml down -v
if errorlevel 1 (
    echo [ERROR] Could not remove the demo containers and volumes.
    exit /b 1
)
if exist "%CREDENTIALS_FILE%" del /q "%CREDENTIALS_FILE%" >nul 2>&1
echo [OK]    Containers stopped, volumes removed, and local demo credentials cleared.
goto :eof

:do_start_build
call :ensure_seed_credentials
if errorlevel 1 exit /b 1
echo [INFO]  Building images from source (this may take a few minutes)...
%COMPOSE_CMD% -f docker-compose.yml up --build -d
goto :health_check

:do_start
call :ensure_seed_credentials
if errorlevel 1 exit /b 1
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
echo   Credentials: %CREDENTIALS_FILE%
echo.
echo   Stop:        %~nx0 --stop
echo   Full reset:  %~nx0 --clean
echo   View logs:   %COMPOSE_CMD% -f docker-compose.yml logs -f
echo ────────────────────────────────────────────

endlocal
