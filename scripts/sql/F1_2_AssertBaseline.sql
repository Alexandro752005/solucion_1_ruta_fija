\set ON_ERROR_STOP on

SELECT current_database(),
       (SELECT count(*)
          FROM pg_tables
         WHERE schemaname NOT IN ('pg_catalog', 'information_schema')),
       (to_regclass('public.flyway_schema_history') IS NULL),
       (SELECT count(*)
          FROM pg_roles
         WHERE rolname IN ('rf_migrator', 'rf_app', 'rf_test')),
       (NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ruta_fija_test')),
       (EXISTS (SELECT 1
                  FROM pg_database
                 WHERE datname = 'ruta_fija_recovery_20260919_f04'));
