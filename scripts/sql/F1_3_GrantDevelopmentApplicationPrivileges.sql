\set ON_ERROR_STOP on

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC, rf_test;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, rf_app, rf_test;
REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history FROM PUBLIC, rf_app, rf_test;
REVOKE EXECUTE ON FUNCTION prevent_audit_event_mutation() FROM PUBLIC, rf_app, rf_test;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE organization,
             app_user,
             refresh_token,
             transport_group,
             group_coordinator,
             driver,
             vehicle,
             driver_vehicle_link,
             assignment,
             incident,
             announcement,
             driver_current_location
    TO rf_app;

GRANT SELECT, INSERT ON TABLE audit_event TO rf_app;
