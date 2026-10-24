# Contrato documental L-01 — Chiclayo — v0.1.0 propuesta

Fecha: 2026-10-15, America/Lima. Estado: **PROPUESTA PARA CONFIRMACIÓN, CONTRASTE EXTERNO PARCIAL**. L-01 EN_CURSO. No es una versión legal aprobada, no publica términos y no habilita documentos reales ni operación.

## Decisiones confirmadas y precedencia

El mensaje directo del titular del 2026-10-15 confirma taxi y colectivo; provincia de Chiclayo, Lambayeque, Perú; sedanes y vehículos de tres filas como intención física, con clasificación documental; aislamiento por organización; aprobación/rechazo por ADMIN dentro de L-01 y ausencia de acceso operativo por OTP/invitación. La provincia del piloto no cambia la ubicación física declarada del PC en Lima. Las cinco organizaciones previstas siguen siendo referencias ficticias hasta su configuración; no tienen autorizaciones externas acreditadas.

El [adjunto conservado](../evidencia/L-01/adjunto-decisiones-documentales-Chiclayo-2026-10-15.md) aporta propuestas DD-07…DD-15. Adjuntarlo no las aprueba. Este contrato concreta esas propuestas y contrasta fuentes oficiales. Un requisito desconocido permanece POR_CONTRASTAR; no es conforme ni no-aplica por defecto.

**DC-01A ya aprobado:** el titular respondió «Sí, identidad separada» y después aprobó contraseña preliminar sin login operativo, elección de grupo tras aprobación y etapa PENDING_REVIEW. Consta en [reconciliación factual](../evidencia/L-01/reconciliacion-ficha-factual-2026-10-15.md) y [corte Q-018](../evidencia/L-01/corte-onboarding-Q018-2026-10-15.md). El modelo anterior de cuenta CONDUCTOR autenticada antes de solicitar no prevalece sobre esa aclaración.

**DC-02B ya aprobado:** el titular confirmó límites de creaciones históricas totales del piloto 1 SUPER_ADMIN / 4 ADMIN / 20 CONDUCTOR y respondió «Bloquear reemplazo al agotar las 4 creaciones». No se sustituye por cupos simultáneos. Una cuenta creada consume la creación aunque esté pendiente, deshabilitada o luego se elimine; recuperar la misma cuenta con su ID no crea otra. Una identidad preliminar sin AppUser ni rol no es todavía un alta CONDUCTOR. El enforcement persistente/concurrente sigue pendiente; la aprobación del criterio no es evidencia de implementación.

## DD-07 — Admisión propuesta pendiente

Admitir inicialmente M1 documentado y título externo compatible. Para colectivo M1, comprobar también carrocería sedán o station wagon conforme al DS 003-2022-MTC, arts. 4.1/7.4. Tres filas no determina categoría ni acredita compatibilidad del servicio. En taxi, comprobar M1 y condiciones del título local concreto. M2 u otras categorías quedan sin habilitación hasta una ampliación expresa y contraste de su variante. No se rechaza definitivamente una unidad ambigua ni se la reclasifica para hacerla pasar.

Registrar por separado categoría documentada, carrocería, asientos/capacidad, año modelo, placa y filas cuando delimiten el piloto. El RNV oficial consultado define M1 por hasta ocho asientos de pasajeros sin conductor, pero su copia identificada de 2016 no acredita consolidación integral a 2026.

## DD-08…DD-12 — Catálogo propuesto

Cada organización conserva expedientes propios; la persona tiene identidad estable sin dos pertenencias o roles activos. No se copian expedientes personales entre organizaciones. La tabla distingue obligatoriedad propuesta para Ruta Fija y evidencia externa leída; la aplicabilidad concreta depende del servicio y título real.

| ID | Entidad / requisito | Obligatoriedad y aplicabilidad propuesta | Campos mínimos y vigencia | Método, acceso y estado externo |
|---|---|---|---|---|
| CON-01 | Persona: documento de identidad | Obligatorio para identificar al postulante; DNI como base propuesta del piloto. Otro documento necesita regla explícita. | Tipo, número, nombres; emisor y vencimiento cuando existan. Distinguir sin vencimiento de desconocido. | ADMIN coteja visualmente la misma persona. PII solo en expediente restringido. Lista de documentos admisibles aún por aprobar. |
| CON-02 | Persona: licencia | Obligatoria para conducción; compatible con categoría, servicio y restricciones. | Número, clase/categoría, emisor, restricciones relevantes, vencimiento real, fecha y resultado de revisión. | MTC clasificación art. 9: taxi M1 A-IIa y superiores que incluyan esa facultad; DS003 art.8.1 colectivo M1 A-IIa, M2 A-IIb y superiores compatibles. A-I no basta por ser automóvil. Tabla explícita, nunca comparación textual de categorías. No imponer duración universal de licencia. |
| CON-03 | Persona: certificado de habilitación conductor | Exigible para la variante local que lo requiera; fuente local identificada, alcance del título real por revisar. | Número, autoridad, empresa/servicio/ámbito, fecha de emisión y fin/renovación comprobados. | TUPA Chiclayo 2026 SE1418AE76 identifica habilitación/renovación anual; DS003 art.18 para colectivo. No calcular un vencimiento ficticio ni equiparar aprobación ADMIN con habilitación estatal. |
| CON-04 | Persona: requisitos adicionales aplicables | Solo con fuente/condición concreta; desconocido bloquea la variante, no se omite. | Requisito, fundamento, resultado y referencia mínima. | TUPA exige capacitación/otros antecedentes para expedir habilitación; no implica que Ruta Fija deba recopilar fotos, declaraciones o copias. Aplicabilidad del art.10.12 DS003 y condiciones locales pendientes de consolidar. |
| CON-05 | Persona: condiciones personales de colectivo | Condicional a la aplicación del DS003 art.8.2/8.3 al participante. | Resultado de comprobación de edad/aptitud y fecha de control pertinente; sin diagnósticos ni historia médica. | El texto leído incluye límite de edad y controles desde 65 años. Evidencia mínima y aplicabilidad aún POR_CONTRASTAR; licencia existente no prueba automáticamente esta condición. |
| VEH-01 | Vehículo: TIV / identificación admitida | Base obligatoria por unidad. | Referencia, placa, categoría, carrocería, marca/modelo, año, asientos y filas si corresponde. | ADMIN coteja unidad/documento. No renovación anual inventada. Expediente de flota separado del conductor. |
| VEH-02 | Vehículo: SOAT o CAT aplicable | Obligatorio antes de operar; CAT condicionado a emisor habilitado y circunscripción autorizada. | Tipo, emisor, referencia, placa, uso/cobertura, inicio/fin y ámbito. | TUPA local admite SOAT/CAT SBS; DS004-2024-MTC modifica RNAT arts.3.16/28. Verificar cobertura y aplicación actual, no aceptar por nombre. |
| VEH-03 | Vehículo: CITV y complemento exigible | Según modalidad/categoría/antigüedad documentadas; desconocido no conforme. | Número, centro emisor, placa, tipo/complemento, resultado, fechas reales, fundamento de aplicabilidad. | DS003 exige complemento colectivo y detalla cómputo/frecuencia; TUPA taxi distingue antigüedad de la unidad. No convertir antigüedad del vehículo en validez del certificado. |
| VEH-04 | Vehículo: TUC u otro título real | Obligatorio cuando la variante/título lo requiere. | Denominación real, número, autoridad, titular, unidad, modalidad, ámbito/ruta, emisión/fin. | TUPA 2026 PA14182B14 y PA1418E325 identifican TUC; comprobar título real y anexos. Una resolución individual no fija plazos universales. |
| VEH-05 | Vehículo: vínculo interno de uso | Necesario antes de oferta/conducción; independiente de aprobación personal. | Vehículo, conductor, organización, tipo propio/empresarial, inicio/fin, ADMIN y resultado. | Regla interna v3.1; no sustituye permiso municipal. No duplicar propiedad de la flota dentro de cada expediente personal. Su operación se integra en el lote correspondiente. |
| EMP-01 | Empresa: identidad y autorización | Referencia sintética para organización académica; título real acreditado antes de operación real. | Referencia empresarial, autoridad/título, modalidad, ámbito, fechas y titular/vínculo comprobado. | Crear organización no acredita autorización externa. Sin título real no se marca validación legal. |
| EMP-02 | Empresa: taxi | Condiciones de la variante provincial concreta. | Modalidad, título, unidad/flota habilitada, ámbito y fechas. | TUPA PA14182B14 cubre renovación; no prueba por sí solo acceso inicial de todas las variantes. OM011-2010/anexos y modificatorias no están consolidados íntegramente. |
| EMP-03 | Empresa: colectivo/ruta | Autorización y recorrido real cuando corresponda. | Autoridad/título, código de ruta, ámbito, recorrido/paradas y unidades habilitadas. | TUPA PA14182194 identifica M1/M2 y estudio de ruta. No basta origen/destino interno. Salir de Chiclayo exige decisión de alcance adicional. Q-040 taxi directo queda como dependencia de su lote, sin implementarlo en L-01. |

## Revisión, vigencia, excepciones y separación de estados

Propuesta DD-12: cada requisito guarda ID, entidad propietaria, catálogo/versión, condición de aplicabilidad, fuente, campos mínimos, tiempo conocido/desconocido/no vencimiento, resultado, ADMIN/organización/hora del servidor, método, referencia de comprobación, observación y versión sustituida. Resultados propuestos: PENDIENTE, CONFORME, OBSERVADO, NO_APLICA_JUSTIFICADO y POR_CONTRASTAR. No son enums desplegados ni aprobación de esta propuesta.

NO_APLICA exige una condición verificable y motivo; no omite obligaciones externas. La excepción interna no dispensa una obligación legal. Datos contradictorios/fechas desconocidas mantienen revisión. Vencer licencia u otro requisito operativo bloquea la habilitación, sin borrar aprobación personal histórica. Los requisitos deben cubrir el periodo del servicio en los controles correspondientes.

Propuesta DD-15: ADMIN de la misma organización revisa, observa/devuelve, aprueba o rechaza con motivo; devolución incrementa una ronda aunque observe varios campos, y reenvío no añade ronda. Q-021 conserva 48 horas desde observaciones disponibles en app, ampliación ADMIN motivada, marca fuera de plazo sin rechazo automático. Q-020 conserva rechazo expreso por reiteración desde cuatro rondas y espera de siete días desde rechazo; nueva invitación, misma identidad/historial y una solicitud pendiente por empresa. No hay aprobación automática por correo, invitación, grupo o unidad.

La aprobación personal es distinta de grupo, vehículo revisado, vínculo y elegibilidad operativa. ADMIN selecciona grupo activo en la aprobación; si falta grupo, la solicitud puede estar aprobada sin acceso operativo hasta asignarlo. Falta de unidad no convierte al conductor en rechazado; no permite conducción/ofertas sin unidad revisada y vinculada. Implementación completa de estas mutaciones pendiente de aprobar el catálogo/evidencia; las cargas reales permanecen inhabilitadas.

## DC-01 — Consulta y subsanación sin sesión operativa

La identidad preliminar verifica correo una vez durante Q-018. Reingresa con correo y contraseña preliminar a un acceso exclusivo de incorporación, no con OTP en cada consulta. El token opaco tiene propósito separado, hash SHA-256 en servidor, TTL absoluto de 30 minutos, revocación por salida y ningún rol, JWT, refresh o cookie operativa. Ni ID de solicitud ni idempotency key son credenciales.

Contrato implementado para consulta: POST `/onboarding/access/login`, GET `/onboarding/applicant/requests` con `X-Onboarding-Token`, POST `/onboarding/access/logout`. Solo devuelve estados/referencias propias sin PII; no crea AppUser/Driver. La subsanación futura usará la misma autorización limitada y versiones de observaciones/documentos, sin acceso a flota, servicio, ubicación o CRM. El acceso no implica que subsanación o aprobación estén implementadas.

Al aprobar se creará/vinculará la identidad existente de forma transaccional, sin segunda persona/cuenta ni dos empresas/roles activos. El modelo actual aún no acredita ese vínculo ni el traslado Q-105; no se afirma duplicación resuelta solo por correo único.

## DD-13 / DD-14A-B — Alternativa concreta para aprobar

**Opción recomendada DD-13A:** datos estructurados y revisión visual controlada por ADMIN, sin fotografías/escaneos/PDF por defecto; no solicitar copias permanentes por chat/correo. Registrar únicamente datos necesarios, método, referencia mínima, resultado, revisor, versión y hora. ADMIN solo accede a PII en expediente restringido de su organización, con auditoría; listas, perfiles y exports usan referencias no personales. Las superficies heredadas aún requieren completar esta política.

**DD-14A propuesto:** deshabilitar al cierre del piloto y eliminar datos estructurados/revisiones a los 30 días de terminar, salvo excepción concreta, motivada y aplicable. Registrar ejecución/fallos y tratar copias antes de restaurar. Esto no es un plazo legal universal ni borra automáticamente auditoría/aceptaciones con otra finalidad. Responsable y procedimiento operativo deben concretarse antes de recoger datos reales.

**DD-14B con DD-13A:** cero copias digitales recolectadas; no hay carga ni almacenamiento de archivos. Si una obligación real requiere copia, mantener ese caso bloqueado hasta aprobar finalidad, piezas exactas, almacenamiento privado fuera de repo/directorio público, acceso API por tenant, formatos/tamaños/contenido, protección tránsito/reposo, retención/eliminación y tratamiento de respaldos.

**Alternativa DD-13B:** conservar copias solo tras completar y aprobar todas esas condiciones por categoría. Elegir esta alternativa no habilita cargas inmediatamente. No se almacenan originales físicos en la plataforma.

Los plazos separados del adjunto (términos 90 días tras piloto, recuperación 30 días tras trámite) deben contrastarse con la ficha y su implementación; auditoría operativa seis meses y detalle GPS siete días conservan sus propias reglas. Actualizar ficha no aprueba términos v0.2.0 ni publica 1.0.0.

## Fuentes oficiales examinadas y límites

| Fuente | Ubicación examinada / resultado | Límite del contraste |
|---|---|---|
| [TUPA Chiclayo vigente 2026](https://www.gob.pe/institucion/munichiclayo/informes-publicaciones/7664214-texto-unico-de-procedimientos-administrativos-tupa-vigente-2026), [PDF oficial](https://cdn.www.gob.pe/uploads/document/file/9348775/7664214-tupa-2026.pdf?v=1769540682) | Páginas impresas 475 PA14182194, 477 PA1418E325, 485 PA14182B14, 620 SE1418AE76. Modificatoria OM000030-2025-MPCH/A. | Requisitos de trámite municipal; no implican recolectar cada copia en Ruta Fija ni acreditan títulos reales de participantes. Se leyó PDF oficial mediante extracción local ante HTTP418 del navegador. |
| [DS003-2022-MTC oficial](https://cdn.www.gob.pe/uploads/document/file/2997184/DS%20003-2022-MTC.pdf.pdf?v=1649254672) | Arts.4.1/7.4 carrocería; art.8 licencia/condiciones; art.18 habilitación; disposición modificatoria CITV. | Aplicabilidad y modificaciones deben comprobarse para título/servicio concreto; no catálogo legal consolidado integral. |
| [MTC clasificación de licencias](https://portal.mtc.gob.pe/transportes/terrestre/licencias/info_general_clasificacion_licencias.html) | Art.9, A-IIa taxi M1 y facultades compatibles. | Orientación oficial; no sustituye revisión documental de la persona. |
| [RNV oficial, copia identificada 23.08.2016](https://portal.mtc.gob.pe/transportes/terrestre/documentos/REGLAMENTO%20NACIONAL%20DE%20VEHICULOS%20%20actualizado%20al%2023.08.2016.pdf) | Anexo I categoría, art.25 taxi. | No es consolidado a 2026. Filas/ruedas no acreditan categoría. |
| [DS004-2024-MTC oficial SAT](https://www.sat.gob.pe/TransparenciaV3/Portals/0/Docs/NormasOtrasEntidades/DS_004-2024-MTC.pdf) | Art.2, RNAT 3.16/28 SOAT/CAT y circunscripción. | Verificar emisor/ámbito/entrada en vigor y normativa complementaria; no aceptar CAT automáticamente. |
| [OM0006-2025-MPCH/A oficial](https://cdn.www.gob.pe/uploads/document/file/7723464/6533519-om-0006-2025-mpch-a.pdf) | Artículo primero deroga OM014-2021; considerandos cuestionan también OM010-2024. | No se infiere derogación de OM010-2024: no aparece en dispositivo. No convertir excepciones individuales en regla universal. |
| [OM011-2010 y anexos](https://www.gob.pe/institucion/munichiclayo/normas-legales/4347333-0011-2010-mpch-a) | Referencia local citada por TUPA identificada. | Anexos/modificatorias no revisados íntegramente: variantes taxi siguen por contrastar. |

Se detectaron contradicciones entre texto web del trámite taxi y TUPA sobre antigüedad/CITV y entre introducción genérica del trámite colectivo y TUPA local sobre categoría. No se programan plazos a partir de esas frases web. No se aplican requisitos ATU ni se usa la resolución particular del adjunto como regla general.

## Confirmaciones pendientes agrupadas

1. DD-07: frontera de unidades M1/carrocería/título por modalidad, frente a ampliación expresa a M2 que aún requeriría contraste.
2. DD-08…DD-12 y DD-15: base de catálogo/revisión propuesta, manteniendo POR_CONTRASTAR los requisitos y títulos no acreditados.
3. DD-13A o DD-13B y DD-14A/B: evidencia sin copias por defecto y conservación propuesta, o alternativa de copias con condiciones completas antes de cargas.

DC-01A y DC-02B no se someten de nuevo a elección. Títulos/rutas reales de futuras organizaciones son evidencia pendiente, no se inventan ni se considera que una confirmación del esquema los acredita. L-01 sigue EN_CURSO.
