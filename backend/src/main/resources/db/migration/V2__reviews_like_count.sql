-- Adds reviews.like_count, which ddl-auto=update failed to create.
--
-- Review.likeCount was introduced with @Column(nullable = false) and no columnDefinition.
-- Hibernate emitted `ALTER TABLE reviews ADD COLUMN like_count integer NOT NULL` against an
-- already-populated table; Postgres rejects that without a DEFAULT, Hibernate logged the
-- failure and booted anyway, and every SELECT on reviews failed from then on. GET /units kept
-- working because it reads denormalized aggregates on `units` and never touches `reviews`.
--
-- Guarded on table existence because Flyway runs before Hibernate: on a brand-new database
-- (local dev, CI) `reviews` does not exist yet and this must be a no-op. Hibernate then creates
-- the column correctly from the entity, which now carries the columnDefinition default.
-- The guards come out once V1__baseline.sql lands and ddl-auto moves to `validate`.

DO $$
BEGIN
    IF to_regclass('public.reviews') IS NULL THEN
        RAISE NOTICE 'reviews table absent - fresh database, skipping';
        RETURN;
    END IF;

    ALTER TABLE reviews ADD COLUMN IF NOT EXISTS like_count integer NOT NULL DEFAULT 0;

    -- Backfill from the source of truth.
    IF to_regclass('public.review_likes') IS NOT NULL THEN
        UPDATE reviews r
        SET like_count = (
            SELECT count(*) FROM review_likes l WHERE l.review_id = r.id
        );
    END IF;
END $$;
