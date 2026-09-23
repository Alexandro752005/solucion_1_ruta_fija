# Ruta Fija Conductor

Cliente Flutter Android exclusivo del rol `CONDUCTOR`. Se comunica con Spring
Boot por `/api/v1/mobile`; nunca accede a PostgreSQL, no contiene credenciales
de base de datos y no reutiliza la cookie de sesión del CRM.

## Estado actual: F4.2

M1, M2 y M3 están implementados: base, navegación, sesión JSON de conductor,
refresh rotativo protegido, perfil propio y disponibilidad real. El avance
verificable es **28/80 puntos**.

La aplicación no implementa todavía asignaciones, aceptación/rechazo,
incidencias, comunicados, GPS, permisos de ubicación, fotos, FCM ni cola
offline. Las rutas de esos módulos siguen señalando honestamente que están
pendientes.

## Seguridad de sesión

- El access token permanece solo en memoria.
- El refresh token se guarda solamente con `flutter_secure_storage` en el
  almacenamiento cifrado de Android y se rota con cada refresh.
- Si dos solicitudes expiran a la vez, el cliente serializa el refresh para no
  reutilizar la familia de tokens del backend.
- No se registran contraseñas, tokens ni cuerpos HTTP. Los errores del backend
  se muestran mediante su código y mensaje uniforme.

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

Desde la raíz del repositorio, la auditoría completa es:

~~~powershell
.\scripts\Test-RutaFijaF42MobileSessionProfileAvailability.ps1
~~~

La salida aprobada empieza con
`F4_2_MOBILE_SESSION_PROFILE_AVAILABILITY=PASS`.

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
