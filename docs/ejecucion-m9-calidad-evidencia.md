# M9 - Calidad, accesibilidad básica, APK y evidencia

## Objetivo verificable

Completar los 9 puntos restantes de la matriz móvil mediante pruebas útiles,
una APK debug firmada y un registro reproducible. M9 no crea una nueva función
del producto: endurece la calidad de M1 a M8 y puede elevar el total técnico de
71/80 a **80/80**.

## Alcance de M9

| Bloque | Evidencia exigida |
| --- | --- |
| Regresión de capas reales | Pruebas de controladores, repositorios y widgets; la aplicación no contiene una capa `ViewModel` artificial. |
| Accesibilidad básica | Encabezados, etiquetas de iconos, región viva de error/estado y acceso con escala de texto al 140 %. |
| Android | `flutter analyze`, pruebas Flutter, APK debug y validación v2 con `apksigner`. |
| Trazabilidad | Versión `0.1.0+2`, SHA-256, commit Git de referencia y estado limpio/sucio del árbol. |
| Prueba física | Flujo principal en teléfono Android, sin depender de mocks en la aplicación instalada. |

## Secuencia de construcción

### M9.1 - Cierre de línea base

- Registrar M8 como aprobado por la validación física del conductor.
- Conservar los límites aprobados: los eventos offline siguen siendo visibles,
  FIFO, de máximo diez y solo en primer plano.
- Documentar la decisión en [ADR-009](decisiones/ADR-009-calidad-evidencia-m9.md).

### M9.2 - Pruebas críticas

- Confirmar que el formulario de inicio de sesión mantiene su botón alcanzable
  con escala de texto al 140 %.
- Confirmar que un error de autenticación se anuncia como región viva y expone
  el mensaje seguro del dominio, no el mensaje técnico del servidor.
- Confirmar que el contador de pendientes y la pantalla de cola vacía exponen
  estado semántico comprensible al lector de pantalla.
- Mantener las pruebas existentes de sesión, repositorios, operaciones,
  ubicación y FIFO/idempotencia M8.

### M9.3 - Accesibilidad básica de interfaz

- Marcar **Acceso del conductor** como encabezado.
- Excluir iconos decorativos del lector de pantalla.
- Convertir el icono de pendientes en una acción con etiqueta que incluye el
  contador y el estado cero.
- Anunciar recuperación de sesión, errores de acceso y estado vacío de cola.

Esta cobertura no sustituye una certificación WCAG ni una auditoría completa de
TalkBack. Es el mínimo objetivo, automatizado y comprobable para el MVP.

### M9.4 - Auditoría y artefacto reproducible

Desde la raíz, con PostgreSQL local y los servicios de desarrollo listos:

~~~powershell
.\scripts\Test-RutaFijaM9QualityEvidence.ps1
~~~

El auditor encadena M8, ejecuta `mvn verify` a través de esa línea base,
`flutter analyze`, todas las pruebas Flutter y `flutter build apk --debug`.
Después valida la firma v2, calcula SHA-256 y muestra el commit Git y el estado
del árbol. Su salida aprobada inicia así:

~~~text
M9_QUALITY_EVIDENCE_AUDIT=PASS m9=9/80 total=80/80
~~~

`total=80/80` describe el resultado técnico automático. No cierra la puerta
humana ni autoriza una publicación de Git por sí mismo.

## Validación física M9 en Android

1. Inicie el backend y CRM con el procedimiento normal del proyecto.
2. Conecte el teléfono con depuración USB y confirme que aparece como `device`:

   ~~~powershell
   $env:Path = 'D:\dev\flutter\bin;D:\Android\Sdk\platform-tools;' + $env:Path
   adb devices
   adb reverse tcp:8080 tcp:8080
   Set-Location mobile
   flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
   ~~~

3. Compruebe inicio de sesión con una cuenta `CONDUCTOR` activa, perfil propio,
   asignaciones propias, incidencias, comunicados y ubicación puntual si se
   cuenta con consentimiento.
4. Abra **Pendientes de sincronización** y compruebe que el contador cero y los
   controles tienen nombre comprensible. Si hay un pendiente real, confirme que
   la primera acción indica tipo y estado sin ocultar los botones.
5. En Android aumente temporalmente el tamaño de fuente/pantalla a alrededor de
   140 % y vuelva a abrir inicio de sesión y pendientes. Los controles deben
   poder alcanzarse mediante desplazamiento y no deben producir errores o
   cierres.
6. Si usa TalkBack, active la exploración táctil de forma temporal y confirme
   que se anuncian “Acceso del conductor”, los campos, “Iniciar sesión”, el
   contador de pendientes y los avisos de error. Desactívelo al terminar si no
   es su configuración habitual.
7. Compruebe que la APK instalada corresponde a
   `mobile/build/app/outputs/flutter-apk/app-debug.apk`. Si Android informa un
   conflicto de firma de una APK vieja, no desinstale automáticamente: primero
   confirme que se trata del paquete de pruebas `pe.rutafija.conductor`; al
   desinstalarlo se borrarán sus datos locales.

## Criterios para VERDE - M9

- `M9_QUALITY_EVIDENCE_AUDIT=PASS` con `total=80/80`.
- APK debug no vacía, firma v2 y SHA-256 registrados.
- Prueba física de inicio, flujo principal, texto ampliado y etiquetas básicas
  aprobada por el usuario.
- El estado Git del artefacto queda documentado; si se exige que la APK esté
  asociada a un commit limpio, el propietario autoriza el commit/publicación
  después de revisar los cambios.
- El 20 % diferido permanece explícito y no se presenta como terminado.
