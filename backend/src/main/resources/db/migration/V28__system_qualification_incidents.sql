-- A server-generated validity incident must not impersonate the creator of
-- the assignment or require inventing another user/role/quota.
ALTER TABLE incident DROP CONSTRAINT ck_incident_source;
ALTER TABLE incident ALTER COLUMN reported_by DROP NOT NULL;
ALTER TABLE incident ADD CONSTRAINT ck_incident_source
    CHECK(source IN('CRM_WEB','MOBILE_APP','SYSTEM'));
ALTER TABLE incident ADD CONSTRAINT ck_incident_reporter_origin
    CHECK((source='SYSTEM' AND reported_by IS NULL)
       OR (source IN('CRM_WEB','MOBILE_APP') AND reported_by IS NOT NULL));
COMMENT ON COLUMN incident.reported_by IS
    'Authenticated reporter for CRM/mobile. NULL for a SYSTEM incident, never a substituted human identity.';
