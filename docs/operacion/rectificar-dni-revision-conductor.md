# Rectificar un DNI digitado por error en la revisión del conductor

Fecha local: 23/10/2026. Complementa el [procedimiento de regularización](regularizacion-conductor-heredado.md). Aplica únicamente antes de vincular un expediente a la cuenta y cuando el DNI correcto coincide con la ficha de conductor conservada.

## Causa y alcance

La revisión de identidad registra una huella protegida del documento comprobado. Si ADMIN digita otro DNI, esa huella no coincide con el documento de la ficha heredada y el servidor rechaza la apertura de regularización. Repetir la revisión inicial no reemplaza una identidad registrada.

El propietario confirmó que había digitado mal el DNI en la revisión. Esto no autoriza a inferir el DNI correcto, sustituir al titular o modificar la ficha heredada. La rectificación debe realizarla ADMIN tras comprobar otra vez el documento original. Si el original tampoco coincide con la ficha conservada, detener este circuito y tramitar una corrección documental autorizada por separado.

## Pasos en el CRM

1. Actualizar el CRM en `http://127.0.0.1:4300` e ingresar como ADMIN de la organización, con correo e identidad verificados y términos vigentes aceptados.
2. Abrir **Conductores** y pulsar **Consultar conductores**. Localizar la misma ficha y cuenta que se intentaron regularizar.
3. Pulsar **Rectificar DNI ingresado por error**. No utilizar otra revisión inicial para intentar reemplazar el registro anterior.
4. Comprobar personalmente el documento original del mismo titular e introducir su **DNI correcto de ocho dígitos**. El sistema no muestra ni rellena el documento anterior.
5. Elegir el método realmente utilizado y escribir una referencia sin datos personales; por ejemplo, `Cotejo del documento original para rectificacion`.
6. Escribir un motivo sin datos personales; por ejemplo, `Rectificacion de error de digitacion en revision previa; se conserva el mismo titular`.
7. Marcar las cuatro confirmaciones únicamente si son ciertas: revisión del original, documento del titular, error de digitación y misma persona de la cuenta y ficha. Introducir la contraseña actual de **ADMIN**, no la del conductor.
8. Pulsar **Confirmar rectificación** y esperar la confirmación del servidor. Los campos de DNI y contraseña se borran al enviar. No se deben enviar por chat, incluir en el motivo ni guardar en capturas.
9. Después del éxito, abrir **Ver detalle** de la misma ficha, consultar su estado y continuar con **Abrir expediente de regularización**. Usar un motivo sin datos personales y confirmar la conservación de la cuenta e historial.
10. Continuar la revisión documental, aprobación personal y vinculación a la cuenta existente según el procedimiento general. La rectificación por sí sola no permite iniciar sesión al conductor, no verifica su correo y no habilita la conducción.

## Si no se confirma

Ante una respuesta perdida o rechazada, consultar nuevamente **Conductores** y **Solicitudes de incorporación** antes de otra acción. El CRM no reenvía automáticamente una contraseña o DNI. Una nueva operación requiere volver a introducir los datos personalmente.

Un conflicto puede indicar documento distinto de la ficha, otro titular ya registrado, expediente vinculado, trabajo pendiente, identidad modificada concurrentemente u operación incompatible. No se resuelve cambiando la ficha para forzar una coincidencia. Una contraseña incorrecta rechaza la reautenticación; se mantienen los límites de intentos.

## Protección técnica

`POST /api/v1/drivers/{driverId}/identity-review/corrections` exige rol ADMIN, organización, correo e identidad verificados, aceptación vigente, reautenticación y confirmaciones expresas. Cada operación usa una clave y una prueba de contenido para evitar cambios al repetirla.

La migración nueva V51 conserva V1 a V50 y añade `person_identity_review_correction`. La función controlada archiva la revisión anterior completa, sus huellas protegidas, autor, motivo, referencia y operación antes de corregir la raíz revisada. No conserva el DNI original ni la contraseña enviada en ese historial. La evidencia es inmutable y no se permite escribirla o falsificarla con el rol de aplicación. La excepción del trigger exige autoridad y fila exactas en la misma transacción; se conserva la vía de retención autorizada de V49.

Se mantienen los UUID, contraseña, correo, ficha, grupo y ledger de creación. Se revocan las sesiones del conductor; su ficha sigue inactiva. No se envían correos, se crean cuentas, se aprueban documentos o se otorgan habilitaciones por rectificar la revisión.

El arranque nativo requiere V1 a V51, sin migraciones fallidas ni repetibles. La base histórica protegida `ruta_fija_test` mantiene V1 a V50 más su repetible exacto. El launcher valida estos perfiles y continúa sin ejecutar migraciones automáticamente.
