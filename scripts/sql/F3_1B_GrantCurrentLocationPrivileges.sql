\set ON_ERROR_STOP on

REVOKE ALL PRIVILEGES ON TABLE driver_current_location FROM PUBLIC, rf_test;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE driver_current_location TO rf_app;
