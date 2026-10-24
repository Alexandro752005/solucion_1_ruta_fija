-- Do not count INSERT ... ON CONFLICT DO NOTHING when no account was inserted.
-- V22 is retained unchanged; the lock/precheck still precedes every insertion.
CREATE OR REPLACE FUNCTION enforce_pilot_account_creation()
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
            RAISE EXCEPTION 'An account identity, role and organization require an authorized transition'
                USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_account_identity_immutable';
        END IF;
        RETURN NEW;
    END IF;
    role_key := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 2 ELSE 3 END;
    role_limit := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 4 ELSE 20 END;
    PERFORM pg_advisory_xact_lock(751844, role_key);
    IF EXISTS (SELECT 1 FROM public.pilot_account_creation WHERE user_id = NEW.id) THEN
        RAISE EXCEPTION 'A historical account identity cannot be recreated'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_creation_identity_once';
    END IF;
    IF (SELECT count(*) FROM public.pilot_account_creation WHERE role = NEW.role) >= role_limit THEN
        RAISE EXCEPTION 'Pilot historical creation limit exhausted for role %', NEW.role
            USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_historical_creation_limit';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION record_inserted_pilot_account()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public
AS $$
DECLARE
    role_key INTEGER;
    role_limit INTEGER;
BEGIN
    role_key := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 2 ELSE 3 END;
    role_limit := CASE NEW.role WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 4 ELSE 20 END;
    PERFORM pg_advisory_xact_lock(751844, role_key);
    -- AFTER ROW may run after all BEFORE ROW callbacks of a multi-row statement.
    -- Recheck at the authoritative write so one statement cannot exceed the cap.
    IF (SELECT count(*) FROM public.pilot_account_creation WHERE role = NEW.role) >= role_limit THEN
        RAISE EXCEPTION 'Pilot historical creation limit exhausted for role %', NEW.role
            USING ERRCODE = '23514', CONSTRAINT = 'ck_pilot_historical_creation_limit';
    END IF;
    INSERT INTO public.pilot_account_creation (user_id, role, created_at)
    VALUES (NEW.id, NEW.role, NEW.created_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_record_inserted_pilot_account
AFTER INSERT ON app_user
FOR EACH ROW EXECUTE FUNCTION record_inserted_pilot_account();
