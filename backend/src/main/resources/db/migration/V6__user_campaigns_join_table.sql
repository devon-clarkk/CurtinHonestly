-- Move users from a single campaign_id FK to a many-to-many membership, so a
-- user can be enrolled in several campaigns at once (multiple draws per link).
--
-- Flyway runs before Hibernate, so this creates + backfills the join table here
-- rather than leaving it to ddl-auto (which would create the table but could not
-- backfill it in the same boot). The @ManyToMany join columns in User.java match
-- this table exactly (user_campaigns.user_id / .campaign_id).
--
-- The old app_users.campaign_id column is deliberately NOT dropped: keeping it one
-- release makes this reversible, and ddl-auto=update never drops columns anyway. A
-- later migration can remove it once nothing reads it.
--
-- Guarded on table existence: on a fresh database app_users does not exist yet
-- (Hibernate creates it after Flyway), so there is nothing to migrate.

DO $$
BEGIN
    IF to_regclass('public.app_users') IS NULL OR to_regclass('public.campaigns') IS NULL THEN
        RAISE NOTICE 'app_users/campaigns absent - fresh database, skipping';
        RETURN;
    END IF;

    CREATE TABLE IF NOT EXISTS user_campaigns (
        user_id     varchar(255) NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
        campaign_id varchar(255) NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
        PRIMARY KEY (user_id, campaign_id)
    );

    -- Backfill existing single-campaign memberships. Idempotent via the PK.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'app_users' AND column_name = 'campaign_id'
    ) THEN
        INSERT INTO user_campaigns (user_id, campaign_id)
        SELECT id, campaign_id FROM app_users WHERE campaign_id IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
