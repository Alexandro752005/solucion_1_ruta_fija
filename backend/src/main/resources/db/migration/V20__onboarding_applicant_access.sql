CREATE TABLE conductor_onboarding_access_token (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES conductor_onboarding_identity(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_onboarding_access_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_onboarding_access_token_window CHECK (
        expires_at > created_at AND expires_at <= created_at + interval '30 minutes'
    ),
    CONSTRAINT ck_onboarding_access_token_revoked CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    )
);

CREATE INDEX ix_onboarding_access_identity_expiry
    ON conductor_onboarding_access_token (identity_id, expires_at DESC);
