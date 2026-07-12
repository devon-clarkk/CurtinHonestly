# Production hotfix runbook — deploy checks & DB changes

This runbook covers the manual, prod-side steps required by the `security/prod-hotfixes` branch.
None of these steps can be done in code — they must be run against GitHub and the production database
by someone with access.

Deploys are via **GitHub Actions only** (`.github/workflows/deploy-backend.yml`). The unused
`.azuredevops/pipelines/` files (confirmed dead: `trigger: none`/`pr: none`, and documented as legacy in
`AZURE_DEPLOYMENT.md`) have been removed from the repo.

## 1. Secret rotation — not required

`jwt-secret`/`database-password` in dev and prod are already distinct real values, separate from the
local-only fallback (`tG4Mz8q7Rs2Lp9FnXKp7dWsYmYeTb4H3` / `CurtinHonestly`) that was removed from
`application.yml` and `docker-compose.yml`. That fallback was never relied on in dev/prod, so no
rotation is needed. The code change (fail-fast if `jwt-secret`/`database-password` is unset) is still
worth keeping as defense-in-depth against a future config gap — it just doesn't require any action now.

## 2. Pre-deploy environment check

Confirm these are set as GitHub Environment secrets for both the `dev` and `prod` GitHub Environments
(repo Settings → Environments → `dev` / `prod` → Secrets), since the new image **hard-fails on boot**
if either is missing:

- `JWT_SECRET`
- `DATABASE_PASSWORD`
- `DATABASE_URL`, `DATABASE_USERNAME`

Also confirm, if you want the CORS allowlist to differ from the built-in prod default
(`https://curtinhonestly.com,https://www.curtinhonestly.com,https://admin.curtinhonestly.com` — `main`'s
`10a5a33` SEO commit makes `www.curtinhonestly.com` the canonical student domain, so both the apex and
`www` are allowed):

- `CORS_ALLOWED_ORIGINS` — and that the SWA custom domains for `curtinhonestly.com`/`www.curtinhonestly.com`
  (student) and `admin.curtinhonestly.com` (admin) are bound and DNS/Cloudflare is pointing at them. CORS
  will reject requests from any origin not in the list.

## 3. Email de-duplication and case-insensitive unique index (for #3)

The app now normalizes email to lowercase everywhere, but existing dev/prod data may already contain
case-variant duplicates (e.g. `Bob@x.com` and `bob@x.com` as separate accounts) from before this fix.
`ddl-auto: update` cannot create a functional unique index on `LOWER(email)` — this project has no
Flyway/Liquibase, so this must be run by hand. The users table is **`app_users`**, not `users`. Run
against **dev first, then prod**.

### 3a. Find duplicates (run this first, on both databases)

```sql
SELECT id, email, created_at, banned, verified_student,
       (SELECT COUNT(*) FROM reviews r WHERE r.user_id = u.id) AS review_count
FROM app_users u
WHERE LOWER(email) IN (
    SELECT LOWER(email) FROM app_users GROUP BY LOWER(email) HAVING COUNT(*) > 1
)
ORDER BY LOWER(email), created_at;
```

Look at the results before doing anything else. Deleting a duplicate account is destructive — `User`
has `cascade = CascadeType.ALL` on its reviews, so deleting the row deletes that user's reviews too.
For any group where one side clearly has real activity (reviews, `verified_student = true`) and the
other is an abandoned empty signup, prefer renaming the loser over deleting it (3b) so no data is lost.

### 3b. Resolve duplicates without deleting anything

Renames the losing row's email so it no longer collides, freeing up the unique index without touching
any reviews. Pick the id to rename from the query above (e.g. the account with 0 reviews / not verified
/ created later) — do **not** run this blind:

```sql
UPDATE app_users
SET email = email || '+dup-' || id
WHERE id = '<loser-id>';
```

If you'd rather not review each pair by hand and are fine with "oldest account per email wins,
everything else gets suffixed", this single statement does that automatically for every duplicate group
in one pass:

```sql
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY LOWER(email) ORDER BY created_at ASC) AS rn
    FROM app_users
)
UPDATE app_users u
SET email = u.email || '+dup-' || u.id
FROM ranked r
WHERE u.id = r.id AND r.rn > 1;
```

Re-run the query in 3a afterward — it should return zero rows.

### 3c. Add the index (after 3a confirms zero duplicates)

```sql
CREATE UNIQUE INDEX CONCURRENTLY uk_app_users_lower_email ON app_users (LOWER(email));
```

`CONCURRENTLY` avoids locking the table during index creation; run it outside a transaction block (most
`psql` sessions default to this — if using a tool that wraps DDL in a transaction, disable that).

## 4. Deploy order

1. Run the email dedup check and index (step 3) against dev, then prod.
2. Merge `security/prod-hotfixes` and let the GitHub Actions deploy run as normal.
3. Confirm boot (step 5).

## 5. Confirm the app booted correctly

- Check container logs: no "must be provided" / unresolved placeholder error at startup.
- Confirm `SecurityConfig` / auth logs are quiet — no `System.out`/stack-trace noise (audit finding #15).
- Spot-check CORS: a request from `https://curtinhonestly.com`, `https://www.curtinhonestly.com`, and
  `https://admin.curtinhonestly.com` succeeds; a request from an arbitrary third-party origin is rejected.
