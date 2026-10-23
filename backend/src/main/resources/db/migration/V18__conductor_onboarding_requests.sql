CREATE TABLE conductor_onboarding_identity (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(30),
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_onboarding_identity_email UNIQUE (email),
    CONSTRAINT ck_onboarding_identity_email CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_onboarding_identity_name CHECK (length(trim(full_name)) > 0)
);

CREATE TABLE conductor_onboarding_email_challenge (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL UNIQUE REFERENCES conductor_onboarding_identity(id) ON DELETE CASCADE,
    invitation_id UUID NOT NULL REFERENCES organization_invitation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    verification_token_hash CHAR(64) NOT NULL UNIQUE,
    otp_hash CHAR(64),
    otp_issued_at TIMESTAMPTZ,
    otp_expires_at TIMESTAMPTZ,
    challenge_expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    blocked_until TIMESTAMPTZ,
    send_at_1 TIMESTAMPTZ,
    send_at_2 TIMESTAMPTZ,
    send_at_3 TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    request_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_onboarding_challenge_token_hash CHECK (verification_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_onboarding_challenge_otp_hash CHECK (otp_hash IS NULL OR otp_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_onboarding_challenge_attempts CHECK (failed_attempts BETWEEN 0 AND 2),
    CONSTRAINT ck_onboarding_challenge_otp_window CHECK (
        otp_expires_at IS NULL OR (
            otp_issued_at IS NOT NULL
            AND otp_expires_at > otp_issued_at
            AND otp_expires_at <= otp_issued_at + interval '5 minutes'
        )
    )
);

CREATE TABLE conductor_onboarding_request (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES conductor_onboarding_identity(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    invitation_id UUID NOT NULL REFERENCES organization_invitation(id),
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_rounds SMALLINT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_by_admin_id UUID,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT uq_onboarding_request_idempotency UNIQUE (identity_id, idempotency_key),
    CONSTRAINT ck_onboarding_request_status CHECK (
        status IN ('PENDING_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_onboarding_request_review_rounds CHECK (review_rounds >= 0),
    CONSTRAINT fk_onboarding_request_admin_tenant
        FOREIGN KEY (reviewed_by_admin_id, organization_id)
        REFERENCES app_user (id, organization_id)
);

CREATE UNIQUE INDEX uq_onboarding_identity_pending_organization
    ON conductor_onboarding_request (identity_id, organization_id)
    WHERE status IN ('PENDING_REVIEW', 'RETURNED');

CREATE INDEX ix_onboarding_request_tenant_status_submitted
    ON conductor_onboarding_request (organization_id, status, submitted_at DESC, id DESC);

CREATE TABLE conductor_onboarding_invitation_failure (
    id UUID PRIMARY KEY,
    identity_email_hash CHAR(64) NOT NULL,
    origin_hash CHAR(64) NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_onboarding_identity_email_hash CHECK (identity_email_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_onboarding_origin_hash CHECK (origin_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_onboarding_invitation_failure_email_time
    ON conductor_onboarding_invitation_failure (identity_email_hash, failed_at DESC);

CREATE INDEX ix_onboarding_invitation_failure_origin_time
    ON conductor_onboarding_invitation_failure (origin_hash, failed_at DESC);

CREATE TABLE conductor_onboarding_invitation_guard (
    guard_type VARCHAR(12) NOT NULL,
    guard_hash CHAR(64) NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_onboarding_invitation_guard PRIMARY KEY (guard_type, guard_hash),
    CONSTRAINT ck_onboarding_invitation_guard_type CHECK (guard_type IN ('EMAIL', 'ORIGIN')),
    CONSTRAINT ck_onboarding_invitation_guard_hash CHECK (guard_hash ~ '^[0-9a-f]{64}$')
);
