CREATE TABLE assignment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    driver_id UUID NOT NULL REFERENCES driver(id),
    vehicle_id UUID NOT NULL REFERENCES vehicle(id),
    created_by UUID NOT NULL REFERENCES app_user(id),
    status VARCHAR(30) NOT NULL,
    origin_text VARCHAR(250) NOT NULL,
    destination_text VARCHAR(250) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    scheduled_end_at TIMESTAMPTZ NOT NULL,
    reserved_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    notes VARCHAR(500),
    idempotency_key VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_assignment_status CHECK (
        status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_assignment_schedule CHECK (scheduled_end_at > scheduled_at),
    CONSTRAINT ck_assignment_terminal_times CHECK (
        (status = 'SCHEDULED' AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'EN_SERVICIO' AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_assignment_organization_idempotency_key
    ON assignment (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_assignment_organization_status_schedule
    ON assignment (organization_id, status, scheduled_at DESC);
CREATE INDEX ix_assignment_driver_status_schedule
    ON assignment (driver_id, status, scheduled_at);
CREATE INDEX ix_assignment_vehicle_status_schedule
    ON assignment (vehicle_id, status, scheduled_at);

CREATE TABLE incident (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    driver_id UUID NOT NULL REFERENCES driver(id),
    assignment_id UUID REFERENCES assignment(id),
    reported_by UUID NOT NULL REFERENCES app_user(id),
    category VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    follow_up_note VARCHAR(1000),
    followed_up_by UUID REFERENCES app_user(id),
    followed_up_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_incident_category CHECK (
        category IN ('AVERIA', 'ACCIDENTE', 'RETRASO', 'OTRO')
    ),
    CONSTRAINT ck_incident_status CHECK (
        status IN ('OPEN', 'FOLLOW_UP', 'RESOLVED')
    ),
    CONSTRAINT ck_incident_resolution CHECK (
        (status = 'RESOLVED' AND resolved_at IS NOT NULL)
        OR (status <> 'RESOLVED' AND resolved_at IS NULL)
    )
);

CREATE INDEX ix_incident_organization_status_reported
    ON incident (organization_id, status, reported_at DESC);
CREATE INDEX ix_incident_assignment ON incident (assignment_id);
CREATE INDEX ix_incident_driver_reported ON incident (driver_id, reported_at DESC);

CREATE TABLE announcement (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_by UUID NOT NULL REFERENCES app_user(id),
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    audience_type VARCHAR(20) NOT NULL,
    audience_id UUID,
    require_read_ack BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_announcement_audience CHECK (
        (audience_type = 'ORGANIZATION' AND audience_id IS NULL)
        OR (audience_type = 'GROUP' AND audience_id IS NOT NULL)
    ),
    CONSTRAINT ck_announcement_read_ack_web_only CHECK (require_read_ack = FALSE)
);

CREATE INDEX ix_announcement_organization_created
    ON announcement (organization_id, created_at DESC);
