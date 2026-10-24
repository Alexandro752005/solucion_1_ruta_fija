"""Read-only source/config checks; writes a new audit record, never old evidence."""
import ast
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

out = Path(__file__).resolve().parent
root = out.parents[2]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
failures = []
python_checks = []
for path in sorted((root / 'scripts').rglob('*.py')):
    try:
        ast.parse(path.read_text(encoding='utf-8-sig'), filename=str(path))
        python_checks.append({'path': path.relative_to(root).as_posix(), 'sha256': sha(path)})
    except SyntaxError as exc:
        failures.append(str(exc))
json_paths = [*root.glob('.vscode/*.json'), *root.glob('frontend/*.json'),
              *root.glob('frontend/public/config/*.json')]
json_checks = []
for path in sorted(json_paths):
    try:
        json.loads(path.read_text(encoding='utf-8-sig'))
        json_checks.append({'path': path.relative_to(root).as_posix(), 'sha256': sha(path)})
    except ValueError as exc:
        failures.append(f'{path.name}: {exc}')
frozen = root / 'docs/evidencia/L-08/incremento-5-candidato-final/manifest-cierre.sha256'
frozen_entries = frozen.read_text(encoding='utf-8-sig').splitlines()
frozen_mismatches = []
for line in frozen_entries:
    expected, relative = line.split('  ', 1)
    path = root / relative
    if not path.is_file() or sha(path) != expected:
        frozen_mismatches.append(relative)
before = out / 'mobile/before/mobile_driver_repository_test.dart.before'
after = root / 'mobile/test/features/profile/mobile_driver_repository_test.dart'
expected = before.read_text(encoding='utf-8').replace(
    "        'permissionGranted': true,\n",
    "        'permissionGranted': true,\n        'locationServiceEnabled': true,\n        'foreground': true,\n")
mobile_review = expected == after.read_text(encoding='utf-8')
mobile_rules = root / 'mobile/analysis_options.yaml'
unchanged_mobile_rules = sha(mobile_rules) == 'fe994f27e5b83c122bab0c2ef62317cc0f590c8df1028f367578ff7f3fd6e195'
settings_before = json.loads((out / 'ide/settings.before.json').read_text())
settings_after = json.loads((root / '.vscode/settings.json').read_text())
settings_after.pop('dart.analysisExcludedFolders')
unchanged_other_settings = settings_before == settings_after
protected = {
    'backups/L-00-2026-10-13-final3/source-snapshot.zip': '3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b',
    'docs/legal/publicado/terminos-admin-v1.0.0.md': '20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417',
}
protected_results = {p: sha(root / p) == expected_hash for p, expected_hash in protected.items()}
result = {'checkedAt': datetime.now(timezone.utc).isoformat(),
          'pythonScripts': python_checks, 'jsonConfigs': json_checks,
          'syntaxFailures': failures, 'L08FrozenEntries': len(frozen_entries),
          'L08FrozenManifestSha256': sha(frozen), 'L08Mismatches': frozen_mismatches,
          'independentMobileTestReviewOnlyTwoRequiredFields': mobile_review,
          'mobileLintRulesUnchanged': unchanged_mobile_rules,
          'otherVSCodeSettingsUnchanged': unchanged_other_settings,
          'immutableSnapshotAndPublishedTerms': protected_results}
passed = (not failures and not frozen_mismatches and mobile_review and
          unchanged_mobile_rules and unchanged_other_settings and all(protected_results.values()))
result['result'] = 'PASS' if passed else 'FAIL'
with (out / 'qa-root-static.json').open('x', encoding='utf-8') as record:
    json.dump(result, record, indent=2)
print(json.dumps(result, indent=2))
raise SystemExit(0 if passed else 1)
