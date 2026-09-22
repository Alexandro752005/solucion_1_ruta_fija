\set ON_ERROR_STOP on

BEGIN;

INSERT INTO organization (id, legal_name, trade_name, status, created_at, updated_at)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'F1.3 APP DML PROBE DELETE',
    'Probe',
    'ACTIVE',
    clock_timestamp(),
    clock_timestamp()
);

SELECT count(*) FROM organization;

UPDATE organization
   SET trade_name = 'Probe actualizado',
       updated_at = clock_timestamp()
 WHERE id = '10000000-0000-0000-0000-000000000001';

DELETE FROM organization
 WHERE id = '10000000-0000-0000-0000-000000000001';

INSERT INTO organization (id, legal_name, trade_name, status, created_at, updated_at)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    'F1.3 APP DML PROBE AUDIT',
    'Probe audit',
    'ACTIVE',
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO app_user (
    id, organization_id, email, password_hash, full_name, role, active, created_at, updated_at
)
VALUES (
    '10000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000002',
    'f13.app.probe@example.test',
    'not-a-real-password-hash',
    'F1.3 App Probe',
    'ADMIN',
    TRUE,
    clock_timestamp(),
    clock_timestamp()
);

INSERT INTO audit_event (
    id, organization_id, user_id, action, entity_type, entity_id, occurred_at
)
VALUES (
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    'F1_3_DML_PROBE',
    'ORGANIZATION',
    '10000000-0000-0000-0000-000000000002',
    clock_timestamp()
);

ROLLBACK;

SELECT 'F1_3_APP_DML=PASS';
