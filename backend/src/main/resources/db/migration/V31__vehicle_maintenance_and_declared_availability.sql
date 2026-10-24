-- Approved MNT-01A/MNT-02A. Open maintenance has no promised operational end.
CREATE TABLE vehicle_maintenance_control (
 vehicle_id UUID PRIMARY KEY REFERENCES vehicle(id), organization_id UUID NOT NULL REFERENCES organization(id),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0)
);
CREATE TABLE vehicle_maintenance (
 id UUID PRIMARY KEY, vehicle_id UUID NOT NULL REFERENCES vehicle(id), organization_id UUID NOT NULL REFERENCES organization(id),
 kind VARCHAR(20) NOT NULL CHECK(kind IN('PREVENTIVE','URGENT')),
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','WORK_COMPLETED','RETURNED','CANCELLED')),
 starts_at TIMESTAMPTZ NOT NULL, planned_ends_at TIMESTAMPTZ, blocking_ends_at TIMESTAMPTZ,
 effective_window TSTZRANGE GENERATED ALWAYS AS(tstzrange(starts_at,blocking_ends_at,'[)')) STORED,
 reason VARCHAR(160) NOT NULL, created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL,
 work_completed_by UUID REFERENCES app_user(id), work_completed_at TIMESTAMPTZ,
 responsible_reference VARCHAR(160), evidence_reference VARCHAR(160),
 returned_by UUID REFERENCES app_user(id), returned_at TIMESTAMPTZ, cancelled_by UUID REFERENCES app_user(id), cancelled_at TIMESTAMPTZ,
 CHECK(planned_ends_at IS NULL OR planned_ends_at>starts_at),
 CHECK(kind<>'PREVENTIVE' OR planned_ends_at IS NOT NULL),
 CHECK(blocking_ends_at IS NULL OR blocking_ends_at>starts_at),
 CHECK((status='OPEN' AND work_completed_at IS NULL AND returned_at IS NULL AND cancelled_at IS NULL AND blocking_ends_at IS NULL)
  OR(status='WORK_COMPLETED' AND work_completed_at IS NOT NULL AND work_completed_by IS NOT NULL AND responsible_reference IS NOT NULL AND evidence_reference IS NOT NULL AND returned_at IS NULL AND blocking_ends_at IS NULL)
  OR(status='RETURNED' AND work_completed_at IS NOT NULL AND work_completed_by IS NOT NULL AND responsible_reference IS NOT NULL AND evidence_reference IS NOT NULL AND returned_at IS NOT NULL AND returned_by IS NOT NULL AND blocking_ends_at IS NOT NULL)
  OR(status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND returned_at IS NULL)),
 CHECK((status='CANCELLED')=(cancelled_at IS NOT NULL)),
 CHECK((status='RETURNED')=(returned_at IS NOT NULL)),
 CHECK(returned_at IS NULL OR(returned_at>=work_completed_at AND blocking_ends_at>returned_at))
);
CREATE INDEX ix_maintenance_vehicle_window ON vehicle_maintenance USING gist(vehicle_id,effective_window) WHERE status<>'CANCELLED';
CREATE TABLE vehicle_maintenance_operation (
 id UUID PRIMARY KEY, vehicle_id UUID NOT NULL REFERENCES vehicle(id), organization_id UUID NOT NULL REFERENCES organization(id),
 actor_id UUID NOT NULL REFERENCES app_user(id), operation_key VARCHAR(100) NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 action VARCHAR(30) NOT NULL CHECK(action IN('CREATE_PREVENTIVE','CREATE_URGENT','WORK_COMPLETED','RETURN_AUTHORIZED','CANCELLED')),
 record_id UUID REFERENCES vehicle_maintenance(id), reason VARCHAR(160) NOT NULL, resulting_version BIGINT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(vehicle_id,actor_id,operation_key)
);
CREATE TRIGGER protect_maintenance_operation BEFORE UPDATE OR DELETE ON vehicle_maintenance_operation FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE FUNCTION maintenance_tenant_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM vehicle WHERE id=NEW.vehicle_id AND organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Maintenance vehicle tenant mismatch' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='vehicle_maintenance' THEN
  IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.created_by AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Maintenance actor tenant mismatch' USING ERRCODE='23514'; END IF;
  IF NEW.work_completed_by IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.work_completed_by AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Work completion actor mismatch' USING ERRCODE='23514'; END IF;
  IF NEW.returned_by IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.returned_by AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Return actor mismatch' USING ERRCODE='23514'; END IF;
  IF NEW.cancelled_by IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.cancelled_by AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Cancellation actor mismatch' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='vehicle_maintenance_operation' THEN
  IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Maintenance operation actor mismatch' USING ERRCODE='23514'; END IF;
  IF NEW.record_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM vehicle_maintenance WHERE id=NEW.record_id AND vehicle_id=NEW.vehicle_id AND organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Maintenance operation record mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER maintenance_control_tenant BEFORE INSERT OR UPDATE ON vehicle_maintenance_control FOR EACH ROW EXECUTE FUNCTION maintenance_tenant_guard();
CREATE TRIGGER maintenance_record_tenant BEFORE INSERT OR UPDATE ON vehicle_maintenance FOR EACH ROW EXECUTE FUNCTION maintenance_tenant_guard();
CREATE TRIGGER maintenance_operation_tenant BEFORE INSERT ON vehicle_maintenance_operation FOR EACH ROW EXECUTE FUNCTION maintenance_tenant_guard();

-- A legacy computed/occupied state is not reconstructed as a driver's declaration.
ALTER TABLE driver ADD COLUMN declared_availability_status VARCHAR(25), ADD COLUMN declared_availability_at TIMESTAMPTZ,
 ADD CONSTRAINT ck_driver_declared_availability CHECK(
   (declared_availability_status IS NULL AND declared_availability_at IS NULL)
   OR(declared_availability_status IS NOT NULL AND declared_availability_status IN('DISPONIBLE','DESCANSO','NO_DISPONIBLE') AND declared_availability_at IS NOT NULL));

-- Q-030: a physical unit is not recreated in another organization. Never merge collisions automatically.
ALTER TABLE vehicle ADD CONSTRAINT ck_vehicle_canonical_plate CHECK(length(regexp_replace(upper(plate),'[^A-Z0-9]','','g'))>0);
CREATE UNIQUE INDEX uq_vehicle_canonical_plate ON vehicle((regexp_replace(upper(plate),'[^A-Z0-9]','','g')));

-- Close the primary-reference/retirement race without changing or deleting legacy memberships.
CREATE OR REPLACE FUNCTION preserve_primary_group_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN IF OLD.group_id=NEW.group_id THEN RETURN NEW; END IF; END IF;
 PERFORM id FROM transport_group WHERE id=NEW.group_id AND organization_id=NEW.organization_id AND active FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'New primary membership requires active tenant group' USING ERRCODE='23514'; END IF;
 INSERT INTO group_driver_membership(group_id,driver_id,organization_id,source)
 VALUES(NEW.group_id,NEW.id,NEW.organization_id,'PRIMARY_COMPATIBILITY')
 ON CONFLICT(group_id,driver_id) DO UPDATE SET active=true,updated_at=now();
 RETURN NEW;
END; $$;
