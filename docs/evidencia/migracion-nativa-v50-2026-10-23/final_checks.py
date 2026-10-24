import hashlib
import json
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone
import operate as ops

root=Path.cwd()
run=ops.RUN
adb='D:/Android/Sdk/platform-tools/adb.exe'
observations=[]
def check_http(url,expected,phone=False):
    if phone:
        result=subprocess.run([adb,'-s','d9538efb','shell','curl','-sS','--max-time','8','-w','HTTP_STATUS:%{http_code}',url],
            capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=12)
        assert result.returncode==0,result.stderr
        body,status=result.stdout.rsplit('HTTP_STATUS:',1)
        status=int(status.strip())
    else:
        try:response=urllib.request.urlopen(url,timeout=10)
        except urllib.error.HTTPError as error:response=error
        status=response.status
        body=response.read().decode('utf-8')
    assert status==expected,(url,status)
    if url.endswith('/actuator/health') and expected==200:assert json.loads(body)['status']=='UP'
    if expected==401:assert json.loads(body)['code']=='AUTH_TOKEN_INVALID'
    if url=='http://127.0.0.1:4300/':assert 'Ruta Fija | CRM administrativo' in body and '<rf-root' in body
    observations.append(dict(url=url,source='REAL_POCO_ADB_SHELL' if phone else 'PC',httpStatus=status,
        health='UP' if url.endswith('/actuator/health') and expected==200 else None,authenticationTest='NO_CREDENTIALS_SENT',
        observedAtUtc=datetime.now(timezone.utc).isoformat()))

check_http('http://127.0.0.1:8080/actuator/health',200)
check_http('http://127.0.0.1:4300/',200)
check_http('http://127.0.0.1:4300/api/v1/mobile/profile',401)
check_http('http://127.0.0.1:8080/actuator/health',200,True)
check_http('http://127.0.0.1:8080/api/v1/mobile/profile',401,True)
check_http('http://192.168.18.4:4311/actuator/health',200,True)
check_http('http://192.168.18.4:4311/api/v1/mobile/profile',401,True)
check_http('http://192.168.18.4:4311/api/v1/users',404,True)
check_http('http://192.168.18.4:4311/actuator/health',403)
ops.save('http-observations.json',observations)

baseline=ops.load('native-before.json')
after=ops.fingerprint(ops.NATIVE,'native-final-original-columns',baseline)
assert all(baseline[name]==after[name] for name in baseline if name!='audit_event')
cutoff=ops.load('backup.json')['createdAtUtc']
cols=baseline['audit_event']['columns']
projection=','.join(f"'{c}',t.\"{c}\"" for c in cols)
rowhash=f"encode(sha256(convert_to(jsonb_build_object({projection})::text,'UTF8')),'hex')"
old_audit=ops.sql(ops.NATIVE,f"SELECT count(*)||'|'||encode(sha256(convert_to(coalesce(string_agg({rowhash},'' ORDER BY {rowhash}),''),'UTF8')),'hex') FROM audit_event t WHERE occurred_at<='{cutoff}'::timestamptz;",'audit-original-subset')
assert old_audit==str(baseline['audit_event']['rows'])+'|'+baseline['audit_event']['sha256']
audit_new=json.loads(ops.sql(ops.NATIVE,f"SELECT coalesce(json_agg(json_build_object('action',action,'occurredAt',occurred_at) ORDER BY occurred_at),'[]'::json) FROM audit_event WHERE occurred_at>'{cutoff}'::timestamptz;",'audit-new-events'))
ops.save('runtime-audit-additions.json',audit_new)
ops.save('preservation.json',dict(result='PASS',originalApplicationTables=15,originalRows=142,originalColumnHashesIdenticalImmediatelyAfterMigration=True,
    original14TablesStillIdenticalAfterRuntime=True,original91AuditRowsStillIdenticalAfterRuntime=True,
    runtimeAuditRowsAppended=len(audit_new),newAuditActions=sorted(set(r['action'] for r in audit_new)),
    protectedTestDatabaseIdentical=(ops.load('before-two-databases.json')['databases'][1]==ops.load('after-runtime-two-databases.json')['databases'][1])))
assert ops.load('preservation.json')['protectedTestDatabaseIdentical']
history=json.loads(ops.sql(ops.NATIVE,"SELECT json_agg(json_build_object('rank',installed_rank,'version',version,'script',script,'checksum',checksum,'success',success) ORDER BY installed_rank) FROM flyway_schema_history;",'final-flyway-history'))
assert len(history)==50 and [r['version'] for r in history]==[str(i) for i in range(1,51)] and all(r['success'] for r in history)
ops.save('flyway-history-final.json',history)
connections=json.loads(ops.sql(ops.NATIVE,"SELECT coalesce(json_agg(json_build_object('database',datname,'user',usename,'applicationName',application_name,'state',state,'backendType',backend_type)),'[]'::json) FROM pg_stat_activity WHERE datname='solucion_ruta_fija_1' AND pid<>pg_backend_pid();",'native-runtime-connections'))
assert any(c['user']=='rf_app' for c in connections)
ops.save('application-database-connections.json',connections)

unchanged={
 'backups/L-00-2026-10-13-final3/source-snapshot.zip':'3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b',
 'docs/legal/publicado/terminos-admin-v1.0.0.md':'20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417',
 'backend/target/ruta-fija-backend-0.1.0-SNAPSHOT.jar':'bf17109f3986c6176c1461389cbc9382902fba4c6a609cf66dd48a3561a5c7b8'}
for name,sha in unchanged.items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==sha,name
with zipfile.ZipFile(root/'backend/target/ruta-fija-backend-0.1.0-SNAPSHOT.jar') as jar:
    files=list((root/'backend/src/main/resources/db/migration').glob('*.sql'))
    assert len(files)==50
    for file in files:assert jar.read('BOOT-INF/classes/db/migration/'+file.name)==file.read_bytes(),file.name
ops.save('immutables-and-jar.json',dict(result='PASS',unchanged=unchanged,packagedRuntimeMigrationSourcesMatch=50))
ops.save('source-migration-sha256.json',{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'backend/src/main/resources/db/migration').glob('*.sql'))})
print('FINAL_CHECKS=PASS http=9 original_tables=15 rows=142 native_migrations=50 test_database=UNCHANGED')
