\set ON_ERROR_STOP on

ALTER DATABASE ruta_fija_test OWNER TO rf_migrator;
ALTER DATABASE ruta_fija_test SET TimeZone TO 'UTC';
REVOKE ALL ON DATABASE ruta_fija_test FROM PUBLIC;
REVOKE ALL ON DATABASE ruta_fija_test FROM rf_app;
GRANT CONNECT ON DATABASE ruta_fija_test TO rf_migrator, rf_test;

ALTER SCHEMA public OWNER TO rf_migrator;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM rf_app;
GRANT USAGE, CREATE ON SCHEMA public TO rf_migrator;
GRANT USAGE ON SCHEMA public TO rf_test;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
