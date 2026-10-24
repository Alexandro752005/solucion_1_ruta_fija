# Incremento3 — Correcciones temporales y concurrencia de autorización

2026-10-20 America/Lima. **PASS local del incremento; L-05 EN_CURSO.** [Resultado](resultado.json), [casos/XML saneados](casos.json), [preservación](preservacion.json), [API](../../../contratos/L-05-tiempos-API-v0.1.0.md), [matriz](../matriz-L05.md).

## Acreditado

- ADMIN propio corrige únicamente servicios completados con todos los tramos originales cerrados. Registra revisiones inmutables con motivo, autor, hora servidor, versión y antes/después. El valor original de ejecución/declaración y los puntos GPS permanecen iguales; un segundo ajuste parte del efectivo anterior.
- Se rechazan futuro, inversión, ausencia/repetición/nulidad de tramos, versiones obsoletas, solicitud ajena y solapamiento con la participación de conductor/unidad en otro servicio. Misma key/payload no duplica; cambiar contenido entra en conflicto. No se recuperan cupos ni se inventan registros físicos previos del servidor.
- CONDUCTOR solo solicita revisión y consulta sus tiempos/historial. CRM requiere motivo/confirmación y conserva la precisión original de una fila no editada. Ambos clientes consultan por key ante respuesta incierta, sin cola ni POST automático mediante refresh. La aceptación de términos no se modifica ni se repite.
- El efecto temporal sobre la corroboración queda separado del original. Si el nuevo cierre antecede al punto, no se considera corroborado. Si el punto ya venció o no existe, se informa que no puede volver a contrastarse; no se recrea ni se extiende su retención.
- Dos transacciones PostgreSQL reprodujeron `40P01`: organización SHARE → actor UPDATE → programación NO KEY UPDATE se interbloqueaba con otro escritor. Se adquiere ahora la modalidad final de organización antes del actor; la regresión de ambas transacciones pasa. Solo lectura/locks sobre el revisor sintético existente, con rollback: no abre un caso humano ni emite una credencial utilizable.
- Se comprobó sesión revocada y organización inactiva pese a entidades en caché. El gate de términos traduce la sesión inválida al envelope HTTP401 de Spring Security, en lugar de propagar una excepción de dominio fuera de MVC. Identidad/términos pendientes siguen denegando acceso. Ningún control se deshabilitó para hacer pasar la prueba.

## Comprobaciones

**46 backend distintos:**15 tiempos,20 GPS,10 ejecución y1 carrera de autorización. Los casos de comandos pasan filtros/servicios Spring y PostgreSQL nativo con rollback; la carrera invoca el proveedor real con contexto de autenticación de prueba, no un navegador ni un login real. **20 CRM** (4 tiempos,11 asignaciones,5 ejecución/GPS) y **24 Flutter** de asignaciones (6 nuevos de repositorio/panel). Reejecuciones del panel después de ajustes de presentación no se suman.

Analyze sin incidencias, typecheck/build de desarrollo, JAR y APK debug correctos. [Artefactos](../../../../artifacts/L-05-incremento-3-2026-10-20/manifest.json). Sin instalación ni despliegue.

```powershell
# backend
mvn.cmd --offline -q '-Dtest=L05AuthorizationLockIntegrationTest,ServiceTimeIntegrationTest#times*,ServiceExecutionIntegrationTest#execution*,ServiceGpsIntegrationTest#gps*' test
mvn.cmd --offline -q -DskipTests package
# L05DatabasePreflight --validate-test, configuración privada y classpath locales
# Flutter
flutter test --no-pub test/features/assignments --reporter expanded
flutter analyze --no-pub
flutter build apk --debug --no-pub
```

CRM: typecheck, tres specs indicados en logs y pase final de los cuatro casos del panel, build:development. V39 aplicada explícitamente solo a test después del preflight; sin clean/repair/override.

## Historial, preservación y pendientes

Se conservan fallos anteriores: import ambiguo `Time`; fixture sin terminal configurado y código genérico de gate; audit JPA no flush antes de consulta JDBC de fixture; contexto JWT de prueba sin autenticación; interbloqueo real40P01; sesión inválida escapaba del gate antes de alcanzar MVC; sintaxis/estilo de fixtures Flutter. Los casos se corrigieron manteniendo assertions de negocio. El primer log de arranque verboso de la carrera no se publica: se conserva su hash y un resumen saneado; XML sin properties/streams.

Postflight V39 válido: ledger **1/3/2**, aceptaciones2 y hashes de casos/historia preservados. Cero cuentas persistentes creadas, correcciones/solicitudes/tramos/puntos de fixtures revertidos. Inmutables12, final3, L-04 y los dos incrementos anteriores contrastados sin regeneración. No SMTP, pruebas humanas, cuentas reales, limpiezas globales ni acciones Git/GitHub.

**CI05 sigue PARCIAL:** falta contrastar tiempos de varios tramos y terminales de interrupción al completar el relevo. SRV-INT-L05-05 está aprobado, registrado y aún pendiente de implementación en este corte. Restan interrupción segura, relevo completo, carreras de negocio, recorrido conectado, retorno y cierre. No se habilitan piloto ni L-06; dispositivo/calibración/batería siguen L-08.
