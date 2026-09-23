# F4.1 — Base Flutter Android y matriz objetiva

## Alcance autorizado

F4.1 abre los días 7–9 de la Etapa 2 y entrega solamente M1 de la matriz móvil: base Flutter, arquitectura, navegación, tema, configuración por entorno y pruebas de arranque. Su valor verificable es **8 de 80 puntos**; no se declararán como construidos los módulos de sesión, disponibilidad, asignaciones, incidencias, comunicados, ubicación u offline antes de sus puertas correspondientes.

## Decisiones de construcción

- Cliente: `mobile/`, proyecto Android `pe.rutafija.conductor`, dirigido exclusivamente al rol `CONDUCTOR`.
- Arquitectura: `app/` para bootstrap, rutas, tema y entorno; `core/` para componentes transversales; `features/` para módulos separados. Las futuras reglas de negocio seguirán viviendo en Spring Boot; Flutter no accede a PostgreSQL.
- Navegación y tema: Material 3, español, contraste alto y la identidad vial blanco, negro y amarillo de Ruta Fija. Las pantallas no implementadas se identificarán como tales; no se usarán datos simulados para aparentar avance.
- Configuración: la URL se recibirá mediante `--dart-define=RF_API_BASE_URL=...`. El valor de desarrollo para emulador será `http://10.0.2.2:8080/api/v1`; para un teléfono se suministra la IP LAN del equipo, nunca una credencial ni el puerto de PostgreSQL.
- Dependencias: M1 usa el SDK Flutter y sus paquetes base. Las dependencias de red, almacenamiento seguro, permisos, ubicación y cola offline se escogerán con versión, licencia y justificación en las fases que las necesiten.
- Equipo limitado: no se instala ni ejecuta emulador. La posterior validación Android podrá hacerse con APK debug y dispositivo físico; PostgreSQL continúa en loopback y Docker no interviene.

## Matriz de progreso

| Módulo | Puntos | Estado después de F4.1 |
| --- | ---: | --- |
| M1. Base, arquitectura y navegación | 8 | Objetivo de esta fase. |
| M2. Sesión y perfil | 12 | Pendiente de F4.2. |
| M3. Disponibilidad | 8 | Pendiente de F4.2. |
| M4. Asignaciones | 18 | Pendiente de F4.3. |
| M5. Incidencias | 7 | Pendiente. |
| M6. Comunicados | 5 | Pendiente. |
| M7. Ubicación foreground | 8 | Pendiente. |
| M8. Offline mínimo | 5 | Pendiente. |
| M9. Calidad, APK y evidencia | 9 | Pendiente; se inicia con análisis y pruebas base. |
| **Total verificable** | **80** | **8/80 al cerrar F4.1** |

## Criterios de salida F4.1

1. Flutter estable se instala fuera del repositorio y se comprueba su integridad sin versionar SDK, archivos temporales ni secretos.
2. `mobile/` se genera con Android como única plataforma móvil objetivo.
3. Existe una estructura de capas mínima, rutas y tema sin presentar funcionalidades futuras como terminadas.
4. La URL API se configura desde compilación/ejecución y no contiene base de datos, tokens ni contraseñas.
5. `flutter analyze` y `flutter test` aprueban.
6. Se registra el resultado de `flutter doctor`; las licencias Android se aceptan solo por el propietario tras leerlas.

## Fuera de F4.1

No se implementan login real, refresh, secure storage, llamadas HTTP funcionales, GPS, permisos, eventos offline, Firebase, fotos, mapas, historial de ubicación, emulador ni APK. La API de F3.4 permanece sin modificaciones.

## Resultado local del 2026-09-23

- Flutter 3.47.5 estable quedó instalado fuera del repositorio en `D:\dev\flutter`; su archivo oficial se verificó con SHA-256 antes de extraerse y su carpeta `bin` se añadió al `PATH` del usuario.
- Se creó `mobile/` como proyecto Android `pe.rutafija.conductor`, sin SDK, secreto ni archivo temporal de Flutter versionado fuera de lo que el proyecto requiere.
- La auditoría `Test-RutaFijaF41FlutterFoundation.ps1` ejecutó `flutter analyze` sin incidencias y `flutter test` con 4 pruebas aprobadas.
- `flutter doctor` confirma Flutter, Windows, Chrome, Edge y red; Android SDK sigue pendiente. Por licencias, el propietario debe revisar y aceptar personalmente la instalación Android antes de generar APK o ejecutar en dispositivo.

La base M1 queda técnicamente aprobada con 8/80 puntos. La habilitación Android no se declara aprobada ni se sustituye por un emulador: queda como requisito explícito antes de la evidencia APK de M9.
