-- F3.1B candidate. It is rehearsed outside the runtime classpath before it is
-- copied byte-for-byte to backend/src/main/resources/db/migration.

ALTER TABLE driver
    ADD CONSTRAINT uq_driver_id_organization UNIQUE (id, organization_id);

CREATE TABLE driver_current_location (
    driver_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    accuracy_m NUMERIC(8, 2) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(30) NOT NULL,
    CONSTRAINT fk_driver_current_location_driver_organization
        FOREIGN KEY (driver_id, organization_id)
        REFERENCES driver (id, organization_id),
    CONSTRAINT ck_driver_current_location_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_driver_current_location_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_driver_current_location_accuracy
        CHECK (accuracy_m >= 0),
    CONSTRAINT ck_driver_current_location_captured_at
        CHECK (captured_at <= received_at + INTERVAL '5 minutes'),
    CONSTRAINT ck_driver_current_location_expiry
        CHECK (expires_at > received_at),
    CONSTRAINT ck_driver_current_location_source
        CHECK (source = 'MOBILE_APP')
);

CREATE INDEX ix_driver_current_location_organization_expires
    ON driver_current_location (organization_id, expires_at);

COMMENT ON TABLE driver_current_location IS
    'Single current location per driver; no historical route or telemetry is retained.';
COMMENT ON COLUMN driver_current_location.expires_at IS
    'Server-calculated current-location expiry; clients do not choose retention.';
