# F4.4 — Incidencias y comunicados móviles del conductor

## Objetivo y alcance

F4.4 continúa los días 7–9 de la Etapa 2 después de F4.3. Entrega M5 y M6
contra las rutas móviles reales que F3.3/F3.4 ya dejaron protegidas. No cambia
el contrato de asignaciones, las dependencias Flutter ni los limites de M7/M8.
El ajuste F4.4.1 incorpora una migracion V11, una transicion auditable de
incidencias y sus vistas CRM/movil, sin borrar registros ni cambiar el alcance
de comunicados.

| Módulo | Puntos | Estado después de F4.4 |
| --- | ---: | --- |
| M1. Base, arquitectura y navegación | 8 | Cerrado. |
| M2. Sesión y perfil | 12 | Cerrado. |
| M3. Disponibilidad | 8 | Cerrado. |
| M4. Asignaciones | 18 | Cerrado. |
| M5. Incidencias | 7 | Lista, detalle y reporte propio. |
| M6. Comunicados | 5 | Lista, detalle y acuse explícito. |
| **Total verificable** | **80** | **58/80** |

Quedan fuera: ubicación y consentimiento M7, cola offline M8, GPS en segundo
plano, fotos, cámara, FCM, notificaciones push, mapas, release firmado y
despliegue externo. PostgreSQL continúa privado en el equipo local y Docker no
participa en esta fase.

## M5 — Incidencias propias

Flutter consume exclusivamente estas rutas de la sesión `MOBILE`:

| Pantalla | Ruta | Regla |
| --- | --- | --- |
| Lista | `GET /mobile/incidents` | Solo incidencias del conductor autenticado. |
| Detalle | `GET /mobile/incidents/{id}` | Un identificador ajeno se resuelve como no disponible. |
| Reporte | `POST /mobile/incidents` | El backend fija conductor, tenant, hora, origen `MOBILE_APP` y estado `OPEN`. |
| Cierre | `POST /mobile/incidents/{id}/close` | Solo el mismo conductor que lo reporto desde movil puede cerrarlo con su `version`. |

El formulario solicita categoría y descripción de 1 a 1000 caracteres. El
cliente completa el `occurredAt` técnico que exige el contrato, pero el backend
conserva `reportedAt` con su propio reloj. No acepta `organizationId`, `userId`,
`driverId`, estado, origen ni una identidad elegida por el dispositivo. La
asociación a una asignación es opcional en el contrato, pero F4.4 no inventa un
selector ni acepta UUID manuales: se entrega un reporte general, correctamente
atribuido al conductor autenticado.

Un resultado de red desconocido no genera una cola ni reenvío automático. La
pantalla bloquea el nuevo envío y pide volver a actualizar la lista antes de
registrar otra incidencia; así no promete una idempotencia que esta ruta no
expone todavía.

## M6 — Comunicados y lectura auténtica

| Pantalla | Ruta | Regla |
| --- | --- | --- |
| Lista | `GET /mobile/announcements` | Solo audiencia visible para su organización/grupo. |
| Detalle | `GET /mobile/announcements/{id}` | La entrega se registra por el backend; abrir no confirma lectura. |
| Confirmación | `POST /mobile/announcements/{id}/read` | Solo se habilita cuando `requireReadAck` es verdadero y no existe `readAt`. |

El conductor ve el contenido antes de confirmar. La confirmación humana abre
un diálogo y usa la ruta idempotente del servidor; si la red falla, el usuario
puede reintentar explícitamente esa misma confirmación. Nunca se marca como
leído solo por abrir la pantalla, y no se simula una notificación push.

## Validación de cierre técnico

Desde la raíz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaF44MobileIncidentsAnnouncements.ps1
~~~

La auditoría encadena F4.3 y F3.4 en modo de lectura para preservar las
garantías nativas de PostgreSQL, revisa las fronteras de M5/M6, ejecuta
`flutter analyze`, `flutter test`, compila la APK debug y verifica su firma
v2. La salida aprobada comienza con:

~~~text
F4_4_MOBILE_INCIDENTS_ANNOUNCEMENTS=PASS ... m5=7/80 m6=5/80 total=58/80 ... docker=0
~~~

## Prueba manual en Android físico

Con Spring Boot en `127.0.0.1:8080`, un teléfono USB autorizado y sin publicar
PostgreSQL:

~~~powershell
Set-Location 'D:\UTP\CICLO X UTP\INTEGRADOR I\APF1\solucion_1_ruta_fija\mobile'
$env:Path = 'D:\dev\flutter\bin;D:\Android\Sdk\platform-tools;' + $env:Path
adb reverse tcp:8080 tcp:8080
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

1. Inicie sesión con una cuenta `CONDUCTOR` activa y vinculada.
2. Abra **Incidencias**, reporte una categoría y descripción, y confirme desde
   el CRM que el registro aparece como `MOBILE_APP`, `OPEN` y en el tenant
   correcto.
3. Desde el CRM publique un comunicado visible para ese conductor, con
   `requireReadAck` activo.
4. En **Comunicados**, abra el detalle: antes del botón no debe existir acuse
   de lectura. Confirme y compruebe que el servidor registra `readAt`.

La validación manual debe cerrarse explícitamente con `VERDE — F4.4`; hasta
entonces no comienza M7.

## Mejora diferida solicitada para Etapa 3, Día 18

La revisión amplia de validaciones visuales, exceso de comentarios,
redundancias y ajustes funcionales se mantiene fuera de F4.4 para no alterar
el cronograma ni mezclar una refactorización transversal con estas funciones.
Al abrir el Día 18 se solicitará al titular una lista priorizada de cambios y
se evaluará cada uno con regresión CRM–móvil antes de incorporarlo.
