-- ID-01: the same reviewed DNI identifies one stable account across all three roles.
-- HMACs are pseudonymous identifiers. No ADMIN/SUPER_ADMIN DNI is stored in clear.
CREATE TABLE person_identity (
    person_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
    document_type VARCHAR(12) NOT NULL CHECK (document_type = 'DNI'),
    reviewed_by_user_id UUID NOT NULL REFERENCES app_user(id),
    review_method VARCHAR(32) NOT NULL CHECK (review_method IN
        ('OWNER_SELF_REVIEW','IN_PERSON','PRIOR_SECURE_CONTACT','CONDUCTOR_DOSSIER')),
    review_reference VARCHAR(160) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_person_identity_hash CHECK (person_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_person_identity_reference CHECK (length(trim(review_reference)) > 0)
);

CREATE TABLE conductor_common_person_bridge (
    person_hash CHAR(64) PRIMARY KEY REFERENCES conductor_person_binding(person_hash),
    common_person_hash CHAR(64) NOT NULL UNIQUE REFERENCES person_identity(person_hash),
    CONSTRAINT ck_conductor_common_same_hash CHECK (person_hash = common_person_hash)
);

-- Migrate only explicit reviewed, linked conductor identities. Missing ADMIN or
-- SUPER_ADMIN evidence remains missing; name/email/role never invents a DNI.
INSERT INTO person_identity
    (person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
SELECT binding.person_hash,binding.user_id,'DNI',request.reviewed_by_admin_id,
       'CONDUCTOR_DOSSIER','Existing reviewed onboarding request ' || request.id,
       request.personal_approved_at
  FROM conductor_person_binding binding
  JOIN conductor_onboarding_request request
    ON request.identity_id=binding.identity_id AND request.linked_user_id=binding.user_id
   AND request.linked_driver_id=binding.driver_id
  JOIN driver ON driver.id=binding.driver_id AND driver.user_id=binding.user_id
 WHERE request.personal_approved_at IS NOT NULL AND request.reviewed_by_admin_id IS NOT NULL
   AND upper(driver.document_type)='DNI' AND driver.document_number ~ '^[0-9]{8}$';

INSERT INTO conductor_common_person_bridge(person_hash,common_person_hash)
SELECT binding.person_hash,binding.person_hash
  FROM conductor_person_binding binding JOIN person_identity person ON person.person_hash=binding.person_hash;

CREATE TABLE person_identity_hash_key (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    key_fingerprint CHAR(64) NOT NULL CHECK (key_fingerprint ~ '^[0-9a-f]{64}$'),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE FUNCTION protect_common_person_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Person identity binding requires a separate authorized correction procedure' USING ERRCODE='23000';
END;
$$;
CREATE TRIGGER protect_person_identity BEFORE UPDATE OR DELETE ON person_identity
    FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_person_identity_key BEFORE UPDATE OR DELETE ON person_identity_hash_key
    FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
CREATE TRIGGER protect_conductor_common_bridge BEFORE UPDATE OR DELETE ON conductor_common_person_bridge
    FOR EACH ROW EXECUTE FUNCTION protect_common_person_identity();
