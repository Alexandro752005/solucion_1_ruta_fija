# A-MOB — Android Flutter

## Responsabilidad

Implementar las experiencias Android del rol `CONDUCTOR`: acceso, incorporación, expediente, operación, perfil y ubicación autorizada.

## Reglas obligatorias

- Sin operación offline, cola de acciones ni sincronización diferida.
- No mostrar aceptación, inicio, cierre ni envío exitoso antes de la confirmación del servidor.
- Ante una respuesta perdida, consultar el estado ya registrado antes de intentar una nueva acción.
- Ubicación con consentimiento, solo en primer plano y durante un servicio iniciado; detenerla ante cierre, interrupción, cancelación o retiro de consentimiento.
- No inventar posiciones, actividad de fondo, historial local de ubicación o prueba de recorrido completo por llegar a un terminal.
- Mantener separados cuenta, expediente, revisión, aprobación y vínculo de unidad.

## Entrega

Archivos asignados, estados/error/conectividad accesibles, referencia Stitch, pruebas Flutter y validación en dispositivo disponibles con evidencia. No requerir emulador ni subir datos personales reales.
