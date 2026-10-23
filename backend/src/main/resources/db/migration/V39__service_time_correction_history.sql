-- Q064: effective times are a separate append-only history; originals and GPS never change.
ALTER TABLE service_execution_segment ADD CONSTRAINT uq_segment_service_tenant UNIQUE(id,service_id,organization_id);
CREATE TABLE service_time_request (
 id UUID PRIMARY KEY, service_id UUID NOT NULL, organization_id UUID NOT NULL,
 segment_id UUID NOT NULL, requested_by UUID NOT NULL REFERENCES app_user(id),
 request_key UUID NOT NULL, reason VARCHAR(300) NOT NULL CHECK(length(btrim(reason))>0),
 requested_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(segment_id,service_id,organization_id) REFERENCES service_execution_segment(id,service_id,organization_id),
 UNIQUE(requested_by,request_key), UNIQUE(id,service_id,organization_id)
);
CREATE TABLE service_time_correction (
 id UUID PRIMARY KEY, service_id UUID NOT NULL, organization_id UUID NOT NULL,
 actor_id UUID NOT NULL REFERENCES app_user(id), revision INTEGER NOT NULL CHECK(revision>0),
 operation_key UUID NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 previous_service_version BIGINT NOT NULL, resulting_service_version BIGINT NOT NULL,
 reason VARCHAR(300) NOT NULL CHECK(length(btrim(reason))>0), corrected_at TIMESTAMPTZ NOT NULL,
 request_id UUID, original_terminal_result VARCHAR(80), effective_terminal_result VARCHAR(80) NOT NULL,
 verification_effect VARCHAR(80) NOT NULL,
 FOREIGN KEY(service_id,organization_id) REFERENCES scheduled_service(id,organization_id),
 FOREIGN KEY(request_id,service_id,organization_id) REFERENCES service_time_request(id,service_id,organization_id),
 UNIQUE(service_id,revision), UNIQUE(actor_id,operation_key), UNIQUE(id,service_id,organization_id), UNIQUE(request_id),
 CHECK(resulting_service_version=previous_service_version+1)
);
CREATE TABLE service_time_correction_segment (
 correction_id UUID NOT NULL, segment_id UUID NOT NULL, service_id UUID NOT NULL, organization_id UUID NOT NULL,
 ordinal INTEGER NOT NULL CHECK(ordinal>0),
 before_started_at TIMESTAMPTZ NOT NULL, before_ended_at TIMESTAMPTZ NOT NULL,
 corrected_started_at TIMESTAMPTZ NOT NULL, corrected_ended_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(correction_id,segment_id), UNIQUE(correction_id,ordinal),
 FOREIGN KEY(correction_id,service_id,organization_id) REFERENCES service_time_correction(id,service_id,organization_id),
 FOREIGN KEY(segment_id,service_id,organization_id) REFERENCES service_execution_segment(id,service_id,organization_id),
 CHECK(before_ended_at>=before_started_at), CHECK(corrected_ended_at>=corrected_started_at)
);
CREATE FUNCTION service_time_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='service_time_request' THEN
  IF NOT EXISTS(SELECT 1 FROM service_execution_segment e JOIN driver d ON d.id=e.driver_id
     JOIN app_user u ON u.id=d.user_id WHERE e.id=NEW.segment_id AND e.ended_at IS NOT NULL
     AND e.organization_id=NEW.organization_id AND u.id=NEW.requested_by
     AND u.organization_id=e.organization_id AND u.role='CONDUCTOR')
  THEN RAISE EXCEPTION 'Only the responsible conductor requests a time correction' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM app_user u JOIN scheduled_service s ON s.id=NEW.service_id
    WHERE u.id=NEW.actor_id AND u.role='ADMIN' AND u.organization_id=NEW.organization_id
     AND s.organization_id=NEW.organization_id AND s.status='COMPLETED')
  THEN RAISE EXCEPTION 'Only tenant ADMIN corrects a completed service' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER time_request_actor BEFORE INSERT ON service_time_request FOR EACH ROW EXECUTE FUNCTION service_time_actor_guard();
CREATE TRIGGER time_correction_actor BEFORE INSERT ON service_time_correction FOR EACH ROW EXECUTE FUNCTION service_time_actor_guard();
CREATE TRIGGER time_request_immutable BEFORE UPDATE OR DELETE ON service_time_request FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER time_correction_immutable BEFORE UPDATE OR DELETE ON service_time_correction FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER time_correction_segment_immutable BEFORE UPDATE OR DELETE ON service_time_correction_segment FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE INDEX ix_time_request_tenant_service ON service_time_request(organization_id,service_id,requested_at);
