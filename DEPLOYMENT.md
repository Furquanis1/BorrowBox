# BorrowBox Deployment Notes

This repository now includes a simple container-based deployment setup:

- `backend/Dockerfile` builds the Spring Boot API into a runnable jar.
- `frontend/Dockerfile` builds the Vite app and serves it with Nginx.
- `docker-compose.yml` wires MySQL, backend, and frontend together.

## Local container run

```bash
docker compose up --build
```

After startup:

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html

## Environment values used by Compose

- MySQL database: `borrowbox_db`
- MySQL root password: `rootpassword`
- Spring datasource URL: `jdbc:mysql://mysql:3306/borrowbox_db`

## Production readiness checklist

- [x] API has validation, error handling, and search endpoints.
- [x] Frontend has item, borrow request, and borrow record flows.
- [x] Integration tests cover the borrow workflow against MySQL.
- [x] Docker Compose provides a reproducible local deployment stack.
- [ ] Replace sample MySQL credentials with environment-specific secrets.
- [ ] Add HTTPS / reverse-proxy configuration for real production hosting.
- [ ] Add cloud-specific deployment manifests if deploying beyond local containers.

## Notes

- The frontend container uses Nginx to proxy `/api` to the backend container.
- The backend container reads datasource settings from environment variables.
- The database is initialized by the MySQL image and persisted in a named volume.
