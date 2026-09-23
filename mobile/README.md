# Ruta Fija Conductor

Cliente Flutter Android del rol `CONDUCTOR`. Su backend es Ruta Fija Spring
Boot; la aplicación nunca se conecta a PostgreSQL ni contiene credenciales.

## Estado de F4.1

M1 está construido: bootstrap, tema, navegación y configuración por entorno.
Los módulos de sesión, disponibilidad, asignaciones, incidencias, comunicados,
ubicación y offline permanecen pendientes y no usan datos simulados.

## Comandos

Desde `mobile/`:

~~~powershell
flutter pub get
flutter analyze
flutter test
~~~

Para Android emulado, el valor de desarrollo es el predeterminado:

~~~powershell
flutter run --dart-define=RF_API_BASE_URL=http://10.0.2.2:8080/api/v1
~~~

Para una URL HTTPS temporal de demostración o una red autorizada, defina la
misma variable con una URL que termine exactamente en `/api/v1`. Nunca incluya
usuario, contraseña, token, consulta URL ni el puerto 5432.

## Requisitos Android pendientes

Flutter está instalado en `D:\dev\flutter` y se añadió al `PATH` del usuario.
Antes de generar un APK o ejecutar Android, el propietario debe instalar el
Android SDK y revisar/aceptar personalmente sus licencias. No se instala un
emulador como requisito del proyecto: un dispositivo físico o un APK debug
serán suficientes para las fases posteriores.

Para un equipo con recursos limitados, use las herramientas oficiales de línea
de comandos Android en vez de instalar o ejecutar un emulador. Revise sus
términos en https://developer.android.com/studio y, cuando el SDK esté
instalado, informe `LISTO ANDROID SDK` para configurar Ruta Fija y volver a
validar `flutter doctor` sin aceptar licencias en su nombre.
