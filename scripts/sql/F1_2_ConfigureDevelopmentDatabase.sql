\set ON_ERROR_STOP on

ALTER DATABASE solucion_ruta_fija_1 OWNER TO rf_migrator;
ALTER DATABASE solucion_ruta_fija_1 SET TimeZone TO 'UTC';
REVOKE ALL ON DATABASE solucion_ruta_fija_1 FROM PUBLIC;
REVOKE ALL ON DATABASE solucion_ruta_fija_1 FROM rf_test;
GRANT CONNECT ON DATABASE solucion_ruta_fija_1 TO rf_migrator, rf_app;

ALTER SCHEMA public OWNER TO rf_migrator;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM rf_test;
GRANT USAGE, CREATE ON SCHEMA public TO rf_migrator;
GRANT USAGE ON SCHEMA public TO rf_app;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
