# BorrowBox 📦

> A full-stack platform for sharing and tracking physical items within groups and workspaces.

[![Build and Test](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml/badge.svg)](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml)

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen?logo=springboot)
![React](https://img.shields.io/badge/React-18.2-blue?logo=react)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)

---

## 🌐 Live Demo

> 🔗 Coming soon — will be deployed to Railway.

---

## 📖 What is BorrowBox?

BorrowBox helps communities manage shared physical assets — think university equipment rooms, office hardware libraries, or neighborhood tool-sharing groups. Instead of spreadsheets and informal tracking, BorrowBox provides a structured workflow:

1. **Add items** to a shared group inventory.
2. **Request to borrow** any available item.
3. **Approve or reject** borrow requests.
4. **Track active loans** and get visibility on overdue items.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Database | MySQL 8.0 |
| Authentication | JWT (HttpOnly Cookie) |
| Frontend | React 18.2, Vite 5 |
| API Docs | Springdoc OpenAPI / Swagger UI |
| DevOps | Docker, Docker Compose, Nginx |
| CI/CD | GitHub Actions ([build.yml](.github/workflows/build.yml), [deploy.yml](.github/workflows/deploy.yml)) |

---

## 🚀 Quick Start (Docker — Recommended)

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.
- [Git](https://git-scm.com/) installed.

### 1. Clone the repository
```bash
git clone https://github.com/<your-username>/BorrowBox.git
cd BorrowBox
```

### 2. Start all services
```bash
docker compose up -d --build
```

This starts three containers:
- `borrowbox-mysql` — MySQL 8.0 database on port `3307`
- `borrowbox-backend` — Spring Boot REST API on port `8080`
- `borrowbox-frontend` — React app served by Nginx on port `3000`

### 3. Open the app
| Service | URL |
|---------|-----|
| Frontend (React UI) | http://localhost:3000 |
| Backend Health Check | http://localhost:8080/api/health |
| Swagger API Docs | http://localhost:8080/swagger-ui.html |

### 4. Stop all services
```bash
docker compose down
```

---

## 💻 Local Development

If you want to run the frontend with hot-reload while the backend runs in Docker:

```bash
# Terminal 1 – start backend + database
docker compose up -d mysql backend

# Terminal 2 – start Vite dev server
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` for the live-reload dev build.

---

## 📁 Project Structure

```
BorrowBox/
├── backend/                  # Spring Boot REST API (Java 21)
│   ├── src/main/java/com/borrowbox/
│   │   ├── controller/       # REST endpoints
│   │   ├── service/          # Business logic
│   │   ├── repository/       # Spring Data JPA
│   │   ├── entity/           # JPA entities (User, Item, Group …)
│   │   ├── dto/              # Request/Response DTOs
│   │   ├── security/         # JWT filter + Spring Security config
│   │   └── exception/        # Global error handling
│   └── Dockerfile
├── frontend/                 # React 18 + Vite SPA
│   ├── src/
│   │   ├── pages/            # Route-level page components
│   │   ├── components/       # Reusable UI components
│   │   ├── contexts/         # React context (global state)
│   │   └── utils/            # Helper utilities
│   ├── nginx.conf
│   └── Dockerfile
└── docker-compose.yml        # Orchestrates all three services
```

---

## 🔑 Default Credentials (Development Only)

> ⚠️ Change these before any public deployment.

The database is seeded on first run. Register a new account via the UI at `http://localhost:3000`.

---

## 🔐 Security Notes

- Authentication uses **JWT stored in HttpOnly cookies** (XSS-safe).
- All API endpoints are secured except `/api/auth/**` and `/api/health`.
- Secrets are managed via environment variables in production.

---

## 📄 License

MIT © BorrowBox Contributors