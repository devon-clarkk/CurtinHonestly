# Curtin Honestly — Admin Panel

Lightweight Angular admin app for platform metrics and moderation. Deploy separately (e.g. Azure Static Web Apps) from the public student frontend.

## Pages

| Route | Purpose |
|-------|---------|
| `/` | At-a-glance metrics (users, reviews, 7-day growth) |
| `/analytics` | Time-series charts and faculty breakdown |
| `/operations` | User management, ban/unban, review moderation |

## Local development

```bash
cd admin-frontend
npm install
npm start
```

Runs on **http://localhost:4201**. Expects the backend API at `http://localhost:8080` (override via root `.env` `API_URL`).

## Auth

- Uses the same JWT login endpoint (`POST /auth/login`) as the student app.
- Only accounts with `ROLE_ADMIN` in the JWT can access admin routes.
- Regular student accounts are rejected at login.

## Backend API

All endpoints are under `/admin/**` and require `ROLE_ADMIN`:

- `GET /admin/stats/overview`
- `GET /admin/stats/analytics?days=30`
- `GET /admin/users`, `POST /admin/users`
- `PATCH /admin/users/{id}/ban`, `PATCH /admin/users/{id}/unban`
- `DELETE /admin/users/{id}`
- `GET /admin/reviews`, `DELETE /admin/reviews/{id}`

## Bootstrap an admin account

Use the existing seed script or create via API once you have one admin:

```bash
cd backend && ./populate_db.sh
# logs in as admin@student.curtin.edu.au
```

Or promote via `POST /admin/users` with `{ "admin": true }` from an existing admin session.
