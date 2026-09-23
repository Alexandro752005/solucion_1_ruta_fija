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

## Preparar Android para APK o dispositivo

Flutter está instalado en `D:\dev\flutter` y las herramientas de línea de
comandos Android están en `D:\Android\Sdk`. Falta instalar los paquetes de
compilación y aceptar las licencias de Android por el titular. Desde la raíz
del repositorio, ejecute personalmente:

~~~powershell
.\scripts\Initialize-RutaFijaAndroidSdkF41.ps1
~~~

El script pide escribir `ACEPTO`, muestra las licencias oficiales para que las
responda directamente y descarga solo `platform-tools`, Android API 36 y
Build-Tools 36.0.0. No instala Android Studio ni un emulador. Al terminar debe
emitir `F4_1_ANDROID_SETUP=PASS`.

Abra una terminal nueva de VS Code y ejecute la auditoría desde la raíz:

~~~powershell
.\scripts\Test-RutaFijaF41FlutterFoundation.ps1
~~~

El resultado debe incluir `android=READY`. Para un equipo con recursos
limitados se probará después con dispositivo físico o APK debug, nunca es
obligatorio instalar o ejecutar un emulador.
