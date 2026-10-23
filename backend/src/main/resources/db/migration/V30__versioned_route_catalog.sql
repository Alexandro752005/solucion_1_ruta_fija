CREATE TABLE route_definition (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 name VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 1, current_version_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_route_definition_name ON route_definition(organization_id,lower(name));
CREATE TABLE route_version (
 id UUID PRIMARY KEY, route_id UUID NOT NULL REFERENCES route_definition(id),
 organization_id UUID NOT NULL REFERENCES organization(id), revision INTEGER NOT NULL CHECK(revision>0),
 name VARCHAR(120) NOT NULL, stops JSONB NOT NULL CHECK(jsonb_typeof(stops)='array' AND jsonb_array_length(stops)>=3),
 content_sha256 CHAR(64) NOT NULL, created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(route_id,revision), UNIQUE(id,organization_id), UNIQUE(id,route_id)
);
ALTER TABLE route_definition ADD CONSTRAINT fk_route_current_version FOREIGN KEY(current_version_id,id)
 REFERENCES route_version(id,route_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE route_operation (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 actor_id UUID NOT NULL REFERENCES app_user(id), operation_key VARCHAR(100) NOT NULL,
 action VARCHAR(20) NOT NULL CHECK(action IN('CREATE','REVISE','STATUS','ASSIGNMENT_CHANGE')),
 payload_sha256 CHAR(64) NOT NULL, route_id UUID NOT NULL REFERENCES route_definition(id),
 resulting_version BIGINT NOT NULL, reason VARCHAR(160) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 UNIQUE(organization_id,actor_id,operation_key)
);
ALTER TABLE assignment ADD COLUMN route_version_id UUID,
 ADD CONSTRAINT fk_assignment_route_tenant FOREIGN KEY(route_version_id,organization_id) REFERENCES route_version(id,organization_id);
CREATE TABLE assignment_route_history (
 id UUID PRIMARY KEY, assignment_id UUID NOT NULL REFERENCES assignment(id),
 organization_id UUID NOT NULL REFERENCES organization(id), previous_version_id UUID REFERENCES route_version(id),
 route_version_id UUID NOT NULL REFERENCES route_version(id), assignment_version BIGINT NOT NULL,
 actor_id UUID NOT NULL REFERENCES app_user(id), reason VARCHAR(160) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER protect_route_version BEFORE UPDATE OR DELETE ON route_version FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_route_operation BEFORE UPDATE OR DELETE ON route_operation FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_assignment_route_history BEFORE UPDATE OR DELETE ON assignment_route_history FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE FUNCTION route_catalog_tenant_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='route_version' THEN
  IF NOT EXISTS(SELECT 1 FROM route_definition WHERE id=NEW.route_id AND organization_id=NEW.organization_id)
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.created_by AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Route version tenant mismatch' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='route_operation' THEN
  IF NOT EXISTS(SELECT 1 FROM route_definition WHERE id=NEW.route_id AND organization_id=NEW.organization_id)
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Route operation tenant mismatch' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='assignment_route_history' THEN
  IF NOT EXISTS(SELECT 1 FROM assignment WHERE id=NEW.assignment_id AND organization_id=NEW.organization_id)
   OR NOT EXISTS(SELECT 1 FROM route_version WHERE id=NEW.route_version_id AND organization_id=NEW.organization_id)
   OR NOT EXISTS(SELECT 1 FROM app_user WHERE id=NEW.actor_id AND organization_id=NEW.organization_id AND role='ADMIN')
  THEN RAISE EXCEPTION 'Assignment route history tenant mismatch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER route_version_tenant BEFORE INSERT ON route_version FOR EACH ROW EXECUTE FUNCTION route_catalog_tenant_guard();
CREATE TRIGGER route_operation_tenant BEFORE INSERT ON route_operation FOR EACH ROW EXECUTE FUNCTION route_catalog_tenant_guard();
CREATE TRIGGER assignment_route_history_tenant BEFORE INSERT ON assignment_route_history FOR EACH ROW EXECUTE FUNCTION route_catalog_tenant_guard();
