"""Read-only review of the prepared Git index. Never prints secret values."""
import argparse
import fnmatch
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args, data=None):
    result = subprocess.run(['git', *args], cwd=ROOT,
                            input=data, capture_output=True, check=True)
    return result.stdout


def paths(data):
    return [p.decode('utf-8') for p in data.split(b'\0') if p]


def check():
    staged = paths(git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z'))
    if not staged:
        raise RuntimeError('No staged source changes to review')
    forbidden = []
    too_large = []
    index = {}
    for record in git('ls-files', '--stage', '-z').split(b'\0'):
        if record:
            metadata, name = record.split(b'\t', 1)
            index[name.decode('utf-8')] = metadata.split()[1].decode('ascii')
    object_ids = sorted({index[p] for p in staged})
    sizes = {}
    for line in git('cat-file', '--batch-check=%(objectname) %(objectsize)',
                    data=('\n'.join(object_ids) + '\n').encode()).decode().splitlines():
        oid, size = line.split()
        sizes[oid] = int(size)
    for relative in staged:
        parts = relative.replace('\\', '/').split('/')
        name = parts[-1].lower()
        if (any(p in {'.local', '.runtime', 'node_modules', '.dart_tool', '__pycache__'} for p in parts)
                or relative.startswith(('backups/', 'memory/backup/', 'backend/target/'))
                or any(fnmatch.fnmatch(name, pat) for pat in ['*.env', '*.env.local', '*.key', '*.pem', '*.p12', '*.pfx',
                       '*.jks', '*.keystore', '*.dpapi', '*.dump', '*.backup', '*.pgdump', '*.private', '*.private.*',
                       'id_ed25519*', 'id_rsa*', '*.log', '*.apk', '*.aab', '*.zip', '*.7z', '*.vsix'])):
            forbidden.append(relative)
        if sizes[index[relative]] > 10 * 1024 * 1024:
            too_large.append(relative)
    if forbidden or too_large:
        raise RuntimeError(json.dumps({'forbiddenPaths': forbidden, 'oversizePaths': too_large}))
    required = ['.gitignore', '.gitattributes', 'scripts/RutaFijaRuntimeSchemaPolicy.ps1',
                'scripts/Test-RutaFijaRuntimeSchemaPolicy.ps1', 'scripts/Invoke-RutaFijaNativeRuntime.ps1',
                'scripts/Invoke-RutaFijaQuickDemo.ps1', 'docs/runtime-y-evaluacion-solucion-1-v3.1.md',
                'docs/actas/L-08-cierre-funcional-global.md', 'docs/legal/protocolo-transicion-piloto-real.md',
                'docs/entrega/2026-10-23-commit-v3.1/COMMIT_MESSAGE.txt']
    for path in required:
        if path not in staged:
            raise RuntimeError('Required change absent from staging: ' + path)
    migrations = paths(git('ls-files', '-z', '--', 'backend/src/main/resources/db/migration',
                          'backend/src/main/resources/db/test-migration'))
    versions = sorted(int(re.search(r'/V(\d+)__', p)[1]) for p in migrations if re.search(r'/V(\d+)__', p))
    repeatables = [p for p in migrations if '/R__' in p]
    if versions != list(range(1, 51)) or repeatables != ['backend/src/main/resources/db/test-migration/R__native_test_role_privileges.sql']:
        raise RuntimeError('Unexpected Flyway source inventory in index')
    exact_paths = ['docs/actas/L-08-cierre-funcional-global.md', 'docs/legal/publicado/terminos-admin-v1.0.0.md',
                   'scripts/Invoke-RutaFijaNativeRuntime.ps1', 'scripts/Invoke-RutaFijaQuickDemo.ps1',
                   'scripts/RutaFijaRuntimeSchemaPolicy.ps1']
    index_hashes = []
    for relative in exact_paths:
        staged_sha = hashlib.sha256(git('show', ':' + relative)).hexdigest()
        actual = sha(ROOT / relative)
        if staged_sha != actual:
            raise RuntimeError('Index bytes differ from reviewed source: ' + relative)
        index_hashes.append({'path': relative, 'sha256': actual})
    return {'checkedAt': datetime.now(timezone.utc).isoformat(), 'result': 'PASS_REVIEWED_STAGING',
            'stagedChanges': len(paths(git('diff', '--cached', '--name-only', '-z'))),
            'stagedNonDeletedFiles': len(staged), 'forbiddenPaths': [], 'filesOver10MiB': [],
            'versionedMigrations': 50, 'testOnlyRepeatables': 1, 'exactIndexBytes': index_hashes,
            'HEAD': git('rev-parse', 'HEAD').decode().strip(), 'commitExecuted': False,
            'remoteContacted': False, 'privateValuesPrinted': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check()
    if args.output:
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(result, output, indent=2)
    print(json.dumps(result, indent=2))
