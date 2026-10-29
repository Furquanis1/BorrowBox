#!/usr/bin/env bash
#
# Create the local/CI BorrowBox application accounts and grants.
#
# This script is intentionally not a production account-provisioning tool. It
# creates the app account with schema-scoped DDL because local and CI startup
# still run schema.sql and schema-patch.sql. Production should use a DML-only
# app account and apply schema changes with the migration account.
#
# Required environment:
#   MYSQL_ROOT_PASSWORD
#   BORROWBOX_APP_DB_PASSWORD
#   BORROWBOX_E2E_DB_PASSWORD
#
# Optional environment:
#   MYSQL_HOST (default: mysql)
#   MYSQL_PORT (default: 3306)
#   MYSQL_ROOT_USER (default: root)
#   MYSQL_DATABASE (default: borrowbox_v2)
#   BORROWBOX_APP_DB_USER (default: borrowbox_app)
#   BORROWBOX_E2E_DB_USER (default: borrowbox_e2e)
#
set -euo pipefail

MYSQL_CMD="${MYSQL_CMD:-mysql}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-borrowbox_v2}"
APP_USER="${BORROWBOX_APP_DB_USER:-borrowbox_app}"
E2E_USER="${BORROWBOX_E2E_DB_USER:-borrowbox_e2e}"

fail() {
    echo "[ERROR] $*" >&2
    exit 1
}

require_value() {
    local name="$1"
    if [ -z "${!name:-}" ]; then
        fail "Set $name before running this script."
    fi
}

validate_identifier() {
    local value="$1"
    local name="$2"
    if ! [[ "$value" =~ ^[A-Za-z0-9_]+$ ]]; then
        fail "$name must contain only letters, digits, and underscores."
    fi
}

validate_port() {
    if ! [[ "$MYSQL_PORT" =~ ^[0-9]+$ ]]; then
        fail "MYSQL_PORT must be numeric."
    fi
    if [ "$MYSQL_PORT" -lt 1 ] || [ "$MYSQL_PORT" -gt 65535 ]; then
        fail "MYSQL_PORT must be between 1 and 65535."
    fi
}

validate_sql_password() {
    local value="$1"
    local name="$2"
    local pattern='^[A-Za-z0-9._~!@#$%^&*()+=:,-]+$'
    # Generated launcher/CI passwords are URL-safe. Reject quotes and backslashes
    # so the account DDL can never depend on MySQL escape-mode settings.
    if ! [[ "$value" =~ $pattern ]]; then
        fail "$name contains characters that are not supported by this bootstrap script."
    fi
}

escape_option_value() {
    local value="$1"
    if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
        fail "MySQL credentials containing CR or LF are not supported."
    fi
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "$value"
}

require_value MYSQL_ROOT_PASSWORD
require_value BORROWBOX_APP_DB_PASSWORD
require_value BORROWBOX_E2E_DB_PASSWORD
validate_identifier "$MYSQL_DATABASE" "MYSQL_DATABASE"
validate_identifier "$MYSQL_ROOT_USER" "MYSQL_ROOT_USER"
validate_identifier "$APP_USER" "BORROWBOX_APP_DB_USER"
validate_identifier "$E2E_USER" "BORROWBOX_E2E_DB_USER"
validate_port
validate_sql_password "$BORROWBOX_APP_DB_PASSWORD" "BORROWBOX_APP_DB_PASSWORD"
validate_sql_password "$BORROWBOX_E2E_DB_PASSWORD" "BORROWBOX_E2E_DB_PASSWORD"

if ! command -v "$MYSQL_CMD" >/dev/null 2>&1; then
    fail "mysql client not found in PATH."
fi

option_file="$(mktemp "${TMPDIR:-/tmp}/borrowbox-bootstrap.XXXXXX")"
cleanup() {
    if [ -n "${option_file:-}" ] && [ -f "$option_file" ]; then
        rm -f "$option_file"
    fi
}
trap cleanup EXIT INT TERM

chmod 600 "$option_file"
{
    echo "[client]"
    echo "user=\"$(escape_option_value "$MYSQL_ROOT_USER")\""
    echo "password=\"$(escape_option_value "$MYSQL_ROOT_PASSWORD")\""
    echo "host=\"$(escape_option_value "$MYSQL_HOST")\""
    echo "port=$MYSQL_PORT"
    echo "protocol=TCP"
    echo "connect-timeout=10"
    echo "default-character-set=utf8mb4"
} > "$option_file"

ready=0
for _ in $(seq 1 30); do
    if "$MYSQL_CMD" --defaults-file="$option_file" --batch --skip-column-names \
        --execute="SELECT 1" >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 1
done

if [ "$ready" -ne 1 ]; then
    fail "MySQL is not reachable at $MYSQL_HOST:$MYSQL_PORT, or the root login was rejected."
fi

"$MYSQL_CMD" --defaults-file="$option_file" <<SQL
CREATE DATABASE IF NOT EXISTS \`$MYSQL_DATABASE\`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '$APP_USER'@'%' IDENTIFIED BY '$BORROWBOX_APP_DB_PASSWORD';
ALTER USER '$APP_USER'@'%' IDENTIFIED BY '$BORROWBOX_APP_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON \`$MYSQL_DATABASE\`.* TO '$APP_USER'@'%';

CREATE USER IF NOT EXISTS '$E2E_USER'@'%' IDENTIFIED BY '$BORROWBOX_E2E_DB_PASSWORD';
ALTER USER '$E2E_USER'@'%' IDENTIFIED BY '$BORROWBOX_E2E_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE
    ON \`$MYSQL_DATABASE\`.* TO '$E2E_USER'@'%';
SQL

echo "[OK] Database accounts ready in $MYSQL_DATABASE (app: $APP_USER, e2e: $E2E_USER)."
