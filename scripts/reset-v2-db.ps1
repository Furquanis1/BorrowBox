<#
.SYNOPSIS
    Drops and recreates the local V2.1 development database (borrowbox_v2).

.DESCRIPTION
    For LOCAL development only. Docker environments use docker-compose.yml credentials.
    This script connects to the local MySQL instance and:
      1. Drops the borrowbox_v2 database if it exists
      2. Recreates it as an empty database
      3. Prints confirmation

    After running this script, start the Spring Boot backend with borrowbox.seed.enabled=true
    to re-run schema.sql and the deterministic seed initializer.

.EXAMPLE
    .\scripts\reset-v2-db.ps1
#>

$ErrorActionPreference = "Stop"

$DB_NAME   = "borrowbox_v2"
$MYSQL_CMD = "mysql"

# Local development credentials (NOT Docker credentials)
$MYSQL_USER     = "root"
$MYSQL_PASSWORD = "khan@123"
$MYSQL_HOST     = "127.0.0.1"
$MYSQL_PORT     = "3306"

Write-Host "=== BorrowBox V2.1 Database Reset ===" -ForegroundColor Cyan
Write-Host "Target:   $DB_NAME @ $MYSQL_HOST`:$MYSQL_PORT"
Write-Host "Credentials: local development (root)"
Write-Host ""

# Verify mysql client is available
try {
    & $MYSQL_CMD --version 2>$null | Out-Null
} catch {
    Write-Error "mysql client not found in PATH. Install MySQL client tools or ensure the mysql command is available."
    exit 1
}

# Drop and recreate the database
$dropQuery   = "DROP DATABASE IF EXISTS ``$DB_NAME``;"
$createQuery = "CREATE DATABASE ``$DB_NAME`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

$pwArg = "-p$MYSQL_PASSWORD"

Write-Host "Dropping database '$DB_NAME'..." -ForegroundColor Yellow
& $MYSQL_CMD -u $MYSQL_USER $pwArg -h $MYSQL_HOST -P $MYSQL_PORT -e $dropQuery
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to drop database. Is MySQL running on $MYSQL_HOST`:$MYSQL_PORT?"
    exit 1
}

Write-Host "Creating database '$DB_NAME'..." -ForegroundColor Yellow
& $MYSQL_CMD -u $MYSQL_USER $pwArg -h $MYSQL_HOST -P $MYSQL_PORT -e $createQuery
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create database."
    exit 1
}

Write-Host ""
Write-Host "Database '$DB_NAME' has been reset." -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Start the backend: cd backend && mvn spring-boot:run"
Write-Host "     (borrowbox.seed.enabled=true in application.properties will re-seed)"
Write-Host ""
Write-Host "  2. Run the backend tests: cd backend && mvn test"
