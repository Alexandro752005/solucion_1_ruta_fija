# CI-L08-07: candidato local ante pérdida prolongada de fix

Fecha: 2026-10-23 (America/Lima). Superficie: cliente Flutter del rol `CONDUCTOR`.

## Alcance y precondiciones

La prueba usa el controlador de servicio, su temporizador de 30 segundos y la bandera de presentación reales, con reloj virtual de Flutter y adaptadores de dispositivo/servidor sintéticos. El caso mantiene exactamente un servicio iniciado, conductor autorizado en primer plano, consentimiento vigente, permiso de Android concedido y proveedor habilitado. Ninguna prueba de esta carpeta consulta ni modifica PostgreSQL.

Se simula que el proveedor deja de entregar coordenadas: cada solicitud puntual falla con la clasificación `timeout` ya soportada. También se prueba no haber recibido un primer fix. No se sustituyen posiciones por 0,0 ni se inventa un recorrido. Se usa ausencia de actualizaciones por más de 120 segundos; reducir la precisión solicitada no demostraría pérdida satelital y no se cambió `LocationAccuracy.high`.

## Comportamiento comprobado

- El controlador expone `isSignalDegraded` cuando transcurren **más de 120 segundos** desde la última captura confirmada o, si no existe, desde el inicio de la observación del servicio. A los 120 segundos exactos no activa la bandera.
- El aviso visible indica «GPS sin captura reciente: señal degradada» y que el servicio continúa. Permisos, consentimiento, red y segundo plano conservan sus estados distintos. La bandera no afirma una causa física de pérdida de satélites.
- El refresco automático usa el ciclo existente de 30 segundos: el aviso aparece en el siguiente intento después de superar el umbral, con tolerancia máxima de un ciclo. No añade un servicio Android en segundo plano ni una cola.
- El servicio en curso permanece disponible para consulta y cierre seguro. La falta de fix no envía comandos de finalización, ni cambia la asignación por su cuenta.
- Durante la falta de fix no se realiza ningún envío de punto. La recuperación envía solo una lectura nueva en el siguiente intento ordinario; un envío de recuperación fallido se descarta y el próximo intento obtiene otra lectura.
- Salir de primer plano, cerrar sesión o confirmar el cierre del servicio elimina el estado aplicable y detiene las lecturas correspondientes. Un servicio nuevo inicia su propia ventana de observación.

## Reproducción y resultados

Desde `mobile/`:

```powershell
& 'D:\dev\flutter\bin\flutter.bat' test --no-pub --reporter expanded test/features/location/l08_prolonged_no_fix_test.dart
& 'D:\dev\flutter\bin\flutter.bat' test --no-pub --reporter expanded test/features/location
& 'D:\dev\flutter\bin\flutter.bat' analyze --no-pub
```

El resultado formal de cada comando y los SHA-256 de las fuentes se registran en `resultado-mobile.json`. Los archivos `flutter-location-tests.log` y `flutter-analyze.log` contienen la salida capturada de la regresión y del análisis, respectivamente.

La suite nueva contiene seis casos: pérdida posterior a una captura válida y recuperación; ausencia de primer fix; cierre seguro; segundo plano/salida de sesión; descarte de envío fallido; aislamiento del intervalo entre servicios.

## Límites de aceptación

Esto aporta evidencia **VERIFICADO_LOCAL_CANDIDATO_FINAL** para la respuesta del software a una pérdida prolongada simulada de fix. No convierte el ensayo físico previo del POCO en una prueba de pérdida real de señal: aquella situación sigue **NO OBSERVADA FÍSICAMENTE**. GPS desactivado, permiso revocado, reloj virtual y mocks son evidencia distinta. No habilita por sí sola despliegue ni piloto real, no acredita geometría terminal, ni reemplaza la verificación integrada de FIS02 ya congelada.

La implementación corresponde al agente móvil. La revisión independiente corresponde al coordinador y al agente de empaquetado; este documento no firma una aprobación humana.
