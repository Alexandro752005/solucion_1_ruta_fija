# Plan de regularización de conductor heredado

Autorización: el propietario solicita construir el flujo técnico pendiente con agentes, conservando la plataforma. Fecha: 23/10/2026. Ámbito: runtime local de Ruta Fija; no habilita despliegue real.

## Decisión y secuencia

1. **Protección previa:** respaldo custom legible y copia de fuentes; huellas de las bases nativa y protegida. Conservar términos, snapshot L-00, ledger, cuentas, contraseñas e historial. No utilizar Git ni limpieza global.
2. **Backend:** ADMIN de la organización abre una regularización para un Driver existente. Exigir identidad previamente revisada, DNI coherente y confirmación expresa. Crear expediente pendiente e identidad preliminar inerte con vínculo explícito; no generar aprobación, verificación de correo, otra cuenta ni otro cupo.
3. **CRM:** acceso desde Conductores; motivo, confirmación y resultado recuperable/idempotente. La revisión documental utiliza las pantallas actuales. Para el legado, presentar la vinculación a la cuenta existente en lugar del alta de otra cuenta.
4. **Provisión:** después de la aprobación personal y el cotejo documental completo, revalidar organización, cuenta, ficha, DNI y grupo; vincular el expediente aprobado conservando identificadores y credenciales. Mantener separada la habilitación operativa.
5. **QA:** pruebas de rol/tenant, estados, documentos, idempotencia, concurrencia, preservación y OTP en base efímera exacta `rf_legacy_qa_20261023`. No ejecutar las suites históricas que limpian `ruta_fija_test`.
6. **Activación técnica local:** con pruebas aprobadas, reconstruir y reiniciar de forma controlada API/CRM/puente. Verificar endpoints y compuertas, dejar instrucciones y evidencia fechada con SHA-256. La revisión, aprobación y recepción del OTP corresponden personalmente a sus titulares.

## Contrato de implementación

- `POST /api/v1/drivers/{driverId}/regularization`: `operationKey`, `reason`, `identityPreservedConfirmed=true`. Respuesta sin PII: `requestId`, `status`, `existingUserId`, `existingDriverId`.
- `POST /api/v1/onboarding/requests/{id}/provision-existing`: decisión versionada e idempotente existente; sólo para un expediente originado expresamente por el flujo de regularización.
- El expediente incorpora `existingAccountRegularization`, `existingUserId`, `existingDriverId`. La apertura queda auditada mediante `LEGACY_REGULARIZATION_OPENED`.
- Se reutilizan las tablas de V50, especialmente `conductor_person_binding`. Los campos de provisión de la solicitud permanecen nulos hasta aprobación y vinculación final, respetando la restricción V21.
- La invitación administrativa de origen se registra de forma expresa, atribuida al ADMIN y consumida internamente; no representa un correo enviado ni consentimiento del conductor.
- Se conserva la autenticación móvil actual y sus límites OTP. No se concede sesión por coincidencia de correo ni se adopta una identidad preliminar preexistente.
- Un DNI revisado que contradiga la ficha no se corrige ni se adopta implícitamente: debe resolverse mediante corrección documental autorizada.

## Responsabilidades

Coordinación principal: respaldo, plan, entorno QA aislado, revisión cruzada, reconstrucción, arranque y evidencia. Agente backend: repositorio, servicios y controladores. Agente CRM: interfaz y pruebas. Agente QA: integración real aislada y pruebas negativas. Las fuentes de cada área se editan por un único responsable.

## Estado inicial comprobado

La base `solucion_ruta_fija_1` contiene la cuenta CONDUCTOR y su Driver activos, organización activa, correo e identidad pendientes, y cero expedientes. ADMIN ya tiene una aceptación de términos. Se comprobará nuevamente el estado al finalizar; el plan no acredita de antemano aprobación personal ni acceso móvil completado.

## Resultado de ejecución

Las seis fases técnicas quedaron completadas. Backend y CRM compilados; 257 pruebas unitarias, 12 integradas de regularización, una prueba con dos carreras concurrentes reales y 42 pruebas del CRM aprobadas. Se añadieron nueve comprobaciones aisladas del cierre del puente después de corregir una carrera de salida natural detectada durante el reinicio.

API, CRM y puente móvil quedaron activos. La comprobación desde el POCO obtuvo salud 200, acceso móvil anónimo 401 y rechazo de ruta administrativa 404. Se conservaron las 105 tablas de la base protegida, los términos, el snapshot base, el ledger y las tres credenciales. Las dos asignaciones antiguas se cancelaron por autorización expresa posterior del propietario, conservando sus filas e historial.

El caso real del conductor continúa pendiente de su revisión y aprobación personal por ADMIN. Eso no se simula para cerrar la implementación. [Guía de uso](regularizacion-conductor-heredado.md), [cierre técnico](cierre-regularizacion-conductor-2026-10-23.md) y [evidencia](../evidencia/regularizacion-conductor-2026-10-23/).
