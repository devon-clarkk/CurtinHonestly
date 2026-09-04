-- Allows ROLE_CLUB in app_user_roles.roles.
--
-- Hibernate emits a CHECK constraint for every @Enumerated(STRING) column
-- listing the constants that existed when the table was created, and
-- ddl-auto=update never alters an existing constraint. Adding UserRole.ROLE_CLUB
-- therefore left dev and prod rejecting the new value at insert time while a
-- fresh database accepted it. Recreating the constraint with the full set keeps
-- both in step; a fresh database gets the same definition from Hibernate.
--
-- Guarded on table existence like V2 to V5: on a fresh database the table does
-- not exist yet when Flyway runs.

DO $$
BEGIN
    IF to_regclass('public.app_user_roles') IS NULL THEN
        RAISE NOTICE 'app_user_roles table absent - fresh database, skipping';
        RETURN;
    END IF;

    ALTER TABLE app_user_roles DROP CONSTRAINT IF EXISTS app_user_roles_roles_check;
    ALTER TABLE app_user_roles
        ADD CONSTRAINT app_user_roles_roles_check
        CHECK (roles IN ('ROLE_USER', 'ROLE_ADMIN', 'ROLE_CLUB'));
END $$;
