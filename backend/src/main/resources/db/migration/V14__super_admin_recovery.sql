ALTER TABLE email_otp_challenge
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'LOGIN',
    ADD COLUMN recipient_email VARCHAR(180),
    ADD COLUMN purpose_reference_id UUID;

UPDATE email_otp_challenge challenge
   SET recipient_email = app_user.email
  FROM app_user
 WHERE app_user.id = challenge.user_id
   AND challenge.recipient_email IS NULL;

ALTER TABLE email_otp_challenge
    ALTER COLUMN recipient_email SET NOT NULL;

ALTER TABLE email_otp_challenge
    ADD CONSTRAINT ck_email_otp_purpose_reference CHECK (
        (purpose = 'LOGIN' AND purpose_reference_id IS NULL)
        OR (purpose = 'SUPER_ADMIN_RECOVERY' AND purpose_reference_id IS NOT NULL)
    );

ALTER TABLE app_user
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_app_user_session_version CHECK (session_version >= 0);

CREATE TABLE super_admin_recovery_credential (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    installation_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    public_key_x509 VARCHAR(256) NOT NULL,
    emergency_code_hash VARCHAR(100) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE super_admin_recovery_challenge (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    installation_id UUID NOT NULL,
    account_reference UUID NOT NULL,
    requested_email VARCHAR(180) NOT NULL,
    nonce VARCHAR(43) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_proof_attempts SMALLINT NOT NULL DEFAULT 0,
    proof_verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_super_admin_recovery_failed_attempts CHECK (failed_proof_attempts BETWEEN 0 AND 3),
    CONSTRAINT uq_super_admin_recovery_nonce UNIQUE (nonce)
);

CREATE INDEX ix_super_admin_recovery_challenge_expiry
    ON super_admin_recovery_challenge (expires_at)
    WHERE consumed_at IS NULL;
