"""Read-only independent review of completed smoke evidence; no DB/process actions."""
import datetime as dt
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / 'docs/evidencia/auditoria-ide-2026-10-23'
RUNTIME = BASE / 'runtime'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


checks = []


def check(name, passed, details=None):
    checks.append({'name': name, 'passed': bool(passed), 'details': details})


before_path = BASE / 'launchers/fingerprint-antes-smoke.json'
after_path = RUNTIME / 'fingerprint-after-smoke.json'
before, after = read(before_path), read(after_path)
check('Both fingerprints successful and readonly',
      before['result'] == after['result'] == 'PASS_TWO_DATABASE_READONLY_CONTENT_FINGERPRINTS'
      and before['dataChanged'] is False and after['dataChanged'] is False)
bd = {d['database']: d for d in before['databases']}
ad = {d['database']: d for d in after['databases']}
check('Exact two database identities', set(bd) == set(ad) == {'solucion_ruta_fija_1', 'ruta_fija_test'})
databases, differences = [], []
for name in sorted(bd):
    first, last = bd[name], ad[name]
    ft = {t['table']: t for t in first['tables']}
    lt = {t['table']: t for t in last['tables']}
    check(name + ' exact unique table inventory', set(ft) == set(lt)
          and len(ft) == first['tableCount'] == last['tableCount']
          and len(ft) == len(first['tables']) == len(last['tables']))
    for table in sorted(set(ft) | set(lt)):
        equal = table in ft and table in lt and ft[table] == lt[table]
        if not equal:
            differences.append({'database': name, 'table': table, 'difference': 'MISSING_OR_CHANGED'})
        check(name + '.' + table + ' count and content SHA256 identical', equal
              and re.fullmatch('[a-f0-9]{64}', ft[table]['contentSha256']) is not None)
    for field in ('pilotLedger', 'adminTermsAcceptances', 'flyway'):
        check(name + ' ' + field + ' exact sequence unchanged', first[field] == last[field])
    flyway = first['flyway']
    versioned = [r.split('|')[0] for r in flyway if not r.startswith('REPEATABLE|')]
    repeatable = [r for r in flyway if r.startswith('REPEATABLE|')]
    if name == 'ruta_fija_test':
        check('Ledger remains1/3/8', set(first['pilotLedger']) == {'ADMIN|3', 'CONDUCTOR|8', 'SUPER_ADMIN|1'})
        check('Two ADMIN terms acceptances remain', first['adminTermsAcceptances'] == ['2'])
        check('Test Flyway V1..V50 plus one repeatable, notV0',
              versioned == [str(i) for i in range(1, 51)] and len(repeatable) == 1
              and len(flyway) == 51 and all(r.endswith('|true') or r.endswith('|t') for r in flyway))
    else:
        check('Development remainsV1..V12', versioned == [str(i) for i in range(1, 13)] and not repeatable)
    databases.append({'database': name, 'tablesCompared': len(ft),
                      'rowsBefore': sum(t['rows'] for t in ft.values()),
                      'rowsAfter': sum(t['rows'] for t in lt.values()),
                      'allTablesEqual': ft == lt, 'ledger': first['pilotLedger'],
                      'termsAcceptances': first['adminTermsAcceptances'],
                      'versionedMigrations': len(versioned), 'repeatableMigrations': len(repeatable),
                      'flywayEntries': len(flyway)})

start, stop = read(RUNTIME / 'start-bat.json'), read(RUNTIME / 'stop-bat.json')
state = read(RUNTIME / 'state-during-smoke.json')
processes = read(RUNTIME / 'qa-independent-ops-processes.json')
check('Real BAT start and stop both exit0', start['exitCode'] == stop['exitCode'] == 0)
check('Runtime explicit SMOKE_READ_ONLY schema2', state['schemaVersion'] == 2 and state['mode'] == 'SMOKE_READ_ONLY')
for service in processes['recordedProcesses']:
    check(service['service'] + ' owned PID now absent', service['pidCurrentlyAbsent'])
check('Only5432 listening among scoped ports',
      all(not p['listeners'] for p in processes['ports'] if p['port'] != 5432)
      and bool(next(p for p in processes['ports'] if p['port'] == 5432)['listeners']))
check('Runtime/demo/lease states absent', processes['runtimeStateAbsent']
      and processes['demoStateAbsent'] and processes['postgresLeaseAbsent'])

http = read(RUNTIME / 'http-checks.json')
health = []
for response in http:
    body = response['body']
    body = bytes(body).decode('utf-8') if isinstance(body, list) else body
    payload = json.loads(body)
    if '/actuator/health' in response['url']:
        check(response['url'] + ' health200UP', response['status'] == 200 and payload['status'] == 'UP')
        health.append({'url': response['url'], 'httpStatus': response['status'], 'status': payload['status']})
    else:
        check('CRM config uses same-origin API', response['status'] == 200 and payload['apiBaseUrl'] == '/api/v1')
browser = read(BASE / 'browser-smoke.json')
check('Edge login rendered with no exceptions or console/network errors', browser['result'] == 'PASS'
      and browser['rendered']['url'] == 'http://127.0.0.1:4300/login' and not browser['exceptions']
      and not browser['consoleErrors'] and not browser['failedRequests'] and browser['signedIn'] is False
      and browser['databaseWritesRequested'] is False)
check('Email and password fields rendered', {'email', 'password'}.issubset({i['type'] for i in browser['rendered']['inputs']}))
anonymous = [r for r in browser['responses'] if r['status'] == 401]
check('Only expected anonymous refresh401, all other responses200', len(anonymous) == 1
      and anonymous[0]['url'].endswith('/api/v1/auth/refresh')
      and all(r['status'] == 200 or r in anonymous for r in browser['responses']))

logs = []
for relative in ('backend.stdout.log', 'backend.stderr.log', 'frontend.stdout.log', 'frontend.stderr.log'):
    path = RUNTIME / relative
    text = path.read_text(encoding='utf-8-sig', errors='replace')
    lines = text.splitlines()
    errors = [i + 1 for i, line in enumerate(lines) if re.search(r'\bERROR\b', line)]
    warnings = [i + 1 for i, line in enumerate(lines) if re.search(r'\bWARN\b', line)]
    failures = [m for m in ('APPLICATION FAILED TO START', 'Caused by:', 'Exception in thread',
                           'BUILD FAILED', 'Application bundle generation failed') if m in text]
    references = [{'line': i + 1, 'configurationReferences': re.findall(r'[A-Za-z_$][A-Za-z0-9_.$]*Exception[A-Za-z0-9_.$]*', line),
                   'stackTrace': line.lstrip().startswith('at ')} for i, line in enumerate(lines) if 'Exception' in line]
    check(relative + ' no ERROR/WARN/failure marker', not errors and not warnings and not failures)
    if relative.endswith('.stderr.log'):
        check(relative + ' empty', path.stat().st_size == 0)
    logs.append({'file': path.relative_to(ROOT).as_posix(), 'sha256': sha(path), 'bytes': path.stat().st_size,
                 'errorSeverityCount': len(errors), 'warningSeverityCount': len(warnings),
                 'failureMarkers': failures, 'exceptionWordReferences': references,
                 'exceptionReferencesAreConfigurationNotFailures': all(not r['stackTrace'] for r in references)})
for item in read(RUNTIME / 'sources-before.json'):
    check('Source or runtime binary unchanged: ' + Path(item['Path']).name,
          sha(Path(item['Path'])) == item['Hash'].lower())

manifest = BASE / 'launchers/manifest-lanzadores-vigentes.sha256'
manifest_files = 0
for line in manifest.read_text(encoding='utf-8').splitlines():
    if line.startswith('#'):
        continue
    digest, relative = line.split('  ', 1)
    check('Frozen live launcher/manual: ' + relative, sha(ROOT / relative) == digest)
    manifest_files += 1

inputs = [before_path, after_path, RUNTIME / 'state-during-smoke.json', RUNTIME / 'start-bat.json',
          RUNTIME / 'stop-bat.json', RUNTIME / 'http-checks.json', BASE / 'browser-smoke.json',
          RUNTIME / 'sources-before.json', RUNTIME / 'qa-independent-ops-processes.json', manifest]
passed = all(c['passed'] for c in checks)
report = {'reviewedAtUtc': dt.datetime.now(dt.timezone.utc).isoformat(),
          'result': 'PASS_INDEPENDENT_READONLY_SMOKE_DATA_PRESERVATION_AND_SHUTDOWN' if passed else 'FAIL_INDEPENDENT_SMOKE_REVIEW',
          'reviewer': 'A-OPS independent of root who executed live smoke',
          'scope': 'Existing evidence and SHA comparison plus OS read-only process/listener query. No new DB connection, test run, service action or browser launched by reviewer.',
          'checksPassed': sum(c['passed'] for c in checks), 'checksTotal': len(checks),
          'tableComparisons': sum(d['tablesCompared'] for d in databases), 'tableDifferences': differences,
          'databases': databases, 'checks': checks, 'logs': logs, 'healthChecks': health,
          'anonymousRefresh401Expected': True, 'currentOwnProcessesAbsent': True,
          'postgresPreservedRunning': True, 'frozenLauncherAndManualFilesVerified': manifest_files,
          'schemaClarification': '51 successes in ruta_fija_test =50 versioned V1..V50 +1 repeatable; noV0. Development remainsV12.',
          'startOutputHandleObservation': 'BAT printed readiness before capture completed; exit0 was captured after child processes were stopped. This does not prove immediate CLI return on launch.',
          'limits': 'Local readonly startup, anonymous UI rendering and controlled shutdown only; no authenticated business-write operation or pilot deployment tested.',
          'manualFrozenUnmodified': True,
          'reviewToolingIncidents': ['Initial browser proof lookup assumed runtime subfolder; actual proof found at audit root without changes.',
                                     'Long inline review command was blocked before execution; same review moved to explicit inspectable Python evidence script.'],
          'inputs': [{'path': p.relative_to(ROOT).as_posix(), 'sha256': sha(p)} for p in inputs],
          'reviewScriptSha256': sha(Path(__file__))}
with (RUNTIME / 'qa-independent-ops.json').open('x', encoding='utf-8') as output:
    json.dump(report, output, ensure_ascii=False, indent=2)
    output.write('\n')
print(json.dumps({'result': report['result'], 'checksPassed': report['checksPassed'], 'checksTotal': len(checks),
                  'tableComparisons': report['tableComparisons'], 'tableDifferences': len(differences),
                  'frozenLauncherFilesVerified': manifest_files, 'databases': databases}))
