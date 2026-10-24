ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_otp_challenge (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    verification_token_hash VARCHAR(64),
    otp_hash VARCHAR(64),
    otp_issued_at TIMESTAMPTZ,
    otp_expires_at TIMESTAMPTZ,
    challenge_expires_at TIMESTAMPTZ,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    blocked_until TIMESTAMPTZ,
    send_at_1 TIMESTAMPTZ,
    send_at_2 TIMESTAMPTZ,
    send_at_3 TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_otp_challenge_user UNIQUE (user_id),
    CONSTRAINT uq_email_otp_token_hash UNIQUE (verification_token_hash),
    CONSTRAINT ck_email_otp_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 2),
    CONSTRAINT ck_email_otp_send_state CHECK (
        (verification_token_hash IS NULL AND otp_hash IS NULL)
        OR (verification_token_hash IS NOT NULL AND challenge_expires_at IS NOT NULL)
    )
);

CREATE INDEX ix_email_otp_challenge_expiry
    ON email_otp_challenge (challenge_expires_at)
    WHERE consumed_at IS NULL;
