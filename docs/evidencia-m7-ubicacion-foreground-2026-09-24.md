# Evidencia M7 — Ubicación manual en primer plano

## Estado de cierre

M7 queda cerrado con la aceptación formal `VERDE — M7` comunicada por el
titular. La aplicación móvil alcanza **66/80 puntos verificables** con M1–M7.

## Alcance aprobado

- Consentimiento explícito antes de solicitar el permiso Android.
- Captura voluntaria de un único punto vigente mientras la aplicación está
  abierta.
- Límite de captura de 15 segundos y validación de disponibilidad en cliente y
  servidor.
- Eliminación del punto vigente o revocación del consentimiento desde el perfil.
- Sin historial, seguimiento continuo, trabajo en segundo plano ni coordenadas
  expuestas en la interfaz.

## Evidencia automática

La ejecución de `scripts/Test-RutaFijaM7ForegroundLocation.ps1` concluyó con:

~~~text
M7_FOREGROUND_LOCATION_AUDIT=PASS m7=8/80 total=66/80 permission=foreground_only current_point=single privacy=no_history regression=M1-M6
~~~

La auditoría verificó la regresión M1–M6, análisis estático de Flutter, 30
pruebas, generación de APK, firma de depuración y el manifiesto Android
resultante. También se comprobó que no contiene permisos de ubicación en segundo
plano ni servicios de primer plano para ubicación.

## Condición de continuidad

M8 queda como la siguiente fase: offline mínimo y explícito, con 5 puntos de la
matriz. M9 conserva la calidad, APK y evidencia final con 9 puntos. Ninguna de
ambas ha sido iniciada por este cierre.

Referencia de ejecución: [M7 — Ubicación manual en primer plano](ejecucion-m7-ubicacion-foreground.md).
