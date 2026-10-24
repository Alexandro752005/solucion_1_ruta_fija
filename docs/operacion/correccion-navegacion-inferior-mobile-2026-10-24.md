# Corrección de navegación inferior en Incidencias y Comunicados

Fecha: 24 de octubre de 2026, America/Lima. Resultado: **corregido, probado e instalado en el POCO**.

## Causa y cambio

Las rutas `/incidents` y `/announcements` devolvían pantallas independientes antes de llegar a `ConductorNavigationShell`. Por eso desaparecían los seis destinos inferiores únicamente al abrir estos módulos.

En `mobile/lib/app/ruta_fija_router.dart`, ambas rutas ahora reutilizan el contenedor de navegación cuando la sesión tiene alcance operativo. La pestaña seleccionada coincide con el módulo abierto y permite pasar a los demás destinos.

Las consultas propias de una cuenta con alcance de incorporación mantienen su acceso limitado anterior. La pérdida de habilitación no oculta las incidencias propias ni los comunicados recibidos; el acceso a los destinos operativos conserva su compuerta de habilitación. No se modificaron permisos del servidor, reglas de negocio ni requisitos de incorporación.

## Verificaciones reproducibles

Ejecutados desde `mobile/`, con Flutter 3.47.5 y Dart 3.13.4:

| Comando | Resultado comprobado |
| --- | --- |
| `D:/dev/flutter/bin/flutter.bat test --no-pub --reporter expanded test/app/conductor_navigation_persistence_test.dart` antes del cambio | 6 fallos y 2 casos aprobados: reproducción de la ausencia de barra |
| El mismo comando después del cambio | 8 de 8 aprobados |
| `D:/dev/flutter/bin/flutter.bat test --no-pub --reporter expanded test/app test/core test/features` | 241 de 241 aprobados |
| `D:/dev/flutter/bin/flutter.bat analyze --no-pub` | Sin incidencias; código de salida 0 |

La regresión cubre navegación por pestañas, entrada directa a las dos rutas, consultas de incorporación y una cuenta suspendida que puede consultar su historial pero no acceder a Disponibilidad. La suite conectada con fixtures de base de datos no se ejecutó en esta corrección.

## APK y comprobación física

Compilación local de depuración para el POCO ARM64, desde `mobile/`:

```powershell
D:/dev/flutter/bin/flutter.bat build apk --debug --no-pub --build-number=5 --target-platform android-arm64 --android-project-arg=rutaFijaL08Isolated=false --dart-define=RF_API_BASE_URL=http://192.168.18.4:4311/api/v1
```

La compilación terminó correctamente en 119,8 s. Conservó una advertencia de herramientas Android sobre versiones de metadatos XML del SDK; no impidió generar ni instalar la APK. No equivale a una nueva firma de entrega release.

| Propiedad | Resultado |
| --- | --- |
| Paquete y launcher resuelto | `pe.rutafija.conductor` / `pe.rutafija.conductor/.MainActivity` |
| Actualización | versionCode 4 → 5; mismo certificado y UID 10286 |
| Archivo | `artifacts/mobile-navigation-2026-10-24/ruta-fija-conductor-debug-v5-navegacion.apk` |
| Tamaño | 96 553 574 bytes |
| SHA-256 APK instalada | `a800ced26ab28413b218b8dffc927580001e51a380aeca2c9fa6175a6ae3031d` |
| Instalación | `adb -s <POCO> install -r <APK verificada>` → `Success` |
| Sesión y datos | Conservados; sin desinstalación, borrado de datos ni nueva introducción de credenciales |

Antes de actualizar, las dos pantallas del POCO reprodujeron la desaparición de los seis botones. Después de actualizar se comprobaron ocho pantallas y transiciones: Inicio, Incidencias, Comunicados, regreso a Inicio, repetición de ambos módulos, Perfil e Inicio final. En todas estuvieron presentes los seis destinos, con una sola pestaña seleccionada y el contenido del módulo correcto. La app quedó abierta en Inicio.

Los volcados XML de interfaz se guardaron en el respaldo privado. La captura de Android se reintentó después de un primer resultado sin raíz durante el arranque; las capturas finales fueron válidas. La advertencia de temas de MIUI también quedó registrada como comportamiento de la herramienta.

## Preservación y evidencia

Se respaldaron antes de instalar la APK anterior y los datos privados de la aplicación en `backend/.local/accesos/mobile-navigation-20261024/`, directorio excluido del control de versiones. No se incluyen esos datos ni los XML con información de cuenta en la evidencia compartible.

La comparación antes/después confirmó las 105 tablas históricas protegidas sin cambios, las tablas de negocio nativas sin cambios y la conservación de credenciales y atributos personales. Solo variaron las tablas de sesión `audit_event` y `refresh_token` por la apertura normal de la aplicación. También se comprobaron el snapshot base y los términos publicados inmutables.

La cuenta quedó activa, con disponibilidad `NO_DISPONIBLE`, sin consentimiento de ubicación, sin servicios ni segmentos abiertos. La prueba no creó incidencias, confirmó lecturas de comunicados ni activó GPS. API y CRM respondieron HTTP 200 al cierre.

Los resultados, registros de pruebas y manifiesto SHA-256 están en `docs/evidencia/mobile-navigation-2026-10-24/`. `final-validation.json` recoge la verificación final del teléfono; `artifacts/mobile-navigation-2026-10-24/manifest.json` identifica la APK instalada. Los registros anteriores al cambio documentan el fallo reproducido, no un fallo pendiente.
