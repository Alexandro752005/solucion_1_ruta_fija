"""Consolidate observed results into the explicitly authorized LOCAL candidate scope."""
import datetime as dt
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = 'docs/evidencia/L-08/incremento-5-candidato-final'

def require(condition, message='FINALIZATION_GATE_FAILED'):
    if not condition:
        raise RuntimeError(message)

def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8-sig'))

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

def write_json(path, data):
    write(path, json.dumps(data, ensure_ascii=False, indent=2) + '\n')

def sha(path):
    h = hashlib.sha256()
    with (ROOT / path).open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def main():
    # Fail before changing any coordination document, including on a rerun.
    tasks = read('docs/seguimiento/tareas.json')
    require(all(l['status'] == 'VERIFICADA' for l in tasks['lots'][:-1]))
    require(tasks['lots'][-1]['id'] == 'L-08')
    require(tasks['lots'][-1]['contractVersion'] == '0.8.0')
    coordination_files = ('docs/contratos/L-08.md', 'docs/evidencia/L-08/matriz-L08.md', 'docs/ejecucion/L-08.md', 'docs/backlog-producto-ruta-fija-v3.1.md', 'docs/seguimiento/tareas.json', 'memory/current.md', 'memory/target.md', 'specs/decisiones-L08-2026-10-21.json')
    for file in coordination_files:
        require(sha(file) == sha('memory/backup/2026-10-23-L08-candidato-final/' + file))
    require(sha('docs/contratos/L-08.md') == sha('docs/contratos/L-08-v0.8.0-antecedente.md'))
    require(sha('docs/evidencia/L-08/matriz-L08.md') == sha('docs/evidencia/L-08/matriz-L08-v0.8.0-antecedente.md'))
    mobile = read(BASE + '/mobile/resultado-mobile.json')
    require(mobile['result'] == 'PASS_LOCAL_SIMULATION_ONLY')
    require(all(c['exitCode'] == 0 for c in mobile['checks']))
    artifact = read('artifacts/L-08-entrega-final/manifest.json')
    require(not artifact['productionRelease'] and not artifact['pilotEnabled'])
    apk, jar = artifact['android'], artifact['backend']
    require(apk['buildType'] == 'release' and not apk['installed'])
    for item in (apk, jar):
        require(sha('artifacts/L-08-entrega-final/' + item['artifact']) == item['sha256'])
    results = [(p, json.loads(p.read_text(encoding='utf-8-sig'))) for p in (ROOT / BASE / 'ops').glob('*/result.json')]
    passing = [(p, r) for p, r in results if r.get('result') == 'PASS_LOCAL_CONTINUITY_CANDIDATE_WITH_EXTERNAL_PREREQUISITES']
    require(passing, 'CONTINUITY_EXECUTED_PASS_REQUIRED')
    opath, ops = passing[-1]
    opsref = opath.relative_to(ROOT).as_posix()
    corrected_postflight = json.loads((opath.parent / 'postflight-final-corrected.json').read_text(encoding='utf-8-sig'))
    correction = json.loads((opath.parent / 'launcher-child-correction.json').read_text(encoding='utf-8-sig'))
    require(corrected_postflight['allOwnPortsFree'] and corrected_postflight['ownedChildJvmAbsent'])
    # Initial 2.117s observation proved PostgreSQL down only. Use the amended
    # all-probe observation after the exact child JVM was actually stopped.
    ops = dict(ops)
    ops['httpAndPgStoppedFailureDetectedSeconds'] = correction['observedHttpFailureDetectionSeconds']
    require(ops['ownedClusterStopped'])
    require(ops['rtoSecondsNoClockReset'] <= 14400)
    hygiene = read(BASE + '/hygiene/preservacion.json')
    require(hygiene['result'] == 'PASS' and not hygiene['mismatches'])
    cleanup = read(BASE + '/hygiene/limpieza-conservadora.json')
    metric = read(BASE + '/hygiene/participacion-java-producto.json')
    qam = read(BASE + '/release/qa-mobile.json')
    require(qam['result'] == 'PASS_NO_BLOCKING_FINDINGS_FOR_LOCAL_CANDIDATE')
    for source in qam['reviewedSources']:
        require(sha(source['path']) == source['sha256'])
    qac = read(BASE + '/mobile/qa-coordination.json')
    require(qac['result'] == 'PASS_REVIEW_NO_BLOCKERS')
    for source in qac['reviewedFiles']:
        require(sha(source['path']) == source['sha256'])
    rebuilds = list((ROOT / BASE / 'release').glob('rebuild-*.json'))
    require(rebuilds, 'REPRODUCTION_EVIDENCE_REQUIRED')
    rebuild = json.loads(rebuilds[-1].read_text(encoding='utf-8-sig'))
    require(rebuild['result'] == 'PASS_RELEASE_SIGNED_CANDIDATE_LOCAL_NOT_INSTALLED')
    require(rebuild['sameCertificate'] and rebuild['byteIdenticalApk'])
    require(rebuild['apkSha256'] == apk['sha256'] and rebuild['signerSha256'] == apk['signerSha256'])
    time = dt.datetime.now(dt.timezone.utc).isoformat()
    author = {
        'localDate': '2026-10-23', 'timezone': 'America/Lima',
        'authority': 'Instrucción maestra expresa del usuario en esta sesión',
        'literalScope': 'GO PARA CANDIDATO LOCAL DE ENTREGA',
        'literalClassification': 'VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL',
        'requestedGpsEvidence': 'simulación de pérdida prolongada de fix o sin actualización de coordenadas por >120 s',
        'formalTransitionRequested': 'anexo vinculante que separa formalmente la verificación técnica local del despliegue en campo',
        'explicitAgentWorkAuthorized': True, 'costPEN': 0,
        'scopeChange': 'La aceptación del candidato local se separa de la aceptación operativa real original; no se convierten documentos, mocks o autorizaciones en hechos físicos',
        'fieldCriteriaPreserved': 'docs/evidencia/L-08/matriz-L08-v0.8.0-antecedente.md',
        'productionDeployment': False, 'pilotEnabled': False,
    }
    write_json(BASE + '/autorizacion-alcance-local.json', author)
    contract = '''# Contrato L-08 — cierre del candidato local y transición al piloto

Versión **0.9.0**, 2026-10-23 America/Lima. **VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL. GO PARA CANDIDATO LOCAL DE ENTREGA. Piloto real: NO_GO.**

## Alcance autorizado y cambio respecto de 0.8.0

La instrucción maestra del usuario autoriza cerrar el candidato **local**, añadir simulación prolongada de no-fix, formalizar continuidad, empaquetar release y condicionar el encendido real a un protocolo posterior. Esta decisión cambia la frontera de aceptación: no acredita las condiciones físicas/operativas originales. La [autorización saneada](../evidencia/L-08/incremento-5-candidato-final/autorizacion-alcance-local.json), el [contrato 0.8.0 exacto](L-08-v0.8.0-antecedente.md) y la [matriz anterior exacta](../evidencia/L-08/matriz-L08-v0.8.0-antecedente.md) conservan su procedencia y requisitos íntegros.

La salida local exige los doce criterios documentados y evidencia ejecutada de los mecanismos: regresión y carga históricas preservadas, ensayo físico FIS02 cerrado, respuesta a no-fix simulado, cifrado/restauración local nuevos, sondas/antigüedad de respaldo ejecutables, APK release firmada distinta de debug y manifiesto JAR/APK. La matriz distingue el estado local del gate real de cada criterio. No se exige inventar un medio, guardia, participante o dominio inexistentes para esta aprobación local.

## Restricciones y datos protegidos

S/0, sin Git/GitHub, monolito modular Java21/Spring Boot3.5.16, Angular22, Flutter3.47.5 y PostgreSQL16. Sin colas offline ni cambios a la autorización en servidor. No migración nueva, cuenta persistente nueva, limpieza global, correo/push externo, publicación, piloto ni despliegue. La generación de la firma **candidata local** está autorizada por esta instrucción; no inscribe claves de recuperación ni acredita custodia definitiva del titular.

Esquema real V50 con **51 migraciones exitosas V0…V50**: el conteo no implica V51. Base compartida `ruta_fija_test@127.0.0.1:5432`, observada SELECT-only. Ledger1/3/8, dos aceptaciones,85 puntos históricos, marcadores/privado ausentes y lock libre; TEST_CLEANUP_RELEASED=true. Los casos retirados no se reabren. Los clústeres históricos55438/55439/55440 permanecen detenidos; el ensayo nuevo usa exclusivamente55441 y se detiene al salir.

Final3, términosADMIN1.0.0, actas/evidencias anteriores y sus manifiestos no se regeneran ni alteran. Los documentos globales de coordinación evolucionan con respaldo exacto. Los residuos se archivan con hashes y mapa origen/destino antes de retirarlos, sin borrado recursivo.

## Frontera de la entrega

La APK release del candidato está firmada con clave local protegida, usa un endpoint HTTPS reservado `.invalid` sin servicio real y no se instala en POCO. La APKdebugv4 física queda como instalación vigente del laboratorio; sus pruebas físicas no se atribuyen a la release. La firma distinta impide una actualización directa: requiere protocolo futuro de distribución/migración consentida. JAR V50 reutilizado con hash y clases/recursos contrastados; no se presenta como una nueva ejecución integrada del backend.

La simulación no acredita pérdida satelital física. El nuevo ensayo SQL sintético no acredita pérdida total del servidor y reconexión de clientes. Una ruta/copias de la misma PC no acreditan independencia, ni un calendario propuesto equivale a respaldo continuo. DPAPI local no sustituye custodia portable de clave. Los criterios originales de detección≤5min, recuperación ordinaria≤30min, RTO≤4h y RPO≤1h siguen vigentes para campo.

## Salida y prerrequisitos reales

La [matriz vigente](../evidencia/L-08/matriz-L08.md) respalda la salida local y el [acta](../actas/L-08-cierre-funcional-global.md) registra firma técnica automatizada verificable, sin suplantar firma humana. El [protocolo vinculante](../legal/protocolo-transicion-piloto-real.md) mantiene gates de privacidad/ADMIN legítimo, ceremonia del titular en USB, TLS/dominio, firma/custodia definitiva, medios/guardia/alertas independientes, RPO y recuperación representativa, personas/unidades y no-fix físico. **GO_LOCAL y NO_GO_PILOTO_REAL son decisiones diferentes; no se emite GO de producción.**
'''
    write('docs/contratos/L-08.md', contract)
    rows = [
        ('01', 'Candidato trazable/reproducible', 'Releasev5, certificado distinto de debug, JAR V50 contrastado y manifiesto; reproducción misma clave registrada', 'Firma/custodia y endpoint efectivos antes de campo'),
        ('02', 'Roles/objetos/transiciones y exclusión offline', 'Regresiones históricas conservadas;56 location actuales (incluyen6 nuevos), analyze sin incidencias; no cola', 'Sin nueva prueba integrada completa de todos los módulos'),
        ('03', 'Carga25/p95 durable≤2s', 'CAP25 histórico intacto:100 creaciones+100 aceptaciones;343,0684/461,8702ms', 'Una PC/loopback, no carga de infraestructura real'),
        ('04', 'Sondas/contingencia y detección≤5min', 'Sondas ejecutadas HTTP sintético/PG real; fallo controlado detectado; procedimiento24/7 y cota105s bajo precondiciones', 'Host independiente, DNS acotado, alertas entregadas/guardia nominal24/7 y recuperación real≤30min pendientes'),
        ('05', 'Restauración/RTO≤4h', f"Nuevo SQL sintético: {ops['rtoSecondsNoClockReset']:.3f}s incluyendo fallos/reintentos; histórico REST03:105tablas/API y318,731s", 'Pérdida total/medio independiente/clave portable/clientes representativos pendientes'),
        ('06', 'Respaldo cifrado y cálculoRPO≤1h', 'pg_dump→AES256/GCM ejecutado, tag alterado/clave errónea rechazados, gate de edad; diseño1800+900=2700s', 'Copia mismaPC; medio/clave independientes, tarea horaria/verificación diaria sostenidas y RPO operativo pendientes'),
        ('07', 'Android/GPS/red/batería y falta de fix', 'FIS02 físico cerrado conservado;6 simulaciones nuevas>120s, servicio preservado, sin0,0 ni replay', 'No-fix físico NO OBSERVADO; releasev5 no instalada físicamente'),
        ('08', 'Chrome/Edge/zoom/formularios', 'Histórico12combinaciones/66páginas/804checks preservado', 'Reinventariar equipos/navegadores de participantes reales'),
        ('09', 'Accesibilidad dirigida', 'FIS02 TalkBack/escala150% humanos, contraste corregido y pruebas dirigidas preservados', 'Sin certificación integral de accesibilidad'),
        ('10', 'Secretos/entrega Android firmada', 'Releasev5 real, firma RSA3072 no debug, ACL user+SYSTEM, APIHTTPS.invalid separada, no instalada', 'Clave candidata local; custodia/distribución/firma definitiva y dominio real pendientes'),
        ('11', 'Retiro y protocolo de transición', 'Postflight SELECT-only conserva ledger/aceptaciones/85puntos; anexo vinculante PR01…06 preparado', 'Aceptación propia ADMIN, privacidad, ceremonia USB titular, títulos/personas/HTTPS reales pendientes'),
        ('12', 'Concordancia/preservación/decisión', '1288 entradas históricas reconciliadas (4 documentos mutables en copias exactas); archivo/acta/firma técnica local', 'Decisión campo independiente NO_GO; no aceptación personal inferida'),
    ]
    matrix = '''# Matriz vigente de aceptación — L-08

2026-10-23 America/Lima. Contrato **0.9.0**. **VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL. GO PARA CANDIDATO LOCAL DE ENTREGA. NO_GO_PILOTO_REAL.**

La instrucción maestra autoriza esta frontera **local**. Cada `VERIFICADO_LOCAL_CANDIDATO_FINAL` acredita el mecanismo/documentación/ensayo de la tercera columna dentro de sus límites. No afirma cumplir en campo la totalidad del requisito original. Se conserva la [matriz0.8.0 exacta](matriz-L08-v0.8.0-antecedente.md) y el [contrato0.9.0](../../contratos/L-08.md) registra expresamente la evolución. No se reetiqueta una simulación como observación física.

| ID | Criterio conservado | Estado del candidato local | Evidencia comprobada | Condición/límite de campo |
|---|---|---|---|---|
'''
    for id, criterion, evidence, limit in rows:
        matrix += f'| CI-L08-{id} | {criterion} | VERIFICADO_LOCAL_CANDIDATO_FINAL | {evidence} | {limit} |\n'
    matrix += f'''
## Índice de pruebas y procedencia

- [Archivo nuevo y comandos](incremento-5-candidato-final/README.md), [higiene/preservación](incremento-5-candidato-final/hygiene/preservacion.json), [postflight de solo lectura](incremento-5-candidato-final/hygiene/postflight-salida.txt).
- [GPS simulado y56regresiones](incremento-5-candidato-final/mobile/resultado-mobile.json), [revisión independiente](incremento-5-candidato-final/release/qa-mobile.json). Las6 nuevas están incluidas en56; no son62 casos diferentes.
- [Continuidad nueva]({opsref.removeprefix('docs/evidencia/L-08/')}), [procedimiento backup](../../operacion/L-08-respaldo-independiente.md), [monitoreo](../../operacion/L-08-monitoreo-operativo.md).
- [Release y receta](incremento-5-candidato-final/release/README.md), [JAR/APK/manifiesto](../../../artifacts/L-08-entrega-final/manifest.json).
- [FIS02 físico histórico](incremento-4-cierre-fisico/README.md), [protocolo real](../../legal/protocolo-transicion-piloto-real.md), [acta local](../../actas/L-08-cierre-funcional-global.md).

**Aceptación operativa original: NO_GO.** Pendientes de campo: no-fix físico; monitor/alertas/guardia independientes24/7; medio/clave independientes, calendario/RPO y restauración total representativa; custodia/firma/distribución definitiva; privacidad, ADMIN/titular/personas/organizaciones/títulos y HTTPS efectivos. El acta local no habilita estos recursos.
'''
    write('docs/evidencia/L-08/matriz-L08.md', matrix)
    summary = ('L-00…L-08 VERIFICADAS dentro de su alcance funcional local; L-08 contrato0.9.0, '
               'FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL y GO PARA CANDIDATO LOCAL DE ENTREGA. '
               'NO_GO_PILOTO_REAL: gates reales separados en protocolo de transición. '
               'V50/51migraciones, ledger1/3/8/aceptaciones2/85puntos preservados, TEST_CLEANUP_RELEASED=true. '
               'APKreleasev5 firmada candidata, endpointHTTPS.invalid sin servicio, no instalada; POCO conserva debugv4. '
               '56tests location (incluyen6nuevos), analyze correcto, continuidad SQL aislada y firma/archivo local trazables.')
    for file in ('docs/backlog-producto-ruta-fija-v3.1.md', 'docs/ejecucion/L-08.md', 'memory/current.md', 'memory/target.md'):
        old = (ROOT / file).read_text(encoding='utf-8-sig')
        write(file, '> **Corte vigente 2026-10-23, L08.5/0.9.0:** ' + summary + ' Los cortes inferiores son antecedentes históricos.\n\n' + old)
    lot = tasks['lots'][-1]
    lot.update(status='VERIFICADA', contractVersion='0.9.0', verifiedLocalCriteria=['CI-L08-' + str(i).zfill(2) for i in range(1,13)], pendingLocalCriteria=[], partialLocalCriteria=[], blockedLocalCriteria=[], pilotEnabled=False, deploymentAuthorized=False, newPersistentCaseAuthorized=False, lastEvidenceAt='2026-10-23', latestIncrement='L08.5-CANDIDATO_FINAL_LOCAL', localStatusNote=summary, latestValidationEvidence=BASE + '/README.md', integralReview=BASE + '/README.md', schemaVersion=50, validatedMigrations=51)
    lot['closure'] = {'status': 'FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL', 'scope': 'LOCAL_CANDIDATE_ONLY', 'localDecision': 'GO PARA CANDIDATO LOCAL DE ENTREGA', 'operationalDecision': 'NO_GO_PILOTO_REAL', 'act': 'docs/actas/L-08-cierre-funcional-global.md', 'realPrerequisites': 'docs/legal/protocolo-transicion-piloto-real.md', 'signature': 'TECHNICAL_AUTOMATED_NOT_HUMAN', 'closedAtUtc': time}
    lot['pendingFieldCriteria'] = ['CI-L08-04','CI-L08-05','CI-L08-06','CI-L08-07','CI-L08-10','CI-L08-11']
    lot['evidence'].append(BASE + '/README.md') if isinstance(lot.get('evidence'), list) else None
    tasks.update(status='VERIFICADA_LOCAL_CANDIDATO_FINAL', pilotEnabled=False, operationalDecision='NO_GO_PILOTO_REAL')
    write_json('docs/seguimiento/tareas.json', tasks)
    decisions = read('specs/decisiones-L08-2026-10-21.json')
    decisions.update(version='0.9.0', status='VERIFICADA', pilotEnabled=False, deploymentAuthorized=False, newPersistentCaseAuthorized=False, pendingScope='PILOTO_REAL_NOT_LOCAL_CANDIDATE')
    decisions['antecedentCoordination08'] = {'path':'memory/backup/2026-10-23-L08-candidato-final/specs/decisiones-L08-2026-10-21.json', 'contractVersion':'0.8.0', 'status':'EN_CURSO', 'scope':'ORIGINAL_INTEGRAL_CRITERIA_STILL_PENDING_FOR_FIELD'}
    for field in ('contractVersion','verifiedLocalCriteria','pendingLocalCriteria','partialLocalCriteria','blockedLocalCriteria','pendingFieldCriteria','schemaVersion','validatedMigrations','localStatusNote','latestValidationEvidence','integralReview','lastEvidenceAt'):
        decisions[field] = lot[field]
    decisions['latestIncrement'] = BASE + '/README.md'
    decisions['finalDeliveryArtifacts'] = 'artifacts/L-08-entrega-final/manifest.json'
    decisions['localCandidateClosure'] = lot['closure']
    decisions['decisions'].append({'id':'L08-CIERRE-CANDIDATO-LOCAL-20261023','status':'CONFIRMADA_USUARIO','evidence':BASE+'/autorizacion-alcance-local.json','scope':'GO_LOCAL_CANDIDATE_NOT_PILOT','previousIntegralCriteriaPreserved':True})
    write_json('specs/decisiones-L08-2026-10-21.json', decisions)
    act = f'''# Acta de cierre funcional global L-08 — candidato local de entrega

**2026-10-23 America/Lima. VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL.**

**Decisión: GO PARA CANDIDATO LOCAL DE ENTREGA. Decisión operativa real: NO_GO_PILOTO_REAL.**

## Autoridad y alcance

La instrucción maestra expresa del usuario autoriza auditoría, resolución técnica, trabajo con agentes y cierre del candidato local. El [contrato0.9.0](../contratos/L-08.md), la [matriz](../evidencia/L-08/matriz-L08.md) y la [autorización](../evidencia/L-08/incremento-5-candidato-final/autorizacion-alcance-local.json) establecen la frontera actual. El alcance integral0.8.0 queda conservado como antecedente y mantiene sus requisitos de campo pendientes. L-00…L-07 conservan su estado y evidencias previas; esta acta no los revalida mediante pruebas nuevas ni inventa firmas humanas anteriores.

## Resultados que respaldan la decisión

- Higiene: solo5432 escuchaba en puertos solicitados al iniciar. {cleanup['archivedCount']} residuos antiguos sin referencia archivados con SHA-256/mapa origen-destino ({cleanup['archivedBytes']}bytes); evidencias referenciadas y dependencias se conservan.
- Preservación:1288entradas reconciliadas;4documentos mutables L07 recuperados por su hash exacto en respaldos. Final3 SHA `{hygiene['protected']['baseSnapshotSha256']}` y términos SHA `{hygiene['protected']['termsSha256']}` intactos.
- Postflight SELECT-only actual: V50,51migraciones exitosas,ledger1/3/8,aceptaciones2,85GPS históricos,lock libre,marcador/privado ausentes,recursos retirados,TEST_CLEANUP_RELEASED=true. No escritura5432 ni limpieza global.
- Móvil actual:56pruebas location aprobadas (incluyen6nuevas de no-fix simulado), analyze sin incidencias. Controlador/UI reales con reloj y proveedor sintéticos;>120s degrada señal sin terminar servicio, inventar0,0 ni almacenar replay. FIS02 físico anterior conserva batería30→28%/959s,controlesGPS/red/primerplano y TalkBack/escala150% humanos; no-fix físico no observado.
- Continuidad nueva: AES256/GCM con dump real sintético, corrupción/clave equivocada rechazadas y restauración íntegra de2tablas/101filas. RTO SQL local `{ops['rtoSecondsNoClockReset']:.3f}s`, reloj conservado incluyendo fallos/intervención; detección controlada `{ops['httpAndPgStoppedFailureDetectedSeconds']:.3f}s`. No equivale a pérdida total del servidor o guardia independiente. REST03 histórico318,731s/105tablas/API conserva su alcance previo.
- Entrega: APKreleasev{apk['versionCode']} firmada con certificado candidato no debug; JARV50 reutilizado y entradas contrastadas. Reproducción con mismo certificado: bit idéntica `{rebuild['byteIdenticalApk']}`. [Manifest](../../artifacts/L-08-entrega-final/manifest.json).
- Participación Java:25110/48450líneas no vacías de producto = {metric['JavaPercentage']}%; original40,50% mezclaba testsDart. Se corrigió la comparabilidad del script sin inflar código. No se certifica un umbral de rúbrica académica no proporcionado.

APK SHA-256: `{apk['sha256']}`. JAR SHA-256: `{jar['sha256']}`. Certificado Android SHA-256: `{apk['signerSha256']}`.

## Condiciones que esta acta no habilita

Endpoint candidato `{apk['apiBaseUrl']}` reservado sin API operativa; no instalación de release enPOCO. La v4 debug instalada mantiene su API privada y no acepta actualización directa con la nueva firma. Requiere preparación de distribución y custodia definitiva antes de campo.

El [protocolo vinculante](../legal/protocolo-transicion-piloto-real.md) exige ADMIN legítimo/aceptación propia, privacidad efectiva, ceremonia personal Ed25519/USB del titular, HTTPS/dominio, medios/clave independientes, guardia/alertas24/7, calendario/RPO y recuperación representativa, participantes/títulos reales y no-fix físico. Estos gates siguen pendientes. No activación del piloto, publicación, costo, correo externo, migración nueva ni datos reales adicionales.

## Firma técnica y revisión

Emisor técnico automatizado: **A-COORD / Codex**. Revisión móvil independiente: **agente de empaquetado**; revisión de coordinación/protocolo: **agente móvil**; revisión de continuidad y firma: **A-COORD**, distinto del autor de esos cambios. Evidencia y comandos en [archivo nuevo](../evidencia/L-08/incremento-5-candidato-final/README.md).

Esta acta se firma criptográficamente con la clave **candidata local** mediante SHA256withRSA; certificado público, firma exacta, verificación y rechazo de alteración se archivan en `incremento-5-candidato-final/release/closure-signature-*/`. Es una **firma técnica automatizada de integridad**, no la firma jurídica/personal del titular, ADMIN o participantes. Ninguna de esas personas se considera firmante por esta emisión.
'''
    write('docs/actas/L-08-cierre-funcional-global.md', act)
    report = f'''# Informe final de sustentación académica y técnica — Ruta Fija V3.1

2026-10-23, America/Lima. LotesL-00…L-08 **VERIFICADAS en su alcance funcional local**. L-08: **FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL**. **GO PARA CANDIDATO LOCAL DE ENTREGA; NO_GO_PILOTO_REAL.**

## Objetivo y arquitectura

Ruta Fija coordina acceso, incorporación, organizaciones, unidades, programación/asignación, inicio/cierre y seguimiento autorizado del servicio, incidencias/comunicados, reportes y auditoría. Conserva un monolito modular con API compartida y controles de rol/organización/objeto en servidor. Roles: SUPER_ADMIN,ADMIN,CONDUCTOR. Stack conservado: Java21/SpringBoot3.5.16,Angular22,Flutter3.47.5,PostgreSQL16;FlywayV50 con51migraciones exitosas. S/0 y trazabilidad local SHA-256; sin Git/GitHub ni publicación.

La ubicación se captura bajo servicio iniciado, consentimiento/permiso y primer plano. No hay capacidad/cola offline: una acción se confirma tras respuesta del servidor; una respuesta incierta se resuelve consultando el estado existente. La falta de fix conserva el servicio y muestra un aviso después de120s, observado en el siguiente ciclo de30s.

## Resultados y comandos comprobables

| Verificación | Comando/entrada ejecutada | Resultado y procedencia |
|---|---|---|
| Puertos/procesos | `Get-NetTCPConnection -State Listen`; `Get-Process` | Inicial:5432único de5432/8080/4300/4311/55438/55439/55440. Inventario sin detener procesos ajenos |
| Inmutables | `python scripts/audit_l08_local.py preserve` |1288entradas reconciliadas;4mutables históricos en respaldo exacto;final3/términos íntegros |
| Limpieza conservadora | `python scripts/audit_l08_local.py archive` |{cleanup['archivedCount']}archivos/{cleanup['archivedBytes']}bytes archivados;mapa+hashes;sin borrado global |
| Base/locks | Java21 `L08PhysicalFinalPostflight` copia de salida nueva |SELECT-only,ledger1/3/8,aceptaciones2,85puntos,lock libre,caso retirado,V50/51migraciones |
| Java académico | `scripts/medir-cumplimiento-java.ps1` |Product:Java25110,TS8529,Dart14811=51,83%;resultado original40,50% preservado. Umbral de rúbrica no proporcionado |
| GPSsimulado | `flutter test --no-pub --reporter expanded test/features/location` |56PASS,incluyen6nuevos;temporizador/controlador/UIreales,proveedor/reloj sintéticos |
| Análisis móvil | `flutter analyze --no-pub` |No issues found,exit0 |
| Continuidad | `scripts/Test-RutaFijaL08Continuity.ps1 -Execute` |PG16nuevo55441,2tablas/101filas restauradas,tag/clave erróneos rechazados;RTOlocal{ops['rtoSecondsNoClockReset']:.3f}s,sin reiniciar reloj |
| Sondas | `scripts/Watch-RutaFijaL08Health.ps1 -Action Probe -Execute ...` |HTTPsintético+PGreal aislado,fallo detectado{ops['httpAndPgStoppedFailureDetectedSeconds']:.3f}s;cota105s bajo precondiciones |
| Firma/empaquetado | `scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute` y `-VerifyRebuild` |Releasev5,no debug,certificado estable,reproducción bitidéntica={rebuild['byteIdenticalApk']};sin instalación |

Los comandos completos, fallos iniciales y correcciones están en [incremento5]({BASE}/README.md), sin reiniciar cronómetros ni ocultar fallos del arnés. Las pruebas nuevas no se suman dos veces. Elbackend completo/CRM/carga/accesibilidad física usan evidencia histórica preservada; no se afirma nueva ejecución completa de esas suites.

## Evidencia histórica integrada con límites

CAP25:25actores/4organizaciones,100creaciones+100aceptaciones,p95durable343,0684/461,8702ms. REST03:105tablas/esquema/permisos y operaciónAPI,intervalo máximo318,731s,mismaPC. Escritorio:12combinaciones,66páginas,804checks,zoom100/200/300%. FIS02POCOAndroid13:debugv4,login/GPS/cierre/logout reales,batería30→28%/959ssin carga,TalkBack/escala150% humanos,contrastecorregido,85puntos históricos conservados y sin envíos tras cierre. Los artefactos de cada corte conservan sus manifiestos originales.

## Entregables y reproducción

[Acta]({ 'docs/actas/L-08-cierre-funcional-global.md' }),[matriz](docs/evidencia/L-08/matriz-L08.md),[contrato0.9.0](docs/contratos/L-08.md),[manifestfinal](artifacts/L-08-entrega-final/manifest.json),[respaldo](docs/operacion/L-08-respaldo-independiente.md),[monitoreo](docs/operacion/L-08-monitoreo-operativo.md),[transición](docs/legal/protocolo-transicion-piloto-real.md).

- APKreleasecandidata SHA-256 `{apk['sha256']}`.
- JARV50 SHA-256 `{jar['sha256']}`.
- CertificadoAndroidSHA-256 `{apk['signerSha256']}`.

La APKincorpora `{apk['apiBaseUrl']}` como destino reservado sin servicio real. Puede revisarse como entrega firmada local; para uso conectado hay que configurar y probar la APIHTTPS autorizada y resolver custodia/firma/distribución. No se instala sobre lav4debug:las firmas difieren. Se preserva una sola app instalada enPOCO.

## Brechas de campo expresamente conservadas

La aprobación local no completa las obligaciones operativas originales: no-fix físico; medio y clave independientes recuperables tras pérdidaSO; respaldo horario y verificación diaria realmente sostenidos/RPO; host de monitor,alertas entregadas y guardia24/7; recuperación de pérdida total hasta clientes; dominio/certificados y custodia definitiva; privacidad/ADMINlegítimo/ceremoniaUSB/personas/unidades/títulos reales. Estos gates quedan en el protocolo vinculante y en la columna de campo de la matriz. No se simulan aceptaciones personales ni se certifica cumplimiento legal integral.

La participación Java medida es51,83% del código de producto,406archivos/25110líneas no vacías. La métrica excluye tests/archivos generados de las tres tecnologías. Esto demuestra predominio por esa medida; no acredita una rúbrica no disponible ni debe elevarse mediante código artificial.

## Integridad y responsabilidad

Final3SHA `{hygiene['protected']['baseSnapshotSha256']}`; términosADMIN1.0.0SHA `{hygiene['protected']['termsSha256']}`. Sin TRUNCATE,cuentas persistentes nuevas,migraciones o escrituras5432. Seguimiento/backlog/memoriasdelproyecto conservan sus antecedentes y reflejanGOlocal/NO_GOpiloto. Firmaacta: técnica automatizada mediante clave candidata, sin atribución personal o jurídica del titular.
'''
    write('INFORME_FINAL_SUSTENTACION_RUTA_FIJA_V3_1_L08.md', report)
    index = f'''# L-08 incremento5 — cierre del candidato local

2026-10-23 America/Lima. **GO PARA CANDIDATO LOCAL DE ENTREGA; NO_GO_PILOTO_REAL.** Contrato0.9.0. [Autorización](autorizacion-alcance-local.json),[acta](../../../actas/L-08-cierre-funcional-global.md),[matriz](../matriz-L08.md),[informe](../../../../INFORME_FINAL_SUSTENTACION_RUTA_FIJA_V3_1_L08.md).

La aprobación del candidato local se separa expresamente de las condiciones de campo de0.8.0. No se considera completado un recurso/persona/ensayofísico mediante documentación o simulación.

| Superficie | Evidencia | Resultado |
|---|---|---|
| Higiene |[Preservación](hygiene/preservacion.json),[retiro selectivo](hygiene/postflight-salida.txt),[residuos](hygiene/limpieza-conservadora.json) |1288entradas reconciliadas,4mutables históricos en backup exacto;{cleanup['archivedCount']}residuos archivados;ledger1/3/8/aceptaciones2 |
| Java |[Original](hygiene/participacion-java-original.txt),[corregida](hygiene/participacion-java-corregida.txt),[inventario](hygiene/participacion-java-producto.json) |40,50%mezclabaDarttests;51,83%producto;sin umbral académico inventado |
| Móvil |[Resultado](mobile/resultado-mobile.json),[procedimiento](mobile/README.md),[QAindependiente](release/qa-mobile.json) |56PASSincluyen6nuevos;analyzePASS;no-fixsimulado,NO físico |
| Continuidad |[ÍndiceOPS](ops/README.md),[caso]({opsref.removeprefix(BASE+'/')}) |AES256/GCM,2tablas/101filas,RTOlocal{ops['rtoSecondsNoClockReset']:.3f}s;detección{ops['httpAndPgStoppedFailureDetectedSeconds']:.3f}s |
| Release |[Receta](release/README.md),[manifest](../../../../artifacts/L-08-entrega-final/manifest.json) |v5firmadano-debug,APIHTTPS.invalidsinservicio,reproducciónbitidéntica={rebuild['byteIdenticalApk']},noinstalada |

## Comandos y secuencia

Higiene: `python scripts/audit_l08_local.py preserve`, `python scripts/audit_l08_local.py archive`, `scripts/medir-cumplimiento-java.ps1`;salidas y copia JavaSELECT-only en `hygiene/`. Las fuentes antiguas del medidor y los intentos iniciales quedan conservados.

Móvil: `flutter test --no-pub --reporter expanded test/features/location/l08_prolonged_no_fix_test.dart` (6PASS), `flutter test --no-pub --reporter expanded test/features/location` (56PASSincluidas6), `flutter analyze --no-pub` (exit0). Logs y fuentes copiadas en `mobile/`.

Continuidad: `scripts/Test-RutaFijaL08Continuity.ps1 -Execute`;los modosPlan y los casos fallidos/nuevos quedan en `ops/`. Se conserva reloj de incidente a través de fallos, sin reinicio para obtenerPASS. HTTPsintético,PG16realnuevo55441;ningunaescritura5432.

Release: `scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute` y `-VerifyRebuild`;metadata y `apksigner verify --verbose --print-certs` archivados. JARreutilizado,clases/recursoscontrastados;sin nueva migración o prueba integrada completa.

## Incidencias y límites

El primer postflight suponía erróneamenteV51;se detuvo antes de escribir y se corrigió aV50con51migraciones. La primera preservación marcó4documentos mutables de coordinaciónL07 como distintos; sus bytes originales se reconciliaron en respaldos exactos, sin alterar manifiestos. MétricaJava corregida por comparar producción con producción. Los fallos de lanzamiento de herramientas de continuidad y firma se conservan en sus áreas y no se etiquetan como éxito de producto.

Las condiciones de campo originales permanecen pendientes y explícitas: no-fixfísico,independencia/custodia/calendarioRPO/recuperaciónrepresentativa,monitor/alertas/guardia24/7 y requisitosreales de identidad/privacidad/ADMIN/titular/infraestructura. Ver [protocolo](../../../legal/protocolo-transicion-piloto-real.md). La signaturedelacta es técnicaautomatizada,nofirmahumana. La releasecandidata noactualizadirectamente ladebugv4instalada.

El manifiesto nuevo `manifest-cierre.sha256` archivará este incremento, fuentes finales, documentos coordinados y binarios. Se verifica en `verificacion-cierre.json`;no se regeneran manifiestos históricos. Claves/contraseñas/datosprivados quedan fuera del archivo público.
'''
    write(BASE + '/README.md', index)
    write_json(BASE + '/consolidacion.json', {'recordedAtUtc':time,'status':'VERIFICADA','classification':'FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL','localDecision':'GO PARA CANDIDATO LOCAL DE ENTREGA','operationalDecision':'NO_GO_PILOTO_REAL','pilotEnabled':False,'criteriaLocalVerified':12,'pendingFieldCriteria':lot['pendingFieldCriteria'],'opsEvidence':opsref,'androidSha256':apk['sha256'],'backendSha256':jar['sha256'],'javaProductPercentage':metric['JavaPercentage'],'historicalEntriesReconciled':hygiene['filesVerified']})
    print(json.dumps({'result':'DOCS_CONSOLIDATED_FROM_EXECUTED_EVIDENCE','ops':opsref,'status':lot['status']}))

if __name__ == '__main__':
    main()
