"""Publish the already approved immutable document in the native local DB only.

No acceptance, migration, account change, or modification of the protected test DB.
Credentials are inherited from the PowerShell wrapper, never command arguments.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
PG = Path('C:/Program Files/PostgreSQL/16/bin')
SHA = '20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417'
APPROVAL_SHA = 'ea0348497927ce671a2a9ce91144226c4d835c38c9b2bc75697c3218132774b1'
BASE_SHA = '3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b'
DB = 'solucion_ruta_fija_1'
EXPECTED_VERSIONS = ','.join(str(n) for n in range(1, 52))
COMMITTED = False


def require(condition, reason):
    if not condition:
        raise RuntimeError(reason)


def save(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def pg_env(test=False):
    env = os.environ.copy()
    key = 'RF_PUBLICATION_TEST_PASSWORD' if test else 'RF_PUBLICATION_PASSWORD'
    require(bool(env.get(key)), 'PRIVATE_DATABASE_CREDENTIAL_MISSING')
    env['PGPASSWORD'] = env[key]
    env['PGCONNECT_TIMEOUT'] = '5'
    env['PGCLIENTENCODING'] = 'UTF8'
    env['PGOPTIONS'] = '-c statement_timeout=30000 -c lock_timeout=5000'
    if test:
        env['PGOPTIONS'] += ' -c default_transaction_read_only=on'
    return env


def sql(query, test=False):
    result = subprocess.run(
        [str(PG / 'psql.exe'), '-X', '-w', '-q', '-At', '-v', 'ON_ERROR_STOP=1',
         '-h', '127.0.0.1', '-p', '5432', '-U', 'rf_migrator',
         '-d', 'ruta_fija_test' if test else DB],
        input=query, encoding='utf-8', capture_output=True, env=pg_env(test), timeout=60)
    # SQL errors can echo the document: expose only a fixed failure code.
    require(result.returncode == 0, 'POSTGRESQL_COMMAND_REJECTED_NO_AUTOMATIC_RETRY')
    return result.stdout.strip()


def snapshot(test=False):
    query = r"""
BEGIN READ ONLY ISOLATION LEVEL REPEATABLE READ;
SELECT format($statement$
 SELECT json_build_object('table',%L,'rows',count(*),
 'fingerprint',md5(coalesce(string_agg(h,'' ORDER BY h),'')))
 FROM (SELECT md5(to_jsonb(t)::text) h FROM %I.%I t) fingerprints;
 $statement$,tablename,schemaname,tablename)
 FROM pg_tables WHERE schemaname='public' ORDER BY tablename
\gexec
COMMIT;
"""
    return [json.loads(line) for line in sql(query, test).splitlines() if line.strip()]


def observation():
    return json.loads(sql("""
BEGIN READ ONLY;
SELECT json_build_object('database',current_database(),'role',current_user,
 'versions',(SELECT string_agg(version,',' ORDER BY installed_rank)
   FROM flyway_schema_history WHERE success AND version IS NOT NULL),
 'successfulMigrations',(SELECT count(*) FROM flyway_schema_history WHERE success),
 'failedMigrations',(SELECT count(*) FROM flyway_schema_history WHERE NOT success),
 'acceptances',(SELECT count(*) FROM admin_terms_acceptance),
 'documents',coalesce((SELECT json_agg(row_to_json(d)) FROM (
   SELECT id,document_key,version,content_sha256,synthetic,created_at,
     content_sha256=encode(sha256(convert_to(content,'UTF8')),'hex') AS hash_valid
   FROM admin_terms_document ORDER BY created_at DESC,id DESC) d),'[]'::json));
COMMIT;
"""))


def validate_source():
    folder = ROOT / 'docs/legal/publicado'
    source = folder / 'terminos-admin-v1.0.0.md'
    approval_path = folder / 'terminos-admin-v1.0.0.aprobacion.json'
    require(sha(source) == SHA and sha(approval_path) == APPROVAL_SHA,
            'IMMUTABLE_SOURCE_OR_APPROVAL_CHANGED')
    approval = json.loads(approval_path.read_text(encoding='utf-8-sig'))
    content = source.read_bytes()
    require(approval['documentKey'] == 'ADMIN_TERMS' and approval['version'] == '1.0.0'
            and approval['decision'] == 'APROBADO_PARA_PUBLICACION_LOCAL_INMUTABLE'
            and approval['contentSha256'].lower() == SHA
            and approval['contentBytes'] == len(content)
            and approval['adminAcceptanceGranted'] is False
            and approval['L02Authorized'] is False, 'PUBLICATION_APPROVAL_MISMATCH')
    require(sha(ROOT / 'backups/L-00-2026-10-13-final3/source-snapshot.zip') == BASE_SHA,
            'IMMUTABLE_BASE_SNAPSHOT_CHANGED')
    return content


def validate_observation(observed, required=False):
    require(observed['database'] == DB and observed['role'] == 'rf_migrator'
            and observed['versions'] == EXPECTED_VERSIONS
            and observed['successfulMigrations'] == 51 and observed['failedMigrations'] == 0,
            'NATIVE_DATABASE_OR_V51_SCHEMA_REJECTED')
    documents = observed['documents']
    if not documents:
        require(not required, 'APPROVED_TERMS_NOT_PUBLISHED')
        return False
    require(len(documents) == 1 and documents[0]['document_key'] == 'ADMIN_TERMS'
            and documents[0]['version'] == '1.0.0' and documents[0]['content_sha256'] == SHA
            and documents[0]['synthetic'] is False and documents[0]['hash_valid'] is True,
            'EXISTING_IMMUTABLE_DOCUMENT_CONFLICT_REQUIRES_REVIEW')
    return True


def publish(content, prior_acceptances):
    encoded = base64.b64encode(content).decode('ascii')
    document_id = str(uuid4())
    audit_id = str(uuid4())
    sql("""
BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT pg_advisory_xact_lock(751846,2);
DO $publication$
DECLARE
 approved_content text := convert_from(decode('%s','base64'),'UTF8');
 existing admin_terms_document%%ROWTYPE;
BEGIN
 IF current_database()<>'solucion_ruta_fija_1' OR current_user<>'rf_migrator' THEN
   RAISE EXCEPTION 'WRONG_DATABASE_OR_ROLE'; END IF;
 IF (SELECT count(*) FROM flyway_schema_history WHERE success)<>51
   OR EXISTS(SELECT 1 FROM flyway_schema_history WHERE NOT success)
   OR (SELECT string_agg(version,',' ORDER BY installed_rank)
       FROM flyway_schema_history WHERE success AND version IS NOT NULL)<>'%s' THEN
   RAISE EXCEPTION 'V51_REQUIRED'; END IF;
 IF encode(sha256(convert_to(approved_content,'UTF8')),'hex')<>'%s' THEN
   RAISE EXCEPTION 'SOURCE_HASH_MISMATCH'; END IF;
 SELECT * INTO existing FROM admin_terms_document
   WHERE document_key='ADMIN_TERMS' AND version='1.0.0';
 IF FOUND THEN
   IF existing.content<>approved_content OR existing.content_sha256<>'%s' OR existing.synthetic THEN
     RAISE EXCEPTION 'IMMUTABLE_VERSION_CONFLICT'; END IF;
 ELSE
   IF EXISTS(SELECT 1 FROM admin_terms_document) THEN
     RAISE EXCEPTION 'UNEXPECTED_DOCUMENT_HISTORY'; END IF;
   INSERT INTO admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at)
     VALUES('%s','ADMIN_TERMS','1.0.0',approved_content,'%s',false,current_timestamp);
   INSERT INTO audit_event(id,organization_id,user_id,action,entity_type,entity_id,correlation_id,metadata_json,occurred_at)
     VALUES('%s',NULL,NULL,'OWNER_AUTHORIZED_LOCAL_ADMIN_TERMS_PUBLICATION',
       'ADMIN_TERMS_DOCUMENT','%s','%s',
       jsonb_build_object('scope','NATIVE_LOCAL','version','1.0.0','contentSha256','%s',
        'approvalSource','docs/legal/publicado/terminos-admin-v1.0.0.aprobacion.json',
        'personalAcceptanceGranted',false,'migrationsExecuted',false),current_timestamp);
 END IF;
 IF (SELECT count(*) FROM admin_terms_acceptance)<>%d THEN
   RAISE EXCEPTION 'ACCEPTANCES_CHANGED_DURING_PUBLICATION'; END IF;
END
$publication$;
COMMIT;
""" % (encoded, EXPECTED_VERSIONS, SHA, SHA, document_id, SHA,
       audit_id, document_id, audit_id, SHA, prior_acceptances))


def main():
    global COMMITTED
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--check', action='store_true')
    mode.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    require(os.environ.get('RF_PUBLICATION_URL') ==
            'jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1', 'LOCAL_URL_REQUIRED')
    content = validate_source()
    before = observation()
    already_present = validate_observation(before)
    if args.check:
        print('ADMIN_TERMS_LOCAL_CHECK=' + ('PASS_PUBLISHED' if already_present else 'PENDING_PUBLICATION'))
        print('DATABASE=solucion_ruta_fija_1; VERSION=1.0.0; V51=PASS; ACCEPTANCES=' + str(before['acceptances']))
        return

    stamp = datetime.now().astimezone().strftime('%Y%m%d_%H%M%S') + '-' + uuid4().hex[:8]
    private = ROOT / 'backend/.local/accesos' / ('publicacion-terminos-' + stamp)
    private.mkdir(parents=False, exist_ok=False)
    before_native = snapshot()
    before_test = snapshot(True)
    save(private / 'before-native.json', before_native)
    save(private / 'before-protected-test.json', before_test)
    save(private / 'before-observation.json', before)
    backup = private / 'pre-publicacion-terminos.dump'
    dumped = subprocess.run([str(PG / 'pg_dump.exe'), '-w', '-h', '127.0.0.1', '-p', '5432',
                             '-U', 'rf_migrator', '-F', 'c', '-b', '-f', str(backup), DB],
                            env=pg_env(), capture_output=True, timeout=120)
    require(dumped.returncode == 0 and backup.is_file() and backup.stat().st_size > 0,
            'PREVENTIVE_BACKUP_FAILED_NO_PUBLICATION')
    listing = subprocess.run([str(PG / 'pg_restore.exe'), '--list', str(backup)],
                             capture_output=True, timeout=30)
    require(listing.returncode == 0 and b'admin_terms_document' in listing.stdout,
            'BACKUP_ARCHIVE_NOT_READABLE_NO_PUBLICATION')
    publish(content, before['acceptances'])
    COMMITTED = True
    save(private / 'transaction-result.json', dict(publicationCommitted=True,
         inserted=not already_present, acceptancesCreated=0,
         committedAtUtc=datetime.now(timezone.utc).isoformat()))
    after = observation()
    validate_observation(after, required=True)
    after_native = snapshot()
    after_test = snapshot(True)
    save(private / 'after-native.json', after_native)
    save(private / 'after-protected-test.json', after_test)
    save(private / 'after-observation.json', after)
    changed = [row['table'] for row, later in zip(before_native, after_native) if row != later]
    require([r['table'] for r in before_native] == [r['table'] for r in after_native]
            and set(changed) <= {'admin_terms_document', 'audit_event'},
            'UNEXPECTED_NATIVE_TABLE_CHANGE_REQUIRES_REVIEW')
    require(before_test == after_test, 'PROTECTED_TEST_CHANGED_REQUIRES_REVIEW')
    require(after['acceptances'] == before['acceptances'], 'PERSONAL_ACCEPTANCES_CHANGED')
    validate_source()
    result = dict(result='PASS', verifiedAtUtc=datetime.now(timezone.utc).isoformat(),
                  database=DB, version='1.0.0', documentId=after['documents'][0]['id'],
                  contentSha256=SHA, sourceBytes=len(content), synthetic=False,
                  inserted=not already_present, migrations=51, acceptanceCount=after['acceptances'],
                  acceptancesCreated=0, changedTables=changed, protectedTestUnchanged=True,
                  protectedTables=len(after_test), backup=str(backup.relative_to(ROOT)).replace('\\', '/'),
                  backupBytes=backup.stat().st_size, backupSha256=sha(backup),
                  backupArchiveListReadable=True, immutableSourceAndBaseSnapshotUnchanged=True)
    save(private / 'result.json', result)
    evidence = ROOT / 'docs/evidencia/operacion-local' / ('publicacion-terminos-' + stamp)
    evidence.mkdir(parents=True, exist_ok=False)
    save(evidence / 'resultado.json', result)
    print('ADMIN_TERMS_LOCAL_PUBLICATION=PASS; NEW_DOCUMENT=' + str(not already_present).lower())
    print('DATABASE=solucion_ruta_fija_1; VERSION=1.0.0; ACCEPTANCES_CREATED=0; PROTECTED_TEST=UNCHANGED')
    print('EVIDENCE=' + str(evidence.relative_to(ROOT)))


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        status = 'COMMITTED_VERIFICATION_INCOMPLETE' if COMMITTED else 'FAILED_BEFORE_COMMIT'
        print('ADMIN_TERMS_LOCAL_PUBLICATION=' + status + '; ' + type(failure).__name__ + ': ' + str(failure), file=sys.stderr)
        sys.exit(1)
