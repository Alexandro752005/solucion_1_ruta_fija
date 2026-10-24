"""Select the audited unit allowlist and require every selected CI report.

No database connections, fixture cleanup or product/test changes are performed.
The three opt-in integrations run against dedicated, fresh runner databases.
Historical/manual harnesses remain outside this explicitly bounded CI check.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INTEGRATIONS = (
    'LegacyConductorReconciliationIT',
    'LegacyConductorRegularizationConcurrencyIT',
    'DriverIdentityReviewCorrectionIT',
)


def unit_classes():
    source = (ROOT / 'scripts/Test-RutaFijaBackendUnitSafe.ps1').read_text(encoding='utf-8-sig')
    block = re.search(r'\$names=@\((.*?)\n\)', source, re.S)
    if block is None:
        raise ValueError('Explicit audited unit selection is missing')
    names = re.findall(r"'([A-Za-z0-9]+Test)'", block.group(1))
    if not names or len(names) != len(set(names)):
        raise ValueError('Audited unit selection is empty or duplicated')
    forbidden = re.compile(r'@SpringBootTest|@DataJpaTest|extends\s+\w*IntegrationTest|NativePostgresTestDatabase|DriverManager|getConnection\(')
    for name in names:
        matches = list((ROOT / 'backend/src/test/java').rglob(name + '.java'))
        if len(matches) != 1 or forbidden.search(matches[0].read_text(encoding='utf-8-sig')):
            raise ValueError('Unit selection is not an exact reviewed database-free class: ' + name)
    return names


def validate_reports(folder, expected):
    reports = {}
    totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    for path in folder.glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        name = suite.attrib['name'].rsplit('.', 1)[-1]
        if name in reports:
            raise ValueError('Duplicate report: ' + name)
        values = {key: int(suite.attrib.get(key, '0')) for key in totals}
        if values['tests'] <= 0 or any(values[key] for key in ('failures', 'errors', 'skipped')):
            raise ValueError('Failed, skipped or empty selected suite: ' + name)
        reports[name] = values
        for key, value in values.items():
            totals[key] += value
    if set(reports) != set(expected):
        raise ValueError('Selected report mismatch: ' + str(sorted(set(expected) ^ set(reports))))
    return {'classes': len(reports), **totals}


def main():
    if sys.argv[1:] == ['units']:
        print(','.join(unit_classes()))
    elif sys.argv[1:] == ['reports']:
        target = ROOT / 'backend/target'
        result = {
            'unit': validate_reports(target / 'surefire-reports', unit_classes()),
            'isolatedIntegration': validate_reports(target / 'failsafe-reports', INTEGRATIONS),
            'historicalManualHarnessesExecuted': False,
        }
        (target / 'ci-candidate-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result, indent=2))
    else:
        raise SystemExit('Usage: backend_candidate_checks.py units|reports')


if __name__ == '__main__':
    main()
