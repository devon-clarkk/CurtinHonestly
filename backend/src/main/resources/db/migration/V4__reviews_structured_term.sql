-- Replaces the free-form reviews.semester_taken string with a structured
-- (term_type, term_year) pair.
--
-- The old column stored a display label, which meant it could not be sorted,
-- filtered, or aggregated, and it had already drifted into two incompatible
-- formats in dev data: "Semester 1, 2026" from the dropdown and "Sem 1 2024"
-- from seeding. It also froze a presentation choice into the database - the
-- label format has already changed once in the frontend.
--
-- semester_taken is NOT dropped here. It keeps the backfilled history readable
-- for one release so this migration is reversible; a later migration removes it
-- once nothing reads it.
--
-- Guarded on table existence for the same reason as V2 and V3: Flyway runs
-- before Hibernate, so on a fresh database there is nothing to alter yet.

DO $$
BEGIN
    IF to_regclass('public.reviews') IS NULL THEN
        RAISE NOTICE 'reviews table absent - fresh database, skipping';
        RETURN;
    END IF;

    ALTER TABLE reviews ADD COLUMN IF NOT EXISTS term_type varchar(32);
    ALTER TABLE reviews ADD COLUMN IF NOT EXISTS term_year integer;

    -- Inside the guard, not after it: on a fresh database the table does not
    -- exist yet and a bare CREATE INDEX would fail the migration.
    CREATE INDEX IF NOT EXISTS idx_reviews_term ON reviews (term_year, term_type);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reviews' AND column_name = 'semester_taken'
    ) THEN
        RAISE NOTICE 'semester_taken already removed - nothing to backfill';
        RETURN;
    END IF;

    -- Backfill. Both known formats share the same shape once punctuation is
    -- ignored: a term word, a number, and a year. Matching on that rather than
    -- on the exact label means "Semester 1, 2026", "Sem 1 2024", and "sem1 2024"
    -- all land correctly.
    --
    -- \y, not \b. Postgres regular expressions are POSIX ARE, where the
    -- word-boundary escape is \y and \b means a literal backspace character.
    -- Written with \b these patterns match nothing at all and every row silently
    -- falls through to the unparseable branch.
    UPDATE reviews
    SET term_type = 'SEMESTER_1',
        term_year = (regexp_match(semester_taken, '(\d{4})'))[1]::integer
    WHERE term_type IS NULL
      AND semester_taken ~* '^\s*sem(ester)?\s*\.?\s*1\y'
      AND semester_taken ~ '\d{4}';

    UPDATE reviews
    SET term_type = 'SEMESTER_2',
        term_year = (regexp_match(semester_taken, '(\d{4})'))[1]::integer
    WHERE term_type IS NULL
      AND semester_taken ~* '^\s*sem(ester)?\s*\.?\s*2\y'
      AND semester_taken ~ '\d{4}';

    -- "Summer, 2024/25" - the stored year is the one the term ENDS in, which is
    -- the first four-digit group plus one. "Summer, 2024/25" therefore becomes
    -- SUMMER/2025, matching how the enum documents the field.
    UPDATE reviews
    SET term_type = 'SUMMER',
        term_year = ((regexp_match(semester_taken, '(\d{4})'))[1]::integer) + 1
    WHERE term_type IS NULL
      AND semester_taken ~* '^\s*summer\y'
      AND semester_taken ~ '\d{4}';

    -- Anything left with a value we could not parse. Left as NULL rather than
    -- guessed: a wrong year is worse than a missing one for aggregate queries.
    -- The original string survives in semester_taken until it is dropped.
    IF EXISTS (
        SELECT 1 FROM reviews
        WHERE term_type IS NULL
          AND semester_taken IS NOT NULL
          AND btrim(semester_taken) <> ''
    ) THEN
        RAISE WARNING 'reviews: % row(s) had an unparseable semester_taken and were left NULL',
            (SELECT count(*) FROM reviews
             WHERE term_type IS NULL
               AND semester_taken IS NOT NULL
               AND btrim(semester_taken) <> '');
    END IF;
END $$;
