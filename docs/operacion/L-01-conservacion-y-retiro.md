# L-01 — Conservación y retiro de datos del piloto

Fecha: 2026-10-15. Estado: **PROCEDIMIENTO PREPARADO; NO EJECUTADO**. L-01 EN_CURSO. No autoriza trabajar sobre una base de desarrollo/piloto ni recopilar datos reales.

## Fuente y responsabilidades

El prompt maestro adoptado por el titular, sección 4, incorpora los plazos de la ficha factual. DD-13A/DD-14A/B y la respuesta posterior asignan al ADMIN de cada organización el retiro de su expediente. El titular conserva la responsabilidad general y técnica declarada en la ficha; no se atribuye al ADMIN el control de copias o infraestructura ajenas a su organización.

| Categoría | Regla declarada | Responsable y alcance |
|---|---|---|
| Expediente personal y revisiones estructuradas | Deshabilitar al cierre; eliminar a los 30 días del fin del piloto, salvo excepción concreta justificada y aplicable con finalidad y vencimiento | T-05 aprobado: ADMIN identifica y solicita antes de la baja; responsable técnico ejecuta retiro limitado y auditado, sin reactivar permisos operativos ni ampliar acceso ordinario SUPER_ADMIN. No fotografías/PDF ni originales. |
| Cuentas y datos personales operativos | RET-02 precisado por T-04: deshabilitar ADMIN/CONDUCTOR al cierre y retirar por campo a los 30 días del fin, incluidos HMAC DNI y vínculos personales, conservando IDs/historial mínimo/cupos | Titular/responsable técnico según ficha. Excepciones específicas con finalidad y vencimiento; no conservar indefinidamente ni llamar irreversible a una transformación vinculable. SUPER_ADMIN/custodia excluidos por inferencia. |
| Términos y aceptaciones | 90 días después del fin del piloto | Titular/responsable técnico; acceso restringido y retiro cuando termine su finalidad. No destruir por analogía al plazo del expediente. |
| Metadatos mínimos de recuperación | 30 días desde finalizar o cancelar el caso | Titular/responsable técnico. La retención no extiende la validez de tokens, retos ni códigos. |
| Auditoría operativa | 6 meses, con revisión acotada de casos abiertos | Rol autorizado en su alcance. No conservar PII documental completa por copiarla a auditoría. |
| Detalle de ubicación | 7 días cuando se habilite en su lote | Fuera de la implementación de L-01. No autoriza captura ni modifica sus condiciones. |

El token final de restablecimiento CONDUCTOR vence a los **30 minutos desde emisión**, con un uso. El OTP mantiene cinco minutos y sus límites propios. Estos valores ya están declarados: no requieren repetir una aprobación de plazo.

## Secuencia operativa previa al uso real

1. Registrar inicio efectivo al habilitar el piloto y fin/cierre en el mismo acto siete días después (T-06 aprobado). El fin/cierre efectivo inicia los plazos de treinta/noventa días vinculados al fin; recuperación conserva su propio evento. No convertir agosto o la estimación 2026-10-26 en fechas ocurridas. Hoy no existe despliegue activo.
2. Identificar por organización y categoría los registros afectados y su fecha de vencimiento. ADMIN comunica su selección antes de la baja; el responsable técnico inventaría y ejecuta técnicamente en el alcance autorizado. No reactivar al ADMIN para retirar después ni ampliar el acceso ordinario a expedientes. No incluir datos de otra organización en una solicitud.
3. Documentar cualquier excepción por caso: categoría exacta, finalidad/fundamento aplicable, responsable, alcance y fecha de revisión. Un campo vacío o un caso abierto genérico no autoriza conservar todo indefinidamente.
4. Deshabilitar los accesos acordados al cierre y revocar credenciales temporales vigentes. No confundir cuenta deshabilitada con datos eliminados.
5. Preparar y probar en `ruta_fija_test`, con datos sintéticos y reloj controlado, una operación de selección previa y retiro transaccional por categoría/organización, sin eludir protecciones generales. Comprobar límite temporal exacto, excepciones, aislamiento, concurrencia, consistencia de referencias e idempotencia.
6. Antes de ejecutar en otro entorno, verificar destino, respaldo y autorización concreta. Presentar conteos saneados por categoría/organización y la acción propuesta; no mostrar nombres, correos, documentos o secretos. Esta interacción solo autoriza ejecutar pruebas en `ruta_fija_test`.
7. Registrar hora, rol responsable, ámbito, cantidad seleccionada/retirada, excepciones, fallos y resultado. Un fallo conserva el caso abierto; no marcarlo como eliminado. La evidencia de ejecución no debe reproducir la información retirada.
8. Inventariar y retirar o sustituir copias controladas que contengan datos vencidos, incluidas papeleras cuando existan. Antes de restaurar, aplicar los vencimientos/retiros pendientes para no reintroducirlos. Hoy no se acredita copia cifrada ni restauración comprobada.

## Dependencias técnicas reales

En V25 no existe aún un mecanismo completo de cierre/purga por categoría. `V21__onboarding_documentary_review.sql` protege `conductor_document_revision` y `conductor_onboarding_review_event` contra UPDATE/DELETE. Esa protección conserva el historial durante su vigencia; **no acredita una eliminación programada**. El retiro futuro requiere una migración correctiva y una operación restringida que preserve ese control, con pruebas específicas. No se cambia una migración aplicada ni se desactivan triggers de forma general para simular cumplimiento.

Hay datos personales también en `conductor_onboarding_identity`, invitaciones, campos de cuenta/conductor y posibles textos de observación o motivo. Limpiar solo una tabla no demuestra retiro integral. Los HMAC o identificadores vinculables siguen requiriendo análisis de finalidad y retirada; no se presentan como anonimización irreversible.

El ledger de creaciones históricas debe conservar el cómputo 1/4/20: borrar una cuenta no devuelve cupo. Esto no autoriza conservar innecesariamente sus datos personales. **RET-02 fue aprobado expresamente con sus límites**: anonimizar ADMIN/CONDUCTOR a treinta días conservando IDs/historial/cupos; no se ejecuta sobre SUPER_ADMIN por inferencia. Conservar DNI/HMAC vinculables no acredita anonimización irreversible. El mecanismo debe seleccionar qué campos y vínculos se retiran, comprobar el cierre operativo e identificar finalidad/plazo de cualquier conservación específica. Aprobación del diseño no es evidencia de ejecución.

**Precisión T-04/T-05/T-06 posterior, aprobada:** el retiro de HMAC DNI y vínculos personales ADMIN/CONDUCTOR vence también a treinta días del fin/cierre efectivo. No queda una decisión nueva pendiente sobre ese plazo. La ejecución futura debe abarcar `person_identity`, el puente conductor/persona y los datos estructurados/índices que sigan identificando, respetar las categorías con plazo propio y conservar ledger/IDs sin contenido personal innecesario. Los triggers de inmutabilidad requieren una operación controlada y pruebas, no se desactivan globalmente. ADMIN identifica/solicita; responsable técnico ejecuta sin reactivar permisos ni abrir consulta ordinaria. Excepciones por caso requieren finalidad y vencimiento. Este documento prepara el procedimiento, no acredita herramienta o ejecución ya existentes.

El prompt maestro excluye ampliar L-01 a construir todos los mecanismos de retención del producto. Este procedimiento y sus dependencias se registran como condición previa al uso real de datos; no se inventa un nuevo AC que exija una eliminación pospiloto ya ejecutada cuando el piloto no ha comenzado. La matriz mantiene por separado los criterios obligatorios de términos, recuperación, privacidad e incorporación.

## Evidencia disponible y pendiente

- Disponible: decisiones trazables, catálogo sin cargas/copia, límites de acceso y pruebas sintéticas de los flujos L-01, descritas en la matriz vigente.
- Pendiente: mecanismo completo de cierre/retiro, prueba de vencimiento por categoría y copias, ejecución sobre datos reales y comprobación posterior. Ninguna de estas ejecuciones se declara realizada.
- Referencias: [contrato documental aprobado](../contratos/L-01-documental-Chiclayo-v0.1.0.md), [aclaraciones vigentes](../../specs/aclaraciones-L01-Chiclayo-2026-10-15.json), [matriz única de cierre](../evidencia/L-01/matriz-cierre-L01-revision-2026-10-14.md).
