# Acta L-01 — Cierre funcional local con piloto bloqueado

**Estado: VERIFICADA. Tipo de cierre: FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Fecha local: **2026-10-17, America/Lima**. Evidencias finales UTC2026-10-18. Emisión por A-COORD sobre las autorizaciones expresas del titular registradas en esta conversación; no representa aceptación de ADMIN legítimo ni habilitación del piloto.

## 1. Alcance y adendas aplicadas

Se cierra el alcance funcional local de L-01 del contrato Ruta Fija V3.1: acceso/roles, organización, OTP, términos, alta/activación/reemplazo ADMIN, ID-01/cupos, aislamiento/autorización, invitaciones e incorporación/revisión documental personal Q-003/PB-01.5, recuperación SUPER_ADMIN/CONDUCTOR, revocación/auditoría y recorridos de clientes correspondientes. No se trasladan los demás criterios a L-02 ni se reducen.

Se aplican [SC-L01-LOCAL-01 1.0.0](../contratos/SC-L01-LOCAL-01-adenda-v1.0.0.md) y [precisión1.1.0 aprobada](../contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md). La1.1.0 admite el gate automatizado complementario sin calificarlo como humano. Se conserva el [contrato anterior íntegro](../../memory/backup/2026-10-17-pre-cierre-local-gate-automatizado/docs/contratos/L-01.md), la propuesta y el registro de aprobación condicionada como antecedentes. No se solicitó una aprobación equivalente adicional.

## 2. Matriz y evidencias de cierre

La [matriz final de los doce criterios](../evidencia/L-01/matriz-final-funcional-local-2026-10-17.md) identifica para cada CI evidencia, entorno, resultado y límites; forma parte de esta acta. No existe criterio obligatorio local pendiente bajo las adendas aprobadas. La revisión no se basa únicamente en el número de pruebas.

| Evidencia esencial | Resultado y referencia |
|---|---|
| CI01 | VERIFICADO histórico; no se repitió SMTP. Run `fa616262-500e-4490-9292-7ee1927d2257`, [consumo/limpieza](../evidencia/L-01/cierre-parcial-CI01-y-terminos-2026-10-17.md). Su solicitud PENDING_REVIEW sin provisión sigue presente al contraste final. |
| CI03 humano | Aceptación manual sintética `c496b63c-735f-48cc-be56-90f7ec1464e1` conservada. [Archivo original](../evidencia/L-01/ensayos-humanos-140c10a1-be0f-4d08-a6d4-c796e2ed107a/observacion-1792227077200.json), SHA **9319F045F65FD4EE61B9A07EDEC3000E5EA6AC0E75618D99E2C4896843B8A719**. Su resultado histórico completo permanece PENDING por gate omitido; no se reescribe. |
| CI03 complemento automatizado | **PASS_AUTOMATED_SYNTHETIC_GATE_ONLY**; [HTTP y PostgreSQL](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/gate-http-postgresql.json), SHA **AC8C49AEAA7D7CA81D0E20086C3C3B32C709B83062D445AF8AA21D9DC539C34A**. `complete`409 específicamente por términos pendientes→aceptación automatizada adicional→mismo`complete`200. Login/grupos200. Origen automatizado auditado; ninguna atribución personal al titular. |
| Resultado SC-03 compuesto | **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**. Mismo RUN/SA/organización; cuenta manual y automatizada distintas, una sola alta adicional. |
| CI07 humano | **PASS_LOCAL_TEMPORARY_COPY_ONLY**; [archivo original](../evidencia/L-01/ensayos-humanos-140c10a1-be0f-4d08-a6d4-c796e2ed107a/observacion-1792287275662.json), SHA **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**; [informe contrastado](../evidencia/L-01/CI07-manual-PASS-2026-10-17.md). No se repitió recuperación. Cuenta/clave/copia temporales, copia en la misma PC. |
| Contraste nativo final | **PASS_FINAL_NATIVE_CORRESPONDENCE_AND_SELECTIVE_CLEANUP**; [SELECT read-only](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/contraste-final-nativo-solo-lectura.json), SHA **7D6EDDCB7618B94196BF0A381CC98864E00E07E93AD0F9D54350BBAA01DC601A**. Rol rf_test/V26, rollback, sin Spring/SMTP/DML. |
| Regresiones | Seis logs anteriores con huellas iguales y copias durables;27 unitarias dirigidas del arnés correctas. [Archivo de fuentes/resultados](../evidencia/L-01/cierre-funcional-local-2026-10-17/). No se repitieron suites completas ni se sumaron ejecuciones como pruebas únicas. |

RUN común: **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. La recuperación conserva el segmento **e140a035-8211-4037-82e6-388329f1cfc8**, pública SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**, una finalización, un REC02 y un REC03 autorizados. El original de inscripción y sus incidentes permanecen antecedentes; no se declara acreditada la entrega original del código perdido. Se conservan las2 emisiones simuladas/1reenvío de CI07 dentro del límite, sin registrar cero.

## 3. Limpieza, protecciones e historial

- Evidencias humana y automatizada archivadas antes de la salida. Se empleó la salida prevista del mismo arnés con condición de cierre complementaria aprobada, no borrado forzado del marcador.
- **TEST_CLEANUP_RELEASED=true**, [comprobación de salida](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/salida-y-protecciones.json): marcador ausente, lock libre, contexto/backend/proxy cerrados. Pueden reanudarse futuras pruebas autorizadas que limpian solo ruta_fija_test. En este cierre no se ejecutó limpieza global.
- Limpieza selectiva: únicamente ADMIN adicional `05f40f13-be6c-4166-91b7-ac7e400d7911` deshabilitado por API normal; refresh activos0 y sesión previa rechazada403 `FORBIDDEN_ROLE`. Se retiró el archivo privado de capacidades exclusivamente automatizadas.
- Ledger histórico **SUPER_ADMIN1 / ADMIN2 / CONDUCTOR0 → SUPER_ADMIN1 / ADMIN3 / CONDUCTOR0**. Quedan **0/1/20** cupos. No se recuperaron cupos. Se preservan las dos aceptaciones, el ADMIN manual, SUPER_ADMIN recuperado, identidad, organización, auditoría y el caso CI01.
- La detención auxiliar por esperar401 en vez de403 y su corrección de test permanecen [documentadas](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/README.md). Reanudación de la misma cuenta, sin segunda creación, emisión OTP o aceptación.

## 4. Requisitos originales pendientes antes de habilitar piloto

Estos requisitos **no están satisfechos**; se separan del cierre local por aprobación expresa, no por equivalencia:

1. ADMIN legítimo elegible, identidad/organización autorizadas y aceptación personal propia de términos vigentes. El titular prevé rol SUPER_ADMIN; los ADMIN ficticios no representan otra persona real ni permiten doble rol real.
2. Preparación, custodia independiente, inscripción previa y ensayo personal del titular real SUPER_ADMIN; clave privada cifrada fuera del servidor/repositorio, copia cifrada independiente comprobada y código de emergencia separado. Solo una PC Windows está confirmada. Hace falta un medio de almacenamiento realmente independiente y disponible —por ejemplo un medio extraíble propio o prestado, sin contratación— antes de acreditar respaldo frente a pérdida de esa PC; su disponibilidad y comprobación siguen pendientes. No se presupone que exista. Dos archivos en la PC acreditan lectura/uso local, no independencia.
3. Información separada de identificación/privacidad completa y aprobada, condiciones de protección/conservación y procedimiento restringido de retiro. T-04/T-05/T-06 y RET-02 permanecen aprobados con sus plazos y responsables; no se declara ejecutada una purga futura o todo el automatismo de retención.
4. Preparación y autorización de infraestructura piloto: base/HTTPS, respaldos cifrados y restauración probada; actualmente no se acreditan por este cierre. Participantes y recursos reales deben habilitarse conforme a cupos, aislamiento y elegibilidad.
5. Títulos, categoría/documentación y autorizaciones reales compatibles con Chiclayo y modalidad; cualquier requisito POR_CONTRASTAR o desconocido continúa bloqueando el caso aplicable. No hay cargas de documentos originales habilitadas ni equivalencia de correo verificado/invitación aceptada con operación.

Los datos personales siguen ocultos en vistas ordinarias y solo ADMIN autorizado revisa el expediente restringido conforme al contrato. Cinco organizaciones son referencias; al menos una no operará con cuatro altas ADMIN históricas. No se comparten cuentas ni amplían cupos para resolverlo.

## 5. Conservación y puerta siguiente

- ADMIN1.0.0 conserva SHA **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**; no se reescribió ni se solicitó nueva aprobación.
- final3 ZIP conserva SHA **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**; manifiesto **E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD**. Verificación actual, sin regeneración.
- Sin envíos externos nuevos, Git/GitHub, despliegue, migraciones de desarrollo/piloto, material definitivo del titular ni habilitación real. S/0, sin offline, RENIEC/MTC o QR. Validación física permanece en L-08.
- **L-02 continúa SIN INICIAR y NO AUTORIZADA. Su OK VERDE es separado.** El cierre de L-01 no inicia el siguiente lote.
