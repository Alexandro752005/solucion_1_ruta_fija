-- F4.4 amendment: a conductor may close once an incident that they reported
-- from the authenticated mobile channel. The row remains auditable and visible
-- to ADMIN; it is never physically deleted.

ALTER TABLE incident
    ADD COLUMN driver_closed_by UUID REFERENCES app_user(id),
    ADD COLUMN driver_closed_at TIMESTAMPTZ;

ALTER TABLE incident
    DROP CONSTRAINT ck_incident_status;

ALTER TABLE incident
    ADD CONSTRAINT ck_incident_status CHECK (
        status IN ('OPEN', 'FOLLOW_UP', 'RESOLVED', 'CLOSED_BY_DRIVER')
    );

ALTER TABLE incident
    ADD CONSTRAINT ck_incident_driver_close_pair CHECK (
        (driver_closed_by IS NULL AND driver_closed_at IS NULL)
        OR (driver_closed_by IS NOT NULL AND driver_closed_at IS NOT NULL)
    );

ALTER TABLE incident
    ADD CONSTRAINT ck_incident_closed_by_driver_state CHECK (
        status <> 'CLOSED_BY_DRIVER'
        OR (driver_closed_by IS NOT NULL AND driver_closed_at IS NOT NULL)
    );

CREATE INDEX ix_incident_organization_driver_closed
    ON incident (organization_id, driver_closed_at DESC)
    WHERE driver_closed_at IS NOT NULL;

COMMENT ON COLUMN incident.driver_closed_by IS
    'Authenticated MOBILE reporter who closed the incident once; preserved after ADMIN follow-up.';
COMMENT ON COLUMN incident.driver_closed_at IS
    'Server time of the one-way conductor closure; not a client supplied timestamp.';
