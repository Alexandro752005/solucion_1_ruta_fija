# L05.4 — GPS, acceso propio y retención

**Incremento verificado localmente; L-05 EN_CURSO.** 2026-10-19 America/Lima. [Resultado](resultado.json), [casos backend/XML saneados](casos.json), [preservación](preservacion.json), [API0.2.0](../../../contratos/L-05-GPS-API-v0.2.0.md), [matriz](../matriz-L05.md). No constituye cierre del lote.

## Alcance comprobado

- Puntos ligados al tramo, captura original y vencimiento fijo168h, incluso referencias incidentales; no hay duplicado permanente de coordenadas en auditoría. Repetición del timestamp conserva el original o rechaza valores distintos. Cierre retira snapshot, no inventa una trayectoria.
- Solicitud propia idempotente; revisión por ADMIN de la misma organización con identidad expresamente comprobada y reautenticación; plazo operativo48h sin decisión automática. Entrega solo al solicitante reautenticado, autorización≤15min y≤vencimiento original. Permisos, aislamiento, SUPER_ADMIN sin acceso GPS, gates reales de términos/identidad y contraseña incorrecta comprobados.
- Purga a los siete días de puntos ordinarios e incidentales, referencia eliminada sin alterar declaración/resultados. Pruebas negativas de reescritura, eliminación y desvinculación prematura. Barrido acotado a tenant; tests con reloj adelantado no ejecutan mantenimiento concurrente sobre datos ajenos.
- Ruta finalizada con corroboración terminal y discrepancia; observación persistida y aviso sin coordenadas. La pérdida de habilitación conserva consulta propia y cierre seguro, sin permitir un inicio nuevo.
- Flutter intenta cada30s en primer plano, no en segundo plano ni sin consentimiento/permiso. Descarta fallos, cambia de sesión sin conservar el tramo y obtiene lectura puntual antes de completar. Timestamp del sensor preservado; precisión no medida no equivale a cero metros. No cola, catch-up ni reintento automático de puntos.
- CRM integra tramos y revisión restringida; Flutter integra solicitud/estado y entrega restringida que se limpia en background y al vencer. Errores de reautenticación no reenvían el POST mediante refresh. El resumen ordinario no muestra coordenadas.

## Pruebas y artefactos

**77 backend distintos:**20 GPS,10 ejecución,20 servicios,14 ofertas,9 rutas y4 política geométrica. Spring/filtros/servicios con PostgreSQL nativo rollback; cuatro casos de política pura. No pruebas con H2 ni respuestas backend fingidas. Los20 GPS incluyen el borrado acotado por organización.

**38 Flutter distintos:**37 del pase conjunto más un negativo nuevo de precisión ausente. El archivo de controlador/sensor se reejecutó tras esa guarda; las repeticiones no se suman. **20 CRM** (asignaciones, tramos/revisión y ruta). Analyze sin incidencias, typecheck y build de desarrollo, backend package y APK debug comprobados. [Artefactos](../../../../artifacts/L-05-incremento-2-2026-10-19/manifest.json). No instalación, navegador/TCP conectado nuevo ni dispositivo físico.

Comandos principales (logs conservados):

```powershell
# backend
mvn.cmd --offline -q '-Dtest=ServiceGpsIntegrationTest#gps*,ServiceExecutionIntegrationTest#execution*,ScheduledServiceIntegrationTest#service*,RouteAssignmentIntegrationTest#route*,OfferAcceptanceIntegrationTest#offer*,TerminalCorroborationPolicyTest' test
mvn.cmd --offline -q -DskipTests package
# L05DatabasePreflight --validate-test, classpath y configuración local existentes
# mobile
flutter test --no-pub test/features/location test/features/assignments/mobile_assignment_repository_test.dart test/core/network/mobile_api_client_test.dart --reporter expanded
flutter test --no-pub test/features/location/mobile_service_location_controller_test.dart --reporter expanded
flutter analyze --no-pub
flutter build apk --debug --no-pub
```

CRM: `npm.cmd run test:ci` filtrado a los tres specs de este incremento, typecheck y `build:development`. No repetir suites completas por rutina.

## Historial y límites

Primeros fallos conservados: FK de purga chocaba con la inmutabilidad de declaración (solución aditiva V38, sin cambiar V32/V37); timers de fixtures requerían dispose desde el widget propietario; diálogo de contraseña requería mantener su controlador hasta desmontar la ruta; prueba de background requería la secuencia legal de Android. No se eliminaron assertions ni se presentaron esos primeros resultados como PASS.

Un formateo Dart amplio produjo cambios exclusivamente de formato en cuatro archivos ajenos al incremento: auth repository, incident models y qualification model/page. Se conserva el log de formato; no se infiere restauración de bytes anteriores sin copia exacta ni se atribuyen esos cambios a nueva funcionalidad.

Solo `ruta_fija_test`, rf_test/rf_migrator. Flyway V36→V37→V38 sin clean/repair/override. Postflight y hashes preservan ledger **1/3/2**, aceptaciones2, ensayos cerrados y servicios/ofertas existentes; cero cuentas persistentes nuevas. Doce inmutables y161 archivos sellados L-04 contrastados; primer incremento y final3 conservados. Correo simulado, sin envíos externos, offline, despliegue ni Git/GitHub.

**Pendiente L-05:** interrupción segura, correcciones de tiempos, relevo completo, carreras, recorrido clientes conectado, retorno y revisión final de matriz. Las comprobaciones actuales de captura son sintéticas; dispositivo, batería y calibración reales siguen L-08. Privacidad, infraestructura, participantes/custodia y documentación operativa siguen bloqueando el piloto. **L-06 no iniciada.**
