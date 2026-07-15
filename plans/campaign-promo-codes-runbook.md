# Campaign promo codes runbook — deploy checks (PR #41 replacement)

This runbook covers the manual, prod-side steps required by `feat/campaign-promo-codes-v2`.
This project has no Flyway/Liquibase — schema changes ship via `ddl-auto: update`, so none of these
steps run automatically. They must be done by hand against the database before/after deploying.

## 1. Pre-deploy: check for duplicate (user_id, unit_id) reviews

The campaign work adds a unique constraint on `reviews (user_id, unit_id)` (`uk_reviews_user_unit`),
enforcing one review per user per unit — required so campaign entries can't be farmed by leaving
several reviews on the same unit. Nothing stopped duplicate reviews before this change, so prod's
`reviews` table may already contain some. **This has not been checked yet** — the email dedup work in
`plans/prod-hotfix-runbook.md` covered `app_users`, not `reviews`.

Run this against **dev first, then prod**, before deploying this branch:

```sql
SELECT user_id, unit_id, COUNT(*) AS review_count
FROM reviews
GROUP BY user_id, unit_id
HAVING COUNT(*) > 1;
```

If this returns zero rows, skip to step 2.

If it returns rows, Postgres will reject the `ALTER TABLE ... ADD CONSTRAINT` on boot. Depending on
Hibernate's handling this either fails the boot outright or — worse — logs the error and continues,
leaving the app running **without** the constraint while looking like a normal successful deploy (see
step 3). Resolve duplicates by keeping the most recent review per `(user_id, unit_id)` pair and deleting
the rest:

```sql
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, unit_id ORDER BY created_at DESC) AS rn
    FROM reviews
)
DELETE FROM reviews
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);
```

Deleting a review also triggers a unit aggregate recalculation on next read for unaffected units, but
won't happen automatically for historical deletes run directly in SQL — after running the delete, hit
`POST /admin/units/recalculate-aggregates` (admin-only) once to refresh `averageRating` etc. for any
affected units.

Re-run the `SELECT ... HAVING COUNT(*) > 1` query afterward — it should return zero rows.

## 2. Deploy order

1. Run the duplicate-review check (step 1) against dev, then prod.
2. Merge `feat/campaign-promo-codes-v2` and let the normal GitHub Actions deploy run.
3. Confirm boot (step 3).

## 3. Post-deploy: confirm the new schema actually applied

`ddl-auto: update` logs-and-continues on a failed DDL statement rather than failing the boot, so a
silently-skipped constraint or table looks identical to a successful deploy from the outside — the app
keeps serving traffic, campaigns just silently don't enforce what they're supposed to. Check the boot
logs for both:

- `campaigns` and `campaign_entries` tables were created (Hibernate logs each `create table` statement
  it runs at `DEBUG`/`INFO` depending on profile; grep for `create table campaigns` and
  `create table campaign_entries`).
- The unique constraint was added: grep for `uk_reviews_user_unit` in the boot log. If step 1 was done
  correctly this should succeed; if you see a constraint-violation error here instead, duplicates still
  exist — re-run the step 1 query and dedup again, then restart the app so Hibernate retries the DDL.

If either is missing from the logs with no corresponding error, something silently no-opped — connect to
the database directly and confirm:

```sql
SELECT to_regclass('campaigns'), to_regclass('campaign_entries');
SELECT conname FROM pg_constraint WHERE conname = 'uk_reviews_user_unit';
```

All three should return non-null / one row. If not, the app is running with campaign features exposed
in the API but without the backing schema — treat as a failed deploy and investigate before letting
traffic route to campaign endpoints.
