# L-01 — Registro común de persona — ID-01

Fecha: 2026-10-15. Decisión: **APROBADA; IMPLEMENTACIÓN Y VERIFICACIÓN POR INCREMENTO**. L-01 EN_CURSO; L-02 NO INICIADA.

## Decisión y alcance

El titular respondió «Aprobar ID-01 con revisión restringida» a la propuesta de registro común para SUPER_ADMIN, ADMIN y CONDUCTOR, con huella HMAC del DNI y comprobación manual autorizada. El alias PERSON-01 utilizado provisionalmente en la matriz corresponde a esta misma decisión. No es la identificación del responsable del documento de términos, ni aprueba ese texto.

La regla vigente prohíbe duplicar personas o acumular roles/empresas activos con correos distintos. El registro vincula identidad documental y cuenta estable; verificar un correo no identifica físicamente a la persona. Una recuperación mantiene esa cuenta y su cupo. No se crea otra cuenta para sortear la identidad ya vinculada ni se implementa por inferencia un traslado/cambio de rol futuro.

## Comprobación y datos

- DNI de ocho dígitos como dato de entrada temporal en una vista administrativa restringida. La persona autorizada confirma cotejo manual del original y correspondencia con la persona, método y referencia de la revisión. No hay consulta RENIEC/MTC ni aprobación automática.
- La nueva tabla común conserva HMAC, vínculo a cuenta, actor/método/referencia/hora y estado de revisión; no conserva el número DNI en claro ni copias. Los datos estructurados del expediente CONDUCTOR mantienen su contrato separado y acceso ADMIN propio.
- Las referencias de revisión no deben contener DNI, nombres, teléfono o correo: identifican el acto/caso. Ninguna lista, JWT o respuesta ordinaria publica DNI ni digest.
- El HMAC es un identificador vinculable de finalidad limitada; no se describe como anonimización irreversible. La estabilidad/rotación de su clave requiere tratamiento explícito para no crear duplicados silenciosos.
- El cotejo declarado es una comprobación manual. No se presenta como acreditación estatal ni como una comprobación independiente por un tercero del titular SUPER_ADMIN.

## Actores y rutas acordadas

| Operación | Actor y autorización | Contrato |
|---|---|---|
| Estado propio | Sesión legítima; sin revelar documento/hash | GET `/api/v1/identity/status` → `{userId, identityVerified}` |
| Enrolamiento inicial SUPER_ADMIN | La propia sesión SUPER_ADMIN, reautenticación y declaración expresa; solo configuración legítima previa | POST `/api/v1/platform/identity/self-enrollment` con `currentPassword`, `ownerDeclaration:true`, `identityReview` |
| Revisión ADMIN existente | SUPER_ADMIN ya identificado; vista administrativa restringida | POST `/api/v1/platform/admins/{adminId}/identity-review` con `identityReview` y `reason` |
| Regularización CONDUCTOR existente | ADMIN identificado de la misma organización; revisión restringida | POST `/api/v1/drivers/{driverId}/identity-review` con `identityReview` y `reason`; conserva cuenta, Driver e historial sin consumir otra alta. |
| Alta/reemplazo ADMIN | SUPER_ADMIN identificado y reglas OP-ADMIN-01/02/cupo histórico | Las rutas existentes añaden `identityReview`; revisión y reserva de identidad en la misma transacción de creación. |
| Provisión CONDUCTOR | ADMIN identificado de la organización, expediente conforme y grupo activo | El CON-01 revisado aporta el DNI; reutiliza registro común antes de crear cuenta/Driver, sin activar conducción. |

`identityReview` contiene `documentType:"DNI"`, `documentNumber` de ocho dígitos, `method:"IN_PERSON"|"PRIOR_SECURE_CONTACT"`, `reference`, `originalReviewed:true` e `identityConfirmed:true`. El enrolamiento propio inicial usa `IN_PERSON` y declaración explícita. No se aceptan casillas falsas, omisión de cotejo o referencias vacías como verificación.

SUPER_ADMIN revisa identidad administrativa; este permiso no abre el expediente documental operativo de CONDUCTOR de una organización. ADMIN subordinado no inscribe ni cambia identidad del titular ni autoriza su recuperación.

La regularización CONDUCTOR no copia ni sustituye silenciosamente los campos documentales anteriores. Si el original revisado difiere, registra en auditoría solo `storedIdentityMatches:false`, sin valores personales, para conciliación documental. La primera regularización desactiva Driver y revoca sesiones; no otorga aprobación documental, grupo o unidad. Repetir la misma identidad ya registrada es idempotente y no vuelve a revocar; cambiar el DNI de un registro común existente exige un procedimiento de corrección separado. Una coincidencia con DNI de un Driver legado de otra cuenta impide dar de alta otra persona/rol; no autoriza adoptarlo por inferencia.

Los motivos admiten hasta 500 caracteres y las referencias hasta 160, ambos sin datos personales ni secretos. Las respuestas de comprobación solo contienen cuenta y estado booleano; no devuelven DNI ni hash.

## Cuentas existentes y autorización

No se infiere DNI a partir de correo, nombre o semillas. Los vínculos CONDUCTOR ya comprobados se incorporan de forma trazable; las cuentas sin revisión permanecen pendientes. El runtime normal deniega operación sin vínculo revisado, incluso ante JWT emitido antes del gate. Se mantienen únicamente rutas mínimas de sesión/configuración para que SUPER_ADMIN pueda regularizar su identidad y luego las de ADMIN, sin crear privilegios operativos por esa excepción.

La activación ADMIN requiere identidad revisada, contraseña propia, correo verificado y aceptación de términos vigentes. El enrolamiento no aprueba términos ni crea otro cupo. Recuperación, clave pública y código de emergencia SUPER_ADMIN conservan sus requisitos independientes.

`GET /identity/status` es excepción exacta también del gate de términos para que un ADMIN pendiente pueda consultar su situación mínima. No se exceptúan escrituras o rutas operativas. Los tickets de eventos operativos comprueban identidad tanto al emitirse como al consumirse.

En las regresiones históricas de perfil exclusivamente `test`, una propiedad explícita puede aislar el criterio de identidad de sus fixtures. Esas pruebas no acreditan ID-01. La suite dedicada lo activa y prueba las rutas reales con datos sintéticos. Fuera de ese perfil, apagar la propiedad no debe conceder operación sin identidad revisada.

## Consistencia y aceptación a verificar

V26 es la migración coordinada de este incremento; solo se aplica a `ruta_fija_test`, tras revisión. No se altera V21…V25 aplicada. La reserva de HMAC y el vínculo a cuenta tienen unicidad y bloqueo transaccional; una duplicación concurrente debe dejar una sola identidad/cuenta y no consumir un cupo por el intento revertido. Recuperar la misma cuenta no cambia persona ni ledger.

Pruebas dirigidas: enrolamiento propio legítimo y negativas; identidad desconocida bloquea rutas operativas; regularización ADMIN por SA y denegación a otros roles; DNI duplicado entre roles/empresas/correos secuencial y concurrente; rollback de alta y cupo; idempotencia sin sustituir documento de otra persona; recuperación de la misma cuenta; ausencia de DNI/digest en vistas ordinarias y auditoría. El resultado ejecutado se registra en la matriz y evidencia; esta definición no declara esas pruebas pasadas.

## Conservación y retorno

RET-02 no autoriza conservar todo bajo la etiqueta de anonimización. El procedimiento de retiro debe identificar campos/vínculos HMAC que permanezcan y su finalidad/plazo o retirarlos cuando corresponda, manteniendo cupos e IDs sin datos innecesarios. No hay purga real acreditada.

Ante un fallo del incremento, detener escrituras y preparar corrección revisada. No volver a un binario que opere cuentas sin revisión ni quitar el gate para simular aceptación. No ejecutar DROP, cambiar una migración aplicada o regenerar final3. [Matriz única](../evidencia/L-01/matriz-cierre-L01-revision-2026-10-14.md).
