-- L06. No reconstruction from today's group membership for legacy announcements.
ALTER TABLE announcement
 ADD COLUMN operation_key UUID,
 ADD COLUMN payload_sha256 CHAR(64),
 ADD CONSTRAINT uq_announcement_tenant UNIQUE(id,organization_id),
 ADD CONSTRAINT ck_announcement_operation_pair CHECK((operation_key IS NULL)=(payload_sha256 IS NULL));
CREATE UNIQUE INDEX uq_announcement_publish_key ON announcement(organization_id,operation_key) WHERE operation_key IS NOT NULL;

CREATE TABLE announcement_recipient (
 announcement_id UUID NOT NULL, organization_id UUID NOT NULL REFERENCES organization(id),
 user_id UUID NOT NULL REFERENCES app_user(id), driver_id UUID NOT NULL REFERENCES driver(id),
 group_at_publication UUID REFERENCES transport_group(id),
 provenance VARCHAR(30) NOT NULL CHECK(provenance IN('PUBLISH_SNAPSHOT','LEGACY_RECEIPT')),
 assigned_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 withdrawn_at TIMESTAMPTZ, withdrawn_by UUID REFERENCES app_user(id), withdrawal_reason VARCHAR(300),
 withdrawal_key UUID, withdrawal_sha256 CHAR(64),
 PRIMARY KEY(announcement_id,user_id),
 FOREIGN KEY(announcement_id,organization_id) REFERENCES announcement(id,organization_id),
 CHECK(version>=0),
 CHECK((withdrawn_at IS NULL AND withdrawn_by IS NULL AND withdrawal_reason IS NULL AND withdrawal_key IS NULL AND withdrawal_sha256 IS NULL)
    OR (withdrawn_at IS NOT NULL AND withdrawn_by IS NOT NULL AND length(trim(withdrawal_reason))>0 AND withdrawal_key IS NOT NULL AND withdrawal_sha256 IS NOT NULL))
);
CREATE INDEX ix_announcement_recipient_user ON announcement_recipient(user_id,assigned_at DESC);
CREATE UNIQUE INDEX uq_announcement_withdraw_key ON announcement_recipient(organization_id,withdrawal_key) WHERE withdrawal_key IS NOT NULL;

-- Only a pre-existing receipt proves that a user received an old announcement.
INSERT INTO announcement_recipient(announcement_id,organization_id,user_id,driver_id,group_at_publication,provenance,assigned_at)
 SELECT a.id,a.organization_id,r.user_id,d.id,a.audience_id,'LEGACY_RECEIPT',r.delivered_at
 FROM announcement_receipt r JOIN announcement a ON a.id=r.announcement_id
 JOIN app_user u ON u.id=r.user_id AND u.organization_id=a.organization_id AND u.role='CONDUCTOR'
 JOIN driver d ON d.user_id=u.id AND d.organization_id=a.organization_id;

CREATE FUNCTION announcement_recipient_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Recipient history must be retained' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' THEN
  IF NOT EXISTS(SELECT 1 FROM app_user u JOIN driver d ON d.user_id=u.id AND d.id=NEW.driver_id
    WHERE u.id=NEW.user_id AND u.role='CONDUCTOR' AND u.organization_id=NEW.organization_id AND d.organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Recipient tenant or role mismatch' USING ERRCODE='23514'; END IF;
  IF NEW.group_at_publication IS NOT NULL AND NOT EXISTS(SELECT 1 FROM transport_group g WHERE g.id=NEW.group_at_publication AND g.organization_id=NEW.organization_id)
  THEN RAISE EXCEPTION 'Recipient group tenant mismatch' USING ERRCODE='23514'; END IF;
 ELSE
  IF (NEW.announcement_id,NEW.organization_id,NEW.user_id,NEW.driver_id,NEW.group_at_publication,NEW.provenance,NEW.assigned_at)
    IS DISTINCT FROM (OLD.announcement_id,OLD.organization_id,OLD.user_id,OLD.driver_id,OLD.group_at_publication,OLD.provenance,OLD.assigned_at)
  THEN RAISE EXCEPTION 'Original recipient snapshot is immutable' USING ERRCODE='23514'; END IF;
  IF OLD.withdrawn_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Security withdrawal cannot be undone' USING ERRCODE='23514'; END IF;
  IF NEW.withdrawn_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.withdrawn_by AND u.organization_id=NEW.organization_id AND u.role='ADMIN' AND u.active)
  THEN RAISE EXCEPTION 'Withdrawal requires an active same-tenant ADMIN' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER announcement_recipient_guard BEFORE INSERT OR UPDATE OR DELETE ON announcement_recipient
 FOR EACH ROW EXECUTE FUNCTION announcement_recipient_guard();

CREATE FUNCTION announcement_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Announcement receipts are retained' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM announcement_recipient r JOIN app_user u ON u.id=r.user_id
   WHERE r.announcement_id=NEW.announcement_id AND r.user_id=NEW.user_id AND r.withdrawn_at IS NULL
   AND u.organization_id=r.organization_id AND u.role='CONDUCTOR' AND u.active)
 THEN RAISE EXCEPTION 'Receipt requires a current authorized recipient' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND ((NEW.announcement_id,NEW.user_id,NEW.delivered_at) IS DISTINCT FROM (OLD.announcement_id,OLD.user_id,OLD.delivered_at)
   OR (OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at))
 THEN RAISE EXCEPTION 'Receipt timestamps cannot be rewritten' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER announcement_receipt_guard BEFORE INSERT OR UPDATE OR DELETE ON announcement_receipt
 FOR EACH ROW EXECUTE FUNCTION announcement_receipt_guard();

CREATE FUNCTION announcement_original_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Published announcements are immutable; withdraw access for security instead' USING ERRCODE='23514';
END $$;
CREATE TRIGGER announcement_original_guard BEFORE UPDATE OR DELETE ON announcement
 FOR EACH ROW EXECUTE FUNCTION announcement_original_guard();

CREATE TABLE operation_notice (
 id UUID PRIMARY KEY, event_id UUID NOT NULL, organization_id UUID NOT NULL REFERENCES organization(id),
 user_id UUID NOT NULL REFERENCES app_user(id), event_type VARCHAR(80) NOT NULL,
 references_json JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, read_at TIMESTAMPTZ,
 external_status VARCHAR(30) NOT NULL CHECK(external_status IN('NOT_REQUIRED','NOT_CONFIGURED','PROVIDER_ACCEPTED','FAILED')),
 external_reason VARCHAR(60) NOT NULL,
 UNIQUE(event_id,user_id), CHECK(read_at IS NULL OR read_at>=created_at)
);
CREATE INDEX ix_operation_notice_inbox ON operation_notice(user_id,created_at DESC,id);
CREATE FUNCTION operation_notice_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Notice history must be retained' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT 1 FROM app_user u WHERE u.id=NEW.user_id AND u.organization_id=NEW.organization_id AND u.role IN('ADMIN','CONDUCTOR'))
 THEN RAISE EXCEPTION 'Notice recipient tenant mismatch' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND ((NEW.id,NEW.event_id,NEW.organization_id,NEW.user_id,NEW.event_type,NEW.references_json,NEW.created_at,NEW.external_status,NEW.external_reason)
   IS DISTINCT FROM (OLD.id,OLD.event_id,OLD.organization_id,OLD.user_id,OLD.event_type,OLD.references_json,OLD.created_at,OLD.external_status,OLD.external_reason)
   OR (OLD.read_at IS NOT NULL AND NEW.read_at IS DISTINCT FROM OLD.read_at))
 THEN RAISE EXCEPTION 'Notice original and confirmed reading are immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER operation_notice_guard BEFORE INSERT OR UPDATE OR DELETE ON operation_notice
 FOR EACH ROW EXECUTE FUNCTION operation_notice_guard();
