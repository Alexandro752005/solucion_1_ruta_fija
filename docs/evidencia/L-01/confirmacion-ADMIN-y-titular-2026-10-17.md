# L-01 — Disponibilidad de ADMIN y rol previsto del titular

Fecha: 2026-10-17, America/Lima. L-01 **EN_CURSO**; L-02 **NO INICIADA**.

## Confirmación recibida

El titular confirma que **aún no hay un ADMIN legítimo preparado** y que su cuenta prevista será **SUPER_ADMIN**. Es una declaración sobre la preparación de los participantes y el rol previsto; no acredita que esa cuenta ya exista ni autoriza a crear otra cuenta por inferencia.

Esta respuesta resuelve la pregunta sobre disponibilidad de ADMIN. No se vuelve a solicitar la misma información ni la aprobación de los términos ya concedida.

## Efecto sobre los criterios

- **CI-L01-01 VERIFICADO:** se conserva la correspondencia y limpieza del caso OTP ya archivadas. No se repite el envío.
- **CI-L01-03 PENDIENTE de aceptación real:** ADMIN 1.0.0 está aprobado y publicado con SHA-256 `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417`. La última comprobación archivada registró cero aceptaciones; esta actualización documental no consulta de nuevo la base. Falta preparar una persona ADMIN legítima y su aceptación personal expresa.
- **CI-L01-07 PENDIENTE del titular:** solo está confirmada una PC Windows. Siguen pendientes los medios independientes, la preparación e inscripción personal de la clave, la comprobación de la copia cifrada y el ensayo. El kit público preparado no acredita custodia.

Una persona no obtiene dos roles activos. El titular previsto como SUPER_ADMIN no se convierte también en ADMIN para acreditar CI-L01-03, ni acepta en nombre de otra persona. Las cuentas y aceptaciones sintéticas tampoco satisfacen esa intervención humana.

## Continuación cuando se prepare un ADMIN legítimo

1. Identificar al participante que ejercerá ADMIN y su organización, respetando la revisión restringida de identidad, los cupos históricos y las condiciones de información y protección antes de introducir nuevos datos reales. No comunicar datos personales ni secretos por chat.
2. Seguir el alta aprobada OP-ADMIN-01: SUPER_ADMIN prepara la cuenta ADMIN inactiva desde `/platform-admins`; el participante establece su propia contraseña y verifica su correo en `/admin-activation`. La preparación consume una de las cuatro altas históricas ADMIN del entorno correspondiente; no se crean cuentas ahora.
3. El propio ADMIN lee el documento publicado y lo acepta expresamente en su activación; si ya tuviera una cuenta elegible, utiliza `/admin-terms`. La aceptación precede a la habilitación y vincula cuenta, organización, documento, versión, huella y fecha del servidor.
4. Suspender las pruebas que limpian `ruta_fija_test` durante ese paso humano. Después contrastar y archivar la evidencia saneada de la aceptación antes de reanudarlas. Solo entonces podrá verificarse CI-L01-03.

Estos pasos no habilitan una base piloto ni amplían permisos de correo o despliegue. Hoy puede continuar el trabajo técnico autorizado en test, incluidas sus limpiezas: la suspensión por el caso OTP finalizado ya se levantó. Habrá que suspenderlas de nuevo al comenzar un próximo paso humano.

## Alcance de esta actualización

Registro documental de la respuesta y sus dependencias. Sin consultas ni escrituras en base de datos, pruebas de ejecución, cuentas nuevas, aceptaciones, envíos, generación o inscripción de claves. Las evidencias anteriores y los archivos publicados de términos permanecen intactos.

Referencias: [evidencia técnica archivada](cierre-parcial-CI01-y-terminos-2026-10-17.md), [matriz vigente](matriz-cierre-L01-revision-2026-10-14.md), [protocolo del titular](recuperacion-superadmin-protocolo.md).
