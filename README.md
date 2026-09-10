# BorrowBox

> A full-stack community-based platform for sharing and tracking physical assets across bounded communities.

[![Build and Test](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml/badge.svg)](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen?logo=springboot)
![React](https://img.shields.io/badge/React-18.2-blue?logo=react)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)
![Cypress](https://img.shields.io/badge/Cypress-54_Tests_Passing-green?logo=cypress)

---

## 📸 Preview

![BorrowBox Dashboard Dark Mode](docs/assets/borrowbox-dashboard-dark.jpg)

---

## 🌐 Live Demo & Deployment Status

BorrowBox is fully containerized and ready for local or cloud deployment.
- **Local Demonstration:** Ready out-of-the-box using one-click launch scripts (`run_demo.bat` / `run_demo.sh`) or `docker compose up -d --build`.
- **Cloud Deployment:** GitHub Actions CI/CD pipeline ([deploy.yml](.github/workflows/deploy.yml)) is configured for Railway and Render deployment hooks when repository secrets are provided.

---

## What is BorrowBox?

BorrowBox is a community-based asset-sharing platform. Users own physical assets independently of communities, then selectively expose them to specific communities via CommunityListings. Community members discover available assets through community-scoped Explore. A single owned asset pool shared across multiple communities always shows one consistent shared physical inventory state.

V2.1 (Community + Ownership Foundation) delivers:

- Community creation with MANAGER_APPROVAL and LOCATION_VERIFIED admission
- Membership with community-specific roles and context metadata (JSON)
- Global asset ownership with immediate AssetUnit materialization
- CommunityListing: selective visibility of owned assets to communities
- Community-scoped Explore with aggregate availability (shared across listings)
- Deterministic seed data and idempotent development reset
- Docker health-gated startup with backend healthcheck

V2.1 does not include transactions, borrowing workflows, messaging, notifications, reputation, or AI. See `docs/BORROWBOX_ROADMAP.ipynb` for the full roadmap.

V2.2.1 (Transaction Negotiation) delivers the first slice of the transaction engine:

- Structured borrow requests: borrower states purpose + duration (1–30 days)
- Lender approve / reject / counter-offer; borrower accept-counter / cancel
- Race-safe basis reservation: pessimistic DB lock + `UNIQUE(reserved_unit_id)` backstop, exactly-one-wins
- Explore "Request" drawer and a Requests inbox (`/me/requests`, `/me/lend-requests`)
- Backend/database is authoritative for reservation ordering; AssetUnit IDs are never exposed

📄 **Project Pitch & Overview:** See the one-page project pitch in [Markdown](docs/PITCH.md) or download the [Pitch PDF](docs/assets/borrowbox-project-pitch.pdf).

---

## Core Features (V2.1)

| Module | Implemented Capabilities |
|---|---|
| **Authentication & Security** | BCrypt password hashing, stateless HttpOnly JWT cookies, global exception handling, protected React route guards. |
| **Community + Membership** | Community creation with type/location/admission mode, MANAGER_APPROVAL and LOCATION_VERIFIED join flows, community-specific roles and context metadata (JSON), manager controls. |
| **Asset Ownership** | Global asset ownership independent of communities, atomic Asset + N AssetUnit creation, owner inventory at `/me/inventory`. |
| **CommunityListing** | Selective visibility: one Asset listed in multiple communities, shared physical inventory pool, authorization enforced server-side (owner must have ACTIVE membership). |
| **Community Explore** | Community-scoped listing discovery with aggregate availability (totalUnits, availableUnits, borrowedUnits), AssetUnit IDs hidden from public API. |
| **Shared Availability** | Changing one AssetUnit status affects availability across all communities where the Asset is listed. Backend/database is authoritative. |

---

## Future Roadmap (BorrowBox V2.2+)

*Planned features beyond V2.1 / V2.2.1:*
- **Transaction Engine:** V2.2.1 delivers negotiation + reservation; remaining pieces are pickup coordination, handover confirmation, loan timer, extensions, return workflow.
- **Trust + Ledger:** Borrow/lending history, reputation events, reliability metrics, badges.
- **Community Health:** Manager dashboard, membership review, flags, moderation.
- **Condition + Evidence Intelligence:** Evidence timeline, condition metadata, before/after comparison.
- **Notifications + Automation:** In-app, email, push notifications.
- **AI + Tribunal:** AI-assisted condition comparison, blind tribunal, anonymous peer review.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Backend API** | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Hibernate ORM, Maven |
| **Database** | MySQL 8.0 (InnoDB), HikariCP connection pooling, dynamic JPA Specifications |
| **Authentication** | Stateless JWT stored in HttpOnly cookies, BCrypt password encoder |
| **Frontend SPA** | React 18.2, Vite 5, React Router 7, Custom CSS Design System |
| **API Docs** | Springdoc OpenAPI 3.0 / Swagger UI |
| **DevOps & Containerization** | Multi-stage Docker builds, Docker Compose, Nginx reverse proxy, launch scripts (`run_demo.bat` / `run_demo.sh`) |
| **Testing** | JUnit 5, Mockito, MockMvc, Cypress 13 E2E testing (20 automated tests) |
| **CI/CD** | GitHub Actions ([build.yml](.github/workflows/build.yml), [e2e.yml](.github/workflows/e2e.yml), [deploy.yml](.github/workflows/deploy.yml)) |

---

## 🚀 Quick Start (Docker — Recommended)

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.
- [Git](https://git-scm.com/) installed.

### 1. Clone the repository
```bash
git clone https://github.com/Furquanis1/BorrowBox.git
cd BorrowBox
```

### 2. One-Click Launch

**Windows:**
```cmd
run_demo.bat
```

**Linux / macOS:**
```bash
chmod +x run_demo.sh
./run_demo.sh
```

**Or using Docker Compose directly:**
```bash
docker compose up -d --build
```

This starts three orchestrated containers:
- `borrowbox-mysql` — MySQL 8.0 database (mapped to host port `3307` to avoid local MySQL conflicts)
- `borrowbox-backend` — Spring Boot REST API on port `8080`
- `borrowbox-frontend` — React application served by Nginx on port `3000`

### 3. Service URLs

| Service | URL | Notes |
|---|---|---|
| **Frontend (React UI)** | [http://localhost:3000](http://localhost:3000) | Main web interface |
| **Backend Health Check** | [http://localhost:8080/api/health](http://localhost:8080/api/health) | Verifies database connectivity |
| **Swagger API Docs** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive OpenAPI documentation |

### 4. Stop Services & Cleanup

- **Stop containers:**
  ```bash
  # Windows
  run_demo.bat --stop

  # Linux / macOS
  ./run_demo.sh --stop

  # Or Compose
  docker compose down
  ```

- **Full reset (stop containers and wipe database volumes):**
  ```bash
  # Windows
  run_demo.bat --clean

  # Linux / macOS
  ./run_demo.sh --clean

  # Or Compose
  docker compose down -v
  ```

---

## Local Development

To run the application locally outside of Docker:

### 1. Database
Ensure MySQL 8.0 is running with database `borrowbox_v2`:
```bash
# Or start just the MySQL container
docker compose up -d mysql
```

To reset the local database:
```powershell
.\scripts\reset-v2-db.ps1
```

### 2. Backend (Spring Boot)
```bash
cd backend
mvn clean spring-boot:run
```
The backend starts on `http://localhost:8080` with `borrowbox.seed.enabled=true` (schema + seed run automatically).

### 3. Frontend (Vite Dev Server)
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` for hot-reloading development (requests to `/api` are automatically proxied to `http://localhost:8080`).

---

## Testing & Verification

BorrowBox maintains a comprehensive test suite across unit, integration, and end-to-end layers:

### Run Backend Tests
```bash
cd backend
mvn test
```
*Requires local MySQL running on port 3306 with database `borrowbox_v2`.*

### Build Frontend
```bash
cd frontend
npm run build
```
*Validates modules, compiles JSX, bundles CSS, and confirms production asset generation with zero errors.*

### Run Cypress End-to-End Tests (44 Tests)
With the application running on `http://localhost:3000`:
```bash
cd frontend
npx cypress run --headless
```
*Covers Landing Page, Authentication, Dashboard Routing, Community Listings, Shared Inventory Availability, and V2.1 Completion Flow.*

---

## 🔧 Practical Troubleshooting Guide

Here are practical solutions for common local development and runtime issues:

### 1. Docker Desktop Not Running
- **Symptom:** `error during connect: This error may indicate that the docker daemon is not running.`
- **Fix:** Launch Docker Desktop from the Start Menu / Applications and wait for the status indicator to turn green before running `run_demo.bat` or `docker compose up`.

### 2. Port 8080 Already in Use
- **Symptom:** Backend container fails to bind to port 8080 or throws `Address already in use`.
- **Fix:** Identify and terminate the process occupying port 8080:
  - *Windows (PowerShell):* `Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force`
  - *Linux/macOS:* `lsof -ti:8080 | xargs kill -9`

### 3. Port 3000 Already in Use
- **Symptom:** Frontend container fails to start because port 3000 is occupied.
- **Fix:** Stop existing web applications running on port 3000:
  - *Windows (PowerShell):* `Get-Process -Id (Get-NetTCPConnection -LocalPort 3000).OwningProcess | Stop-Process -Force`
  - *Linux/macOS:* `lsof -ti:3000 | xargs kill -9`

### 4. MySQL Port Conflict (Port 3306)
- **Note:** BorrowBox maps the MySQL container to host port **3307** (`3307:3306`) in `docker-compose.yml` to prevent conflicts with any local MySQL instances running on port 3306. Local development uses port 3306 directly.

### 5. Backend Startup Timing / Database Waiting
- **Symptom:** Backend container restarts or logs HikariCP connection retry warnings.
- **Fix:** The MySQL container takes a few seconds to complete internal initialization on first start. Docker Compose waits for MySQL healthcheck before starting the backend. The backend healthcheck (`/api/health`) ensures the frontend does not start until the API is ready.

### 6. Resetting Corrupted or Stale State
- **Symptom:** Database schema inconsistencies or stale session cookies.
- **Fix:** Execute a clean reset:
  ```bash
  # Docker reset
  docker compose down -v
  docker compose up -d --build
  ```
  For local development:
  ```powershell
  .\scripts\reset-v2-db.ps1
  cd backend && mvn spring-boot:run
  ```
  Then clear cookies/localStorage in your browser or use an incognito window.

### 7. Inspecting Container Logs
- **Backend logs:** `docker compose logs -f backend`
- **Frontend logs:** `docker compose logs -f frontend`
- **MySQL logs:** `docker compose logs -f mysql`

---

## Project Structure

```
BorrowBox/
├── .github/
│   └── workflows/
│       ├── build.yml         # CI build, Maven tests, Docker Buildx verification
│       ├── e2e.yml           # Headless Cypress E2E pipeline with live MySQL
│       └── deploy.yml        # Deployment automation (Railway & Render hooks)
├── backend/                  # Spring Boot REST API (Java 21)
│   ├── src/main/java/com/borrowbox/
│   │   ├── config/           # Security, CORS, OpenAPI, SeedDataInitializer
│   │   ├── controller/       # REST endpoints (Auth, Community, Asset, Listing, Health)
│   │   ├── dto/              # Request & Response DTOs
│   │   ├── entity/           # JPA Entities (User, Community, Membership, Asset, AssetUnit, CommunityListing, etc.)
│   │   ├── exception/        # Global exception handler
│   │   ├── repository/       # Spring Data JPA repositories
│   │   ├── security/         # JWT filter & authentication logic
│   │   ├── service/          # Business logic services
│   │   └── spec/             # Dynamic JPA Specifications
│   ├── src/test/java/        # Unit, service, controller, and integration tests
│   └── Dockerfile
├── frontend/                 # React 18 + Vite SPA
│   ├── cypress/              # Cypress E2E tests (5 spec files, 44 tests)
│   │   └── e2e/              # auth, landing, dashboard, listings, completion
│   ├── src/
│   │   ├── components/       # UI components
│   │   ├── contexts/         # React Contexts (AuthContext)
│   │   ├── pages/            # Page components & DashboardLayout
│   │   └── utils/            # API client and helper utilities
│   ├── nginx.conf            # Production Nginx reverse proxy configuration
│   └── Dockerfile
├── scripts/
│   └── reset-v2-db.ps1       # Local V2.1 database reset script
├── docs/
│   ├── BORROWBOX_ROADMAP.ipynb
│   ├── BORROWBOX_DECISIONS.ipynb
│   ├── BORROWBOX_V2_1_DATABASE_SCHEMA.ipynb
│   ├── BORROWBOX_V2_1_SEED_DATA_AND_INITIALIZATION.ipynb
│   └── ...                   # Additional planning and specification notebooks
├── docker-compose.yml        # V2.1 multi-container orchestration
├── PROJECT_STATUS.md         # Current project state
├── DEV_NOTES.md              # Developer notes (reset, seed, Docker)
└── README.md
```

---

## 🔐 Security & Default Configuration

- **Authentication:** Stateless **JWT stored in HttpOnly cookies**, protecting against XSS token theft.
- **Access Control:** All API endpoints are secured by default; public access is restricted to `/api/auth/**` and `/api/health`.
- **Credential Hygiene:** Change default database passwords and JWT secrets via environment variables before any production deployment.

---

## 📄 License

MIT © BorrowBox Contributors