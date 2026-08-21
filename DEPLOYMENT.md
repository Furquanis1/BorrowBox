# BorrowBox Deployment Notes

This repository includes a container-based deployment setup:

- `backend/Dockerfile` builds the Spring Boot API into a runnable jar.
- `frontend/Dockerfile` builds the Vite app and serves it with Nginx.
- `docker-compose.yml` wires MySQL, backend, and frontend together.

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
2. Start all containers in detached mode.
3. Poll the backend health endpoint until the API is ready.
4. Print service URLs when ready.

### Script options

| Flag       | Description                                       |
|------------|---------------------------------------------------|
| *(none)*   | Start all services (reuses existing images)       |
| `--build`  | Force a full rebuild of all Docker images          |
| `--stop`   | Stop and remove all containers                     |
| `--clean`  | Stop containers **and** remove database volumes    |

### Manual launch (without the script)

```bash
docker compose up --build
```

## Service URLs

After startup:

| Service         | URL                                           |
|-----------------|-----------------------------------------------|
| Frontend        | http://localhost:3000                          |
| Backend API     | http://localhost:8080                          |
| Health Check    | http://localhost:8080/api/health               |

## Environment values used by Compose

- MySQL database: `borrowbox_db`
- MySQL root password: `rootpassword`
- Spring datasource URL: `jdbc:mysql://mysql:3306/borrowbox_db`

## Production readiness checklist

- [x] API has validation, error handling, and search endpoints.
- [x] Frontend has item, borrow request, and borrow record flows.
- [x] Integration tests cover the borrow workflow against MySQL.
- [x] Docker Compose provides a reproducible local deployment stack.
- [x] One-click demo scripts for Linux/macOS and Windows.
- [ ] Replace sample MySQL credentials with environment-specific secrets.
- [ ] Add HTTPS / reverse-proxy configuration for real production hosting.
- [ ] Add cloud-specific deployment manifests if deploying beyond local containers.

## Notes

- The frontend container uses Nginx to proxy `/api` to the backend container.
- The backend container reads datasource settings from environment variables.
- The database is initialized by the MySQL image and persisted in a named volume.
- The demo scripts wait up to 120 seconds for the backend health endpoint before timing out.
