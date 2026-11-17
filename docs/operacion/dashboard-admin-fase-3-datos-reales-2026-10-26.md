# Dashboard ADMIN: integración con datos reales, fase 3

Fecha: **26/10/2026**, America/Lima. Resultado: **INTEGRACIÓN_DE_DATOS_REALES_VERIFICADA_CON_LÍMITE_DEL_SHELL_DOCUMENTADO**. Autorización: «OK VERDE PASAMOS A LA FASE 3». La revisión visual final del propietario sigue siendo independiente de las pruebas automatizadas.

El Resumen del perfil ADMIN en **http://127.0.0.1:4300/dashboard** utiliza ahora el Dashboard de cinco indicadores, seis paneles y tres donas. La vista consulta la empresa de la sesión a través de las API existentes y conserva las prioridades antes de los indicadores. SUPER_ADMIN conserva su resumen de plataforma.

Este documento actualiza el estado de implementación descrito como pendiente en los antecedentes de [fase 2](dashboard-admin-fase-2-maqueta-2026-10-26.md) y [contrato DA-01](../contratos/DA-01-dashboard-admin-v0.1.0.md). Sus bytes y evidencias históricas se conservan; las fuentes de presentación evolucionan en este corte y se identifican con un manifiesto nuevo.

## Qué se integró

- `AdminDashboardFacade` concentra consultas, cancelación y estados por fuente; `admin-dashboard.adapter.ts` valida respuestas y construye el modelo visual. Los componentes de presentación reciben ese modelo.
- El resumen ADMIN añade `zoneId` y `operationalDate`, derivados de la organización del actor y el reloj del servidor. Los campos y permisos anteriores mantienen su significado. El cliente tolera un servidor anterior: conserva contadores actuales válidos y bloquea las consultas por fecha si falta ese contexto.
- Los KPI usan disponibilidad registrada y servicios canónicos. Las asignaciones futuras y las ofertas en servicio no se confunden con servicios canónicos. Los denominadores incluyen todos los estados recibidos válidos.
- El selector consulta servicios e incidencias del día elegido. Flota, prioridades, conductores, bandeja e indicadores actuales mantienen sus cortes propios. La interfaz muestra el corte del servidor o «Consultado» cuando la fuente carece de él.
- Las fuentes fallan por separado. No se convierten errores en ceros. Un dato válido anterior puede mantenerse identificado como anterior; respuestas inválidas o prohibidas se presentan sin afirmar disponibilidad. Cambiar de usuario, organización o rol cancela solicitudes y vacía datos del Dashboard.
- El esquema se construye únicamente con coordenadas de paradas de una versión registrada. Se interrumpe el trazo cuando faltan coordenadas intermedias. No se inventan posiciones ni se consulta GPS personal.
- Los conductores aparecen por código, con disponibilidad y estado administrativo diferenciados. No se proyectan nombre, documento ni teléfono en este resumen. La bandeja corresponde al ADMIN autenticado, muestra hasta dos avisos y no confirma lecturas.
- La actividad se consulta al expandirla. Los accesos navegan a módulos existentes mediante destinos controlados. Se reutiliza el canal operativo existente y se agrupan sus eventos en 300 ms.

No se editaron el shell, rutas, guardas, autenticación, términos, migraciones ni Android. El único cambio en Java de producción está en el contrato y servicio del resumen ADMIN. La suite segura suma cuatro casos de fecha/zona, servicio canónico y rechazo del rol global.

## Comprobación ejecutada

Los comandos de npm se ejecutaron desde `frontend/`; el resto, desde la raíz. Los registros y resultados están en [evidencia de esta fase](../evidencia/dashboard-admin-fase-3-2026-10-26/validation.json).

| Comando o comprobación | Resultado final |
| --- | --- |
| `npm.cmd run typecheck` | Código 0, aplicación y pruebas |
| `npm.cmd run test:ci` | **49 archivos, 304 pruebas aprobadas**, 0 fallos |
| `npm.cmd run build` | Código 0, 0 advertencias |
| `npm.cmd run build -- --configuration=dashboard-preview` | Código 0, 0 advertencias; revisión sintética separada |
| `./scripts/Test-RutaFijaBackendUnitSafe.ps1 -Execute` | **51 clases, 261 pruebas aprobadas**, 0 fallos/errores; sin base compartida |
| Maven 3.9.15 local: `--offline -f backend/pom.xml -Dmaven.test.skip=true package` | `BUILD SUCCESS`, tras las pruebas seguras; JAR contiene V1..V51 |
| `finalizar_ruta_fija.bat` y `iniciar_ruta_fija.bat` | Parada propia y arranque controlados aprobados; PostgreSQL compartido conservado |
| `scripts/Invoke-RutaFijaNativeRuntime.ps1 -Action Test` | `PASS`, backend y frontend UP en 8080/4300 |
| `GET http://127.0.0.1:8080/actuator/health` | `UP` |
| `GET http://127.0.0.1:4300` | HTTP 200 |
| `scripts/Test-RutaFijaLocalSecretPolicy.ps1 -Action Check -Execute` | Exclusiones y ACL de configuración privada aprobadas |
| Edge real, sesión ADMIN ordinaria | Cinco KPI iguales a su fuente; fecha real America/Lima; consultas diarias HTTP 200; **0 excepciones JavaScript** |

La primera ejecución de la prueba de cancelación de rutas detectó un dato de prueba incompleto; se corrigió el fixture y la suite final aprobó sus 304 casos. El primer empaquetado encontró el JAR abierto por Windows; se repitió después de detener el runtime mediante su lanzador. No se cambiaron reglas ni presupuestos de compilación para superar esas comprobaciones.

La suite del adaptador incorpora **25 casos** de transporte/datos y límites: fallos independientes, respuesta inválida, dato anterior, categorías desconocidas, incompatibilidad temporal, acceso denegado, cancelación al cambiar fecha o sesión, bandeja ajena, campos personales, catálogo vacío, tramos discontinuos y coordenadas cero legítimas. Se conserva además la cobertura del host ADMIN/SUPER_ADMIN y de presentación.

La prueba del navegador utilizó credenciales privadas existentes y el login habitual. El 401 inicial de `/auth/refresh` corresponde al navegador nuevo sin cookie; después el login, las guardas y las fuentes respondieron correctamente. No se omitieron OTP, términos ni permisos. La sesión exitosa de prueba terminó con logout HTTP 204.

## Resultado observado en datos locales

| Indicador | Valor contrastado con API |
| --- | --- |
| Conductores disponibles | **1 de 2 registrados** |
| Vehículos disponibles | **2 de 2 registrados** |
| Asignaciones futuras | **0** |
| Servicios canónicos en curso | **0** |
| Incidencias abiertas | **0** |

Corte de operación: **26/10/2026**, `America/Lima`. No hay servicios programados para esa cohorte: el porcentaje permanece nulo y la interfaz muestra «Sin servicios programados», sin inventar un cumplimiento de 0 % o 100 %. La cohorte de incidencias está vacía.

El catálogo consultado no tiene rutas registradas. El panel lo informa expresamente; no se añadió una ruta de ejemplo a la base. La rama de detalle y geometría tiene cobertura de pruebas con respuestas sintéticas, pero no se atribuye una validación integrada de una ruta real inexistente.

La carga inicial del Dashboard realizó **7 GET propios**, más el ticket operativo normal. El objetivo es 8 cuando hay una ruta seleccionable; aquí no corresponde el GET de detalle. Cambiar al **25/10/2026** generó dos GET y mantuvo iguales los cinco KPI actuales. Expandir actividad generó un GET adicional. No hubo comandos de negocio, emisión de reportes ni confirmación de avisos.

## Integridad y trazabilidad

Antes de editar se respaldaron **847 archivos fuente** y el JAR vigente de **91.385.017 bytes** bajo `backend/.local/accesos/dashboard-admin-fase-3-20261026/`, excluido del repositorio. El manifiesto final identifica los hashes de ambos binarios; el respaldo conserva los bytes anteriores de los componentes de fase 2.

Se obtuvieron huellas por tabla mediante transacciones `READ ONLY` antes y después. Las **105 tablas de `ruta_fija_test` permanecieron idénticas**. En la base nativa se compararon 106 tablas, con **51 migraciones correctas y ninguna fallida**. Se mantuvo la cantidad de aceptaciones. Los únicos cambios de huellas fueron `app_user`, `driver`, `audit_event` y `refresh_token`: sesiones ordinarias de comprobación y el mantenimiento previo de vigencias, observado en eventos `DRIVER_OPERATIONAL_VALIDITY_CHECK`. No se ejecutaron comandos de negocio desde el Dashboard ni SQL de modificación para las pruebas.

El snapshot L-00, los términos publicados y su aprobación conservan sus SHA-256 originales. Las migraciones, el shell y las restantes fuentes de backend conservan sus huellas respecto al respaldo de inicio. El contrato DA-01 MD/JSON conserva su hash de fase 1. Los marcadores de fixtures y de maqueta están ausentes del resultado normal de producción.

No se ejecutaron pruebas históricas que limpian la base compartida. No se cambiaron secretos, se publicaron credenciales ni se crearon commits o publicaciones remotas en esta fase. Las respuestas completas de las API y perfiles de navegador se guardaron exclusivamente en el directorio privado; la evidencia pública contiene resultados y referencias seguras.

## Capturas y límite conocido

- [Dashboard con datos reales, escritorio](../evidencia/dashboard-admin-fase-3-2026-10-26/dashboard-admin-real-1440.png).
- [Ventana de 390 px](../evidencia/dashboard-admin-fase-3-2026-10-26/dashboard-admin-real-390.png).
- [Ventana de 320 px](../evidencia/dashboard-admin-fase-3-2026-10-26/dashboard-admin-real-320.png).
- [Ampliación CSS al 150 %](../evidencia/dashboard-admin-fase-3-2026-10-26/dashboard-admin-real-zoom150.png).
- [Mediciones y solicitudes del navegador](../evidencia/dashboard-admin-fase-3-2026-10-26/browser-validation.json).
- [Manifiesto SHA-256](../evidencia/dashboard-admin-fase-3-2026-10-26/manifest.json).

El Dashboard no desborda horizontalmente en esas cuatro comprobaciones. **A 320 px, la cabecera general del CRM sí desborda aproximadamente 7,73 px por sus acciones de usuario.** Es una fuente previa del shell, comprobada sin cambios de hash; no se modificó por quedar fuera del main ADMIN solicitado. No se declara que toda la página cumpla a 320 px. La comprobación al 150 % corresponde a ampliación CSS, no a un ensayo humano con lector de pantalla.

## Frontera de fase y estado final

**Fase 3 completada en su alcance de integración. Fase 4 pendiente.** El botón de exportación presenta el alcance y conduce al módulo Reportes existente; el flujo completo de emisión, selección de versiones y descargas dentro del Dashboard corresponde a la siguiente fase. También quedan para ella los controles de ruta, el estado explícito de conexión y el sondeo condicionado a visibilidad de pestaña definido en DA-01. No se certifica todavía el cumplimiento global de todas las interacciones de DA-01.

Al cierre, API **8080**, CRM **4300**, puente móvil privado **4311** y maqueta en loopback **4312** continúan activos. El puente conserva su función anterior; esta fase no incluye una nueva prueba física del POCO. Para abrir el resultado, iniciar sesión como ADMIN y entrar en **Resumen**; si la pestaña conservaba el componente anterior, recargarla.
