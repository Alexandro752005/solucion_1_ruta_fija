# Cierre técnico local del flujo de rectificación de DNI

Fecha local: 23/10/2026. Resultado: **IMPLEMENTADO, PROBADO Y DESPLEGADO LOCALMENTE; PENDIENTE LA RECTIFICACIÓN PERSONAL DEL ADMIN**.

El propietario confirmó un error de digitación en la revisión de identidad. La huella revisada no coincidía con el documento de la ficha conservada y bloqueaba correctamente la apertura de regularización. Se añadió un procedimiento expreso de rectificación previa a provisión, con reautenticación, cuatro confirmaciones, coincidencia exacta con la ficha y evidencia inmutable de la revisión anterior. No se sustituyó al titular ni se borró el error.

## Verificación comprobada

| Comprobación | Resultado |
|---|---|
| PostgreSQL integrado, base dedicada y fixtures con rollback | 10/10 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Regresión backend segura, 50 clases sin base compartida | 257/257 pruebas |
| CRM: pruebas de Conductores y Solicitudes | 53/53 pruebas |
| CRM: tipos y compilación de producción | Aprobados; 0 advertencias de build |
| Compuerta de esquema | 14 casos sintéticos aprobados; ambas bases reales aprobadas SELECT-only |
| Migración nativa | Una V51 aplicada en transacción; total 51, ninguna fallida o repetible |
| Historial Flyway anterior | Filas V1..V50 y sus sumas sin cambios |
| Base histórica protegida | 105 tablas con las mismas huellas; mantiene V50 y su repetible exacto |
| API y CRM | HTTP 200, health UP; puertos 8080 y 4300 activos |
| POCO real por puente 4311 | health 200; mobile/me 401 sin sesión; users 404 fuera del alcance móvil |
| Términos y aceptaciones | Documento publicado intacto; aceptación ADMIN existente, ninguna creada por el agente |
| Logs de runtime | backend.stderr.log y frontend.stderr.log vacíos al terminar |

La publicación de términos sólo se comprobó; no se publicó otra versión. El launcher valida el perfil nativo V51 y conserva el perfil histórico V50 de pruebas, sin migrar al arrancar. Se actualizó la comprobación del publicador para exigir el nuevo esquema nativo. El PostgreSQL compartido se preservó durante el reinicio administrado.

Los UUID, roles, correo, contraseña, ficha, grupo, ledger de creación, identidad revisada actual, asignaciones e historial quedaron sin cambios en la instancia real. La nueva tabla de rectificaciones sigue vacía porque la comprobación y entrada del DNI corresponden al ADMIN. Las pruebas de corrección efectiva utilizaron datos sintéticos en una base distinta. No se enviaron OTP ni se concedieron aprobaciones personales.

## Respaldo y evidencia

Respaldo previo: `backend/.local/accesos/correccion-dni-20261023_223428/pre-correccion-dni-v51.dump`, 660191 bytes. SHA-256: `aafea2909eadee7ed9d199593177176a4c536feaddf1c095a9add2c96e1dc9f9`. Archivo de volcado privado; listado de restauración legible. Los originales modificados permanecen en el respaldo privado de fuentes. No se utilizó Git.

Los comandos, resultados, hashes del JAR y manifiesto de archivos constan en [la evidencia de validación](../evidencia/correccion-dni-conductor-2026-10-23/validacion.json) y sus anexos. Las advertencias de instrumentación Mockito/JDK pertenecen al ejecutor de pruebas y no se presentan como fallos del producto. Un error inicial del script de comprobación Python se corrigió antes de cualquier llamada o mutación; la comprobación completa posterior pasó.

## Continuación personal

Actualizar el CRM, entrar como ADMIN y seguir [Rectificar un DNI digitado por error](rectificar-dni-revision-conductor.md). Después del éxito, abrir la regularización de la misma ficha y completar revisión documental, aprobación y vinculación. La habilitación operativa y verificación de correo continúan bajo sus compuertas vigentes. Este cierre acredita el flujo técnico; no acredita una rectificación, aprobación o habilitación realizada por una persona.
