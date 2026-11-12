[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('grant-admin', 'revoke-admin', 'suspend', 'reactivate')]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [string]$Email,

    [switch]$DryRun,

    [string]$ConfirmEmail
)

$ErrorActionPreference = 'Stop'

trap {
    Write-Output 'Error: Unexpected script failure.'
    exit 1
}

function Stop-Refused {
    param([string]$Message)
    Write-Output "Refused: $Message"
    exit 2
}

function Stop-Failed {
    param([string]$Message)
    Write-Output "Error: $Message"
    exit 1
}

function Get-RequiredEnvironmentValue {
    param([string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        Stop-Failed "Missing required environment variable $Name."
    }
    return $value
}

function Get-MySqlConfiguration {
    $url = Get-RequiredEnvironmentValue 'SPRING_DATASOURCE_URL'
    $username = Get-RequiredEnvironmentValue 'SPRING_DATASOURCE_USERNAME'
    $password = Get-RequiredEnvironmentValue 'SPRING_DATASOURCE_PASSWORD'

    if (-not $url.StartsWith('jdbc:mysql://', [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Failed 'SPRING_DATASOURCE_URL must use the jdbc:mysql:// scheme.'
    }

    try {
        $uri = [Uri]('mysql:' + $url.Substring('jdbc:mysql:'.Length))
    } catch {
        Stop-Failed 'SPRING_DATASOURCE_URL is not a valid JDBC MySQL URL.'
    }

    if ([string]::IsNullOrWhiteSpace($uri.Host)) {
        Stop-Failed 'SPRING_DATASOURCE_URL does not contain a database host.'
    }

    $port = if ($uri.Port -gt 0) { $uri.Port } else { 3306 }
    if ($port -lt 1 -or $port -gt 65535) {
        Stop-Failed 'SPRING_DATASOURCE_URL contains an invalid database port.'
    }

    $database = [Uri]::UnescapeDataString($uri.AbsolutePath.TrimStart('/'))
    if ([string]::IsNullOrWhiteSpace($database) -or $database.Contains('/')) {
        Stop-Failed 'SPRING_DATASOURCE_URL does not contain a valid database name.'
    }

    return [pscustomobject]@{
        Host     = $uri.Host
        Port     = $port
        Database = $database
        Username = $username
        Password = $password
    }
}

function Get-MySqlExecutable {
    $command = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        Stop-Failed 'MySQL CLI (mysql) was not found in PATH.'
    }
    return $command.Source
}

function Invoke-MySql {
    param(
        [string]$Executable,
        [pscustomobject]$Configuration,
        [string]$Query
    )

    $arguments = @(
        '--no-defaults',
        '--protocol=TCP',
        "--host=$($Configuration.Host)",
        "--port=$($Configuration.Port)",
        "--user=$($Configuration.Username)",
        "--database=$($Configuration.Database)",
        '--connect-timeout=5',
        '--batch',
        '--skip-column-names',
        '--raw',
        '--default-character-set=utf8mb4',
        "--execute=$Query"
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    $previousPreference = $ErrorActionPreference
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $Configuration.Password, 'Process')
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        $output = & $Executable @arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
        if ($null -eq $previousPassword) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousPassword, 'Process')
        }
    }

    $lines = @($output | Where-Object { $_ -is [string] })
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines    = $lines
    }
}

function ConvertTo-MySqlStringLiteral {
    param([string]$Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hex = -join ($bytes | ForEach-Object { $_.ToString('X2') })
    return "CONVERT(X'$hex' USING utf8mb4)"
}

function Test-EmailAddress {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt 255) {
        return $false
    }
    if ($Value -ne $Value.Trim() -or $Value -match '[\x00-\x1F\x7F]' -or $Value -match '\s') {
        return $false
    }
    if (($Value -split '@').Count -ne 2) {
        return $false
    }

    try {
        $address = New-Object System.Net.Mail.MailAddress($Value)
    } catch {
        return $false
    }
    return [string]::Equals($address.Address, $Value, [StringComparison]::Ordinal)
}

function Resolve-UserTransition {
    param(
        [string]$Role,
        [string]$Status,
        [int]$ActiveAdminCount
    )

    if ($Role -notin @('USER', 'ADMIN') -or $Status -notin @('ACTIVE', 'SUSPENDED')) {
        return [pscustomobject]@{
            Allowed = $false
            NewRole = $Role
            NewStatus = $Status
            Reason = 'The target has an unexpected role or status.'
        }
    }

    switch ($Action) {
        'grant-admin' {
            if ($Role -eq 'ADMIN') {
                return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The target already has the ADMIN role.' }
            }
            return [pscustomobject]@{ Allowed = $true; NewRole = 'ADMIN'; NewStatus = $Status; Reason = $null }
        }
        'revoke-admin' {
            if ($Role -ne 'ADMIN') {
                return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The target does not have the ADMIN role.' }
            }
            $remainingAdmins = $ActiveAdminCount
            if ($Status -eq 'ACTIVE') {
                $remainingAdmins--
            }
            if ($remainingAdmins -lt 1) {
                return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The operation would leave no active ADMIN.' }
            }
            return [pscustomobject]@{ Allowed = $true; NewRole = 'USER'; NewStatus = $Status; Reason = $null }
        }
        'suspend' {
            if ($Status -ne 'ACTIVE') {
                return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The target is already suspended.' }
            }
            if ($Role -eq 'ADMIN') {
                $remainingAdmins = $ActiveAdminCount - 1
                if ($remainingAdmins -lt 1) {
                    return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The operation would leave no active ADMIN.' }
                }
            }
            return [pscustomobject]@{ Allowed = $true; NewRole = $Role; NewStatus = 'SUSPENDED'; Reason = $null }
        }
        'reactivate' {
            if ($Status -ne 'SUSPENDED') {
                return [pscustomobject]@{ Allowed = $false; NewRole = $Role; NewStatus = $Status; Reason = 'The target is already active.' }
            }
            return [pscustomobject]@{ Allowed = $true; NewRole = $Role; NewStatus = 'ACTIVE'; Reason = $null }
        }
    }
}

function Get-TargetState {
    param(
        [string]$Executable,
        [pscustomobject]$Configuration,
        [string]$EmailLiteral
    )

    $query = "SELECT email, user_role, status, (SELECT COUNT(*) FROM users WHERE user_role = 'ADMIN' AND status = 'ACTIVE') FROM users WHERE email = $EmailLiteral;"
    $result = Invoke-MySql -Executable $Executable -Configuration $Configuration -Query $query
    if ($result.ExitCode -ne 0) {
        Stop-Failed 'Database read failed.'
    }

    $rows = @($result.Lines | Where-Object { $_ -ne '' })
    if ($rows.Count -eq 0) {
        return $null
    }
    if ($rows.Count -gt 1) {
        Stop-Refused 'More than one user matched the supplied email.'
    }

    $fields = $rows[0].Split("`t")
    if ($fields.Count -ne 4) {
        Stop-Failed 'Database returned an unexpected result.'
    }

    return [pscustomobject]@{
        Email             = $fields[0]
        Role              = $fields[1]
        Status            = $fields[2]
        ActiveAdminCount  = [int]$fields[3]
    }
}

function Get-ActionUpdateSql {
    switch ($Action) {
        'grant-admin' {
            return "UPDATE users SET user_role = 'ADMIN', updated_at = CURRENT_TIMESTAMP(6) WHERE id = @target_id AND @lock_ok = 1 AND user_role = 'USER' AND status IN ('ACTIVE', 'SUSPENDED');"
        }
        'revoke-admin' {
            return "UPDATE users SET user_role = 'USER', updated_at = CURRENT_TIMESTAMP(6) WHERE id = @target_id AND @lock_ok = 1 AND user_role = 'ADMIN' AND status IN ('ACTIVE', 'SUSPENDED') AND @active_admins >= IF(status = 'ACTIVE', 2, 1);"
        }
        'suspend' {
            return "UPDATE users SET status = 'SUSPENDED', updated_at = CURRENT_TIMESTAMP(6) WHERE id = @target_id AND @lock_ok = 1 AND status = 'ACTIVE' AND user_role IN ('USER', 'ADMIN') AND (user_role <> 'ADMIN' OR @active_admins >= 2);"
        }
        'reactivate' {
            return "UPDATE users SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(6) WHERE id = @target_id AND @lock_ok = 1 AND status = 'SUSPENDED' AND user_role IN ('USER', 'ADMIN');"
        }
    }
}

if (-not (Test-EmailAddress $Email)) {
    Stop-Refused 'The supplied email address is malformed.'
}

$configuration = Get-MySqlConfiguration
$mysql = Get-MySqlExecutable
$emailLiteral = ConvertTo-MySqlStringLiteral $Email

$target = Get-TargetState -Executable $mysql -Configuration $configuration -EmailLiteral $emailLiteral
if ($null -eq $target) {
    Stop-Refused 'No user exists with the supplied email address.'
}

Write-Output "Target: $($target.Email)"
Write-Output "Action: $Action"
Write-Output "Current: role=$($target.Role), status=$($target.Status)"

$decision = Resolve-UserTransition -Role $target.Role -Status $target.Status -ActiveAdminCount $target.ActiveAdminCount
if (-not $decision.Allowed) {
    Stop-Refused $decision.Reason
}

Write-Output "Planned: role=$($decision.NewRole), status=$($decision.NewStatus)"

if ($DryRun) {
    Write-Output 'Dry run: no changes made.'
    exit 0
}

if ($PSBoundParameters.ContainsKey('ConfirmEmail')) {
    if (-not [string]::Equals($ConfirmEmail, $Email, [StringComparison]::Ordinal)) {
        Stop-Refused 'Confirmation did not match the target email.'
    }
} else {
    $confirmation = Read-Host 'Type the target email exactly to confirm'
    if (-not [string]::Equals($confirmation, $Email, [StringComparison]::Ordinal)) {
        Stop-Refused 'Confirmation did not match the target email.'
    }
}

$updateSql = Get-ActionUpdateSql
$query = @"
SET @lock_name = CONCAT('bb_manage_user_', MD5(DATABASE()));
SET @lock_ok = GET_LOCK(@lock_name, 10);
START TRANSACTION;
SET @target_id = NULL;
SET @old_role = NULL;
SET @old_status = NULL;
SET @active_admins = NULL;
SET @affected = 0;
SELECT id, user_role, status INTO @target_id, @old_role, @old_status FROM users WHERE email = $emailLiteral FOR UPDATE;
SELECT COUNT(*) INTO @active_admins FROM users WHERE user_role = 'ADMIN' AND status = 'ACTIVE' FOR UPDATE;
$updateSql
SET @affected = ROW_COUNT();
COMMIT;
SELECT @lock_ok, IF(@target_id IS NULL, 0, 1), IFNULL(@old_role, 'NULL'), IFNULL(@old_status, 'NULL'), IFNULL(@active_admins, -1), @affected, IFNULL(RELEASE_LOCK(@lock_name), -1);
"@

$writeResult = Invoke-MySql -Executable $mysql -Configuration $configuration -Query $query
if ($writeResult.ExitCode -ne 0) {
    $confirmedState = Get-TargetState -Executable $mysql -Configuration $configuration -EmailLiteral $emailLiteral
    if ($null -ne $confirmedState -and $confirmedState.Role -eq $decision.NewRole -and $confirmedState.Status -eq $decision.NewStatus) {
        Write-Output "Updated: role=$($confirmedState.Role), status=$($confirmedState.Status)"
        exit 0
    }
    Stop-Failed 'Database update failed and the resulting state could not be confirmed.'
}

$resultRows = @($writeResult.Lines | Where-Object { $_ -ne '' })
if ($resultRows.Count -ne 1) {
    Stop-Failed 'Database update returned an unexpected result.'
}

$fields = $resultRows[0].Split("`t")
if ($fields.Count -ne 7) {
    Stop-Failed 'Database update returned an unexpected result.'
}

$lockAcquired = $fields[0]
$targetFound = $fields[1]
$oldRole = $fields[2]
$oldStatus = $fields[3]
$activeAdminCount = [int]$fields[4]
$affected = [int]$fields[5]

if ($lockAcquired -ne '1') {
    Stop-Refused 'Could not acquire the operator lock; no changes made.'
}
if ($targetFound -ne '1') {
    Stop-Refused 'The target user no longer exists; no changes made.'
}

$writeDecision = Resolve-UserTransition -Role $oldRole -Status $oldStatus -ActiveAdminCount $activeAdminCount
if (-not $writeDecision.Allowed) {
    Stop-Refused "$($writeDecision.Reason) No changes made."
}
if ($affected -ne 1) {
    Stop-Failed 'The database did not apply exactly one update; no changes were confirmed.'
}

$newRole = $writeDecision.NewRole
$newStatus = $writeDecision.NewStatus
Write-Output "Updated: role=$newRole, status=$newStatus"
exit 0
