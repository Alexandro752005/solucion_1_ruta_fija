ALTER TABLE conductor_onboarding_request
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN modality VARCHAR(16),
    ADD COLUMN catalog_id VARCHAR(40) NOT NULL DEFAULT 'L01-CHICLAYO',
    ADD COLUMN catalog_version VARCHAR(20) NOT NULL DEFAULT '0.1.0',
    ADD COLUMN catalog_hash CHAR(64) NOT NULL DEFAULT encode(sha256(convert_to('L01-CHICLAYO|0.1.0|CON-01:DNI|CON-02:LICENSE:M1:A-IIA,A-IIB,A-IIIA,A-IIIB,A-IIIC|CON-03:AUTHORIZATION:CONDITIONAL|CON-04:EXTRA:UNKNOWN|CON-05:COLECTIVO:ELIGIBILITY|NO_FILES|VISUAL_REVIEW|RETENTION30D', 'UTF8')), 'hex'),
    ADD COLUMN returned_at TIMESTAMPTZ,
    ADD COLUMN correction_deadline TIMESTAMPTZ,
    ADD COLUMN personal_approved_at TIMESTAMPTZ,
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN rejection_kind VARCHAR(20),
    ADD COLUMN reapply_after TIMESTAMPTZ,
    ADD COLUMN linked_user_id UUID,
    ADD COLUMN linked_driver_id UUID,
    ADD COLUMN provisioned_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_onboarding_version CHECK (version >= 0),
    ADD CONSTRAINT ck_onboarding_modality CHECK (modality IS NULL OR modality IN ('TAXI', 'COLECTIVO')),
    ADD CONSTRAINT ck_onboarding_catalog_hash CHECK (catalog_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_onboarding_rejection_kind CHECK (rejection_kind IS NULL OR rejection_kind IN ('GENERAL', 'REITERATION')),
    ADD CONSTRAINT fk_onboarding_linked_user_tenant FOREIGN KEY (linked_user_id, organization_id) REFERENCES app_user(id, organization_id),
    ADD CONSTRAINT fk_onboarding_linked_driver_tenant FOREIGN KEY (linked_driver_id, organization_id) REFERENCES driver(id, organization_id),
    ADD CONSTRAINT ck_onboarding_provisioning CHECK (
        (linked_user_id IS NULL AND linked_driver_id IS NULL AND provisioned_at IS NULL)
        OR (linked_user_id IS NOT NULL AND linked_driver_id IS NOT NULL AND provisioned_at IS NOT NULL AND personal_approved_at IS NOT NULL AND status = 'APPROVED'));

CREATE TABLE conductor_document_revision (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES conductor_onboarding_request(id),
    requirement_id VARCHAR(12) NOT NULL,
    revision_no BIGINT NOT NULL,
    entity VARCHAR(12) NOT NULL DEFAULT 'PERSON',
    fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    expiry_mode VARCHAR(16) NOT NULL,
    valid_until TIMESTAMPTZ,
    applicability VARCHAR(20) NOT NULL,
    applicability_condition VARCHAR(500),
    source_reference VARCHAR(500),
    result VARCHAR(32) NOT NULL,
    review_reference VARCHAR(500),
    observation VARCHAR(500),
    reviewer_id UUID REFERENCES app_user(id),
    actor_kind VARCHAR(12) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    catalog_id VARCHAR(40) NOT NULL,
    catalog_version VARCHAR(20) NOT NULL,
    catalog_hash CHAR(64) NOT NULL,
    CONSTRAINT uq_document_revision UNIQUE (request_id, requirement_id, revision_no),
    CONSTRAINT ck_document_requirement CHECK (requirement_id IN ('CON-01','CON-02','CON-03','CON-04','CON-05')),
    CONSTRAINT ck_document_entity CHECK (entity = 'PERSON'),
    CONSTRAINT ck_document_fields CHECK (jsonb_typeof(fields) = 'object'),
    CONSTRAINT ck_document_expiry CHECK (expiry_mode IN ('DATED','NO_EXPIRY','UNKNOWN') AND ((expiry_mode = 'DATED' AND valid_until IS NOT NULL) OR (expiry_mode <> 'DATED' AND valid_until IS NULL))),
    CONSTRAINT ck_document_applicability CHECK (applicability IN ('CONFIRMED','POR_CONTRASTAR')),
    CONSTRAINT ck_document_result CHECK (result IN ('PENDING','CONFORME','OBSERVADO','NO_APLICA_JUSTIFICADO','POR_CONTRASTAR')),
    CONSTRAINT ck_document_actor CHECK (actor_kind IN ('ADMIN','APPLICANT') AND (actor_kind <> 'APPLICANT' OR (reviewer_id IS NULL AND result IN ('PENDING','POR_CONTRASTAR'))))
);

CREATE INDEX ix_document_revision_latest ON conductor_document_revision(request_id, requirement_id, revision_no DESC);

CREATE TABLE conductor_onboarding_review_event (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES conductor_onboarding_request(id),
    operation_key VARCHAR(100) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_kind VARCHAR(12) NOT NULL,
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500),
    review_round INTEGER NOT NULL,
    resulting_version BIGINT NOT NULL,
    resulting_request_id UUID REFERENCES conductor_onboarding_request(id),
    catalog_id VARCHAR(40) NOT NULL,
    catalog_version VARCHAR(20) NOT NULL,
    catalog_hash CHAR(64) NOT NULL,
    CONSTRAINT uq_onboarding_review_operation UNIQUE (request_id, actor_kind, actor_id, operation_key),
    CONSTRAINT ck_onboarding_event_actor CHECK (actor_kind IN ('ADMIN','APPLICANT')),
    CONSTRAINT ck_onboarding_event_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$' AND catalog_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_onboarding_review_history ON conductor_onboarding_review_event(request_id, occurred_at, resulting_version);

CREATE TABLE conductor_person_binding (
    person_hash CHAR(64) PRIMARY KEY,
    identity_id UUID NOT NULL UNIQUE REFERENCES conductor_onboarding_identity(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    user_id UUID UNIQUE REFERENCES app_user(id),
    driver_id UUID UNIQUE REFERENCES driver(id),
    bound_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_person_binding_hash CHECK (person_hash ~ '^[0-9a-f]{64}$')
);

CREATE FUNCTION protect_onboarding_documentary_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Documentary history is append-only' USING ERRCODE = '23000';
END;
$$;

CREATE TRIGGER protect_conductor_document_revision BEFORE UPDATE OR DELETE ON conductor_document_revision
    FOR EACH ROW EXECUTE FUNCTION protect_onboarding_documentary_history();
CREATE TRIGGER protect_onboarding_review_event BEFORE UPDATE OR DELETE ON conductor_onboarding_review_event
    FOR EACH ROW EXECUTE FUNCTION protect_onboarding_documentary_history();
