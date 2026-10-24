# Correo OTP y puente móvil del runtime local

Actualización del 23/10/2026. Ámbito: desarrollo local `solucion_ruta_fija_1`, esquema V50. Se conservan las restricciones de identidad, incorporación, términos y habilitación. No habilita el piloto real.

## Correos y contraseñas

El propietario indicó un Gmail diferente para SUPER_ADMIN, ADMIN y CONDUCTOR. Se actualizaron las tres cuentas existentes por UUID, manteniendo contraseñas, roles, organización, historial de creación y estado activo. Se incrementó la versión de sesión y se añadieron tres eventos `OWNER_AUTHORIZED_LOCAL_EMAIL_UPDATE` a la auditoría. No se marcaron correos como verificados.

Los accesos actuales están exclusivamente en `backend/.local/accesos/CREDENCIALES_ACTIVAS.md`, excluido de Git y con permisos restringidos. Los usuarios anteriores `.local` y el correo anterior del conductor son antecedentes; usar los Gmail de ese archivo. Los respaldos y las pruebas de preservación de esta operación están en la subcarpeta privada `backend/.local/accesos/preparacion-correo-20261023_1803/`.

`APP_OTP_EMAIL_ALLOWED_RECIPIENTS` es una opción privada del importador nativo. Contiene una lista de direcciones diferentes, separadas por comas, sin nombres de presentación ni espacios. Es opcional: las configuraciones existentes sin esta variable conservan el destinatario predeterminado anterior. El perfil de pruebas no hereda esta opción de runtime.

El remitente sigue configurado mediante `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`; sus valores no se incluyen en código ni en esta guía. Se comprobó conexión SMTP autenticada con TLS mediante `JavaMailSenderImpl.testConnection()`, sin enviar mensajes. Esto acredita conexión/autenticación, no recepción de un correo en una bandeja concreta.

## Verificación personal desde el CRM

1. Iniciar el proyecto en una consola normal con `iniciar_ruta_fija.bat`.
2. Abrir `http://127.0.0.1:4300/login`.
3. Introducir el Gmail de ADMIN o SUPER_ADMIN y su contraseña del archivo privado actualizado.
4. El primer acceso solicita al backend el código de seis dígitos y muestra la pantalla de verificación. El código se envía al Gmail de esa cuenta; no se obtiene mediante un SELECT de la base.
5. Abrir el mensaje en esa bandeja e introducir el código únicamente en la interfaz. Vence a los cinco minutos. La pantalla permite reenviar dentro de los límites establecidos.
6. Después de verificar, continúan las compuertas correspondientes al rol. Para ADMIN, la aceptación personal de términos y los requisitos de identidad siguen siendo necesarios.

Cada titular completa su propia verificación. La preparación de las direcciones no envía códigos automáticamente y no sustituye la recepción ni la introducción del código.

## Cuenta heredada del conductor

La cuenta móvil existente tiene conductor vinculado en su organización, pero la base nativa no contiene su expediente de incorporación aprobado/provisionado. `ConductorProvisioningPolicy.requireApproved` impide el login antes de comenzar el OTP.

La apertura pública de una nueva solicitud tampoco admite un correo ya existente en `app_user`. Por tanto, esta cuenta V12 necesita una conciliación controlada de incorporación que conserve su UUID y su historial, con la revisión personal/documental correspondiente. No basta con una nueva invitación al mismo correo. Esta operación de correo no inventa un expediente ni acredita una aprobación personal. La cuenta móvil sigue pendiente de esa conciliación antes de poder completar el acceso.

## Inicio automático del puente

`iniciar_ruta_fija.bat` inicia primero PostgreSQL con lease, API8080 y CRM4300. En modo normal llama después a `Invoke-RutaFijaMobileLocalBridge.ps1 -Action Start -Execute`.

El puente solo se activa cuando existe una configuración privada habilitada en `backend/.local/ruta-fija-mobile-bridge.json`. Si no existe o está deshabilitada, el proyecto mantiene el arranque API/CRM sin exponer un puerto móvil. El smoke de solo lectura no inicia el puente.

La configuración identifica la IPv4 privada del PC, la de un solo teléfono y la URL compilada en su APK. Se exige una interfaz local /24 con perfil de red Private, un teléfono distinto en la misma subred y el puerto4311. La API permanece en loopback8080. No se agregan reglas del firewall ni túneles públicos automáticamente.

El puente verifica el PID y el instante de inicio del backend. Se registra su propio PID, ejecutable, fecha, fuente y hash; una segunda llamada reutiliza únicamente la instancia que coincide. No libera puertos de procesos ajenos. El tráfico se limita al teléfono configurado y a rutas móviles, incorporación y recuperación móvil; el backend conserva todos sus controles de autenticación y negocio. No registra cuerpos, tokens, códigos ni contraseñas.

Para comprobarlo después del arranque:

```powershell
.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test
.\scripts\Invoke-RutaFijaMobileLocalBridge.ps1 -Action Test -Execute
```

Para terminar:

```powershell
.\finalizar_ruta_fija.bat
```

El cierre detiene API/CRM y comprueba la retirada del puente propio. El puente también termina al desaparecer la instancia exacta del backend. PostgreSQL se conserva si ya estaba activo o no hay lease de propiedad. No hace falta ejecutar el auxiliar Python temporal del ensayo de migración.

El arranque y cierre deben realizarse desde una consola con acceso a los procesos creados. Si una sesión anterior fue iniciada con elevación y Windows oculta su identidad, el cierre se rechaza; se debe cerrarla con el mismo nivel de acceso y volver al arranque normal. No se debilita la comprobación de identidad para detenerla.

## Validación y trazabilidad

- Cambio de correos transaccional, con respaldo PostgreSQL custom previo, conservación del hash de contraseña y auditoría sin contraseñas.
- Importador: rutas SMTP opcionales, rechazo de duplicados, listas vacías, espacios y direcciones de presentación.
- Puente: compilación Java21, pruebas de rutas y pruebas de propiedad de procesos, incluido el cierre que coincide con la salida automática del proceso.
- Regresión de seguridad de los BAT y de la lease PostgreSQL; build del backend sin conexión ni ejecución de integraciones.
- Prueba real de inicio/cierre/reinicio, salud API/CRM y acceso desde el POCO. Los resultados finales y hashes de fuentes se guardan en `.runtime/preparacion-correo-20261023_1803/`; no constituyen prueba de login autenticado ni de recepción OTP.

Las actas, términos publicados, migraciones y manifiestos históricos permanecen inalterados. El build local actualizado sustituye al JAR de la sesión anterior para este runtime; no se reemplazó la APK instalada ni se publicó una nueva entrega.

## Actualización posterior: términos disponibles en la base nativa

El 23/10/2026 se publicó el documento ADMIN aprobado `1.0.0` también en `solucion_ruta_fija_1`. Su contenido y aprobación se conservaron sin cambios. El CRM ya lo entrega con HTTP 200 a ADMIN con correo e identidad verificados; la lectura y aceptación personal siguen siendo obligatorias. Véanse el [procedimiento, respaldo y pruebas de publicación](publicacion-terminos-admin-runtime-local.md). La conciliación pendiente de la cuenta heredada CONDUCTOR es un procedimiento separado.

## Actualización posterior: flujo de regularización implementado

Se construyó el flujo ADMIN de apertura, revisión, aprobación, subsanación y vinculación de un expediente a la cuenta heredada existente. Se mantienen el esquema V50, los identificadores y las credenciales, y el OTP sigue completándose por el titular en la app después de la vinculación. La descripción anterior de ausencia del flujo corresponde al estado previo. Véanse el [algoritmo de uso vigente](regularizacion-conductor-heredado.md) y el [plan y criterios de comprobación](plan-regularizacion-conductor-heredado.md). Su implementación no sustituye las decisiones personales pendientes del caso real.
