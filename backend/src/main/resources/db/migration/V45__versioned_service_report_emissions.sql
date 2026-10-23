-- L07: additive report snapshots. No data backfill or pilot activation.
CREATE TABLE pilot_lifecycle (
 id VARCHAR(20) PRIMARY KEY CHECK(id='PILOT'), started_at TIMESTAMPTZ NOT NULL,
 ended_at TIMESTAMPTZ NOT NULL CHECK(ended_at=started_at+INTERVAL '7 days'),
 recorded_by UUID NOT NULL REFERENCES app_user(id), recorded_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER pilot_lifecycle_immutable BEFORE UPDATE OR DELETE ON pilot_lifecycle
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();

CREATE TABLE report_emission_series (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organization(id),
 issuer_id UUID NOT NULL REFERENCES app_user(id), own_driver_id UUID REFERENCES driver(id),
 scope VARCHAR(30) NOT NULL CHECK(scope IN('ORGANIZATION','OWN_PARTICIPATION')),
 from_day DATE NOT NULL, to_day DATE NOT NULL CHECK(to_day>=from_day AND to_day-from_day<90),
 zone_id VARCHAR(80) NOT NULL,
 CHECK((scope='OWN_PARTICIPATION')=(own_driver_id IS NOT NULL)),
 UNIQUE(organization_id,issuer_id,scope,from_day,to_day)
);
CREATE TABLE report_emission (
 id UUID PRIMARY KEY, series_id UUID NOT NULL, revision INTEGER NOT NULL CHECK(revision>0),
 previous_emission_id UUID REFERENCES report_emission(id), issued_at TIMESTAMPTZ NOT NULL,
 content_sha256 CHAR(64) NOT NULL CHECK(content_sha256 ~ '^[a-f0-9]{64}$'),
 rule_version VARCHAR(40) NOT NULL, UNIQUE(series_id,revision),
 CHECK((revision=1)=(previous_emission_id IS NULL))
);
-- Ownership and content are separate from the immutable minimum reference.
CREATE TABLE report_emission_content (
 emission_id UUID PRIMARY KEY REFERENCES report_emission(id),
 series_id UUID NOT NULL REFERENCES report_emission_series(id), body_json JSONB NOT NULL
);
CREATE TABLE report_emission_request (
 issuer_id UUID NOT NULL REFERENCES app_user(id), operation_key UUID NOT NULL,
 filters_sha256 CHAR(64) NOT NULL, emission_id UUID NOT NULL REFERENCES report_emission(id),
 PRIMARY KEY(issuer_id,operation_key)
);
CREATE TABLE report_emission_retirement (
 emission_id UUID PRIMARY KEY REFERENCES report_emission(id), retired_at TIMESTAMPTZ NOT NULL,
 policy_version VARCHAR(50) NOT NULL, procedure_id UUID NOT NULL
);
CREATE TRIGGER report_emission_immutable BEFORE UPDATE OR DELETE ON report_emission
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER report_emission_retirement_immutable BEFORE UPDATE OR DELETE ON report_emission_retirement
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER report_emission_content_immutable BEFORE UPDATE OR DELETE ON report_emission_content
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER report_emission_series_immutable BEFORE UPDATE OR DELETE ON report_emission_series
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE TRIGGER report_emission_request_immutable BEFORE UPDATE OR DELETE ON report_emission_request
 FOR EACH ROW EXECUTE FUNCTION service_type_immutable();
CREATE INDEX ix_report_emission_series_revision ON report_emission(series_id,revision DESC);
CREATE FUNCTION report_emission_owner_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.issuer_id AND u.organization_id=NEW.organization_id
   AND ((NEW.scope='ORGANIZATION' AND u.role='ADMIN') OR (NEW.scope='OWN_PARTICIPATION' AND u.role='CONDUCTOR'
    AND EXISTS(SELECT 1 FROM driver d WHERE d.id=NEW.own_driver_id AND d.user_id=u.id AND d.organization_id=u.organization_id))))
 THEN RAISE EXCEPTION 'Report scope requires its same-tenant owner' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER report_emission_owner_guard BEFORE INSERT ON report_emission_series
 FOR EACH ROW EXECUTE FUNCTION report_emission_owner_guard();
