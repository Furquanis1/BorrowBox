#!/usr/bin/env bash
#
# Apply the version-controlled schema to a deployed database using a dedicated
# migration account. Run this before starting the application when
# SPRING_SQL_INIT_MODE=never is used in production.
#
# Required environment:
#   MYSQL_MIGRATION_PASSWORD
#
# Optional environment:
#   MYSQL_HOST (default: 127.0.0.1)
#   MYSQL_PORT (default: 3306)
#   MYSQL_DATABASE (default: borrowbox_v2)
#   MYSQL_MIGRATION_USER (default: borrowbox_migrator)
#   SCHEMA_DIR (default: backend/src/main/resources)
#
set -euo pipefail

MYSQL_CMD="${MYSQL_CMD:-mysql}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-borrowbox_v2}"
MYSQL_MIGRATION_USER="${MYSQL_MIGRATION_USER:-borrowbox_migrator}"
MYSQL_MIGRATION_PASSWORD="${MYSQL_MIGRATION_PASSWORD:-}"
SCHEMA_DIR="${SCHEMA_DIR:-backend/src/main/resources}"

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

escape_option_value() {
    local value="$1"
    if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
        fail "MySQL credentials containing CR or LF are not supported."
    fi
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "$value"
}

require_value MYSQL_MIGRATION_PASSWORD
validate_identifier "$MYSQL_DATABASE" "MYSQL_DATABASE"
validate_identifier "$MYSQL_MIGRATION_USER" "MYSQL_MIGRATION_USER"

if ! [[ "$MYSQL_PORT" =~ ^[0-9]+$ ]] || [ "$MYSQL_PORT" -lt 1 ] || [ "$MYSQL_PORT" -gt 65535 ]; then
    fail "MYSQL_PORT must be between 1 and 65535."
fi

if ! command -v "$MYSQL_CMD" >/dev/null 2>&1; then
    fail "mysql client not found in PATH."
fi

for schema_file in schema.sql schema-patch.sql; do
    if [ ! -f "$SCHEMA_DIR/$schema_file" ]; then
        fail "Schema file not found: $SCHEMA_DIR/$schema_file"
    fi
done

option_file="$(mktemp "${TMPDIR:-/tmp}/borrowbox-schema.XXXXXX")"
cleanup() {
    if [ -n "${option_file:-}" ] && [ -f "$option_file" ]; then
        rm -f "$option_file"
    fi
}
trap cleanup EXIT INT TERM

chmod 600 "$option_file"
{
    echo "[client]"
    echo "user=\"$(escape_option_value "$MYSQL_MIGRATION_USER")\""
    echo "password=\"$(escape_option_value "$MYSQL_MIGRATION_PASSWORD")\""
    echo "host=\"$(escape_option_value "$MYSQL_HOST")\""
    echo "port=$MYSQL_PORT"
    echo "protocol=TCP"
    echo "connect-timeout=10"
    echo "default-character-set=utf8mb4"
} > "$option_file"

for schema_file in schema.sql schema-patch.sql; do
    echo "[INFO] Applying $schema_file to $MYSQL_DATABASE..."
    "$MYSQL_CMD" --defaults-file="$option_file" "$MYSQL_DATABASE" < "$SCHEMA_DIR/$schema_file"
done

echo "[OK] Schema applied to $MYSQL_DATABASE."
