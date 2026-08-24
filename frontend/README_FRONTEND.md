# BorrowBox Frontend

A modern React 18 single-page application built with Vite 5, React Router 7, and a dark-themed CSS design system.

---

## 🚀 Quick Start

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Start Development Server
```bash
npm run dev
```
Open `http://localhost:5173`. The Vite development server automatically proxies `/api` requests to `http://localhost:8080`.

### 3. Production Build & Preview
```bash
npm run build
npm run preview
```

---

## 🏗️ Architecture & Component Overview

- **`src/pages/`**:
  - `LandingPage.jsx`: Public landing page with product overview and call to action.
  - `SignInPage.jsx`: Secure sign-in form with validation and cookie authentication.
  - `SignUpPage.jsx`: New user registration.
  - `DashboardLayout.jsx`: Core authenticated shell with sidebar navigation, tab switcher, community selector, stats header, and active tab persistence via `localStorage`.
- **`src/pages/tabs/`**:
  - `ExploreDashboard.jsx`: Live searchable catalog with status filters (`All`, `Available`, `Borrowed`), category filter, pagination, and borrow request trigger.
  - `InventoryManager.jsx`: Owner-scoped inventory list, item creation modal, and archive/delete controls.
  - `RequestInbox.jsx`: Incoming borrow request manager with approval/rejection and loan confirmation (due date assignment).
  - `ActiveLoans.jsx`: Active and overdue loan tracker with one-click return reconciliation.
- **`src/contexts/`**:
  - `AuthContext.jsx`: Authentication lifecycle, current user state, and sign-out cleanup.
  - `AppContext.jsx`: Active community/group state and global notifications.
- **`src/components/`**:
  - `BorrowRequestModal.jsx`: Interactive dialog for submitting loan requests with notes.
  - `Toast.jsx`: Accessible notifications and feedback banners.
  - `Pagination.jsx`: Dynamic pagination control.
- **`src/utils/`**:
  - `api.js`: Centralized Axios/fetch client handling credentialed requests.

---

## 🧪 End-to-End Testing (Cypress)

BorrowBox uses Cypress 13 for automated UI and user-journey testing (20 passing specs):

```bash
# Headless run (CI/CLI)
npx cypress run --headless

# Interactive Cypress Test Runner
npx cypress open
```

Specs located in `cypress/e2e/`:
- `landing.cy.js` — Landing page rendering, feature sections, and navigation.
- `auth.cy.js` — Registration, validation, sign-in, and protected route redirection.
- `dashboard.cy.js` — Navigation tabs, tab persistence on page refresh, and sign-out.

