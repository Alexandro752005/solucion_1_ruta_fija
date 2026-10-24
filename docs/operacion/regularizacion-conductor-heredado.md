# Regularizar y acceder con una cuenta CONDUCTOR existente

Flujo local de Ruta Fija V3.1. Fecha: 23/10/2026. Conserva el usuario, contraseña, ficha de conductor, organización, grupo e historial. Reutiliza el esquema V50; no crea otra cuenta ni consume una creación histórica.

## Preparación

ADMIN debe tener correo e identidad verificados y haber aceptado los términos vigentes. La cuenta CONDUCTOR debe estar activa, asociada a su ficha y organización, y conservar un grupo activo. Su documento debe ser coherente con la identidad revisada; las discrepancias requieren una corrección documental autorizada, no otra cuenta ni datos inventados.

No puede haber asignaciones pendientes o tramos en ejecución. En la cuenta local que motivó esta implementación existían dos asignaciones de prueba programadas. El propietario confirmó expresamente que eran pruebas antiguas y autorizó cancelarlas conservando su historial. Se cancelaron mediante la API ADMIN; ambas filas se conservaron. No se declaró ningún servicio completado en nombre del conductor.

Las credenciales se consultan en el archivo privado del propietario `backend/.local/accesos/CREDENCIALES_ACTIVAS.md`. Esta guía no incluye contraseñas. No es necesario cambiar la APK para utilizar el flujo de regularización desde el CRM.

## Algoritmo para el titular ADMIN

1. Abrir `http://127.0.0.1:4300/login` e ingresar como ADMIN. En **Conductores**, localizar la ficha existente y pulsar **Revisar identidad restringida**.
2. Comprobar personalmente el documento original del titular. Introducir su DNI de ocho dígitos, el método realmente utilizado, una referencia y un motivo sin datos personales. Completar las confirmaciones y pulsar **Confirmar revisión**. Una persona no puede adoptar las cuentas de otros roles con el mismo DNI.
3. Esta revisión puede desactivar la ficha y revocar sus sesiones. Es el comportamiento previsto: no concede aprobación documental ni habilita conducción.
4. Abrir **Ver detalle** de la misma ficha y consultar su estado actualizado. En **Incorporación y condiciones de operación**, pulsar **Abrir expediente de regularización**. El botón requiere identidad revisada y ausencia de otro expediente asociado.
5. Escribir un motivo sin DNI, teléfonos, correo o secretos; por ejemplo, `Regularización de cuenta existente`. Confirmar expresamente que se conservarán cuenta, ficha e historial y enviar la apertura.
6. Anotar el código de expediente confirmado. Abrir **Solicitudes de incorporación** y pulsar **Abrir expediente restringido** en esa solicitud. La apertura deja el expediente **PENDING_REVIEW**: no aprueba documentos ni verifica el correo.
7. Pulsar **Revisar documentación**, elegir la modalidad correspondiente y registrar el cotejo visual real de los requisitos del catálogo `L01-CHICLAYO 0.1.0`. Guardar la revisión antes de decidir.

   | Requisito | Alcance |
   |---|---|
   | CON-01 | Identidad del titular |
   | CON-02 | Licencia compatible con el servicio M1 |
   | CON-03 | Habilitación personal aplicable |
   | CON-04 | Otros requisitos personales aplicables |
   | CON-05 | Condiciones personales de colectivo |

8. Registrar vigencias, fuentes y referencias de comprobación. Un requisito desconocido, observado, pendiente o por contrastar impide la aprobación. La no aplicabilidad requiere la justificación permitida por el catálogo; no sustituye requisitos obligatorios. No se cargan fotografías ni PDF.
9. Cuando todo corresponda, registrar el motivo y pulsar **Aprobar personalmente**. Esta decisión es del ADMIN que efectuó la comprobación. Todavía falta la vinculación final.
10. En el expediente aprobado, pulsar **Vincular expediente a cuenta existente**. La aplicación conserva el grupo de la ficha y comprueba que siga activo. No ofrece crear otra cuenta ni seleccionar un grupo diferente para este flujo.
11. Comprobar que el servidor confirme la cuenta vinculada. Abrir otra vez el detalle del conductor para consultar el estado. La ficha permanece sin habilitación operativa automática.

## Si se devuelve para subsanación

ADMIN puede marcar requisitos observados y utilizar **Devolver para subsanación**. Cuando reciba la subsanación por el canal personal autorizado, registra el motivo y pulsa **Reabrir revisión tras subsanación**. Esto devuelve el expediente a revisión pendiente sin aprobar ni cambiar documentos automáticamente. ADMIN actualiza el cotejo y decide otra vez. No se exige acceso móvil previo al conductor para completar este circuito.

Un expediente rechazado o una identidad ya asociada no se reabre ni se libera automáticamente para crear otra cuenta. Debe conservarse y revisarse el caso mediante el procedimiento autorizado.

## Respuesta perdida o recarga del navegador

Una apertura en curso bloquea acciones paralelas. Si no se confirma la respuesta, consultar la misma operación mantiene su clave y contenido, sin reintento automático. Cerrar la ventana no revierte lo procesado por el servidor.

Al recargar el detalle, el estado reconoce el expediente pendiente mediante su vínculo explícito y el evento de apertura ADMIN. Muestra su código y el enlace a Solicitudes, y deja de ofrecer otra apertura. El servidor rechaza claves reutilizadas con otro contenido y asociaciones duplicadas.

## Algoritmo de ingreso en la app

1. Después de confirmar la vinculación del expediente aprobado, abrir la app instalada y usar el correo y contraseña actuales de la cuenta existente.
2. Si el correo todavía está pendiente, recibir el OTP en la bandeja del titular e introducirlo únicamente en la app. Se conservan el vencimiento, intentos y límites de envío existentes. La regularización no genera ni acepta códigos en nombre de la persona.
3. Tras verificar el correo, la app permite el acceso de **incorporación** cuando falta habilitación operativa. La cuenta y sus credenciales son las mismas.
4. Para ponerse DISPONIBLE, aceptar servicios o iniciar recorridos, ADMIN debe completar aparte la habilitación, vigencias, grupo y unidad. El acceso a la app no habilita esas operaciones por sí solo.

## Contrato y trazabilidad

- Apertura: `POST /api/v1/drivers/{driverId}/regularization`, con motivo, clave de operación y confirmación de conservación.
- Provisión del legado: `POST /api/v1/onboarding/requests/{id}/provision-existing`, con decisión versionada e idempotente.
- Reapertura administrativa: `POST /api/v1/onboarding/requests/{id}/resubmit-existing`, sólo para el expediente legado devuelto y aún no provisionado.
- La identidad preliminar tiene una contraseña aleatoria inaccesible; nunca sustituye la credencial vigente. La referencia administrativa de invitación se consume internamente y no acredita correo enviado ni consentimiento del conductor.
- Se preservan los controles de roles, organización, términos, identidad, aprobación, presupuesto OTP y operación. Los campos de provisión permanecen nulos hasta la aprobación y vinculación efectivas.
- [Plan de implementación y QA](plan-regularizacion-conductor-heredado.md).

## Actualización posterior: error de digitación en la revisión de identidad

El flujo descrito arriba se implementó inicialmente sobre V50. La rectificación controlada posterior requiere V51 en la base nativa; los antecedentes y evidencias de V50 conservan su valor histórico. Si ADMIN digitó por error el DNI al revisar la identidad, debe usar **Rectificar DNI ingresado por error**, comprobar nuevamente el original y reautenticarse. No se reemplaza la ficha ni se borra la revisión anterior. Seguir el [algoritmo de rectificación](rectificar-dni-revision-conductor.md) antes de volver a abrir la regularización.
