-- F1.4: este archivo solo se ejecuta bajo el perfil Spring "test".
-- Nunca se autoriza contra desarrollo ni recuperación.
DO $$
BEGIN
    IF current_database() <> 'ruta_fija_test' THEN
        RAISE EXCEPTION 'La configuración de pruebas solo puede modificar ruta_fija_test';
    END IF;
END
$$;

REVOKE CREATE ON SCHEMA public FROM rf_test;
GRANT USAGE ON SCHEMA public TO rf_test;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rf_test;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rf_test;

-- Las migraciones siguientes también serán propiedad de rf_migrator y deberán
-- mantener al rol de pruebas con DML, nunca DDL.
ALTER DEFAULT PRIVILEGES FOR ROLE rf_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rf_test;
ALTER DEFAULT PRIVILEGES FOR ROLE rf_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO rf_test;
