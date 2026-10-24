"""Freeze the exact approved local candidate; verification never repairs files."""
import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'docs/evidencia/L-08/incremento-5-candidato-final'
MANIFEST = BASE / 'manifest-cierre.sha256'
SOURCES = [
    'scripts/audit_l08_local.py', 'scripts/finalize_l08_candidate.py', 'scripts/seal_l08_candidate.py',
    'scripts/medir-cumplimiento-java.ps1', 'scripts/Build-RutaFijaL08ReleaseCandidate.ps1',
    'scripts/Sign-RutaFijaL08Closure.ps1', 'scripts/support/L08ClosureSigner.java',
    'scripts/Invoke-RutaFijaL08IndependentBackup.ps1', 'scripts/Watch-RutaFijaL08Health.ps1',
    'scripts/Test-RutaFijaL08Continuity.ps1', 'scripts/Test-RutaFijaL08BackupAge.ps1', 'scripts/L08ContinuityTools.java',
    'mobile/android/app/build.gradle.kts',
    'mobile/lib/features/location/domain/mobile_service_location_controller.dart',
    'mobile/lib/features/location/presentation/service_location_status.dart',
    'mobile/test/features/location/l08_prolonged_no_fix_test.dart',
]
DOCUMENTS = [
    'INFORME_FINAL_SUSTENTACION_RUTA_FIJA_V3_1_L08.md',
    'docs/actas/L-08-cierre-funcional-global.md', 'docs/contratos/L-08.md',
    'docs/contratos/L-08-v0.8.0-antecedente.md', 'docs/evidencia/L-08/matriz-L08.md',
    'docs/evidencia/L-08/matriz-L08-v0.8.0-antecedente.md',
    'docs/operacion/L-08-respaldo-independiente.md', 'docs/operacion/L-08-monitoreo-operativo.md',
    'docs/legal/protocolo-transicion-piloto-real.md', 'docs/ejecucion/L-08.md',
    'docs/backlog-producto-ruta-fija-v3.1.md', 'docs/seguimiento/tareas.json',
    'memory/current.md', 'memory/target.md', 'specs/decisiones-L08-2026-10-21.json',
]

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def manifest_entries(manifest):
    entries = {}
    for line in manifest.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        match = re.fullmatch(r'([0-9a-fA-F]{64})  (.+)', line)
        require(match is not None, 'MALFORMED_MANIFEST_LINE')
        digest, relative = match.groups()
        path = (ROOT / relative).resolve()
        require(not Path(relative).is_absolute() and path.is_relative_to(ROOT), 'MANIFEST_PATH_OUTSIDE_WORKSPACE')
        require(relative not in entries, 'DUPLICATE_MANIFEST_ENTRY')
        entries[relative] = digest.lower()
    require(entries, 'EMPTY_MANIFEST_REFUSED')
    return entries

def permitted_file(path):
    relative = path.relative_to(ROOT)
    private = {'.local', '.aws', '.codex', '.git', 'private'}
    require(not private.intersection(relative.parts), 'PRIVATE_DIRECTORY_REFUSED')
    require(path.suffix.lower() not in {'.private', '.p12', '.pfx', '.jks', '.keystore', '.dpapi', '.key', '.env'}, 'PRIVATE_EXTENSION_REFUSED')
    cursor = path
    while cursor != ROOT:
        require(not cursor.is_symlink() and not cursor.is_junction(), 'REPARSE_POINT_REFUSED')
        cursor = cursor.parent

def prepare():
    require(not MANIFEST.exists(), 'ALREADY_FROZEN_NO_SOURCE_REWRITE')
    for source in SOURCES:
        target = BASE / 'sources' / source
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / source, target)
        require(sha(ROOT / source) == sha(target), 'SOURCE_COPY_MISMATCH')
    print('FINAL_SOURCE_COPIES=VERIFIED')

def links():
    checked, missing = 0, []
    paths = [ROOT / p for p in DOCUMENTS[:9]]
    paths.extend(BASE.rglob('README.md'))
    for p in paths:
        for match in re.finditer(r'\[[^\]]*\]\(([^)]+)\)', p.read_text(encoding='utf-8-sig')):
            target = match.group(1).strip('<>').split('#')[0]
            if not target or re.match(r'^[a-zA-Z]+:', target):
                continue
            resolved = (p.parent / target).resolve()
            checked += 1
            if not resolved.is_relative_to(ROOT) or not resolved.exists():
                missing.append({'source': p.relative_to(ROOT).as_posix(), 'target': target})
    require(not missing, 'MISSING_LOCAL_LINKS: ' + json.dumps(missing))
    return checked

def guards():
    tasks = read(ROOT / 'docs/seguimiento/tareas.json')
    require(tasks['pilotEnabled'] is False and tasks['operationalDecision'] == 'NO_GO_PILOTO_REAL', 'GLOBAL_PILOT_GATE_MISMATCH')
    require(len(tasks['lots']) == 9 and {l['id'] for l in tasks['lots']} == {'L-' + str(i).zfill(2) for i in range(9)} and all(l['status'] == 'VERIFICADA' for l in tasks['lots']), 'LOT_STATUS_MISMATCH')
    lot = tasks['lots'][-1]
    require(lot['id'] == 'L-08', 'LAST_LOT_MUST_BE_L08')
    require(lot['contractVersion'] == '0.9.0' and not lot['pilotEnabled'] and not lot['deploymentAuthorized'], 'SCOPE_MISMATCH')
    require(len(lot['verifiedLocalCriteria']) == 12 and set(lot['verifiedLocalCriteria']) == {'CI-L08-' + str(i).zfill(2) for i in range(1,13)}, 'LOCAL_CRITERIA_SET_MISMATCH')
    require(len(lot['pendingFieldCriteria']) == 6 and set(lot['pendingFieldCriteria']) == {'CI-L08-' + str(i).zfill(2) for i in (4,5,6,7,10,11)}, 'FIELD_CRITERIA_SET_MISMATCH')
    require(lot['TEST_CLEANUP_RELEASED'], 'CASE_NOT_RELEASED')
    mobile = read(BASE / 'mobile/resultado-mobile.json')
    require(all(c['exitCode'] == 0 for c in mobile['checks']), 'MOBILE_CHECK_FAILED')
    signatures = list((BASE / 'release').glob('closure-signature-*/signature-verification.json'))
    require(len(signatures) == 1, 'ONE_EXACT_SIGNED_ACTA_REQUIRED')
    signature = read(signatures[0])
    require(signature['document'] == 'docs/actas/L-08-cierre-funcional-global.md', 'SIGNATURE_MUST_BIND_EXACT_L08_ACTA')
    require(signature['classification'] == 'FIRMA_TECNICA_AUTOMATIZADA_CANDIDATO_LOCAL' and signature['legalTitleholderSignature'] is False, 'TECHNICAL_NOT_TITLEHOLDER_SIGNATURE_REQUIRED')
    require(signature['result'] == 'PASS_PUBLIC_CERTIFICATE_VERIFICATION_AND_ONE_BYTE_REJECTION', 'SIGNATURE_RESULT_FAILED')
    require(signature['modifiedOneByteRejected'] and signature['verifiedWithPublicCertificateOnly'], 'SIGNATURE_CHECK_FAILED')
    require(sha(ROOT / signature['document']) == signature['documentSha256'], 'SIGNED_ACTA_CHANGED')
    require(sha(signatures[0].parent / 'acta.sig') == signature['signatureSha256'], 'SIGNATURE_BYTES_CHANGED')
    require(sha(signatures[0].parent / 'candidate-public-cert.der') == signature['certificateSha256'], 'PUBLIC_CERTIFICATE_CHANGED')
    independent_files = list(signatures[0].parent.glob('independent-verification-*.json'))
    require(independent_files, 'INDEPENDENT_PUBLIC_SIGNATURE_VERIFICATION_REQUIRED')
    for p in independent_files:
        independent = read(p)
        require(independent['result'] == signature['result'] and independent['modifiedOneByteRejected'] and independent['verifiedWithPublicCertificateOnly'], 'INDEPENDENT_SIGNATURE_CHECK_FAILED')
        for field in ('documentSha256', 'signatureSha256', 'certificateSha256'):
            require(independent[field] == signature[field], 'INDEPENDENT_SIGNATURE_CHECK_WRONG_FILE')
    qa = read(BASE / 'qa-final-rendered.json')
    require(qa['result'] == 'PASS_LOCAL_CANDIDATE_DOCUMENTS_NO_BLOCKERS', 'INDEPENDENT_DOCUMENT_REVIEW_REQUIRED')
    for document in qa['reviewedFiles']:
        require(sha(ROOT / document['path']) == document['sha256'], 'REVIEWED_DOCUMENT_CHANGED')
    qa_seal = read(BASE / 'qa-seal-script-final.json')
    require(qa_seal['result'] == 'PASS_SEAL_SCRIPT_REVIEW_AFTER_CORRECTIONS' and qa_seal['sourceSha256'] == sha(Path(__file__)), 'SEAL_SCRIPT_INDEPENDENT_REVIEW_REQUIRED')
    artifact = read(ROOT / 'artifacts/L-08-entrega-final/manifest.json')
    require(artifact['productionRelease'] is False and artifact['pilotEnabled'] is False, 'ARTIFACT_MUST_REMAIN_LOCAL_ONLY')
    require(artifact['android']['signerSha256'] == signature['certificateSha256'], 'SIGNATURE_NOT_CANDIDATE_CERT')
    for kind in ('android', 'backend'):
        item = artifact[kind]
        require(sha(ROOT / 'artifacts/L-08-entrega-final' / item['artifact']) == item['sha256'], 'ARTIFACT_HASH_MISMATCH')

def seal():
    require(not MANIFEST.exists(), 'REFUSE_REGENERATING_FROZEN_MANIFEST')
    guards()
    count_links = links()
    for source in SOURCES:
        require(sha(ROOT / source) == sha(BASE / 'sources' / source), 'SOURCES_NOT_PREPARED_OR_CHANGED')
    files = set(p for p in BASE.rglob('*') if p.is_file() and p.name not in ('manifest-cierre.sha256', 'verificacion-cierre.json'))
    files.update(ROOT / p for p in DOCUMENTS + SOURCES)
    files.update(p for p in (ROOT / 'artifacts/L-08-entrega-final').rglob('*') if p.is_file())
    files.update(p for p in (ROOT / 'memory/backup/2026-10-23-L08-candidato-final').rglob('*') if p.is_file())
    for nested in (ROOT / 'backups/L-08-higiene-2026-10-23/manifest.sha256', BASE / 'ops/manifest-ops-final.sha256'):
        files.add(nested)
        for relative, digest in manifest_entries(nested).items():
            file = ROOT / relative
            require(sha(file) == digest, 'NESTED_ARCHIVE_CHANGED')
            files.add(file)
    for file in files:
        permitted_file(file)
    entries = [(p.relative_to(ROOT).as_posix(), sha(p)) for p in sorted(files)]
    with MANIFEST.open('x', encoding='utf-8', newline='\n') as f:
        f.writelines(digest + '  ' + path + '\n' for path, digest in entries)
    proof = verify(require_existing_proof=False)
    proof['localMarkdownLinksChecked'] = count_links
    with (BASE / 'verificacion-cierre.json').open('x', encoding='utf-8') as f:
        json.dump(proof, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(json.dumps(proof))

def verify(require_existing_proof=True):
    require(MANIFEST.exists(), 'FROZEN_MANIFEST_REQUIRED')
    entries = manifest_entries(MANIFEST)
    mandatory = set(DOCUMENTS + SOURCES)
    mandatory.update('docs/evidencia/L-08/incremento-5-candidato-final/sources/' + s for s in SOURCES)
    mandatory.update('artifacts/L-08-entrega-final/' + name for name in ('manifest.json','SHA256SUMS.txt','Ruta-Fija-Conductor-candidato-local-release.apk','ruta-fija-backend-candidato-local.jar'))
    require(mandatory.issubset(entries), 'MANIFEST_TRUNCATED_REQUIRED_ENTRIES_MISSING')
    for nested in (ROOT / 'backups/L-08-higiene-2026-10-23/manifest.sha256', BASE / 'ops/manifest-ops-final.sha256'):
        require(nested.relative_to(ROOT).as_posix() in entries, 'NESTED_MANIFEST_MISSING')
        for relative, digest in manifest_entries(nested).items():
            require(entries.get(relative) == digest, 'NESTED_MANIFEST_ENTRY_MISSING_OR_CHANGED')
    proof_file = BASE / 'verificacion-cierre.json'
    if require_existing_proof:
        require(proof_file.is_file(), 'SEALED_PROOF_REQUIRED_FOR_VERIFY')
        proof = read(proof_file)
        require(proof['manifestSha256'] == sha(MANIFEST) and proof['filesVerified'] == len(entries), 'MANIFEST_DIFFERS_FROM_SEALED_PROOF')
    checked, errors = 0, []
    for relative, digest in entries.items():
        path = (ROOT / relative).resolve()
        permitted_file(ROOT / relative)
        if not path.is_relative_to(ROOT) or not path.is_file() or sha(path) != digest:
            errors.append(relative)
        checked += 1
    require(not errors, 'FROZEN_ARCHIVE_MISMATCH: ' + json.dumps(errors))
    return {'verifiedAtUtc': dt.datetime.now(dt.timezone.utc).isoformat(), 'result':'PASS_FROZEN_LOCAL_CANDIDATE_ARCHIVE', 'filesVerified':checked,'manifestSha256':sha(MANIFEST),'mismatches':errors,'localDecision':'GO PARA CANDIDATO LOCAL DE ENTREGA','operationalDecision':'NO_GO_PILOTO_REAL','pilotEnabled':False,'signatureClassification':'TECHNICAL_AUTOMATED_NOT_HUMAN','privateKeysIncluded':False}

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('action', choices=('prepare-sources','seal','verify','links'))
    action = p.parse_args().action
    if action == 'prepare-sources':
        prepare()
    elif action == 'seal':
        seal()
    elif action == 'links':
        print(json.dumps({'linksChecked':links()}))
    else:
        print(json.dumps(verify()))
