"""Local L08 preservation and conservative orphan-log archival; no database writes."""
import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/evidencia/L-08/incremento-5-candidato-final/hygiene'

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def preserve():
    previous = ROOT / 'docs/evidencia/L-08/incremento-4-cierre-fisico'
    manifests = [ROOT / c['manifest'] for c in json.loads((previous / 'preservacion-durante-bateria.json').read_text(encoding='utf-8-sig'))['checks'] if 'manifest' in c]
    manifests.append(previous / 'manifest-caso-cerrado.sha256')
    expected_manifests = {
        'docs/evidencia/L-07/manifest-cierre.sha256': '9ba71c3071583695991fb22ecf1eb261de5b9b83f9db76f73ff7da1e2cebcf13',
        'docs/evidencia/L-08/incremento-1-candidato-y-preautenticacion/manifest.sha256': 'f5565a3491d9890d6a0adcc2d8b882b49d40993808f8926bb9fc6113dca861bf',
        'docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/manifest-caso-cerrado.sha256': '62498733d6cb86cd884b8fc6e38b9bb367a7c7579811f9443b9303decb93635f',
        'docs/evidencia/L-08/cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/manifest-caso-cerrado.sha256': '1a72d34aaeb94b67328dafbb92af57c0b5459808dbc2ebdbef0a08de6975fdcb',
        'docs/evidencia/L-08/incremento-2-mision-y-controles/manifest-corte-post-cap25.sha256': '7091e94d1d3f26416ca4e9831202a2f8488aa4566b1a2f648fe056d47fc1afe2',
        'docs/evidencia/L-08/incremento-4-cierre-fisico/manifest-caso-cerrado.sha256': 'c74aa55d4d3a7e4d43f26d653b48139b420c8346d669b1b3f3e21965bbffeb1d',
    }
    checks, failures, historical = [], [], []
    mutable_coordination = {'docs/backlog-producto-ruta-fija-v3.1.md', 'docs/seguimiento/tareas.json', 'memory/current.md', 'memory/target.md'}
    for manifest in manifests:
        assert sha(manifest) == expected_manifests[manifest.relative_to(ROOT).as_posix()]
        checked = 0
        for line in manifest.read_text(encoding='utf-8-sig').splitlines():
            match = re.match(r'^([a-fA-F0-9]{64})\s+\*?(.+)$', line)
            if not match:
                if line.strip():
                    raise ValueError('unrecognized manifest line')
                continue
            expected, relative = match.groups()
            file = ROOT / relative
            if not file.is_file() or sha(file).lower() != expected.lower():
                if relative in mutable_coordination and manifest.as_posix().endswith('L-07/manifest-cierre.sha256'):
                    matches = [p for p in (ROOT / 'memory/backup').rglob(file.name) if sha(p).lower() == expected.lower()]
                    if not matches:
                        failures.append(relative)
                    else:
                        historical.append({'path': relative, 'expectedSha256': expected, 'preservedHistoricalCopy': matches[0].relative_to(ROOT).as_posix(), 'reason': 'Mutable coordination superseded by later authorized lots; original L07 bytes verified in backup'})
                else:
                    failures.append(relative)
            checked += 1
        checks.append({'manifest': manifest.relative_to(ROOT).as_posix(), 'sha256': sha(manifest), 'filesVerified': checked})
    baseline = ROOT / 'backups/L-00-2026-10-13-final3/source-snapshot.zip'
    terms = ROOT / 'docs/legal/publicado/terminos-admin-v1.0.0.md'
    protected = {'baseSnapshotSha256': sha(baseline), 'termsSha256': sha(terms)}
    assert protected['baseSnapshotSha256'] == '3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b'
    assert protected['termsSha256'] == '20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417'
    state = json.loads((ROOT / 'backend/.local/l08-fis02-state.json').read_text(encoding='utf-8-sig'))
    clean = state.get('TEST_CLEANUP_RELEASED') is True
    assert clean and not (ROOT / 'backend/.local/l08-connected.active').exists()
    assert not (ROOT / 'backend/.local/l08-fis02.private').exists()
    result = {'observedAtUtc': dt.datetime.now(dt.timezone.utc).isoformat(), 'result': 'PASS' if not failures else 'FAIL', 'checks': checks, 'filesVerified': sum(c['filesVerified'] for c in checks), 'historicalMutableFilesReconciled': historical, 'mismatches': failures, 'protected': protected, 'TEST_CLEANUP_RELEASED': clean, 'markerAndPrivateAbsent': True}
    write('preservacion.json', result)
    print(json.dumps(result, ensure_ascii=False))
    if failures:
        raise SystemExit(1)

def archive():
    # Mentioned logs remain at their original path. No recursive deletion, no
    # generated classes/classpaths/JARs or active current-run files are moved.
    texts = []
    protected_hashes = set()
    for base in ('docs', 'specs', 'memory', 'artifacts', 'scripts'):
        for p in (ROOT / base).rglob('*'):
            if p.is_file() and p.suffix in ('.md', '.json', '.sha256', '.ps1', '.java', '.py') and p.stat().st_size < 5_000_000:
                try:
                    text = p.read_text(encoding='utf-8-sig')
                except (UnicodeError, OSError):
                    continue
                texts.append(text)
                if p.suffix == '.sha256':
                    protected_hashes.update(re.findall(r'\b[a-fA-F0-9]{64}\b', text.lower()))
    corpus = '\n'.join(texts)
    dest = ROOT / 'backups/L-08-higiene-2026-10-23'
    entries, retained = [], []
    inventory = []
    cutoff = dt.datetime(2026, 10, 23, tzinfo=dt.timezone.utc).timestamp()
    for base in (ROOT / '.runtime', ROOT / 'backend/target'):
        for p in base.rglob('*'):
            if not p.is_file():
                continue
            inventory.append({'path': p.relative_to(ROOT).as_posix(), 'bytes': p.stat().st_size})
            if p.suffix.lower() not in ('.log', '.tmp', '.exit') or p.stat().st_mtime >= cutoff:
                continue
            digest = sha(p)
            relative = p.relative_to(ROOT)
            if p.name in corpus or digest in protected_hashes:
                retained.append(relative.as_posix())
                continue
            target = dest / relative
            assert p.resolve().is_relative_to(base.resolve())
            assert target.resolve().is_relative_to(dest.resolve())
            if target.exists():
                raise RuntimeError('archive target exists; refuse overwrite')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(p), str(target))
            assert sha(target) == digest
            entries.append({'source': relative.as_posix(), 'archive': target.relative_to(ROOT).as_posix(), 'sha256': digest, 'bytes': target.stat().st_size})
    write('inventario-compilacion-previo.json', inventory)
    write('limpieza-conservadora.json', {'policy': 'Archive old unreferenced logs/tmp/exit only; no recursive deletion; sources, compiled dependencies and referenced evidence retained', 'archivedCount': len(entries), 'archivedBytes': sum(e['bytes'] for e in entries), 'retainedReferencedLogs': retained, 'entries': entries})
    dest.mkdir(parents=True, exist_ok=True)
    manifest = dest / 'manifest.sha256'
    if manifest.exists():
        raise RuntimeError('archive manifest exists')
    manifest.write_text(''.join(e['sha256'] + '  ' + e['archive'] + '\n' for e in entries), encoding='utf-8')
    print(json.dumps({'archivedCount': len(entries), 'retainedReferencedLogs': len(retained), 'archiveManifestSha256': sha(manifest)}))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('preserve', 'archive'))
    args = parser.parse_args()
    preserve() if args.action == 'preserve' else archive()
