# L08.4 — Ensayo FIS02 cerrado y una sola app principal

2026-10-23 America/Lima. FIS02 cerrado y retirado selectivamente; una sola app principal v4 instalada y verificada. Batería 30→28% (2 puntos, 959s sin carga), GPS real y 85 puntos conservados; TalkBack humano, escala150%, negativas y contraste comprobados con límites. Ledger1/3/8 y aceptaciones2; TEST_CLEANUP_RELEASED=true; regla temporal, servidor y ADB TCP retirados. L-08 EN_CURSO; decisión NO_GO para aceptación integral/piloto: señal GPS sin fix físico, continuidad independiente, respaldo/RPO, firma de entrega y preparación real siguen pendientes.

## APK seleccionada

[Ruta Fija Conductor v4](../../../../artifacts/L-08-app-unica-local-2026-10-22-v4/Ruta-Fija-Conductor.apk) · `pe.rutafija.conductor` · SHA-256 `3b5533d3bb70fdd7bd043ed9fa62b002fadcc86c4ceee2554a4dc9cc02a6ae3c`. Android confirmó la misma huella instalada y quedó una sola app Ruta Fija. El usuario sustituyó la propuesta de restaurar APF1 por conservar una sola versión actual funcional. [Autorización](autorizacion-app-unica.json), [instalación](principal-instalacion.json), [salida del dispositivo](dispositivo-salida.json), [resolución](incidencia-resolucion.json).

Es una compilación **debug para validación local**, con el certificado ya existente y API privada `http://192.168.18.4:4311/api/v1`. Necesita servidor PC y cuenta elegible; el servidor temporal del caso quedó detenido y su cuenta retirada. No es distribución de producción ni habilitación del piloto. No se generó una clave definitiva. La restitución exacta del binario desconocido anterior no se afirma; se conservaron copias privadas del binario del incidente y sus archivos/preferencias sin desinstalar o limpiar datos de la principal.

## Comprobaciones nuevas

- Contraste 1,571 reproducido con dos fallos; corregido en botones de texto/contorno. [Antes](contraste-red.log), [8 pruebas dirigidas después](contraste-y-regresion.log), [analyze](flutter-analyze.log). Las ocho incluyen las dos nuevas y regresiones existentes; no se suman suites históricas como pruebas nuevas.
- [Reconstrucción bit a bit idéntica](apk-reproduccion.json) con el mismo entorno/caché. [GPS y contraste físicos en APK principal](principal-v4-gps.png); login401 deliberado, otro401 por entrada heredada de automatización y finalmente200 mediante entrada nativa. [Trazas saneadas](principal-login.json).
- Permisos realmente revocados, aviso y recuperación por diálogo normal Android; GPS realmente apagado y restaurado. [Permiso](permiso-denegado.png), [GPSapagado](gps-apagado.png). Sin ADBgrant o sensor ficticio.
- Wi-Fi realmente desactivado: escritura sin confirmación y bloqueada hasta consulta. Al volver cambió la IP del mismo POCO; la guarda rechazó el nuevo peer hasta actualizar exclusivamente la regla/peer y comprobar HTTP200 real. Un primer sondeo vencido queda conservado. [Reconciliación](red-reconciliacion.json), [regla](red-cambio-peer.json), [sondeo válido](network-preflight-peer-actual-2.json).
- Segundo plano real de 95,214 s, servidor activo y conteo41→41; al regresar hubo nuevas capturas. [Prueba](segundo-plano-controlado-resultado.json).
- El uso operativo de TalkBack procede del relato humano anterior; el usuario confirmó lectura y ambos botones a150%, después escala0,81 restaurada. Automatización/árbolUI no sustituyen al lector. [Procedencia](procedencia-confirmaciones-humanas.json).
- Batería 30→28 %, caída de 2 puntos en 959 s sin carga, 32 capturas y separación máxima de 30 s. [Telemetría real](battery-real-telemetry.json), [muestras](battery-real-samples.jsonl), [resultado humano/técnico](bateria-resultado.json). Criterio ≤5 puntos/15 min, limitado a este POCO/sesión con instrumentación; no autonomía diaria.

## Archivo y retiro

[Cierre](cierre-caso.json), [fis02-postflight-f253d9a3-1faa-445b-ae4a-9fc82140f713.json ](fis02-postflight-f253d9a3-1faa-445b-ae4a-9fc82140f713.json), [preservación](preservacion-durante-bateria.json). Cuenta/conductor/unidad/grupo deshabilitados, sesiones/periodos revocados, servicio/tramo cerrados y ubicación vigente retirada; 85 puntos históricos conservados. Ledger1/3/8 y dos aceptaciones intactos. Archivo previo al retiro y hashes de antecedentes reconciliados. Marcador/privado ausentes, lock libre, servidor/regla temporal/ADB TCP cerrados; ninguna limpieza global o caso anterior reabierto.

## Salida integral

[Matriz0.8.0](../matriz-L08.md), [revisión integral](../revision-integral-2026-10-23.md). Faltan señal GPS físicamente no disponible/no-fix, continuidad independiente24/7, respaldo horario externo/RPO y recuperación total representativa, firma de entrega y preparación real. **L-08 EN_CURSO; NO_GO integral/piloto.** No se redujeron criterios ni se emitió acta de aceptación.

[Manifiesto de este archivo cerrado](manifest-caso-cerrado.sha256) y [verificación](verificacion-archivo-final.json). Las muestras originales y el error de agregación corregido están conservados; no se repitió ni reinició la medición.
