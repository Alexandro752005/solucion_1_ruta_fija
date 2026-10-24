> **Estado actualizado 2026-10-21: ensayo limitado satisfactorio, archivado.** Instalación y arranque autorizados del paquete .l08; el usuario confirmó validación del formulario vacío y el mensaje de rechazo del único login ficticio HTTP503. Sin peticiones adicionales en105s. Servidor cerrado y reverse del caso retirado; ambas apps se conservan. [Evidencia](../evidencia/L-08/incremento-1-candidato-y-preautenticacion/telefono/cierre-publico.json). No acredita login operativo/GPS/batería/TalkBack. La preparación inferior se conserva como antecedente.

# L08.1 — Ensayo físico de preautenticación, paquete separado

Estado: **PREPARADO, NO INSTALADO NI EJECUTADO**. El usuario confirmó USB y ADB observó un Poco X3 Pro(modeloM2102J20SG),Android13/API33. No se registró serie ni se consultó ubicación. La app Ruta Fija anterior está instalada y se conserva.

## Artefacto concreto

- [APK debug](../../artifacts/L-08-preparacion-2026-10-21/ruta-fija-l08-debug.apk),SHA-256 `2e0f38e5c99cc32241c2b444cce3d8e1148027da7979cde285a63b0808e28e3a`.
- Paquete esperado `pe.rutafija.conductor.l08`;nombre **Ruta Fija Pruebas**;mínimo API29(Android10),objetivo actual36. Debug,únicamente ensayo local;no es candidato firmado de producción.
- API compilada `http://127.0.0.1:4311/api/v1`,acceso USB reverse,servidor ligado solo a127.0.0.1. No LAN,HTTPS público,base piloto o correo. Los comandos de entrega release están bloqueados por falta de firma aprobada;no se generó una clave definitiva.
- Debe comprobarse el ID del APK real con AAPT antes de instalar. Una compilación Flutter previa no propagó la propiedad de aislamiento:se rechazó en ese control y no se instaló. El artefacto enlazado procede de Gradle con `-PrutaFijaL08Isolated=true` explícito y validación posterior del paquete.

## Límites

Este paso valida instalación/arranque,formulario,etiquetas/errores y negativa de acceso en el dispositivo. El servidor de ensayo **siempre responde503**,sin autenticar o simular éxito;no se conecta al backend/PostgreSQL,no crea cuenta ni consume cupo. No demuestra login operativo,GPS,batería,notificaciones,descarga Android ni aislamiento empresarial. No sustituye el recorrido operativo futuro.

No introducir datos o contraseñas reales,OTP ni claves. No aceptar términos,enrolar propietario,subir documentos ni otorgar ubicación. Una prueba con TalkBack y escalado debe ejecutarla personalmente el usuario y archivarse como tal;el árbol accesible automático no la acredita. La APK no se instala o abre hasta la autorización específica del usuario para este paso,puesto que el acceso anterior al teléfono se limitó a inventario.

## Secuencia preparada para A-COORD

1. Confirmar exactamente un dispositivo ADB autorizado,sin imprimir su serie. Verificar hash/paquete/minSdk y que `pe.rutafija.conductor.l08` no exista;si existe,conservar y revisar,sin `uninstall`,borrado de datos o reemplazo por inferencia.
2. Con autorización,`adb install --no-streaming -t` sobre el APK enlazado,sin `-r` ni `-g`. No cambiar la instalación `pe.rutafija.conductor`. Si falla,conservar el estado y diagnosticar antes de repetir.
3. Antes de crear reverse,comprobar que tcp4311 no está ocupado por otra sesión. Servir con `node frontend/e2e/l08-phone-preauth.mjs --serve-public-only`;solo127.0.0.1 y respuestas503. Crear únicamente `adb reverse tcp:4311 tcp:4311`,registrando si lo creó este ensayo.
4. El usuario abre **Ruta Fija Pruebas**,lee etiquetas,envía el formulario vacío y comprueba los errores. No usar la app anterior. Después puede usar únicamente los datos ficticios `conductor.sin-cuenta@rutafija.test` y `DatoFicticio-L08-NoEsCuenta`;no son credenciales de ninguna cuenta.
5. Comprobar personalmente que la respuesta no inicia sesión,habilita operación o encola un reintento. El servidor registra solo método/ruta normalizada/estado/hora,nunca cuerpo,cabeceras,cookies o valores ingresados. Archivar el resultado humano separado de la observación HTTP.
6. Para cerrar el servidor,escribir `cerrar` en su terminal de control. Retirar únicamente el reverse creado por este caso con `adb reverse --remove tcp:4311`. No remover otros mapeos,revocar la confianza USB o desinstalar apps por inferencia. Mantener el resultado de L08 EN_CURSO.

RUN público de este paso `9f2be6c1-77be-4d2b-8d72-f83dbd6f5247`. Solo el ensayo de preautenticación;no representa un nuevo participante/ADMIN/CONDUCTOR ni reapertura de L01…07.
