-- Adds app_users.tokens_valid_after, the cut-off that lets a credential change
-- invalidate JWTs that were already issued (security audit finding #4).
--
-- JWTs are stateless with a 7-day TTL, so before this column a password reset
-- did not actually end the attacker's session: a stolen token stayed valid for
-- up to a week after the victim "secured" the account. The filter now rejects
-- any token whose `iat` predates this stamp.
--
-- Deliberately NULLABLE. A NOT NULL column cannot be added to a populated table
-- without a DEFAULT (this is exactly the reviews.like_count failure documented
-- in this directory's README), and there is no honest default here: existing
-- sessions were never invalidated, so NULL means "no cut-off, accept any token"
-- and every existing row keeps working. The column only becomes non-NULL the
-- first time that user resets a password or changes their email.
--
-- Guarded on table existence for the same reason as V2/V3/V4: Flyway runs before
-- Hibernate, so on a fresh database app_users does not exist yet and Hibernate
-- creates the column from the entity instead.

DO $$
BEGIN
    IF to_regclass('public.app_users') IS NULL THEN
        RAISE NOTICE 'app_users table absent - fresh database, skipping';
        RETURN;
    END IF;

    ALTER TABLE app_users ADD COLUMN IF NOT EXISTS tokens_valid_after timestamptz;
END $$;
