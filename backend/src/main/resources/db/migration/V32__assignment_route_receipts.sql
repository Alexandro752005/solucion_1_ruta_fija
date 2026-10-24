-- Additive only: legacy assignments keep unknown route references and no invented declarations.
ALTER TABLE assignment ADD COLUMN creation_payload_sha256 CHAR(64);
CREATE TABLE assignment_route_replacement (
 previous_assignment_id UUID PRIMARY KEY REFERENCES assignment(id),
 replacement_assignment_id UUID NOT NULL UNIQUE REFERENCES assignment(id),
 organization_id UUID NOT NULL REFERENCES organization(id), actor_id UUID NOT NULL REFERENCES app_user(id),
 operation_key VARCHAR(100) NOT NULL, payload_sha256 CHAR(64) NOT NULL,
 reason VARCHAR(160) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(organization_id,actor_id,operation_key), CHECK(previous_assignment_id<>replacement_assignment_id)
);
CREATE TABLE assignment_route_declaration (
 assignment_id UUID PRIMARY KEY REFERENCES assignment(id), organization_id UUID NOT NULL REFERENCES organization(id),
 driver_id UUID NOT NULL REFERENCES driver(id), actor_id UUID NOT NULL REFERENCES app_user(id),
 route_version_id UUID, declared_at TIMESTAMPTZ NOT NULL,
 terminal_result VARCHAR(60) NOT NULL CHECK(terminal_result IN('CORROBORATED','DISCREPANCY','NOT_CORROBORATED_NO_POINT','NOT_CORROBORATED_STALE_OR_IMPRECISE','NOT_CORROBORATED_TERMINAL_UNCONFIGURED')),
 radius_m INTEGER NOT NULL CHECK(radius_m=200), maximum_age_seconds INTEGER NOT NULL CHECK(maximum_age_seconds=120),
 maximum_accuracy_m INTEGER NOT NULL CHECK(maximum_accuracy_m=100),
 FOREIGN KEY(route_version_id,organization_id) REFERENCES route_version(id,organization_id)
);
CREATE FUNCTION assignment_route_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='assignment_route_replacement' THEN
  IF NOT EXISTS(SELECT 1 FROM assignment a JOIN assignment b ON b.id=NEW.replacement_assignment_id
   WHERE a.id=NEW.previous_assignment_id AND a.organization_id=NEW.organization_id AND b.organization_id=a.organization_id
   AND a.driver_id=b.driver_id AND a.vehicle_id=b.vehicle_id AND a.status='CANCELLED'
   AND b.status='PENDING_RESPONSE' AND b.response_mode='MOBILE_CONFIRMATION' AND b.accepted_at IS NULL
   AND a.started_at IS NULL AND b.route_version_id IS NOT NULL)
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Route replacement tenant or transition mismatch' USING ERRCODE='23514'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM assignment a JOIN driver d ON d.id=a.driver_id JOIN app_user u ON u.id=d.user_id
   WHERE a.id=NEW.assignment_id AND a.organization_id=NEW.organization_id AND d.organization_id=a.organization_id
   AND d.id=NEW.driver_id AND u.id=NEW.actor_id AND u.organization_id=a.organization_id AND u.role='CONDUCTOR'
   AND a.status='COMPLETED' AND a.started_at IS NOT NULL AND a.accepted_at IS NOT NULL
   AND a.route_version_id IS NOT DISTINCT FROM NEW.route_version_id AND NEW.declared_at>=a.started_at)
  THEN RAISE EXCEPTION 'Route declaration tenant or participation mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER route_replacement_tenant BEFORE INSERT ON assignment_route_replacement FOR EACH ROW EXECUTE FUNCTION assignment_route_receipt_guard();
CREATE TRIGGER route_declaration_tenant BEFORE INSERT ON assignment_route_declaration FOR EACH ROW EXECUTE FUNCTION assignment_route_receipt_guard();
CREATE TRIGGER protect_route_replacement BEFORE UPDATE OR DELETE ON assignment_route_replacement FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_route_declaration BEFORE UPDATE OR DELETE ON assignment_route_declaration FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE FUNCTION preserve_assignment_route_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.route_version_id IS DISTINCT FROM OLD.route_version_id THEN
  RAISE EXCEPTION 'Captured route cannot be overwritten; use explicit replacement with new acceptance' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_route_immutable BEFORE UPDATE OF route_version_id ON assignment FOR EACH ROW EXECUTE FUNCTION preserve_assignment_route_version();
