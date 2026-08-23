# BorrowBox 📦
### Full-Stack Asset Sharing & Equipment Lending Platform

> **Value Proposition:** BorrowBox is a full-stack inventory sharing and equipment loan tracking platform that replaces disorganized spreadsheets and informal chat requests with a structured, secure, and auditable borrowing workflow for organizations, campus labs, and shared communities.

---

## 🎯 The Problem
Physical asset sharing within teams, university labs, maker spaces, and co-living communities suffers from recurring operational friction:
- **Lost & Forgotten Equipment:** Informal lending via verbal requests or direct messages lacks accountability, leading to forgotten loans and misplaced gear.
- **Zero Real-Time Visibility:** Members cannot easily discover what items exist, who currently has them, or when they are expected back.
- **Spreadsheet Overhead:** Manual logs require constant maintenance, offer no access control, and cannot automatically track overdue loans or status transitions.

## 💡 The Solution
BorrowBox delivers a centralized, self-service web application with structured lending lifecycles:
- **Self-Service Item Catalog:** Live inventory discovery with real-time status indicators (`AVAILABLE`, `BORROWED`, `ARCHIVED`) and multi-criteria specification filtering.
- **Formal Request Pipeline:** Borrowers submit requests with custom notes; item owners review, approve, reject, or confirm loans with binding due dates.
- **Automated Loan Management:** Automatic state synchronization between items and borrow records, overdue calculation, audit trails, and one-click return processing.

---

## 🚀 Core Features Implemented

| Domain | Implemented Capabilities |
|---|---|
| **Authentication & Security** | BCrypt password hashing, stateless HttpOnly JWT cookies (XSS-resilient), global exception handling, and protected route guards. |
| **Catalog & Inventory** | Complete CRUD operations for inventory items, category classification, dynamic search via JPA Specifications, and item archiving. |
| **Borrow Request Workflow** | Borrower request submission, custodian incoming request inbox, approval/rejection controls, and atomic loan confirmation with due dates. |
| **Loan & Return Tracking** | Active loan monitoring, overdue loan detection, borrower history logging, and one-click item return reconciliation. |
| **User Experience** | Modern responsive dashboard (Explore, My Inventory, Borrow Requests, Active Loans), real-time stats, and server-side pagination. |

---

## 🏗️ Architecture & Technology Stack

```
[ Client: React 18 SPA (Vite + Nginx) ] ──(HttpOnly JWT / REST API)──► [ API: Spring Boot 3.5 (Java 21) ] ──(JPA / Hibernate)──► [ DB: MySQL 8.0 ]
```

- **Frontend:** React 18, Vite 5, React Router DOM 7, Custom CSS Design System, Responsive Layouts.
- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Hibernate ORM, Maven.
- **Database:** MySQL 8.0 (InnoDB), HikariCP connection pooling, JPA Specification dynamic queries.
- **DevOps & Containerization:** Multi-stage Docker builds, Docker Compose orchestration, Nginx reverse proxy, and one-click launch scripts (`run_demo.sh` / `run_demo.bat`).

---

## 🧪 Testing, Quality & CI/CD
- **Backend Test Suite:** 93 automated tests across unit, service, controller (MockMvc), and repository integration tests against MySQL.
- **End-to-End Testing:** Cypress test suite (20 tests) verifying complete user journeys: landing page, authentication, protected navigation, and dashboard operations.
- **CI/CD Automation:** GitHub Actions workflows executing Maven tests with a live MySQL service container, frontend production builds, Docker Buildx packaging, and headless Cypress testing.

---

## 🔮 Future Vision
*The following capabilities represent the planned project roadmap beyond current release:*
- **Multi-Tenant Community Workspaces:** Isolated group hierarchies and multi-organization role permissions.
- **Automated Notifications:** Email and webhook alerts for request approvals, upcoming due dates, and overdue reminders.
- **Barcode / QR Scanning:** Mobile camera-driven physical item check-out and instant check-in.
- **Cloud Asset Storage:** S3-compatible cloud object storage integration for item photos and condition documentation.
