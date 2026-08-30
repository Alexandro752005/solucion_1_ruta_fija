CREATE TABLE organization (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(150) NOT NULL,
    trade_name VARCHAR(120),
    status VARCHAR(20) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'America/Lima',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    CONSTRAINT uq_organization_legal_name UNIQUE (legal_name)
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_app_user_role CHECK (role IN ('SUPER_ADMIN', 'ADMINISTRADOR', 'COORDINADOR', 'CONDUCTOR')),
    CONSTRAINT ck_app_user_tenant CHECK (
        (role = 'SUPER_ADMIN' AND organization_id IS NULL)
        OR (role <> 'SUPER_ADMIN' AND organization_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (LOWER(email));
CREATE INDEX ix_app_user_organization_role_active
    ON app_user (organization_id, role, active);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES refresh_token(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_token_replaced_by UNIQUE (replaced_by_id)
);

CREATE INDEX ix_refresh_token_user_active
    ON refresh_token (user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    user_id UUID REFERENCES app_user(id),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60),
    entity_id UUID,
    correlation_id VARCHAR(80),
    metadata_json JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_audit_event_organization_occurred
    ON audit_event (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_event_user_occurred
    ON audit_event (user_id, occurred_at DESC);

CREATE FUNCTION prevent_audit_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_append_only
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION prevent_audit_event_mutation();
