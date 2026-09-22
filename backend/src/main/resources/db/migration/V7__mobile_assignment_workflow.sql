-- F3.1B candidate. It is rehearsed outside the runtime classpath before it is
-- copied byte-for-byte to backend/src/main/resources/db/migration.

ALTER TABLE assignment
    ADD COLUMN response_mode VARCHAR(30) NOT NULL DEFAULT 'ADMIN_DIRECT',
    ADD COLUMN response_deadline_at TIMESTAMPTZ,
    ADD COLUMN accepted_at TIMESTAMPTZ,
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason VARCHAR(300),
    ADD COLUMN expired_at TIMESTAMPTZ;

ALTER TABLE assignment
    DROP CONSTRAINT ck_assignment_status,
    DROP CONSTRAINT ck_assignment_terminal_times;

ALTER TABLE assignment
    ADD CONSTRAINT ck_assignment_status CHECK (
        status IN (
            'PENDING_RESPONSE',
            'SCHEDULED',
            'EN_SERVICIO',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    ),
    ADD CONSTRAINT ck_assignment_terminal_times CHECK (
        (status IN ('PENDING_RESPONSE', 'SCHEDULED', 'EN_SERVICIO', 'REJECTED', 'EXPIRED')
            AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
    ),
    ADD CONSTRAINT ck_assignment_response_lifecycle CHECK (
        (
            response_mode = 'ADMIN_DIRECT'
            AND status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'CANCELLED')
            AND response_deadline_at IS NULL
            AND accepted_at IS NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL
            AND expired_at IS NULL
        )
        OR
        (
            response_mode = 'MOBILE_CONFIRMATION'
            AND response_deadline_at IS NOT NULL
            AND response_deadline_at < scheduled_at
            AND (accepted_at IS NULL OR accepted_at <= response_deadline_at)
            AND (rejected_at IS NULL OR rejected_at <= response_deadline_at)
            AND (
                (status = 'PENDING_RESPONSE'
                    AND accepted_at IS NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NULL)
                OR
                (status IN ('SCHEDULED', 'EN_SERVICIO', 'COMPLETED')
                    AND accepted_at IS NOT NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NOT NULL)
                OR
                (status = 'REJECTED'
                    AND accepted_at IS NULL
                    AND rejected_at IS NOT NULL
                    AND expired_at IS NULL
                    AND reserved_at IS NULL)
                OR
                (status = 'EXPIRED'
                    AND accepted_at IS NULL
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NOT NULL
                    AND expired_at >= response_deadline_at
                    AND reserved_at IS NULL)
                OR
                (status = 'CANCELLED'
                    AND rejected_at IS NULL
                    AND rejection_reason IS NULL
                    AND expired_at IS NULL
                    AND (
                        (accepted_at IS NULL AND reserved_at IS NULL)
                        OR (accepted_at IS NOT NULL AND reserved_at IS NOT NULL)
                    ))
            )
        )
    );

ALTER TABLE assignment
    DROP CONSTRAINT ex_assignment_driver_schedule_no_overlap,
    DROP CONSTRAINT ex_assignment_vehicle_schedule_no_overlap;

ALTER TABLE assignment
    ADD CONSTRAINT ex_assignment_driver_schedule_no_overlap
    EXCLUDE USING gist (
        driver_id WITH =,
        tstzrange(scheduled_at, scheduled_end_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING_RESPONSE', 'SCHEDULED', 'EN_SERVICIO')),
    ADD CONSTRAINT ex_assignment_vehicle_schedule_no_overlap
    EXCLUDE USING gist (
        vehicle_id WITH =,
        tstzrange(scheduled_at, scheduled_end_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING_RESPONSE', 'SCHEDULED', 'EN_SERVICIO'));

CREATE INDEX ix_assignment_driver_pending_response_deadline
    ON assignment (driver_id, response_deadline_at)
    WHERE status = 'PENDING_RESPONSE';

CREATE INDEX ix_assignment_organization_pending_response_deadline
    ON assignment (organization_id, response_deadline_at)
    WHERE status = 'PENDING_RESPONSE';

COMMENT ON COLUMN assignment.response_mode IS
    'ADMIN_DIRECT preserves CRM scheduling; MOBILE_CONFIRMATION requires an authenticated driver response.';
COMMENT ON COLUMN assignment.response_deadline_at IS
    'UTC deadline evaluated by server-side mobile workflow.';
COMMENT ON COLUMN assignment.accepted_at IS
    'UTC server timestamp of the authenticated driver acceptance.';
COMMENT ON COLUMN assignment.rejected_at IS
    'UTC server timestamp of the authenticated driver rejection.';
COMMENT ON COLUMN assignment.expired_at IS
    'UTC server timestamp when a pending mobile response expires.';
