# GitHub Secrets Configuration

This document lists the repository secrets required by the CI/CD pipelines
in `.github/workflows/`.

## Required Secrets

Configure these in **Settings → Secrets and variables → Actions → New repository secret**.

### CI Pipeline (`build.yml` and `e2e.yml`)

| Secret Name                | Used By                     | Description |
|----------------------------|-----------------------------|-------------|
| `CI_MYSQL_ROOT_PASSWORD`   | `build.yml`, `e2e.yml`      | Root password for the disposable MySQL service container and the account-bootstrap step. It is CI-only and must be rotated after any suspected exposure. |

Configure `CI_MYSQL_ROOT_PASSWORD` in **Settings → Secrets and variables → Actions**.
The service container needs the value before any workflow step runs, so it cannot
be generated inside the job.

Each job generates ephemeral values for:

- `BORROWBOX_APP_DB_PASSWORD`
- `BORROWBOX_E2E_DB_PASSWORD`
- `BORROWBOX_SEED_PASSWORD`
- `APP_JWT_SECRET`

The workflow masks these values with `::add-mask::`, bootstraps the
`borrowbox_app` and `borrowbox_e2e` accounts, and never gives the application or
Cypress the root password. These generated test secrets must never be reused for
a production deployment.

### Deployment Pipeline (`deploy.yml`)

| Secret Name              | Provider | Description                                                        | Required |
|--------------------------|----------|--------------------------------------------------------------------|----------|
| `RAILWAY_API_TOKEN`      | Railway  | API token from [railway.app/account/tokens](https://railway.app/account/tokens) | Optional |
| `RENDER_DEPLOY_HOOK_URL` | Render   | Deploy hook URL from Render dashboard → Service → Settings → Deploy Hook | Optional |
| `DOCKERHUB_USERNAME`     | Docker   | Docker Hub username for publishing container images                 | Optional |
| `DOCKERHUB_TOKEN`        | Docker   | Docker Hub access token (not your password) from hub.docker.com/settings/security | Optional |

> **Note:** Deployment jobs skip gracefully with an informational notice if the
> corresponding secrets are not configured. The CI build and E2E jobs still
> require `CI_MYSQL_ROOT_PASSWORD`; only the deployment secrets are optional.

## How to Obtain Each Secret

### Railway API Token

1. Sign up or log in at [railway.app](https://railway.app).
2. Navigate to **Account Settings → Tokens**.
3. Generate a new token with a descriptive name (e.g., `borrowbox-deploy`).
4. Copy the token and add it as `RAILWAY_API_TOKEN` in GitHub Secrets.

### Render Deploy Hook

1. Sign up or log in at [render.com](https://render.com).
2. Create a new **Web Service** pointing to this repository.
3. Go to **Settings → Deploy Hook** and copy the URL.
4. Add it as `RENDER_DEPLOY_HOOK_URL` in GitHub Secrets.

### Docker Hub Credentials

1. Sign up or log in at [hub.docker.com](https://hub.docker.com).
2. Go to **Account Settings → Security → Access Tokens**.
3. Create a new access token with **Read & Write** scope.
4. Add your username as `DOCKERHUB_USERNAME` and the token as `DOCKERHUB_TOKEN`.

## Environment Variables

The following environment variables are used at runtime by Docker Compose and
can be overridden for different deployment environments:

| Variable                         | Value / Default                        | Used By |
|----------------------------------|----------------------------------------|---------|
| `SPRING_DATASOURCE_URL`         | Compose: `jdbc:mysql://mysql:3306/borrowbox_v2`; CI: `.../borrowbox_db` | Backend |
| `SPRING_DATASOURCE_USERNAME`    | `borrowbox_app`                        | Backend |
| `SPRING_DATASOURCE_PASSWORD`    | App password from `BORROWBOX_APP_DB_PASSWORD` | Backend |
| `SPRING_SQL_INIT_MODE`          | `always` locally/CI; `never` in production after migration | Backend |
| `APP_JWT_SECRET`                | *(required; no default)*                | Backend |
| `APP_JWT_COOKIE_SECURE`         | `false` locally; `true` for HTTPS       | Backend |
| `BORROWBOX_SEED_ENABLED`        | `false`                                | Backend |
| `BORROWBOX_SEED_PASSWORD`       | *(required only when seeding is enabled)* | Backend |
| `CYPRESS_SEED_PASSWORD`         | *(test-only; must match backend)*       | Cypress |
| `CYPRESS_DB_USER`               | `borrowbox_e2e`                        | Cypress |
| `CYPRESS_DB_PASSWORD`           | E2E password from `BORROWBOX_E2E_DB_PASSWORD` | Cypress |
| `CYPRESS_TEST_USER_PASSWORD`    | *(per-run generated if unset)*          | Cypress |
| `MYSQL_DATABASE`                | `borrowbox_v2` locally; `borrowbox_db` in CI | MySQL |
| `MYSQL_ROOT_PASSWORD`           | *(required; launcher/CI-managed)*       | MySQL container and bootstrap only |
| `BORROWBOX_APP_DB_PASSWORD`     | *(required; launcher/CI-managed)*       | App account bootstrap and backend |
| `BORROWBOX_E2E_DB_PASSWORD`     | *(required; launcher/CI-managed)*       | E2E account bootstrap and Cypress |
| `MYSQL_MIGRATION_PASSWORD`      | *(production migration only)*           | `scripts/apply-schema.sh` |

> **Warning:** Replace all default credentials before deploying to any public environment.
> Compose has no MySQL credential defaults. The demo launchers load or generate
> the values in the protected `.borrowbox-demo-credentials` file; direct Compose
> runs must supply them in the environment. The application and Cypress must never
> receive the MySQL root password.

CI writes generated app/E2E/seed/JWT values to `$GITHUB_ENV`; later steps read
them from the process environment. The workflows do not use the `env` context for
values generated during the run.

### Backend runtime secret

The backend requires `APP_JWT_SECRET`, a Base64-encoded signing secret, and has
no committed default. Supply it through the deployment platform's secret or
environment configuration (for example, Railway/Render service variables) or an
untracked local `.env` file. Never commit it, and never reuse a test-only secret
generated by CI or the demo scripts in production.

When the production deployment uses HTTPS, set `APP_JWT_COOKIE_SECURE=true`.
Leave it unset or `false` for local plain-HTTP stacks. The cookie setting must
match the actual deployment topology; this repository targets a same-origin
reverse proxy with `SameSite=Lax`.

### Production database migration

Do not grant the production application account DDL privileges. Apply
`backend/src/main/resources/schema.sql` and `schema-patch.sql` with a dedicated
`borrowbox_migrator` account before releasing the application:

```bash
MYSQL_HOST=<db-host> \
MYSQL_DATABASE=borrowbox_v2 \
MYSQL_MIGRATION_USER=borrowbox_migrator \
MYSQL_MIGRATION_PASSWORD='<from-secret-store>' \
bash scripts/apply-schema.sh
```

Then set `SPRING_SQL_INIT_MODE=never` for the application service. The app account
should have only `SELECT, INSERT, UPDATE, DELETE` on the application schema.

### Credential exposure response

If any database password, seed password, JWT secret, CI secret, or operator
credential is exposed, treat it as compromised and rotate it:

- Rotate `CI_MYSQL_ROOT_PASSWORD` in GitHub Actions settings; CI service
  containers are disposable, so a new value is safe there.
- Rotate the production `APP_JWT_SECRET` to invalidate existing sessions, then
  rotate the app and migration database passwords with an explicit `ALTER USER`.
- Rotate the E2E and seed passwords used by any shared test environment.
- Rotate any operator credentials used by `scripts/manage-user.ps1`.
- Remove exposed values from logs and local copies. Changing a file does not
  remove values from Git history; history rewriting requires explicit owner
  approval and coordinated force-pushes, and is not part of routine remediation.
- Review database and application logs for unauthorized use before rotating.
