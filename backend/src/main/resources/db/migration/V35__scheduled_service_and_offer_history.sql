-- A service survives its offers. Historical rows are deliberately not certified/backfilled.
CREATE TABLE scheduled_service (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 created_by UUID NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REASSIGNMENT'
  CHECK(status IN ('PENDING_REASSIGNMENT','PENDING_RESPONSE','CONFIRMED','IN_SERVICE','COMPLETED','CANCELLED')),
 scheduled_at TIMESTAMPTZ NOT NULL, scheduled_end_at TIMESTAMPTZ NOT NULL,
 route_version_id UUID NOT NULL,
 service_type_version_id UUID NOT NULL,
 current_offer_id UUID, cancelled_at TIMESTAMPTZ, cancellation_reason VARCHAR(160),
 UNIQUE(id,organization_id), CHECK(scheduled_end_at>scheduled_at),
 FOREIGN KEY(route_version_id,organization_id) REFERENCES route_version(id,organization_id),
 FOREIGN KEY(service_type_version_id,organization_id) REFERENCES service_type_version(id,organization_id),
 CHECK((status='CANCELLED')=(cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL))
);
ALTER TABLE assignment ADD COLUMN service_id UUID,
 ADD CONSTRAINT uq_assignment_service_tenant UNIQUE(id,service_id,organization_id),
 ADD CONSTRAINT fk_assignment_service_tenant FOREIGN KEY(service_id,organization_id) REFERENCES scheduled_service(id,organization_id);
ALTER TABLE scheduled_service ADD CONSTRAINT fk_service_current_offer
 FOREIGN KEY(current_offer_id,id,organization_id) REFERENCES assignment(id,service_id,organization_id) DEFERRABLE INITIALLY DEFERRED;
CREATE UNIQUE INDEX uq_service_live_offer ON assignment(service_id)
 WHERE service_id IS NOT NULL AND status IN ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO');
CREATE INDEX ix_service_organization ON scheduled_service(organization_id,created_at,id);
CREATE INDEX ix_offer_service_history ON assignment(service_id,created_at,id);
CREATE TABLE scheduled_service_operation (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 actor_id UUID NOT NULL REFERENCES app_user(id), service_id UUID NOT NULL,
 operation_key VARCHAR(100) NOT NULL, action VARCHAR(20) NOT NULL CHECK(action IN ('REOFFER','CANCEL_SERVICE')),
 payload_sha256 CHAR(64) NOT NULL, previous_offer_id UUID REFERENCES assignment(id),
 resulting_offer_id UUID REFERENCES assignment(id), reason VARCHAR(160) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL, resulting_version BIGINT NOT NULL,
 FOREIGN KEY(service_id,organization_id) REFERENCES scheduled_service(id,organization_id),
 UNIQUE(organization_id,actor_id,operation_key)
);
CREATE TRIGGER scheduled_service_operation_immutable BEFORE UPDATE OR DELETE ON scheduled_service_operation
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE FUNCTION scheduled_service_actor_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor UUID;
BEGIN
 IF TG_TABLE_NAME='scheduled_service' THEN actor:=NEW.created_by; ELSE actor:=NEW.actor_id; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user WHERE id=actor AND organization_id=NEW.organization_id AND role='ADMIN')
 THEN RAISE EXCEPTION 'Service actor tenant mismatch' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER scheduled_service_actor BEFORE INSERT ON scheduled_service FOR EACH ROW EXECUTE FUNCTION scheduled_service_actor_guard();
CREATE TRIGGER scheduled_service_operation_actor BEFORE INSERT ON scheduled_service_operation FOR EACH ROW EXECUTE FUNCTION scheduled_service_actor_guard();
CREATE FUNCTION assignment_service_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s scheduled_service%ROWTYPE;
BEGIN
 IF TG_OP='UPDATE' AND NEW.service_id IS DISTINCT FROM OLD.service_id
 THEN RAISE EXCEPTION 'Offer service membership is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.service_id IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO s FROM scheduled_service WHERE id=NEW.service_id AND organization_id=NEW.organization_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Offer service tenant mismatch' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF s.status<>'PENDING_REASSIGNMENT' OR NEW.status<>'PENDING_RESPONSE'
     OR NEW.response_mode<>'MOBILE_CONFIRMATION' OR NEW.route_version_id IS NULL OR NEW.service_type_version_id IS NULL
  THEN RAISE EXCEPTION 'Service requires an explicit pending offer' USING ERRCODE='23514'; END IF;
 ELSIF NEW.status IS DISTINCT FROM OLD.status AND s.current_offer_id IS DISTINCT FROM NEW.id THEN
  RAISE EXCEPTION 'Only the current offer can transition' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_service_guard BEFORE INSERT OR UPDATE ON assignment
 FOR EACH ROW EXECUTE FUNCTION assignment_service_guard();
CREATE FUNCTION assignment_service_progress() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.service_id IS NULL THEN RETURN NEW; END IF;
 IF TG_OP='UPDATE' AND NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
 UPDATE scheduled_service SET current_offer_id=NEW.id,
  scheduled_at=NEW.scheduled_at,scheduled_end_at=NEW.scheduled_end_at,
  route_version_id=NEW.route_version_id,service_type_version_id=NEW.service_type_version_id,
  status=CASE NEW.status WHEN 'PENDING_RESPONSE' THEN 'PENDING_RESPONSE' WHEN 'SCHEDULED' THEN 'CONFIRMED'
   WHEN 'EN_SERVICIO' THEN 'IN_SERVICE' WHEN 'COMPLETED' THEN 'COMPLETED' ELSE 'PENDING_REASSIGNMENT' END,
  version=version+1,updated_at=NEW.updated_at WHERE id=NEW.service_id;
 RETURN NEW;
END; $$;
CREATE TRIGGER assignment_service_progress AFTER INSERT OR UPDATE ON assignment
 FOR EACH ROW EXECUTE FUNCTION assignment_service_progress();
