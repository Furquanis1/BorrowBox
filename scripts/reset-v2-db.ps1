<#
.SYNOPSIS
    Drops and recreates the local V2.1 development database (borrowbox_v2).

.DESCRIPTION
    For LOCAL development only. Docker environments use docker-compose.yml.

    This script connects to the local MySQL instance and:
      1. Drops the borrowbox_v2 database if it exists
      2. Recreates it as an empty database
      3. Creates/updates the local app and E2E accounts with the supplied passwords
      4. Prints confirmation

    Credentials are never accepted on the command line and never printed.
    Provide the administrator credentials through BORROWBOX_RESET_DB_USER and
    BORROWBOX_RESET_DB_PASSWORD, or let the script prompt for them. The password
    is written only to a protected temporary MySQL option file that is deleted
    in a finally block.

    BORROWBOX_APP_DB_PASSWORD and BORROWBOX_E2E_DB_PASSWORD are required. They
    are used to create/update the scoped local accounts borrowbox_app and
    borrowbox_e2e after the database is recreated. Set them in the environment
    before running this script; they are never printed.

    After running this script, start the Spring Boot backend with
    SPRING_DATASOURCE_USERNAME=borrowbox_app and the app password from the
    environment. Demo seeding is disabled by default; opt in explicitly with
    BORROWBOX_SEED_ENABLED=true and a non-blank BORROWBOX_SEED_PASSWORD.

.EXAMPLE
    .\scripts\reset-v2-db.ps1
#>

$ErrorActionPreference = "Stop"

$DB_NAME    = "borrowbox_v2"
$APP_USER   = "borrowbox_app"
$E2E_USER   = "borrowbox_e2e"
$MYSQL_HOST = "127.0.0.1"
$MYSQL_PORT = "3306"
$MYSQL_CMD  = "mysql"
$optionFile = $null
$previousMysqlPwd = [Environment]::GetEnvironmentVariable("MYSQL_PWD", "Process")

function Get-RequiredValue {
    param(
        [Parameter(Mandatory = $true)][string]$EnvironmentVariable,
        [Parameter(Mandatory = $true)][string]$Prompt
    )

    $value = [Environment]::GetEnvironmentVariable($EnvironmentVariable, "Process")
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        return $value.Trim()
    }

    if ([Console]::IsInputRedirected) {
        throw "Set $EnvironmentVariable or run this script interactively."
    }

    try {
        $value = Read-Host -Prompt $Prompt
    } catch {
        throw "Set $EnvironmentVariable or run this script interactively."
    }

    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "No value was provided for $EnvironmentVariable."
    }
    return $value.Trim()
}

function Get-RequiredSecret {
    param(
        [Parameter(Mandatory = $true)][string]$EnvironmentVariable,
        [Parameter(Mandatory = $true)][string]$Prompt
    )

    $value = [Environment]::GetEnvironmentVariable($EnvironmentVariable, "Process")
    if (-not [string]::IsNullOrEmpty($value)) {
        return $value
    }

    if ([Console]::IsInputRedirected) {
        throw "Set $EnvironmentVariable or run this script interactively."
    }

    try {
        $secureValue = Read-Host -Prompt $Prompt -AsSecureString
    } catch {
        throw "Set $EnvironmentVariable or run this script interactively."
    }

    if ($null -eq $secureValue -or $secureValue.Length -eq 0) {
        throw "No value was provided for $EnvironmentVariable."
    }

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Get-RequiredEnvironmentSecret {
    param([Parameter(Mandatory = $true)][string]$EnvironmentVariable)

    $value = [Environment]::GetEnvironmentVariable($EnvironmentVariable, "Process")
    if ([string]::IsNullOrEmpty($value)) {
        throw "Set $EnvironmentVariable before running this script."
    }
    return $value
}

function Assert-SqlPasswordSupported {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Name
    )

    if ($Value -notmatch '^[A-Za-z0-9._~!@#$%^&*()+=:,-]+$') {
        throw "$Name contains characters that are not supported by this reset script."
    }
}

function ConvertTo-MySqlOptionValue {
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -match "[\x00\r\n]") {
        throw "Credentials containing NUL, CR, or LF characters are not supported."
    }

    return $Value.Replace('\', '\\').Replace('"', '\"')
}

function Protect-CredentialFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $icacls = Get-Command icacls.exe -ErrorAction SilentlyContinue
    if ($null -eq $icacls) {
        throw "icacls.exe is required to protect the temporary credential file."
    }

    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & $icacls.Source $Path /inheritance:r /grant:r "$($identity):F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not restrict permissions on the temporary MySQL option file."
    }
}

try {
    $mysql = Get-Command $MYSQL_CMD -ErrorAction SilentlyContinue
    if ($null -eq $mysql) {
        throw "mysql client not found in PATH. Install MySQL client tools or ensure the mysql command is available."
    }

    $mysqlUser = Get-RequiredValue -EnvironmentVariable "BORROWBOX_RESET_DB_USER" -Prompt "MySQL user"
    $mysqlPassword = Get-RequiredSecret -EnvironmentVariable "BORROWBOX_RESET_DB_PASSWORD" -Prompt "MySQL password"
    $appPassword = Get-RequiredEnvironmentSecret -EnvironmentVariable "BORROWBOX_APP_DB_PASSWORD"
    $e2ePassword = Get-RequiredEnvironmentSecret -EnvironmentVariable "BORROWBOX_E2E_DB_PASSWORD"
    Assert-SqlPasswordSupported -Value $appPassword -Name "BORROWBOX_APP_DB_PASSWORD"
    Assert-SqlPasswordSupported -Value $e2ePassword -Name "BORROWBOX_E2E_DB_PASSWORD"

    $userOption = ConvertTo-MySqlOptionValue $mysqlUser
    $passwordOption = ConvertTo-MySqlOptionValue $mysqlPassword

    $optionFile = Join-Path ([System.IO.Path]::GetTempPath()) ("borrowbox-reset-" + [Guid]::NewGuid().ToString("N") + ".cnf")
    New-Item -ItemType File -Path $optionFile -ErrorAction Stop | Out-Null
    Protect-CredentialFile -Path $optionFile

    $optionContent = @"
[client]
user="$userOption"
password="$passwordOption"
host=$MYSQL_HOST
port=$MYSQL_PORT
protocol=TCP
connect-timeout=5
default-character-set=utf8mb4
"@
    [System.IO.File]::WriteAllText(
        $optionFile,
        $optionContent,
        (New-Object System.Text.UTF8Encoding -ArgumentList $false))

    [Environment]::SetEnvironmentVariable("MYSQL_PWD", $null, "Process")

    Write-Host "=== BorrowBox V2.1 Database Reset ===" -ForegroundColor Cyan
    Write-Host "Target:   $DB_NAME @ $MYSQL_HOST`:$MYSQL_PORT"
    Write-Host "Credentials: supplied through environment variables or secure prompt"
    Write-Host ""

    $dropQuery   = "DROP DATABASE IF EXISTS ``$DB_NAME``;"
    $createQuery = "CREATE DATABASE ``$DB_NAME`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    Write-Host "Dropping database '$DB_NAME'..." -ForegroundColor Yellow
    & $mysql.Source "--defaults-file=$optionFile" "--execute=$dropQuery"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to drop database. Is MySQL running on $MYSQL_HOST`:$MYSQL_PORT?"
    }

    Write-Host "Creating database '$DB_NAME'..." -ForegroundColor Yellow
    & $mysql.Source "--defaults-file=$optionFile" "--execute=$createQuery"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create database."
    }

    Write-Host "Provisioning application database accounts..." -ForegroundColor Yellow
    $accountSql = @"
CREATE USER IF NOT EXISTS '$APP_USER'@'%' IDENTIFIED BY '$appPassword';
ALTER USER '$APP_USER'@'%' IDENTIFIED BY '$appPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON ``$DB_NAME``.* TO '$APP_USER'@'%';
CREATE USER IF NOT EXISTS '$E2E_USER'@'%' IDENTIFIED BY '$e2ePassword';
ALTER USER '$E2E_USER'@'%' IDENTIFIED BY '$e2ePassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON ``$DB_NAME``.* TO '$E2E_USER'@'%';
"@
    $accountSql | & $mysql.Source "--defaults-file=$optionFile"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to provision application database accounts."
    }

    Write-Host ""
    Write-Host "Database '$DB_NAME' has been reset." -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Start the backend with the app account:"
    Write-Host "       `$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/$DB_NAME'"
    Write-Host "       `$env:SPRING_DATASOURCE_USERNAME = '$APP_USER'"
    Write-Host "       `$env:SPRING_DATASOURCE_PASSWORD = <BORROWBOX_APP_DB_PASSWORD>"
    Write-Host "       cd backend && mvn spring-boot:run"
    Write-Host "     (schema.sql runs automatically; demo seeding is opt-in)"
    Write-Host "     Set BORROWBOX_SEED_ENABLED=true and BORROWBOX_SEED_PASSWORD only when needed."
    Write-Host ""
    Write-Host "  2. Run the backend tests with the same app credentials: cd backend && mvn test"
} catch {
    [Console]::Error.WriteLine("Error: $($_.Exception.Message)")
    exit 1
} finally {
    [Environment]::SetEnvironmentVariable("MYSQL_PWD", $previousMysqlPwd, "Process")
    if ($null -ne $optionFile -and (Test-Path -LiteralPath $optionFile)) {
        try {
            [System.IO.File]::WriteAllText(
                $optionFile,
                "",
                (New-Object System.Text.UTF8Encoding -ArgumentList $false))
            Remove-Item -LiteralPath $optionFile -Force -ErrorAction Stop
        } catch {
            Write-Warning "Temporary MySQL option file could not be removed: $optionFile"
        }
    }
}
