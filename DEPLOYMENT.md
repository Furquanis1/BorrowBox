# BorrowBox Deployment Notes

This repository includes a container-based deployment setup:

- `backend/Dockerfile` builds the Spring Boot API into a runnable jar.
- `frontend/Dockerfile` builds the Vite app and serves it with Nginx.
- `docker-compose.yml` wires MySQL, backend, and frontend together.
- Evidence photos are stored in the `media_data` Docker volume at `/app/media`.

## Quick Start

### One-click launch

**Linux / macOS:**
```bash
chmod +x run_demo.sh
./run_demo.sh
```

**Windows:**
```cmd
run_demo.bat
```

The script will:
1. Verify Docker and Docker Compose are installed and running.
2. Generate a temporary `APP_JWT_SECRET` if one is not already supplied.
3. Reuse `.borrowbox-demo-credentials`, or generate random demo-user, MySQL root,
   app-account, and E2E-account passwords when no demo database exists yet. The
   file is Git-ignored and restricted to the current user.
4. Start MySQL, create the `borrowbox_app` and `borrowbox_e2e` accounts, then
   start the backend as `borrowbox_app`.
5. Start all containers in detached mode with demo seeding explicitly enabled.
6. Poll the backend health endpoint until the API is ready.
7. Print service URLs and the credentials file path when ready.

The generated JWT secret, demo password, MySQL root password, app password, and
E2E password are for local runs only and must not be reused in production. The
launchers export the root password only to the MySQL container and bootstrap
service, the app password to the backend, and the E2E password to Cypress. The
backend and Cypress never receive the MySQL root password.

Launchers never print credentials or pass passwords as command-line arguments.
If the credentials file is missing or unreadable while a demo database still
exists, or if it lacks `MYSQL_ROOT_PASSWORD`, the launcher refuses to start rather
than silently replacing an existing database password. Restore the file or run
`--clean` (destructive) to start fresh. A launcher encountering an older
credentials file adds missing app/E2E passwords and leaves the root password
unchanged.

### Script options

| Flag       | Description                                                                    |
|------------|--------------------------------------------------------------------------------|
| *(none)*   | Start all services (reuses existing images)                                    |
| `--build`  | Force a full rebuild of all Docker images                                       |
| `--stop`   | Stop and remove all containers, keeping database, evidence, and credentials    |
| `--clean`  | Stop containers, remove database and evidence volumes, and clear credentials    |

`--clean` is destructive. It permanently deletes the demo database and every
stored evidence photo. Do not use `docker compose down -v` unless that loss is
intended. `--stop` is non-destructive and keeps both named volumes.

### Manual launch (without the script)

Compose has no default database credentials. Set the root, app, and E2E
passwords in the environment from the protected `.borrowbox-demo-credentials`
file, and set `APP_JWT_SECRET` securely, before starting Compose:

```bash
# Linux / macOS: export without printing the values
export MYSQL_ROOT_PASSWORD="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .borrowbox-demo-credentials)"
export BORROWBOX_APP_DB_PASSWORD="$(sed -n 's/^BORROWBOX_APP_DB_PASSWORD=//p' .borrowbox-demo-credentials)"
export BORROWBOX_E2E_DB_PASSWORD="$(sed -n 's/^BORROWBOX_E2E_DB_PASSWORD=//p' .borrowbox-demo-credentials)"
export APP_JWT_SECRET='<your-secret>'
docker compose up --build
```

On Windows PowerShell, set the equivalent process environment variables from the
same protected file. Do not put the values in `docker compose` arguments, tracked
files, or diagnostic output. Missing credentials make Compose fail interpolation
instead of falling back to a default. The `mysql-bootstrap` service creates the
application accounts before the backend starts.

## Service URLs

After startup:

| Service         | URL                                           |
|-----------------|-----------------------------------------------|
| Frontend        | http://localhost:3000                          |
| Backend API     | http://localhost:8080                          |
| Health Check    | http://localhost:8080/api/health               |

## Environment values used by Compose

- MySQL database: `borrowbox_v2`
- `MYSQL_ROOT_PASSWORD` (required, no default): the local launchers load or
  generate it in `.borrowbox-demo-credentials` and export it only to the MySQL
  container, healthcheck, and one-time account-bootstrap service. The backend
  never receives it. Direct Compose runs must supply it through the environment.
  Use an environment-specific secret for shared or production deployments.
- `BORROWBOX_APP_DB_PASSWORD` (required, no default): password for
  `borrowbox_app`. The launcher generates it locally, CI generates it per job,
  and deployment supplies it from the platform secret store.
- `BORROWBOX_E2E_DB_PASSWORD` (required for local Compose, no default): password
  for the DML-only `borrowbox_e2e` Cypress cleanup account.
- Spring datasource URL: `jdbc:mysql://mysql:3306/borrowbox_v2`
- Spring datasource username: `borrowbox_app`
- `SPRING_SQL_INIT_MODE`: defaults to `always` for local/CI. Production should
  apply `scripts/apply-schema.sh` with `borrowbox_migrator` and set
  `SPRING_SQL_INIT_MODE=never`, leaving the app account DML-only.
- `APP_JWT_SECRET` (required, Base64-encoded, no default): supply it securely
  through the deployment platform's secret/environment settings or an untracked
  `.env` file. Never commit it or reuse a test-only generated secret.
- `APP_JWT_COOKIE_SECURE`: set to `true` when the production deployment uses
  HTTPS. Leave it unset or `false` for local plain-HTTP stacks. It must match
  the actual topology; the supported topology is a same-origin reverse proxy
  with `SameSite=Lax`.
- `BORROWBOX_SEED_ENABLED`: defaults to `false`. Demo seeding requires the exact
  value `true` plus a non-blank `BORROWBOX_SEED_PASSWORD`.
- `BORROWBOX_SEED_PASSWORD`: no default. The local launchers generate it and store
  it in `.borrowbox-demo-credentials`; CI generates an ephemeral value. Never use
  a demo or CI password in production.
- `CYPRESS_SEED_PASSWORD`: Cypress-only local/CI value that must match the
  E2E backend's `BORROWBOX_SEED_PASSWORD`.
- `CYPRESS_DB_USER`: `borrowbox_e2e` for local/CI Cypress cleanup. The account
  has only `SELECT, INSERT, UPDATE, DELETE` on the disposable test schema.
- `CYPRESS_TEST_USER_PASSWORD`: optional per-run password for throwaway Cypress
  accounts. When unset, Cypress generates a random value at startup.
- `BORROWBOX_MEDIA_DIR`: backend evidence directory. Compose sets it to
  `/app/media`, which is backed by the `media_data` named volume.
- Persistent volumes:
  - `mysql_data` for MySQL data.
  - `media_data` for evidence photos.

## Evidence storage

Evidence image bytes live only in the `media_data` volume. MySQL stores the
evidence row and the UUID file reference, not the image bytes. The database and
media volume must always be backed up and restored together as one consistent
set.

The backend validates every upload as a real PNG or JPEG and ignores
client-supplied MIME types. Uploads are also bounded by the configured file-size,
pixel-count, and dimension limits. Existing evidence is revalidated before it is
served.

Evidence binaries are never served statically. They are available only through
the authenticated, participant-only content endpoint
`/api/evidence/{evidenceId}/content`, which enforces transaction membership in
`TransactionService`. Stored files use UUID-only names, and responses use
`nosniff`, `Cross-Origin-Resource-Policy: same-origin`, a sandbox CSP, and
`no-store` caching.

### Migrating evidence from the pre-volume container

Before this change, evidence was written inside the backend container's writable
layer. Recreating the backend container destroys it. Migrate before running the
updated Compose stack:

1. Stop the backend without removing the old container:
```bash
docker compose stop backend
```
2. Copy the old media directory to the host:
```bash
mkdir -p media-migration
docker compose cp backend:/app/media/. ./media-migration/
```
3. Recreate the backend with the new volume:
```bash
docker compose up -d --build backend
```
4. Copy the evidence into the new volume and repair ownership:
```bash
docker compose cp ./media-migration/. backend:/app/media/
docker compose exec -u root backend chown -R borrowbox:borrowbox /app/media
```
5. Verify old evidence content URLs still return `200` before removing
   `media-migration/`.

If the old backend container has already been removed and no host copy exists,
the old evidence cannot be recovered from the container.

### Verifying persistence

1. Upload evidence through the UI.
2. Restart the backend without removing volumes:
```bash
docker compose restart backend
```
3. Open the same evidence content URL. It should still return the image.
4. Confirm the media volume exists:
```bash
docker volume ls | grep media_data
```

### Confirming the backend is non-root

```bash
docker compose exec backend id
docker compose exec backend touch /app/media/.write-check
docker compose exec backend rm /app/media/.write-check
```

The `id` output should show the `borrowbox` user, and the write check should
succeed.

## MySQL credentials and existing volumes

MySQL applies `MYSQL_ROOT_PASSWORD` only when it initializes an empty data
directory. An existing named `mysql_data` volume keeps the root password stored
inside that database. Changing `MYSQL_ROOT_PASSWORD` or
`.borrowbox-demo-credentials` does **not** rotate the password in an existing
volume, and the launchers never rotate it automatically.

The `mysql-bootstrap` service runs on every local start and uses the stored root
password once to ensure `borrowbox_app` and `borrowbox_e2e` exist. It uses
`CREATE USER IF NOT EXISTS` and does not run `ALTER USER`, so it cannot silently
change a password stored in an existing volume. If an app account already exists
with a different password, the backend fails authentication; use the credential
that account already has, or perform an explicit coordinated rotation. The
backend never falls back to root.

- The launchers never delete a volume automatically. Only the explicit,
  destructive `--clean` action removes the `mysql_data` and `media_data`
  volumes and the credential file.
- For a demo volume created before this change, set `MYSQL_ROOT_PASSWORD` in
  `.borrowbox-demo-credentials` to the root password that volume already uses.
  If the value is still available in a local credential file, use it once to
  rotate the database; do not copy exposed values into new documentation or
  share them. Do not generate a new value for an existing database and expect it
  to work.
- If the existing password is unknown, recover it from a backup. If the demo data
  is disposable, `--clean` is the explicit reset path; it deletes the database
  and evidence volumes and all data in them.
- Volumes created before `MYSQL_ROOT_HOST="%"` was added may not allow the
  bootstrap container's remote root login. If bootstrap cannot authenticate and
  the demo data is disposable, use `--clean`; otherwise provision the accounts
  from an administrator session that can connect locally.
- To rotate deliberately, first set the current password in the credential file.
  Start the stack or MySQL alone with that value, then run an interactive MySQL
  client (for example `docker compose exec mysql mysql -uroot -p`, which prompts
  for the current password without exposing it in the command line). Change the
  root password with a deliberate `ALTER USER` statement, update
  `.borrowbox-demo-credentials` to the new value, and restart. Do not automate
  rotation or run it against a database you do not own.

A password mismatch normally appears as an unhealthy MySQL container, an
`Access denied` log, or backend connection failures. The stack does not silently
fall back to a default password.

## User administration (ADMIN and suspension)

The application deliberately seeds no administrator and its registration and
admin-user APIs always create `USER` accounts. Use the operator script
`scripts/manage-user.ps1` to grant or revoke the `ADMIN` role and to suspend or
reactivate accounts.

Startup and demo seeding never delete, suspend, or rotate the password of an
existing account. If demo accounts must be retired, suspend them explicitly with
`scripts/manage-user.ps1`; do not rely on reseeding or a startup hook to remove
them.

The script requires the same database settings as the backend and the MySQL CLI
(`mysql`) on `PATH`. Run it from the repository root on the host, not from inside
the backend container. For the Compose stack, the host-published MySQL port is
`3307`, so the operator URL is:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3307/borrowbox_v2'
$env:SPRING_DATASOURCE_USERNAME = '<operator-db-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<operator-db-password>'
```

Use a database account limited to the operations the script needs:
`SELECT` and `UPDATE` on `users`. Do not place the password in the script or
commit it to the repository.

### First administrator

1. Register the intended administrator through the application.
2. Preview the change:

   ```powershell
   .\scripts\manage-user.ps1 -Action grant-admin -Email admin@example.com -DryRun
   ```

3. Apply it:

   ```powershell
   .\scripts\manage-user.ps1 -Action grant-admin -Email admin@example.com -ConfirmEmail admin@example.com
   ```

4. Verify that the account can access an administrator-only endpoint, such as
   `GET /api/users`.

### Supported actions

| Action | Required current state | Result |
| --- | --- | --- |
| `grant-admin` | `USER` (active or suspended) | role becomes `ADMIN`; status is unchanged |
| `revoke-admin` | `ADMIN` (active or suspended) | role becomes `USER`; status is unchanged |
| `suspend` | `ACTIVE` | status becomes `SUSPENDED`; role is unchanged |
| `reactivate` | `SUSPENDED` | status becomes `ACTIVE`; role is unchanged |

The script refuses malformed or unknown emails, redundant changes, unexpected
stored role/status values, and any change that would leave no active
administrator. Suspension takes effect immediately for existing JWT sessions;
reactivation restores access without requiring a new token. Role changes also
take effect on the next request.

Always use `-DryRun` first. Without `-DryRun`, the script prompts for the target
email unless `-ConfirmEmail` is supplied. `-ConfirmEmail` must exactly match
`-Email`.

Exit codes:

- `0`: completed successfully, or a dry run succeeded.
- `1`: configuration, database, or unexpected failure. The resulting state may
  be unknown; inspect the account before retrying.
- `2`: refused; no change was made.

Administrator changes are serialized with a database advisory lock and applied
in a transaction. The last-active-administrator check is repeated inside that
transaction, so two concurrent operators cannot remove or suspend the final
active administrator. If the lock cannot be acquired within 10 seconds, the
script refuses without changing data.

Do not perform role or status changes with ad-hoc SQL. The PowerShell script is
the supported operator interface because it preserves the last-active-
administrator invariant and provides the required serialization. Manual SQL
updates are unsupported.

## Production database migration

Production must not run schema DDL through the application account. Provision two
database roles:

- `borrowbox_app`: `SELECT, INSERT, UPDATE, DELETE` on the application schema.
- `borrowbox_migrator`: `CREATE, ALTER, INDEX, REFERENCES` on the application
  schema.

Before starting or replacing the application, apply the version-controlled schema:

```bash
MYSQL_HOST=<db-host> \
MYSQL_PORT=3306 \
MYSQL_DATABASE=borrowbox_v2 \
MYSQL_MIGRATION_USER=borrowbox_migrator \
MYSQL_MIGRATION_PASSWORD='<from-secret-store>' \
bash scripts/apply-schema.sh
```

Then set `SPRING_SQL_INIT_MODE=never` on the application service. Startup still
uses `ddl-auto=validate`, so the migration must complete successfully first. Keep
the migration and root credentials out of the application environment.

## Backup and restore

Create a timestamped backup directory under the Git-ignored `backups/` folder.
Back up MySQL and media at the same time so evidence metadata and files stay
consistent. Run these commands from the repository root with the same Compose
environment exported for a manual launch. Stop the backend first to prevent
uploads from changing either store during the backup.

The Linux/macOS blocks use Bash. The Windows blocks use native PowerShell and keep
dump/restore redirection inside the MySQL container, so PowerShell never
redirects Docker output and binary data is not corrupted. The MySQL commands use
the root password already present in the MySQL container environment through
`MYSQL_PWD`; the value is never printed or placed on the command line as an
argument.

### Backup

**Linux / macOS (Bash):**

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="backups/$STAMP"
mkdir -p "$BACKUP_DIR/media"
docker compose stop backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers borrowbox_v2 > /tmp/borrowbox-db.sql'
docker compose cp mysql:/tmp/borrowbox-db.sql "$BACKUP_DIR/borrowbox-db.sql"
docker compose exec -T mysql rm -f /tmp/borrowbox-db.sql
docker compose cp backend:/app/media/. "$BACKUP_DIR/media"
docker compose start backend
```

**Windows (PowerShell):**

```powershell
$STAMP = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path "backups" $STAMP
$mediaDir = Join-Path $backupDir "media"
New-Item -ItemType Directory -Path $mediaDir -Force | Out-Null
docker compose stop backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --result-file=/tmp/borrowbox-db.sql borrowbox_v2'
docker compose cp mysql:/tmp/borrowbox-db.sql "$backupDir/borrowbox-db.sql"
docker compose exec -T mysql rm -f /tmp/borrowbox-db.sql
docker compose cp backend:/app/media/. "$mediaDir"
docker compose start backend
```

The `sh -c` argument runs inside the MySQL container so the root password is
never placed on the Windows command line. The dump is written inside the
container with `--result-file`, then copied to Windows with `docker compose cp`.

### Restore

Restore both files from the same backup set. Keep the backend stopped until the
database is restored, the media files are copied into the volume, and ownership
has been repaired. The one-off `docker compose run` command mounts `media_data`
and repairs ownership without starting the backend service.

**Linux / macOS (Bash):**

```bash
docker compose stop backend
docker compose cp backups/BACKUP-STAMP/borrowbox-db.sql mysql:/tmp/borrowbox-db.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot borrowbox_v2 < /tmp/borrowbox-db.sql'
docker compose exec -T mysql rm -f /tmp/borrowbox-db.sql
docker compose cp backups/BACKUP-STAMP/media/. backend:/app/media/
docker compose run --rm --no-deps --entrypoint sh -u root backend -c 'chown -R borrowbox:borrowbox /app/media'
docker compose start backend
```

**Windows (PowerShell):**

```powershell
$STAMP = Read-Host -Prompt "Enter backup stamp (yyyyMMdd-HHmmss)"
$backupDir = Join-Path "backups" $STAMP
docker compose stop backend
docker compose cp "$backupDir/borrowbox-db.sql" mysql:/tmp/borrowbox-db.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot borrowbox_v2 < /tmp/borrowbox-db.sql'
docker compose exec -T mysql rm -f /tmp/borrowbox-db.sql
docker compose cp "$backupDir/media/." backend:/app/media/
docker compose run --rm --no-deps --entrypoint sh -u root backend -c 'chown -R borrowbox:borrowbox /app/media'
docker compose start backend
```

The `<` redirection runs inside the MySQL container, not in PowerShell. These
commands assume the backend container still exists; do not remove it with
`docker compose down` before the restore. After starting the backend, verify a
known evidence content URL and the backend health endpoint.

## Production readiness checklist

- [x] API has validation, error handling, and search endpoints.
- [x] Frontend has item, borrow request, and borrow record flows.
- [x] Integration tests cover the borrow workflow against MySQL.
- [x] Docker Compose provides a reproducible local deployment stack.
- [x] One-click demo scripts for Linux/macOS and Windows.
- [x] Compose has no default database credentials; values come from a protected
  launcher-managed file or deployment environment.
- [x] Backend and Cypress use dedicated least-privilege accounts, never root.
- [x] The app account can be DML-only in production with a separate migrator.
- [x] Evidence photos persist in the `media_data` named volume.
- [x] Evidence uploads are validated as real PNG/JPEG and served through the
  participant-only content endpoint with hardened headers.
- [ ] Supply environment-specific MySQL app/migration passwords and a production
  `APP_JWT_SECRET`, and set `APP_JWT_COOKIE_SECURE=true` for HTTPS deployments.
- [ ] Configure `CI_MYSQL_ROOT_PASSWORD` in GitHub Actions secrets for the CI
  build and E2E workflows.
- [ ] Rotate any credential exposed in Git history or logs before production use.
- [ ] Add HTTPS / reverse-proxy configuration for real production hosting.
- [ ] Add scheduled combined MySQL + media backups.
- [ ] Add cloud-specific deployment manifests if deploying beyond local containers.

## Notes

- The frontend container uses Nginx to proxy `/api` to the backend container.
- The backend container reads datasource settings from environment variables and
  connects as `borrowbox_app`, never as root.
- The database is initialized by the MySQL image and persisted in the
  `mysql_data` named volume. Its stored root password does not change when the
  credential file changes.
- The mysql-bootstrap service creates app/E2E accounts with idempotent
  `CREATE USER IF NOT EXISTS`; it never silently rotates existing passwords.
- The demo scripts wait up to 120 seconds for the backend health endpoint before
  timing out.
- The backend runs as the non-root `borrowbox` user and owns `/app/media`.
- Evidence bytes are persisted in `media_data`; the database stores only the
  evidence row and UUID file reference.
