# Reconciliación de ficha factual y repositorio — L-01

**Corte:** 2026-10-15, America/Lima  
**Fuente declarativa:** `C:\Users\Ale\Downloads\Ruta_Fija_Ficha_Factual_L01_2026-10-15.md`  
**Estado:** contrastada con respuestas posteriores del titular y código/documentación local. La ficha original no se modifica ni se considera evidencia de implementación.

## Hechos aclarados por el titular

| Tema | Aclaración vigente | Efecto sobre L-01 |
|---|---|---|
| Participantes | 25 personas reales: máximo 1 `SUPER_ADMIN`, 4 `ADMIN` y 20 `CONDUCTOR`; son topes de creación para el piloto. | La ficha queda reconciliada. La aplicación todavía debe aplicar y probar esos límites antes del uso del piloto. |
| Organizaciones | Cinco organizaciones ficticias; las referencias se definirán al momento de crear el piloto. | No crear organizaciones ni datos de piloto en este lote. La activación/admin por organización queda diferida hasta que el piloto tenga referencias. |
| Cronograma | La idea inició aproximadamente el 2026-08-20; 2026-10-26 sigue siendo una estimación. El evento de cierre será una semana después del despliegue. | No registrar fechas de aprobación ni aceptación retroactivas. No hay despliegue activo que inicie el periodo de cierre. |
| Ubicación e infraestructura | Equipo hospedador en Lima, Perú. No hay base `ruta_fija_piloto`, túnel HTTPS, copia cifrada ni restauración comprobada activos. | No realizar pruebas de piloto ni publicar servicios. La base automatizada continúa separada. La preparación de respaldo/recuperación es previa al despliegue, no evidencia de L-01. |
| Roles y módulos | `SUPER_ADMIN`: CRM, resumen global, organizaciones, cuentas ADMIN y auditoría de plataforma. `ADMIN`: CRM de organización, usuarios CONDUCTOR, grupos, conductores, vehículos, asignaciones, incidencias, comunicados, reportes, auditoría y mapa. `CONDUCTOR`: Android, incorporación, perfil, disponibilidad, asignaciones, incidencias, comunicados y mapa de servicio. | Mapas pertenecen a sus lotes futuros. El rol CONDUCTOR no recibe acceso CRM. Las invitaciones y solicitudes son parte de incorporación; aceptación de términos y recuperación son flujos de acceso. |
| Trabajo offline | Excluido del alcance vigente; no se habilita desde CRM/app. | El código y pruebas heredados se conservan como regresión, pero no cuentan como capacidad aceptada. Se verificará aparte su accesibilidad en ejecución. |
| Datos personales en interfaces | El titular confirmó ocultar globalmente todos los datos personales en CRM y app. | Requisito transversal pendiente de implementación y prueba; contradice campos actualmente visibles en perfiles, reportes, auditoría, invitaciones y operación. Debe conservarse la capacidad funcional mediante identificadores no personales. No registrar esto como ya cumplido. |
| Invitación Q-018 | El titular aprobó: CONDUCTOR con correo verificado acepta; la operación crea o vincula solicitud `PENDING_REVIEW`; consumo de código y creación/vínculo de solicitud son atómicos e idempotentes; no se habilita acceso operativo ni se aprueban documentos automáticamente. | Decisión vigente y aprobada; el repositorio actual aún solo valida códigos y no implementa esta aceptación transaccional. |

## Diferencias y pendientes que siguen abiertos

1. **SMTP:** la última comprobación local saneada encontró una definición de usuario y contraseña en `backend/.local/ruta-fija-native.env`, pero el usuario SMTP no coincide con el remitente permitido por `backend/src/main/resources/application-local.properties`. La allowlist coincide con la autorización ya dada y no se cambió. No había proceso backend ni listener en 8080. No hubo conexión ni envío externo. Corrección manual necesaria, si corresponde: solo `SPRING_MAIL_USERNAME` en `backend/.local/ruta-fija-native.env`; la prueba permanece detenida.
2. **Contacto de términos:** la ficha declara un buzón de soporte distinto del remitente autorizado para SMTP. No se asumirá que ambos sean el mismo contacto. El correo de soporte/incidentes/privacidad y la función de aprobación declarada como “Transporte” requieren confirmación antes de redactar candidato legal final.
3. **Privacidad frente a revisión documental:** ocultar todo dato personal en UI afecta el alta, la selección de destinatario, la recuperación y la revisión de expedientes. Se implementará ocultamiento/pseudonimización en la presentación, sin exponer valores en mensajes, títulos, confirmaciones ni accesibilidad; la revisión de documentos personales requiere definir un mecanismo de decisión sin mostrar el contenido al ADMIN. No se reemplazarán permisos ni se rebajará la verificación documental por inferencia.
4. **Conectividad de participantes:** la distribución de cuentas quedó confirmada; no se aclaró si las 25 personas usarán redes/ubicaciones distintas. Esa condición no cambia este incremento local, pero sigue pendiente para el ensayo operativo.
5. **Referencias de organizaciones:** quedan para el piloto. No sembrar nombres como “Empresa Monterrico S.A” ni asociar ADMIN/CONDUCTOR a organizaciones ficticias en la base actual.

## Contraste de implementación y comprobación nueva

- El repositorio conserva los roles vigentes `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`; el alta de usuarios ADMIN por SUPER_ADMIN y los límites globales 1/4/20 no están implementados.
- El cliente de recuperación asistida CONDUCTOR existe en CRM y Flutter y usa dobles en pruebas de cliente; la integración de autenticación con el flujo de extremo a extremo no se volvió a ejecutar en este corte.
- Se añadió un gate backend transversal para las rutas protegidas `/api/v1/**`: ADMIN sin aceptación de la versión vigente queda denegado; se exceptúan el contexto mínimo de sesión y las rutas de consulta/aceptación de términos. El gate compara actor, organización, documento actual, versión y SHA-256. Los documentos sintéticos se permiten solo en perfil `test`.
- La aceptación final de términos sigue bloqueada: el borrador v0.2.0 no está aprobado y ninguna prueba sintética registra aceptación real.
- El código actual de invitaciones conserva emisión, consulta, revocación, validación y límites independientes; falta consumo con la solicitud de incorporación definida por Q-018.
- Verificación ejecutada tras el cambio del gate: `backend: .\\mvnw.cmd --batch-mode --no-transfer-progress verify`, con el importador oficial `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Test`. Resultado final: BUILD SUCCESS, 46 pruebas unitarias y 64 de integración; `ruta_fija_test` en V17, sin migración nueva. Incluye 2 pruebas nuevas del gate con documento sintético. La primera ejecución reveló dos defectos de prueba/estado HTTP, se corrigieron y la segunda regresión completa pasó.
- La regresión aún incluye `MobileOfflineSyncIT` (3 pruebas) como protección heredada. No acredita funcionalidad offline ni su accesibilidad en runtime.

## Estado del lote

L-01 continúa `EN_CURSO`. No se inició L-02. Siguen pendientes la coincidencia de SMTP y entrega real autorizada, límites piloto, alta/activación/reemplazo ADMIN, aceptación Q-018, revisión de aislamiento, aplicación transversal de ocultamiento de PII, términos aprobados, ensayo del titular de recuperación SUPER_ADMIN y demás recorridos operativos. La prueba física corresponde a L-08.
## Aclaración posterior del titular y corte Q-018 — 2026-10-15

La discrepancia anterior de privacidad se resuelve así: los datos personales no se mostrarán en CRM/app, salvo al ADMIN autorizado dentro de una vista restringida de expediente. La distribución de creación del piloto queda fijada en 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR; consumidos los 4 cupos ADMIN, no se permite reemplazo. El usuario aprobó identidad preliminar separada, contraseña establecida durante el alta sin acceso operativo previo a la aprobación y elección de grupo activo por ADMIN al aprobar.

Implementación parcial de Q-018: identidad preliminar, OTP, consumo atómico/idempotente de invitación y solicitud PENDING_REVIEW; expediente restringido por ADMIN/organización y auditoría. No hay todavía catálogo documental, aprobación/rechazo, cuenta/Driver post-aprobación ni UI CRM/app. Ver [corte técnico Q-018](corte-onboarding-Q018-2026-10-15.md).

Comprobación ejecutada: test-compile y verify dirigido, 46 unitarias + 4 integraciones dirigidas; cero fallos. ruta_fija_test V19. SMTP simulado sin conexión externa. L-01 EN_CURSO; final3 vigente; L-02 sin iniciar.

## Revisión factual vigente — Chiclayo, OP-ADMIN, RET-02 e ID-01

Este apartado actualiza el estado sin modificar el archivo original del titular ni reemplazar la evidencia de los cortes anteriores.

- **Ámbito:** taxi/colectivo en provincia de Chiclayo, Lambayeque, Perú. M1 documentado por modalidad/título; colectivo sedán/station wagon. Filas/ruedas no deducen categoría. El PC sigue declarado por separado en Lima; no se infiere traslado de la infraestructura por cambiar la provincia del piloto.
- **Identidad/incorporación:** DC-01A mantiene identidad preliminar separada, sin AppUser/Driver ni sesión operativa. Invitación verificada crea PENDING_REVIEW. La aprobación/rechazo personal pertenece a L-01; no se traslada. Catálogo DD-07…15 aprobado con POR_CONTRASTAR bloqueante; datos estructurados y cotejo visual sin copias. ADMIN elige grupo activo después de aprobar; Driver nuevo no queda habilitado para conducir.
- **Cupos:** DC-02B son creaciones históricas globales 1/4/20. Desactivar o borrar no devuelve cupo; al agotar cuatro ADMIN se bloquea un nuevo sustituto. El ledger y sus pruebas se implementaron en V22/V23; no reconstruye altas borradas anteriores sin evidencia.
- **Alta/reemplazo ADMIN:** OP-ADMIN-01/02 aprobados: credencial de un uso/24 h, contraseña propia, OTP y términos antes de habilitar; solicitud independiente comprobada y revocación atómica/preventiva. No se impone un ADMIN único por organización.
- **Contacto:** función declarada del responsable/aprobador: titular, desarrollador y responsable técnico del proyecto Ruta Fija. Canal del proyecto rutafija.dev1@gmail.com, revisado cada dos o tres días sin disponibilidad permanente. Contacto operativo CONDUCTOR–ADMIN por el teléfono organizacional que ADMIN registre en incidencias, aún sin valor proporcionado. La excepción de contacto no abre un directorio personal.
- **SMTP:** el usuario privado vigente ya coincidía; la nueva discrepancia estaba en el remitente permitido antiguo. Corregido al remitente exacto autorizado, una entrega real del adaptador fue aceptada y el destinatario confirmó entrada. No se ejercitó consumo HTTP de ese OTP; no se repitió automáticamente.
- **Conservación:** el prompt maestro ya declara reset CONDUCTOR 30 min/un uso, evidencia mínima recuperación 30 días desde terminar, aceptaciones 90 días del fin y auditoría operativa 6 meses separada. DD-14 atribuye al ADMIN el expediente a treinta días. RET-02 aprobado confirma deshabilitar ADMIN/CONDUCTOR al cierre y anonimizar sus datos personales a treinta días conservando IDs/historial/cupos; no se borra SUPER_ADMIN/custodia por inferencia. Ninguna declaración acredita purga, copia cifrada ni restauración. Conservar HMAC vinculable no prueba anonimización irreversible.
- **ID-01:** aprobado el registro común de persona para tres roles, huella HMAC DNI y revisión manual restringida. La implementación V26 está en curso; no queda verificada por las pruebas V25. El alias provisional PERSON-01 se refiere a la misma decisión, no a la identificación del responsable legal.
- **Términos:** el borrador 0.3.0 incorpora hechos confirmados y conserva 0.2.0. Ninguno está aprobado, vigente o aceptado realmente. Faltan el texto final completo identificado y la aprobación expresa del titular.

Evidencia de código, pruebas y límites en [incremento Chiclayo](incremento-Chiclayo-2026-10-15.md) y [matriz única](matriz-cierre-L01-revision-2026-10-14.md). V25 pasó regresión completa 49 unitarias/141 IT y suplementaria 52 unitarias/15 IT; CRM anterior al nuevo ID-01 108 pruebas, Flutter 74. Los nuevos cambios ID-01 se validan aparte. Originales y snapshot final3 conservados; desarrollo/piloto no migrados; L-02 no iniciado.

### Conciliación posterior V26

ID-01 ya implementado y probado en sus casos con gate habilitado: 10 IT dedicadas y 1 con términos+identidad. Regresión completa 56 unitarias/155 IT; posterior documental 56/21, CRM 121/typecheck/build; Flutter 74/analyze sin cambio posterior. No acredita inscripción del titular, UI conectada ni datos reales revisados.

Elección de información final: función/canal técnico en reglas de uso; privacidad/identificación por separado antes del piloto con datos reales. Candidato ADMIN 1.0.0 y ficha preparados, sin campos incompletos ni aprobación. El titular pidió texto completo y ambas huellas antes de decidir; se conservan bytes bajo revisión y borradores anteriores. Una observación independiente precisa que la separación persona/grupo/unidad requiere mayor explicitud en el candidato si se modifica, con nueva huella.

Entrega SMTP anterior continúa confirmada; nuevo validador HTTP persistido solo probado con correo doble, sin envío real adicional. Retención/purgas y copias no ejecutadas; RET-02 no debe volver a preguntarse ni confundirse con cumplimiento realizado. La matriz V26 mantiene L-01 EN_CURSO, final3 vigente y L-02 no iniciada.

### Precisiones posteriores confirmadas y hechos actuales

T-03: cinco referencias/al menos una sin operar por cuatro ADMIN históricos. T-04: retirar también HMAC/vínculos ADMIN/CONDUCTOR a30 días del fin con IDs/historial mínimo/cupos y excepciones con finalidad/vencimiento, sin inferir retiro SUPER_ADMIN. T-05: ADMIN identifica/solicita antes de baja y técnico ejecuta restringido/auditado. T-06: inicio efectivo al habilitar y fin/cierre mismo acto siete días después. No equivalen a hechos futuros ocurridos ni aprobación del texto.

Candidato corregido1.0.0 SHA20ffd952… y ficha preparados; separación persona/grupo/unidad ya explícita. Copias previas en histórico. Aviso separado de privacidad incompleto/no aprobado. Custodia declarada por titular: solo PC Windows; medio independiente sin confirmar, clave/inscripción/copia/código pendientes.

Recorridos de interfaces conectadas ahora pasan con datos sintéticos y mail doble: build actual y57 unitarias/6 IT dirigidas; analyze limpio. No son dispositivo físico ni ensayo del titular. Consumo OTP real pendiente de entrada local solicitada; no inferir otra discrepancia SMTP ni repetir envío. Matriz/evidencia canónicas actualizadas. L-01 EN_CURSO; final3 vigente; L-02 no iniciada.
