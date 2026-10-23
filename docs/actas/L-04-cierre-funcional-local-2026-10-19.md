# Acta de cierre L-04 — Ofertas y asignaciones

**2026-10-19, America/Lima. VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Alcance del [contrato0.3.0](../contratos/L-04.md) y registro v3.1 acreditado localmente. Se aplican el OK VERDE de L-04 y la autorización específica de un único CONDUCTOR sintético para el recorrido conectado. No modifica adendas ni evidencias de L-01. **L-05 NO INICIADA / NO AUTORIZADA.**

## Criterios y evidencia

- [Matriz final CI-L04-01…10](../evidencia/L-04/matriz-L04.md): oferta ADMIN propia, aceptación CONDUCTOR, deadline exacto, márgenes explícitos/candidatos, conflictos, servicio persistente, rechazo/expiración/cancelación, sustituciones con nueva aceptación, clientes, migración/retorno y conservación.
- [Incremento1](../evidencia/L-04/incremento-1/README.md): frontera de aceptación y vencimiento. [Incremento2](../evidencia/L-04/incremento-2-margenes/README.md): configuración, buffers, candidatos y dos carreras PG históricas.
- [Incremento3 y archivo](../evidencia/L-04/incremento-3-servicios/README.md):121 casos backend distintos (116 Spring/HTTP/PG rollback,4 política,1 carrera nueva),28 CRM,15 Flutter, typecheck/build/analyze, JAR y APK debug. Reejecuciones no sumadas; dos carreras V34 conservadas, sin presentarlas como reejecutadas enV35.
- [Resultado final](../evidencia/L-04/cierre/resultado.json), [fuentes actuales](../evidencia/L-04/cierre/fuentes.sha256), [manifiesto de cierre](../evidencia/L-04/manifest-cierre.sha256), [artefactos locales](../../artifacts/L-04-2026-10-19/manifest.json).

## Recorrido conectado y retorno

RUN **bbd70822-8f52-45a6-b617-9b896ff90a2c**: [resultado original](../evidencia/L-04/conectado-bbd70822-8f52-45a6-b617-9b896ff90a2c/resultado.json), [postflight independiente](../evidencia/L-04/conectado-bbd70822-8f52-45a6-b617-9b896ff90a2c/postflight.json).

Angular/Edge real emite, reasigna y cancela expresamente el servicio. Repositorio Flutter usa HTTP/TCP real para aceptar la primera oferta, rechazar la segunda y consultar ruta. Backend/PG reales; correo simulado. Aceptación anterior preservada y nueva oferta sin aceptación heredada. Ensayo automatizado sintético: **no intervención humana nueva ni Android físico**. El último borde de inicio previsto perdido se probó por separado después, sin repetir el caso.

[Retorno compatible](../operacion/L-04-retorno-compatible.md): mismas clases compiladas con nuevas operaciones deshabilitadas; consulta conservada, reoferta409 y rollback exacto del servicio/ofertas. Gate específico corroborado en prueba nativa. No down migration, binario antiguo, despliegue ni atribución del cuerpo no archivado del409.

## Retiro, contadores y conservación

- Cuenta/driver nuevos deshabilitados, período revocado, unidad/grupo deshabilitados, ruta/tipo retirados. Dos ofertas, aceptación, rechazo e historial conservados; cero reservas vivas. Refresh del conductor y tres familias ADMIN de prueba revocados; secreto privado/marcador ausentes y lock libre. **TEST_CLEANUP_RELEASED=true**.
- Ledger **1 SUPER_ADMIN /3 ADMIN /2 CONDUCTOR**; cupos históricos restantes **0/1/18**. Una sola creación nueva expresamente autorizada; no recuperación de cupos. Huella del ledger anterior excluyendo exclusivamente esa cuenta nueva coincide con la previa. Aceptaciones **2** y casos humanos/L-02 intactos.
- Carrera técnica **c78fe5ef-d14b-4f97-bf48-bd01c2c1a79c** conservada cancelada, recursos sin cuenta inactivos, historial/recibo retenidos. Su catálogo retirado no es una configuración operativa del piloto ni un alta acreditada por API.
- Solo `ruta_fija_test`, runtime `rf_test`, migrador explícito `rf_migrator`, V35/Flyway final válidos. [Postflight](../evidencia/L-04/cierre/l04-final-postflight-v35.json), [12 huellas](../evidencia/L-04/cierre/inmutables-preservados.json) y [archivos anteriores](../evidencia/L-04/cierre/archivos-anteriores-preservados.json) correctos. **Final3 se conserva y no se regenera**.

La liberación del caso no habilita limpieza global: los históricos siguen protegidos. No Git/GitHub, SMTP externo, migraciones de desarrollo/piloto, claves definitivas, cuentas reales ni despliegue.

## Requisitos del piloto que siguen pendientes

Aceptación propia de ADMIN legítimo; preparación/inscripción/custodia independiente y ensayo del titular real; información de privacidad/identificación, protección y retiro efectivos; infraestructura/base piloto/HTTPS, copia cifrada y restauración; preparación real de participantes, recursos y títulos. El piloto no queda habilitado por estas pruebas. Se mantiene S/0, sin offline/colas, RENIEC/MTC ni QR; GPS corresponde a L-05 y dispositivo/calibración a L-08.

No quedan criterios obligatorios abiertos del **alcance funcional local de L-04**. El inicio de L-05 requiere su propio **OK VERDE**, aún no concedido para ese lote.
