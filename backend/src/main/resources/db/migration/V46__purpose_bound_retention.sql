-- L07: exact, purpose-bound retention. No backfill, pilot activation or purge.
CREATE FUNCTION retention_audit_due(p_event_at TIMESTAMPTZ) RETURNS TIMESTAMPTZ
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
 SELECT ((p_event_at AT TIME ZONE 'America/Lima') + INTERVAL '6 months') AT TIME ZONE 'America/Lima'
$$;
CREATE FUNCTION retention_audit_visible(p_id UUID,p_event_at TIMESTAMPTZ,p_as_of TIMESTAMPTZ)
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
 SELECT p_as_of < retention_audit_due(p_event_at)
$$;

CREATE TABLE retention_policy_catalog (
 category VARCHAR(40) PRIMARY KEY, policy_version VARCHAR(50) NOT NULL,
 anchor VARCHAR(40) NOT NULL, duration_description VARCHAR(100) NOT NULL,
 execution_capability VARCHAR(40) NOT NULL,
 CHECK(execution_capability IN('EXACT_PLAN','EXISTING_GPS_PURGE','INVENTORIED_NOT_IMPLEMENTED','EXCLUDED'))
);
INSERT INTO retention_policy_catalog VALUES
 ('OPERATIONAL_AUDIT','AUD-L07-02-v1','EVENT_LOCAL_LIMA','6 calendar months; exact approved hold <=30 days','EXACT_PLAN'),
 ('REPORT_EMISSION','REP-RET-L07-03-v1','ACTUAL_PILOT_END','90 days, not reset by correction or download','EXACT_PLAN'),
 ('GPS_POINT','GPS-L05-RET-v1','CAPTURED_AT','7 days maximum, including incident evidence','EXISTING_GPS_PURGE'),
 ('DOCUMENTARY_CASE','RET-02-T05-v1','ACTUAL_PILOT_END','30 days','INVENTORIED_NOT_IMPLEMENTED'),
 ('ADMIN_CONDUCTOR_PII','RET-02-T04-v1','ACTUAL_PILOT_END','30 days; preserve IDs/history/creation ledger','INVENTORIED_NOT_IMPLEMENTED'),
 ('PERSON_HMAC_LINKS','T04-v1','ACTUAL_PILOT_END','30 days; excludes SUPER_ADMIN custody','INVENTORIED_NOT_IMPLEMENTED'),
 ('TERMS_ACCEPTANCE','RET-02-v1','ACTUAL_PILOT_END','90 days','INVENTORIED_NOT_IMPLEMENTED'),
 ('RECOVERY_CASE_MINIMUM','RET-02-v1','CASE_FINISHED_OR_CANCELLED','30 days','INVENTORIED_NOT_IMPLEMENTED'),
 ('SUPER_ADMIN_CUSTODY','EXCLUDED-v1','EXPLICIT_OWNER_DECISION','No inferred deletion or key rotation','EXCLUDED');
CREATE TRIGGER retention_policy_catalog_immutable BEFORE UPDATE OR DELETE ON retention_policy_catalog
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();

CREATE TABLE retention_audit_hold_request (
 id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE,
 organization_id UUID NOT NULL REFERENCES organization(id), requested_by UUID NOT NULL REFERENCES app_user(id),
 requested_at TIMESTAMPTZ NOT NULL, case_reference UUID NOT NULL,
 justification_code VARCHAR(60) NOT NULL CHECK(justification_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
 ordinary_due_at TIMESTAMPTZ NOT NULL, requested_until TIMESTAMPTZ NOT NULL,
 CHECK(requested_until>ordinary_due_at AND requested_until<=ordinary_due_at+INTERVAL '30 days')
);
CREATE TABLE retention_audit_hold_approval (
 request_id UUID PRIMARY KEY REFERENCES retention_audit_hold_request(id),
 approved_by UUID NOT NULL REFERENCES app_user(id), approved_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER retention_audit_hold_request_immutable BEFORE UPDATE OR DELETE ON retention_audit_hold_request
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER retention_audit_hold_approval_immutable BEFORE UPDATE OR DELETE ON retention_audit_hold_approval
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE FUNCTION retention_audit_effective_due(p_event_id UUID,p_event_at TIMESTAMPTZ)
RETURNS TIMESTAMPTZ LANGUAGE SQL STABLE STRICT AS $$
 SELECT COALESCE((SELECT h.requested_until FROM retention_audit_hold_request h
 JOIN retention_audit_hold_approval a ON a.request_id=h.id WHERE h.event_id=p_event_id),retention_audit_due(p_event_at))
$$;

CREATE TABLE retention_plan (
 id UUID PRIMARY KEY, category VARCHAR(40) NOT NULL CHECK(category IN('OPERATIONAL_AUDIT','REPORT_EMISSION')),
 organization_id UUID REFERENCES organization(id), requested_by UUID NOT NULL REFERENCES app_user(id),
 case_reference UUID NOT NULL, justification_code VARCHAR(60) NOT NULL CHECK(justification_code ~ '^[A-Z][A-Z0-9_]{2,59}$'),
 requested_at TIMESTAMPTZ NOT NULL, selection_sha256 CHAR(64) NOT NULL CHECK(selection_sha256 ~ '^[a-f0-9]{64}$'),
 item_count INTEGER NOT NULL CHECK(item_count BETWEEN 1 AND 1000),
 policy_version VARCHAR(50) NOT NULL
);
CREATE TABLE retention_plan_item (
 plan_id UUID NOT NULL REFERENCES retention_plan(id) DEFERRABLE INITIALLY DEFERRED,
 resource_id UUID NOT NULL, expected_due_at TIMESTAMPTZ NOT NULL,
 resource_sha256 CHAR(64) NOT NULL CHECK(resource_sha256 ~ '^[a-f0-9]{64}$'),
 PRIMARY KEY(plan_id,resource_id)
);
CREATE TABLE retention_execution (
 plan_id UUID PRIMARY KEY REFERENCES retention_plan(id), technical_actor_id UUID NOT NULL REFERENCES app_user(id),
 executed_at TIMESTAMPTZ NOT NULL, selection_sha256 CHAR(64) NOT NULL,
 retired_count INTEGER NOT NULL CHECK(retired_count BETWEEN 1 AND 1000)
);
-- A private transaction-scoped authority, never a session GUC or a persistent bypass.
CREATE TABLE retention_execution_authority (
 plan_id UUID PRIMARY KEY REFERENCES retention_plan(id), transaction_id BIGINT NOT NULL, backend_pid INTEGER NOT NULL
);
CREATE TABLE retention_controlled_copy (
 id UUID PRIMARY KEY, source_category VARCHAR(40) NOT NULL CHECK(source_category IN('OPERATIONAL_AUDIT','REPORT_EMISSION')),
 source_reference UUID NOT NULL, medium_reference UUID NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 recorded_by UUID NOT NULL REFERENCES app_user(id), recorded_at TIMESTAMPTZ NOT NULL,
 synthetic BOOLEAN NOT NULL
);
CREATE TABLE retention_copy_evidence (
 id UUID PRIMARY KEY, copy_id UUID NOT NULL REFERENCES retention_controlled_copy(id),
 action VARCHAR(40) NOT NULL CHECK(action IN('RETIRED','REAPPLIED_BEFORE_ACCESS')),
 procedure_reference UUID NOT NULL, recorded_by UUID NOT NULL REFERENCES app_user(id), recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER retention_controlled_copy_immutable BEFORE UPDATE OR DELETE ON retention_controlled_copy
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER retention_copy_evidence_immutable BEFORE UPDATE OR DELETE ON retention_copy_evidence
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER retention_plan_immutable BEFORE UPDATE OR DELETE ON retention_plan
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER retention_plan_item_immutable BEFORE UPDATE OR DELETE ON retention_plan_item
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER retention_execution_immutable BEFORE UPDATE OR DELETE ON retention_execution
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();

CREATE FUNCTION retention_require_actor(p_actor UUID,p_role VARCHAR,p_org UUID) RETURNS VOID
LANGUAGE plpgsql STABLE AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=p_actor AND u.active AND u.email_verified_at IS NOT NULL
  AND u.role=p_role AND u.organization_id IS NOT DISTINCT FROM p_org
  AND (p_org IS NULL OR EXISTS(SELECT 1 FROM organization o WHERE o.id=p_org AND o.status='ACTIVE')))
 THEN RAISE EXCEPTION 'RETENTION_ACTOR_NOT_AUTHORIZED' USING ERRCODE='42501'; END IF;
END $$;

CREATE FUNCTION retention_request_hold(p_id UUID,p_event UUID,p_actor UUID,p_case UUID,p_reason VARCHAR,
 p_until TIMESTAMPTZ,p_now TIMESTAMPTZ) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE e audit_event%ROWTYPE; due_at TIMESTAMPTZ;
BEGIN
 SELECT * INTO e FROM audit_event WHERE id=p_event;
 IF NOT FOUND OR e.organization_id IS NULL THEN RAISE EXCEPTION 'RETENTION_RESOURCE_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 PERFORM retention_require_actor(p_actor,'ADMIN',e.organization_id);
 PERFORM 1 FROM organization WHERE id=e.organization_id FOR NO KEY UPDATE;
 IF NOT EXISTS(SELECT 1 FROM audit_event WHERE id=p_event) THEN RAISE EXCEPTION 'RETENTION_RESOURCE_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 due_at:=retention_audit_due(e.occurred_at);
 IF p_until IS NULL OR p_now IS NULL OR p_until<=due_at OR p_until>due_at+INTERVAL '30 days' OR p_until<=p_now
 THEN RAISE EXCEPTION 'RETENTION_HOLD_OUTSIDE_FIXED_LIMIT' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM retention_execution_authority x JOIN retention_plan_item i ON i.plan_id=x.plan_id WHERE i.resource_id=p_event)
 THEN RAISE EXCEPTION 'RETENTION_RESOURCE_ALREADY_RETIRING' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_audit_hold_request VALUES(p_id,p_event,e.organization_id,p_actor,p_now,p_case,p_reason,due_at,p_until);
 RETURN p_id;
END $$;
CREATE FUNCTION retention_approve_hold(p_request UUID,p_actor UUID,p_now TIMESTAMPTZ) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE h retention_audit_hold_request%ROWTYPE;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 SELECT * INTO h FROM retention_audit_hold_request WHERE id=p_request;
 IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_REQUEST_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM organization WHERE id=h.organization_id FOR NO KEY UPDATE;
 IF p_now IS NULL OR p_now>=h.requested_until OR NOT EXISTS(SELECT 1 FROM audit_event WHERE id=h.event_id)
 THEN RAISE EXCEPTION 'RETENTION_REQUEST_EXPIRED_OR_RETIRED' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_audit_hold_approval VALUES(p_request,p_actor,p_now) ON CONFLICT(request_id) DO NOTHING;
 RETURN p_request;
END $$;

CREATE FUNCTION retention_prepare_plan(p_id UUID,p_category VARCHAR,p_org UUID,p_actor UUID,p_case UUID,
 p_reason VARCHAR,p_ids UUID[],p_now TIMESTAMPTZ) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE n INTEGER; selected_hash TEXT; due_at TIMESTAMPTZ; policy TEXT;
BEGIN
 IF p_now IS NULL OR p_ids IS NULL OR cardinality(p_ids) NOT BETWEEN 1 AND 1000
  OR cardinality(p_ids)<>(SELECT count(DISTINCT v) FROM unnest(p_ids) v)
 THEN RAISE EXCEPTION 'RETENTION_EXACT_NONEMPTY_SELECTION_REQUIRED' USING ERRCODE='23514'; END IF;
 IF p_org IS NULL THEN
  IF p_category<>'OPERATIONAL_AUDIT' THEN RAISE EXCEPTION 'RETENTION_PLATFORM_SCOPE_INVALID' USING ERRCODE='23514'; END IF;
  PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 ELSE
  PERFORM retention_require_actor(p_actor,'ADMIN',p_org);
  PERFORM 1 FROM organization WHERE id=p_org FOR NO KEY UPDATE;
 END IF;
 IF p_category='OPERATIONAL_AUDIT' THEN
  INSERT INTO retention_plan_item
   SELECT p_id,a.id,retention_audit_effective_due(a.id,a.occurred_at),
    encode(sha256(convert_to(to_jsonb(a)::TEXT,'UTF8')),'hex') FROM audit_event a
   WHERE a.id=ANY(p_ids) AND a.organization_id IS NOT DISTINCT FROM p_org;
  policy:='AUD-L07-02-v1';
 ELSIF p_category='REPORT_EMISSION' THEN
  SELECT ended_at+INTERVAL '90 days' INTO due_at FROM pilot_lifecycle WHERE id='PILOT';
  IF due_at IS NULL THEN RAISE EXCEPTION 'RETENTION_ACTUAL_PILOT_END_NOT_RECORDED' USING ERRCODE='23514'; END IF;
  INSERT INTO retention_plan_item
   SELECT p_id,e.id,due_at,e.content_sha256 FROM report_emission e
    JOIN report_emission_content c ON c.emission_id=e.id JOIN report_emission_series s ON s.id=c.series_id
   WHERE e.id=ANY(p_ids) AND s.organization_id=p_org;
  policy:='REP-RET-L07-03-v1';
 ELSE RAISE EXCEPTION 'RETENTION_CATEGORY_NOT_IMPLEMENTED' USING ERRCODE='23514'; END IF;
 SELECT count(*),encode(sha256(convert_to(string_agg(resource_id::TEXT||'|'||extract(epoch from expected_due_at)::TEXT||'|'||resource_sha256,'\n' ORDER BY resource_id),'UTF8')),'hex')
  INTO n,selected_hash FROM retention_plan_item WHERE plan_id=p_id;
 IF n<>cardinality(p_ids) THEN RAISE EXCEPTION 'RETENTION_SELECTION_OUTSIDE_SCOPE_OR_ABSENT' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_plan VALUES(p_id,p_category,p_org,p_actor,p_case,p_reason,p_now,selected_hash,n,policy);
 RETURN p_id;
END $$;

CREATE FUNCTION retention_delete_is_authorized(p_category VARCHAR,p_resource UUID) RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM retention_execution_authority x JOIN retention_plan p ON p.id=x.plan_id
  JOIN retention_plan_item i ON i.plan_id=p.id WHERE x.transaction_id=txid_current() AND x.backend_pid=pg_backend_pid()
  AND p.category=p_category AND i.resource_id=p_resource)
$$;
CREATE OR REPLACE FUNCTION prevent_audit_event_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND retention_delete_is_authorized('OPERATIONAL_AUDIT',OLD.id) THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'audit_event is append-only; exact authorized expired retirement required' USING ERRCODE='23514';
END $$;
CREATE FUNCTION retention_report_mutation_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'DELETE' THEN RAISE EXCEPTION 'Report references and content are immutable' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='report_emission_content' OR TG_TABLE_NAME='report_emission_request' THEN
  IF retention_delete_is_authorized('REPORT_EMISSION',OLD.emission_id) THEN RETURN OLD; END IF;
 ELSIF TG_TABLE_NAME='report_emission_series' THEN
  IF NOT EXISTS(SELECT 1 FROM report_emission_content WHERE series_id=OLD.id)
   AND EXISTS(SELECT 1 FROM report_emission e WHERE e.series_id=OLD.id AND retention_delete_is_authorized('REPORT_EMISSION',e.id))
  THEN RETURN OLD; END IF;
 END IF;
 RAISE EXCEPTION 'Report retirement requires an exact authorized expired plan' USING ERRCODE='23514';
END $$;
DROP TRIGGER report_emission_content_immutable ON report_emission_content;
DROP TRIGGER report_emission_series_immutable ON report_emission_series;
DROP TRIGGER report_emission_request_immutable ON report_emission_request;
CREATE TRIGGER report_emission_content_immutable BEFORE UPDATE OR DELETE ON report_emission_content
 FOR EACH ROW EXECUTE FUNCTION retention_report_mutation_guard();
CREATE TRIGGER report_emission_series_immutable BEFORE UPDATE OR DELETE ON report_emission_series
 FOR EACH ROW EXECUTE FUNCTION retention_report_mutation_guard();
CREATE TRIGGER report_emission_request_immutable BEFORE UPDATE OR DELETE ON report_emission_request
 FOR EACH ROW EXECUTE FUNCTION retention_report_mutation_guard();

CREATE FUNCTION retention_execute_plan(p_id UUID,p_expected_sha VARCHAR,p_actor UUID,p_now TIMESTAMPTZ) RETURNS INTEGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE p retention_plan%ROWTYPE; i retention_plan_item%ROWTYPE; e audit_event%ROWTYPE;
 actual_hash TEXT; actual_due TIMESTAMPTZ; n INTEGER; prior INTEGER;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 SELECT * INTO p FROM retention_plan WHERE id=p_id FOR UPDATE;
 IF NOT FOUND OR p_expected_sha IS NULL OR p.selection_sha256<>p_expected_sha
 THEN RAISE EXCEPTION 'RETENTION_APPROVED_SELECTION_MISMATCH' USING ERRCODE='23514'; END IF;
 SELECT retired_count INTO prior FROM retention_execution WHERE plan_id=p_id;
 IF FOUND THEN RETURN prior; END IF;
 IF p.organization_id IS NOT NULL THEN PERFORM 1 FROM organization WHERE id=p.organization_id FOR NO KEY UPDATE; END IF;
 IF p_now IS NULL OR p_now<p.requested_at THEN RAISE EXCEPTION 'RETENTION_SERVER_TIME_INVALID' USING ERRCODE='23514'; END IF;
 -- Validate every item before deleting any; a hold/correction cannot partially retire a plan.
 FOR i IN SELECT * FROM retention_plan_item WHERE plan_id=p_id ORDER BY resource_id LOOP
  IF p.category='OPERATIONAL_AUDIT' THEN
   SELECT * INTO e FROM audit_event WHERE id=i.resource_id AND organization_id IS NOT DISTINCT FROM p.organization_id FOR UPDATE;
   IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_RESOURCE_CHANGED_OR_ABSENT' USING ERRCODE='23514'; END IF;
   actual_hash:=encode(sha256(convert_to(to_jsonb(e)::TEXT,'UTF8')),'hex');
   actual_due:=retention_audit_effective_due(e.id,e.occurred_at);
  ELSE
   SELECT r.content_sha256,l.ended_at+INTERVAL '90 days' INTO actual_hash,actual_due FROM report_emission r
    JOIN report_emission_content c ON c.emission_id=r.id JOIN report_emission_series s ON s.id=c.series_id
    CROSS JOIN pilot_lifecycle l WHERE r.id=i.resource_id AND s.organization_id=p.organization_id AND l.id='PILOT' FOR UPDATE OF c;
   IF NOT FOUND THEN RAISE EXCEPTION 'RETENTION_RESOURCE_CHANGED_OR_ABSENT' USING ERRCODE='23514'; END IF;
  END IF;
  IF actual_hash<>i.resource_sha256 OR actual_due<>i.expected_due_at OR p_now<actual_due
  THEN RAISE EXCEPTION 'RETENTION_NOT_EXPIRED_OR_SELECTION_STALE' USING ERRCODE='23514'; END IF;
 END LOOP;
 INSERT INTO retention_execution_authority VALUES(p_id,txid_current(),pg_backend_pid());
 IF p.category='OPERATIONAL_AUDIT' THEN
  DELETE FROM audit_event WHERE id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  GET DIAGNOSTICS n=ROW_COUNT;
 ELSE
  INSERT INTO report_emission_retirement(emission_id,retired_at,policy_version,procedure_id)
   SELECT resource_id,p_now,p.policy_version,p_id FROM retention_plan_item WHERE plan_id=p_id;
  DELETE FROM report_emission_request WHERE emission_id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  DELETE FROM report_emission_content WHERE emission_id IN(SELECT resource_id FROM retention_plan_item WHERE plan_id=p_id);
  GET DIAGNOSTICS n=ROW_COUNT;
  DELETE FROM report_emission_series s WHERE NOT EXISTS(SELECT 1 FROM report_emission_content c WHERE c.series_id=s.id)
   AND s.id IN(SELECT r.series_id FROM report_emission r JOIN retention_plan_item i ON i.resource_id=r.id WHERE i.plan_id=p_id);
 END IF;
 IF n<>p.item_count THEN RAISE EXCEPTION 'RETENTION_ATOMIC_COUNT_MISMATCH' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_execution VALUES(p_id,p_actor,p_now,p_expected_sha,n);
 DELETE FROM retention_execution_authority WHERE plan_id=p_id;
 RETURN n;
END $$;

CREATE FUNCTION retention_register_copy(p_id UUID,p_category VARCHAR,p_source UUID,p_medium UUID,p_actor UUID,p_now TIMESTAMPTZ,p_synthetic BOOLEAN)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
DECLARE due_at TIMESTAMPTZ;
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 IF p_category='OPERATIONAL_AUDIT' THEN
  SELECT retention_audit_due(occurred_at) INTO due_at FROM audit_event WHERE id=p_source;
 ELSIF p_category='REPORT_EMISSION' THEN
  SELECT l.ended_at+INTERVAL '90 days' INTO due_at FROM pilot_lifecycle l,report_emission_content c WHERE l.id='PILOT' AND c.emission_id=p_source;
 END IF;
 IF due_at IS NULL THEN RAISE EXCEPTION 'RETENTION_COPY_SOURCE_OR_ANCHOR_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_controlled_copy VALUES(p_id,p_category,p_source,p_medium,due_at,p_actor,p_now,p_synthetic);
 RETURN p_id;
END $$;
CREATE FUNCTION retention_attest_copy(p_id UUID,p_copy UUID,p_action VARCHAR,p_procedure UUID,p_actor UUID,p_now TIMESTAMPTZ)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public SET timezone='UTC' AS $$
BEGIN
 PERFORM retention_require_actor(p_actor,'SUPER_ADMIN',NULL);
 IF NOT EXISTS(SELECT 1 FROM retention_controlled_copy WHERE id=p_copy) THEN RAISE EXCEPTION 'RETENTION_COPY_NOT_AVAILABLE' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_copy_evidence VALUES(p_id,p_copy,p_action,p_procedure,p_actor,p_now);
 RETURN p_id;
END $$;
CREATE FUNCTION retention_copy_restore_ready(p_copy UUID,p_as_of TIMESTAMPTZ) RETURNS BOOLEAN
LANGUAGE SQL STABLE STRICT AS $$
 SELECT EXISTS(SELECT 1 FROM retention_controlled_copy c WHERE c.id=p_copy AND p_as_of<c.expires_at
  AND EXISTS(SELECT 1 FROM retention_copy_evidence e WHERE e.copy_id=c.id AND e.action='REAPPLIED_BEFORE_ACCESS' AND e.recorded_at<=p_as_of)
  AND NOT EXISTS(SELECT 1 FROM retention_copy_evidence e WHERE e.copy_id=c.id AND e.action='RETIRED'))
$$;

-- Public application roles can read the protocol references but cannot mint a deletion authority
-- or edit its immutable selections. Application-layer authentication/reauthentication is also required.
REVOKE ALL ON retention_execution_authority FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_request_hold(UUID,UUID,UUID,UUID,VARCHAR,TIMESTAMPTZ,TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_approve_hold(UUID,UUID,TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_prepare_plan(UUID,VARCHAR,UUID,UUID,UUID,VARCHAR,UUID[],TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_execute_plan(UUID,VARCHAR,UUID,TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_register_copy(UUID,VARCHAR,UUID,UUID,UUID,TIMESTAMPTZ,BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION retention_attest_copy(UUID,UUID,VARCHAR,UUID,UUID,TIMESTAMPTZ) FROM PUBLIC;
DO $$
DECLARE app_role TEXT; protected_table TEXT;
BEGIN
 FOREACH app_role IN ARRAY ARRAY['rf_app','rf_test'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=app_role) THEN
   FOREACH protected_table IN ARRAY ARRAY['retention_policy_catalog','retention_audit_hold_request','retention_audit_hold_approval','retention_plan','retention_plan_item','retention_execution','retention_execution_authority','retention_controlled_copy','retention_copy_evidence'] LOOP
    EXECUTE format('REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON public.%I FROM %I',protected_table,app_role);
    IF protected_table<>'retention_execution_authority' THEN EXECUTE format('GRANT SELECT ON public.%I TO %I',protected_table,app_role);
    ELSE EXECUTE format('REVOKE ALL ON public.%I FROM %I',protected_table,app_role); END IF;
   END LOOP;
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_request_hold(UUID,UUID,UUID,UUID,VARCHAR,TIMESTAMPTZ,TIMESTAMPTZ) TO %I',app_role);
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_approve_hold(UUID,UUID,TIMESTAMPTZ) TO %I',app_role);
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_prepare_plan(UUID,VARCHAR,UUID,UUID,UUID,VARCHAR,UUID[],TIMESTAMPTZ) TO %I',app_role);
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_execute_plan(UUID,VARCHAR,UUID,TIMESTAMPTZ) TO %I',app_role);
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_register_copy(UUID,VARCHAR,UUID,UUID,UUID,TIMESTAMPTZ,BOOLEAN) TO %I',app_role);
   EXECUTE format('GRANT EXECUTE ON FUNCTION retention_attest_copy(UUID,UUID,VARCHAR,UUID,UUID,TIMESTAMPTZ) TO %I',app_role);
  END IF;
 END LOOP;
END $$;
