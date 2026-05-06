# BorrowBox Frontend (Minimal Demo)

This is a minimal Vite + React frontend demo that calls the backend search API.

Quick start

```bash
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173 in your browser. The dev server proxies `/api` to `http://localhost:8080`.

Notes

- The backend should be running on `http://localhost:8080` (default Spring Boot port).
- If you prefer not to use the proxy, ensure CORS is enabled on the backend (already configured in the project).

Files

- `src/components/ItemSearch.jsx`: search plus create/edit flow for `/api/items`.
- `src/components/BorrowRecordSearch.jsx`: simple UI to call `/api/borrow-records/search` (active/overdue filters).
- `src/components/BorrowRecordCreate.jsx`: create flow for `/api/borrow-records`.
- `src/components/BorrowRequestCreate.jsx`: small create form for `/api/borrow-requests`.
- `src/components/BorrowRequestList.jsx`: list and approve flow for `/api/borrow-requests`.
- `vite.config.mjs`: dev proxy for `/api` to backend.
- `src/components/ItemSearch.jsx`: now also includes an item detail view fetched by ID.

Next steps I can take for you

- Add a small BorrowRecord search page and navigation.
- Build a production-ready integration with authentication.
- Create a Postman collection and add example environment variables.
