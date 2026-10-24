# Cierre de habilitación operativa: escenario sintético Taxi

Fecha local: 24/10/2026, America/Lima. Resultado: **COMPLETADO LOCALMENTE, CONDUCTOR ACTIVO Y POCO EN PANTALLA OPERATIVA**.

El propietario autorizó expresamente completar los datos sintéticos y aprobar la incorporación por medio del ADMIN. Se conservaron la cuenta, contraseña, correo, teléfono, DNI, ficha, grupo, documentación personal aprobada e historial existentes. No se creó otra cuenta ni se atribuyó validez documental real a las fuentes ficticias de laboratorio.

## Estado inicial y diagnóstico

La solicitud móvil `3ad7f3aa-4ef6-4f7a-8f09-65bb5d8625d6` estaba pendiente para la unidad `5d60bdfc-e857-476f-8497-db616e508d13`, placa `T1F-456`. La membresía en La Primavera y las cuatro revisiones VEH-01..04 ya estaban guardadas. Faltaban las revisiones de empresa, la decisión de solicitud, el período y la activación. El conductor tenía correo verificado, ficha inactiva, NO_DISPONIBLE y ninguna suspensión.

El mensaje del CRM «La sesión no permite esta consulta. Inicia sesión de nuevo.» corresponde al tratamiento de HTTP401 de la página de habilitación. El UUID era válido. Una sesión ADMIN nueva autenticó correctamente y pudo completar todas las operaciones. El TTL observado fue 900 segundos. La causa exacta del rechazo del token anterior no se acredita: no se conservó ese token/respuesta del navegador, por lo que no se distingue expiración de revocación. Las escrituras de habilitación conservan SKIP_AUTH_REFRESH para evitar reenvío automático; no se modificaron autenticación, roles, plazos ni reglas de negocio.

## Operaciones realizadas mediante la API

| Orden | Operación | HTTP | Versión de habilitación resultante |
|---|---|---|---|
| 1 | Revisiones EMP-01 y EMP-02 CONFORME, EMP-03 NO_APLICA_JUSTIFICADO por Taxi; fuentes SIM explícitas | 200 | 3 |
| 2 | Aprobación de la solicitud de unidad existente | 200 | 4 |
| 3 | Período COMPANY de la unidad: 24/10/2026 a 25/10/2026 inclusive, Lima | 200 | 5 |
| 4 | Activación operativa del conductor existente | 200 | 6 |

El fin almacenado del período es exclusivo: **26/10/2026 00:00 Lima** (`2026-10-26T05:00:00Z`). Para continuar pruebas después se requiere un período autorizado vigente, cubierto por las revisiones. La disponibilidad quedó NO_DISPONIBLE y no se inició captura GPS, asignación ni servicio.

Las operaciones utilizaron la versión observada y claves únicas conservadas con sus pruebas de contenido. No se ejecutó SQL para alterar estados de negocio, omitir validaciones o escribir aprobaciones; los cambios se realizaron mediante sus controladores y servicios existentes. Los comandos HTTP y resultados constan en [operations.json](../evidencia/habilitacion-taxi-sintetica-2026-10-24/operations.json) y [http-statuses.json](../evidencia/habilitacion-taxi-sintetica-2026-10-24/http-statuses.json).

## Verificación de acceso y dispositivo físico

GET de habilitación e incorporación: operativo confirmado, documentación CURRENT y bloqueos vacíos. Sesión móvil aislada: login200 sin OTP, alcance OPERATIONS; consulta de habilitación200, perfil200 y renovación200 con alcance OPERATIONS. Las sesiones aisladas de prueba se cerraron204, conservando la familia de sesión del POCO.

En el POCO real desbloqueado se pulsó Consultar estado actual y se observó «Requisitos operativos vigentes según el servidor». Luego se pulsó Comprobar acceso operativo; la aplicación `pe.rutafija.conductor` pasó a su pantalla principal con Inicio, Asignaciones y Perfil. No se reintrodujeron credenciales, se enviaron documentos personales o se generó otra solicitud de unidad en el teléfono. La evidencia UI detallada permanece privada; se publican únicamente acciones, etiquetas comprobadas y hashes.

El comando uiautomator emitió un aviso MIUI por theme_compatibility.xml ausente; la extracción XML y las interacciones finalizaron correctamente. No es un fallo de la app. API y CRM finalizaron con HTTP200; sus archivos stderr estaban vacíos.

## Conservación y respaldo

Las 105 tablas de la base histórica protegida mantienen las mismas huellas. Se verificó igualdad de los atributos críticos de cuenta y ficha, documentos personales, revisiones de identidad, ledger, términos y aceptaciones, migraciones, asignaciones y membresías. No se hicieron migraciones, limpiezas globales, cambios de contraseña, verificaciones OTP por el agente ni comandos Git.

Respaldo previo: `backend/.local/accesos/habilitacion-taxi-20261024/pre-habilitacion-sintetica.dump`, **691375 bytes**, listado de restauración legible. SHA-256: `ce124ae4779fe6216a4bc1543c981ac1262866f736d2133b083bb4174fd5daaf`.

La evidencia [completion.json](../evidencia/habilitacion-taxi-sintetica-2026-10-24/completion.json), [poco-validation.json](../evidencia/habilitacion-taxi-sintetica-2026-10-24/poco-validation.json) y [final-integrity.json](../evidencia/habilitacion-taxi-sintetica-2026-10-24/final-integrity.json) distingue el acceso operativo local del cumplimiento documental real. Las referencias SIM y la habilitación de este caso sirven exclusivamente a la prueba sintética autorizada.
