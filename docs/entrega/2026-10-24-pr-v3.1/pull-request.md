## Resumen ejecutivo

Ruta Fija (Solución 1) integra gestión, despacho, telemetría consentida y supervisión del transporte público urbano. Esta entrega consolida el monolito modular y sus clientes CRM/Android, con trazabilidad por organización, incorporación de conductores, asignaciones, incidencias, comunicados, reportes y auditoría.

**Decisión formal: GO PARA CANDIDATO LOCAL DE ENTREGA.** Los lotes L-00 a L-08 están verificados dentro de sus contratos locales. Se mantiene **NO_GO_PILOTO_REAL** hasta cumplir el protocolo de transición; esta entrega no acredita explotación comercial con pasajeros ni cumplimiento documental real del escenario Taxi sintético.

## Arquitectura y stack tecnológico

| Componente | Versión y alcance |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Spring Security/JWT con permisos por rol y organización, JPA/Hibernate, PostgreSQL 16 y Flyway |
| Esquema vigente | **51 migraciones versionadas V1..V51**. El corte histórico V50 de pruebas conserva 50 versionadas más su repetible específica; no se confunden ambos conteos |
| CRM | Angular **22.2.3**, componentes standalone, guardas `/admin-terms`, interceptores, sincronización de sesión entre pestañas y tokens CSS Stitch |
| Android | Flutter **3.47.5** / Dart **3.13.4**, paquete `pe.rutafija.conductor`, APK de depuración instalada versionCode 5 |
| Ubicación | Capturas puntuales consentidas con la app visible, cadencia nominal de 30 s y degradación ante falta de fix; se suspende la captura al pasar a background |

El backend es un monolito modular con dos clientes. La captura con app visible no se presenta como un servicio Android persistente en segundo plano. El ensayo físico FIS02 registró batería 30 % → 28 % durante 959 s en el POCO; describe ese caso y no garantiza una autonomía diaria.

## Resumen de lotes y cambios entregados

- **L-00 a L-08:** contratos, backlog, seguimiento, actas y evidencia SHA-256 del candidato funcional local. El ledger histórico de altas **1/3/8** y sus dos aceptaciones se preservaron en el entorno histórico protegido.
- **Backend/CRM:** regularización de una cuenta CONDUCTOR heredada conservando cuenta, ficha e historial; cotejo y aprobación personal, vinculación explícita y rectificación auditada del DNI ingresado por error mediante V51.
- **Términos:** publicación controlada y aceptación autenticada del ADMIN con documento/version/hash SHA-256. No equivale a una firma criptográfica personal Ed25519. La firma técnica del acta tiene su propia evidencia y alcance.
- **Operación:** migración nativa ensayada y respaldada, compuerta estricta de esquema, puente móvil privado integrado en inicio/cierre, correo OTP y preservación del PostgreSQL previamente compartido.
- **Android:** Incidencias y Comunicados mantienen los seis destinos inferiores; las consultas propias y las restricciones de habilitación conservan sus reglas. Actualización del POCO en sitio conservando certificado, UID, sesión y datos.
- **Continuidad y piloto:** procedimientos de respaldo cifrado AES-256/GCM, restauración y monitoreo; las condiciones independientes de operación real permanecen explícitas en el protocolo PR-01..PR-06.

## Validación y evidencia

Estos resultados son verificaciones fechadas incluidas en el repositorio; el estado de las nuevas ejecuciones de GitHub Actions se consulta en la sección Checks del PR.

El check de backend en CI selecciona la misma allowlist de 50 clases unitarias seguras del runner local y las tres suites de integración dedicadas de regularización/concurrencia/rectificación, sobre bases efímeras separadas. Exige todos sus reportes y cero omisiones. No ejecuta los arneses históricos/manuales restantes ni su limpieza global, que conservan su evidencia y requieren preparación específica; no se afirma que este check reejecute toda la historia de L-00 a L-08. Frontend ejecuta la suite completa y móvil muestra por separado los casos conectados opt-in omitidos por falta de fixture.

| Validación | Resultado registrado |
| --- | --- |
| Backend unitario seguro | **257 pruebas aprobadas**, 50 clases, sin acceder a la base histórica |
| Regularización heredada | **13 integraciones aprobadas**, incluidas concurrencia y preservación de la cuenta, sobre bases aisladas |
| Rectificación de identidad | **10 integraciones aprobadas** sobre base dedicada, sin limpieza del entorno histórico |
| CRM tras la rectificación | Typecheck, **53 pruebas específicas** y build de producción aprobados |
| Móvil completo seguro | **241 pruebas aprobadas** y `flutter analyze` sin incidencias |
| Ubicación | **56 pruebas históricas**, incluidas seis de no-fix simulado; no-fix físico pendiente de campo |
| Navegación Android | **8 regresiones** y **8 pantallas/transiciones físicas** verificadas en el POCO |
| Recuperación | RTO SQL local de **415,026 s**; la recuperación representativa previa de **318,731 s** conserva su evidencia propia |

Referencias: [acta L-08](../../actas/L-08-cierre-funcional-global.md), [regularización](../../operacion/cierre-regularizacion-conductor-2026-10-23.md), [rectificación V51](../../operacion/cierre-rectificacion-dni-conductor-2026-10-23.md), [navegación móvil](../../operacion/correccion-navegacion-inferior-mobile-2026-10-24.md) y [manual vigente](../../Uso%20del%20Sistema.md).

## Resolución de divergencias con main

Se integró `origin/main` mediante `git merge origin/main -X ours`, con revisión antes del commit. Se conservaron las fuentes, el esquema V51 y las dependencias verificadas de v3.1; se incorporaron metadatos de colaboración y mantenimiento. Las propuestas de Maven 3.10.0, JaCoCo 0.8.15 y jsdom 30.1.1 quedan para una evaluación separada. La integración no migra bases locales ni ejecuta el arnés histórico.

## Higiene del repositorio

El escaneo del árbol candidato inicial revisó **4 429 archivos / 4 217 blobs**, contrastó los secretos locales conocidos y buscó patrones de claves privadas/tokens: **sin coincidencias detectadas**. No se versionan credenciales reales, `.env` privados, `.local/`, runtime, dependencias, logs originales, dumps ni APK/JAR. `.env.example` contiene marcadores de sustitución. Se refuerzan exclusiones para temporales, `.rfdb` y carpetas generadas a cualquier profundidad.

No se purgaron evidencias selladas: los informes XML/JSON y las fuentes archivadas explican buena parte del volumen del PR. Los binarios y respaldos locales quedan referenciados por hashes; un clon de fuentes no incorpora esos archivos privados. El escaneo no se presenta como una garantía universal sobre secretos desconocidos en cualquier historial.

## Migración, recuperación y límites

El arranque comprueba el esquema y **no aplica migraciones automáticamente**. Una base existente requiere backup, ensayo aislado, ejecución controlada de Flyway con `rf_migrator` y verificación de privilegios. No se reescriben migraciones aplicadas ni se utiliza limpieza global de la fixture histórica local. La recuperación de datos se ensaya desde backup; no se promete un downgrade SQL automático.

La release candidata firmada y la APK debug v5 del teléfono tienen identidades y destinos de API distintos. Antes del piloto se requieren privacidad efectiva, ADMIN legítimo y aceptación personal, ceremonia USB/Ed25519 del titular, dominio TLS/HTTPS, distribución Android y continuidad independiente, además de participantes y permisos reales. Véase el [protocolo vinculante](../../legal/protocolo-transicion-piloto-real.md).

## Checklist de entrega

- [x] Código candidato probado y evidencias locales incorporadas.
- [x] Divergencia integrada localmente sin rutas en conflicto.
- [x] Versiones y migraciones de v3.1 conservadas.
- [x] Arquitectura, manuales y procedimientos en `docs/`.
- [x] Exclusiones de archivos privados y generados verificadas.
- [x] Alcance local y condiciones pendientes del piloto diferenciados.
- [ ] Confirmar el estado de los Checks de GitHub para el último commit antes de fusionar.
- [ ] Revisión humana independiente, si la política de protección de la rama la exige.
