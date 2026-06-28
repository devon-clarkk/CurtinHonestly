# CurtinHonestly — University Unit Review Platform

A website where university students in Australia can leave honest reviews for their units (courses), helping future students make informed decisions.

## Live versions

| Environment | Frontend | Backend |
|---|---|---|
| **Production** | https://icy-sand-081cc7100.7.azurestaticapps.net | `https://be-curtinhonestly-prod.happyplant-9f34dec7.australiaeast.azurecontainerapps.io` |
| **Dev** | https://nice-pebble-059fa6b00.7.azurestaticapps.net | `https://be-curtinhonestly-dev.happyplant-9f34dec7.australiaeast.azurecontainerapps.io` |

---

## Tech Stack

### Backend (Spring Boot)
- **Spring Boot (Java 17+)** – REST API, authentication, persistence
- **PostgreSQL** (Supabase) – Stores users, reviews, and unit data
- **JWT** – Stateless authentication
- **Maven** – Dependency management
- **Docker** – Containerised for deployment

### Frontend (Angular)
- **Angular** (SSR build, served as static content)
- API base URL injected at build time via `frontend/set-env.js` (`API_URL`)

### API Communication
- Spring Boot exposes REST endpoints (e.g., `/units`, `/reviews`, `/auth`)
- Frontend calls the backend over HTTPS using the build-time `API_URL`

---

## Architecture

```
                 ┌─────────────────────────────┐
   Users ──────► │  Azure Static Web Apps (FE)  │   Azure "Free" subscription
                 │  dev: nice-pebble            │
                 │  prod: icy-sand              │
                 └──────────────┬──────────────┘
                                │ HTTPS (API_URL)
                                ▼
                 ┌─────────────────────────────┐
                 │  Azure Container Apps (BE)   │   Azure for Students subscription
                 │  be-curtinhonestly-dev       │   Australia East
                 │  be-curtinhonestly-prod      │   (shared environment:
                 │  image from ACR              │    cae-curtinhonestly-shared)
                 └──────────────┬──────────────┘
                                │ JDBC (sslmode=require)
                                ▼
                 ┌─────────────────────────────┐
                 │  Supabase PostgreSQL         │   dev + prod projects
                 └─────────────────────────────┘
```

| Layer | Service | Region / Notes |
|---|---|---|
| Frontend | Azure Static Web Apps (Free) | One app per environment, served via global CDN |
| Backend | Azure Container Apps (Consumption) | Australia East, dev + prod share one environment |
| Image registry | Azure Container Registry `acrcurtinhonestly` | `rg-curtinhonestly-shared`, OIDC/managed-identity auth |
| Database | Supabase PostgreSQL | JDBC direct (Data API not used) |
| CI/CD | GitHub Actions | OIDC to Azure, per-environment secrets |
| Tickets | Azure DevOps | Issue tracking only |

> The frontend and backend live in **two different Azure subscriptions** under one personal tenant. This is fine — they communicate over public HTTPS, and one service principal holds roles across both.

---

## CI/CD (GitHub Actions)

| Workflow | Trigger | Deploys |
|---|---|---|
| `.github/workflows/azure-static-web-apps-nice-pebble-059fa6b00.yml` | push/PR to `dev`, manual | Frontend → dev SWA |
| `.github/workflows/azure-static-web-apps-icy-sand-081cc7100.yml` | push/PR to `main`, manual | Frontend → prod SWA |
| `.github/workflows/deploy-backend.yml` | push to `dev`/`main` (backend paths), manual | Backend → Container App |

- Backend deploys authenticate to Azure with **OIDC** (no stored credentials), build the Docker image, push to ACR, then `az containerapp update`.
- Frontend deploys bake `API_URL` into the Angular build so the SPA knows where the API lives.
- Both use GitHub **Environments** (`dev`, `prod`) to scope secrets; `prod` can require manual approval.

See [`AZURE_DEPLOYMENT.md`](AZURE_DEPLOYMENT.md) and [`SETUP_CHECKLIST.md`](SETUP_CHECKLIST.md) for full setup, required secrets, and the OIDC app registration steps.

---

## Local development

### Backend
```bash
cd backend
./mvnw spring-boot:run
```
Reads `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET` (defaults to a local Postgres in `application.yml`).

> In Azure, the same settings are provided as lowercase-hyphen secrets (`database-url`, `database-username`, `database-password`, `jwt-secret`). `Application.java` reads either form.

### Frontend
```bash
cd frontend
npm install
API_URL=http://localhost:8080 npm run build   # or `npm start` for dev server
```

### Full stack with Docker
```bash
docker-compose up --build
```

---

## Software & Tools

**Backend:** Java 17+, Spring Boot, Maven, PostgreSQL, Docker, Postman (API testing)
**Frontend:** Node.js, Angular CLI

---

## Notes
- Registration requires a `@student.curtin.edu.au` email.
- Database schema is created on first boot via `spring.jpa.hibernate.ddl-auto=update`.
- Independent student platform — not affiliated with Curtin University.
