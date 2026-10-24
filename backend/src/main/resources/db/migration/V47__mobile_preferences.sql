-- Own-account presentation preferences; no identity, token, document or GPS data.
CREATE TABLE mobile_preferences (
    user_id uuid PRIMARY KEY REFERENCES app_user(id),
    organization_id uuid NOT NULL REFERENCES organization(id),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    optional_notice_alerts boolean NOT NULL DEFAULT true,
    text_size varchar(16) NOT NULL DEFAULT 'NORMAL' CHECK (text_size IN ('NORMAL','LARGE','EXTRA_LARGE')),
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_mobile_preferences_organization ON mobile_preferences(organization_id);
COMMENT ON TABLE mobile_preferences IS 'Presentation only. Mandatory notices, security and service confirmations are never suppressed. Account link follows approved personal-data retirement.';
