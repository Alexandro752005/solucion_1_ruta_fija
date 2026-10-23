# L06-CON-01 — Caso conectado propuesto, v0.1.0

2026-10-20, America/Lima. **PROPUESTA; sin autorización de alta persistente todavía**. Referencia reservada `51e69ee6-e4a0-4c3f-9a35-f19de0072422`. [Contrato](../contratos/L-06.md).

## Alcance concreto solicitado

Un caso nuevo exclusivamente en `ruta_fija_test` con **máximo un CONDUCTOR sintético nuevo**. Reutilizar el ADMIN sintético elegible y su organización; no crear ADMIN/SUPER_ADMIN ni reactivar cuentas retiradas de L01…05. El preflight V44 conservó ledger **1/3/4**, cupos **0/1/16** y dos aceptaciones. Con el alta autorizada quedaría **1/3/5**, con 15 altas CONDUCTOR restantes; deshabilitar no devuelve cupo.

1. Registrar marcador/lock y estado reanudable antes de la primera escritura. Comprobar base/roles, Flyway, contadores, identidades/gates y sellos. El arnés no migra, limpia globalmente ni altera estados para forzar resultados.
2. Incorporar esa identidad ficticia mediante el flujo normal: invitación, OTP simulado, solicitud, revisión documental estructurada restringida y aprobación. Crear únicamente grupo(s), unidad/período y recursos sintéticos necesarios del mismo caso para su habilitación. Credenciales locales privadas, sin secretos en argumentos, chat o evidencia.
3. Recorrido automatizado identificado: CRM en navegador local y widgets/repositorios Flutter de producción conectados por HTTP real al backend/PostgreSQL test. Reporte explícito, designación ADMIN, información/seguimientos, cierre y caso posterior vinculado sin reapertura. Conservar la separación entre incidente y atención segura de L05.
4. Publicación a grupo secundario, consulta sin confirmar lectura, salida del grupo conservando el mensaje recibido y exclusión de publicaciones nuevas. Leído expreso, pendiente ADMIN y retirada por seguridad. Comprobar que la ausencia de lectura no cambia ofertas ni servicios.
5. Bandeja propia durable, controles de lectura independientes de Leído del comunicado y resultados externos no configurados visibles ADMIN. No configurar Firebase/dispositivos, enviar correo/push real ni atribuir entrega a un equipo.
6. Carreras HTTP independientes del mismo caso: lectura frente a retirada por seguridad y acciones concurrentes de incidente con una versión. Mantener como válidos solo los estados realmente confirmados por servidor; un fallo conserva el mismo caso, sin altas sucesivas.
7. Retorno compatible con las clases/esquema actuales y operaciones deshabilitadas en el contexto test: conservar consulta limitada y gates. No ejecutar binarios antiguos contra datos aditivos ni revertir SQL.

## Salida y límites

Archivar evidencia saneada antes de retirar. Deshabilitar únicamente la cuenta/identidad del caso, revocar sus sesiones/períodos y retirar sus recursos conforme a los mecanismos existentes; preservar ledger, expedientes/historial, incidentes y destinatarios/lecturas originales. Verificar ausencia de compromisos abiertos, hashes de casos ajenos, términos y final3; liberar marcador/lock solo al pasar la salida selectiva. Ante error, conservar protección y reanudar el mismo caso.

Esta autorización no sería cierre, piloto, persona real, GPS físico, claves definitivas, proveedor push preparado, SMTP, migraciones de desarrollo/piloto, Git/GitHub, regeneración de final3 ni L-07. Físico permanece en L-08. El arnés y sus guardas deberán estar comprobados antes de usarla.
