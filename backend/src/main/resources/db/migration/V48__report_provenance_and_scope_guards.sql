-- L07 hardening. Existing applied SQL and prior rows remain unchanged.
ALTER TABLE report_emission ADD CONSTRAINT uq_report_emission_series_identity UNIQUE(id,series_id);
ALTER TABLE report_emission_content ADD CONSTRAINT fk_report_content_same_series
 FOREIGN KEY(emission_id,series_id) REFERENCES report_emission(id,series_id);
CREATE FUNCTION report_emission_revision_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.revision>1 AND NOT EXISTS(SELECT 1 FROM report_emission p WHERE p.id=NEW.previous_emission_id
  AND p.series_id=NEW.series_id AND p.revision=NEW.revision-1 AND p.issued_at<=NEW.issued_at)
 THEN RAISE EXCEPTION 'Report revision must extend its previous immutable emission' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER report_emission_revision_guard BEFORE INSERT ON report_emission
 FOR EACH ROW EXECUTE FUNCTION report_emission_revision_guard();
CREATE FUNCTION report_emission_content_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE s report_emission_series%ROWTYPE;
BEGIN
 SELECT * INTO s FROM report_emission_series WHERE id=NEW.series_id;
 IF NOT FOUND OR jsonb_typeof(NEW.body_json)<>'object' OR NEW.body_json->>'scope' IS DISTINCT FROM s.scope
  OR NEW.body_json->>'from' IS DISTINCT FROM s.from_day::TEXT OR NEW.body_json->>'to' IS DISTINCT FROM s.to_day::TEXT
  OR NEW.body_json->>'zoneId' IS DISTINCT FROM s.zone_id OR jsonb_typeof(NEW.body_json->'items') IS DISTINCT FROM 'array'
 THEN RAISE EXCEPTION 'Report content must match its authorized series' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER report_emission_content_scope BEFORE INSERT ON report_emission_content
 FOR EACH ROW EXECUTE FUNCTION report_emission_content_guard();
CREATE FUNCTION report_request_owner_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM report_emission_content c JOIN report_emission_series s ON s.id=c.series_id
  WHERE c.emission_id=NEW.emission_id AND s.issuer_id=NEW.issuer_id)
 THEN RAISE EXCEPTION 'Report request must belong to its issuer' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER report_request_owner_guard BEFORE INSERT ON report_emission_request
 FOR EACH ROW EXECUTE FUNCTION report_request_owner_guard();
CREATE FUNCTION report_offer_original_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.service_id IS NOT NULL AND
  (NEW.organization_id,NEW.service_id,NEW.driver_id,NEW.vehicle_id,NEW.scheduled_at,NEW.scheduled_end_at,
   NEW.created_at,NEW.route_version_id,NEW.service_type_version_id,NEW.response_mode)
  IS DISTINCT FROM
  (OLD.organization_id,OLD.service_id,OLD.driver_id,OLD.vehicle_id,OLD.scheduled_at,OLD.scheduled_end_at,
   OLD.created_at,OLD.route_version_id,OLD.service_type_version_id,OLD.response_mode)
 THEN RAISE EXCEPTION 'Canonical service offer provenance cannot be rewritten; use a new offer' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER report_offer_original_guard BEFORE UPDATE ON assignment
 FOR EACH ROW EXECUTE FUNCTION report_offer_original_guard();
CREATE FUNCTION mobile_preferences_owner_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (NEW.user_id,NEW.organization_id) IS DISTINCT FROM (OLD.user_id,OLD.organization_id)
 THEN RAISE EXCEPTION 'Preference ownership cannot change' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.user_id AND u.organization_id=NEW.organization_id AND u.role='CONDUCTOR')
 THEN RAISE EXCEPTION 'Preferences require a same-tenant CONDUCTOR' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER mobile_preferences_owner_guard BEFORE INSERT OR UPDATE ON mobile_preferences
 FOR EACH ROW EXECUTE FUNCTION mobile_preferences_owner_guard();

-- V46 remains immutable. Fix ambiguous PL/pgSQL row-variable/SQL alias in the report retirement branch.
CREATE OR REPLACE FUNCTION retention_execute_plan(p_id UUID,p_expected_sha VARCHAR,p_actor UUID,p_now TIMESTAMPTZ) RETURNS INTEGER
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
   AND s.id IN(SELECT r.series_id FROM report_emission r JOIN retention_plan_item chosen ON chosen.resource_id=r.id WHERE chosen.plan_id=p_id);
 END IF;
 IF n<>p.item_count THEN RAISE EXCEPTION 'RETENTION_ATOMIC_COUNT_MISMATCH' USING ERRCODE='23514'; END IF;
 INSERT INTO retention_execution VALUES(p_id,p_actor,p_now,p_expected_sha,n);
 DELETE FROM retention_execution_authority WHERE plan_id=p_id;
 RETURN n;
END $$;

