-- Candidate V6 validated only by F2.1B on an isolated V5 restoration.
-- It moves unchanged to backend/src/main/resources/db/migration in F2.2,
-- together with the backend and CRM code that understands ADMIN.

ALTER TABLE app_user
    DROP CONSTRAINT ck_app_user_role;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (
        role IN ('SUPER_ADMIN', 'ADMINISTRADOR', 'COORDINADOR', 'ADMIN', 'CONDUCTOR')
    );

UPDATE refresh_token
   SET revoked_at = CURRENT_TIMESTAMP
 WHERE revoked_at IS NULL
   AND user_id IN (
       SELECT id
         FROM app_user
        WHERE role IN ('ADMINISTRADOR', 'COORDINADOR')
   );

UPDATE app_user
   SET role = 'ADMIN',
       updated_at = CURRENT_TIMESTAMP
 WHERE role IN ('ADMINISTRADOR', 'COORDINADOR');

ALTER TABLE app_user
    DROP CONSTRAINT ck_app_user_role;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (
        role IN ('SUPER_ADMIN', 'ADMIN', 'CONDUCTOR')
    );

