-- Detailed points are service/segment-bound and expire from capture, including incident evidence.
CREATE TABLE service_gps_point (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL, driver_id UUID NOT NULL,
 segment_id UUID NOT NULL,
 latitude NUMERIC(9,6) NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude NUMERIC(9,6) NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 accuracy_m NUMERIC(8,2) NOT NULL CHECK(accuracy_m>=0),
 captured_at TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 UNIQUE(segment_id,captured_at), UNIQUE(id,organization_id), UNIQUE(id,organization_id,driver_id),
 FOREIGN KEY(segment_id,organization_id,driver_id) REFERENCES service_execution_segment(id,organization_id,driver_id),
 CHECK(captured_at<=received_at AND received_at<expires_at),
 CHECK(expires_at=captured_at+INTERVAL '168 hours')
);
CREATE INDEX ix_gps_expiry ON service_gps_point(expires_at);
CREATE INDEX ix_gps_segment_capture ON service_gps_point(segment_id,captured_at,id);
CREATE FUNCTION service_gps_point_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN RAISE EXCEPTION 'GPS originals are immutable' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM service_execution_segment e JOIN driver d ON d.id=e.driver_id
   JOIN assignment a ON a.id=e.assignment_id
   WHERE e.id=NEW.segment_id AND e.organization_id=NEW.organization_id AND e.driver_id=NEW.driver_id
    AND e.ended_at IS NULL AND a.status='EN_SERVICIO' AND d.location_consent
    AND NEW.captured_at>=e.started_at)
 THEN RAISE EXCEPTION 'GPS requires its own executing segment and consent' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER service_gps_point_original BEFORE INSERT OR UPDATE ON service_gps_point
 FOR EACH ROW EXECUTE FUNCTION service_gps_point_guard();
ALTER TABLE driver_current_location ADD COLUMN gps_point_id UUID,
 ADD CONSTRAINT fk_current_gps_point FOREIGN KEY(gps_point_id,organization_id,driver_id)
 REFERENCES service_gps_point(id,organization_id,driver_id) ON DELETE SET NULL (gps_point_id);
ALTER TABLE assignment_route_declaration ADD COLUMN evidence_point_id UUID REFERENCES service_gps_point(id) ON DELETE SET NULL;

CREATE TABLE service_closure_notice (
 assignment_id UUID PRIMARY KEY REFERENCES assignment_route_declaration(assignment_id),
 organization_id UUID NOT NULL REFERENCES organization(id), created_at TIMESTAMPTZ NOT NULL,
 kind VARCHAR(30) NOT NULL CHECK(kind='TERMINAL_DISCREPANCY')
);
CREATE TRIGGER service_closure_notice_immutable BEFORE UPDATE OR DELETE ON service_closure_notice
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();

CREATE TABLE gps_access_request (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 driver_id UUID NOT NULL, requester_id UUID NOT NULL REFERENCES app_user(id),
 service_id UUID NOT NULL, request_key UUID NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 from_at TIMESTAMPTZ NOT NULL, to_at TIMESTAMPTZ NOT NULL,
 purpose VARCHAR(30) NOT NULL CHECK(purpose='OWN_LOCATION_ACCESS'),
 requested_at TIMESTAMPTZ NOT NULL, review_due_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','APPROVED','NO_DATA','DENIED')),
 version BIGINT NOT NULL DEFAULT 0, reviewed_by UUID REFERENCES app_user(id), reviewed_at TIMESTAMPTZ,
 verification_reference VARCHAR(80), grant_expires_at TIMESTAMPTZ,
 UNIQUE(requester_id,request_key),
 FOREIGN KEY(driver_id,organization_id) REFERENCES driver(id,organization_id),
 FOREIGN KEY(service_id,organization_id) REFERENCES scheduled_service(id,organization_id),
 CHECK(to_at>=from_at AND to_at<=requested_at),
 CHECK(review_due_at=requested_at+INTERVAL '48 hours'),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND grant_expires_at IS NULL)
    OR(status<>'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND verification_reference IS NOT NULL)),
 CHECK((status='APPROVED' AND grant_expires_at>reviewed_at AND grant_expires_at<=reviewed_at+INTERVAL '15 minutes')
    OR(status<>'APPROVED' AND grant_expires_at IS NULL))
);
CREATE INDEX ix_gps_access_tenant ON gps_access_request(organization_id,requested_at,id);
CREATE FUNCTION gps_access_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'GPS request history must be retained' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (OLD.status<>'PENDING' OR NEW.status='PENDING' OR
   (to_jsonb(NEW)-ARRAY['status','version','reviewed_by','reviewed_at','verification_reference','grant_expires_at']) IS DISTINCT FROM
   (to_jsonb(OLD)-ARRAY['status','version','reviewed_by','reviewed_at','verification_reference','grant_expires_at']))
 THEN RAISE EXCEPTION 'GPS review may only resolve its original pending request' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM driver d JOIN app_user u ON u.id=d.user_id
    WHERE d.id=NEW.driver_id AND d.organization_id=NEW.organization_id AND u.id=NEW.requester_id AND u.role='CONDUCTOR')
 THEN RAISE EXCEPTION 'GPS requester mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.reviewed_by IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app_user u
    WHERE u.id=NEW.reviewed_by AND u.organization_id=NEW.organization_id AND u.role='ADMIN')
 THEN RAISE EXCEPTION 'GPS reviewer tenant mismatch' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gps_access_request_guard BEFORE INSERT OR UPDATE OR DELETE ON gps_access_request
 FOR EACH ROW EXECUTE FUNCTION gps_access_guard();

CREATE TABLE gps_incident_evidence (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 incident_id UUID NOT NULL REFERENCES incident(id), point_id UUID REFERENCES service_gps_point(id) ON DELETE SET NULL,
 point_reference UUID NOT NULL, created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL,
 captured_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 justification VARCHAR(30) NOT NULL CHECK(justification IN ('SAFETY_REVIEW','SERVICE_DISCREPANCY')),
 UNIQUE(incident_id,point_reference), CHECK(expires_at=captured_at+INTERVAL '168 hours')
);
-- Deleting an expired point clears its FK; references/results survive without coordinates.
CREATE FUNCTION gps_incident_evidence_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN
  IF NEW.point_id IS NOT NULL OR (to_jsonb(NEW)-'point_id') IS DISTINCT FROM(to_jsonb(OLD)-'point_id')
  THEN RAISE EXCEPTION 'Incident GPS evidence is immutable except for expiry unlinking' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM service_gps_point p JOIN service_execution_segment e ON e.id=p.segment_id
     JOIN incident i ON i.id=NEW.incident_id JOIN assignment a ON a.id=i.assignment_id
     JOIN app_user u ON u.id=NEW.created_by
     WHERE p.id=NEW.point_id AND p.id=NEW.point_reference AND p.organization_id=NEW.organization_id
      AND i.organization_id=p.organization_id AND i.driver_id=p.driver_id AND a.service_id=e.service_id
      AND u.organization_id=p.organization_id AND u.role='ADMIN'
      AND NEW.captured_at=p.captured_at AND NEW.expires_at=p.expires_at)
  THEN RAISE EXCEPTION 'Incident evidence must retain its own tenant/service point expiry' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER gps_incident_evidence_guard BEFORE INSERT OR UPDATE ON gps_incident_evidence
 FOR EACH ROW EXECUTE FUNCTION gps_incident_evidence_guard();

-- Harden the V36 contract additively: an inserted segment is open, and end actors remain conductors of its tenant.
CREATE OR REPLACE FUNCTION execution_segment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a assignment%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Execution originals cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND NEW.ended_at IS NOT NULL
 THEN RAISE EXCEPTION 'A new segment must be open' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF OLD.ended_at IS NOT NULL OR
   (to_jsonb(NEW)-ARRAY['ended_at','end_recorded_at','ended_by','end_event_id','end_kind']) IS DISTINCT FROM
   (to_jsonb(OLD)-ARRAY['ended_at','end_recorded_at','ended_by','end_event_id','end_kind'])
  THEN RAISE EXCEPTION 'Execution originals are immutable' USING ERRCODE='23514'; END IF;
  IF NEW.ended_at IS NULL THEN RAISE EXCEPTION 'Only closing an open segment is allowed' USING ERRCODE='23514'; END IF;
 END IF;
 SELECT * INTO a FROM assignment WHERE id=NEW.assignment_id;
 IF a.id IS NULL OR a.driver_id<>NEW.driver_id OR a.vehicle_id<>NEW.vehicle_id OR a.organization_id<>NEW.organization_id
  OR a.service_id IS DISTINCT FROM NEW.service_id OR a.accepted_at IS NULL OR a.started_at IS NULL
  OR a.started_at<>NEW.started_at OR a.response_mode<>'MOBILE_CONFIRMATION'
 THEN RAISE EXCEPTION 'Segment must describe the accepted executing offer' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND a.status<>'EN_SERVICIO'
 THEN RAISE EXCEPTION 'Segment requires a started service' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user u JOIN driver d ON d.user_id=u.id
    WHERE u.id=NEW.started_by AND d.id=NEW.driver_id AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Only the assigned conductor starts a segment' USING ERRCODE='23514'; END IF;
 IF NEW.ended_by IS NOT NULL AND (NOT EXISTS(SELECT 1 FROM app_user u
    WHERE u.id=NEW.ended_by AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id)
    OR(NEW.end_kind IN ('COMPLETED','INTERRUPTED') AND NEW.ended_by<>NEW.started_by))
 THEN RAISE EXCEPTION 'Only the responsible conductor ends this segment' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
