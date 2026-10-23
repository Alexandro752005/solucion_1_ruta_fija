CREATE TABLE admin_terms_document (
    id UUID PRIMARY KEY,
    document_key VARCHAR(64) NOT NULL,
    version VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    synthetic BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_admin_terms_document_version UNIQUE (document_key, version),
    CONSTRAINT ck_admin_terms_document_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_admin_terms_document_content CHECK (length(content) > 0),
    CONSTRAINT ck_admin_terms_document_synthetic_marker CHECK (
        NOT synthetic
        OR (
            version LIKE 'SYNTHETIC-TEST-ONLY-%'
            AND content LIKE 'SYNTHETIC TEST DOCUMENT ONLY.%'
        )
    )
);

CREATE INDEX ix_admin_terms_document_current
    ON admin_terms_document (document_key, created_at DESC, id DESC);

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_id_organization UNIQUE (id, organization_id);

CREATE TABLE admin_terms_acceptance (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_id UUID NOT NULL REFERENCES admin_terms_document(id),
    document_version VARCHAR(40) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_admin_terms_acceptance_admin_org
        FOREIGN KEY (admin_user_id, organization_id)
        REFERENCES app_user(id, organization_id),
    CONSTRAINT uq_admin_terms_acceptance_admin_document
        UNIQUE (admin_user_id, document_id),
    CONSTRAINT ck_admin_terms_acceptance_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_admin_terms_acceptance_organization
    ON admin_terms_acceptance (organization_id, accepted_at DESC);

CREATE FUNCTION prevent_admin_terms_document_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'admin_terms_document is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_terms_document_immutable
BEFORE UPDATE OR DELETE ON admin_terms_document
FOR EACH ROW EXECUTE FUNCTION prevent_admin_terms_document_mutation();

CREATE FUNCTION prevent_admin_terms_acceptance_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'admin_terms_acceptance is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_terms_acceptance_append_only
BEFORE UPDATE OR DELETE ON admin_terms_acceptance
FOR EACH ROW EXECUTE FUNCTION prevent_admin_terms_acceptance_mutation();
