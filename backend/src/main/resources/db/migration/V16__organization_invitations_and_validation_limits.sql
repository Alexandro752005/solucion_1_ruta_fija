CREATE TABLE organization_invitation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    issuer_user_id UUID NOT NULL,
    recipient_email VARCHAR(180) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    CONSTRAINT fk_invitation_issuer_tenant
        FOREIGN KEY (issuer_user_id, organization_id)
        REFERENCES app_user (id, organization_id),
    CONSTRAINT ck_invitation_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_invitation_recipient_email CHECK (length(trim(recipient_email)) > 0),
    CONSTRAINT ck_invitation_validity CHECK (
        expires_at > issued_at
        AND expires_at <= issued_at + interval '120 hours'
    ),
    CONSTRAINT ck_invitation_not_revoked_and_used CHECK (revoked_at IS NULL OR used_at IS NULL)
);

CREATE UNIQUE INDEX uq_invitation_active_tenant_recipient
    ON organization_invitation (organization_id, recipient_email)
    WHERE revoked_at IS NULL AND used_at IS NULL;

CREATE UNIQUE INDEX uq_invitation_active_recipient_code
    ON organization_invitation (recipient_email, code_hash)
    WHERE revoked_at IS NULL AND used_at IS NULL;

CREATE INDEX ix_invitation_tenant_issued
    ON organization_invitation (organization_id, issued_at DESC, id DESC);

CREATE TABLE invitation_validation_account_guard (
    user_id UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE invitation_validation_origin_guard (
    origin_hash CHAR(64) PRIMARY KEY,
    paused_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_invitation_origin_hash CHECK (origin_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE invitation_validation_failure (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    origin_hash CHAR(64) NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_invitation_failure_origin_hash CHECK (origin_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_invitation_failure_user_time
    ON invitation_validation_failure (user_id, failed_at DESC);

CREATE INDEX ix_invitation_failure_origin_time
    ON invitation_validation_failure (origin_hash, failed_at DESC);

CREATE INDEX ix_invitation_failure_time
    ON invitation_validation_failure (failed_at);
