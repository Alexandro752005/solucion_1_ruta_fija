-- Q-053 permits the exact start instant as deadline. Existing V7 data remains valid.
-- Preserve every other lifecycle condition and all historical response modes.
ALTER TABLE assignment DROP CONSTRAINT ck_assignment_response_lifecycle;
ALTER TABLE assignment
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
            AND response_deadline_at <= scheduled_at
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
