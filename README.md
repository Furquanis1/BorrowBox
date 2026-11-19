# BorrowBox

> A calm, community-based space for peer-to-peer sharing and lending among people who know each other.

[![Build and Test](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml/badge.svg)](https://github.com/Furquanis1/BorrowBox/actions/workflows/build.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-brightgreen?logo=springboot)
![React](https://img.shields.io/badge/React-18.2-blue?logo=react)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)
![Tests](https://img.shields.io/badge/Tests-973_backend_%2B_166_E2E-green?logo=cypress)

---

## What is BorrowBox?

BorrowBox is a peer-to-peer sharing workspace for close circles of 5 to 500 people — friends, classmates, colleagues, and neighbors. People own their items independently, choose which communities to share them with, and borrow within a circle built on sharing, caring, and mutual trust.

It is deliberately **not** a marketplace: there are no listings to buy, no purchase flows, and no savings claims. The product is about making lending feel personal, clear, and safe inside a community you already trust.

## Current capabilities

The completed V2 product includes:

- **Privacy-preserving communities** — create communities, join by exact numeric Community ID only (no discovery or location suggestions), leave, and manage membership with community-specific roles.
- **Distinct Home and Explore surfaces** — Community Home is a calm hub with Pulse metrics, guidelines, quick actions, and an Explore CTA; Explore is the searchable borrowable-item catalog.
- **Shared community listings** — expose owned items to selected communities while keeping one authoritative shared inventory.
- **Borrow negotiation** — purpose and duration requests, lender approval or rejection, counter-offers, borrower acceptance, and cancellation.
- **Extensions** — borrowers can request more time and lenders can accept or counter with a revised return date.
- **Waitlists** — a single per-asset queue across communities, with automatic promotion when a unit is released.
- **Four-moment condition care** — condition evidence around listing/handover, use, and return, with a shared evidence timeline.
- **Manager dispute resolution** — managers review handover and return disputes, record a resolution note, and apply fair state/unit transitions.
- **In-app notifications** — participants are notified about join requests, requests, decisions, handover, returns, and dispute outcomes; managers receive pending-join notices and applicants receive approval/rejection results.
- **Append-only reputation** — community-scoped reputation events derived from completed loans and dispute outcomes, with idempotent ledger rows.
- **Manager workspace** — upgraded dashboards, membership review, flags, rules, dispute queues, and health/utilization breakdowns.
- **Derived trust profile** — on-time, late, completed, and dispute metrics computed from the immutable event ledger.
- **TripGlide-inspired UI** — calm editorial styling, focused cards and rails, and clear status/notification states across the product.

### Validation status

| Layer | Status |
|---|---|
| Backend | **973 tests** passing |
| Frontend | Production build passing |
| Cypress | **166 E2E tests across 25 spec files** passing |
| Docker | Health-gated local stack verified |

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend API | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Hibernate, Maven |
| Database | MySQL 8.0, HikariCP, JPA Specifications |
| Authentication | HttpOnly JWT cookies, BCrypt password hashing |
| Frontend | React 18, Vite 5, React Router, TripGlide-inspired editorial UI with a custom CSS design system |
| API docs | Springdoc OpenAPI 3 / Swagger UI |
| DevOps | Multi-stage Docker builds, Docker Compose, Nginx |
| Testing | JUnit 5, Mockito, MockMvc, Cypress 15 |

---

## Quick start (Docker)

### Prerequisites

- Docker Desktop installed and running
- Git installed

### One-click demo launch

**Windows — clean demo launch with a forced image rebuild:**

```cmd
run_demo.bat --build
```

**Linux / macOS:**

```bash
chmod +x run_demo.sh
./run_demo.sh --build
```

The launchers create and reuse `.borrowbox-demo-credentials` at the repository root. That Git-ignored, user-restricted file holds locally generated demo and database credentials. Read values from it into environment variables when needed; never commit the file or print/paste its values into documentation, issues, or chat.

Use `run_demo.bat --clean` or `./run_demo.sh --clean` to stop the stack, remove the database volume, and clear the local credential file.

### Docker Compose directly

Compose intentionally has no committed credentials. Supply the required values from `.borrowbox-demo-credentials` in your shell environment without printing them:

**Linux / macOS:**

```bash
export MYSQL_ROOT_PASSWORD="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .borrowbox-demo-credentials)"
export BORROWBOX_APP_DB_PASSWORD="$(sed -n 's/^BORROWBOX_APP_DB_PASSWORD=//p' .borrowbox-demo-credentials)"
export BORROWBOX_E2E_DB_PASSWORD="$(sed -n 's/^BORROWBOX_E2E_DB_PASSWORD=//p' .borrowbox-demo-credentials)"
export BORROWBOX_SEED_PASSWORD="$(sed -n 's/^BORROWBOX_SEED_PASSWORD=//p' .borrowbox-demo-credentials)"
export APP_JWT_SECRET="<generate-your-own-secret>"
export BORROWBOX_SEED_ENABLED=true

docker compose up -d --build
```

**Windows PowerShell:** read the same keys from `.borrowbox-demo-credentials` into the equivalent `$env:` variables, generate an `APP_JWT_SECRET`, then run `docker compose up -d --build`.

Compose fails safely if required values are missing. Plain `docker compose up` does not enable demo seeding; set `BORROWBOX_SEED_ENABLED=true` and `BORROWBOX_SEED_PASSWORD` explicitly to opt in.

### Services

| Service | URL / port | Notes |
|---|---|---|
| Frontend | <http://localhost:3000> | React SPA served by Nginx |
| Backend health | <http://localhost:8080/api/health> | Database connectivity check |
| Swagger UI | <http://localhost:8080/swagger-ui.html> | Interactive API documentation |
| MySQL | `127.0.0.1:3307` | Mapped to avoid local port 3306 conflicts |

Stop with `run_demo.bat --stop` / `./run_demo.sh --stop`, or `docker compose down` with the same environment variables loaded.

---

## Local development

Read database values from `.borrowbox-demo-credentials`; do not hard-code them.

### Database

```powershell
$env:BORROWBOX_APP_DB_PASSWORD = '<value from .borrowbox-demo-credentials>'
$env:BORROWBOX_E2E_DB_PASSWORD = '<value from .borrowbox-demo-credentials>'
.\scripts\reset-v2-db.ps1
```

The reset script prompts for MySQL administrator credentials unless `BORROWBOX_RESET_DB_USER` and `BORROWBOX_RESET_DB_PASSWORD` are set. It never prints or stores them.

### Backend

```powershell
cd backend
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/borrowbox_v2'
$env:SPRING_DATASOURCE_USERNAME = 'borrowbox_app'
$env:SPRING_DATASOURCE_PASSWORD = '<value from .borrowbox-demo-credentials>'
mvn spring-boot:run
```

Demo seeding is disabled by default. To opt in, set both `BORROWBOX_SEED_ENABLED=true` and `BORROWBOX_SEED_PASSWORD` from `.borrowbox-demo-credentials`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. `/api` requests are proxied to the backend on port 8080.

---

## Testing & verification

### Backend

```bash
cd backend
mvn test
```

Runs **973 tests**. Requires a local MySQL database and the `borrowbox_app` account with its password supplied from `.borrowbox-demo-credentials`.

### Frontend build

```bash
cd frontend
npm run build
```

Compiles JSX and produces the optimized production bundle.

### Cypress

```bash
cd frontend
export CYPRESS_BASE_URL='http://127.0.0.1:3000'
export CYPRESS_DB_HOST='127.0.0.1'
export CYPRESS_DB_PORT='3307'
export CYPRESS_DB_NAME='borrowbox_v2'
export CYPRESS_DB_USER='borrowbox_e2e'
export CYPRESS_DB_PASSWORD="$(sed -n 's/^BORROWBOX_E2E_DB_PASSWORD=//p' ../.borrowbox-demo-credentials)"
export CYPRESS_SEED_PASSWORD="$(sed -n 's/^BORROWBOX_SEED_PASSWORD=//p' ../.borrowbox-demo-credentials)"
npx cypress run --headless
```

Runs **166 E2E tests across 25 spec files**, covering authentication, communities, listings, borrow negotiation, extensions, waitlists, loan lifecycle, evidence and timelines, disputes and manager resolution, flags, rules, reputation, notifications, and UI states.

On Windows PowerShell, set the equivalent `$env:CYPRESS_*` values from `.borrowbox-demo-credentials`. Never commit generated test passwords or shared demo values.

---

## Project structure

```text
BorrowBox/
├── backend/                  Spring Boot REST API and 973 tests
├── frontend/                 React SPA
│   ├── cypress/e2e/          25 spec files, 166 E2E tests
│   └── src/                  pages, components, contexts, services, styles
├── scripts/                  local bootstrap, schema, and database reset helpers
├── docs/                     roadmap, decisions, schema, seed, and reports
├── docker-compose.yml        local multi-container stack
├── PROJECT_STATUS.md         current capability and validation snapshot
└── README.md
```

---

## Security notes

- API access is denied by default; only health and auth endpoints are public.
- JWTs are stored in HttpOnly cookies; browser JavaScript never reads the token.
- The backend, Cypress, and bootstrap use dedicated database accounts and never receive the MySQL root password.
- Demo credentials are generated locally into `.borrowbox-demo-credentials` and are never committed.
- Manager-only endpoints verify an active MANAGER membership for the transaction's community.

---

## License

MIT © BorrowBox Contributors
