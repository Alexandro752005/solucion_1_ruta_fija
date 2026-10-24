# Acta de cierre funcional global L-08 — candidato local de entrega

**2026-10-23 America/Lima. VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL.**

**Decisión: GO PARA CANDIDATO LOCAL DE ENTREGA. Decisión operativa real: NO_GO_PILOTO_REAL.**

## Autoridad y alcance

La instrucción maestra expresa del usuario autoriza auditoría, resolución técnica, trabajo con agentes y cierre del candidato local. El [contrato 0.9.0](../contratos/L-08.md), la [matriz](../evidencia/L-08/matriz-L08.md) y la [autorización](../evidencia/L-08/incremento-5-candidato-final/autorizacion-alcance-local.json) establecen la frontera actual. El alcance integral 0.8.0 queda conservado como antecedente y mantiene sus requisitos de campo pendientes. L-00 a L-07 conservan su estado y evidencias previas; esta acta no los revalida mediante pruebas nuevas ni atribuye firmas humanas anteriores.

## Resultados que respaldan la decisión

- **Higiene:** solo 5432 escuchaba entre los puertos solicitados al iniciar. Se archivaron 260 residuos antiguos sin referencia, con SHA-256 y mapa de origen/destino: 10.583.186 bytes. Las evidencias referenciadas y dependencias se conservan.
- **Preservación:** 1.288 entradas reconciliadas; cuatro documentos mutables de L-07 conservados en respaldos con el hash original. Final3 SHA `3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b` y términos SHA `20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417` intactos.
- **Postflight de solo lectura:** V50 con 51 migraciones exitosas; ledger 1/3/8, dos aceptaciones, 85 puntos GPS históricos, lock libre, marcador y secreto del caso ausentes, recursos retirados y `TEST_CLEANUP_RELEASED=true`. Sin escrituras en 5432 ni limpieza global.
- **Móvil:** 56 pruebas de ubicación aprobadas, incluidas seis nuevas de falta de fix simulada; análisis sin incidencias. El controlador y la UI reales usan reloj y proveedor sintéticos. Tras más de 120 s muestran señal degradada sin terminar el servicio, inventar coordenadas 0,0 ni almacenar reenvíos. FIS02 físico conserva batería de 30 a 28 % en 959 s sin carga, controles GPS/red/primer plano y TalkBack/escala al 150 % confirmados por el usuario. La pérdida física de señal sigue sin observarse.
- **Continuidad:** AES-256/GCM con dump real de datos sintéticos; corrupción y clave equivocada rechazadas. Restauración íntegra de dos tablas y 101 filas. RTO SQL local: **415,026 s**, incluyendo fallos e intervención sin reiniciar el reloj. Detección de caída PG: **2,117 s**; conjunto HTTP/PG corregido: **8,413 s**. No equivale a pérdida total del servidor o guardia independiente. REST03 conserva su resultado histórico de 318,731 s y 105 tablas/API.
- **Entrega:** APK release v5 firmada con certificado candidato distinto de debug; JAR V50 reutilizado y sus 672 entradas contrastadas. La reproducción con el mismo certificado produjo una APK idéntica byte a byte. [Manifiesto](../../artifacts/L-08-entrega-final/manifest.json).
- **Participación Java:** 25.110 de 48.450 líneas no vacías de producto: **51,83 %**. El resultado original, 40,50 %, mezclaba pruebas Dart. Se corrigió la comparabilidad del script sin inflar código. No se certifica un umbral académico no proporcionado.

APK SHA-256: `a4685115e712cfe0c5e24ad84efc4abaf1f4f679a6f95fd1ca0b0354d7b1d776`. JAR SHA-256: `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`. Certificado Android SHA-256: `181da8d8cf0c18d56a424d0ed22fb9424a2bdf904c8e0dff7395a512c658bb45`.

## Condiciones que esta acta no habilita

El endpoint candidato `https://api.rutafija.invalid/api/v1` es reservado y no dispone de una API operativa. La release no se instaló en el POCO. La v4 debug instalada mantiene su API privada y no acepta una actualización directa con la nueva firma. Se requiere preparar la distribución y custodia definitiva antes de usarla en campo.

El [protocolo vinculante](../legal/protocolo-transicion-piloto-real.md) exige ADMIN legítimo y aceptación propia, privacidad efectiva, ceremonia personal Ed25519 en USB del titular, HTTPS y dominio, medios y clave independientes, guardia y alertas 24/7, calendario/RPO y recuperación representativa, participantes y títulos reales y pérdida física de señal. Estas condiciones siguen pendientes. No se habilitan piloto, publicación, gasto, correo externo, migraciones ni nuevos datos reales.

## Firma técnica y revisión

Emisor técnico automatizado: **A-COORD / Codex**. Revisión móvil independiente: **agente de empaquetado**; revisión de coordinación/protocolo: **agente móvil**; revisión de continuidad y firma: **A-COORD**, distinto del autor de esos cambios. Evidencia y comandos en [archivo nuevo](../evidencia/L-08/incremento-5-candidato-final/README.md).

Esta acta se firma criptográficamente con la clave **candidata local** mediante SHA256withRSA; certificado público, firma exacta, verificación y rechazo de alteración se archivan en `incremento-5-candidato-final/release/closure-signature-*/`. Es una **firma técnica automatizada de integridad**, no la firma jurídica/personal del titular, ADMIN o participantes. Ninguna de esas personas se considera firmante por esta emisión.
