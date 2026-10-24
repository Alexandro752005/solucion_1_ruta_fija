-- SRV-INT-L05-05 approved: interruption is not completion and never releases resources automatically.
ALTER TABLE assignment DROP CONSTRAINT ck_assignment_status, DROP CONSTRAINT ck_assignment_terminal_times;
ALTER TABLE assignment ADD COLUMN interrupted_at TIMESTAMPTZ, ADD COLUMN interruption_reason VARCHAR(32),
 ADD CONSTRAINT ck_assignment_status CHECK(status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO','COMPLETED','REJECTED','CANCELLED','EXPIRED','INTERRUPTED')),
 ADD CONSTRAINT ck_assignment_terminal_times CHECK(
  (status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO','REJECTED','EXPIRED','INTERRUPTED') AND completed_at IS NULL AND cancelled_at IS NULL)
  OR(status='COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
  OR(status='CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)),
 ADD CONSTRAINT ck_assignment_interruption CHECK(
  (status='INTERRUPTED' AND interrupted_at IS NOT NULL AND interruption_reason IS NOT NULL AND started_at IS NOT NULL
    AND interrupted_at>=started_at AND interruption_reason IN('ACCIDENT_RISK','UNSAFE_BREAKDOWN','IMPOSSIBLE_SERVICE'))
  OR(status<>'INTERRUPTED' AND interrupted_at IS NULL AND interruption_reason IS NULL));
ALTER TABLE scheduled_service DROP CONSTRAINT scheduled_service_status_check;
ALTER TABLE scheduled_service ADD CONSTRAINT scheduled_service_status_check
 CHECK(status IN('PENDING_REASSIGNMENT','PENDING_RESPONSE','CONFIRMED','IN_SERVICE','COMPLETED','CANCELLED','INTERRUPTED'));
ALTER TABLE driver DROP CONSTRAINT ck_driver_availability_status;
ALTER TABLE driver ADD CONSTRAINT ck_driver_availability_status
 CHECK(availability_status IN('DISPONIBLE','RESERVADO','EN_SERVICIO','DESCANSO','NO_DISPONIBLE','ATENCION'));
ALTER TABLE vehicle DROP CONSTRAINT ck_vehicle_status;
ALTER TABLE vehicle ADD CONSTRAINT ck_vehicle_status CHECK(status IN('DISPONIBLE','EN_SERVICIO','MANTENIMIENTO','INACTIVO','ATENCION'));
ALTER TABLE mobile_command_receipt DROP CONSTRAINT ck_mobile_command_receipt_type;
ALTER TABLE mobile_command_receipt ADD CONSTRAINT ck_mobile_command_receipt_type
 CHECK(command_type IN('AVAILABILITY_CHANGE','ASSIGNMENT_ACCEPT','ASSIGNMENT_REJECT','ASSIGNMENT_START','ASSIGNMENT_COMPLETE','ASSIGNMENT_INTERRUPT'));

ALTER TABLE service_execution_segment ADD CONSTRAINT uq_segment_offer_tenant UNIQUE(id,assignment_id,organization_id);
ALTER TABLE incident ADD COLUMN execution_segment_id UUID,
 ADD CONSTRAINT ck_incident_segment_offer CHECK(execution_segment_id IS NULL OR assignment_id IS NOT NULL),
 ADD CONSTRAINT fk_incident_execution FOREIGN KEY(execution_segment_id,assignment_id,organization_id)
 REFERENCES service_execution_segment(id,assignment_id,organization_id);
CREATE TABLE service_attention (
 id UUID PRIMARY KEY, service_id UUID NOT NULL, organization_id UUID NOT NULL,
 assignment_id UUID NOT NULL UNIQUE, segment_id UUID NOT NULL UNIQUE,
 driver_id UUID NOT NULL REFERENCES driver(id),vehicle_id UUID NOT NULL REFERENCES vehicle(id),incident_id UUID NOT NULL UNIQUE REFERENCES incident(id),
 recorded_by UUID NOT NULL REFERENCES app_user(id),source VARCHAR(32) NOT NULL CHECK(source IN('CONDUCTOR','ADMIN_VERIFIED_REPORT')),
 operation_key UUID NOT NULL,payload_sha256 CHAR(64) NOT NULL,
 reason VARCHAR(32) NOT NULL CHECK(reason IN('ACCIDENT_RISK','UNSAFE_BREAKDOWN','IMPOSSIBLE_SERVICE')),
 detail VARCHAR(300) NOT NULL CHECK(length(btrim(detail))>0),safe_stop_confirmed BOOLEAN NOT NULL,
 verification_reference VARCHAR(100) NOT NULL,interrupted_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(assignment_id,service_id,organization_id) REFERENCES assignment(id,service_id,organization_id),
 FOREIGN KEY(segment_id,assignment_id,organization_id) REFERENCES service_execution_segment(id,assignment_id,organization_id),
 UNIQUE(recorded_by,operation_key),UNIQUE(id,organization_id),
 CHECK(source<>'ADMIN_VERIFIED_REPORT' OR safe_stop_confirmed)
);
CREATE TABLE service_attention_release (
 id UUID PRIMARY KEY,attention_id UUID NOT NULL,organization_id UUID NOT NULL,actor_id UUID NOT NULL REFERENCES app_user(id),
 operation_key UUID NOT NULL,payload_sha256 CHAR(64) NOT NULL,
 resource_kind VARCHAR(10) NOT NULL CHECK(resource_kind IN('DRIVER','VEHICLE')),
 disposition VARCHAR(24) NOT NULL CHECK(disposition IN('ELIGIBILITY_RECHECKED','READY','WITHDRAWN')),
 reason VARCHAR(300) NOT NULL CHECK(length(btrim(reason))>0),verification_reference VARCHAR(100) NOT NULL,
 safe_stop_and_transfer_confirmed BOOLEAN NOT NULL CHECK(safe_stop_and_transfer_confirmed),released_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(attention_id,organization_id) REFERENCES service_attention(id,organization_id),
 UNIQUE(actor_id,operation_key,resource_kind),
 CHECK((resource_kind='DRIVER')=(disposition='ELIGIBILITY_RECHECKED'))
);
CREATE UNIQUE INDEX uq_attention_final_resource ON service_attention_release(attention_id,resource_kind) WHERE disposition<>'WITHDRAWN';
CREATE UNIQUE INDEX uq_attention_withdrawal ON service_attention_release(attention_id,resource_kind) WHERE disposition='WITHDRAWN';
CREATE INDEX ix_attention_driver ON service_attention(organization_id,driver_id);
CREATE INDEX ix_attention_vehicle ON service_attention(organization_id,vehicle_id);
CREATE FUNCTION service_attention_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM id FROM organization WHERE id=NEW.organization_id FOR NO KEY UPDATE;
 IF TG_TABLE_NAME='service_attention' THEN
  IF NOT EXISTS(SELECT 1 FROM assignment a JOIN service_execution_segment e ON e.assignment_id=a.id
    JOIN incident i ON i.id=NEW.incident_id JOIN app_user u ON u.id=NEW.recorded_by
    WHERE a.id=NEW.assignment_id AND a.service_id=NEW.service_id AND a.organization_id=NEW.organization_id
     AND a.driver_id=NEW.driver_id AND a.vehicle_id=NEW.vehicle_id AND a.status='EN_SERVICIO'
     AND e.id=NEW.segment_id AND e.ended_at IS NULL AND e.started_at<=NEW.interrupted_at
     AND i.execution_segment_id=e.id AND i.driver_id=a.driver_id AND i.organization_id=a.organization_id
     AND u.organization_id=a.organization_id AND((NEW.source='CONDUCTOR' AND u.role='CONDUCTOR' AND u.id=e.started_by)
       OR(NEW.source='ADMIN_VERIFIED_REPORT' AND u.role='ADMIN' AND NEW.safe_stop_confirmed)))
  THEN RAISE EXCEPTION 'Interruption requires its original segment, incident and actual authorized reporter' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND role='ADMIN' AND organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Only tenant ADMIN resolves safety attention' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER service_attention_actor BEFORE INSERT ON service_attention FOR EACH ROW EXECUTE FUNCTION service_attention_actor_guard();
CREATE TRIGGER service_attention_release_actor BEFORE INSERT ON service_attention_release FOR EACH ROW EXECUTE FUNCTION service_attention_actor_guard();
CREATE TRIGGER service_attention_immutable BEFORE UPDATE OR DELETE ON service_attention FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER service_attention_release_immutable BEFORE UPDATE OR DELETE ON service_attention_release FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE FUNCTION protect_interruption_original() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.interrupted_at IS NOT NULL AND(NEW.interrupted_at IS DISTINCT FROM OLD.interrupted_at OR NEW.interruption_reason IS DISTINCT FROM OLD.interruption_reason)
 THEN RAISE EXCEPTION 'Interruption originals cannot be rewritten' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_interruption_original BEFORE UPDATE ON assignment FOR EACH ROW EXECUTE FUNCTION protect_interruption_original();
CREATE FUNCTION protect_safety_resource() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='driver' THEN
  IF(NEW.availability_status IS DISTINCT FROM OLD.availability_status OR NEW.active IS DISTINCT FROM OLD.active)
   AND NEW.availability_status NOT IN('ATENCION','NO_DISPONIBLE') AND EXISTS(SELECT 1 FROM service_attention h
    WHERE h.driver_id=NEW.id AND NOT EXISTS(SELECT 1 FROM service_attention_release r WHERE r.attention_id=h.id AND r.resource_kind='DRIVER'))
  THEN RAISE EXCEPTION 'Driver safety attention requires explicit tenant ADMIN review' USING ERRCODE='23514'; END IF;
 ELSE
  IF(NEW.status IS DISTINCT FROM OLD.status OR NEW.active IS DISTINCT FROM OLD.active)
   AND NOT(NEW.status='ATENCION' OR(NEW.status='INACTIVO' AND NOT NEW.active)) AND EXISTS(SELECT 1 FROM service_attention h
    WHERE h.vehicle_id=NEW.id AND NOT EXISTS(SELECT 1 FROM service_attention_release r WHERE r.attention_id=h.id AND r.resource_kind='VEHICLE' AND r.disposition='READY'))
  THEN RAISE EXCEPTION 'Vehicle safety attention requires explicit tenant ADMIN review' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER driver_safety_attention BEFORE UPDATE ON driver FOR EACH ROW EXECUTE FUNCTION protect_safety_resource();
CREATE TRIGGER vehicle_safety_attention BEFORE UPDATE ON vehicle FOR EACH ROW EXECUTE FUNCTION protect_safety_resource();

-- Exact prior guards extended additively; prior migration bytes preserved.
-- Q-053 permits the exact start instant as deadline. Existing V7 data remains valid.
-- Preserve every other lifecycle condition and all historical response modes.
ALTER TABLE assignment DROP CONSTRAINT ck_assignment_response_lifecycle;
ALTER TABLE assignment
    ADD CONSTRAINT ck_assignment_response_lifecycle CHECK (
        (
            response_mode = 'ADMIN_DIRECT'
            AND status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'CANCELLED')
            AND response_deadline_at IS NULL
            AND accepted_at IS NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL
            AND expired_at IS NULL
        )
        OR
        (
            response_mode = 'MOBILE_CONFIRMATION'
            AND response_deadline_at IS NOT NULL
            AND response_deadline_at <= scheduled_at
            AND (accepted_at IS NULL OR accepted_at <= response_deadline_at)
            AND (rejected_at IS NULL OR rejected_at <= response_deadline_at)
            AND (
                (status = 'PENDING_RESPONSE'
                    AND accepted_at IS NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NULL)
                OR
                (status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'INTERRUPTED')
                    AND accepted_at IS NOT NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NOT NULL)
                OR
                (status = 'REJECTED'
                    AND accepted_at IS NULL
                    AND rejected_at IS NOT NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NULL)
                OR
                (status = 'EXPIRED'
                    AND accepted_at IS NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NOT NULL
                    AND expired_at >= response_deadline_at
                    AND reserved_at IS NULL)
                OR
                (status = 'CANCELLED'
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND (
                        (accepted_at IS NULL AND reserved_at IS NULL)
                        OR (accepted_at IS NOT NULL AND reserved_at IS NOT NULL)
                    ))
            )
        )
    );

CREATE OR REPLACE FUNCTION assignment_service_progress() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.service_id IS NULL THEN RETURN NEW; END IF;
 IF TG_OP='UPDATE' AND NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
 UPDATE scheduled_service SET current_offer_id=NEW.id,
  scheduled_at=NEW.scheduled_at,scheduled_end_at=NEW.scheduled_end_at,
  route_version_id=NEW.route_version_id,service_type_version_id=NEW.service_type_version_id,
  status=CASE NEW.status WHEN 'PENDING_RESPONSE' THEN 'PENDING_RESPONSE' WHEN 'SCHEDULED' THEN 'CONFIRMED'
   WHEN 'EN_SERVICIO' THEN 'IN_SERVICE' WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'INTERRUPTED' THEN 'INTERRUPTED' ELSE 'PENDING_REASSIGNMENT' END,
  version=version+1,updated_at=NEW.updated_at WHERE id=NEW.service_id;
 RETURN NEW;
END; $$;

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
 IF NEW.ended_by IS NOT NULL THEN
  IF NEW.end_kind='INTERRUPTED' THEN
   IF NOT EXISTS(SELECT 1 FROM service_attention h JOIN app_user u ON u.id=h.recorded_by
     WHERE h.segment_id=NEW.id AND h.assignment_id=NEW.assignment_id AND h.organization_id=NEW.organization_id
      AND h.recorded_by=NEW.ended_by AND h.operation_key=NEW.end_event_id AND h.interrupted_at=NEW.ended_at
      AND((h.source='CONDUCTOR' AND u.role='CONDUCTOR' AND u.id=NEW.started_by)
       OR(h.source='ADMIN_VERIFIED_REPORT' AND u.role='ADMIN' AND h.safe_stop_confirmed)))
   THEN RAISE EXCEPTION 'Interruption must retain its actual authorized reporter and safety record' USING ERRCODE='23514'; END IF;
  ELSE
   IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.ended_by AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id)
      OR(NEW.end_kind='COMPLETED' AND NEW.ended_by<>NEW.started_by)
   THEN RAISE EXCEPTION 'Only the responsible conductor completes a segment' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION service_time_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
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
     AND s.organization_id=NEW.organization_id AND s.status IN('COMPLETED','CANCELLED'))
  THEN RAISE EXCEPTION 'Only tenant ADMIN corrects a completed service' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;

