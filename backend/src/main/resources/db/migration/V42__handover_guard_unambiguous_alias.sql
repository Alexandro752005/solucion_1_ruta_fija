-- Additive correction of PL/pgSQL row-variable / table-alias ambiguity in V41. All guards retained.
CREATE OR REPLACE FUNCTION handover_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
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
   OR(NEW.kind='ASSUMED' AND NOT EXISTS(SELECT 1 FROM scheduled_service sh JOIN assignment participant ON participant.id=sh.current_offer_id WHERE sh.id=h.service_id AND sh.organization_id=h.organization_id AND participant.id=h.outgoing_assignment_id AND sh.status IN('IN_SERVICE','INTERRUPTED') AND participant.status IN('EN_SERVICIO','INTERRUPTED')))
   OR(NEW.kind='WITHDRAWN' AND(a.status NOT IN('PENDING_RESPONSE','SCHEDULED') OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=h.organization_id AND role='ADMIN')))
   OR(NEW.kind='SERVICE_ENDED' AND(a.status NOT IN('PENDING_RESPONSE','SCHEDULED') OR NOT EXISTS(SELECT 1 FROM assignment o JOIN driver d ON d.id=o.driver_id WHERE o.id=h.outgoing_assignment_id AND o.status='EN_SERVICIO' AND d.user_id=NEW.actor_id)))
  THEN RAISE EXCEPTION 'Handover resolution requires the accepted incoming conductor or explicit ADMIN withdrawal' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
