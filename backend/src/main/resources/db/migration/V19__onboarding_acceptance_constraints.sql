ALTER TABLE conductor_onboarding_email_challenge
    ADD CONSTRAINT fk_onboarding_challenge_request
        FOREIGN KEY (request_id)
        REFERENCES conductor_onboarding_request(id);

CREATE UNIQUE INDEX uq_onboarding_request_invitation
    ON conductor_onboarding_request (invitation_id);
