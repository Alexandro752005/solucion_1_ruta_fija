# F3.2 - Sesion movil y errores uniformes

## Objetivo cerrado

F3.2 agrega una sesion nativa exclusiva para el conductor vinculado, sin
mezclarla con la cookie del CRM. No crea Flutter ni endpoints operativos de
movil; solo `login`, `refresh` y `logout` bajo `/api/v1/mobile/auth`.

## Precondiciones

- G2 y F3.1B estan aprobadas.
- PostgreSQL 16 nativo esta disponible solo en `127.0.0.1:5432`.
- Existe `backend/.local/ruta-fija-native.env`, ignorado por Git.
- Desarrollo tiene Flyway V1-V8; no ejecute scripts de V9/V10.
- No se usa Docker.

## Verificacion reproducible

Desde la raiz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaF32MobileSession.ps1
.\verificar_ruta_fija.bat
~~~

La primera orden comprueba configuracion local privada, V1-V8, privilegios,
las tres rutas de sesion permitidas, separacion web/movil, bloqueo de refresh,
errores uniformes y ausencia de rutas moviles operativas. Su salida aprobada
es:

~~~text
F3_2_SESSION_AUDIT=PASS flyway=V1-V8 mobile_auth=login,refresh,logout conductor_bound=1 refresh=body_rotating_for_update errors=code,correlationId operational_mobile_endpoints=0 docker=0
~~~

La segunda ejecuta unidades e integracion contra `ruta_fija_test`, nunca contra
la base de desarrollo. Su cierre esperado es:

~~~text
F3_2_NATIVE_VERIFY=PASS flyway=V1-V8 mobile_session=PASS docker=0
~~~

Tambien puede ejecutar la tarea de VS Code **Ruta Fija: auditar sesion movil
(F3.2)**.

## Contrato para el futuro cliente Flutter

El cliente debera guardar el `refreshToken` solo en almacenamiento seguro de
Android y enviar `Authorization: Bearer <accessToken>` cuando F3.3 publique
rutas protegidas. No debe enviar `organizationId`, `driverId`, rol ni aceptar
una respuesta del CRM como si fuera una accion del conductor.

Fuera del desarrollo local, el transporte debe ser HTTPS. No registre los
cuerpos de login o refresh y no copie tokens en capturas, tickets o evidencia.

## Limite de la fase

No se debe probar aun una accion de asignacion, GPS, consentimiento,
incidencia, comunicado, notificacion ni una pantalla Flutter: no pertenecen a
F3.2. La siguiente fase autorizable es F3.3.
