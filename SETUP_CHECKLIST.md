# CurtinHonestly — setup checklist (post-Azure resources)

Use this after Static Web Apps + Container Apps exist in Azure.

---

## Phase 1 — Supabase (dev + prod)

Create **two Supabase projects** (recommended):

| Project | Purpose |
|---|---|
| `curtinhonestly-dev` | Dev data, safe to reset |
| `curtinhonestly-prod` | Production data |

For each project:

1. **Do not** enable Data API (backend uses JDBC directly).
2. **Project Settings → Database** → copy connection info.
3. Build JDBC URL for Spring Boot:

```text
jdbc:postgresql://<host>:5432/postgres?sslmode=require
```

4. Note **username** and **password**.
5. **Settings → Database → Network** → allow connections (disable IP restrictions for dev, or allow Azure outbound later).

Set on each **Container App** (Portal → Container App → Secrets + Environment variables).

Azure secret **names** must be lowercase-hyphen (e.g. `database-password`). Map them to env vars with the **same names**:

| Secret / env var name | Dev | Prod |
|---|---|---|
| `database-url` | dev JDBC URL | prod JDBC URL |
| `database-username` | dev user | prod user |
| `database-password` | dev password | prod password |
| `jwt-secret` | dev secret (long random) | prod secret (different!) |

Local/docker still supports legacy `DATABASE_URL`, `DATABASE_USERNAME`, etc.

Also mirror these in **GitHub Environment secrets** (see Phase 3) so deploys don't wipe them.

**Schema:** backend uses `spring.jpa.hibernate.ddl-auto=update` — tables are created on first successful boot.

---

## Phase 2 — Wire Azure resources together

### Backend ingress
For each Container App (`be-curtinhonestly-dev`, `be-curtinhonestly-prod`):

1. **Ingress → Target port = 8080** (not 80 quickstart default).
2. Copy **Application URL**.

### ACR pull (if deploy fails on image pull)
1. Container App → **Identity** → enable **System assigned**.
2. ACR → **Access control (IAM)** → add role **AcrPull** for the container app's identity.
3. Container App → **Containers** → set image source to your ACR image after first push.

### Record URLs

| Resource | URL |
|---|---|
| FE dev (SWA) | |
| FE prod (SWA) | |
| BE dev (Container App) | |
| BE prod (Container App) | |

Frontend `API_URL` at build time must match backend URLs (no trailing slash).

---

## Phase 3 — GitHub Actions secrets

### Repository secrets (shared)

| Secret | Where to get it |
|---|---|
| `AZURE_CLIENT_ID` | Entra app registration (OIDC) |
| `AZURE_TENANT_ID` | Entra overview |
| `AZURE_SUBSCRIPTION_ID` | `486c3faf-48e3-4822-bc00-08126fb5ff6a` |
| `ACR_NAME` | ACR resource name |
| `ACR_LOGIN_SERVER` | ACR → Login server |

### OIDC setup (one-time)

1. Entra ID → **App registrations** → New → `github-curtinhonestly-deploy`
2. **Federated credentials** → GitHub Actions:
   - `repo:<org>/CurtinHonestly:environment:dev`
   - `repo:<org>/CurtinHonestly:environment:prod`
3. Assign roles:
   - **Contributor** on `rg-curtinhonestly-dev` and `rg-curtinhonestly-prod`
   - **AcrPush** on ACR

### GitHub Environment: `dev`

| Secret | Value |
|---|---|
| `RESOURCE_GROUP` | `rg-curtinhonestly-dev` |
| `CONTAINER_APP_NAME` | `be-curtinhonestly-dev` |
| `DATABASE_URL` | dev JDBC |
| `DATABASE_USERNAME` | dev |
| `DATABASE_PASSWORD` | dev |
| `JWT_SECRET` | dev |
| `API_URL` | `https://<be-dev-url>` (no trailing slash) |

### GitHub Environment: `prod`

Same keys, prod values. Add **Required reviewers** on prod environment.

---

## Phase 4 — Workflows in this repo

| Workflow | Triggers | Deploys |
|---|---|---|
| `azure-static-web-apps-witty-island-0b1c8f100.yml` | push `dev`/`development`, manual | SWA **dev** |
| `azure-static-web-apps-salmon-island-096d5fa00.yml` | push `main`, manual | SWA **prod** |
| `deploy-backend.yml` | push `dev`/`main` (backend paths), manual | Container App |

Both SWA workflows read **`API_URL`** from the GitHub Environment (`dev` or `prod`) during `npm run build`.
Azure auto-injects `AZURE_STATIC_WEB_APPS_API_TOKEN_*` secrets — no manual deployment token needed.

---

## Phase 5 — First deploy & verify

1. Commit + push workflows to `dev`.
2. **Actions → Deploy Backend** → confirm success.
3. **Actions → Deploy Frontend** → confirm success.
4. Test:
   - `GET https://<be-dev>/units` → JSON
   - Open SWA dev URL → units load
   - Register/login on dev

---

## Phase 6 — Optional next

- [ ] Custom domains on SWA
- [ ] Prod approval gate (GitHub Environment reviewers) ✅ recommended
- [ ] Flyway migrations (replace `ddl-auto=update`)
- [ ] Seed units data (`backend/populate_db.sh` or manual SQL)
- [ ] Budget alerts on Azure subscription
- [ ] Remove quickstart image references in portal

---

## Architecture note (your subscription)

- **One Container Apps Environment** in Australia East (quota limit).
- **Dev + prod container apps** share that environment.
- **One ACR** in `rg-curtinhonestly-shared`.
- **One Log Analytics workspace** per environment (filter logs by app name).
