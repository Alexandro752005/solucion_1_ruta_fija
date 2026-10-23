-- SRV-L05-02: a distinct accepted standby offer; only its conductor assumes the next segment.
CREATE TABLE service_handover (
 id UUID PRIMARY KEY,service_id UUID NOT NULL,organization_id UUID NOT NULL,
 outgoing_assignment_id UUID NOT NULL,incoming_driver_id UUID NOT NULL REFERENCES driver(id),
 incoming_vehicle_id UUID NOT NULL REFERENCES vehicle(id),prepared_by UUID NOT NULL REFERENCES app_user(id),
 operation_key UUID NOT NULL,payload_sha256 CHAR(64) NOT NULL,prepared_at TIMESTAMPTZ NOT NULL,
 reason VARCHAR(300) NOT NULL CHECK(length(btrim(reason))>0),point_reference VARCHAR(100) NOT NULL,
 safety_and_transfer_confirmed BOOLEAN NOT NULL CHECK(safety_and_transfer_confirmed),
 scheduled_at TIMESTAMPTZ NOT NULL,scheduled_end_at TIMESTAMPTZ NOT NULL CHECK(scheduled_end_at>scheduled_at),
 UNIQUE(id,service_id,organization_id),UNIQUE(id,organization_id),UNIQUE(prepared_by,operation_key),
 FOREIGN KEY(outgoing_assignment_id,service_id,organization_id) REFERENCES assignment(id,service_id,organization_id)
);
ALTER TABLE assignment ADD COLUMN handover_id UUID,ADD COLUMN transferred_at TIMESTAMPTZ,
 ADD CONSTRAINT uq_handover_incoming_offer UNIQUE(handover_id),
 ADD CONSTRAINT fk_handover_offer FOREIGN KEY(handover_id,service_id,organization_id) REFERENCES service_handover(id,service_id,organization_id);
CREATE TABLE service_handover_resolution (
 id UUID PRIMARY KEY,handover_id UUID NOT NULL UNIQUE,organization_id UUID NOT NULL,
 actor_id UUID NOT NULL REFERENCES app_user(id),operation_key UUID NOT NULL,payload_sha256 CHAR(64) NOT NULL,
 kind VARCHAR(20) NOT NULL CHECK(kind IN('ASSUMED','WITHDRAWN','SERVICE_ENDED')),
 reason VARCHAR(300) NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(actor_id,operation_key),FOREIGN KEY(handover_id,organization_id) REFERENCES service_handover(id,organization_id)
);
CREATE TRIGGER service_handover_immutable BEFORE UPDATE OR DELETE ON service_handover FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER service_handover_resolution_immutable BEFORE UPDATE OR DELETE ON service_handover_resolution FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE INDEX ix_handover_service ON service_handover(organization_id,service_id,prepared_at,id);
DROP INDEX uq_service_live_offer;
CREATE UNIQUE INDEX uq_service_live_offer ON assignment(service_id) WHERE service_id IS NOT NULL AND handover_id IS NULL AND status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO');
CREATE UNIQUE INDEX uq_service_executing_offer ON assignment(service_id) WHERE service_id IS NOT NULL AND status='EN_SERVICIO';
CREATE UNIQUE INDEX uq_service_standby_offer ON assignment(service_id) WHERE service_id IS NOT NULL AND handover_id IS NOT NULL AND status IN('PENDING_RESPONSE','SCHEDULED');
ALTER TABLE assignment DROP CONSTRAINT ck_assignment_status,DROP CONSTRAINT ck_assignment_terminal_times;
ALTER TABLE assignment ADD CONSTRAINT ck_assignment_status CHECK(status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO','COMPLETED','REJECTED','CANCELLED','EXPIRED','INTERRUPTED','TRANSFERRED')),
 ADD CONSTRAINT ck_assignment_terminal_times CHECK(
 (status IN('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO','REJECTED','EXPIRED','INTERRUPTED','TRANSFERRED') AND completed_at IS NULL AND cancelled_at IS NULL)
 OR(status='COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
 OR(status='CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)),
 ADD CONSTRAINT ck_assignment_transfer CHECK((status='TRANSFERRED' AND transferred_at IS NOT NULL AND started_at IS NOT NULL AND transferred_at>=started_at) OR(status<>'TRANSFERRED' AND transferred_at IS NULL));
ALTER TABLE mobile_command_receipt DROP CONSTRAINT ck_mobile_command_receipt_type;
ALTER TABLE mobile_command_receipt ADD CONSTRAINT ck_mobile_command_receipt_type CHECK(command_type IN('AVAILABILITY_CHANGE','ASSIGNMENT_ACCEPT','ASSIGNMENT_REJECT','ASSIGNMENT_START','ASSIGNMENT_COMPLETE','ASSIGNMENT_INTERRUPT','ASSIGNMENT_ASSUME_HANDOVER'));
CREATE FUNCTION handover_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h service_handover%ROWTYPE;a assignment%ROWTYPE;s scheduled_service%ROWTYPE;
BEGIN
 PERFORM 1 FROM organization WHERE id=NEW.organization_id FOR NO KEY UPDATE;
 IF TG_TABLE_NAME='service_handover' THEN
  SELECT * INTO s FROM scheduled_service WHERE id=NEW.service_id AND organization_id=NEW.organization_id;
  SELECT * INTO a FROM assignment WHERE id=NEW.outgoing_assignment_id AND organization_id=NEW.organization_id;
  IF s.id IS NULL OR s.current_offer_id<>a.id OR s.status NOT IN('IN_SERVICE','INTERRUPTED') OR a.status NOT IN('EN_SERVICIO','INTERRUPTED')
    OR a.accepted_at IS NULL OR a.started_at IS NULL OR a.driver_id=NEW.incoming_driver_id OR a.vehicle_id=NEW.incoming_vehicle_id
    OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.prepared_by AND organization_id=NEW.organization_id AND role='ADMIN')
    OR NOT EXISTS(SELECT 1 FROM driver WHERE id=NEW.incoming_driver_id AND organization_id=NEW.organization_id)
    OR NOT EXISTS(SELECT 1 FROM vehicle WHERE id=NEW.incoming_vehicle_id AND organization_id=NEW.organization_id)
    OR EXISTS(SELECT 1 FROM service_handover x JOIN assignment i ON i.handover_id=x.id WHERE x.service_id=NEW.service_id AND i.status IN('PENDING_RESPONSE','SCHEDULED'))
  THEN RAISE EXCEPTION 'Handover requires tenant ADMIN and one distinct safe standby for the current executing participation' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO h FROM service_handover WHERE id=NEW.handover_id AND organization_id=NEW.organization_id;
  SELECT * INTO a FROM assignment WHERE handover_id=h.id;
  IF h.id IS NULL OR a.id IS NULL OR NEW.occurred_at<h.prepared_at
   OR(NEW.kind='ASSUMED' AND(a.status<>'SCHEDULED' OR a.accepted_at IS NULL OR NOT EXISTS(SELECT 1 FROM driver d JOIN app_user u ON u.id=d.user_id WHERE d.id=h.incoming_driver_id AND u.id=NEW.actor_id AND u.role='CONDUCTOR' AND u.organization_id=h.organization_id)))
   OR(NEW.kind='ASSUMED' AND NOT EXISTS(SELECT 1 FROM scheduled_service s JOIN assignment o ON o.id=s.current_offer_id WHERE s.id=h.service_id AND s.organization_id=h.organization_id AND o.id=h.outgoing_assignment_id AND s.status IN('IN_SERVICE','INTERRUPTED') AND o.status IN('EN_SERVICIO','INTERRUPTED')))
   OR(NEW.kind='WITHDRAWN' AND(a.status NOT IN('PENDING_RESPONSE','SCHEDULED') OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=h.organization_id AND role='ADMIN')))
   OR(NEW.kind='SERVICE_ENDED' AND(a.status NOT IN('PENDING_RESPONSE','SCHEDULED') OR NOT EXISTS(SELECT 1 FROM assignment o JOIN driver d ON d.id=o.driver_id WHERE o.id=h.outgoing_assignment_id AND o.status='EN_SERVICIO' AND d.user_id=NEW.actor_id)))
  THEN RAISE EXCEPTION 'Handover resolution requires the accepted incoming conductor or explicit ADMIN withdrawal' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER service_handover_actor BEFORE INSERT ON service_handover FOR EACH ROW EXECUTE FUNCTION handover_actor_guard();
CREATE TRIGGER service_handover_resolution_actor BEFORE INSERT ON service_handover_resolution FOR EACH ROW EXECUTE FUNCTION handover_actor_guard();
CREATE OR REPLACE FUNCTION assignment_service_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s scheduled_service%ROWTYPE;h service_handover%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' AND(NEW.service_id IS DISTINCT FROM OLD.service_id OR NEW.handover_id IS DISTINCT FROM OLD.handover_id OR(OLD.transferred_at IS NOT NULL AND NEW.transferred_at IS DISTINCT FROM OLD.transferred_at))
 THEN RAISE EXCEPTION 'Offer service, handover membership and transfer originals are immutable' USING ERRCODE='23514'; END IF;
 IF NEW.service_id IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO s FROM scheduled_service WHERE id=NEW.service_id AND organization_id=NEW.organization_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Offer service tenant mismatch' USING ERRCODE='23514'; END IF;
 IF NEW.handover_id IS NOT NULL THEN
  SELECT * INTO h FROM service_handover WHERE id=NEW.handover_id AND organization_id=NEW.organization_id;
  IF h.id IS NULL OR h.service_id<>NEW.service_id OR h.incoming_driver_id<>NEW.driver_id OR h.incoming_vehicle_id<>NEW.vehicle_id
   OR h.scheduled_at<>NEW.scheduled_at OR h.scheduled_end_at<>NEW.scheduled_end_at OR NEW.route_version_id<>s.route_version_id OR NEW.service_type_version_id<>s.service_type_version_id
  THEN RAISE EXCEPTION 'Standby offer must retain its prepared resource, service and captured route/type' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'PENDING_RESPONSE' OR NEW.response_mode<>'MOBILE_CONFIRMATION' OR NEW.route_version_id IS NULL OR NEW.service_type_version_id IS NULL
    OR(NEW.handover_id IS NULL AND s.status<>'PENDING_REASSIGNMENT')
    OR(NEW.handover_id IS NOT NULL AND(s.status NOT IN('IN_SERVICE','INTERRUPTED') OR s.current_offer_id<>h.outgoing_assignment_id OR NEW.created_by<>h.prepared_by))
  THEN RAISE EXCEPTION 'Service requires an explicit pending normal or prepared standby offer' USING ERRCODE='23514'; END IF;
 ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
  IF NEW.status='TRANSFERRED' THEN
   IF OLD.status<>'EN_SERVICIO' OR NOT EXISTS(SELECT 1 FROM service_handover x JOIN service_handover_resolution r ON r.handover_id=x.id WHERE x.outgoing_assignment_id=NEW.id AND r.kind='ASSUMED' AND r.occurred_at=NEW.transferred_at)
   THEN RAISE EXCEPTION 'Transfer requires the accepted incoming conductor handover resolution' USING ERRCODE='23514'; END IF;
  ELSIF s.current_offer_id IS DISTINCT FROM NEW.id THEN
   IF NEW.handover_id IS NULL OR s.current_offer_id<>h.outgoing_assignment_id OR s.status NOT IN('IN_SERVICE','INTERRUPTED')
    OR(NEW.status='EN_SERVICIO' AND NOT EXISTS(SELECT 1 FROM service_handover_resolution WHERE handover_id=h.id AND kind='ASSUMED' AND occurred_at=NEW.started_at))
    OR NEW.status NOT IN('SCHEDULED','REJECTED','EXPIRED','CANCELLED','EN_SERVICIO')
   THEN RAISE EXCEPTION 'Only current execution or its prepared standby may transition' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE OR REPLACE FUNCTION assignment_service_progress() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.service_id IS NULL THEN RETURN NEW; END IF;
 IF TG_OP='UPDATE' AND NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
 IF NEW.status='TRANSFERRED' OR(NEW.handover_id IS NOT NULL AND NEW.status<>'EN_SERVICIO' AND NOT EXISTS(SELECT 1 FROM scheduled_service WHERE id=NEW.service_id AND current_offer_id=NEW.id)) THEN
  UPDATE scheduled_service SET version=version+1,updated_at=NEW.updated_at WHERE id=NEW.service_id;RETURN NEW;
 END IF;
 UPDATE scheduled_service SET current_offer_id=NEW.id,
  scheduled_at=CASE WHEN NEW.handover_id IS NULL THEN NEW.scheduled_at ELSE scheduled_at END,
  scheduled_end_at=CASE WHEN NEW.handover_id IS NULL THEN NEW.scheduled_end_at ELSE scheduled_end_at END,
  route_version_id=CASE WHEN NEW.handover_id IS NULL THEN NEW.route_version_id ELSE route_version_id END,
  service_type_version_id=CASE WHEN NEW.handover_id IS NULL THEN NEW.service_type_version_id ELSE service_type_version_id END,
  status=CASE NEW.status WHEN 'PENDING_RESPONSE' THEN 'PENDING_RESPONSE' WHEN 'SCHEDULED' THEN 'CONFIRMED' WHEN 'EN_SERVICIO' THEN 'IN_SERVICE' WHEN 'COMPLETED' THEN 'COMPLETED' WHEN 'INTERRUPTED' THEN 'INTERRUPTED' ELSE 'PENDING_REASSIGNMENT' END,
  version=version+1,updated_at=NEW.updated_at WHERE id=NEW.service_id;
 RETURN NEW;
END; $$;

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
                (status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'INTERRUPTED', 'TRANSFERRED')
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
  ELSIF NEW.end_kind='TRANSFERRED' THEN
   IF NOT EXISTS(SELECT 1 FROM service_handover h JOIN service_handover_resolution r ON r.handover_id=h.id
      WHERE h.outgoing_assignment_id=NEW.assignment_id AND r.kind='ASSUMED' AND r.operation_key=NEW.end_event_id
       AND r.actor_id=NEW.ended_by AND r.occurred_at=NEW.ended_at AND h.organization_id=NEW.organization_id)
   THEN RAISE EXCEPTION 'Only a validated incoming conductor handover closes the outgoing segment' USING ERRCODE='23514'; END IF;
  ELSE
   IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.ended_by AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id)
      OR(NEW.end_kind='COMPLETED' AND NEW.ended_by<>NEW.started_by)
   THEN RAISE EXCEPTION 'Only the responsible conductor completes a segment' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END; $$;

