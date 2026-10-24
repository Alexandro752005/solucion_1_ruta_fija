# Revisión OTP, aislamiento y alcance offline — 2026-10-14

Esta sección separa lo comprobado en código de lo ejecutado en pruebas. No es un cierre de L-01.

## OTP: reglas y cobertura encontrada

Implementación revisada estáticamente:

- Código numérico de seis dígitos; el generador usa `SecureRandom` y conserva ceros iniciales.
- Vigencia de cinco minutos; se acepta un uso y el reenvío reemplaza código y token anteriores.
- Hasta tres solicitudes por usuario en una ventana móvil de sesenta minutos. El desafío conserva el contador aunque cambie propósito/correo; el contador es común a login y recuperación SUPER_ADMIN.
- Dos validaciones consecutivas incorrectas bloquean el desafío treinta minutos y anulan el hash vigente. Mientras dure ese bloqueo no se admite comenzar ni reenviar.
- El OTP se almacena como HMAC-SHA-256 con contexto y el token de verificación como hash SHA-256.

Fuentes: `EmailOtpService.java`, `EmailOtpChallenge.java`, `OtpCodeGenerator.java`, `OtpCodeHasher.java` y `EmailOtpChallengeRepository.java`.

Cobertura ejecutada previamente en `EmailOtpFlowIT`: tres casos para hash/uso único, dos errores y bloqueo, y máximo inicial más dos reenvíos. En esta revalidación todavía no se han vuelto a ejecutar pruebas nuevas para expiración exacta, invalidación después del reenvío, transición en la frontera móvil de 60 minutos ni contadores compartidos entre propósito. `EmailOtpChallengeTest` se añadió para comprobar el borde matemático de la ventana y el bloqueo de 30 minutos; su resultado queda pendiente de ejecución.

## Aislamiento por organización

La identidad del actor se revalida desde el usuario persistido; los repositorios de usuarios, conductores, unidades y operaciones filtran por organización. Hay pruebas existentes de identidad/organización discordante, ADMIN de otra organización que no puede consultar un conductor/asignación/auditoría, y conductor que no puede leer una asignación ajena.

Esto acredita rutas concretas, no una matriz completa de todos los endpoints y verbos. Invitaciones, términos, alta/reemplazo ADMIN y recuperación asistida todavía no tienen rutas que auditar; deben añadirse pruebas de tenant negativo con cada implementación. El endpoint heredado de sincronización offline no tenía cobertura cross-tenant en `MobileOfflineSyncIT`.

## Offline heredado

La inspección inicial encontró el controlador `POST /api/v1/mobile/sync/events` disponible para CONDUCTOR, la cola y transporte cableados en la raíz de dependencias Flutter, y una ruta de sincronización pendiente accesible desde la navegación. También se encontraron flujos operativos que almacenaban acciones para reintento al fallar la red. Por ello, la exclusión de offline en v3.1 no estaba satisfecha solo por excluir sus pruebas.

El cambio para deshabilitar esas rutas en el runtime predeterminado está en curso. No se ha iniciado una aplicación móvil en dispositivo ni se ha ejecutado esta verificación de accesibilidad; el estado final se documentará tras revisar el cambio y correr las pruebas pertinentes. El código histórico se conserva. No se purgará cola persistida potencialmente existente en dispositivos sin evidencia y procedimiento de migración/retención aprobados.

## Invitaciones

Las reglas v3.1 ya determinan emisión únicamente por ADMIN de la empresa, código numérico de seis dígitos distinto del OTP, destinatario identificado, aceptación por cuenta autenticada con correo verificado, un solo uso, vigencia máxima de 120 horas y consumo atómico/idempotente junto con la solicitud de incorporación. El limitador separado del OTP es: cinco códigos incorrectos por cuenta en ventana móvil de quince minutos causan bloqueo de invitaciones por treinta minutos; cincuenta fallos por origen en quince minutos pausan ese endpoint quince minutos. Un bloqueo por origen no bloquea otras acciones ni todas las cuentas detrás de NAT.

No hay todavía enforcement de estas reglas. Falta enlazar aceptación con el expediente mínimo de incorporación y definir cómo una cuenta de conductor aún no asociada a la organización completa el inicio de sesión requerido por la regla de aceptación autenticada. No se informará que están implementadas hasta que exista ruta transaccional y se prueben bordes, idempotencia, concurrencia y aislamiento.
