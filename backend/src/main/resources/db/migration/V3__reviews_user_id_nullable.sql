-- Relaxes reviews.user_id to nullable, which ddl-auto=update cannot do at all.
--
-- Roadmap 1.4 (anonymize-instead-of-delete) changes Review.user to a nullable @JoinColumn with
-- @OnDelete(SET_NULL), and UserService.deleteAccount() severs authorship with
-- `reviews.forEach(review -> review.setUser(null))`. The column was created NOT NULL by the
-- previous entity definition, and ddl-auto=update only ever *adds* — it never drops a
-- constraint. Without this migration the first account deletion fails on a not-null violation,
-- with no prior warning in any log because Hibernate never attempts the change.
--
-- Safe to apply ahead of the feature: relaxing a constraint cannot invalidate existing rows.
-- Guarded on table existence for the same reason as V2 — Flyway runs before Hibernate.

DO $$
BEGIN
    IF to_regclass('public.reviews') IS NULL THEN
        RAISE NOTICE 'reviews table absent - fresh database, skipping';
        RETURN;
    END IF;

    ALTER TABLE reviews ALTER COLUMN user_id DROP NOT NULL;
END $$;
