# Información de identificación y privacidad — piloto Ruta Fija

Versión de trabajo 0.1.0; 2026-10-15, America/Lima. **BORRADOR INCOMPLETO; NO APROBADO NI PUBLICADO. No habilita recoger nuevos datos reales.** Separado de las reglas de uso ADMIN por elección expresa del titular.

## Identificación y canal

- Función confirmada: titular, desarrollador y responsable técnico del proyecto Ruta Fija. Esta función no identifica por sí sola al responsable del tratamiento ni su clasificación jurídica.
- **Pendiente del titular en un archivo local:** identificación real del responsable que corresponda al tratamiento efectivo y los datos de contacto/identificación requeridos, sin presumir nombre, domicilio, representación o condición jurídica. No facilitar secretos por chat. Este borrador no sustituye esos datos con un alias ni los publica en CRM/app.
- Canal del proyecto confirmado: rutafija.dev1@gmail.com; revisión cada dos o tres días, sin atención permanente. No se inventa plazo legal de respuesta.
- Contacto operativo CONDUCTOR–ADMIN: teléfono que ADMIN registre para su organización en incidencias; valor todavía no proporcionado. No es un directorio personal general.

## Alcance efectivo y finalidades previstas

Piloto académico taxi/colectivo Chiclayo, Lambayeque, Perú, S/0. Actualmente validación local con datos sintéticos; no hay despliegue piloto acreditado. L-01 trata acceso, incorporación/revisión, cuentas, invitaciones, términos y recuperación. Mapas/GPS y dispositivo físico pertenecen a sus lotes; este borrador no activa captura de ubicación.

| Categoría | Finalidad funcional prevista | Acceso previsto |
|---|---|---|
| Cuenta y contacto: correo, contraseña derivada, estado, rol, organización y referencias | Acceso, verificación y administración de la cuenta | Backend autorizado; vistas ordinarias por referencias sin datos personales. Contraseña/OTP/tokens nunca visibles como historial o mensajes. |
| Identidad administrativa: DNI presentado, HMAC y constancia de cotejo | Evitar duplicar una persona entre roles/cuentas mediante revisión manual | SUPER_ADMIN restringido para su declaración inicial/ADMIN; sin originales almacenados, DNI o digest ordinarios. No integración estatal. |
| Identidad preliminar/expediente CONDUCTOR: nombre/contacto y datos estructurados de identidad, licencia y requisitos aplicables | Invitación, revisión, observación/subsanación y aprobación/rechazo | Postulante por acceso limitado a estado y subsanación; expediente solo ADMIN de la organización, lectura auditada. Sin fotos/PDF ni copias originales. |
| Aceptación de términos: cuenta, organización, documento, versión, huella y hora | Conservar qué contenido fue expresamente aceptado | Alcance autorizado; no aceptar por terceros. |
| Recuperación: constancia mínima, clave pública del titular, retos y credenciales derivadas | Comprobar el procedimiento y revocar accesos anteriores | Flujo independiente o ADMIN propio según rol; clave privada del titular siempre fuera del servidor/repositorio/env. |
| Auditoría funcional mínima | Trazabilidad de accesos/cambios y resultados autorizados | Rol y organización autorizados, sin secretos ni copia de documentos personales. |

No se utiliza la aceptación de reglas ADMIN como autorización general de nuevas finalidades, documentos o ubicación. Antes del uso real deben concretarse la información a cada participante y las condiciones que correspondan, sin afirmar aprobación jurídica por este borrador.

## Proveedores, infraestructura y destinos

- Cuenta emisora Gmail autorizada rutafija.dev1@gmail.com; smtp.gmail.com:587, autenticación y STARTTLS obligatorio. Google controla gmail.com. Recibe la dirección destinataria y el contenido necesario del mensaje de verificación. **No se ha identificado aquí una ubicación geográfica efectiva de ese tratamiento ni condiciones jurídicas; quedan por comprobar**, sin deducirlas de la ubicación del PC.
- PC declarado en Lima, Perú; desarrollo local. Base piloto/HTTPS no activos. No hay copia cifrada ni restauración comprobada; Drive no se registra como destino activo.
- Antes de datos reales: inventariar alojamientos/proveedores/destinos efectivos, accesos, registros y copias. No desplegar ni abrir puertos/túneles para completar este borrador.

## Conservación y retiro

Inicio efectivo al habilitar el piloto; fin/cierre en un mismo acto siete días después, con acta aún pendiente. La idea de agosto y la fecha estimada no inician plazos.

- ADMIN/CONDUCTOR y expediente: deshabilitar al cierre; retiro por campo a los 30 días del fin. HMAC DNI/vínculos personales ADMIN/CONDUCTOR también a 30 días, conservando IDs, historial mínimo y cupos; excepción limitada con finalidad y vencimiento, sin extenderla a toda la base. SUPER_ADMIN/custodia excluidos por inferencia.
- Expediente/revisiones: ADMIN identifica y solicita antes de la baja; responsable técnico ejecuta de forma restringida y auditada, sin reactivar permisos operativos ni acceso ordinario general al expediente.
- Términos/aceptaciones: 90 días después del fin. Evidencia mínima de recuperación: 30 días desde finalizar/cancelar. Auditoría operativa: 6 meses, separada de las otras categorías.
- Las relaciones/huellas vinculables siguen protegidas; no afirmar anonimización irreversible. La eliminación en base activa no acredita retiro de copias.

El [procedimiento](../operacion/L-01-conservacion-y-retiro.md) está preparado; su herramienta completa, pruebas de retiro por categoría/copia y ejecución están pendientes. No se realizó purga.

## Consultas y datos pendientes antes del uso real

Se prevé recibir solicitudes de acceso/corrección/retiro mediante el canal del proyecto y comprobar identidad/alcance sin revelar terceros. **Pendientes:** responsable identificado, procedimiento efectivo de recepción/verificación/respuesta y sus condiciones/plazos aplicables; información a participantes, destinatarios/destinos efectivos y revisión del texto completo. No se atribuyen obligaciones legales no contrastadas ni se declara cumplimiento integral.

Este documento no constituye aprobación de términos, evidencia de ensayo real o autorización para nuevos datos personales, correo adicional, claves reales, despliegue o migración de desarrollo/piloto. L-01 EN_CURSO; preparación del piloto y criterios obligatorios se distinguen en la matriz.
