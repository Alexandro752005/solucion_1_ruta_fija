-- Additive L-02. Legacy active links are not documentary/temporal authorization.
-- Existing services have no proven historical plate. Leave those snapshots
-- NULL rather than inventing their past from today's mutable vehicle record.
ALTER TABLE assignment ADD COLUMN vehicle_plate_snapshot VARCHAR(15);
CREATE TABLE vehicle_plate_revision (
 id UUID PRIMARY KEY, vehicle_id UUID NOT NULL REFERENCES vehicle(id), organization_id UUID NOT NULL REFERENCES organization(id),
 previous_plate VARCHAR(15) NOT NULL, corrected_plate VARCHAR(15) NOT NULL,
 reviewed_by UUID NOT NULL REFERENCES app_user(id), review_reference VARCHAR(500) NOT NULL,
 reason VARCHAR(500) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER protect_vehicle_plate_revision BEFORE UPDATE OR DELETE ON vehicle_plate_revision FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TABLE driver_qualification_control (
 driver_id UUID PRIMARY KEY REFERENCES driver(id), organization_id UUID NOT NULL REFERENCES organization(id),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0), suspended BOOLEAN NOT NULL DEFAULT false,
 suspension_reference VARCHAR(160), updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(driver_id,organization_id)
);
CREATE TABLE qualification_review (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), driver_id UUID NOT NULL REFERENCES driver(id),
 vehicle_id UUID REFERENCES vehicle(id), scope VARCHAR(12) NOT NULL CHECK(scope IN('VEHICLE','COMPANY')),
 catalog_id VARCHAR(32) NOT NULL DEFAULT 'L02-CHICLAYO', catalog_version VARCHAR(20) NOT NULL DEFAULT '0.2.0',
 catalog_hash CHAR(64) NOT NULL DEFAULT 'e53a74f0cd2526d229bb5c49508fe3a274ff070705701c771f6528c773ea8a42',
 requirement_id VARCHAR(12) NOT NULL, fields JSONB NOT NULL, expiry_mode VARCHAR(12) NOT NULL CHECK(expiry_mode IN('DATED','NO_EXPIRY','UNKNOWN')),
 valid_until TIMESTAMPTZ, applicability VARCHAR(20) NOT NULL CHECK(applicability IN('CONFIRMED','POR_CONTRASTAR')),
 applicability_condition VARCHAR(500), source_reference VARCHAR(500), result VARCHAR(30) NOT NULL
 CHECK(result IN('CONFORME','OBSERVADO','NO_APLICA_JUSTIFICADO','POR_CONTRASTAR')),
 review_reference VARCHAR(500), version BIGINT NOT NULL, reviewer_id UUID NOT NULL REFERENCES app_user(id), recorded_at TIMESTAMPTZ NOT NULL,
 CHECK((scope='VEHICLE' AND vehicle_id IS NOT NULL) OR (scope='COMPANY' AND vehicle_id IS NULL)),
 CHECK((expiry_mode='DATED' AND valid_until IS NOT NULL) OR (expiry_mode<>'DATED' AND valid_until IS NULL))
);
CREATE INDEX qualification_review_latest ON qualification_review(driver_id,scope,requirement_id,vehicle_id,version DESC);
CREATE TABLE driver_unit_period (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), driver_id UUID NOT NULL REFERENCES driver(id),
 vehicle_id UUID NOT NULL REFERENCES vehicle(id), kind VARCHAR(12) NOT NULL CHECK(kind IN('OWN','COMPANY')),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ, status VARCHAR(12) NOT NULL CHECK(status IN('AUTHORIZED','REVOKED')),
 reason VARCHAR(500) NOT NULL, version BIGINT NOT NULL, created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ, revoked_by UUID REFERENCES app_user(id),
 CHECK(ends_at IS NULL OR ends_at>starts_at), CHECK(kind<>'COMPANY' OR ends_at IS NOT NULL),
 EXCLUDE USING gist (driver_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status='AUTHORIZED'),
 EXCLUDE USING gist (vehicle_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status='AUTHORIZED')
);
CREATE TABLE qualification_operation (
 id UUID PRIMARY KEY, driver_id UUID NOT NULL REFERENCES driver(id), organization_id UUID NOT NULL REFERENCES organization(id),
 actor_id UUID NOT NULL REFERENCES app_user(id), operation_key VARCHAR(100) NOT NULL, payload_hash CHAR(64) NOT NULL,
 action VARCHAR(60) NOT NULL, reason VARCHAR(500) NOT NULL, resulting_version BIGINT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(driver_id,actor_id,operation_key)
);
CREATE TABLE driver_unit_request (
 id UUID PRIMARY KEY, driver_id UUID NOT NULL REFERENCES driver(id), organization_id UUID NOT NULL REFERENCES organization(id),
 vehicle_id UUID REFERENCES vehicle(id), kind VARCHAR(12) NOT NULL CHECK(kind IN('OWN','COMPANY')),
 status VARCHAR(20) NOT NULL CHECK(status IN('PENDING_REVIEW','APPROVED','REJECTED')),
 reference VARCHAR(160) NOT NULL, created_at TIMESTAMPTZ NOT NULL, decided_at TIMESTAMPTZ, decided_by UUID REFERENCES app_user(id)
);
CREATE UNIQUE INDEX one_pending_unit_request ON driver_unit_request(driver_id) WHERE(status='PENDING_REVIEW');
-- Retain the planned service even when the old offer is cancelled. In-service
-- attention is distinct: it never pretends that the service was completed.
CREATE TABLE qualification_service_attention (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id), driver_id UUID NOT NULL REFERENCES driver(id),
 assignment_id UUID NOT NULL REFERENCES assignment(id), kind VARCHAR(30) NOT NULL CHECK(kind IN('PENDING_REASSIGNMENT','SAFE_HANDOVER_REQUIRED')),
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','RESOLVED')),
 reason VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL, resolved_at TIMESTAMPTZ,
 replacement_assignment_id UUID REFERENCES assignment(id), UNIQUE(assignment_id,kind)
);
CREATE FUNCTION qualification_tenant_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM driver WHERE id=NEW.driver_id AND organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Qualification tenant mismatch' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME IN('qualification_review','driver_unit_period','driver_unit_request') THEN
 IF NEW.vehicle_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM vehicle WHERE id=NEW.vehicle_id AND organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Qualification vehicle tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='qualification_service_attention' THEN
 IF NOT EXISTS(SELECT 1 FROM assignment WHERE id=NEW.assignment_id AND driver_id=NEW.driver_id AND organization_id=NEW.organization_id)
 THEN RAISE EXCEPTION 'Qualification assignment tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER qualification_control_tenant BEFORE INSERT OR UPDATE ON driver_qualification_control FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER qualification_review_tenant BEFORE INSERT ON qualification_review FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER qualification_period_tenant BEFORE INSERT OR UPDATE ON driver_unit_period FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER qualification_request_tenant BEFORE INSERT OR UPDATE ON driver_unit_request FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER qualification_operation_tenant BEFORE INSERT ON qualification_operation FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER qualification_attention_tenant BEFORE INSERT OR UPDATE ON qualification_service_attention FOR EACH ROW EXECUTE FUNCTION qualification_tenant_guard();
CREATE TRIGGER protect_qualification_review BEFORE UPDATE OR DELETE ON qualification_review FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_qualification_operation BEFORE UPDATE OR DELETE ON qualification_operation FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
