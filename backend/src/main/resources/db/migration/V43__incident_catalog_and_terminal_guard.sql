-- L06 additive catalogue and terminal protection. Apply only after guarded test preflight.
-- No existing incident/category/status/evidence is reconstructed or rewritten.
ALTER TABLE incident DROP CONSTRAINT ck_incident_category;
ALTER TABLE incident ADD CONSTRAINT ck_incident_category CHECK (category IN (
 'AVERIA','ACCIDENTE','RETRASO','LICENCIA_VENCIDA','IMPOSIBILIDAD_SERVICIO',
 'UBICACION_NO_CORROBORADA','RIESGO_SEGURIDAD','ACCESO_PARADA','FALLO_PLATAFORMA','OTRO'));

ALTER TABLE incident
 ADD COLUMN severity VARCHAR(20) NOT NULL DEFAULT 'UNASSESSED',
 ADD COLUMN responsible_admin_id UUID REFERENCES app_user(id),
 ADD COLUMN previous_incident_id UUID,
 ADD COLUMN relation_reason VARCHAR(20),
 ADD COLUMN workflow_revision BIGINT NOT NULL DEFAULT 0 CHECK(workflow_revision>=0),
 ADD CONSTRAINT uq_incident_id_tenant UNIQUE(id,organization_id),
 ADD CONSTRAINT ck_incident_severity CHECK(severity IN('UNASSESSED','LOW','MEDIUM','HIGH','CRITICAL')),
 ADD CONSTRAINT ck_incident_previous_pair CHECK(
  (previous_incident_id IS NULL AND relation_reason IS NULL) OR
  (previous_incident_id IS NOT NULL AND previous_incident_id<>id AND relation_reason IN('RECURRENCE','CLOSURE_ERROR'))),
 ADD CONSTRAINT fk_incident_previous_tenant FOREIGN KEY(previous_incident_id,organization_id) REFERENCES incident(id,organization_id);

CREATE FUNCTION incident_workflow_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (NEW.previous_incident_id,NEW.relation_reason) IS DISTINCT FROM (OLD.previous_incident_id,OLD.relation_reason)
 THEN RAISE EXCEPTION 'The original prior-case link is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.responsible_admin_id IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM app_user u WHERE u.id=NEW.responsible_admin_id AND u.organization_id=NEW.organization_id AND u.role='ADMIN')
 THEN RAISE EXCEPTION 'Incident responsibility requires an ADMIN of the same tenant' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND NEW.previous_incident_id IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM incident p WHERE p.id=NEW.previous_incident_id AND p.organization_id=NEW.organization_id AND p.status IN('RESOLVED','CLOSED_BY_DRIVER'))
 THEN RAISE EXCEPTION 'A prior incident link requires a closed original case' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER incident_workflow_guard BEFORE INSERT OR UPDATE ON incident
 FOR EACH ROW EXECUTE FUNCTION incident_workflow_guard();

CREATE TABLE incident_action (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 incident_id UUID NOT NULL,actor_id UUID NOT NULL REFERENCES app_user(id),operation_key UUID NOT NULL,
 payload_sha256 CHAR(64) NOT NULL,kind VARCHAR(30) NOT NULL
 CHECK(kind IN('REPORT','RESPONSIBILITY','ASSESSMENT','FOLLOW_UP','INFORMATION','RESOLUTION','DRIVER_CLOSE')),
 detail JSONB NOT NULL,recorded_at TIMESTAMPTZ NOT NULL,resulting_version BIGINT NOT NULL CHECK(resulting_version>=0),
 UNIQUE(organization_id,operation_key),
 UNIQUE(incident_id,resulting_version),
 FOREIGN KEY(incident_id,organization_id) REFERENCES incident(id,organization_id)
);
CREATE INDEX ix_incident_action_history ON incident_action(incident_id,recorded_at,id);
CREATE FUNCTION incident_action_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Incident actions are append-only' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user u JOIN incident i ON i.id=NEW.incident_id AND i.organization_id=NEW.organization_id
  LEFT JOIN driver d ON d.id=i.driver_id
  WHERE u.id=NEW.actor_id AND u.organization_id=NEW.organization_id AND u.active AND
    (u.role='ADMIN' OR (u.role='CONDUCTOR' AND u.id=d.user_id AND NEW.kind IN('REPORT','INFORMATION','DRIVER_CLOSE'))))
 THEN RAISE EXCEPTION 'Incident action actor is not authorized in this tenant' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER incident_action_guard BEFORE INSERT OR UPDATE OR DELETE ON incident_action
 FOR EACH ROW EXECUTE FUNCTION incident_action_guard();

CREATE FUNCTION incident_terminal_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status IN ('RESOLVED','CLOSED_BY_DRIVER') THEN
   IF TG_OP='DELETE' THEN
     RAISE EXCEPTION 'A closed incident must retain its original history' USING ERRCODE='23514';
   END IF;
   IF NEW IS DISTINCT FROM OLD THEN
     RAISE EXCEPTION 'A closed incident cannot be reopened or rewritten' USING ERRCODE='23514';
   END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER incident_terminal_guard BEFORE UPDATE OR DELETE ON incident
 FOR EACH ROW EXECUTE FUNCTION incident_terminal_guard();
