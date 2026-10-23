# SC-L01-LOCAL-01 — Adenda 1.1.0: complemento automatizado del gate

Fecha de registro: 2026-10-17. **APROBADA por mensaje directo del titular; aplicación condicionada a las evidencias, sin nueva aprobación equivalente.**

## Antecedentes conservados

La [adenda 1.0.0](SC-L01-LOCAL-01-adenda-v1.0.0.md), el contrato anterior, la propuesta y la aprobación condicionada permanecen como antecedentes. La aceptación manual existente, el intento de gate posterior y la recuperación ya acreditada no se borran ni alteran.

## Precisión de SC-03 autorizada

La aceptación humana del ADMIN sintético se conserva como evidencia manual. La comprobación negativa y positiva del gate se admite mediante una ejecución **automatizada, separada y trazable**, contra backend HTTP y PostgreSQL reales de pruebas y correo simulado. No se denomina ensayo humano ni se atribuye al titular la aceptación automatizada de otra cuenta.

Para completar REC-CI03-01 se autoriza como máximo un ADMIN sintético adicional, previa comprobación del ledger, conservando SUPER_ADMIN, organización y RUN_ID cuando lo permita el arnés. Consume un alta histórica, no recuperable. Alta y activación usan mecanismos normales y una identidad exclusivamente ficticia. Antes de aceptar debe demostrarse rechazo específico por términos pendientes; después de aceptar el documento publicado se repite **la misma operación protegida** y se comprueba su autorización. Un 401, una denegación de otro permiso o un bloqueo visual no acreditan el gate.

La operación seleccionada es `POST /api/v1/admin-activation/complete`, protegida por la capacidad de activación válida, contraseña, identidad y correo ya comprobados: `409 RESOURCE_CONFLICT` por ausencia de aceptación; aceptación automatizada expresa de ADMIN 1.0.0; misma operación `200 COMPLETED`. Login ordinario y consulta de grupos corroboran habilitación. No se otorga una sesión operativa previa para fabricar la comprobación.

El resultado compuesto de CI03 se identifica como `PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE`. El archivo manual original conserva su resultado histórico `PENDING` por gate omitido; no se convierte en `PASS_MANUAL_SYNTHETIC_ONLY`. La evidencia adicional identifica origen `AUTOMATED_SYNTHETIC_ONLY` y ambas cuentas separadas.

SC-07 mantiene íntegramente su aprobación anterior y `PASS_LOCAL_TEMPORARY_COPY_ONLY`; no se repite la recuperación.

## Condiciones de aplicación y cierre

1. Contrastar huellas, IDs, persistencia, orden temporal y resultados del servidor. No inventar éxito ni forzar estados por SQL.
2. Archivar antes de liberar la protección. Usar la salida prevista del arnés, sin limpiezas globales ni borrado de aceptaciones/ledger; comprobar protección liberada y contadores conservados.
3. Contrastar todos los demás criterios locales sin reducirlos. Solo entonces emitir acta **FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**.
4. Recuperar el mismo caso ante fallo, sin crear cuentas sucesivas ni reintentar correo externo.

## Requisitos originales que siguen pendientes antes del piloto real

- Aceptación propia y expresa de un ADMIN legítimo; el titular real seguirá siendo SUPER_ADMIN, sin doble rol.
- Custodia independiente del titular real, medios realmente disponibles, copia comprobada, código separado, preparación, inscripción previa y ensayo personal. Dos archivos en una PC no acreditan independencia.
- Identificación/privacidad, condiciones de protección y conservación, preparación de participantes/recursos y requisitos documentales desconocidos o por contrastar.
- Infraestructura piloto, HTTPS, base autorizada, respaldos cifrados y restauración comprobada, con sus autorizaciones separadas.

Se conservan términos ADMIN 1.0.0 y su huella, controles de seguridad, ID-01, cupos 1/4/20, aislamiento y demás criterios. CI01 y CI07 no se repiten. final3 permanece intacto. Sin Git/GitHub, despliegue, SMTP adicional, cuentas reales, claves definitivas o migraciones de desarrollo/piloto. **L-02 permanece sin iniciar y requiere su OK VERDE separado.**
