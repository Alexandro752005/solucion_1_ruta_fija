ALTER TABLE email_otp_challenge DROP CONSTRAINT ck_email_otp_purpose_reference;
ALTER TABLE email_otp_challenge ADD CONSTRAINT ck_email_otp_purpose_reference CHECK (
    (purpose = 'LOGIN' AND purpose_reference_id IS NULL)
    OR (purpose IN ('SUPER_ADMIN_RECOVERY','CONDUCTOR_RECOVERY','ADMIN_ACTIVATION') AND purpose_reference_id IS NOT NULL)
);

CREATE TABLE admin_activation (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL UNIQUE,
    organization_id UUID NOT NULL,
    created_by_super_admin_id UUID NOT NULL REFERENCES app_user(id),
    replaced_admin_user_id UUID,
    verification_method VARCHAR(32),
    verification_reference VARCHAR(160),
    creation_reason VARCHAR(500) NOT NULL,
    credential_hash CHAR(64) NOT NULL UNIQUE,
    credential_issued_at TIMESTAMPTZ NOT NULL,
    credential_expires_at TIMESTAMPTZ NOT NULL,
    credential_consumed_at TIMESTAMPTZ,
    activation_token_hash CHAR(64) UNIQUE,
    activation_expires_at TIMESTAMPTZ,
    activation_token_revoked_at TIMESTAMPTZ,
    password_set_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_CLAIM',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_admin_activation_candidate_tenant FOREIGN KEY (admin_user_id,organization_id) REFERENCES app_user(id,organization_id),
    CONSTRAINT fk_admin_activation_outgoing_tenant FOREIGN KEY (replaced_admin_user_id,organization_id) REFERENCES app_user(id,organization_id),
    CONSTRAINT ck_admin_activation_status CHECK (status IN ('PENDING_CLAIM','IN_PROGRESS','COMPLETED')),
    CONSTRAINT ck_admin_activation_credential_hash CHECK (credential_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_admin_activation_token_hash CHECK (activation_token_hash IS NULL OR activation_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_admin_activation_credential_window CHECK (credential_expires_at > credential_issued_at AND credential_expires_at <= credential_issued_at + interval '24 hours'),
    CONSTRAINT ck_admin_activation_token_window CHECK (activation_expires_at IS NULL OR (password_set_at IS NOT NULL AND activation_expires_at > password_set_at AND activation_expires_at <= password_set_at + interval '30 minutes')),
    CONSTRAINT ck_admin_activation_replacement CHECK (
        (replaced_admin_user_id IS NULL AND verification_method IS NULL AND verification_reference IS NULL)
        OR (replaced_admin_user_id IS NOT NULL AND replaced_admin_user_id <> admin_user_id
            AND verification_method IN ('PRIOR_SECURE_CONTACT','IN_PERSON') AND length(trim(verification_reference))>0)
    ),
    CONSTRAINT ck_admin_activation_scope CHECK (
        (status='PENDING_CLAIM' AND credential_consumed_at IS NULL AND activation_token_hash IS NULL AND password_set_at IS NULL)
        OR (status='IN_PROGRESS' AND credential_consumed_at IS NOT NULL AND activation_token_hash IS NOT NULL AND activation_expires_at IS NOT NULL AND password_set_at IS NOT NULL)
        OR (status='COMPLETED' AND completed_at IS NOT NULL AND email_verified_at IS NOT NULL AND password_set_at IS NOT NULL AND activation_token_revoked_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_admin_pending_replacement ON admin_activation(replaced_admin_user_id)
    WHERE replaced_admin_user_id IS NOT NULL AND status IN ('PENDING_CLAIM','IN_PROGRESS');

CREATE TABLE admin_activation_event (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL REFERENCES admin_activation(id),
    action VARCHAR(40) NOT NULL,
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500)
);

CREATE FUNCTION protect_admin_activation_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ADMIN activation history is append-only' USING ERRCODE='23000';
END;
$$;
CREATE TRIGGER protect_admin_activation_history BEFORE UPDATE OR DELETE ON admin_activation_event
    FOR EACH ROW EXECUTE FUNCTION protect_admin_activation_history();

CREATE TABLE admin_security_revocation (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    authorized_by_super_admin_id UUID NOT NULL REFERENCES app_user(id),
    reason VARCHAR(500) NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_admin_security_revocation_tenant FOREIGN KEY (admin_user_id,organization_id) REFERENCES app_user(id,organization_id)
);
CREATE TRIGGER protect_admin_security_revocation_history BEFORE UPDATE OR DELETE ON admin_security_revocation
    FOR EACH ROW EXECUTE FUNCTION protect_admin_activation_history();
