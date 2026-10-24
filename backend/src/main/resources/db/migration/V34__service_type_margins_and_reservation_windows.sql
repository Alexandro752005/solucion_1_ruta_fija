-- Explicit ADMIN configuration only. No default service type or approved margin.
CREATE TABLE service_type (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 name VARCHAR(100) NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
 version BIGINT NOT NULL DEFAULT 1, current_version_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,organization_id)
);
CREATE UNIQUE INDEX uq_service_type_name ON service_type(organization_id,lower(name));
CREATE TABLE service_type_version (
 id UUID PRIMARY KEY, type_id UUID NOT NULL, organization_id UUID NOT NULL,
 revision INTEGER NOT NULL CHECK(revision>0), name VARCHAR(100) NOT NULL,
 before_minutes INTEGER NOT NULL CHECK(before_minutes>=0),
 after_minutes INTEGER NOT NULL CHECK(after_minutes>=0),
 content_sha256 CHAR(64) NOT NULL, created_by UUID NOT NULL REFERENCES app_user(id),
 created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(type_id,revision), UNIQUE(id,organization_id), UNIQUE(id,type_id),
 FOREIGN KEY(type_id,organization_id) REFERENCES service_type(id,organization_id)
);
ALTER TABLE service_type ADD CONSTRAINT fk_service_type_current
 FOREIGN KEY(current_version_id,id) REFERENCES service_type_version(id,type_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE service_type_operation (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 actor_id UUID NOT NULL REFERENCES app_user(id), operation_key VARCHAR(100) NOT NULL,
 action VARCHAR(20) NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 type_id UUID NOT NULL, resulting_version BIGINT NOT NULL,
 reason VARCHAR(160) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(organization_id,actor_id,operation_key),
 FOREIGN KEY(type_id,organization_id) REFERENCES service_type(id,organization_id)
);
CREATE FUNCTION service_type_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Service type revision/receipt is immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER service_type_version_immutable BEFORE UPDATE OR DELETE ON service_type_version
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER service_type_operation_immutable BEFORE UPDATE OR DELETE ON service_type_operation
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE FUNCTION service_type_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor UUID;
BEGIN
 IF TG_TABLE_NAME='service_type_version' THEN actor:=NEW.created_by; ELSE actor:=NEW.actor_id; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=actor AND organization_id=NEW.organization_id AND role='ADMIN')
 THEN RAISE EXCEPTION 'Service type actor tenant mismatch' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER service_type_version_actor BEFORE INSERT ON service_type_version
 FOR EACH ROW EXECUTE FUNCTION service_type_actor_guard();
CREATE TRIGGER service_type_operation_actor BEFORE INSERT ON service_type_operation
 FOR EACH ROW EXECUTE FUNCTION service_type_actor_guard();

ALTER TABLE assignment
 ADD COLUMN service_type_version_id UUID REFERENCES service_type_version(id),
 ADD COLUMN margin_before_minutes INTEGER,
 ADD COLUMN margin_after_minutes INTEGER,
 ADD COLUMN reservation_starts_at TIMESTAMPTZ,
 ADD COLUMN reservation_ends_at TIMESTAMPTZ;
-- Known calendar bounds only. NULL type/margins retain unknown historical configuration.
UPDATE assignment SET reservation_starts_at=scheduled_at,reservation_ends_at=scheduled_end_at;
ALTER TABLE assignment
 ALTER COLUMN reservation_starts_at SET NOT NULL,
 ALTER COLUMN reservation_ends_at SET NOT NULL,
 ADD CONSTRAINT ck_assignment_margin_capture CHECK(
  (service_type_version_id IS NULL AND margin_before_minutes IS NULL AND margin_after_minutes IS NULL)
  OR (service_type_version_id IS NOT NULL AND margin_before_minutes IS NOT NULL AND margin_after_minutes IS NOT NULL
      AND margin_before_minutes>=0 AND margin_after_minutes>=0)),
 ADD CONSTRAINT ck_assignment_reservation_window CHECK(reservation_ends_at>reservation_starts_at);
CREATE FUNCTION assignment_margin_capture_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v service_type_version%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' AND OLD.service_type_version_id IS NULL AND NEW.service_type_version_id IS NOT NULL
 THEN RAISE EXCEPTION 'Historical margins cannot be certified retrospectively' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND OLD.service_type_version_id IS NOT NULL AND (
    NEW.service_type_version_id IS DISTINCT FROM OLD.service_type_version_id
    OR NEW.margin_before_minutes IS DISTINCT FROM OLD.margin_before_minutes
    OR NEW.margin_after_minutes IS DISTINCT FROM OLD.margin_after_minutes
    OR NEW.driver_id IS DISTINCT FROM OLD.driver_id OR NEW.vehicle_id IS DISTINCT FROM OLD.vehicle_id
    OR NEW.scheduled_at IS DISTINCT FROM OLD.scheduled_at OR NEW.scheduled_end_at IS DISTINCT FROM OLD.scheduled_end_at)
 THEN RAISE EXCEPTION 'Captured service conditions require a new offer' USING ERRCODE='23514'; END IF;
 IF NEW.service_type_version_id IS NOT NULL THEN
  SELECT * INTO v FROM service_type_version WHERE id=NEW.service_type_version_id AND organization_id=NEW.organization_id;
  IF NOT FOUND OR NEW.margin_before_minutes IS DISTINCT FROM v.before_minutes OR NEW.margin_after_minutes IS DISTINCT FROM v.after_minutes
  THEN RAISE EXCEPTION 'Service type margin or tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 NEW.reservation_starts_at:=NEW.scheduled_at-make_interval(mins=>coalesce(NEW.margin_before_minutes,0));
 NEW.reservation_ends_at:=NEW.scheduled_end_at+make_interval(mins=>coalesce(NEW.margin_after_minutes,0));
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_margin_capture BEFORE INSERT OR UPDATE ON assignment
 FOR EACH ROW EXECUTE FUNCTION assignment_margin_capture_guard();
ALTER TABLE assignment DROP CONSTRAINT ex_assignment_driver_schedule_no_overlap,
 DROP CONSTRAINT ex_assignment_vehicle_schedule_no_overlap;
ALTER TABLE assignment
 ADD CONSTRAINT ex_assignment_driver_schedule_no_overlap EXCLUDE USING gist
 (driver_id WITH =,tstzrange(reservation_starts_at,reservation_ends_at,'[)') WITH &&)
 WHERE(status IN ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')),
 ADD CONSTRAINT ex_assignment_vehicle_schedule_no_overlap EXCLUDE USING gist
 (vehicle_id WITH =,tstzrange(reservation_starts_at,reservation_ends_at,'[)') WITH &&)
 WHERE(status IN ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO'));
