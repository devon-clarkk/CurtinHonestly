# Production hotfix runbook — secret rotation & DB changes

This runbook covers the manual, prod-side steps required by the `security/prod-hotfixes` branch.
None of these steps can be done in code — they must be run against GitHub/ADO and the production
database by someone with access. **Do this before deploying the new backend image**, since the new
image will refuse to boot without `jwt-secret` and `database-password` set.

## 0. Important: rotate the secret at its source, not just on the Container App

**Both deploy pipelines push secrets into the Container App on every run**, overwriting whatever is
currently set there:

- `.github/workflows/deploy-backend.yml` sets `jwt-secret`/`database-password` from GitHub
  **Environment** secrets (`secrets.JWT_SECRET`, `secrets.DATABASE_PASSWORD`) scoped to the `dev` and
  `prod` GitHub Environments (repo Settings → Environments → `dev` / `prod` → Secrets).
- `.azuredevops/pipelines/backend.yml` sets them from ADO pipeline variables
  (`JWT_SECRET_DEV`/`JWT_SECRET_PROD`, `DATABASE_PASSWORD_DEV`/`DATABASE_PASSWORD_PROD`), typically in
  a variable group under Pipelines → Library.

If you only rotate the value directly on the Container App (e.g. via `az containerapp update`), the
**next automatic deploy** (any push to `dev`/`main`, from either pipeline) will silently overwrite it
back to the old, burned value with no error and no warning. Since it's not yet decided which pipeline
is canonical (see the PR note on the #14 commit), **update the secret in both GitHub Environments and
the ADO variable group**, not just Azure directly. This requires repo-admin / ADO project-admin access
that only your team has — it cannot be done from this branch or by an agent.

## 1. Pre-deploy environment check

Confirm the following are set as GitHub Environment secrets (`dev` and `prod`) **and** ADO pipeline
variables (`_DEV`/`_PROD` suffixed), and that the live Container App reflects them
(`az containerapp show` or the portal):

- `jwt-secret` / `JWT_SECRET`
- `database-url` / `DATABASE_URL`
- `database-username` / `DATABASE_USERNAME`
- `database-password` / `DATABASE_PASSWORD`
- `CORS_ALLOWED_ORIGINS` — only needed if the allowlist should differ from the built-in prod default
  (`https://curtinhonestly.com,https://admin.curtinhonestly.com`). Confirm the SWA custom domains for
  `curtinhonestly.com` (student) and `admin.curtinhonestly.com` (admin) are bound and DNS/Cloudflare is
  pointing at them — CORS will reject requests from any origin not in this list.

The new build hard-fails on startup with an unresolved-placeholder error if `jwt-secret` or
`database-password` is missing — this is intentional (audit finding #1). Do not deploy until these
are confirmed set at the source (step 0), not just on the live Container App.

## 2. Rotate the JWT secret

The current JWT signing secret is committed to git history and must be treated as compromised.

1. Generate a new secret, at least 32 bytes, e.g.:
   ```
   openssl rand -base64 48
   ```
2. Update it in **both** places so neither pipeline reverts the other:
   - GitHub: Settings → Environments → `dev`/`prod` → update the `JWT_SECRET` environment secret.
   - ADO: Pipelines → Library → update `JWT_SECRET_DEV` / `JWT_SECRET_PROD` in the variable group.
   - Optionally also set it directly on the Container App (`az containerapp update ... --set-env-vars
     jwt-secret="<new-secret>"`) for immediate effect before the next deploy runs — but treat this as a
     stopgap only; the CI secret stores above are the source of truth.
3. **Effect:** every existing JWT becomes invalid — all logged-in users will need to log in again.
   This is expected and acceptable.

## 3. Rotate the database password

The default DB password (`CurtinHonestly`) was also committed and must be rotated.

1. Change the Postgres user's password (via the Azure Postgres resource, or `ALTER ROLE ... WITH PASSWORD ...`
   if self-managed).
2. Update `DATABASE_PASSWORD` / `database-password` in the same two places as step 2 (GitHub Environment
   secrets and the ADO variable group), plus the Container App directly if you need immediate effect.
3. Coordinate this with the JWT rotation and the deploy below so the DB and app cut over together —
   an old app revision with the old password will fail to connect once the DB password changes.

## 4. Email de-duplication and case-insensitive unique index (for #3)

The app now normalizes email to lowercase everywhere, but existing prod data may already contain
case-variant duplicates (e.g. `Bob@x.com` and `bob@x.com` as separate accounts) from before this fix.
`ddl-auto: update` cannot create a functional unique index on `LOWER(email)` — this project has no
Flyway/Liquibase, so this must be run by hand against the production database. The users table is
**`app_users`**, not `users`.

1. **Run this first** — it must return zero rows before proceeding:
   ```sql
   SELECT LOWER(email), COUNT(*) FROM app_users GROUP BY 1 HAVING COUNT(*) > 1;
   ```
2. If it returns rows, resolve each duplicate manually (merge or rename one of the accounts) before continuing.
3. Once zero duplicates are confirmed, add the case-insensitive unique index:
   ```sql
   CREATE UNIQUE INDEX CONCURRENTLY uk_app_users_lower_email ON app_users (LOWER(email));
   ```
   `CONCURRENTLY` avoids locking the table during index creation; run it outside a transaction block
   (most `psql` sessions default to this — if using a tool that wraps DDL in a transaction, disable that).

## 5. Deploy order

1. Rotate `jwt-secret` and `database-password` **in GitHub Environment secrets and the ADO variable
   group** (steps 2–3 above) — not just the live Container App.
2. Run the email dedup check and index (step 4) against prod.
3. Merge `security/prod-hotfixes` and let CI/CD deploy the new image — this deploy will also push the
   rotated secrets to the Container App as part of its normal `--set-env-vars` step.
4. Confirm boot (step 6).

## 6. Confirm the app booted with the new values

- Check container logs: no "must be provided" / unresolved placeholder error at startup.
- Log in as a test user and confirm a token is issued; confirm an **old** token (issued before rotation)
  is now rejected (401/403).
- Confirm `SecurityConfig` / auth logs are quiet — no `System.out`/stack-trace noise (audit finding #15).
- `az containerapp show` (or the portal) to confirm the live env vars match what you set in step 2/3 —
  if a second, un-updated pipeline deploys later, it will silently revert them, so re-check after any
  subsequent deploy until the team picks one canonical pipeline (see the #14 commit note).
