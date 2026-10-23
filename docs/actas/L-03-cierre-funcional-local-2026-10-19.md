# Acta L-03 — Cierre funcional local con piloto bloqueado

**Estado: VERIFICADA. Tipo: FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Fecha **2026-10-19, America/Lima**. A-COORD concluye el alcance local autorizado mediante OK VERDE posterior al cierre L-02 y las instrucciones de continuar. Se aplican **MNT-01A/MNT-02A** ya aprobadas: bloqueo abierto hasta retorno ADMIN y liberación desde el día siguiente al retorno real. No se solicita otra aprobación equivalente. **L-04 NO INICIADA / NO AUTORIZADA**.

## Alcance cerrado

[Contrato 0.2.0](../contratos/L-03.md) y [antecedente0.1.0](../contratos/L-03-v0.1.0-antecedente.md). Grupos múltiples, recursos y compromisos antes de retirada lógica; identidad/vigencia/exclusividad de unidades reutilizadas de L-02 y endurecidas; preventivo multifecha, avería urgente/relevo, trabajo y retorno separados; rutas/paradas/versiones inmutables, captura histórica y cambio expreso con nueva aceptación; consulta propia CRM/Flutter, declaración final y corroboración limitada; disponibilidad propia persistente separada de elegibilidad/ocupación/conexión.

No se trasladó ni redujo un criterio obligatorio local. La [matriz final CI-L03-01…10](../evidencia/L-03/matriz-final-funcional-local-2026-10-19.md) identifica evidencia, entorno, resultado y límites. **Los diez criterios locales están verificados.** L-01/L-02 conservan sus cierres y los requisitos reales de piloto siguen pendientes.

## Resultados y límites

| Verificación ejecutada | Evidencia / resultado | Límite |
|---|---|---|
| Servidor final | [97 casos distintos](../evidencia/L-03/aceptacion-V32/resultado.json),0fallos/errores/omitidos:87 HTTP Spring/PG,6 carreras de base y4 geometría. | MockMvc usa filtros/servicios/PG reales, no procesoTCP independiente. Fixtures HTTP con rollback; carreras con dos conexiones y recursos técnicos sin cuentas. |
| Cliente CRM |20 componentes, typecheck y build local correctos. [Archivo](../evidencia/L-03/aceptacion-V32/README.md). | Componentes con dobles separados del navegador real. |
| Cliente Flutter |28 casos dirigidos, analyze sin incidencias, APK debug correcto. | Modelos/repositorios/widgets; no Android físico ni prueba de GPS real. |
| Conectado | [RUN2e734706](../evidencia/L-03/conectado-2e734706-f7ef-4947-b940-1f64fd13f86a/resultado.json): navegador Angular/Edge real y backendTCP/PG para catálogo/preview/preventivo/cancelación; repositorio FlutterTCP rechazado403 por rol. | No consulta móvil positivaTCP ni un recorrido humano nuevo. Backend/PG y cliente cubren esa consulta por separado. Smoke anterior al último endurecimiento de locks/caché, cubierto después por la suite nativa final. |
| Migraciones |V29…V32 solo `ruta_fija_test`; [Flyway final válido](../evidencia/L-03/aceptacion-V32/post-suite-solo-lectura.json), runtime rf_test/migrador rf_migrator. | No desarrollo/piloto, repair, edición de migración aplicada o down migration. |
| Retorno seguro |[Procedimiento](../operacion/L-03-validacion-y-retorno.md) e interruptor operativo incluido entre las regresiones finales. | Mismo binario actualizado, esquema/historia retenidos; no restauración/despliegue de producción probado. |
| Artefactos |[JAR final, APK y CRM local con SHA256](../../artifacts/L-03-2026-10-19/manifest.json). JAR empaquetado después de las últimas correcciones. | Solo local, sin despliegue ni nuevo final3; no configuración privada ni arnés de prueba empaquetados. |

La revisión transversal corrigió la falta de serialización de escrituras ordinarias de flota y la lectura JPA obsoleta de Driver tras esperar el lock. Se añadieron regresiones de caché y corrección material de unidad con compromisos, incluidas en los97. Una prueba adversa de ruta verifica que el fallo después de cancelar marca rollback de la transacción física; no se presenta como dos casosTCP confirmados.

Tras la suite final se corrigió solo el nivel de registro en contextos de prueba. La reejecución dirigida de ocho casos de declaración y uno de catálogo pasó; no se suma al97. Se sanearon fragmentos/valores de credenciales generadas en registros propios L-03 mediante [journal](../evidencia/L-03/saneamiento-registros-generados.json), conservando resultados y fallos. La primera pasada se interrumpió por codificación y no conserva todos sus hashes originales; se documenta esa limitación, sin inventar trazabilidad cruda.

## Cierre selectivo y conservación

Caso conectado **2e734706-f7ef-4947-b940-1f64fd13f86a**. Se reutilizó ADMIN/organización sintéticos de L-01 sin cambiar contraseña ni aceptar términos otra vez. **Cero cuentas comprometidas nuevas**, cero SMTP externos; no se reabrió el CONDUCTOR cerrado de L-02.

[Postflight independiente final](../evidencia/L-03/aceptacion-V32/postflight-selectivo-final.json): unidad técnica `95d84927-72b5-48ad-ab0f-b8191c0664f6` inactiva; ruta `f8f3925a-6979-49f7-8fe9-f15dbfb82576` retirada y revisión retenida; mantenimiento `0bca782b-9933-4713-8563-b367df1c1c54` cancelado; tres refresh del ensayo revocados. Marcador ausente y lock libre, **TEST_CLEANUP_RELEASED=true**. Historial y recibos retenidos. No hubo limpieza global; su barrera histórica permanece obligatoria.

Ledger **1 SUPER_ADMIN / 3 ADMIN / 1 CONDUCTOR**, cupos restantes **0/1/19**, **dos aceptaciones** y hashes de casos humanos/L-02 preservados. [Ocho archivos inmutables](../evidencia/L-03/aceptacion-V32/inmutables-preservados.json): snapshot/manifiesto final3, términos, CLI anterior, actas L-01/L-02 y evidencia manual de aceptación/recuperación sin cambios. CI-L01-01 y CI-L01-07 no se repitieron.

Final3 SHA256 **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**. Términos ADMIN1.0.0 **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**. Solo lectura de huellas, sin regeneración ni publicación nueva.

## Archivo y concordancia

[Ejecución](../ejecucion/L-03.md), [índice final](../evidencia/L-03/aceptacion-V32/README.md), [fuentes por huella](../evidencia/L-03/fuentes-cierre.sha256), [manifiesto de cierre](../evidencia/L-03/manifest-cierre.sha256). Respaldo público previo en `memory/backup/2026-10-19-L03-antes-consolidacion`; no incluye secretos. Contrato, matriz, backlog, tablero y memoria expresan el mismo estado. Se conservan cortes previos EN_CURSO como antecedentes. Revisión/ejecución por A-COORD; no se atribuye a una revisión independiente adicional.

## Piloto real todavía bloqueado

Permanecen pendientes los requisitos originales: aceptación propia de ADMIN legítimo; custodia independiente/clave definitiva y ensayo del titular real; identificación/privacidad y mecanismos efectivos de protección/retiro con excepciones; infraestructura/base piloto/HTTPS, copias cifradas y restauración; participantes, organizaciones, unidades y títulos reales. No se presume medio independiente adicional a la PC Windows. GPS operativo L-05, calibración/dispositivo L-08. No se retrofechan inicio/fin efectivos del piloto.

**Resultado final: L-03 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO. S/0; sin offline, correo externo, Git/GitHub, despliegue, claves definitivas o migraciones de desarrollo/piloto. L-04 requiere su propio OK VERDE y no se inicia.**
