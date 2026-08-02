# Database migrations

Flyway owns schema changes. It runs before Hibernate on every boot.

## Why this exists

`ddl-auto: update` is a best-effort diff, not a migration engine. It adds tables and columns.
It never drops, alters, renames, retypes, relaxes nullability, or backfills. It has produced two
separate production-shaped failures in this repo:

- **`reviews.like_count`** — `update` attempted `ADD COLUMN ... NOT NULL` against a populated
  table, Postgres rejected it for want of a `DEFAULT`, Hibernate logged the failure and booted
  anyway. Every `SELECT` on `reviews` failed afterwards. Dev unit detail pages returned 500 for
  roughly two weeks while `GET /units` kept returning 200, because the list endpoint reads
  denormalized aggregates on `units` and never touches `reviews`.
- **`reviews.user_id`** — `update` never attempts to drop a NOT NULL constraint. There is no
  error to find in any log; the code simply disagrees with the database.

Note that these are different failure modes. Better DDL error logging would have surfaced the
first and been useless against the second.

## Current state — transitional

Hibernate still owns table and column *creation* (`ddl-auto: update`). Flyway owns the changes
`update` cannot make. Migrations are guarded with `to_regclass(...) IS NULL` early-returns so they
no-op on a fresh database, where Flyway runs before Hibernate has created anything.

## Finishing the migration

The end state is `ddl-auto: validate`, which fails the boot on any drift between entities and
schema instead of silently corrupting it. That needs a baseline first.

**Do not set `validate` before `V1__baseline.sql` exists.** It will refuse to boot.

1. Dump the real production schema — the source of truth, not the entity definitions, because
   the entities are what drifted:

   ```bash
   pg_dump --schema-only --no-owner --no-privileges "$PROD_DATABASE_URL" \
     > backend/src/main/resources/db/migration/V1__baseline.sql
   ```

2. Reconcile: apply V2 and V3 to a scratch copy of prod, then diff that schema against what
   Hibernate generates from the entities. Anything left over becomes `V4__`, `V5__`, and so on.

3. Drop the `to_regclass` guards from V2 and V3 — with a baseline in place, a fresh database gets
   its tables from V1 before V2 runs.

4. Switch `spring.jpa.hibernate.ddl-auto` to `validate` in `application.yml`.

5. Add a CI gate. `spring-boot-testcontainers` and `testcontainers:postgresql` are already test
   dependencies: boot a `@SpringBootTest` against a throwaway `PostgreSQLContainer`, let Flyway
   migrate, and let Hibernate validate. Drift then fails the build rather than the deploy.

Note that `application-prod.yml` does not override `ddl-auto`, so production is running `update`
against the live database today. It has escaped both failures only because its `reviews` table is
empty.

## Conventions

- `V<n>__snake_case_description.sql`, sequential, never renumbered.
- Applied migrations are immutable. Flyway checksums them; edit one that has run anywhere and the
  next boot fails validation. Fix forward with a new version.
- Every `nullable = false` column needs a `columnDefinition` carrying a DB-level `DEFAULT`, or it
  cannot be added to a populated table. This is the convention `e4ee43d` established and
  `0b9ad8e` missed.
