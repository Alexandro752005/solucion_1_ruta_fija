\set ON_ERROR_STOP on

BEGIN;

DO $f31b$
DECLARE
    actual_constraint text;
    current_rows integer;
    current_latitude numeric(9, 6);
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM assignment
         WHERE id = '00000000-0000-4000-8000-000000031501'
           AND status = 'SCHEDULED'
           AND response_mode = 'ADMIN_DIRECT'
           AND response_deadline_at IS NULL
           AND accepted_at IS NULL
           AND rejected_at IS NULL
           AND expired_at IS NULL
    ) THEN
        RAISE EXCEPTION 'V7 did not preserve the V6 assignment as ADMIN_DIRECT';
    END IF;

    BEGIN
        INSERT INTO assignment (
            id, organization_id, driver_id, vehicle_id, created_by, status,
            response_mode, response_deadline_at, origin_text, destination_text,
            scheduled_at, scheduled_end_at, version, created_at, updated_at
        ) VALUES (
            '00000000-0000-4000-8000-000000031511',
            '00000000-0000-4000-8000-000000031001',
            '00000000-0000-4000-8000-000000031301',
            '00000000-0000-4000-8000-000000031402',
            '00000000-0000-4000-8000-000000031101',
            'PENDING_RESPONSE', 'MOBILE_CONFIRMATION', TIMESTAMPTZ '2030-04-10 09:00:00+00',
            'Origen', 'Destino', TIMESTAMPTZ '2030-04-10 09:30:00+00', TIMESTAMPTZ '2030-04-10 10:30:00+00',
            0, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
        );
        RAISE EXCEPTION 'V7 accepted a pending assignment overlapping the same driver';
    EXCEPTION WHEN exclusion_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ex_assignment_driver_schedule_no_overlap' THEN
            RAISE EXCEPTION 'Unexpected driver overlap constraint: %', actual_constraint;
        END IF;
    END;

    BEGIN
        INSERT INTO assignment (
            id, organization_id, driver_id, vehicle_id, created_by, status,
            response_mode, response_deadline_at, origin_text, destination_text,
            scheduled_at, scheduled_end_at, version, created_at, updated_at
        ) VALUES (
            '00000000-0000-4000-8000-000000031512',
            '00000000-0000-4000-8000-000000031001',
            '00000000-0000-4000-8000-000000031302',
            '00000000-0000-4000-8000-000000031401',
            '00000000-0000-4000-8000-000000031101',
            'PENDING_RESPONSE', 'MOBILE_CONFIRMATION', TIMESTAMPTZ '2030-04-10 09:00:00+00',
            'Origen', 'Destino', TIMESTAMPTZ '2030-04-10 09:30:00+00', TIMESTAMPTZ '2030-04-10 10:30:00+00',
            0, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
        );
        RAISE EXCEPTION 'V7 accepted a pending assignment overlapping the same vehicle';
    EXCEPTION WHEN exclusion_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ex_assignment_vehicle_schedule_no_overlap' THEN
            RAISE EXCEPTION 'Unexpected vehicle overlap constraint: %', actual_constraint;
        END IF;
    END;

    BEGIN
        INSERT INTO assignment (
            id, organization_id, driver_id, vehicle_id, created_by, status,
            response_mode, origin_text, destination_text, scheduled_at,
            scheduled_end_at, version, created_at, updated_at
        ) VALUES (
            '00000000-0000-4000-8000-000000031513',
            '00000000-0000-4000-8000-000000031001',
            '00000000-0000-4000-8000-000000031302',
            '00000000-0000-4000-8000-000000031402',
            '00000000-0000-4000-8000-000000031101',
            'PENDING_RESPONSE', 'ADMIN_DIRECT', 'Origen', 'Destino',
            TIMESTAMPTZ '2030-04-10 11:00:00+00', TIMESTAMPTZ '2030-04-10 12:00:00+00',
            0, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
        );
        RAISE EXCEPTION 'V7 accepted a false CRM acceptance state';
    EXCEPTION WHEN check_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ck_assignment_response_lifecycle' THEN
            RAISE EXCEPTION 'Unexpected false-acceptance constraint: %', actual_constraint;
        END IF;
    END;

    INSERT INTO assignment (
        id, organization_id, driver_id, vehicle_id, created_by, status,
        response_mode, response_deadline_at, origin_text, destination_text,
        scheduled_at, scheduled_end_at, version, created_at, updated_at
    ) VALUES (
        '00000000-0000-4000-8000-000000031514',
        '00000000-0000-4000-8000-000000031001',
        '00000000-0000-4000-8000-000000031302',
        '00000000-0000-4000-8000-000000031402',
        '00000000-0000-4000-8000-000000031101',
        'PENDING_RESPONSE', 'MOBILE_CONFIRMATION', TIMESTAMPTZ '2030-04-10 10:30:00+00',
        'Origen', 'Destino', TIMESTAMPTZ '2030-04-10 11:00:00+00', TIMESTAMPTZ '2030-04-10 12:00:00+00',
        0, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
    );

    INSERT INTO organization (
        id, legal_name, trade_name, status, timezone, created_at, updated_at
    ) VALUES (
        '00000000-0000-4000-8000-000000031002',
        'Organizacion F3.1B B', 'F3.1B B', 'ACTIVE', 'America/Lima',
        TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
    );

    BEGIN
        INSERT INTO driver_current_location (
            driver_id, organization_id, latitude, longitude, accuracy_m,
            captured_at, received_at, expires_at, source
        ) VALUES (
            '00000000-0000-4000-8000-000000031301',
            '00000000-0000-4000-8000-000000031001',
            91, -79.840900, 8.25,
            TIMESTAMPTZ '2030-04-10 08:00:00+00', TIMESTAMPTZ '2030-04-10 08:00:05+00',
            TIMESTAMPTZ '2030-04-10 08:05:05+00', 'MOBILE_APP'
        );
        RAISE EXCEPTION 'V8 accepted invalid latitude';
    EXCEPTION WHEN check_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'ck_driver_current_location_latitude' THEN
            RAISE EXCEPTION 'Unexpected latitude constraint: %', actual_constraint;
        END IF;
    END;

    BEGIN
        INSERT INTO driver_current_location (
            driver_id, organization_id, latitude, longitude, accuracy_m,
            captured_at, received_at, expires_at, source
        ) VALUES (
            '00000000-0000-4000-8000-000000031301',
            '00000000-0000-4000-8000-000000031002',
            -6.771400, -79.840900, 8.25,
            TIMESTAMPTZ '2030-04-10 08:00:00+00', TIMESTAMPTZ '2030-04-10 08:00:05+00',
            TIMESTAMPTZ '2030-04-10 08:05:05+00', 'MOBILE_APP'
        );
        RAISE EXCEPTION 'V8 accepted a driver location across tenants';
    EXCEPTION WHEN foreign_key_violation THEN
        GET STACKED DIAGNOSTICS actual_constraint = CONSTRAINT_NAME;
        IF actual_constraint <> 'fk_driver_current_location_driver_organization' THEN
            RAISE EXCEPTION 'Unexpected tenant FK: %', actual_constraint;
        END IF;
    END;

    INSERT INTO driver_current_location (
        driver_id, organization_id, latitude, longitude, accuracy_m,
        captured_at, received_at, expires_at, source
    ) VALUES (
        '00000000-0000-4000-8000-000000031301',
        '00000000-0000-4000-8000-000000031001',
        -6.771400, -79.840900, 8.25,
        TIMESTAMPTZ '2030-04-10 08:00:00+00', TIMESTAMPTZ '2030-04-10 08:00:05+00',
        TIMESTAMPTZ '2030-04-10 08:05:05+00', 'MOBILE_APP'
    ) ON CONFLICT (driver_id) DO UPDATE
        SET latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            accuracy_m = EXCLUDED.accuracy_m,
            captured_at = EXCLUDED.captured_at,
            received_at = EXCLUDED.received_at,
            expires_at = EXCLUDED.expires_at,
            source = EXCLUDED.source;

    INSERT INTO driver_current_location (
        driver_id, organization_id, latitude, longitude, accuracy_m,
        captured_at, received_at, expires_at, source
    ) VALUES (
        '00000000-0000-4000-8000-000000031301',
        '00000000-0000-4000-8000-000000031001',
        -6.771500, -79.841000, 4.50,
        TIMESTAMPTZ '2030-04-10 08:01:00+00', TIMESTAMPTZ '2030-04-10 08:01:05+00',
        TIMESTAMPTZ '2030-04-10 08:06:05+00', 'MOBILE_APP'
    ) ON CONFLICT (driver_id) DO UPDATE
        SET latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            accuracy_m = EXCLUDED.accuracy_m,
            captured_at = EXCLUDED.captured_at,
            received_at = EXCLUDED.received_at,
            expires_at = EXCLUDED.expires_at,
            source = EXCLUDED.source;

    SELECT count(*), max(latitude)
      INTO current_rows, current_latitude
      FROM driver_current_location
     WHERE driver_id = '00000000-0000-4000-8000-000000031301';
    IF current_rows <> 1 OR current_latitude <> -6.771500 THEN
        RAISE EXCEPTION 'V8 UPSERT did not preserve exactly one current location';
    END IF;
END
$f31b$;

ROLLBACK;

SELECT 'F3_1B_MIGRATION_PROBE=PASS';
