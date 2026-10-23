-- DC-02B: global lifetime creations, including inactive and pending accounts.
-- Deliberately no FK: deleting an account must never return its historical slot.
CREATE TABLE pilot_account_creation (
    user_id UUID PRIMARY KEY,
    role VARCHAR(30) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'CONDUCTOR')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pilot_creation_identity_role UNIQUE (user_id, role)
);
CREATE INDEX ix_pilot_creation_role ON pilot_account_creation (role);

INSERT INTO pilot_account_creation (user_id, role, created_at)
SELECT id, role, created_at FROM app_user;

-- An existing installation exceeding the approved pilot scope needs reconciliation;
-- migration must not silently erase history or label excess accounts compliant.
DO $$
BEGIN
    IF EXISTS (
        SELECT role FROM pilot_account_creation GROUP BY role
        HAVING count(*) > CASE role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 4 ELSE 20 END
    ) THEN
        RAISE EXCEPTION 'Existing account history exceeds approved pilot creation limits'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION enforce_pilot_account_creation()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
DECLARE
    role_key INTEGER;
    role_limit INTEGER;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.role IS DISTINCT FROM OLD.role
           OR NEW.organization_id IS DISTINCT FROM OLD.organization_id THEN
            RAISE EXCEPTION 'An account identity, role and organization are immutable'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_account_identity_immutable';
        END IF;
        RETURN NEW;
    END IF;

    role_key := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 2 ELSE 3 END;
    role_limit := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 4 ELSE 20 END;
    -- Every insertion path (repositories, provisioning and bootstrap) shares this lock.
    PERFORM pg_advisory_xact_lock(751844, role_key);
    IF EXISTS (SELECT 1 FROM public.pilot_account_creation WHERE user_id = NEW.id) THEN
        RAISE EXCEPTION 'A historical account identity cannot be recreated'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_creation_identity_once';
    END IF;
    IF (SELECT count(*) FROM public.pilot_account_creation WHERE role = NEW.role) >= role_limit THEN
        RAISE EXCEPTION 'Pilot historical creation limit exhausted for role %', NEW.role
            USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_historical_creation_limit';
    END IF;
    INSERT INTO public.pilot_account_creation (user_id, role, created_at)
    VALUES (NEW.id, NEW.role, NEW.created_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pilot_account_creation
BEFORE INSERT OR UPDATE OF id, role, organization_id ON app_user
FOR EACH ROW EXECUTE FUNCTION enforce_pilot_account_creation();

CREATE FUNCTION protect_pilot_account_creation_history()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Pilot creation history is append-only and written by account insertion'
        USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_creation_history_append_only';
END;
$$;
CREATE TRIGGER trg_pilot_creation_history_append_only
BEFORE INSERT OR UPDATE OR DELETE ON pilot_account_creation
FOR EACH ROW EXECUTE FUNCTION protect_pilot_account_creation_history();

REVOKE ALL ON pilot_account_creation FROM PUBLIC;
DO $$
DECLARE account_role TEXT;
BEGIN
    FOREACH account_role IN ARRAY ARRAY['rf_app', 'rf_test'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = account_role) THEN
            EXECUTE format('REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.pilot_account_creation FROM %I', account_role);
            EXECUTE format('GRANT SELECT ON public.pilot_account_creation TO %I', account_role);
        END IF;
    END LOOP;
END;
$$;
