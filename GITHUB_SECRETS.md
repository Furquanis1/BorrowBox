# GitHub Secrets Configuration

This document lists the repository secrets required by the CI/CD pipelines
in `.github/workflows/`.

## Required Secrets

Configure these in **Settings → Secrets and variables → Actions → New repository secret**.

### CI Pipeline (`build.yml`)

The build workflow uses GitHub-provided MySQL service containers and does not
require any custom secrets. It runs automatically on push and pull request events.

### Deployment Pipeline (`deploy.yml`)

| Secret Name              | Provider | Description                                                        | Required |
|--------------------------|----------|--------------------------------------------------------------------|----------|
| `RAILWAY_API_TOKEN`      | Railway  | API token from [railway.app/account/tokens](https://railway.app/account/tokens) | Optional |
| `RENDER_DEPLOY_HOOK_URL` | Render   | Deploy hook URL from Render dashboard → Service → Settings → Deploy Hook | Optional |
| `DOCKERHUB_USERNAME`     | Docker   | Docker Hub username for publishing container images                 | Optional |
| `DOCKERHUB_TOKEN`        | Docker   | Docker Hub access token (not your password) from hub.docker.com/settings/security | Optional |

> **Note:** Deployment jobs will skip gracefully with an informational notice if the
> corresponding secrets are not configured. No builds will fail due to missing secrets.

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

| Variable                         | Default Value                          | Used By  |
|----------------------------------|----------------------------------------|----------|
| `SPRING_DATASOURCE_URL`         | `jdbc:mysql://mysql:3306/borrowbox_db` | Backend  |
| `SPRING_DATASOURCE_USERNAME`    | `root`                                 | Backend  |
| `SPRING_DATASOURCE_PASSWORD`    | `rootpassword`                         | Backend  |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update`                               | Backend  |
| `MYSQL_DATABASE`                | `borrowbox_db`                         | MySQL    |
| `MYSQL_ROOT_PASSWORD`           | `rootpassword`                         | MySQL    |

> **Warning:** Replace all default credentials before deploying to any public environment.
