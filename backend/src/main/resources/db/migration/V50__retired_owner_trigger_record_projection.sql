-- V49 remains immutable. A trigger shared by differently shaped records must
-- inspect JSON fields instead of resolving nonexistent NEW.identity_id fields.
CREATE OR REPLACE FUNCTION retention_reject_retired_owner_write() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid UUID;retired BOOLEAN;row_data JSONB;
BEGIN
 row_data:=to_jsonb(NEW);
 IF TG_TABLE_NAME='app_user' THEN
  rid:=(row_data->>'id')::UUID;
  SELECT EXISTS(SELECT 1 FROM retention_personal_retirement WHERE category IN('ADMIN_CONDUCTOR_PII','PERSON_HMAC_LINKS') AND resource_id=rid)
    OR EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id
      WHERE x.category='DOCUMENTARY_CASE' AND q.linked_user_id=rid) INTO retired;
 ELSE
  rid:=(CASE WHEN TG_TABLE_NAME='conductor_onboarding_identity' THEN row_data->>'id' ELSE row_data->>'identity_id' END)::UUID;
  SELECT EXISTS(SELECT 1 FROM retention_personal_retirement x JOIN conductor_onboarding_request q ON q.id=x.resource_id
   WHERE x.category='DOCUMENTARY_CASE' AND q.identity_id=rid) INTO retired;
 END IF;
 IF retired AND NOT retention_personal_row_is_authorized(TG_TABLE_NAME,row_data->>'id',TG_OP)
 THEN RAISE EXCEPTION 'Retired personal record cannot be reactivated or repopulated' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
