# Ruta Fija Conductor

Cliente Flutter Android exclusivo del rol `CONDUCTOR`. Se comunica con Spring
Boot por `/api/v1/mobile`; nunca accede a PostgreSQL, no contiene credenciales
de base de datos y no reutiliza la cookie de sesión del CRM.

## Estado actual: M9 en validación física

M1, M2, M3 y M4 están implementados: base, navegación, sesión JSON de
conductor, refresh rotativo protegido, perfil, disponibilidad y asignaciones
propias idempotentes. F4.4 completa M5 y M6: incidencias propias y comunicados
visibles para su audiencia con lectura explícita. M7 aprobado añade consentimiento y
ubicación manual en primer plano: un único punto voluntario, sin historial ni
seguimiento continuo. M8 aprobada agrega una cola persistente y visible, de
máximo diez eventos FIFO, para disponibilidad, inicio y cierre de servicio
cuando ocurre una falla temporal de red. El avance aprobado es **71/80 puntos**.
M9 aprobó la auditoría automática con 80/80: pruebas críticas, accesibilidad
básica, APK debug firmada y evidencia reproducible. Falta la validación física
del conductor antes de declarar el VERDE formal.

La aplicación no implementa ubicación continua, fotos, FCM, notificaciones
push ni sincronización en segundo plano.

## Seguridad de sesión

- El access token permanece solo en memoria.
- El refresh token se guarda solamente con `flutter_secure_storage` en el
  almacenamiento cifrado de Android y se rota con cada refresh.
- Si dos solicitudes expiran a la vez, el cliente serializa el refresh para no
  reutilizar la familia de tokens del backend.
- No se registran contraseñas, tokens ni cuerpos HTTP. Los errores del backend
  se muestran mediante su código y mensaje uniforme.
- Antes de mutar una asignación, el cliente guarda un único `clientEventId`,
  versión e instante en almacenamiento protegido. Ante un resultado desconocido
  conserva ese evento y pide un reintento explícito; no crea otro comando.
- Una incidencia se atribuye en el servidor al conductor de la sesión. Si la
  red deja su resultado desconocido, la app pide revisar la lista y no crea una
  cola ni reenvío automático que pueda duplicarla.
- Abrir un comunicado no registra lectura. Cuando el servidor exige acuse, el
  conductor lo confirma mediante una acción humana y una ruta idempotente.
- La ubicación se solicita solo desde **Perfil → Gestionar ubicación**, después
   de consentimiento explícito y mientras la aplicación está abierta. No se
   guardan coordenadas en el dispositivo ni se inicia un rastreo continuo.
- M8 solo conserva disponibilidad, inicio y cierre de servicio. Cada evento
  conserva su UUID, fecha y carga exactos; un conflicto o rechazo bloquea la
  secuencia hasta que el conductor la concilie y descarte conscientemente.
- La cola es privada para la combinación usuario/conductor, se limpia al cerrar
  sesión y nunca usa temporizadores, detección de conectividad ni trabajo en
  segundo plano.

## Comandos de validación

Desde `mobile/`:

~~~powershell
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
~~~

Si `flutter` no está en el `PATH` de la terminal, agregue su instalación solo
para la sesión actual. En este equipo está en `D:\dev\flutter`; en otro equipo
reemplace esa ruta por la que corresponda:

~~~powershell
$env:Path = 'D:\dev\flutter\bin;' + $env:Path
flutter --version
~~~

Desde la raíz del repositorio, la auditoría completa de F4.4 es:

~~~powershell
.\scripts\Test-RutaFijaF44MobileIncidentsAnnouncements.ps1
~~~

La salida aprobada empieza con
`F4_4_MOBILE_INCIDENTS_ANNOUNCEMENTS=PASS` y declara `total=58/80`.

Para M7, la auditoría específica es:

~~~powershell
.\scripts\Test-RutaFijaM7ForegroundLocation.ps1
~~~

Su resultado aprobado comienza con `M7_FOREGROUND_LOCATION_AUDIT=PASS` y
declara `total=66/80`.

Para M8, después de que PostgreSQL esté en V12, ejecute:

~~~powershell
.\scripts\Test-RutaFijaM8OfflineMinimum.ps1
~~~

El resultado técnico esperado es
`M8_OFFLINE_MINIMUM_AUDIT=PASS` y declara `total=71/80`. M8 ya fue aprobada
después de su prueba física. Su guía conserva el procedimiento reproducible en
[Ejecución M8](../docs/ejecucion-m8-offline-minimo.md).

Para M9, ejecute desde la raíz:

~~~powershell
.\scripts\Test-RutaFijaM9QualityEvidence.ps1
~~~

El resultado técnico esperado inicia con `M9_QUALITY_EVIDENCE_AUDIT=PASS` y
declara `total=80/80`. Aún requiere la validación física de interfaz indicada
en [Ejecución M9](../docs/ejecucion-m9-calidad-evidencia.md).

## Ejecutar en desarrollo

Para un emulador debug, la URL predeterminada es:

~~~powershell
flutter run --debug --dart-define=RF_API_BASE_URL=http://10.0.2.2:8080/api/v1
~~~

Para un teléfono físico conectado por USB, sin abrir PostgreSQL ni el backend
hacia la LAN, use ADB reverse y el loopback del dispositivo:

~~~powershell
adb reverse tcp:8080 tcp:8080
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

Debe existir una cuenta `CONDUCTOR` activa y vinculada, creada desde el CRM.
No guarde usuario, contraseña, token ni el puerto `5432` en
`RF_API_BASE_URL`. Para release solo se permite una URL HTTPS que termine
exactamente en `/api/v1`.

Para probar M4, el ADMIN crea una asignación `MOBILE_CONFIRMATION` para el
conductor vinculado. La app solo muestra las asignaciones propias y permite
aceptar/rechazar, iniciar o completar cuando el backend autoriza el estado. No
existe un botón CRM que suplante la respuesta del conductor. Para M5, el
conductor reporta una incidencia propia; el backend fija su origen y estado.
Para M6, el ADMIN publica un comunicado dirigido a la audiencia del conductor:
abrirlo no confirma nada y el acuse se realiza solo mediante el botón explícito.
Para M7, el conductor abre **Perfil → Gestionar ubicación** y confirma el
envío de un único punto actual. Puede eliminar el punto vigente o retirar el
consentimiento desde la misma pantalla.
Para M8, al fallar temporalmente una disponibilidad, inicio o cierre, aparece
un contador en el icono de sincronización superior. El conductor abre
**Pendientes de sincronización** y pulsa **Sincronizar pendientes**; ante un
conflicto revisa el estado actual y descarta únicamente la primera acción
bloqueada antes de continuar.

## Android y recursos limitados

Flutter está en `D:\dev\flutter`; Android SDK está en `D:\Android\Sdk` con
API 36, Build-Tools 36.0.0, platform-tools y NDK 28.2.13676358. No se requiere
Android Studio ni emulador.

Gradle está limitado a 1 GB, un worker y sin daemon persistente para convivir
con un equipo de 8 GB de RAM, PostgreSQL local y el CRM. El NDK participa solo
en la compilación de activos nativos de Flutter; no es un servicio residente.

La configuración debug permite HTTP únicamente para ADB/emulador local. El
manifiesto principal deshabilita backup Android y no permite HTTP global; una
compilación release exige HTTPS.
