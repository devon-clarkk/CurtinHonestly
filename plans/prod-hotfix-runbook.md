# Production hotfix runbook — secret rotation & DB changes

This runbook covers the manual, prod-side steps required by the `security/prod-hotfixes` branch.
None of these steps can be done in code — they must be run against Azure / the production database
by someone with access. **Do this before deploying the new backend image**, since the new image will
refuse to boot without `jwt-secret` and `database-password` set.

## 1. Pre-deploy environment check

Confirm the Azure Container App has the following env vars set (`az containerapp show` or the portal):

- `jwt-secret`
- `database-url`
- `database-username`
- `database-password`

The new build hard-fails on startup with an unresolved-placeholder error if `jwt-secret` or
`database-password` is missing — this is intentional (audit finding #1). Do not deploy until these
are confirmed set.

## 2. Rotate the JWT secret

The current JWT signing secret is committed to git history and must be treated as compromised.

1. Generate a new secret, at least 32 bytes, e.g.:
   ```
   openssl rand -base64 48
   ```
2. Set it as `jwt-secret` on the Container App:
   ```
   az containerapp update --name <CONTAINER_APP_NAME> --resource-group <RESOURCE_GROUP> \
     --set-env-vars jwt-secret="<new-secret>"
   ```
3. **Effect:** every existing JWT becomes invalid — all logged-in users will need to log in again.
   This is expected and acceptable.

## 3. Rotate the database password

The default DB password (`CurtinHonestly`) was also committed and must be rotated.

1. Change the Postgres user's password (via the Azure Postgres resource, or `ALTER ROLE ... WITH PASSWORD ...`
   if self-managed).
2. Update `database-password` on the Container App to match.
3. Coordinate this with the JWT rotation and the deploy below so the DB and app cut over together —
   an old app revision with the old password will fail to connect once the DB password changes.

## 5. Deploy order

1. Set/rotate `jwt-secret` and `database-password` on the Container App (steps 2–3 above).
2. Deploy the new backend image (`security/prod-hotfixes` merged, then normal CI/CD deploy).
3. Confirm boot (step 6).

## 6. Confirm the app booted with the new values

- Check container logs: no "must be provided" / unresolved placeholder error at startup.
- Log in as a test user and confirm a token is issued; confirm an **old** token (issued before rotation)
  is now rejected (401/403).
- Confirm `SecurityConfig` / auth logs are quiet — no `System.out`/stack-trace noise (audit finding #15).
