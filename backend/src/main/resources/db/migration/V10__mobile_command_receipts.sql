-- F3.3: durable idempotency receipts for state-changing mobile assignment commands.
-- event_id is global: reusing it with another actor, command, assignment, or
-- request hash is a conflict and never executes the command again.

CREATE TABLE mobile_command_receipt (
    event_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    driver_id UUID NOT NULL,
    command_type VARCHAR(40) NOT NULL,
    assignment_id UUID REFERENCES assignment(id),
    request_hash VARCHAR(64) NOT NULL,
    result_status VARCHAR(30) NOT NULL,
    http_status SMALLINT NOT NULL,
    server_state VARCHAR(30),
    error_code VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_mobile_command_receipt_driver_organization
        FOREIGN KEY (driver_id, organization_id)
        REFERENCES driver (id, organization_id),
    CONSTRAINT ck_mobile_command_receipt_type CHECK (
        command_type IN (
            'ASSIGNMENT_ACCEPT',
            'ASSIGNMENT_REJECT',
            'ASSIGNMENT_START',
            'ASSIGNMENT_COMPLETE'
        )
    ),
    CONSTRAINT ck_mobile_command_receipt_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_mobile_command_receipt_result CHECK (
        result_status IN ('APPLIED', 'REJECTED', 'CONFLICT')
    ),
    CONSTRAINT ck_mobile_command_receipt_http_status CHECK (http_status BETWEEN 200 AND 599),
    CONSTRAINT ck_mobile_command_receipt_outcome CHECK (
        (result_status = 'APPLIED' AND error_code IS NULL)
        OR (result_status IN ('REJECTED', 'CONFLICT') AND error_code IS NOT NULL)
    ),
    CONSTRAINT ck_mobile_command_receipt_processed_after_occurrence
        CHECK (processed_at >= occurred_at)
);

CREATE INDEX ix_mobile_command_receipt_driver_processed
    ON mobile_command_receipt (driver_id, processed_at DESC);
CREATE INDEX ix_mobile_command_receipt_assignment_processed
    ON mobile_command_receipt (assignment_id, processed_at DESC)
    WHERE assignment_id IS NOT NULL;

COMMENT ON TABLE mobile_command_receipt IS
    'Idempotency receipt only; it stores scalar outcome metadata and never JWT, refresh token, location coordinates, or payload JSON.';
