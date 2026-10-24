-- M8: availability changes join the durable mobile command-receipt boundary.
-- Availability has no assignment; service commands always retain their assignment.

ALTER TABLE mobile_command_receipt
    DROP CONSTRAINT ck_mobile_command_receipt_type;

ALTER TABLE mobile_command_receipt
    ADD CONSTRAINT ck_mobile_command_receipt_type CHECK (
        command_type IN (
            'AVAILABILITY_CHANGE',
            'ASSIGNMENT_ACCEPT',
            'ASSIGNMENT_REJECT',
            'ASSIGNMENT_START',
            'ASSIGNMENT_COMPLETE'
        )
    );

ALTER TABLE mobile_command_receipt
    ADD CONSTRAINT ck_mobile_command_receipt_assignment_scope CHECK (
        (command_type = 'AVAILABILITY_CHANGE' AND assignment_id IS NULL)
        OR (command_type <> 'AVAILABILITY_CHANGE' AND assignment_id IS NOT NULL)
    );

COMMENT ON CONSTRAINT ck_mobile_command_receipt_assignment_scope ON mobile_command_receipt IS
    'Availability is scoped to the authenticated driver; assignment commands retain the related assignment.';
