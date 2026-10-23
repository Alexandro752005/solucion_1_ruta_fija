-- Q-025/026/028: group composition is independent from driver/unit periods.
ALTER TABLE transport_group ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE group_driver_membership (
 group_id UUID NOT NULL REFERENCES transport_group(id),
 driver_id UUID NOT NULL REFERENCES driver(id),
 organization_id UUID NOT NULL REFERENCES organization(id),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 source VARCHAR(30) NOT NULL CHECK(source IN('LEGACY_PRIMARY','PRIMARY_COMPATIBILITY','ADMIN')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(group_id,driver_id)
);
CREATE INDEX ix_group_driver_membership_driver ON group_driver_membership(driver_id,active);
CREATE TABLE group_vehicle_membership (
 group_id UUID NOT NULL REFERENCES transport_group(id),
 vehicle_id UUID NOT NULL REFERENCES vehicle(id),
 organization_id UUID NOT NULL REFERENCES organization(id),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(group_id,vehicle_id)
);
CREATE INDEX ix_group_vehicle_membership_vehicle ON group_vehicle_membership(vehicle_id,active);
CREATE TABLE group_membership_operation (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES transport_group(id),
 organization_id UUID NOT NULL REFERENCES organization(id), actor_id UUID NOT NULL REFERENCES app_user(id),
 operation_key VARCHAR(100) NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 resource_kind VARCHAR(10) NOT NULL CHECK(resource_kind IN('DRIVER','VEHICLE')),
 resource_id UUID NOT NULL, active BOOLEAN NOT NULL, reason VARCHAR(160) NOT NULL,
 resulting_version BIGINT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(group_id,actor_id,operation_key)
);
CREATE TRIGGER protect_group_membership_operation BEFORE UPDATE OR DELETE ON group_membership_operation
 FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();

CREATE FUNCTION group_membership_tenant_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM transport_group WHERE id=NEW.group_id AND organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Group tenant mismatch' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='group_driver_membership' THEN
  IF NOT EXISTS(SELECT 1 FROM driver WHERE id=NEW.driver_id AND organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Driver membership tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='group_vehicle_membership' THEN
  IF NOT EXISTS(SELECT 1 FROM vehicle WHERE id=NEW.vehicle_id AND organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Vehicle membership tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='group_membership_operation' THEN
  IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Membership actor tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER group_driver_tenant BEFORE INSERT OR UPDATE ON group_driver_membership
 FOR EACH ROW EXECUTE FUNCTION group_membership_tenant_guard();
CREATE TRIGGER group_vehicle_tenant BEFORE INSERT OR UPDATE ON group_vehicle_membership
 FOR EACH ROW EXECUTE FUNCTION group_membership_tenant_guard();
CREATE TRIGGER group_operation_tenant BEFORE INSERT ON group_membership_operation
 FOR EACH ROW EXECUTE FUNCTION group_membership_tenant_guard();

-- This records an observed legacy primary association, not its unknown original date.
INSERT INTO group_driver_membership(group_id,driver_id,organization_id,source)
 SELECT group_id,id,organization_id,'LEGACY_PRIMARY' FROM driver;
CREATE FUNCTION preserve_primary_group_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN
  IF OLD.group_id=NEW.group_id THEN RETURN NEW; END IF;
 END IF;
 INSERT INTO group_driver_membership(group_id,driver_id,organization_id,source)
 VALUES(NEW.group_id,NEW.id,NEW.organization_id,'PRIMARY_COMPATIBILITY')
 ON CONFLICT(group_id,driver_id) DO UPDATE SET active=true,updated_at=now();
 RETURN NEW;
END; $$;
CREATE TRIGGER driver_primary_membership AFTER INSERT OR UPDATE OF group_id ON driver
 FOR EACH ROW EXECUTE FUNCTION preserve_primary_group_membership();

CREATE TABLE assignment_group_snapshot (
 assignment_id UUID NOT NULL REFERENCES assignment(id), group_id UUID NOT NULL REFERENCES transport_group(id),
 organization_id UUID NOT NULL REFERENCES organization(id),
 source VARCHAR(30) NOT NULL CHECK(source IN('CREATION_SNAPSHOT','LEGACY_CURRENT_REFERENCE')),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(assignment_id,group_id)
);
-- Existing assignments do not claim a reconstructed historical group.
INSERT INTO assignment_group_snapshot(assignment_id,group_id,organization_id,source)
 SELECT a.id,d.group_id,a.organization_id,'LEGACY_CURRENT_REFERENCE'
 FROM assignment a JOIN driver d ON d.id=a.driver_id AND d.organization_id=a.organization_id;

CREATE FUNCTION capture_assignment_groups() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g RECORD; has_driver_group BOOLEAN := FALSE;
BEGIN
 FOR g IN SELECT t.id,EXISTS(SELECT 1 FROM group_driver_membership m WHERE m.group_id=t.id AND m.driver_id=NEW.driver_id AND m.active) AS for_driver
 FROM transport_group t WHERE t.organization_id=NEW.organization_id AND t.active
 AND (EXISTS(SELECT 1 FROM group_driver_membership m WHERE m.group_id=t.id AND m.driver_id=NEW.driver_id AND m.active)
   OR EXISTS(SELECT 1 FROM group_vehicle_membership m WHERE m.group_id=t.id AND m.vehicle_id=NEW.vehicle_id AND m.active))
 ORDER BY t.id FOR SHARE OF t
 LOOP
  IF g.for_driver THEN has_driver_group := TRUE; END IF;
  INSERT INTO assignment_group_snapshot(assignment_id,group_id,organization_id,source)
  VALUES(NEW.id,g.id,NEW.organization_id,'CREATION_SNAPSHOT') ON CONFLICT DO NOTHING;
 END LOOP;
 IF NOT has_driver_group THEN RAISE EXCEPTION 'Assignment requires active driver group membership' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_capture_groups AFTER INSERT ON assignment FOR EACH ROW EXECUTE FUNCTION capture_assignment_groups();
