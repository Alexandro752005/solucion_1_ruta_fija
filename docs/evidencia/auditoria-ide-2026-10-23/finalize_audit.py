"""Preserve and verify this new audit cut without modifying any older seal."""
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
MANIFEST = OUT / 'manifest-auditoria.sha256'
PROOF = OUT / 'verificacion-final.json'
REPORT = ROOT / 'AUDITORIA_INTEGRAL_IDE_Y_EJECUTABLES_2026-10-23.md'


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def safe_path(relative):
    path = (ROOT / relative).resolve()
    require(path.is_relative_to(ROOT), f'Outside workspace: {relative}')
    require(not any(p.lower() in {'.local', '.git'} for p in path.relative_to(ROOT).parts), 'Private path refused')
    require(path.suffix.lower() not in {'.env', '.p12', '.jks', '.key', '.dpapi'}, 'Private extension refused')
    require(path.is_file() and not path.is_symlink(), f'Not a regular file: {relative}')
    return path


def verify_manifest(path):
    entries = path.read_text(encoding='utf-8-sig').splitlines()
    require(bool(entries), f'Empty manifest: {path}')
    seen = set()
    for entry in entries:
        require(bool(re.fullmatch(r'[a-f0-9]{64}  .+', entry)), f'Bad manifest line: {entry}')
        expected, relative = entry.split('  ', 1)
        require(relative not in seen, f'Duplicate manifest entry: {relative}')
        seen.add(relative)
        require(sha(safe_path(relative)) == expected, f'Hash mismatch: {relative}')
    return len(entries)


def validate():
    frozen = ROOT / 'docs/evidencia/L-08/incremento-5-candidato-final/manifest-cierre.sha256'
    require(sha(frozen) == '2708399adc572cacf8a3d3f09ba31e507604717e6817ed66e7c871c4a148ac5a', 'Old manifest changed')
    require(verify_manifest(frozen) == 427, 'Old archive entry count changed')
    require(sha(ROOT / 'backups/L-00-2026-10-13-final3/source-snapshot.zip') == '3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b', 'Base snapshot changed')
    require(sha(ROOT / 'docs/legal/publicado/terminos-admin-v1.0.0.md') == '20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417', 'Published terms changed')
    initial = read(OUT / 'ide/diagnostics.initial.json')
    final = read(OUT / 'ide/diagnostics.after-resume.json')
    require((initial['errors'], initial['warnings']) == (1633, 83), 'Initial count mismatch')
    require((final['errors'], final['warnings'], final['diagnostics']) == (0, 0, []), 'IDE not clean')
    require(final['capturedAt'] > initial['capturedAt'], 'Invalid diagnostic chronology')
    backend = read(OUT / 'backend/summary.json')
    require((backend['finalTests'], backend['failures'], backend['errors'], backend['javacWarnings']) == (257, 0, 0, 0), 'Backend gate failed')
    require(sha(ROOT / 'backend/target/ruta-fija-backend-0.1.0-SNAPSHOT.jar') == backend['runtimeJarSha256'], 'Runtime JAR changed')
    for name in ['typecheck', 'test-ci', 'build']:
        require(read(OUT / f'frontend/{name}.result.json')['exitCode'] == 0, f'Frontend {name} failed')
    require('241 passed' in (OUT / 'frontend/test-ci.native.log').read_text(encoding='utf-8-sig'), 'Frontend suite count failed')
    mobile = read(OUT / 'mobile/resultado-mobile-ide.json')
    require(mobile['result'] == 'PASS_MOBILE_IDE_ANALYSIS_AND_COMPLETE_LOCAL_FLUTTER_SUITE', 'Mobile gate failed')
    mobile_suites = [x for x in mobile['checks'] if x['stage'] == 'after'
                    and x['command'] == 'flutter test --no-pub --reporter expanded']
    require(len(mobile_suites) == 1 and
            tuple(mobile_suites[0][k] for k in ['exitCode', 'passed', 'failed', 'skippedConnected']) == (0, 233, 0, 7),
            'Complete mobile suite count failed')
    require(read(OUT / 'launchers/qa-mocks-lease-demo-final.json')['checksPassed'] == 42,
            'Launcher safety checks failed')
    retired = read(OUT / 'ide/temporary-tools-retired.json')
    require(retired['observerUninstallExitCode'] == 0 and retired['headlessBrowserProcessAbsent'],
            'Temporary QA tools not retired')
    for name in ['start', 'stop']:
        require(read(OUT / f'runtime/{name}-bat.json')['exitCode'] == 0, f'{name} failed')
    require(read(OUT / 'launchers/verificar-bat-real.json')['exitCode'] == 0, 'BAT verification failed')
    before = read(OUT / 'launchers/fingerprint-antes-smoke.json')['databases']
    after = read(OUT / 'runtime/fingerprint-after-smoke.json')['databases']
    require(before == after and sum(x['tableCount'] for x in after) == 121, 'Database preservation failed')
    require(read(OUT / 'browser-smoke.json')['result'] == 'PASS', 'Browser gate failed')
    ops = read(OUT / 'runtime/qa-independent-ops.json')
    require(ops['checksPassed'] == ops['checksTotal'] == 207, 'Independent runtime QA failed')
    for item in read(OUT / 'mobile/qa-java-warning-patch.json')['reviewedFiles']:
        require(sha(safe_path(item['path'])) == item['sha256'], f"Reviewed Java file changed: {item['path']}")
    for item in read(OUT / 'mobile/qa-launcher-observability-final.json')['reviewedFiles']:
        require(sha(safe_path(item['path'])) == item['sha256'], f"Reviewed launcher changed: {item['path']}")
    checked_links = 0
    for doc in [REPORT, ROOT / 'docs/Uso del Sistema.md', ROOT / 'backend/README.md']:
        current_text = doc.read_text(encoding='utf-8').split('<details>', 1)[0]
        for label, target in re.findall(r'\[([^\]]+)\]\((<[^>]+>|[^)]+)\)', current_text):
            target = target.strip('<>')
            if re.match(r'^[a-z]+:', target) or target.startswith('#'):
                continue
            path = (doc.parent / target.split('#', 1)[0]).resolve()
            if path in {MANIFEST, PROOF}:
                continue
            require(path.is_file(), f'Broken link {label}: {path}')
            checked_links += 1
    return {'oldL08ManifestEntriesVerified': 427, 'oldL08ManifestUnchanged': True,
            'initialIdeErrors': 1633, 'initialIdeWarnings': 83, 'finalIdeErrors': 0, 'finalIdeWarnings': 0,
            'finalIdeCapturedAt': final['capturedAt'], 'javaTests': 257, 'angularTests': 241,
            'flutterTests': 233, 'flutterConnectedTestsSkipped': 7, 'totalPassedLocalTests': 731,
            'javacWarnings': 0, 'unchangedDatabaseTables': 121, 'runtimeIndependentChecks': 207,
            'localLinksChecked': checked_links, 'developmentDatabaseVersion': 12,
            'testDatabaseVersion': 50, 'pilotEnabled': False}


def seal():
    require(not MANIFEST.exists() and not PROOF.exists(), 'This audit cut already exists; never overwrite it')
    summary = validate()
    # Bridge working files can change until VS Code reloads its extension host.
    # Only immutable named snapshots are evidence of the final diagnostic count.
    mutable_observer_files = {OUT / 'ide/diagnostics.current.json',
                              OUT / 'ide/bridge-request.json', OUT / 'ide/bridge-response.json'}
    paths = set(p for p in OUT.rglob('*') if p.is_file() and p not in mutable_observer_files)
    paths.add(REPORT)
    for relative in ['.vscode/settings.json', 'analysis_options.yaml', 'frontend/angular.json',
                     'frontend/vitest-base.config.mts', 'mobile/test/features/profile/mobile_driver_repository_test.dart',
                     'backend/target/ruta-fija-backend-0.1.0-SNAPSHOT.jar']:
        paths.add(safe_path(relative))
    for review in ['mobile/qa-java-warning-patch.json', 'mobile/qa-launcher-observability-final.json']:
        paths.update(safe_path(x['path']) for x in read(OUT / review)['reviewedFiles'])
    changes = read(OUT / 'launchers/source-supersession.json')
    paths.update(safe_path(x['path']) for x in changes['files'])
    # Include all current launcher helpers and docs, even when they had no predecessor.
    for relative in ['scripts/Invoke-RutaFijaPostgresqlLease.ps1', 'scripts/Test-RutaFijaLocalSecretPolicy.ps1',
                     'scripts/Get-RutaFijaReadOnlyFingerprint.ps1', 'scripts/Test-RutaFijaLauncherSafety.ps1',
                     'scripts/Test-RutaFijaBackendUnitSafe.ps1', 'docs/Uso del Sistema.md', 'backend/README.md']:
        paths.add(safe_path(relative))
    lines = []
    for path in sorted(paths, key=lambda p: p.as_posix().lower()):
        relative = path.relative_to(ROOT).as_posix()
        path = safe_path(relative)
        lines.append(f'{sha(path)}  {relative}')
    with MANIFEST.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write('\n'.join(lines) + '\n')
    count = verify_manifest(MANIFEST)
    proof = {'verifiedAtUtc': datetime.now(timezone.utc).isoformat(),
             'result': 'PASS_AUDIT_IDE_CODE_AND_READ_ONLY_RUNTIME',
             'filesVerified': count, 'mismatches': [], 'manifestSha256': sha(MANIFEST), **summary,
             'scopeLimit': 'Native V12 migration pending; authenticated operations and real pilot not certified',
             'temporaryBrowserProfileRetained': '.runtime/qa-edge-1792772227178',
             'temporaryProfileRemovalBlockedByAutomaticReview': True}
    with PROOF.open('x', encoding='utf-8') as stream:
        json.dump(proof, stream, indent=2)
    print(json.dumps(proof, indent=2))


def verify():
    proof = read(PROOF)
    require(sha(MANIFEST) == proof['manifestSha256'], 'Frozen audit manifest changed')
    require(verify_manifest(MANIFEST) == proof['filesVerified'], 'Frozen audit count changed')
    summary = validate()
    print(json.dumps({'result': 'PASS_FROZEN_AUDIT_REVERIFIED', 'filesVerified': proof['filesVerified'],
                      'manifestSha256': proof['manifestSha256'], **summary}, indent=2))


if __name__ == '__main__':
    {'check': lambda: print(json.dumps(validate(), indent=2)), 'seal': seal, 'verify': verify}[sys.argv[1]]()
