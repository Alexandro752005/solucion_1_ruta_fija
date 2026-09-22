\set ON_ERROR_STOP on

INSERT INTO organization (
    id, legal_name, trade_name, status, timezone, created_at, updated_at
) VALUES
    ('00000000-0000-4000-8000-000000031001', 'Organizacion F3.1B A', 'F3.1B A', 'ACTIVE', 'America/Lima', TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO app_user (
    id, organization_id, email, password_hash, full_name, phone, role, active, last_login_at, created_at, updated_at
) VALUES
    ('00000000-0000-4000-8000-000000031101', '00000000-0000-4000-8000-000000031001', 'admin.f31b@rutafija.fixture', 'fixture-not-for-login', 'Admin F3.1B', NULL, 'ADMIN', TRUE, NULL, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'),
    ('00000000-0000-4000-8000-000000031102', '00000000-0000-4000-8000-000000031001', 'driver.f31b@rutafija.fixture', 'fixture-not-for-login', 'Conductor F3.1B', NULL, 'CONDUCTOR', TRUE, NULL, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO transport_group (
    id, organization_id, name, description, active, created_at, updated_at
) VALUES
    ('00000000-0000-4000-8000-000000031201', '00000000-0000-4000-8000-000000031001', 'Grupo F3.1B', 'Grupo para ensayo de V7 y V8', TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO driver (
    id, organization_id, user_id, group_id, full_name, phone, document_type,
    document_number, license_number, availability_status, location_consent,
    active, created_at, updated_at
) VALUES
    ('00000000-0000-4000-8000-000000031301', '00000000-0000-4000-8000-000000031001', '00000000-0000-4000-8000-000000031102', '00000000-0000-4000-8000-000000031201', 'Conductor F3.1B', NULL, 'DNI', 'F31B-DRIVER-1', 'F31B-LIC-1', 'DISPONIBLE', FALSE, TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'),
    ('00000000-0000-4000-8000-000000031302', '00000000-0000-4000-8000-000000031001', NULL, '00000000-0000-4000-8000-000000031201', 'Conductor Alterno F3.1B', NULL, 'DNI', 'F31B-DRIVER-2', 'F31B-LIC-2', 'DISPONIBLE', FALSE, TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO vehicle (
    id, organization_id, plate, brand, model, year, color, status, active, created_at, updated_at
) VALUES
    ('00000000-0000-4000-8000-000000031401', '00000000-0000-4000-8000-000000031001', 'F31B-01', 'Ruta', 'Uno', 2026, 'Azul', 'DISPONIBLE', TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'),
    ('00000000-0000-4000-8000-000000031402', '00000000-0000-4000-8000-000000031001', 'F31B-02', 'Ruta', 'Dos', 2026, 'Verde', 'DISPONIBLE', TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO driver_vehicle_link (driver_id, vehicle_id, is_primary, active, linked_at) VALUES
    ('00000000-0000-4000-8000-000000031301', '00000000-0000-4000-8000-000000031401', TRUE, TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00'),
    ('00000000-0000-4000-8000-000000031302', '00000000-0000-4000-8000-000000031402', TRUE, TRUE, TIMESTAMPTZ '2030-04-01 00:00:00+00');

INSERT INTO assignment (
    id, organization_id, driver_id, vehicle_id, created_by, status, origin_text,
    destination_text, scheduled_at, scheduled_end_at, reserved_at, started_at,
    completed_at, cancelled_at, cancellation_reason, notes, idempotency_key,
    version, created_at, updated_at
) VALUES (
    '00000000-0000-4000-8000-000000031501',
    '00000000-0000-4000-8000-000000031001',
    '00000000-0000-4000-8000-000000031301',
    '00000000-0000-4000-8000-000000031401',
    '00000000-0000-4000-8000-000000031101',
    'SCHEDULED', 'Origen F3.1B', 'Destino F3.1B',
    TIMESTAMPTZ '2030-04-10 09:00:00+00', TIMESTAMPTZ '2030-04-10 10:00:00+00',
    NULL, NULL, NULL, NULL, NULL, 'Fila V6 que debe conservarse directa', NULL,
    0, TIMESTAMPTZ '2030-04-01 00:00:00+00', TIMESTAMPTZ '2030-04-01 00:00:00+00'
);
