-- F3.3: mobile-only operational facts. This migration deliberately adds no
-- location history and does not change existing CRM-authored incidents.

ALTER TABLE incident
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'CRM_WEB';

ALTER TABLE incident
    ADD CONSTRAINT ck_incident_source CHECK (source IN ('CRM_WEB', 'MOBILE_APP'));

ALTER TABLE announcement
    DROP CONSTRAINT ck_announcement_read_ack_web_only;

CREATE TABLE announcement_receipt (
    announcement_id UUID NOT NULL REFERENCES announcement(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    delivered_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    PRIMARY KEY (announcement_id, user_id),
    CONSTRAINT ck_announcement_receipt_read_after_delivery
        CHECK (read_at IS NULL OR read_at >= delivered_at)
);

CREATE INDEX ix_announcement_receipt_user_read
    ON announcement_receipt (user_id, read_at, delivered_at DESC);

COMMENT ON COLUMN incident.source IS
    'CRM_WEB for administrative reports and MOBILE_APP for authenticated conductor reports.';
COMMENT ON TABLE announcement_receipt IS
    'Per-user delivery/read receipt for a visible announcement; no tenant column avoids partial duplication.';
