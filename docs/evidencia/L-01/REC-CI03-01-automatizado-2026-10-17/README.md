# REC-CI03-01 — Gate automatizado complementario acreditado

Fecha local: 2026-10-17, America/Lima. RUN `140c10a1-be0f-4d08-a6d4-c796e2ed107a`.

**Resultado: PASS_AUTOMATED_SYNTHETIC_GATE_ONLY**. Compuesto SC-03: **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**. Esta ejecución no es humana ni constituye aceptación de una persona ADMIN legítima.

## Alcance autorizado y evidencia anterior evaluada

Se aplicó la precisión aprobada de [SC-03 1.1.0](../../../contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md). Los IT anteriores de términos usaban MockMvc, autenticación inyectada y documentos sintéticos; aportan regresión, pero no cubrían exactamente este recorrido HTTP con ambos gates y el documento publicado. No se usaron por equivalencia para omitir la comprobación pendiente.

Se amplió únicamente el soporte de test del arnés existente. No cambió código de producción, esquema, permisos, seguridad ni documento publicado. El nuevo modo `-CompleteApprovedLocalRunId` conserva el segmento de recuperación ya finalizado. SUPER_ADMIN recibe una credencial de fixture mediante el `JwtService` real de test; el servidor verifica ese JWT firmado y su estado persistido normalmente. Esto no es login humano del SUPER_ADMIN ni prueba de custodia real. El ADMIN sigue alta/claim/OTP/aceptación/activación/login HTTP normales con identidad y credenciales exclusivamente sintéticas.

## Correspondencia y recorrido

| Referencia | Valor |
|---|---|
| Entorno | PostgreSQL local `ruta_fija_test`, rol `rf_test`, V26; backend real en loopback, correo simulado |
| SUPER_ADMIN conservado | `088518c1-821c-4316-9f87-090f51ac44e0` |
| Organización conservada | `1a759298-7831-4aaf-9299-c3920a9cc28a` |
| ADMIN manual conservado | `92ebc07c-1319-4850-a734-f792cb299979` |
| Aceptación humana conservada | `c496b63c-735f-48cc-be56-90f7ec1464e1` |
| Único ADMIN adicional automatizado | `05f40f13-be6c-4166-91b7-ac7e400d7911` |
| Activación automatizada | `83f9c824-1abe-43fe-bca8-7538be6de87c` |
| Aceptación automatizada | `7978c493-5501-4d09-a714-bd90dceda745` |
| Documento | `f590e228-5d2e-47e4-b144-b0df1198bd49`, ADMIN 1.0.0, SHA `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417` |
| Auditoría explícita de origen automatizado | `f6eecbbe-e1e9-4b3d-b9ed-bbae0582a099`, `L01_AUTOMATED_SYNTHETIC_GATE_VERIFIED`, actor sistema, organización correcta |

1. Alta normal inactiva, identidad ficticia distinta y un cupo consumido; claim establece contraseña sintética. Un OTP simulado, cero reenvíos, verificación por API.
2. `GET /admin-activation/status` confirma capacidad válida, contraseña/correo/identidad comprobados, sin aceptación y estado IN_PROGRESS. SELECT confirma cuenta inactiva y ninguna aceptación.
3. **Misma operación protegida antes:** `POST /api/v1/admin-activation/complete` → **409 RESOURCE_CONFLICT**, mensaje exacto `Falta la aceptacion expresa de los terminos aprobados actualmente vigentes.` Timestamp servidor **2026-10-18T02:32:23.127668400Z**, correlación `c0475b61-7fd7-46f3-a329-920415d3f74b`. No se usa 401/403 como prueba de este gate.
4. Lectura de términos publicados y aceptación explícita automatizada exclusivamente para el ADMIN adicional. Persistencia **02:32:23.265Z** a la precisión publicada del serializador; cuenta/organización/documento/versión/huella correctos.
5. **Misma operación protegida después:** `POST /api/v1/admin-activation/complete` → **200 COMPLETED**, persistido **02:32:23.330Z** a la precisión publicada. El arnés compara el orden usando los instantes nativos, sin truncarlos para esa comparación.
6. Login ordinario del ADMIN y `GET /api/v1/groups` → **200**. Se preservan los pasos, correlaciones, resultados y hashes de cuerpos de respuesta; no los cuerpos que contienen capacidades.

## Incidente auxiliar conservado y recuperación del mismo caso

El gate y el recorrido pasaron en la primera ejecución. Después de revocar el ADMIN adicional por API normal, una expectativa auxiliar exigía 401, pero el backend vigente devuelve **403 FORBIDDEN_ROLE**: el convertidor JWT retira autoridades de cuentas inactivas o con versión de sesión distinta. Se conservó el [intento detenido](intento-1-detencion-limpieza-http403.json), SHA **DD82679D09ECCA6B046E5F1E5B17820E452C2002D8BDF0BE6311C4BE8C565144**.

Se corrigió solo esa expectativa de test, contrastándola con `SecurityConfig` y `JsonAccessDeniedHandler`, y se reanudó desde la limpieza de **la misma cuenta**. No se repitieron alta, OTP ni aceptación y no se creó otro ADMIN. La segunda comprobación de sesión también obtuvo 403. No se cambió el código del producto para obtener un estado distinto.

El lector SELECT auxiliar inicialmente buscó OTP con propósito ADMIN_ACTIVATION después de completar/revocar; el mecanismo normal ya lo había invalidado y restablecido a LOGIN. Se corrigió la consulta auxiliar, sin DML. Su resultado final comprueba OTP/capacidad retirados y marcas de envío conservadas, sin afirmar un `consumed_at` actualmente presente después de esa invalidación. La verificación anterior queda acreditada por HTTP, activación y auditoría.

## Limpieza selectiva y salida

- Solo el ADMIN automatizado adicional se deshabilitó mediante `POST /platform/admins/{id}/revoke`. Sin refresh activo; sesión anterior rechazada con403. Su aceptación, identidad, historial y cupo permanecen.
- SUPER_ADMIN, ADMIN manual, aceptación humana y caso OTP anterior permanecen. El lector final confirma la solicitud CI01 `e0f3de05-5b8a-4f26-93d8-89e6ebc693dd`, PENDING_REVIEW sin provisión.
- Ledger **1/2/0 → 1/3/0**; cupos restantes **0/1/20**. No reinicio, borrado ni limpieza global.
- Se retiró únicamente el archivo privado de capacidades generado para la automatización. No se abrieron ni borraron claves privadas, contraseñas del operador o su código de emergencia.
- Se archivó antes de la salida prevista del arnés. Marcador propio retirado por esa salida, conexión del lock cerrada, contexto/backend/proxy detenidos. **TEST_CLEANUP_RELEASED=true**. La recarga anterior usó únicamente el comando público `salir` en el prompt exacto, mediante el puente de consola test; no leyó historial ni entradas secretas.

## Archivos y huellas

| Archivo | SHA-256 |
|---|---|
| [Gate HTTP/PostgreSQL](gate-http-postgresql.json) | `AC8C49AEAA7D7CA81D0E20086C3C3B32C709B83062D445AF8AA21D9DC539C34A` |
| [Contraste SELECT final](contraste-final-nativo-solo-lectura.json) | `7D6EDDCB7618B94196BF0A381CC98864E00E07E93AD0F9D54350BBAA01DC601A` |
| [Salida y protecciones](salida-y-protecciones.json) | Ver lateral `.sha256` y manifiesto de cierre |
| [Huellas de antecedentes y final3](huellas-antecedentes-y-final3.json) | Diez comparaciones actuales correctas, sin regenerar ni reejecutar suites |

La observación final del arnés es `observacion-1792290845998.json`, SHA **2C965C42F4C7E349C8F5E012A279A10003365A4FD52CAD571581339B35DA0804**. Conserva `MANUAL_ADMIN_RESULT=PENDING` como resultado del ensayo humano original incompleto respecto del gate, y añade el resultado compuesto SC03 y su referencia automatizada. No se renombra como PASS manual. `MANUAL_RECOVERY_RESULT=PASS_LOCAL_TEMPORARY_COPY_ONLY` permanece acreditado sin repetirlo.

27 pruebas unitarias dirigidas (4 nuevas de complemento +23 del arnés) correctas después de la corrección; no conectan ni limpian DB. La ejecución HTTP conectada se registra por separado y no se suma a una cifra ficticia de tests únicos. Fuentes y resultados congelados en [archivo de cierre](../cierre-funcional-local-2026-10-17/).
