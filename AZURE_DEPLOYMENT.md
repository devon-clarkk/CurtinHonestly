# Azure deployment guide (CurtinHonestly)

Stack:

- **Frontend**: Azure Static Web Apps (Free) — `fe-dev`, `fe-prod` ✅
- **Backend**: Azure Container Apps (Consumption)
- **Database**: Supabase Postgres (keep for now)
- **CI/CD**: GitHub Actions
- **Tickets**: Azure DevOps Boards (linked to GitHub repo)

---

## Naming convention

| Resource | Dev | Prod |
|---|---|---|
| Resource group | `rg-curtinhonestly-dev` | `rg-curtinhonestly-prod` |
| Static Web App | `curtinhonestly-fe-dev` | `curtinhonestly-fe-prod` |
| Container App | `curtinhonestly-be-dev` | `curtinhonestly-be-prod` |
| Container Apps environment | `cae-curtinhonestly-dev` | `cae-curtinhonestly-prod` |
| Container registry (shared) | `acrcurtinhonestly` (one ACR for both) | same |

Region:

- **Backend / ACR / Container Apps**: **Australia East**
- **Static Web Apps**: **East Asia** (only option closest to AU)

Tags (all resources):

- `project=curtinhonestly`
- `environment=dev` or `prod`
- `owner=devon`
- `managed-by=github-actions`

---

## Part 1 — Backend infrastructure (Azure Portal)

Do these in order.

### Step 1: Resource groups

Create:

1. `rg-curtinhonestly-dev` — region **Australia East**
2. `rg-curtinhonestly-prod` — region **Australia East**

### Step 2: Azure Container Registry (ACR)

One registry shared by dev + prod (cheaper than two).

1. **Create a resource** → **Container Registry**
2. Settings:
   - Resource group: `rg-curtinhonestly-dev` (or a shared `rg-curtinhonestly-shared`)
   - Registry name: `acrcurtinhonestly` *(must be globally unique — add digits if taken)*
   - Location: **Australia East**
   - SKU: **Basic** (~$5/month, uses student credit)
3. After creation, note:
   - **Login server**: e.g. `acrcurtinhonestly.azurecr.io`
   - Enable **Admin user** (Settings → Access keys) for initial setup  
     *(OIDC deploy workflow uses `az acr login`, not admin user — admin is for manual first push if needed)*

### Step 3: Container Apps environment (dev)

1. **Create a resource** → **Container Apps**
2. Choose **Create Container Apps Environment** (or create environment as part of first app wizard)
3. Settings:
   - Resource group: `rg-curtinhonestly-dev`
   - Environment name: `cae-curtinhonestly-dev`
   - Region: **Australia East**
   - Workload profile: **Consumption** (pay per use + monthly free grant)

Repeat for prod:

- RG: `rg-curtinhonestly-prod`
- Name: `cae-curtinhonestly-prod`

### Step 4: Create backend Container App (dev)

1. **Create a resource** → **Container App**
2. Basics:
   - Resource group: `rg-curtinhonestly-dev`
   - Name: `curtinhonestly-be-dev`
   - Region: **Australia East**
   - Container Apps environment: `cae-curtinhonestly-dev`
3. Container:
   - **Use quickstart image for now** (e.g. `mcr.microsoft.com/k8se/quickstart`) — GitHub Actions replaces it on first deploy
   - CPU/Memory: **0.5 CPU, 1 GiB** (Spring Boot needs more than 0.25/0.5)
   - Ingress: **Enabled**
   - Ingress traffic: **Accept traffic from anywhere**
   - Target port: **8080** (Spring Boot default)
4. Scaling (Consumption):
   - Min replicas: **0** (scale to zero when idle — saves credit)
   - Max replicas: **3** (enough for promo spikes)
5. Environment variables (dev — from Supabase):

   | Name | Value |
   |---|---|
   | `DATABASE_URL` | Supabase JDBC URL (see below) |
   | `DATABASE_USERNAME` | Supabase user |
   | `DATABASE_PASSWORD` | Supabase password |
   | `JWT_SECRET` | Long random string (not the default in `application.yml`) |

6. Registry (after first image push, or configure now):
   - Server: `acrcurtinhonestly.azurecr.io`
   - Auth: ACR admin or managed identity *(workflow uses OIDC + `az acr login`)*

### Step 5: Create backend Container App (prod)

Same as dev, but:

- RG: `rg-curtinhonestly-prod`
- Environment: `cae-curtinhonestly-prod`
- Name: `curtinhonestly-be-prod`
- Use **prod** Supabase credentials (or same DB initially, separate later)

### Step 6: Copy backend URLs

After each Container App is created:

1. Open the app → **Application URL** (e.g. `https://curtinhonestly-be-dev.<random>.australiaeast.azurecontainerapps.io`)
2. Save:
   - **Dev backend URL** → used as `API_URL` for `fe-dev` builds
   - **Prod backend URL** → used as `API_URL` for `fe-prod` builds

---

## Part 2 — Supabase connection strings

In Supabase → **Project Settings → Database**:

1. Use **Connection string** → **URI** (Session pooler or Direct)
2. Convert to JDBC format for Spring Boot:

```text
jdbc:postgresql://<host>:5432/postgres?sslmode=require
```

3. Set env vars on the Container App:
   - `DATABASE_URL` = JDBC URL above
   - `DATABASE_USERNAME` = `postgres` (or pooler user)
   - `DATABASE_PASSWORD` = your Supabase DB password

**Do not enable Supabase Data API** — the backend talks to Postgres directly.

If connection fails from Azure, check Supabase **Network restrictions** (allow Azure IPs or disable restrictions for dev).

---

## Part 3 — GitHub Actions (backend deploy)

Workflow: `.github/workflows/deploy-backend.yml`

### Triggers

| Event | Deploys |
|---|---|
| Push to `dev` / `development` (backend paths) | **dev** |
| Push to `main` (backend paths) | **prod** |
| Manual **Run workflow** | choose dev or prod |

### Step A: Create GitHub Environments

In GitHub repo → **Settings → Environments**:

1. Create **`dev`**
2. Create **`prod`** — add **Required reviewers** (manual approval before prod deploy)

### Step B: Azure OIDC (federated credentials)

1. Azure Portal → **Microsoft Entra ID** → **App registrations** → **New registration**
   - Name: `github-curtinhonestly-deploy`
2. **Certificates & secrets** → **Federated credentials** → Add:
   - Entity: **GitHub Actions**
   - Org/repo: `devon-clarkk/CurtinHonestly` *(adjust if different)*
   - Subject: `repo:devon-clarkk/CurtinHonestly:environment:dev` (repeat for `prod` and optionally `ref:refs/heads/main`)
3. Note **Application (client) ID** and **Directory (tenant) ID**
4. **Subscriptions** → your subscription ID
5. Assign roles to the app on resource groups:
   - **Contributor** on `rg-curtinhonestly-dev` and `rg-curtinhonestly-prod`
   - **AcrPush** on the ACR resource

### Step C: GitHub secrets

**Repository secrets** (shared):

| Secret | Example |
|---|---|
| `AZURE_CLIENT_ID` | App registration client ID |
| `AZURE_TENANT_ID` | Tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription ID |
| `ACR_NAME` | `acrcurtinhonestly` |
| `ACR_LOGIN_SERVER` | `acrcurtinhonestly.azurecr.io` |

**Environment `dev` secrets**:

| Secret | Value |
|---|---|
| `RESOURCE_GROUP` | `rg-curtinhonestly-dev` |
| `CONTAINER_APP_NAME` | `curtinhonestly-be-dev` |
| `DATABASE_URL` | Supabase JDBC URL |
| `DATABASE_USERNAME` | Supabase user |
| `DATABASE_PASSWORD` | Supabase password |
| `JWT_SECRET` | Dev JWT secret |

**Environment `prod` secrets**: same keys, prod values.

### Step D: First deploy

1. Commit and push `.github/workflows/deploy-backend.yml` to `dev`
2. GitHub → **Actions** → **Deploy Backend** → watch the run
3. Verify: open Container App URL → e.g. `https://<be-dev-url>/units` (should return JSON or 401, not 502)

---

## Part 4 — Connect frontend to backend

Each SWA build must set `API_URL` (see `frontend/set-env.js`).

### Option A: Edit Azure-generated SWA workflow

Azure created `.github/workflows/azure-static-web-apps-*.yml` when you linked GitHub. Add before `npm run build`:

```yaml
env:
  API_URL: https://<your-be-dev-or-prod-url>
```

Or use GitHub Environment secrets and branch conditions (dev branch → dev API URL).

### Option B: GitHub Environment secrets

| Environment | Secret | Value |
|---|---|---|
| dev | `API_URL` | `https://curtinhonestly-be-dev....azurecontainerapps.io` |
| prod | `API_URL` | `https://curtinhonestly-be-prod....azurecontainerapps.io` |

Build command becomes:

```bash
API_URL=${{ secrets.API_URL }} npm run build
```

---

## Part 5 — Verify end-to-end

1. **Backend**: `GET https://<be-dev-url>/units` returns data
2. **Frontend**: open SWA URL, units load (no CORS errors in browser console)
3. **Auth**: register/login against dev backend
4. **Promo spike**: Container Apps scales up; watch **Metrics** in portal

---

## Cost notes (Azure for Students)

| Service | Cost |
|---|---|
| Static Web Apps Free | $0 (100 GB bandwidth cap) |
| Container Apps | Free monthly grant, then pay-as-you-go from **$100 credit** |
| ACR Basic | ~$5/month from credit |
| Supabase | Free tier |

Dev + prod **share** the Container Apps free grant (per subscription/month).

---

## Troubleshooting

| Problem | Fix |
|---|---|
| 502 / container not listening | Ingress target port must be **8080** |
| DB connection refused | Check Supabase URL, `sslmode=require`, network restrictions |
| CORS errors | Backend `SecurityConfig` allows `*` — ensure `API_URL` has no trailing slash mismatch |
| ACR pull failed | Confirm OIDC app has **AcrPush** and `az acr login` works in workflow logs |
| App scales to zero cold start | First request after idle may take 5–15s — normal on Consumption |

---

## Legacy: Azure DevOps pipeline files

`.azuredevops/pipelines/` and `trigger-azure-devops.yml` were an earlier approach.  
You can ignore/delete them if using GitHub Actions only.
