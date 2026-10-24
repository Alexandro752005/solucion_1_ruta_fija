# L-03 — Validación y retorno compatible

**2026-10-19, America/Lima.** Destino autorizado exclusivamente `ruta_fija_test`, runtime `rf_test`, migrador `rf_migrator`. [Contrato](../contratos/L-03.md), [acta](../actas/L-03-cierre-funcional-local-2026-10-19.md).

## Migraciones aditivas verificadas

- V29: membresías/historia/snapshots de grupos; la historia anterior no se reconstruye.
- V30: catálogo y versiones de ruta inmutables; asignaciones anteriores sin referencia siguen `LEGACY_UNRECORDED`.
- V31: mantenimiento de día completo, declaración de disponibilidad inicialmente desconocida, placa canónica única y protección de membresía primaria. No se deduce retorno por fecha prevista.
- V32: captura y sustitución explícita de ruta, recibos de declaración final y guardas de tenant/inmutabilidad. Las revisiones aprobadas siguen separadas de reserva/operación.

Preflight revisó historial, colisiones, roles, marcadores/lock y hashes antes de cada migración. El primer intento V31 esperó una lectura del mismo preflight: se canceló solo su proceso propio, se comprobó rollback/V30 y se liberó esa transacción antes del DDL conservando el advisory lock de protección. Se continuó la misma migración sin repair ni cambio de SQL aplicado. [Validación final V32](../evidencia/L-03/aceptacion-V32/post-suite-solo-lectura.json), cero migraciones inválidas.

## Detener la operación conservando evidencia

1. Archivar resultados, referencias y manifiestos saneados. No repetir ensayos humanos/OTP ni abrir un caso conectado cerrado.
2. Mantener el esquema V32 y el binario compatible actualizado. No volver a un binario que ignore mantenimiento, captura de ruta o declaración separada; no editar Flyway aplicado, hacer repair, borrar tablas ni ejecutar down migration por inferencia.
3. En una instancia local de pruebas autorizada, usar `app.qualification.operations-enabled=false` (variable de proceso `APP_QUALIFICATION_OPERATIONS_ENABLED=false`) y reiniciar solo esa instancia cuando corresponda. La política deniega habilitación/reserva/aceptación/inicio, conservando expediente/consulta y cese seguro. No ejecutar esta acción en desarrollo/piloto sin su autorización.
4. La prueba `operationalReturnSwitchFailsClosedAndPreservesTheApprovedDossier`, incluida entre los54 casos finales de MaintenanceIntegrationTest, acredita el bloqueo funcional y preservación. No acredita reinicio/despliegue/restauración de producción; no se hizo un downgrade.
5. Conservar ocupación de servicio iniciado y atención de relevo seguro. No completar/cancelar automáticamente un servicio en curso, reactivar unidad, devolver mantenimiento ni fabricar declaración por detener la aplicación.
6. Diagnosticar y revalidar causa antes de volver a habilitar. El retorno mecánico normal sigue requiriendo ADMIN, trabajo acreditado, documentos/otros bloqueos y medianoche Lima siguiente.

## Retiro del caso técnico, no borrado de históricos

RUN `2e734706-f7ef-4947-b940-1f64fd13f86a`: plan futuro cancelado mediante API, unidad técnica inactiva, ruta retirada y revisión retenida; familia refresh del ensayo revocada. Se conservaron recibos, historial y casos L-01/L-02. [Postflight final](../evidencia/L-03/aceptacion-V32/postflight-selectivo-final.json) confirma marcador ausente, lock libre y recursos exactos retirados de operación. **TEST_CLEANUP_RELEASED=true** no permite TRUNCATE/limpieza global ni recuperar cupos. Futuras pruebas usarán rollback o retiro selectivo comprobado.

Ledger1/3/1 y dos aceptaciones sin cambios; no cuentas comprometidas nuevas L-03. El correo del arnés es un doble que rechaza tráfico externo. No SMTP, desarrollo/piloto, Git/GitHub, despliegue, claves definitivas o nuevo final3.

## Antes de uso real

Siguen obligatorios los requisitos de aceptación propia de ADMIN legítimo, custodia independiente/ensayo titular real, privacidad/identificación/protección y retiro ejecutable, infraestructura/base/HTTPS/copia cifrada/restauración y preparación de participantes/recursos/títulos. No se acredita independencia de custodia por dos archivos en una PC, ni purga por documentarla. GPS operativo L-05 y dispositivo/calibración L-08. L-04 no iniciada ni autorizada.
