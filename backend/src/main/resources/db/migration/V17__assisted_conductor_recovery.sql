ALTER TABLE email_otp_challenge
    DROP CONSTRAINT ck_email_otp_purpose_reference,
    ADD CONSTRAINT ck_email_otp_purpose_reference CHECK (
        (purpose = 'LOGIN' AND purpose_reference_id IS NULL)
        OR (purpose IN ('SUPER_ADMIN_RECOVERY', 'CONDUCTOR_RECOVERY') AND purpose_reference_id IS NOT NULL)
    );

CREATE TABLE conductor_recovery_request (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    conductor_user_id UUID NOT NULL,
    authorized_by_admin_id UUID NOT NULL,
    previous_email VARCHAR(180) NOT NULL,
    requested_email VARCHAR(180) NOT NULL,
    evidence_method VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(200) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    expediente_reviewed BOOLEAN NOT NULL,
    independent_evidence_confirmed BOOLEAN NOT NULL,
    password_reset_required BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    password_reset_token_hash VARCHAR(64),
    password_reset_expires_at TIMESTAMPTZ,
    password_reset_consumed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_conductor_recovery_target_tenant
        FOREIGN KEY (conductor_user_id, organization_id)
        REFERENCES app_user (id, organization_id),
    CONSTRAINT fk_conductor_recovery_authorizer_tenant
        FOREIGN KEY (authorized_by_admin_id, organization_id)
        REFERENCES app_user (id, organization_id),
    CONSTRAINT ck_conductor_recovery_evidence_method
        CHECK (evidence_method IN ('PRIOR_SECURE_CONTACT', 'IN_PERSON')),
    CONSTRAINT ck_conductor_recovery_evidence_reference
        CHECK (length(trim(evidence_reference)) BETWEEN 1 AND 200),
    CONSTRAINT ck_conductor_recovery_reason
        CHECK (length(trim(reason)) BETWEEN 1 AND 300),
    CONSTRAINT ck_conductor_recovery_status
        CHECK (status IN ('OTP_PENDING', 'PASSWORD_RESET_PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_conductor_recovery_reset_state CHECK (
        (password_reset_token_hash IS NULL AND password_reset_expires_at IS NULL)
        OR (password_reset_token_hash IS NOT NULL AND password_reset_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_conductor_recovery_reset_hash
        CHECK (password_reset_token_hash IS NULL OR password_reset_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_conductor_recovery_completion_time
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE UNIQUE INDEX uq_conductor_recovery_active_request
    ON conductor_recovery_request (conductor_user_id)
    WHERE status IN ('OTP_PENDING', 'PASSWORD_RESET_PENDING');

CREATE UNIQUE INDEX uq_conductor_recovery_reset_token
    ON conductor_recovery_request (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;

CREATE INDEX ix_conductor_recovery_tenant_created
    ON conductor_recovery_request (organization_id, created_at DESC, id DESC);

CREATE INDEX ix_conductor_recovery_conductor_created
    ON conductor_recovery_request (conductor_user_id, created_at DESC, id DESC);
