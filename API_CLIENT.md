# BorrowBox API Client / Quickstart

This file contains quick curl examples to exercise common API endpoints.

Base URL (local):

- http://localhost:8080

Notes:
- The backend must be running (from `backend`):

```bash
cd backend
mvn spring-boot:run
```

Items
-----

Search items (paginated):

```bash
curl -s -G "http://localhost:8080/api/items/search" \
  --data-urlencode "q=book" \
  --data-urlencode "status=AVAILABLE" \
  --data-urlencode "categoryId=5" \
  --data-urlencode "page=0" \
  --data-urlencode "size=10"
```

Create an item:

```bash
curl -s -X POST "http://localhost:8080/api/items" \
  -H "Content-Type: application/json" \
  -d '{"title":"Cordless Drill","description":"18V drill"}'
```

Get item by id:

```bash
curl -s "http://localhost:8080/api/items/1"
```

Archive an item:

```bash
curl -s -X POST "http://localhost:8080/api/items/1/archive"
```

Borrow records
--------------

Search borrow records (active only):

```bash
curl -s -G "http://localhost:8080/api/borrow-records/search" \
  --data-urlencode "active=true" \
  --data-urlencode "page=0" \
  --data-urlencode "size=10"
```

Search overdue borrow records:

```bash
curl -s -G "http://localhost:8080/api/borrow-records/search" \
  --data-urlencode "overdue=true" \
  --data-urlencode "page=0" \
  --data-urlencode "size=10"
```

Create a borrow record (example fields):

```bash
curl -s -X POST "http://localhost:8080/api/borrow-records" \
  -H "Content-Type: application/json" \
  -d '{"borrowRequestId":3,"itemId":1,"borrowedByUserId":2,"borrowedAt":"2026-05-01T10:00:00","dueAt":"2026-05-08T10:00:00"}'
```

Other quick calls
-----------------

List all items:

```bash
curl -s "http://localhost:8080/api/items"
```

List all borrow requests:

```bash
curl -s "http://localhost:8080/api/borrow-requests"
```

OpenAPI / Swagger UI
--------------------

- Swagger UI is available at: `http://localhost:8080/swagger-ui.html` (or `/swagger-ui/index.html`).
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Troubleshooting
---------------
- If CORS blocks requests from a browser-based frontend, ensure the backend is running with the `CorsConfig` enabled (already added).
- If endpoints return 404, confirm the server port and context path.

If you want, I can:
- Add example Postman collection or a small Node/JS client.
- Scaffold a minimal React frontend that hits the search endpoints.
