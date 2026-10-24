-- A witnessed data-entry correction is distinct from replacing a person or
-- rewriting a legacy Driver. V1..V50 and the shared immutable guards stay intact.
CREATE TABLE person_identity_review_correction (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    driver_id UUID NOT NULL REFERENCES driver(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    reviewer_user_id UUID NOT NULL REFERENCES app_user(id),
    previous_person_hash CHAR(64) NOT NULL,
    corrected_person_hash CHAR(64) NOT NULL,
    old_review_metadata JSONB NOT NULL,
    review_method VARCHAR(32) NOT NULL,
    review_reference VARCHAR(160) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    transaction_id BIGINT NOT NULL,
    backend_pid INTEGER NOT NULL,
    CONSTRAINT uq_identity_review_correction_operation UNIQUE(reviewer_user_id,operation_key),
    CONSTRAINT ck_identity_review_correction_hashes CHECK (
        previous_person_hash ~ '^[0-9a-f]{64}$' AND corrected_person_hash ~ '^[0-9a-f]{64}$'
        AND payload_hash ~ '^[0-9a-f]{64}$' AND previous_person_hash<>corrected_person_hash),
    CONSTRAINT ck_identity_review_correction_old CHECK (jsonb_typeof(old_review_metadata)='object'),
    CONSTRAINT ck_identity_review_correction_method CHECK (review_method IN('IN_PERSON','PRIOR_SECURE_CONTACT')),
    CONSTRAINT ck_identity_review_correction_reference CHECK (length(btrim(review_reference))>0),
    CONSTRAINT ck_identity_review_correction_reason CHECK (length(btrim(reason))>0),
    CONSTRAINT ck_identity_review_correction_operation CHECK (length(operation_key) BETWEEN 8 AND 100)
);
CREATE INDEX ix_identity_review_correction_user ON person_identity_review_correction(user_id,corrected_at,id);
COMMENT ON TABLE person_identity_review_correction IS
    'Append-only witnessed correction of a pre-provisioning CONDUCTOR review. No raw DNI, credentials, OTP or identity adoption.';

CREATE FUNCTION correct_unprovisioned_conductor_identity(
    p_id UUID,p_actor UUID,p_user UUID,p_driver UUID,p_org UUID,
    p_expected_hash TEXT,p_corrected_hash TEXT,p_method TEXT,p_reference TEXT,p_reason TEXT,
    p_operation_key TEXT,p_payload_hash TEXT,p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE actor_row public.app_user%ROWTYPE;target_row public.app_user%ROWTYPE;
        driver_row public.driver%ROWTYPE;old_identity public.person_identity%ROWTYPE;
        prior public.person_identity_review_correction%ROWTYPE;terms_row public.admin_terms_document%ROWTYPE;
BEGIN
    IF p_id IS NULL OR p_actor IS NULL OR p_user IS NULL OR p_driver IS NULL OR p_org IS NULL
       OR p_expected_hash IS NULL OR p_expected_hash !~ '^[0-9a-f]{64}$'
       OR p_corrected_hash IS NULL OR p_corrected_hash !~ '^[0-9a-f]{64}$'
       OR p_payload_hash IS NULL OR p_payload_hash !~ '^[0-9a-f]{64}$'
       OR p_method IS NULL OR p_method NOT IN('IN_PERSON','PRIOR_SECURE_CONTACT')
       OR p_reference IS NULL OR length(btrim(p_reference)) NOT BETWEEN 1 AND 160
       OR p_reason IS NULL OR length(btrim(p_reason)) NOT BETWEEN 1 AND 500
       OR p_operation_key IS NULL OR length(p_operation_key) NOT BETWEEN 8 AND 100 OR p_now IS NULL
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_COMMAND_INVALID' USING ERRCODE='23514'; END IF;
    IF p_reference ~ '[0-9]{6,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
       OR p_reason ~ '[0-9]{6,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_REFERENCE_MUST_NOT_CONTAIN_PERSONAL_DATA' USING ERRCODE='23514'; END IF;

    -- Same order as CurrentUserProvider writes: organization -> actor -> target -> Driver.
    PERFORM 1 FROM public.organization WHERE id=p_org AND status='ACTIVE' FOR NO KEY UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_ORGANIZATION_NOT_ACTIVE' USING ERRCODE='23514'; END IF;
    SELECT * INTO actor_row FROM public.app_user WHERE id=p_actor FOR UPDATE;
    IF NOT FOUND OR actor_row.role<>'ADMIN' OR NOT actor_row.active OR actor_row.email_verified_at IS NULL
       OR actor_row.organization_id IS DISTINCT FROM p_org
       OR NOT EXISTS(SELECT 1 FROM public.person_identity WHERE user_id=p_actor)
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_REVIEWER_NOT_AUTHORIZED' USING ERRCODE='23514'; END IF;

    -- The production document is exact immutable UTF-8 content, never a synthetic fallback.
    SELECT * INTO terms_row FROM public.admin_terms_document
      WHERE document_key='ADMIN_TERMS' AND NOT synthetic ORDER BY created_at DESC,id DESC LIMIT 1;
    IF NOT FOUND OR terms_row.content_sha256<>encode(sha256(convert_to(terms_row.content,'UTF8')),'hex')
       OR NOT EXISTS(SELECT 1 FROM public.admin_terms_acceptance a
          WHERE a.admin_user_id=p_actor AND a.organization_id=p_org AND a.document_id=terms_row.id
            AND a.document_version=terms_row.version AND a.content_sha256=terms_row.content_sha256)
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_CURRENT_TERMS_REQUIRED' USING ERRCODE='23514'; END IF;

    SELECT * INTO prior FROM public.person_identity_review_correction
      WHERE reviewer_user_id=p_actor AND operation_key=p_operation_key;
    IF FOUND THEN
        IF prior.user_id IS DISTINCT FROM p_user OR prior.driver_id IS DISTINCT FROM p_driver
           OR prior.organization_id IS DISTINCT FROM p_org OR prior.payload_hash<>p_payload_hash
           OR prior.corrected_person_hash<>p_corrected_hash
           OR NOT EXISTS(SELECT 1 FROM public.person_identity WHERE user_id=p_user AND person_hash=p_corrected_hash)
        THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_OPERATION_CONFLICT' USING ERRCODE='23514'; END IF;
        RETURN prior.id;
    END IF;

    SELECT * INTO target_row FROM public.app_user WHERE id=p_user FOR UPDATE;
    IF NOT FOUND OR NOT target_row.active OR target_row.role<>'CONDUCTOR'
       OR target_row.organization_id IS DISTINCT FROM p_org
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_TARGET_NOT_ALLOWED' USING ERRCODE='23514'; END IF;
    SELECT * INTO driver_row FROM public.driver WHERE id=p_driver AND organization_id=p_org FOR UPDATE;
    IF NOT FOUND OR driver_row.user_id IS DISTINCT FROM p_user OR driver_row.active
       OR upper(driver_row.document_type)<>'DNI' OR driver_row.document_number !~ '^[0-9]{8}$'
       OR driver_row.availability_status IN('RESERVADO','EN_SERVICIO','ATENCION')
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_LEGACY_DRIVER_NOT_ELIGIBLE' USING ERRCODE='23514'; END IF;

    -- Both person locks are nonblocking; never wait on another actor while holding this actor.
    IF NOT pg_catalog.pg_try_advisory_xact_lock(pg_catalog.hashtextextended('person-identity:'||least(p_expected_hash,p_corrected_hash),0))
       OR NOT pg_catalog.pg_try_advisory_xact_lock(pg_catalog.hashtextextended('person-identity:'||greatest(p_expected_hash,p_corrected_hash),0))
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_PERSON_BUSY' USING ERRCODE='23514'; END IF;
    SELECT * INTO old_identity FROM public.person_identity WHERE user_id=p_user FOR UPDATE;
    IF NOT FOUND OR old_identity.person_hash<>p_expected_hash OR old_identity.document_type<>'DNI'
       OR p_corrected_hash=p_expected_hash OR p_now<old_identity.reviewed_at
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_EXPECTED_REVIEW_CHANGED' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM public.person_identity WHERE person_hash=p_corrected_hash AND user_id<>p_user)
       OR EXISTS(SELECT 1 FROM public.driver WHERE id<>p_driver AND
             (user_id=p_user OR (upper(document_type)='DNI' AND document_number=driver_row.document_number)))
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_PERSON_ALREADY_REGISTERED' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM public.conductor_person_binding WHERE user_id=p_user OR driver_id=p_driver
          OR person_hash IN(p_expected_hash,p_corrected_hash))
       OR EXISTS(SELECT 1 FROM public.conductor_common_person_bridge
          WHERE person_hash IN(p_expected_hash,p_corrected_hash) OR common_person_hash IN(p_expected_hash,p_corrected_hash))
       OR EXISTS(SELECT 1 FROM public.conductor_onboarding_request WHERE linked_user_id=p_user OR linked_driver_id=p_driver)
       OR EXISTS(SELECT 1 FROM public.assignment WHERE driver_id=p_driver AND status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO'))
       OR EXISTS(SELECT 1 FROM public.service_execution_segment WHERE driver_id=p_driver AND ended_at IS NULL)
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_REQUIRES_UNBOUND_UNPROVISIONED_IDLE_ACCOUNT' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM public.retention_personal_retirement WHERE resource_id=p_user
          AND category IN('ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS'))
    THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_RETIRED_ACCOUNT_NOT_ALLOWED' USING ERRCODE='23514'; END IF;

    INSERT INTO public.person_identity_review_correction
       (id,user_id,driver_id,organization_id,reviewer_user_id,previous_person_hash,corrected_person_hash,
        old_review_metadata,review_method,review_reference,reason,corrected_at,operation_key,payload_hash,
        transaction_id,backend_pid)
    VALUES(p_id,p_user,p_driver,p_org,p_actor,p_expected_hash,p_corrected_hash,to_jsonb(old_identity),
        p_method,btrim(p_reference),btrim(p_reason),p_now,p_operation_key,p_payload_hash,pg_catalog.txid_current(),pg_catalog.pg_backend_pid());
    UPDATE public.person_identity SET person_hash=p_corrected_hash,reviewed_by_user_id=p_actor,
        review_method=p_method,review_reference=btrim(p_reference),reviewed_at=p_now
      WHERE user_id=p_user AND person_hash=p_expected_hash;
    IF NOT FOUND THEN RAISE EXCEPTION 'IDENTITY_CORRECTION_COMPARE_AND_SET_FAILED' USING ERRCODE='23514'; END IF;
    -- A correction grants neither email verification, documentary approval nor operational access.
    UPDATE public.app_user SET session_version=session_version+1 WHERE id=p_user;
    UPDATE public.refresh_token SET revoked_at=p_now WHERE user_id=p_user AND revoked_at IS NULL;
    RETURN p_id;
END $$;

CREATE FUNCTION protect_person_identity_review_correction() RETURNS trigger LANGUAGE plpgsql
SET search_path=pg_catalog,public AS $$
DECLARE correction_owner NAME;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Identity correction history is append-only' USING ERRCODE='23514'; END IF;
    SELECT pg_catalog.pg_get_userbyid(proowner) INTO correction_owner FROM pg_catalog.pg_proc
      WHERE oid='public.correct_unprovisioned_conductor_identity(uuid,uuid,uuid,uuid,uuid,text,text,text,text,text,text,text,timestamp with time zone)'::regprocedure;
    IF current_user<>correction_owner OR NEW.transaction_id<>pg_catalog.txid_current() OR NEW.backend_pid<>pg_catalog.pg_backend_pid()
       OR NOT EXISTS(SELECT 1 FROM public.person_identity p WHERE p.user_id=NEW.user_id
           AND p.person_hash=NEW.previous_person_hash AND to_jsonb(p)=NEW.old_review_metadata)
    THEN RAISE EXCEPTION 'Identity correction history requires its exact controlled function' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER person_identity_review_correction_append_guard BEFORE INSERT OR UPDATE OR DELETE
    ON person_identity_review_correction FOR EACH ROW EXECUTE FUNCTION protect_person_identity_review_correction();
CREATE TRIGGER person_identity_review_correction_truncate_guard BEFORE TRUNCATE
    ON person_identity_review_correction FOR EACH STATEMENT EXECUTE FUNCTION protect_person_identity_review_correction();

CREATE FUNCTION controlled_person_identity_mutation_guard() RETURNS trigger LANGUAGE plpgsql
SET search_path=pg_catalog,public AS $$
DECLARE correction_owner NAME;
BEGIN
    -- Preserve the existing exact, expired L-07 retirement authority. It is not
    -- a correction permission and cannot be supplied through application GUCs.
    IF public.retention_personal_row_is_authorized('person_identity',OLD.person_hash,TG_OP) THEN
        IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
    END IF;
    IF TG_OP='UPDATE' THEN
        SELECT pg_catalog.pg_get_userbyid(proowner) INTO correction_owner FROM pg_catalog.pg_proc
          WHERE oid='public.correct_unprovisioned_conductor_identity(uuid,uuid,uuid,uuid,uuid,text,text,text,text,text,text,text,timestamp with time zone)'::regprocedure;
        IF current_user=correction_owner AND OLD.user_id=NEW.user_id AND OLD.document_type=NEW.document_type
           AND EXISTS(SELECT 1 FROM public.person_identity_review_correction c
                WHERE c.user_id=OLD.user_id AND c.previous_person_hash=OLD.person_hash
                  AND c.corrected_person_hash=NEW.person_hash AND c.old_review_metadata=to_jsonb(OLD)
                  AND c.reviewer_user_id=NEW.reviewed_by_user_id AND c.review_method=NEW.review_method
                  AND c.review_reference=NEW.review_reference AND c.corrected_at=NEW.reviewed_at
                  AND c.transaction_id=pg_catalog.txid_current() AND c.backend_pid=pg_catalog.pg_backend_pid())
        THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'Identity review is immutable outside an exact controlled correction or retirement' USING ERRCODE='23514';
END $$;
DROP TRIGGER person_identity_retirement_guard ON person_identity;
CREATE TRIGGER person_identity_retirement_guard BEFORE UPDATE OR DELETE ON person_identity
    FOR EACH ROW EXECUTE FUNCTION controlled_person_identity_mutation_guard();

REVOKE ALL ON TABLE person_identity_review_correction FROM PUBLIC;
REVOKE ALL ON FUNCTION correct_unprovisioned_conductor_identity(UUID,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION protect_person_identity_review_correction() FROM PUBLIC;
REVOKE ALL ON FUNCTION controlled_person_identity_mutation_guard() FROM PUBLIC;
DO $$ DECLARE account_role TEXT;BEGIN
    FOREACH account_role IN ARRAY ARRAY['rf_app','rf_test'] LOOP
        IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=account_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.person_identity_review_correction FROM %I',account_role);
            EXECUTE format('REVOKE ALL ON FUNCTION public.correct_unprovisioned_conductor_identity(UUID,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) FROM %I',account_role);
        END IF;
    END LOOP;
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rf_app') THEN
        GRANT SELECT ON TABLE public.person_identity_review_correction TO rf_app;
        GRANT EXECUTE ON FUNCTION public.correct_unprovisioned_conductor_identity(UUID,UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ) TO rf_app;
    END IF;
END $$;
