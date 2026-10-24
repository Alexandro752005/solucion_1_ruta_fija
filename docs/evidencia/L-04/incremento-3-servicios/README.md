# L04.3–5 — Servicio, ofertas e integración

**PASS local**, 2026-10-19. [Resultado](resultado.json), [casos](casos.json), [API0.3.0](../../../contratos/L-04-servicios-API-v0.3.0.md), [cierre integral](../cierre/resultado.json). El cierre del lote consta en el acta, no se infiere del total de pruebas.

## Comportamiento acreditado

- Servicio persistente separado de cada oferta. Expiración/rechazo/cancelación retiran su reserva, conservan programación, aceptación anterior y vínculo. No reoferta automática.
- Reasignación expresa/versionada/idempotente con nuevo conductor/unidad/ruta/horario y nueva aceptación. El conductor anterior no puede contestar la oferta nueva. Índice y lock mantienen una sola oferta viva por servicio.
- Cancelación motivada del servicio como acto separado, historial inmutable y consultas por operación. Ocupación física no se libera al cancelar un compromiso futuro. No se sustituye una prestación iniciada; un inicio previsto perdido admite reprogramación expresa antes del inicio real.
- CRM integrado con candidatos por servicio e historia. Emisión/reoferta/cancelaciones no se repiten automáticamente ante incertidumbre; se consulta el estado/recibo y se conservan formulario y clave. Gates reales de términos/identidad, rol y organización.
- Flutter identifica servicio y oferta por separado; no habilita inicio sin aceptación móvil auténtica. Sin nueva cola offline. Modelos/repositorio/widgets comprobados y dos etapas conectadas con el repositorio de producción.

## Ejecuciones y límites

**121 casos distintos** en este corte:116 Spring/HTTP/PostgreSQL con fixtures rollback,4 de política temporal y1 carrera PG con conexiones independientes. [XML y lista](casos.json). Se reutilizan99 casos de la regresión pertinente y se actualizan las ejecuciones de servicio/rutas; no se suman reejecuciones. Las dos carreras de márgenes V34 del incremento2 se conservan y contrastan, sin reejecutarlas ni contarlas otra vez en121.

Principales comandos desde `backend`:

```powershell
mvn.cmd --offline -q '-Dtest=ScheduledServiceIntegrationTest#service*,ServiceMarginIntegrationTest#margin*,OfferAcceptanceIntegrationTest#offer*,RouteAssignmentIntegrationTest#route*,MaintenanceIntegrationTest,OfferResponseWindowTest' test
mvn.cmd --offline -q '-Dtest=ScheduledServiceIntegrationTest#service*,RouteAssignmentIntegrationTest#route*' test
mvn.cmd --offline -q '-Dtest=L04ServiceOfferConcurrencyTest' test
mvn.cmd --offline -q '-Dtest=QualificationReturnGateIntegrationTest#return*' test
mvn.cmd --offline -q -DskipTests package
```

La segunda selección se ejecutó después del último ajuste de inicio previsto perdido:20 casos de servicio y9 de rutas. La prueba de retorno confirma `OPERATIONAL_FEATURE_DISABLED` como único blocker pendiente para habilitación tras revisiones/período, conserva consulta y rechaza activación. El recorrido conectado acredita además el rollback completo de una reoferta HTTP con esa configuración.

CRM: typecheck,28 pruebas de cinco specs de asignaciones/tipos/panel de servicio/panel de ruta/API y build de desarrollo. Flutter:15 pruebas de cuatro archivos de asignaciones, analyze sin incidencias y APK debug. Son dobles/componentes salvo las etapas del caso conectado; no dispositivo físico, seguimiento GPS ni ensayo humano nuevo.

## Caso conectado expresamente autorizado

RUN **bbd70822-8f52-45a6-b617-9b896ff90a2c**. [Resultado original](../conectado-bbd70822-8f52-45a6-b617-9b896ff90a2c/resultado.json), [postflight independiente](../conectado-bbd70822-8f52-45a6-b617-9b896ff90a2c/postflight.json), [autorización](../autorizacion-conectado.json).

Angular/Edge real: emisión, reoferta y cancelación expresa del servicio. Flutter por TCP real: aceptación propia de primera oferta, rechazo de la segunda y consulta de ruta capturada; ninguna aceptación implícita. Backend y PG reales con correo simulado. La configuración de márgenes0/0 es explícita y sintética, no un default del piloto. Las revisiones documentales son una fixture sintética, no títulos reales.

Un único CONDUCTOR nuevo autorizado: cuenta `0794eb2a-bc9a-4334-b617-427bd7f28ef1`, driver `c4567be0-3d33-4674-8762-5429f68d1c46`, servicio `7fd33a9e-8e13-49a9-86f6-0dd707e08264`. Dos ofertas conservadas, primera aceptación intacta, segunda rechazada. Cuenta/driver/unidad/grupo deshabilitados, período revocado, ruta/tipo retirados, refresh del conductor y tres familias ADMIN de prueba revocados. Se retiró el archivo privado y marcador; lock libre y **TEST_CLEANUP_RELEASED=true**. Ledger **1/3/2**, cupos0/1/18, aceptaciones2. La huella del ledger previo excluyendo exclusivamente la cuenta nueva coincide con el original. No se reabrió L-02.

El recorrido conectado ocurrió antes del último ajuste que permite reprogramar un inicio previsto perdido; esa variante se verificó después mediante HTTP/PG con rollback, sin repetir la cuenta/caso conectado. [Cortes de evidencia y fuentes actuales](../cierre/cortes-de-evidencia.json).

## Migración, carrera y retiro técnico

V35 aplicada solo a `ruta_fija_test` por `rf_migrator`; runtime `rf_test`, Flyway final válido. No backfill de confirmaciones, servicios ni márgenes desconocidos. [Migración](l04-service-migrate-v35.json), [postflight final](l04-final-postflight-v35.json).

Carrera PG RUN **c78fe5ef-d14b-4f97-bf48-bd01c2c1a79c**: dos recursos distintos intentan ofertar el mismo servicio; el segundo espera el lock y se rechaza23514 tras commit del primero. [Caso](concurrencia/c78fe5ef-d14b-4f97-bf48-bd01c2c1a79c.json), [retiro exacto](l04-final-technical-retirement.json). Recursos sin cuenta/inactivos, sin aprobación personal ni emisión HTTP: solo acredita la protección de base. Servicio/oferta cancelados e historial/recibo/recursos inactivos retenidos; cero reservas vivas. No se borran revisiones ni se desactivan triggers.

Esta fixture reutiliza una revisión técnica de margen ya retirada de incremento2. Su referencia nueva cancelada queda identificada exactamente; el postflight actual distingue ese vínculo de una reserva viva y mantiene catálogo/versiones/recibos originales intactos. No se reescribió el postflight histórico que en su momento registró cero referencias.

## Historial de fallos y conservación

[Histórico inicial](historico-inicial/): una fixture intentó cambiar de empresa una cuenta y la protección la rechazó; se corrigió usando una cabecera técnica extranjera, sin cambiar la cuenta. La primera preparación de carrera usó grupo inactivo y fue rechazada antes de commit; se preparó como grupo vigente y se retiró al final. El primer postflight antiguo asumía cero referencias de catálogo y rechazó la referencia técnica posterior: se acotó a los IDs exactos del caso, conservando el fallo. Ninguno se contabiliza como PASS final.

[Saneamiento](saneamiento-registros.json) de logs/XML nuevos; no se alteran evidencias selladas anteriores. [12 huellas](../cierre/inmutables-preservados.json) correctas: final3, términos, CLI, actas y evidencias L-01/L-02 más cuatro sellos L-03. Se verificaron29 archivos históricos de evidencia de incremento1 y35 de incremento2; sus manifiestos de fuentes antiguas no se reinterpretan como fuentes actuales.

La liberación de protección corresponde a estos casos; **no autoriza limpieza global ni NativePostgresIntegrationTest**. Usar rollback/retiro selectivo. Sin SMTP externo, Git/GitHub, desarrollo/piloto, despliegue o nuevo final3. Artefactos locales en [manifest](../../../../artifacts/L-04-2026-10-19/manifest.json).
