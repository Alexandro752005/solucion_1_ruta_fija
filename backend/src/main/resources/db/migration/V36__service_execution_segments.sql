-- L-05: actual execution is append-only by segment. No historical backfill.
CREATE TABLE service_execution_segment (
 id UUID PRIMARY KEY,
 service_id UUID NOT NULL, organization_id UUID NOT NULL,
 assignment_id UUID NOT NULL UNIQUE,
 driver_id UUID NOT NULL REFERENCES driver(id), vehicle_id UUID NOT NULL REFERENCES vehicle(id),
 ordinal INTEGER NOT NULL CHECK(ordinal>0),
 started_at TIMESTAMPTZ NOT NULL, start_recorded_at TIMESTAMPTZ NOT NULL,
 started_by UUID NOT NULL REFERENCES app_user(id), start_event_id UUID NOT NULL UNIQUE,
 ended_at TIMESTAMPTZ, end_recorded_at TIMESTAMPTZ,
 ended_by UUID REFERENCES app_user(id), end_event_id UUID UNIQUE,
 end_kind VARCHAR(24) CHECK(end_kind IN ('COMPLETED','INTERRUPTED','TRANSFERRED')),
 UNIQUE(service_id,ordinal), UNIQUE(id,organization_id,driver_id),
 FOREIGN KEY(assignment_id,service_id,organization_id) REFERENCES assignment(id,service_id,organization_id),
 CHECK(started_at=start_recorded_at),
 CHECK((ended_at IS NULL AND end_recorded_at IS NULL AND ended_by IS NULL AND end_event_id IS NULL AND end_kind IS NULL)
    OR (ended_at IS NOT NULL AND end_recorded_at IS NOT NULL AND ended_by IS NOT NULL AND end_event_id IS NOT NULL
        AND end_kind IS NOT NULL AND ended_at>=started_at AND ended_at=end_recorded_at))
);
CREATE UNIQUE INDEX uq_service_open_segment ON service_execution_segment(service_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX uq_driver_open_segment ON service_execution_segment(driver_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX uq_vehicle_open_segment ON service_execution_segment(vehicle_id) WHERE ended_at IS NULL;
CREATE INDEX ix_execution_tenant_service ON service_execution_segment(organization_id,service_id,ordinal);

CREATE FUNCTION execution_segment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a assignment%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Execution originals cannot be deleted' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' THEN
  IF OLD.ended_at IS NOT NULL OR
   (to_jsonb(NEW)-ARRAY['ended_at','end_recorded_at','ended_by','end_event_id','end_kind']) IS DISTINCT FROM
   (to_jsonb(OLD)-ARRAY['ended_at','end_recorded_at','ended_by','end_event_id','end_kind'])
  THEN RAISE EXCEPTION 'Execution originals are immutable' USING ERRCODE='23514'; END IF;
  IF NEW.ended_at IS NULL THEN RAISE EXCEPTION 'Only closing an open segment is allowed' USING ERRCODE='23514'; END IF;
 END IF;
 SELECT * INTO a FROM assignment WHERE id=NEW.assignment_id;
 IF a.driver_id<>NEW.driver_id OR a.vehicle_id<>NEW.vehicle_id OR a.organization_id<>NEW.organization_id
  OR a.service_id IS DISTINCT FROM NEW.service_id OR a.accepted_at IS NULL OR a.started_at IS NULL
  OR a.started_at<>NEW.started_at OR a.response_mode<>'MOBILE_CONFIRMATION'
 THEN RAISE EXCEPTION 'Segment must describe the accepted executing offer' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND a.status<>'EN_SERVICIO'
 THEN RAISE EXCEPTION 'Segment requires a started service' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user u JOIN driver d ON d.user_id=u.id
    WHERE u.id=NEW.started_by AND d.id=NEW.driver_id AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Only the assigned conductor starts a segment' USING ERRCODE='23514'; END IF;
 IF NEW.ended_by IS NOT NULL AND NEW.end_kind='COMPLETED' AND NEW.ended_by<>NEW.started_by
 THEN RAISE EXCEPTION 'Only the responsible conductor completes a segment' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER execution_segment_originals BEFORE INSERT OR UPDATE OR DELETE ON service_execution_segment
 FOR EACH ROW EXECUTE FUNCTION execution_segment_guard();

-- A stale snapshot from availability or a different service cannot corroborate a new segment.
ALTER TABLE driver_current_location ADD COLUMN execution_segment_id UUID,
 ADD CONSTRAINT fk_current_location_execution FOREIGN KEY(execution_segment_id,organization_id,driver_id)
 REFERENCES service_execution_segment(id,organization_id,driver_id);
