# L-01 — Preparación verificable de los ensayos humanos CI03/CI07

Fecha: 2026-10-17, America/Lima. **L-01 EN_CURSO; L-02 NO INICIADA. Ensayos humanos pendientes.**

## Autorización y contrato contrastados

El titular autoriza operar personalmente ADMIN y SUPER_ADMIN sintéticos en test, establecer contraseñas, aceptar los términos y usar claves temporales con correo simulado. Su identidad real continúa prevista como SUPER_ADMIN. No representa a otra persona ADMIN legítima ni autoriza dos roles activos reales. No se generan ni inscriben claves definitivas del titular.

Leídos: contrato L-01 y últimas adendas, OP-ADMIN-01/02, ID-01, matriz vigente, protocolo/cliente Ed25519 y runner conectado existente. El alcance vigente exige todavía **aceptación operativa de ADMIN legítimo** y **custodia independiente/ensayo legítimos**. Ninguno se reemplaza por estos ensayos. La [propuesta SC-L01-LOCAL-01](../../contratos/L-01-propuesta-validacion-funcional-local.md) queda como borrador para revisar después de los resultados manuales; no está aprobada.

## Entorno y cupos antes de cualquier escritura del caso

Consulta JDBC solo lectura usando la configuración privada existente: **ruta_fija_test / rf_test / V26** a **2026-10-17T06:15:43.590935600Z**. No Spring/Flyway/SMTP en esa inspección.

- Ledger: SUPER_ADMIN **0/1**, ADMIN **1/4**, CONDUCTOR **0/20**. No hay SUPER_ADMIN, vínculo ID-01 o credencial de recuperación inscritos.
- **Cero ADMIN sintéticos pendientes elegibles.** El ADMIN del caso OTP verificado se excluye del ensayo; no se reactiva ni se modifica. Nueva preparación necesaria: un SUPER_ADMIN sintético y un ADMIN normal pendiente; triggers del ledger activos. El mismo caso será reutilizable sin volver a crear cuentas; no borrar para recuperar cupo.
- Documento actual **f590e228-5d2e-47e4-b144-b0df1198bd49**, ADMIN_TERMS **1.0.0**, `synthetic=false`, SHA **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**, coincidente con archivo aprobado. Aceptaciones **0**.
- Estado de preparación: todavía **sin fixture humano creado, sin clave temporal generada, sin aceptación y sin marcador activo**. El servidor manual no queda iniciado por esta preparación; lo iniciará el titular para introducir su contraseña protegida.

Inspección durable: [log](ensayos-humanos-preparacion-2026-10-17/inspeccion-solo-lectura.log), SHA **0F5C9583EEFCADF0A4CE2D70E1B3760C249E4F20C672C7B2A3F8EB59D166D044**.

## Incremento implementado

`scripts/Invoke-RutaFijaL01HumanTrial.ps1` ofrece inspección por defecto, preparación sin tests/BD, comprobación de arranque y modo manual interactivo. Clases de soporte únicamente en `src/test`; no modifica API, validaciones, gates o protocolo del runtime normal.

- Frontend local **127.0.0.1:4311** sirve el CRM compilado y reenvía a backend **127.0.0.1:8091**. Solo loopback/origen esperado. El CLI debe apuntar a4311 para que se observen sus respuestas normales.
- Perfil test con ID-01 y términos **activos**, offline y seed deshabilitados, JPA validate y Flyway apagado. Adaptador SMTP sustituido, autoconfiguración JavaMail excluida y valores SMTP vacíos. Correo exclusivamente en memoria para direcciones propias `@rutafija.test`.
- Bootstrap autorizado de prueba: SUPER_ADMIN sintético activo/correo precomprobado y organización sintética; sin ADMIN ni aceptación automáticos. La identidad se representa manualmente por el CRM. Clave HMAC/JWT temporal del arnés en archivo privado con ACL y estable para reanudar el mismo caso; no es la clave Ed25519 de recuperación.
- Alta ADMIN, credencial, contraseña propia, OTP, aceptación y habilitación por las rutas ordinarias. `gate` comprobará409 antes de aceptar y401 a una operación sin sesión. La activación/aceptación no son ejecutadas por el agente.
- Registro saneado observa solo referencias/flags. Consulta la aceptación persistida con cuenta/organización/documento/versión/huella/hora, orden de etapas, ID-01 y auditoría. Exige interacción/confirmación humana y login posterior para declarar el resultado **sintético** satisfactorio.
- En recuperación observa pública conservada, código anterior no coincidente/código rotado vigente, presentación de respuestas una vez, reto consumido/auditoría, versión de sesión y rechazo HTTP de las sesiones de prueba observadas. La generación y uso de archivos privados son personales mediante el JAR vigente, no automatizados por el arnés.
- Lock asesor existente más marcador persistente. `NativePostgresTestDatabase.cleanBusinessData` rechaza limpieza antes y después de adquirir su lock si el ensayo humano sigue marcado. `salir` libera solo después de archivar ambos resultados técnicos satisfactorios; ante interrupción/pendientes conserva el marcador, sin borrar filas ni ledger. Esta guarda solo afecta las facilidades de pruebas.
- Una falla de observación no sustituye ni suprime la respuesta real del backend, especialmente un código presentado una vez. Marca evidencia incompleta y evita un PASS; no fuerza un reintento de la operación.

Guía completa y acciones exactas: [L-01-ensayos-humanos-sinteticos.md](../../operacion/L-01-ensayos-humanos-sinteticos.md).

## Comprobaciones nuevas ejecutadas y límites

1. **10 unitarias PASS**, sin fallos/errores/omitidas: configuración de gates/mail/loopback, archivo privado sin sobrescritura, fuerza/diferencia de secreto efímero, huella aprobada, bloqueo por marcador y predicados que rechazan resultados incompletos o sin acción humana. [Log](ensayos-humanos-preparacion-2026-10-17/unitarias.log), SHA **E84D31B26A3FE6739063FBA991365F22DAAAD4D0DBA85865CE92840511700B24**.
2. **Arranque real PASS** del backend de prueba y proxy: GET `/auth/me` y `/groups` sin sesión401, configuración test del CRM disponible, correo sustituido. Mismos ledger, vínculos, documento y aceptaciones antes/después; sin fixture, migración o limpieza. [Log](ensayos-humanos-preparacion-2026-10-17/arranque-real.log), SHA **B18106A544F6D985A14D4936C277A6138E71EF3B9714ADEEFE6F5153AFF30188**.
3. Guarda de modo no interactivo PASS: `-Serve` rechazado sin caso, cuenta ni marcador creados. AST PowerShell del lanzador y **cuatro** bloques ejecutables de la guía válido, sin ejecutar esos pasos personales.
4. Manifiesto histórico de cierre parcial: **todos sus archivos conservan huellas**, incluyendo documentos/logs y código fijados entonces. JAR CLI vigente **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A**, intacto. final3 ZIP **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**, intacto.

No son una regresión completa ni una repetición de CI01. Las pruebas previas aprobadas de activación, términos, identidad, cuotas, CLI/recuperación con Tomcat/PG y clientes conectados se reutilizan con sus límites. El nuevo lanzador **todavía no acredita** su alta/aceptación humana, gate409 de ese caso o recuperación humana con copia. Esas comprobaciones se ejecutarán durante el recorrido personal, sin rebajar los criterios por número de tests.

## Resultados y acciones pendientes

| Elemento | Estado al preparar |
|---|---|
| Ensayo manual ADMIN sintético | **PENDIENTE INTERVENCIÓN PERSONAL** |
| Aceptación de ADMIN legítimo | **PENDIENTE**; no existe participante preparado |
| Ensayo manual recuperación temporal | **PENDIENTE INTERVENCIÓN PERSONAL** |
| Custodia independiente real | **PENDIENTE**; solo PC Windows confirmada |
| SC-03/SC-07 cambio a cierre funcional local | **PROPUESTA NO APROBADA**, completar evidencia manual antes de pedir revisión |

El operador ejecutará el modo manual una vez, seguirá la guía y comunicará solo RUN_ID/resultados/ruta/huella/liberación. Después se contrastarán los registros persistidos y archivos saneados locales; no se declara éxito por promesa de ejecutar. Las limpiezas están permitidas antes de iniciar el ensayo, y se bloquean automáticamente al arrancar `-Serve`; no reanudarlas durante él ni después de un fallo sin revisar y archivar el caso.

Sin envíos externos, pruebas físicas, cuentas reales, Git/GitHub, regeneración final3, despliegue o migración. L-01 EN_CURSO; L-02 sin iniciar.
