# Evidencia M8 - Offline mínimo, limitado y visible

- Fecha de ejecución: 2026-09-26.
- Estado automático: aprobado.
- Estado de puerta M8: aprobado por confirmación VERDE explícita del usuario
  después de la prueba física.

## Alcance comprobado

M8 retiene exclusivamente tres acciones de conductor: cambio de
disponibilidad, inicio de servicio y cierre de servicio. Cada acción conserva
un UUID, instante y carga inmutables en una cola privada para el
usuario/conductor, FIFO y limitada a diez eventos.

El servidor expone `POST /api/v1/mobile/sync/events`, usa recibos durables y
detiene el lote ante `CONFLICT` o `REJECTED`. La disponibilidad compara
`expectedAvailabilityUpdatedAt`; inicio y cierre conservan el control de
versión existente de la asignación. No se añadieron sincronización en segundo
plano, polling, detección de conectividad, GPS, incidencias, comunicados ni
aceptación/rechazo automático.

## Resultado automático reproducible

Desde la raíz se ejecutó:

~~~powershell
.\scripts\Test-RutaFijaM8OfflineMinimum.ps1
~~~

Salida final observada:

~~~text
M8_OFFLINE_MINIMUM_AUDIT=PASS m8=5/80 total=71/80 fifo=PASS idempotency=PASS foreground_only=PASS
~~~

La auditoría encadenó F3.3, F3.4, F4.4 y M7. Confirmó que el desarrollo está
en Flyway V1-V12 y que las restricciones físicas de V12 permiten
`AVAILABILITY_CHANGE` solo sin asignación y conservan asignación obligatoria
para los comandos de servicio.

El auditor M8 usa `mvn verify`, no solo `mvn test`; por ello se ejecutaron 24
pruebas unitarias y 33 integraciones PostgreSQL nativas sin fallos. Entre ellas,
`MobileOfflineSyncIT` aprobó sus tres casos: aplicación y replay sin duplicar,
conflicto que detiene FIFO y token de disponibilidad obsoleto que no sobrescribe
el estado del servidor.

Flutter aprobó `flutter analyze`, 38 pruebas y la APK debug:

~~~text
mobile/build/app/outputs/flutter-apk/app-debug.apk
~~~

## Observaciones no bloqueantes

Durante el empaquetado Android apareció la advertencia del procesador SDK sobre
XML versión 4 frente a una herramienta que entiende hasta versión 3. La APK se
construyó correctamente y la auditoría aprobó; queda como mantenimiento del SDK
del equipo, no como fallo funcional M8. Las advertencias de Mockito por carga
dinámica de agente se originan en el entorno de pruebas Java y tampoco afectan
el artefacto móvil ni el backend en ejecución.

## Cierre de puerta

La prueba física y la confirmación explícita `VERDE — M8` fueron recibidas del
usuario. M8 queda cerrado con 71/80 puntos. M9 toma esta evidencia como línea
base para calidad, accesibilidad básica, APK debug y trazabilidad; no reabre ni
amplía el alcance offline aprobado.
