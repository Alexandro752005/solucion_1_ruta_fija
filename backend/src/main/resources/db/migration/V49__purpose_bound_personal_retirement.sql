-- L08. Explicit purpose-bound retirement, never a timer, backfill or global bypass.
-- Actor authentication/reauthentication is required by RetentionProcedure. SQL enforces
-- exact scope, due time, stale detection, and an unforgeable row/plan/transaction authority.
-- Nothing here activates a pilot or edits the enrolled SUPER_ADMIN credential/global HMAC key.

ALTER TABLE conductor_recovery_request ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE conductor_recovery_request ADD CONSTRAINT ck_recovery_cancelled_at
 CHECK(cancelled_at IS NULL OR status='CANCELLED');
CREATE FUNCTION retention_record_recovery_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.status IN('COMPLETED','CANCELLED') AND NEW.status IS DISTINCT FROM OLD.status
 THEN RAISE EXCEPTION 'A finished recovery case cannot be reopened' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND OLD.status='COMPLETED' AND NEW.completed_at IS DISTINCT FROM OLD.completed_at
 THEN RAISE EXCEPTION 'Recovery completion anchor is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.status='CANCELLED' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
  NEW.cancelled_at:=statement_timestamp();
 ELSIF TG_OP='UPDATE' AND OLD.status='CANCELLED' AND NEW.cancelled_at IS DISTINCT FROM OLD.cancelled_at THEN
  RAISE EXCEPTION 'Recovery cancellation anchor is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER retention_recovery_cancellation BEFORE INSERT OR UPDATE ON conductor_recovery_request
 FOR EACH ROW EXECUTE FUNCTION retention_record_recovery_cancellation();

ALTER TABLE retention_plan DROP CONSTRAINT retention_plan_category_check;
ALTER TABLE retention_plan ADD CONSTRAINT retention_plan_category_check CHECK(category IN
 ('OPERATIONAL_AUDIT','REPORT_EMISSION','DOCUMENTARY_CASE','ADMIN_CONDUCTOR_PII',
  'PERSON_HMAC_LINKS','TERMS_ACCEPTANCE','RECOVERY_CASE_MINIMUM'));
ALTER TABLE retention_controlled_copy DROP CONSTRAINT retention_controlled_copy_source_category_check;
ALTER TABLE retention_controlled_copy ADD CONSTRAINT retention_controlled_copy_source_category_check CHECK(source_category IN
 ('OPERATIONAL_AUDIT','REPORT_EMISSION','DOCUMENTARY_CASE','ADMIN_CONDUCTOR_PII',
  'PERSON_HMAC_LINKS','TERMS_ACCEPTANCE','RECOVERY_CASE_MINIMUM'));

-- ADMIN identifies the exact resources while authorized; the technical actor can
-- prepare their current fingerprint after that ADMIN has been disabled, without
-- reactivation or ordinary dossier access. A delegation cannot be expanded.
CREATE TABLE retention_personal_delegation (
 id UUID PRIMARY KEY,category VARCHAR(40) NOT NULL,organization_id UUID NOT NULL REFERENCES organization(id),
 requested_by UUID NOT NULL REFERENCES app_user(id),case_reference UUID NOT NULL,
 justification_code VARCHAR(60) NOT NULL CHECK(justification_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
 resource_ids UUID[] NOT NULL CHECK(cardinality(resource_ids) BETWEEN 1 AND 1000),requested_at TIMESTAMPTZ NOT NULL,
 CHECK(category IN('DOCUMENTARY_CASE','ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS','TERMS_ACCEPTANCE','RECOVERY_CASE_MINIMUM'))
);
CREATE TABLE retention_personal_plan_delegation (
 plan_id UUID PRIMARY KEY REFERENCES retention_plan(id),delegation_id UUID NOT NULL REFERENCES retention_personal_delegation(id)
);
CREATE TABLE retention_personal_hold_request (
 id UUID PRIMARY KEY,category VARCHAR(40) NOT NULL,resource_id UUID NOT NULL,
 organization_id UUID NOT NULL REFERENCES organization(id),requested_by UUID NOT NULL REFERENCES app_user(id),
 case_reference UUID NOT NULL,purpose_code VARCHAR(60) NOT NULL CHECK(purpose_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
 ordinary_due_at TIMESTAMPTZ NOT NULL,requested_until TIMESTAMPTZ NOT NULL CHECK(requested_until>ordinary_due_at),
 requested_at TIMESTAMPTZ NOT NULL,UNIQUE(category,resource_id),
 CHECK(category IN('DOCUMENTARY_CASE','ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS','TERMS_ACCEPTANCE','RECOVERY_CASE_MINIMUM'))
);
CREATE TABLE retention_personal_hold_approval (
 request_id UUID PRIMARY KEY REFERENCES retention_personal_hold_request(id),approved_by UUID NOT NULL REFERENCES app_user(id),
 approved_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE retention_personal_retirement (
 category VARCHAR(40) NOT NULL,resource_id UUID NOT NULL,organization_id UUID REFERENCES organization(id),
 plan_id UUID NOT NULL REFERENCES retention_plan(id),retired_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(category,resource_id)
);
CREATE TABLE retention_personal_row_authority (
 plan_id UUID NOT NULL REFERENCES retention_plan(id),table_name TEXT NOT NULL,row_key TEXT NOT NULL,
 mutation VARCHAR(6) NOT NULL CHECK(mutation IN('UPDATE','DELETE')),
 transaction_id BIGINT NOT NULL,backend_pid INTEGER NOT NULL,
 PRIMARY KEY(plan_id,table_name,row_key,mutation)
);
DO $$ DECLARE t TEXT; BEGIN
 FOREACH t IN ARRAY ARRAY['retention_personal_delegation','retention_personal_plan_delegation',
 'retention_personal_hold_request','retention_personal_hold_approval','retention_personal_retirement'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION service_type_immutable()',t||'_immutable',t);
 END LOOP;
END $$;

CREATE FUNCTION retention_personal_is_category(p_category VARCHAR) RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE STRICT AS $$
 SELECT p_category IN('DOCUMENTARY_CASE','ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS','TERMS_ACCEPTANCE','RECOVERY_CASE_MINIMUM')
$$;

-- Return only the owner reference, never content to an ordinary platform view.
CREATE FUNCTION retention_personal_owner(p_category VARCHAR,p_resource UUID) RETURNS UUID
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE org UUID;
BEGIN
 IF p_category='DOCUMENTARY_CASE' THEN SELECT organization_id INTO org FROM conductor_onboarding_request WHERE id=p_resource;
 ELSIF p_category IN('ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS') THEN
  SELECT organization_id INTO org FROM app_user WHERE id=p_resource AND role IN('ADMIN','CONDUCTOR');
 ELSIF p_category='TERMS_ACCEPTANCE' THEN SELECT organization_id INTO org FROM admin_terms_acceptance WHERE id=p_resource;
 ELSIF p_category='RECOVERY_CASE_MINIMUM' THEN
  SELECT organization_id INTO org FROM conductor_recovery_request WHERE id=p_resource;
  IF NOT FOUND AND EXISTS(SELECT 1 FROM super_admin_recovery_challenge c JOIN app_user u ON u.id=c.user_id WHERE c.id=p_resource AND u.role='SUPER_ADMIN') THEN RETURN NULL; END IF;
 ELSE RAISE EXCEPTION 'RETENTION_CATEGORY_NOT_IMPLEMENTED' USING ERRCODE='23514'; END IF;
 IF org IS NULL THEN RAISE EXCEPTION 'RETENTION_RESOURCE_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 RETURN org;
END $$;

CREATE FUNCTION retention_personal_ordinary_due(p_category VARCHAR,p_resource UUID) RETURNS TIMESTAMPTZ
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE anchor_at TIMESTAMPTZ;
BEGIN
 IF NOT retention_personal_is_category(p_category) THEN RAISE EXCEPTION 'RETENTION_CATEGORY_NOT_IMPLEMENTED' USING ERRCODE='23514'; END IF;
 IF p_category='RECOVERY_CASE_MINIMUM' THEN
  SELECT CASE WHEN status='COMPLETED' THEN completed_at WHEN status='CANCELLED' THEN cancelled_at END INTO anchor_at FROM conductor_recovery_request WHERE id=p_resource;
  IF NOT FOUND THEN SELECT consumed_at INTO anchor_at FROM super_admin_recovery_challenge WHERE id=p_resource; END IF;
  IF anchor_at IS NULL THEN RAISE EXCEPTION 'RETENTION_ACTUAL_CASE_FINISH_OR_CANCELLATION_NOT_RECORDED' USING ERRCODE='23514'; END IF;
  RETURN anchor_at+INTERVAL '30 days';
 END IF;
 SELECT ended_at INTO anchor_at FROM pilot_lifecycle WHERE id='PILOT';
 IF anchor_at IS NULL THEN RAISE EXCEPTION 'RETENTION_ACTUAL_PILOT_END_NOT_RECORDED' USING ERRCODE='23514'; END IF;
 RETURN anchor_at+CASE WHEN p_category='TERMS_ACCEPTANCE' THEN INTERVAL '90 days' ELSE INTERVAL '30 days' END;
END $$;
CREATE FUNCTION retention_personal_effective_due(p_category VARCHAR,p_resource UUID) RETURNS TIMESTAMPTZ
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
 SELECT COALESCE((SELECT h.requested_until FROM retention_personal_hold_request h JOIN retention_personal_hold_approval a ON a.request_id=h.id
 WHERE h.category=p_category AND h.resource_id=p_resource),retention_personal_ordinary_due(p_category,p_resource))
$$;

-- Fixed inventory. Full records are hashed transiently, never copied into the plan.
-- Fields with another purpose (audit/report/GPS/recovery/terms) retain their own
-- procedure and deadline; this is not a claim to scrub arbitrary PII from every
-- operational free-text field. IDs, coded outcomes and timestamps remain history.
CREATE FUNCTION retention_personal_rows(p_category VARCHAR,p_resource UUID,p_org UUID)
RETURNS TABLE(table_name TEXT,row_key TEXT,body JSONB,mutation TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE r conductor_onboarding_request%ROWTYPE;u app_user%ROWTYPE;
BEGIN
 IF retention_personal_owner(p_category,p_resource) IS DISTINCT FROM p_org THEN RAISE EXCEPTION 'RETENTION_SELECTION_OUTSIDE_SCOPE_OR_ABSENT' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM retention_personal_retirement WHERE category=p_category AND resource_id=p_resource) THEN RAISE EXCEPTION 'RETENTION_RESOURCE_ALREADY_RETIRED' USING ERRCODE='23514'; END IF;
 IF p_category='DOCUMENTARY_CASE' THEN
  SELECT * INTO STRICT r FROM conductor_onboarding_request WHERE id=p_resource;
  IF EXISTS(SELECT 1 FROM conductor_onboarding_request q WHERE q.identity_id=r.identity_id AND q.organization_id<>p_org)
    OR EXISTS(SELECT 1 FROM conductor_onboarding_email_challenge c JOIN organization_invitation v ON v.id=c.invitation_id WHERE c.identity_id=r.identity_id AND v.organization_id<>p_org)
    OR EXISTS(SELECT 1 FROM conductor_person_binding b WHERE b.identity_id=r.identity_id AND b.organization_id<>p_org)
    OR EXISTS(SELECT 1 FROM organization_invitation v JOIN conductor_onboarding_identity i ON i.email=v.recipient_email WHERE i.id=r.identity_id AND v.organization_id<>p_org)
  THEN RAISE EXCEPTION 'RETENTION_SHARED_IDENTITY_REQUIRES_COMPLETE_EXACT_SCOPE' USING ERRCODE='23514'; END IF;
  RETURN QUERY SELECT 'conductor_onboarding_request',q.id::TEXT,to_jsonb(q),'UPDATE' FROM conductor_onboarding_request q WHERE q.id=r.id;
  RETURN QUERY SELECT 'conductor_onboarding_identity',i.id::TEXT,to_jsonb(i),'UPDATE' FROM conductor_onboarding_identity i WHERE i.id=r.identity_id;
  RETURN QUERY SELECT 'conductor_document_revision',d.id::TEXT,to_jsonb(d),'UPDATE' FROM conductor_document_revision d WHERE d.request_id=r.id;
  RETURN QUERY SELECT 'conductor_onboarding_review_event',e.id::TEXT,to_jsonb(e),'UPDATE' FROM conductor_onboarding_review_event e WHERE e.request_id=r.id;
  RETURN QUERY SELECT 'conductor_onboarding_access_token',t.id::TEXT,to_jsonb(t),'DELETE' FROM conductor_onboarding_access_token t WHERE t.identity_id=r.identity_id;
  RETURN QUERY SELECT 'conductor_onboarding_email_challenge',c.id::TEXT,to_jsonb(c),'DELETE' FROM conductor_onboarding_email_challenge c WHERE c.identity_id=r.identity_id;
  RETURN QUERY SELECT 'organization_invitation',v.id::TEXT,to_jsonb(v),'UPDATE' FROM organization_invitation v WHERE v.id=r.invitation_id
    OR v.id IN(SELECT c.invitation_id FROM conductor_onboarding_email_challenge c WHERE c.identity_id=r.identity_id)
    OR (v.organization_id=p_org AND v.recipient_email=(SELECT i.email FROM conductor_onboarding_identity i WHERE i.id=r.identity_id));
  RETURN QUERY SELECT 'qualification_review',q.id::TEXT,to_jsonb(q),'UPDATE' FROM qualification_review q WHERE q.driver_id=r.linked_driver_id AND q.organization_id=p_org;
  RETURN QUERY SELECT 'driver_qualification_control',q.driver_id::TEXT,to_jsonb(q),'UPDATE' FROM driver_qualification_control q WHERE q.driver_id=r.linked_driver_id AND q.organization_id=p_org;
 ELSIF p_category='ADMIN_CONDUCTOR_PII' THEN
  SELECT * INTO STRICT u FROM app_user WHERE id=p_resource AND role IN('ADMIN','CONDUCTOR');
  RETURN QUERY SELECT 'app_user',a.id::TEXT,to_jsonb(a),'UPDATE' FROM app_user a WHERE a.id=u.id;
  RETURN QUERY SELECT 'driver',d.id::TEXT,to_jsonb(d),'UPDATE' FROM driver d WHERE d.user_id=u.id AND d.organization_id=p_org;
  RETURN QUERY SELECT 'email_otp_challenge',c.id::TEXT,to_jsonb(c),'UPDATE' FROM email_otp_challenge c WHERE c.user_id=u.id AND c.purpose NOT IN('SUPER_ADMIN_RECOVERY','CONDUCTOR_RECOVERY');
  RETURN QUERY SELECT 'refresh_token',t.id::TEXT,to_jsonb(t),'UPDATE' FROM refresh_token t WHERE t.user_id=u.id;
  RETURN QUERY SELECT 'admin_activation',a.id::TEXT,to_jsonb(a),'UPDATE' FROM admin_activation a WHERE (a.admin_user_id=u.id OR a.replaced_admin_user_id=u.id) AND a.organization_id=p_org;
  RETURN QUERY SELECT 'admin_activation_event',e.id::TEXT,to_jsonb(e),'UPDATE' FROM admin_activation_event e JOIN admin_activation a ON a.id=e.activation_id WHERE (a.admin_user_id=u.id OR a.replaced_admin_user_id=u.id) AND a.organization_id=p_org;
  RETURN QUERY SELECT 'admin_security_revocation',e.id::TEXT,to_jsonb(e),'UPDATE' FROM admin_security_revocation e WHERE e.admin_user_id=u.id AND e.organization_id=p_org;
 ELSIF p_category='PERSON_HMAC_LINKS' THEN
  IF EXISTS(SELECT 1 FROM person_identity i JOIN conductor_person_binding b ON b.person_hash=i.person_hash
      WHERE i.user_id=p_resource AND (b.user_id IS DISTINCT FROM p_resource OR b.organization_id<>p_org))
   OR EXISTS(SELECT 1 FROM conductor_person_binding b JOIN person_identity i ON i.person_hash=b.person_hash
      WHERE b.user_id=p_resource AND (i.user_id<>p_resource OR b.organization_id<>p_org))
  THEN RAISE EXCEPTION 'RETENTION_SHARED_PERSON_BINDING_REQUIRES_COMPLETE_EXACT_SCOPE' USING ERRCODE='23514'; END IF;
  RETURN QUERY SELECT 'person_identity',i.person_hash::TEXT,to_jsonb(i),'DELETE' FROM person_identity i WHERE i.user_id=p_resource;
  RETURN QUERY SELECT 'conductor_common_person_bridge',b.person_hash::TEXT,to_jsonb(b),'DELETE' FROM conductor_common_person_bridge b JOIN person_identity i ON i.person_hash=b.common_person_hash WHERE i.user_id=p_resource;
  RETURN QUERY SELECT 'conductor_person_binding',b.person_hash::TEXT,to_jsonb(b),'DELETE' FROM conductor_person_binding b WHERE b.user_id=p_resource AND b.organization_id=p_org;
  -- Account state belongs to the approval fingerprint, but is not altered here.
  RETURN QUERY SELECT 'app_user',a.id::TEXT,to_jsonb(a),'NONE' FROM app_user a WHERE a.id=p_resource AND a.role IN('ADMIN','CONDUCTOR');
 ELSIF p_category='TERMS_ACCEPTANCE' THEN
  RETURN QUERY SELECT 'admin_terms_acceptance',a.id::TEXT,to_jsonb(a),'DELETE' FROM admin_terms_acceptance a WHERE a.id=p_resource AND a.organization_id=p_org;
 ELSIF p_category='RECOVERY_CASE_MINIMUM' THEN
  IF p_org IS NULL THEN
   RETURN QUERY SELECT 'super_admin_recovery_challenge',c.id::TEXT,to_jsonb(c),'DELETE' FROM super_admin_recovery_challenge c JOIN app_user a ON a.id=c.user_id WHERE c.id=p_resource AND a.role='SUPER_ADMIN';
  ELSE
   RETURN QUERY SELECT 'conductor_recovery_request',c.id::TEXT,to_jsonb(c),'DELETE' FROM conductor_recovery_request c WHERE c.id=p_resource AND c.organization_id=p_org;
  END IF;
  RETURN QUERY SELECT 'email_otp_challenge',c.id::TEXT,to_jsonb(c),'DELETE' FROM email_otp_challenge c WHERE c.purpose_reference_id=p_resource
    AND c.purpose=CASE WHEN p_org IS NULL THEN 'SUPER_ADMIN_RECOVERY' ELSE 'CONDUCTOR_RECOVERY' END;
 END IF;
END $$;

CREATE FUNCTION retention_personal_fingerprint(p_category VARCHAR,p_resource UUID,p_org UUID) RETURNS TEXT
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT encode(sha256(convert_to(string_agg(table_name||'|'||row_key||'|'||body::TEXT,E'\n' ORDER BY table_name,row_key),'UTF8')),'hex')
 FROM retention_personal_rows(p_category,p_resource,p_org)
$$;

CREATE FUNCTION retention_personal_lock_inventory(p_category VARCHAR,p_resource UUID,p_org UUID) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE r RECORD;key_column TEXT;
BEGIN
 -- The inventory function is a fixed table/selection whitelist. No identifier
 -- comes from user input, and every identifier is quoted independently.
 FOR r IN SELECT * FROM retention_personal_rows(p_category,p_resource,p_org) ORDER BY table_name,row_key LOOP
  key_column:=CASE WHEN r.body ? 'id' THEN 'id' WHEN r.body ? 'person_hash' THEN 'person_hash' ELSE 'driver_id' END;
  EXECUTE format('SELECT 1 FROM public.%I WHERE %I::text=$1 FOR UPDATE NOWAIT',r.table_name,key_column) USING r.row_key;
 END LOOP;
END $$;

CREATE FUNCTION retention_personal_require_retirable(p_category VARCHAR,p_resource UUID,p_org UUID,p_now TIMESTAMPTZ)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE r conductor_onboarding_request%ROWTYPE; target_user UUID;
BEGIN
 IF p_category='DOCUMENTARY_CASE' THEN
  SELECT * INTO STRICT r FROM conductor_onboarding_request WHERE id=p_resource FOR UPDATE NOWAIT;
  target_user:=r.linked_user_id;
  PERFORM 1 FROM conductor_onboarding_identity WHERE id=r.identity_id FOR UPDATE NOWAIT;
  IF EXISTS(SELECT 1 FROM conductor_onboarding_access_token WHERE identity_id=r.identity_id AND revoked_at IS NULL AND expires_at>p_now)
    OR EXISTS(SELECT 1 FROM conductor_onboarding_email_challenge WHERE identity_id=r.identity_id AND consumed_at IS NULL AND challenge_expires_at>p_now)
  THEN RAISE EXCEPTION 'RETENTION_ACTIVE_PRELIMINARY_ACCESS' USING ERRCODE='23514'; END IF;
 ELSIF p_category IN('ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS') THEN target_user:=p_resource;
 ELSIF p_category='TERMS_ACCEPTANCE' THEN
  SELECT admin_user_id INTO target_user FROM admin_terms_acceptance WHERE id=p_resource FOR UPDATE NOWAIT;
 ELSIF p_category='RECOVERY_CASE_MINIMUM' THEN
  IF p_org IS NULL THEN
   PERFORM 1 FROM super_admin_recovery_challenge WHERE id=p_resource AND consumed_at IS NOT NULL FOR UPDATE NOWAIT;
   IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_RECOVERY_CASE_NOT_FINISHED' USING ERRCODE='23514'; END IF;
  ELSE
   PERFORM 1 FROM conductor_recovery_request WHERE id=p_resource AND status IN('COMPLETED','CANCELLED') FOR UPDATE NOWAIT;
   IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_RECOVERY_CASE_NOT_FINISHED' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 IF target_user IS NOT NULL THEN
  PERFORM 1 FROM app_user WHERE id=target_user AND organization_id=p_org AND role IN('ADMIN','CONDUCTOR') AND NOT active FOR UPDATE NOWAIT;
  IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_ACTIVE_ACCOUNT_OR_OWNER_CUSTODY_EXCLUDED' USING ERRCODE='23514'; END IF;
  IF p_category='ADMIN_CONDUCTOR_PII' AND EXISTS(SELECT 1 FROM conductor_recovery_request WHERE conductor_user_id=target_user AND status NOT IN('COMPLETED','CANCELLED'))
  THEN RAISE EXCEPTION 'RETENTION_RECOVERY_MUST_BE_ACTUALLY_FINISHED_OR_CANCELLED' USING ERRCODE='23514'; END IF;
  PERFORM 1 FROM driver WHERE user_id=target_user FOR UPDATE NOWAIT;
  IF EXISTS(SELECT 1 FROM driver WHERE user_id=target_user AND active)
   OR EXISTS(SELECT 1 FROM driver d JOIN assignment a ON a.driver_id=d.id WHERE d.user_id=target_user AND a.status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO'))
   OR EXISTS(SELECT 1 FROM driver d JOIN service_execution_segment s ON s.driver_id=d.id WHERE d.user_id=target_user AND s.ended_at IS NULL)
  THEN RAISE EXCEPTION 'RETENTION_ACTIVE_DRIVER_OR_OPERATION' USING ERRCODE='23514'; END IF;
 END IF;
END $$;

CREATE FUNCTION retention_delegate_personal_selection(p_id UUID,p_category VARCHAR,p_org UUID,p_actor UUID,p_case UUID,p_reason VARCHAR,p_ids UUID[],p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE r UUID;
BEGIN
 PERFORM retention_require_actor(p_actor,'ADMIN',p_org);
 IF NOT retention_personal_is_category(p_category) OR p_ids IS NULL OR cardinality(p_ids) NOT BETWEEN 1 AND 1000 OR p_now IS NULL
 OR EXISTS(SELECT 1 FROM unnest(p_ids) v WHERE v IS NULL) OR cardinality(p_ids)<>(SELECT count(DISTINCT v) FROM unnest(p_ids) v)
 THEN RAISE EXCEPTION 'RETENTION_EXACT_NONEMPTY_SELECTION_REQUIRED' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM organization WHERE id=p_org FOR NO KEY UPDATE;
 FOREACH r IN ARRAY p_ids LOOP
  IF retention_personal_owner(p_category,r) IS DISTINCT FROM p_org THEN RAISE EXCEPTION 'RETENTION_SELECTION_OUTSIDE_SCOPE_OR_ABSENT' USING ERRCODE='23514'; END IF;
 END LOOP;
 INSERT INTO retention_personal_delegation VALUES(p_id,p_category,p_org,p_actor,p_case,p_reason,p_ids,p_now);
 RETURN p_id;
END $$;

CREATE FUNCTION retention_request_personal_hold(p_id UUID,p_category VARCHAR,p_resource UUID,p_org UUID,p_actor UUID,p_case UUID,p_purpose VARCHAR,p_until TIMESTAMPTZ,p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE due_at TIMESTAMPTZ;
BEGIN
 PERFORM retention_require_actor(p_actor,'ADMIN',p_org);
 PERFORM 1 FROM organization WHERE id=p_org FOR NO KEY UPDATE;
 IF NOT retention_personal_is_category(p_category) OR retention_personal_owner(p_category,p_resource) IS DISTINCT FROM p_org
 THEN RAISE EXCEPTION 'RETENTION_SELECTION_OUTSIDE_SCOPE_OR_ABSENT' USING ERRCODE='23514'; END IF;
 due_at:=retention_personal_ordinary_due(p_category,p_resource);
 IF p_until IS NULL OR p_now IS NULL OR p_until<=due_at OR p_until<=p_now THEN RAISE EXCEPTION 'RETENTION_EXACT_PURPOSE_AND_EXPIRY_REQUIRED' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM retention_personal_retirement WHERE category=p_category AND resource_id=p_resource) THEN RAISE EXCEPTION 'RETENTION_RESOURCE_ALREADY_RETIRED' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_personal_hold_request VALUES(p_id,p_category,p_resource,p_org,p_actor,p_case,p_purpose,due_at,p_until,p_now);
 RETURN p_id;
END $$;
CREATE FUNCTION retention_approve_personal_hold(p_id UUID,p_actor UUID,p_now TIMESTAMPTZ) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE h retention_personal_hold_request%ROWTYPE;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 SELECT * INTO STRICT h FROM retention_personal_hold_request WHERE id=p_id;
 PERFORM 1 FROM organization WHERE id=h.organization_id FOR NO KEY UPDATE;
 IF p_now IS NULL OR p_now<h.requested_at OR p_now>=h.requested_until
 OR retention_personal_ordinary_due(h.category,h.resource_id) IS DISTINCT FROM h.ordinary_due_at
 OR EXISTS(SELECT 1 FROM retention_personal_retirement WHERE category=h.category AND resource_id=h.resource_id)
 THEN RAISE EXCEPTION 'RETENTION_HOLD_STALE_OR_EXPIRED' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_personal_hold_approval VALUES(p_id,p_actor,p_now);RETURN p_id;
END $$;

-- Preserve the original audit/report implementation and its behavior; dispatch
-- new categories through the same exact-plan protocol.
ALTER FUNCTION retention_prepare_plan(UUID,VARCHAR,UUID,UUID,UUID,VARCHAR,UUID[],TIMESTAMPTZ) RENAME TO retention_prepare_audit_report_plan;
ALTER FUNCTION retention_execute_plan(UUID,VARCHAR,UUID,TIMESTAMPTZ) RENAME TO retention_execute_audit_report_plan;
CREATE FUNCTION retention_prepare_personal_plan_internal(p_id UUID,p_category VARCHAR,p_org UUID,p_requester UUID,p_case UUID,p_reason VARCHAR,p_ids UUID[],p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE r UUID;n INTEGER;sha TEXT;
BEGIN
 IF NOT retention_personal_is_category(p_category) OR p_ids IS NULL OR cardinality(p_ids) NOT BETWEEN 1 AND 1000 OR p_now IS NULL
 OR EXISTS(SELECT 1 FROM unnest(p_ids) v WHERE v IS NULL) OR cardinality(p_ids)<>(SELECT count(DISTINCT v) FROM unnest(p_ids) v)
 THEN RAISE EXCEPTION 'RETENTION_EXACT_NONEMPTY_SELECTION_REQUIRED' USING ERRCODE='23514'; END IF;
 FOREACH r IN ARRAY p_ids LOOP
  IF retention_personal_owner(p_category,r) IS DISTINCT FROM p_org THEN RAISE EXCEPTION 'RETENTION_SELECTION_OUTSIDE_SCOPE_OR_ABSENT' USING ERRCODE='23514'; END IF;
  IF p_category='DOCUMENTARY_CASE' AND EXISTS(SELECT 1 FROM conductor_onboarding_request q
    JOIN conductor_onboarding_request selected ON selected.identity_id=q.identity_id AND selected.id=r
    WHERE q.organization_id<>p_org OR NOT(q.id=ANY(p_ids)))
  THEN RAISE EXCEPTION 'RETENTION_SHARED_IDENTITY_REQUIRES_COMPLETE_EXACT_SCOPE' USING ERRCODE='23514'; END IF;
  INSERT INTO retention_plan_item VALUES(p_id,r,retention_personal_effective_due(p_category,r),retention_personal_fingerprint(p_category,r,p_org));
 END LOOP;
 SELECT count(*),encode(sha256(convert_to(string_agg(resource_id::TEXT||'|'||extract(epoch from expected_due_at)::TEXT||'|'||resource_sha256,'\n' ORDER BY resource_id),'UTF8')),'hex')
 INTO n,sha FROM retention_plan_item WHERE plan_id=p_id;
 INSERT INTO retention_plan VALUES(p_id,p_category,p_org,p_requester,p_case,p_reason,p_now,sha,n,'RET-L08-PERSONAL-v1');RETURN p_id;
END $$;
CREATE FUNCTION retention_prepare_plan(p_id UUID,p_category VARCHAR,p_org UUID,p_actor UUID,p_case UUID,p_reason VARCHAR,p_ids UUID[],p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
BEGIN
 IF NOT retention_personal_is_category(p_category) THEN RETURN retention_prepare_audit_report_plan(p_id,p_category,p_org,p_actor,p_case,p_reason,p_ids,p_now); END IF;
 IF p_org IS NULL THEN
  IF p_category<>'RECOVERY_CASE_MINIMUM' THEN RAISE EXCEPTION 'RETENTION_PLATFORM_SCOPE_INVALID' USING ERRCODE='23514'; END IF;
  PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 ELSE PERFORM retention_require_actor(p_actor,'ADMIN',p_org);PERFORM 1 FROM organization WHERE id=p_org FOR NO KEY UPDATE;
 END IF;
 RETURN retention_prepare_personal_plan_internal(p_id,p_category,p_org,p_actor,p_case,p_reason,p_ids,p_now);
END $$;
CREATE FUNCTION retention_prepare_delegated_personal_plan(p_id UUID,p_delegation UUID,p_actor UUID,p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE d retention_personal_delegation%ROWTYPE;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 SELECT * INTO STRICT d FROM retention_personal_delegation WHERE id=p_delegation;
 PERFORM 1 FROM organization WHERE id=d.organization_id FOR NO KEY UPDATE;
 IF p_now IS NULL OR p_now<d.requested_at THEN RAISE EXCEPTION 'RETENTION_SERVER_TIME_INVALID' USING ERRCODE='23514'; END IF;
 PERFORM retention_prepare_personal_plan_internal(p_id,d.category,d.organization_id,d.requested_by,d.case_reference,d.justification_code,d.resource_ids,p_now);
 INSERT INTO retention_personal_plan_delegation VALUES(p_id,p_delegation);RETURN p_id;
END $$;

CREATE FUNCTION retention_personal_row_is_authorized(p_table TEXT,p_key TEXT,p_mutation TEXT) RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM retention_personal_row_authority x JOIN retention_plan p ON p.id=x.plan_id
 WHERE x.table_name=p_table AND x.row_key=p_key AND x.mutation=p_mutation
 AND x.transaction_id=txid_current() AND x.backend_pid=pg_backend_pid() AND retention_personal_is_category(p.category))
$$;
CREATE FUNCTION retention_personal_immutable_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE k TEXT;
BEGIN
 k:=COALESCE(to_jsonb(OLD)->>'id',to_jsonb(OLD)->>'person_hash',to_jsonb(OLD)->>'driver_id');
 IF retention_personal_row_is_authorized(TG_TABLE_NAME,k,TG_OP) THEN
  IF TG_OP='DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
 END IF;
 RAISE EXCEPTION 'Immutable history requires an exact expired personal-retirement plan' USING ERRCODE='23514';
END $$;
-- Replace only these specific triggers. The shared identity/history function is
-- deliberately unchanged (other service/vehicle histories must remain immutable).
DROP TRIGGER protect_conductor_document_revision ON conductor_document_revision;
DROP TRIGGER protect_onboarding_review_event ON conductor_onboarding_review_event;
DROP TRIGGER protect_qualification_review ON qualification_review;
DROP TRIGGER protect_person_identity ON person_identity;
DROP TRIGGER protect_conductor_common_bridge ON conductor_common_person_bridge;
DROP TRIGGER trg_admin_terms_acceptance_append_only ON admin_terms_acceptance;
DROP TRIGGER protect_admin_activation_history ON admin_activation_event;
DROP TRIGGER protect_admin_security_revocation_history ON admin_security_revocation;
DO $$ DECLARE t TEXT;BEGIN
 FOREACH t IN ARRAY ARRAY['conductor_document_revision','conductor_onboarding_review_event','qualification_review',
 'person_identity','conductor_common_person_bridge','admin_terms_acceptance','admin_activation_event','admin_security_revocation'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION retention_personal_immutable_guard()',t||'_retirement_guard',t);
 END LOOP;
END $$;
CREATE TRIGGER conductor_person_binding_delete_guard BEFORE DELETE ON conductor_person_binding
 FOR EACH ROW EXECUTE FUNCTION retention_personal_immutable_guard();

CREATE FUNCTION retention_execute_plan(p_id UUID,p_expected_sha VARCHAR,p_actor UUID,p_now TIMESTAMPTZ) RETURNS INTEGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE p retention_plan%ROWTYPE;i retention_plan_item%ROWTYPE;r RECORD;prior INTEGER;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 SELECT * INTO p FROM retention_plan WHERE id=p_id FOR UPDATE;
 IF NOT FOUND OR p_expected_sha IS NULL OR p.selection_sha256<>p_expected_sha THEN RAISE EXCEPTION 'RETENTION_APPROVED_SELECTION_MISMATCH' USING ERRCODE='23514'; END IF;
 IF NOT retention_personal_is_category(p.category) THEN RETURN retention_execute_audit_report_plan(p_id,p_expected_sha,p_actor,p_now); END IF;
 SELECT retired_count INTO prior FROM retention_execution WHERE plan_id=p_id;IF FOUND THEN RETURN prior; END IF;
 IF p_now IS NULL OR p_now<p.requested_at THEN RAISE EXCEPTION 'RETENTION_SERVER_TIME_INVALID' USING ERRCODE='23514'; END IF;
 IF p.organization_id IS NOT NULL THEN PERFORM 1 FROM organization WHERE id=p.organization_id FOR NO KEY UPDATE NOWAIT; END IF;
 -- Validate every resource first, then authorize precisely its inventory rows.
 FOR i IN SELECT * FROM retention_plan_item WHERE plan_id=p_id ORDER BY resource_id LOOP
  PERFORM retention_personal_require_retirable(p.category,i.resource_id,p.organization_id,p_now);
  PERFORM retention_personal_lock_inventory(p.category,i.resource_id,p.organization_id);
  IF p.category='DOCUMENTARY_CASE' AND EXISTS(SELECT 1 FROM conductor_onboarding_request q
    JOIN conductor_onboarding_request selected ON selected.identity_id=q.identity_id AND selected.id=i.resource_id
    WHERE q.organization_id<>p.organization_id OR NOT EXISTS(SELECT 1 FROM retention_plan_item exact_item WHERE exact_item.plan_id=p_id AND exact_item.resource_id=q.id))
  THEN RAISE EXCEPTION 'RETENTION_SHARED_IDENTITY_REQUIRES_COMPLETE_EXACT_SCOPE' USING ERRCODE='23514'; END IF;
  IF p_now<retention_personal_effective_due(p.category,i.resource_id)
   OR i.expected_due_at IS DISTINCT FROM retention_personal_effective_due(p.category,i.resource_id)
   OR i.resource_sha256 IS DISTINCT FROM retention_personal_fingerprint(p.category,i.resource_id,p.organization_id)
  THEN RAISE EXCEPTION 'RETENTION_NOT_EXPIRED_OR_SELECTION_STALE' USING ERRCODE='23514'; END IF;
  FOR r IN SELECT * FROM retention_personal_rows(p.category,i.resource_id,p.organization_id) WHERE mutation<>'NONE' LOOP
   INSERT INTO retention_personal_row_authority VALUES(p_id,r.table_name,r.row_key,r.mutation,txid_current(),pg_backend_pid()) ON CONFLICT DO NOTHING;
  END LOOP;
 END LOOP;
 IF p.category='DOCUMENTARY_CASE' THEN
  UPDATE conductor_document_revision SET fields='{}'::jsonb,applicability_condition=NULL,source_reference=NULL,review_reference=NULL,observation=NULL
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_document_revision');
  UPDATE conductor_onboarding_review_event SET reason=NULL,operation_key='RETIRED-'||id::TEXT,payload_hash=encode(sha256(convert_to('RETIRED-'||id::TEXT,'UTF8')),'hex')
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_onboarding_review_event');
  UPDATE qualification_review SET fields='{}'::jsonb,applicability_condition=NULL,source_reference=NULL,review_reference=NULL
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='qualification_review');
  UPDATE driver_qualification_control SET suspension_reference=NULL
   WHERE driver_id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='driver_qualification_control');
  DELETE FROM conductor_onboarding_access_token WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_onboarding_access_token');
  DELETE FROM conductor_onboarding_email_challenge WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_onboarding_email_challenge');
  UPDATE conductor_onboarding_request SET idempotency_key='RETIRED-'||id::TEXT
   WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  UPDATE conductor_onboarding_identity SET email='retired.'||id::TEXT||'@invalid.example',password_hash='!RETIRED-'||id::TEXT,full_name='RETIRED',phone=NULL,email_verified_at=NULL
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_onboarding_identity');
  UPDATE organization_invitation SET recipient_email='retired.'||id::TEXT||'@invalid.example',code_hash=encode(sha256(convert_to('RETIRED-'||id::TEXT,'UTF8')),'hex'),
    revoked_at=CASE WHEN used_at IS NULL THEN COALESCE(revoked_at,p_now) ELSE NULL END
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='organization_invitation');
 ELSIF p.category='PERSON_HMAC_LINKS' THEN
  DELETE FROM conductor_common_person_bridge WHERE person_hash::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_common_person_bridge');
  DELETE FROM person_identity WHERE person_hash::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='person_identity');
  DELETE FROM conductor_person_binding WHERE person_hash::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='conductor_person_binding');
 ELSIF p.category='ADMIN_CONDUCTOR_PII' THEN
  UPDATE app_user SET email='retired.'||id::TEXT||'@invalid.example',full_name='RETIRED',phone=NULL,password_hash='!RETIRED-'||id::TEXT,email_verified_at=NULL,session_version=session_version+1
   WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  UPDATE driver SET full_name='RETIRED',phone=NULL,document_type='RETIRED',document_number='RETIRED-'||substr(replace(id::TEXT,'-',''),1,20),license_number=NULL,location_consent=false
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='driver');
  UPDATE email_otp_challenge SET recipient_email='retired.'||id::TEXT||'@invalid.example',verification_token_hash=NULL,otp_hash=NULL,otp_issued_at=NULL,otp_expires_at=NULL,challenge_expires_at=NULL,consumed_at=COALESCE(consumed_at,p_now)
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='email_otp_challenge');
  UPDATE refresh_token SET token_hash=encode(sha256(convert_to('RETIRED-'||id::TEXT,'UTF8')),'hex'),revoked_at=COALESCE(revoked_at,p_now)
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='refresh_token');
  -- Activation constraints retain scalar lifecycle facts; credentials are replaced
  -- by unusable deterministic tombstones, not reset for reuse.
  UPDATE admin_activation SET verification_reference=CASE WHEN replaced_admin_user_id IS NULL THEN NULL ELSE 'RETIRED' END,creation_reason='RETIRED',credential_hash=encode(sha256(convert_to('RETIRED-CREDENTIAL-'||id::TEXT,'UTF8')),'hex'),activation_token_hash=CASE WHEN activation_token_hash IS NULL THEN NULL ELSE encode(sha256(convert_to('RETIRED-ACTIVATION-'||id::TEXT,'UTF8')),'hex') END,
    activation_token_revoked_at=COALESCE(activation_token_revoked_at,p_now)
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='admin_activation')
    AND admin_user_id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  -- The outgoing person's references can appear in a successor's activation.
  -- Remove those free-text fields without rotating that successor's credentials.
  UPDATE admin_activation SET verification_reference='RETIRED',creation_reason='RETIRED'
   WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='admin_activation')
    AND admin_user_id NOT IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  UPDATE admin_activation_event SET reason=NULL WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='admin_activation_event');
  UPDATE admin_security_revocation SET reason='RETIRED' WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='admin_security_revocation');
 ELSIF p.category='TERMS_ACCEPTANCE' THEN
  DELETE FROM admin_terms_acceptance WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
 ELSIF p.category='RECOVERY_CASE_MINIMUM' THEN
  DELETE FROM email_otp_challenge WHERE id::TEXT IN(SELECT row_key FROM retention_personal_row_authority WHERE plan_id=p_id AND table_name='email_otp_challenge');
  DELETE FROM conductor_recovery_request WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  DELETE FROM super_admin_recovery_challenge WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
 END IF;
 INSERT INTO retention_personal_retirement SELECT p.category,resource_id,p.organization_id,p_id,p_now FROM retention_plan_item WHERE plan_id=p_id;
 INSERT INTO retention_execution VALUES(p_id,p_actor,p_now,p_expected_sha,p.item_count);
 DELETE FROM retention_personal_row_authority WHERE plan_id=p_id;
 RETURN p.item_count;
END $$;

-- Prevent later reactivation/rewrites of the selected retired accounts and
-- preliminary identities; normal functioning is unchanged for all other rows.
CREATE FUNCTION retention_reject_retired_owner_write() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid UUID;retired BOOLEAN;
BEGIN
 IF TG_TABLE_NAME='app_user' THEN
  rid:=NEW.id;
  SELECT EXISTS(SELECT 1 FROM retention_personal_retirement WHERE category IN('ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS') AND resource_id=rid)
    OR EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id
      WHERE x.category='DOCUMENTARY_CASE' AND q.linked_user_id=rid) INTO retired;
 ELSE
  rid:=CASE WHEN TG_TABLE_NAME='conductor_onboarding_identity' THEN NEW.id ELSE NEW.identity_id END;
  SELECT EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id
   WHERE x.category='DOCUMENTARY_CASE' AND q.identity_id=rid) INTO retired;
 END IF;
 IF retired AND NOT retention_personal_row_is_authorized(TG_TABLE_NAME,NEW.id::TEXT,TG_OP)
 THEN RAISE EXCEPTION 'Retired personal record cannot be reactivated or repopulated' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER retention_retired_user BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION retention_reject_retired_owner_write();
CREATE TRIGGER retention_retired_preliminary BEFORE UPDATE ON conductor_onboarding_identity FOR EACH ROW EXECUTE FUNCTION retention_reject_retired_owner_write();
CREATE TRIGGER retention_retired_preliminary_request BEFORE INSERT ON conductor_onboarding_request FOR EACH ROW EXECUTE FUNCTION retention_reject_retired_owner_write();
CREATE TRIGGER retention_retired_preliminary_token BEFORE INSERT ON conductor_onboarding_access_token FOR EACH ROW EXECUTE FUNCTION retention_reject_retired_owner_write();
CREATE TRIGGER retention_retired_preliminary_challenge BEFORE INSERT ON conductor_onboarding_email_challenge FOR EACH ROW EXECUTE FUNCTION retention_reject_retired_owner_write();

-- Serialization of scoped writes is required in addition to stale hashes. An
-- insert that waits for retirement must observe the tombstone, not repopulate
-- the just-retired dossier. Parent locks are acquired before writing children.
CREATE FUNCTION retention_personal_child_write_guard() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE row_data JSONB;request_key UUID;identity_key UUID;user_key UUID;related_user UUID;driver_key UUID;locked_user UUID;
BEGIN
 row_data:=CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
 IF TG_TABLE_NAME IN('conductor_document_revision','conductor_onboarding_review_event') THEN
  request_key:=(row_data->>'request_id')::UUID;
  SELECT identity_id,linked_user_id INTO identity_key,user_key FROM conductor_onboarding_request WHERE id=request_key FOR UPDATE;
 ELSIF TG_TABLE_NAME IN('conductor_onboarding_request','conductor_onboarding_access_token','conductor_onboarding_email_challenge') THEN
  identity_key:=(row_data->>'identity_id')::UUID;
  user_key:=(row_data->>'linked_user_id')::UUID;
 ELSIF TG_TABLE_NAME IN('person_identity','conductor_person_binding','refresh_token','email_otp_challenge') THEN
  user_key:=(row_data->>'user_id')::UUID;
  identity_key:=(row_data->>'identity_id')::UUID;
 ELSIF TG_TABLE_NAME='admin_activation' THEN user_key:=(row_data->>'admin_user_id')::UUID;related_user:=(row_data->>'replaced_admin_user_id')::UUID;
 ELSIF TG_TABLE_NAME='admin_activation_event' THEN SELECT admin_user_id,replaced_admin_user_id INTO user_key,related_user FROM admin_activation WHERE id=(row_data->>'activation_id')::UUID FOR UPDATE;
 ELSIF TG_TABLE_NAME='admin_security_revocation' THEN user_key:=(row_data->>'admin_user_id')::UUID;
 ELSIF TG_TABLE_NAME IN('qualification_review','driver_qualification_control') THEN
  driver_key:=(row_data->>'driver_id')::UUID;SELECT user_id INTO user_key FROM driver WHERE id=driver_key;
 ELSIF TG_TABLE_NAME='conductor_common_person_bridge' THEN
  SELECT user_id,identity_id INTO user_key,identity_key FROM conductor_person_binding WHERE person_hash=row_data->>'person_hash' FOR UPDATE;
 END IF;
 IF TG_TABLE_NAME='organization_invitation' THEN
  PERFORM 1 FROM organization WHERE id=(row_data->>'organization_id')::UUID FOR NO KEY UPDATE;
  SELECT id INTO identity_key FROM conductor_onboarding_identity WHERE email=row_data->>'recipient_email';
 END IF;
 IF identity_key IS NOT NULL THEN
  PERFORM 1 FROM conductor_onboarding_identity WHERE id=identity_key FOR UPDATE;
  IF EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id
    WHERE x.category='DOCUMENTARY_CASE' AND q.identity_id=identity_key)
   AND NOT retention_personal_row_is_authorized(TG_TABLE_NAME,COALESCE(row_data->>'id',row_data->>'person_hash',row_data->>'driver_id'),TG_OP)
  THEN RAISE EXCEPTION 'Retired documentary identity cannot be repopulated' USING ERRCODE='23514'; END IF;
 END IF;
 FOR locked_user IN SELECT DISTINCT v FROM unnest(ARRAY[user_key,related_user]) v WHERE v IS NOT NULL ORDER BY v LOOP
  PERFORM 1 FROM app_user WHERE id=locked_user FOR UPDATE;
  IF (EXISTS(SELECT 1 FROM retention_personal_retirement x WHERE x.resource_id=locked_user AND
    (x.category='ADMIN_CONDUCTOR_PII' OR (x.category='PERSON_HMAC_LINKS' AND TG_TABLE_NAME IN('person_identity','conductor_person_binding','conductor_common_person_bridge'))))
    OR EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id WHERE x.category='DOCUMENTARY_CASE' AND q.linked_user_id=locked_user))
   AND NOT retention_personal_row_is_authorized(TG_TABLE_NAME,COALESCE(row_data->>'id',row_data->>'person_hash',row_data->>'driver_id'),TG_OP)
  THEN RAISE EXCEPTION 'Retired personal account cannot be repopulated' USING ERRCODE='23514'; END IF;
 END LOOP;
 IF driver_key IS NOT NULL THEN PERFORM 1 FROM driver WHERE id=driver_key FOR UPDATE;END IF;
 IF TG_OP='DELETE' THEN RETURN OLD;ELSE RETURN NEW;END IF;
END $$;
DO $$ DECLARE t TEXT;BEGIN
 FOREACH t IN ARRAY ARRAY['conductor_document_revision','conductor_onboarding_review_event','conductor_onboarding_request',
 'conductor_onboarding_access_token','conductor_onboarding_email_challenge','person_identity','conductor_person_binding','conductor_common_person_bridge',
 'refresh_token','email_otp_challenge','admin_activation','admin_activation_event','admin_security_revocation','qualification_review','driver_qualification_control','organization_invitation'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION retention_personal_child_write_guard()',t||'_retention_serialization',t);
 END LOOP;
END $$;

ALTER FUNCTION retention_register_copy(UUID,VARCHAR,UUID,UUID,UUID,TIMESTAMPTZ,BOOLEAN) RENAME TO retention_register_audit_report_copy;
CREATE FUNCTION retention_register_copy(p_id UUID,p_category VARCHAR,p_source UUID,p_medium UUID,p_actor UUID,p_now TIMESTAMPTZ,p_synthetic BOOLEAN)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE due_at TIMESTAMPTZ;
BEGIN
 IF NOT retention_personal_is_category(p_category) THEN RETURN retention_register_audit_report_copy(p_id,p_category,p_source,p_medium,p_actor,p_now,p_synthetic);END IF;
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 PERFORM retention_personal_owner(p_category,p_source);
 due_at:=retention_personal_ordinary_due(p_category,p_source);
 INSERT INTO retention_controlled_copy VALUES(p_id,p_category,p_source,p_medium,due_at,p_actor,p_now,p_synthetic);RETURN p_id;
END $$;

-- No edits to earlier catalog rows: a successor inventory states the actual tool
-- coverage instead of weakening the immutable V46 catalog.
CREATE TABLE retention_personal_capability (
 category VARCHAR(40) PRIMARY KEY,policy_version VARCHAR(50) NOT NULL,execution_capability VARCHAR(40) NOT NULL,
 limitation_code VARCHAR(100) NOT NULL
);
INSERT INTO retention_personal_capability SELECT category,'RET-L08-PERSONAL-v1','EXACT_PLAN',
 CASE WHEN category='DOCUMENTARY_CASE' THEN 'SHARED_IDENTITIES_REQUIRE_COMPLETE_SCOPE_NO_ARBITRARY_OPERATIONAL_TEXT_PURGE'
 WHEN category='ADMIN_CONDUCTOR_PII' THEN 'ACCOUNT_AND_LIFECYCLE_FIELDS_ONLY_OTHER_PURPOSES_SEPARATE'
 WHEN category='RECOVERY_CASE_MINIMUM' THEN 'ONLY_ACTUAL_FINISHED_OR_CANCELLED_CASES_OWNER_CREDENTIAL_EXCLUDED'
 ELSE 'EXACT_RESOURCES_ONLY_NO_GLOBAL_HMAC_KEY_OR_PUBLISHED_DOCUMENT_CHANGE' END
 FROM retention_policy_catalog WHERE retention_personal_is_category(category);
CREATE TRIGGER retention_personal_capability_immutable BEFORE UPDATE OR DELETE ON retention_personal_capability
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();

-- Hide all helpers capable of reading personal content or minting row authority.
-- Only the explicitly authenticated service/CLI procedures are callable by app roles.
REVOKE ALL ON retention_personal_row_authority FROM PUBLIC;
DO $$ DECLARE f RECORD;r TEXT;t TEXT;BEGIN
 FOR f IN SELECT oid::regprocedure signature FROM pg_proc WHERE pronamespace='public'::regnamespace
 AND (proname LIKE 'retention_personal_%' OR proname IN('retention_prepare_personal_plan_internal','retention_prepare_audit_report_plan','retention_execute_audit_report_plan',
 'retention_register_audit_report_copy','retention_register_copy','retention_delegate_personal_selection','retention_request_personal_hold','retention_approve_personal_hold','retention_prepare_delegated_personal_plan','retention_prepare_plan','retention_execute_plan')) LOOP
  EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',f.signature);
  FOREACH r IN ARRAY ARRAY['rf_app','rf_test'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN EXECUTE format('REVOKE ALL ON FUNCTION %s FROM %I',f.signature,r);END IF;
  END LOOP;
 END LOOP;
 FOREACH r IN ARRAY ARRAY['rf_app','rf_test'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
   FOREACH t IN ARRAY ARRAY['retention_personal_delegation','retention_personal_plan_delegation','retention_personal_hold_request',
    'retention_personal_hold_approval','retention_personal_retirement','retention_personal_row_authority','retention_personal_capability'] LOOP
    EXECUTE format('REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON public.%I FROM %I',t,r);
    IF t='retention_personal_row_authority' THEN EXECUTE format('REVOKE ALL ON public.%I FROM %I',t,r);
    ELSE EXECUTE format('GRANT SELECT ON public.%I TO %I',t,r);END IF;
   END LOOP;
   FOR f IN SELECT oid::regprocedure signature FROM pg_proc WHERE pronamespace='public'::regnamespace
    AND proname IN('retention_delegate_personal_selection','retention_request_personal_hold','retention_approve_personal_hold',
    'retention_register_copy','retention_prepare_delegated_personal_plan','retention_prepare_plan','retention_execute_plan') LOOP
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO %I',f.signature,r);
   END LOOP;
   -- Trigger predicates disclose a boolean only; content-bearing helpers stay private.
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_personal_row_is_authorized(TEXT,TEXT,TEXT) TO %I',r);
  END IF;
 END LOOP;
END $$;
