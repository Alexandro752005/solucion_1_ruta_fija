\set ON_ERROR_STOP on

BEGIN;

INSERT INTO organization (id, legal_name, status, created_at, updated_at)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    'F1.3 BUSINESS RULE PROBE',
    'ACTIVE',
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO app_user (
    id, organization_id, email, password_hash, full_name, role, active, created_at, updated_at
)
VALUES (
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001',
    'f13.migrator.probe@example.test',
    'not-a-real-password-hash',
    'F1.3 Migrator Probe',
    'ADMIN',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO transport_group (id, organization_id, name, active, created_at, updated_at)
VALUES (
    '30000000-0000-0000-0000-000000000003',
    '30000000-0000-0000-0000-000000000001',
    'Grupo F1.3',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO driver (
    id, organization_id, group_id, full_name, document_type, document_number,
    license_number, availability_status, active, created_at, updated_at
)
VALUES
(
    '30000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    'Conductor F1.3 Uno',
    'DNI',
    'F13-DRIVER-ONE',
    'F13-LICENSE-ONE',
    'DISPONIBLE',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
),
(
    '30000000-0000-0000-0000-000000000005',
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    'Conductor F1.3 Dos',
    'DNI',
    'F13-DRIVER-TWO',
    'F13-LICENSE-TWO',
    'DISPONIBLE',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO vehicle (
    id, organization_id, plate, brand, model, status, active, created_at, updated_at
)
VALUES
(
    '30000000-0000-0000-0000-000000000006',
    '30000000-0000-0000-0000-000000000001',
    'F13-001',
    'Ruta Fija',
    'Probe Uno',
    'DISPONIBLE',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
),
(
    '30000000-0000-0000-0000-000000000007',
    '30000000-0000-0000-0000-000000000001',
    'F13-002',
    'Ruta Fija',
    'Probe Dos',
    'DISPONIBLE',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO assignment (
    id, organization_id, driver_id, vehicle_id, created_by, status, origin_text,
    destination_text, scheduled_at, scheduled_end_at, created_at, updated_at
)
VALUES (
    '30000000-0000-0000-0000-000000000008',
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000006',
    '30000000-0000-0000-0000-000000000002',
    'SCHEDULED',
    'Origen F1.3',
    'Destino F1.3',
    TIMESTAMPTZ '2026-09-21 10:00:00+00',
    TIMESTAMPTZ '2026-09-21 12:00:00+00',
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO audit_event (
    id, organization_id, user_id, action, entity_type, entity_id, occurred_at
)
VALUES (
    '30000000-0000-0000-0000-000000000009',
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    'F1_3_BUSINESS_RULE_PROBE',
    'ASSIGNMENT',
    '30000000-0000-0000-0000-000000000008',
    clock_timestamp()
);

DO $$
DECLARE
    actual_message TEXT;
BEGIN
    BEGIN
        UPDATE audit_event
           SET action = 'MUTATED'
         WHERE id = '30000000-0000-0000-0000-000000000009';
        RAISE EXCEPTION 'audit update was not blocked';
    EXCEPTION WHEN raise_exception THEN
        GET STACKED DIAGNOSTICS actual_message = MESSAGE_TEXT;
        IF actual_message <> 'audit_event is append-only' THEN
            RAISE;
        END IF;
    END;

    BEGIN
        DELETE FROM audit_event
         WHERE id = '30000000-0000-0000-0000-000000000009';
        RAISE EXCEPTION 'audit delete was not blocked';
    EXCEPTION WHEN raise_exception THEN
        GET STACKED DIAGNOSTICS actual_message = MESSAGE_TEXT;
        IF actual_message <> 'audit_event is append-only' THEN
            RAISE;
        END IF;
    END;
END;
$$;

DO $$
DECLARE
    actual_constraint TEXT;
BEGIN
    BEGIN
        INSERT INTO driver (
            id, organization_id, group_id, full_name, document_type, document_number,
            availability_status, active, created_at, updated_at
        )
        VALUES (
            '30000000-0000-0000-0000-000000000010',
            '30000000-0000-0000-0000-000000000001',
            '30000000-0000-0000-0000-000000000003',
            'Conductor F1.3 No Permitido',
            'DNI',
            'F13-DRIVER-OFFLINE',
            'DESCONECTADO',
            TRUE,
            clock_timestamp(),
            clock_timestamp()
        );
        RAISE EXCEPTION 'driver status DESCONECTADO was not blocked';
    EXCEPTION WHEN check_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ck_driver_availability_status' THEN
            RAISE EXCEPTION 'unexpected driver check constraint: %', actual_constraint;
        END IF;
    END;
END;
$$;

DO $$
DECLARE
    actual_constraint TEXT;
BEGIN
    BEGIN
        INSERT INTO assignment (
            id, organization_id, driver_id, vehicle_id, created_by, status, origin_text,
            destination_text, scheduled_at, scheduled_end_at, created_at, updated_at
        )
        VALUES (
            '30000000-0000-0000-0000-000000000011',
            '30000000-0000-0000-0000-000000000001',
            '30000000-0000-0000-0000-000000000004',
            '30000000-0000-0000-0000-000000000007',
            '30000000-0000-0000-0000-000000000002',
            'SCHEDULED',
            'Origen F1.3 conductor',
            'Destino F1.3 conductor',
            TIMESTAMPTZ '2026-09-21 10:30:00+00',
            TIMESTAMPTZ '2026-09-21 11:30:00+00',
            clock_timestamp(),
            clock_timestamp()
        );
        RAISE EXCEPTION 'driver overlap was not blocked';
    EXCEPTION WHEN exclusion_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ex_assignment_driver_schedule_no_overlap' THEN
            RAISE EXCEPTION 'unexpected overlap constraint: %', actual_constraint;
        END IF;
    END;

    BEGIN
        INSERT INTO assignment (
            id, organization_id, driver_id, vehicle_id, created_by, status, origin_text,
            destination_text, scheduled_at, scheduled_end_at, created_at, updated_at
        )
        VALUES (
            '30000000-0000-0000-0000-000000000012',
            '30000000-0000-0000-0000-000000000001',
            '30000000-0000-0000-0000-000000000005',
            '30000000-0000-0000-0000-000000000006',
            '30000000-0000-0000-0000-000000000002',
            'SCHEDULED',
            'Origen F1.3 vehiculo',
            'Destino F1.3 vehiculo',
            TIMESTAMPTZ '2026-09-21 10:30:00+00',
            TIMESTAMPTZ '2026-09-21 11:30:00+00',
            clock_timestamp(),
            clock_timestamp()
        );
        RAISE EXCEPTION 'vehicle overlap was not blocked';
    EXCEPTION WHEN exclusion_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ex_assignment_vehicle_schedule_no_overlap' THEN
            RAISE EXCEPTION 'unexpected overlap constraint: %', actual_constraint;
        END IF;
    END;
END;
$$;

ROLLBACK;

SELECT 'F1_3_BUSINESS_RULES=PASS';
