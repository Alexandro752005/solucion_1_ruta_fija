ALTER TABLE conductor_onboarding_access_token
    ADD COLUMN linked_user_id UUID REFERENCES app_user(id),
    ADD COLUMN linked_session_version BIGINT,
    ADD CONSTRAINT ck_onboarding_access_account_snapshot CHECK (
        (linked_user_id IS NULL AND linked_session_version IS NULL)
        OR (linked_user_id IS NOT NULL AND linked_session_version IS NOT NULL AND linked_session_version >= 0)
    );

-- Existing tokens intentionally retain a NULL snapshot. Once an identity has an
-- explicit provisioned account, only a newly authenticated token can authorize it.
CREATE UNIQUE INDEX uq_onboarding_provisioned_identity
    ON conductor_onboarding_request(identity_id) WHERE linked_user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_onboarding_provisioned_account
    ON conductor_onboarding_request(linked_user_id) WHERE linked_user_id IS NOT NULL;

-- Deactivation must permanently revoke scoped credentials, even if the account
-- is later reactivated without rotating its operational session version.
CREATE FUNCTION revoke_onboarding_access_on_account_deactivation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.active AND NOT NEW.active THEN
        UPDATE conductor_onboarding_access_token
           SET revoked_at = greatest(CURRENT_TIMESTAMP, created_at)
         WHERE linked_user_id = NEW.id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER revoke_onboarding_access_on_deactivation
    AFTER UPDATE OF active ON app_user
    FOR EACH ROW EXECUTE FUNCTION revoke_onboarding_access_on_account_deactivation();
