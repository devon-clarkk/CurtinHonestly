-- With multi-campaign membership, one review can back a draw entry in EACH
-- campaign the user is enrolled in, so campaign_entries.review_id is no longer
-- one-to-one. Drop the auto-named UNIQUE constraint that enforced one entry per
-- review; intra-campaign duplicates are still prevented by the per-(campaign,
-- user, unit) gate in CampaignService plus one-review-per-unit.
--
-- The constraint name is Hibernate-generated (e.g. uk3xenv0i797yi9bbpl2qur1979),
-- so it is discovered dynamically rather than named. Guarded on table existence:
-- on a fresh database campaign_entries does not exist yet (Hibernate creates it
-- after Flyway, already without the unique constraint), so there is nothing to drop.

DO $$
DECLARE
    cname text;
BEGIN
    IF to_regclass('public.campaign_entries') IS NULL THEN
        RAISE NOTICE 'campaign_entries absent - fresh database, skipping';
        RETURN;
    END IF;

    FOR cname IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE rel.relname = 'campaign_entries'
          AND con.contype = 'u'
          AND att.attname = 'review_id'
          AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE campaign_entries DROP CONSTRAINT %I', cname);
        RAISE NOTICE 'Dropped unique constraint % on campaign_entries.review_id', cname;
    END LOOP;
END $$;
