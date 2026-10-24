import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from datetime import datetime, timezone

ROOT = Path.cwd()
RUN = ROOT / '.runtime/migracion-nativa-v50-20261023_1714'
PG = Path('C:/Program Files/PostgreSQL/16/bin')
NATIVE = 'solucion_ruta_fija_1'
CLONE = 'rf_v50_rehearsal_20261023_1714'

def save(name, value):
    (RUN / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

def load(name):
    return json.loads((RUN / name).read_text(encoding='utf-8-sig'))

def call(args, label, *, sql=None, bootstrap=False, database=NATIVE):
    env = os.environ.copy()
    key = 'RUTA_FIJA_BOOTSTRAP_PASSWORD' if bootstrap else 'SPRING_FLYWAY_PASSWORD'
    env['PGPASSWORD'] = env[key]
    env['PGCONNECT_TIMEOUT'] = '10'
    env['PGTZ'] = 'UTC'
    env['RF_V50_DATABASE'] = database
    start = time.monotonic()
    result = subprocess.run([str(a) for a in args], input=sql, text=True, encoding='utf-8', errors='replace',
                            capture_output=True, env=env, timeout=240)
    suffix = ''
    attempt = 1
    while (RUN / f'{label}{suffix}.stdout.log').exists():
        attempt += 1
        suffix = f'-attempt{attempt}'
    (RUN / f'{label}{suffix}.stdout.log').write_text(result.stdout, encoding='utf-8')
    (RUN / f'{label}{suffix}.stderr.log').write_text(result.stderr, encoding='utf-8')
    if result.returncode:
        print(result.stderr[-2500:])
        print(result.stdout[-2500:])
        raise RuntimeError(f'{label}: exit={result.returncode}')
    print(f'{label}=PASS seconds={time.monotonic()-start:.3f}', flush=True)
    return result.stdout.strip()

def sql(database, query, label, bootstrap=False):
    user = os.environ['RUTA_FIJA_BOOTSTRAP_USERNAME'] if bootstrap else 'rf_migrator'
    return call([PG/'psql.exe', '-X', '-w', '-q', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', '5432',
                 '-U', user, '-d', database, '-At', '-f', '-'], label, sql=query, bootstrap=bootstrap)

def history(db, label):
    return json.loads(sql(db, "SELECT json_build_object('database',current_database(),'versions',"
        "(SELECT json_agg(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version IS NOT NULL),"
        "'failed',(SELECT count(*) FROM flyway_schema_history WHERE NOT success),'repeatables',"
        "(SELECT count(*) FROM flyway_schema_history WHERE version IS NULL),'total',(SELECT count(*) FROM flyway_schema_history));", label))

def fingerprint(db, label, original=None):
    if original is None:
        meta = json.loads(sql(db, "SELECT json_object_agg(table_name,cols) FROM (SELECT table_name,json_agg(column_name ORDER BY ordinal_position) cols "
            "FROM information_schema.columns WHERE table_schema='public' AND table_name<>'flyway_schema_history' GROUP BY table_name) s;", label+'-columns'))
    else:
        meta = {n:r['columns'] for n,r in original.items()}
    commands = ['BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;', "SET LOCAL TIME ZONE 'UTC';"]
    for name, cols in sorted(meta.items()):
        assert re.fullmatch('[a-z_][a-z_0-9]*', name)
        assert all(re.fullmatch('[a-z_][a-z_0-9]*', c) for c in cols)
        projection = ','.join(f"'{c}',t.\"{c}\"" for c in cols)
        rowhash = f"encode(sha256(convert_to(jsonb_build_object({projection})::text,'UTF8')),'hex')"
        commands.append(f"SELECT '{name}|'||count(*)||'|'||encode(sha256(convert_to(coalesce(string_agg({rowhash},'' ORDER BY {rowhash}),''),'UTF8')),'hex') FROM public.\"{name}\" t;")
    commands.append('COMMIT;')
    result = {}
    for line in sql(db, '\n'.join(commands), label+'-rows').splitlines():
        name, count, sha = line.split('|')
        result[name] = dict(columns=meta[name], rows=int(count), sha256=sha)
    assert len(result) == len(meta)
    save(label+'.json', result)
    return result

def migrate(db, label):
    before = history(db, label+'-history-before')
    assert before['versions'] == [str(i) for i in range(1,13)] and before['failed'] == before['repeatables'] == 0
    cp = (ROOT/'backend/target/f1-3-flyway-classpath.txt').read_text(encoding='utf-8-sig').strip()
    assert all(Path(p).is_file() for p in cp.split(';'))
    java = Path('C:/Program Files/Java/jdk-21.0.10/bin/java.exe')
    call([java, '-Xms32m', '-Xmx192m', '--class-path', cp, RUN/'MigrateNativeV50.java'], label, database=db)
    after = history(db, label+'-history-after')
    assert after['versions'] == [str(i) for i in range(1,51)] and after['total'] == 50 and after['failed'] == after['repeatables'] == 0
    save(label+'-history.json', after)

def grants(db, label):
    readonly = ['flyway_schema_history','pilot_account_creation','retention_policy_catalog','retention_audit_hold_request',
        'retention_audit_hold_approval','retention_plan','retention_plan_item','retention_execution','retention_controlled_copy',
        'retention_copy_evidence','retention_personal_delegation','retention_personal_plan_delegation',
        'retention_personal_hold_request','retention_personal_hold_approval','retention_personal_retirement','retention_personal_capability']
    hidden = ['retention_execution_authority','retention_personal_row_authority']
    query = ['BEGIN;', 'GRANT USAGE ON SCHEMA public TO rf_app;',
        'GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO rf_app;',
        'GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public TO rf_app;']
    query += [f'REVOKE INSERT,UPDATE,DELETE,TRUNCATE ON public.{t} FROM rf_app;' for t in readonly]
    query += [f'REVOKE ALL ON public.{t} FROM rf_app;' for t in hidden]
    query += ['COMMIT;']
    (RUN/'grant-rf-app-preserving-security.sql').write_text('\n'.join(query)+'\n', encoding='utf-8')
    sql(db, '\n'.join(query), label)
    observation = json.loads(sql(db, "SELECT json_object_agg(tablename,json_build_object('select',has_table_privilege('rf_app','public.'||tablename,'SELECT'),"
        "'insert',has_table_privilege('rf_app','public.'||tablename,'INSERT'),'update',has_table_privilege('rf_app','public.'||tablename,'UPDATE'),"
        "'delete',has_table_privilege('rf_app','public.'||tablename,'DELETE'),'truncate',has_table_privilege('rf_app','public.'||tablename,'TRUNCATE'))) FROM pg_tables WHERE schemaname='public';", label+'-verification'))
    for name, values in observation.items():
        if name in hidden:
            assert not any(values.values()), name
        elif name in readonly:
            assert values['select'] and not any(values[k] for k in ['insert','update','delete','truncate']), name
        else:
            assert all(values[k] for k in ['select','insert','update','delete']), name
    save(label+'.json', dict(tables=len(observation), readonlyTables=readonly, authorityTablesNoAccess=hidden,
                            operationalDmlTables=len(observation)-len(readonly)-len(hidden), privileges=observation))

def main(action):
    assert os.environ['SPRING_FLYWAY_USERNAME'] == 'rf_migrator'
    assert os.environ['SPRING_FLYWAY_URL'] == 'jdbc:postgresql://127.0.0.1:5432/'+NATIVE
    if action == 'backup':
        assert history(NATIVE,'native-preflight')['versions'] == [str(i) for i in range(1,13)]
        fingerprint(NATIVE,'native-before')
        stamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        backup = ROOT/f'backups/solucion_ruta_fija_1_pre_v50_{stamp}.dump'
        assert not backup.exists()
        call([PG/'pg_dump.exe','-w','-h','127.0.0.1','-p','5432','-U','rf_migrator','-F','c','-b','-v','-f',backup,NATIVE], 'backup')
        assert backup.stat().st_size > 0
        call([PG/'pg_restore.exe','--list',backup], 'backup-list')
        record=dict(path=str(backup),bytes=backup.stat().st_size,sha256=hashlib.sha256(backup.read_bytes()).hexdigest(),
                    createdAtUtc=datetime.now(timezone.utc).isoformat(),format='PostgreSQL custom full database',database=NATIVE)
        save('backup.json', record)
        print(json.dumps(record))
    elif action == 'rehearse':
        backup = load('backup.json')
        assert hashlib.sha256(Path(backup['path']).read_bytes()).hexdigest() == backup['sha256']
        exists = sql('postgres',f"SELECT count(*) FROM pg_database WHERE datname='{CLONE}';",'clone-absence',True)
        if exists == '0':
            sql('postgres',f'CREATE DATABASE {CLONE} OWNER rf_migrator TEMPLATE template0;', 'clone-create',True)
            sql('postgres',f'REVOKE CONNECT ON DATABASE {CLONE} FROM PUBLIC; GRANT CONNECT ON DATABASE {CLONE} TO rf_migrator,rf_app;', 'clone-isolate',True)
            call([PG/'pg_restore.exe','-w','-h','127.0.0.1','-p','5432','-U','rf_migrator','-d',CLONE,'--exit-on-error','--single-transaction',backup['path']], 'clone-restore')
        else:
            assert exists == '1'
            assert history(CLONE,'clone-resume-history')['versions'] == [str(i) for i in range(1,13)]
        baseline=load('native-before.json')
        assert fingerprint(CLONE,'clone-before',baseline) == baseline
        migrate(CLONE,'clone-migrate')
        assert fingerprint(CLONE,'clone-after',baseline) == baseline
        grants(CLONE,'clone-grants')
        save('rehearsal.json',dict(result='PASS_RESTORED_V12_MIGRATED_TO_V50_ORIGINAL_DATA_PRESERVED',database=CLONE,
            applied=38,total=50,preservedTables=len(baseline),preservedRows=sum(r['rows'] for r in baseline.values())))
        print('REHEARSAL=PASS original_tables_and_columns_unchanged')
    elif action == 'migrate':
        assert load('rehearsal.json')['result'].startswith('PASS_')
        baseline=load('native-before.json')
        assert fingerprint(NATIVE,'native-fresh-before',baseline) == baseline
        sessions=sql(NATIVE,"SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid();",'native-other-clients')
        assert sessions == '0', 'Unexpected concurrent native database client'
        migrate(NATIVE,'native-migrate')
        assert fingerprint(NATIVE,'native-after-migration',baseline) == baseline
        grants(NATIVE,'native-grants')
        assert fingerprint(NATIVE,'native-after-grants',baseline) == baseline
        save('migration-result.json',dict(result='PASS',database=NATIVE,applied=38,total=50,
            originalTablesPreserved=len(baseline),originalRowsPreserved=sum(r['rows'] for r in baseline.values()),
            finishedAtUtc=datetime.now(timezone.utc).isoformat(),testDatabaseChanged=False))
        print('NATIVE_MIGRATION=PASS V13..V50 applied=38 total=50')
    elif action == 'retire-clone':
        assert load('migration-result.json')['result']=='PASS'
        assert sql('postgres',f"SELECT count(*) FROM pg_stat_activity WHERE datname='{CLONE}';",'clone-connections',True)=='0'
        sql('postgres',f'DROP DATABASE {CLONE};','clone-retired',True)
        save('clone-retired.json',dict(database=CLONE,backupRetained=True,result='PASS_EXACT_REHEARSAL_DATABASE_REMOVED'))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('action',choices=['backup','rehearse','migrate','retire-clone'])
    main(parser.parse_args().action)
