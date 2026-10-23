# Smoke real de API/CRM y revisión independiente

2026-10-23 America/Lima. Ejecución: coordinador raíz. Revisión independiente: A-OPS, mediante lectura de evidencia existente, hashes y consultas del sistema operativo; sin volver a conectar a PostgreSQL, ejecutar pruebas o iniciar/detener servicios.

**Resultado: PASS.** [Revisión independiente](qa-independent-ops.json): **207 comprobaciones aprobadas**, incluida comparación exhaustiva de 121 tablas antes/después y verificación de los 51 archivos del corte congelado de ejecutables/manual.

| Verificación | Resultado comprobado |
|---|---|
| `iniciar_ruta_fija.bat --smoke-read-only --test-database` | Código0; runtime v2 `SMOKE_READ_ONLY`, PID de API2720 y Angular17604 registrados con ejecutable/inicio UTC |
| Salud y readiness de API | HTTP200 y `status=UP` en ambos endpoints |
| Angular y proxy del mismo origen | Configuración runtime `/api/v1`; login real renderizado en Edge, correo/contraseña visibles, cero excepciones, errores de consola o peticiones fallidas |
| Refresh anónimo sin cookie | Un HTTP401 esperado en `/api/v1/auth/refresh`; demás respuestas del navegador HTTP200. No se inició sesión ni se solicitó una escritura de negocio |
| `finalizar_ruta_fija.bat` | Código0; API/CRM detenidos, PostgreSQL conservado porque ya estaba activo y no se había adquirido una lease |
| Procesos posteriores | Consulta independiente: PID2720 y17604 ausentes; estado runtime/demo/lease ausentes; puertos8080/4300/4311/55438/55439/55440/55441/56441/56442 libres. PostgreSQL5432 sigue en loopback |
| Logs | stdout backend/frontend sin ERROR, WARN ni marcadores de fallo; ambos stderr vacíos. Cuatro menciones de `Exception` en stdout corresponden a clases de configuración de traducción de excepciones, no a fallos ni trazas |

## Preservación de datos e historial

Se compararon los fingerprints ya capturados [antes](../launchers/fingerprint-antes-smoke.json) y [después](fingerprint-after-smoke.json), sin repetir la prueba ni abrir otra conexión durante la revisión:

| Base | Tablas | Filas totales | Comparación |
|---|---:|---:|---|
| `ruta_fija_test` | 105 | 1424 → 1424 | Cada tabla conserva su conteo y SHA-256 completo de contenido; ledger SUPER_ADMIN1/ADMIN3/CONDUCTOR8 y dos aceptaciones ADMIN intactos |
| `solucion_ruta_fija_1` | 16 | 154 → 154 | Cada tabla conserva su conteo y SHA-256 completo de contenido; permanece en V12 |

No hay diferencias de tablas, conteos, contenido ni secuencias Flyway. En la fixture, **51 ejecuciones exitosas = 50 migraciones versionadas V1 a V50 + una repetible; no existe V0**. La aclaración se registra en esta auditoría nueva sin reescribir el acta firmada anterior.

Los hashes del runtime, la lease y el JAR usados por el coordinador coinciden con los capturados antes del smoke. Los 51 archivos de `manifest-lanzadores-vigentes.sha256`, incluidos los manuales congelados y sus antecedentes exactos, siguen sin modificaciones.

## Alcance

Se acredita el arranque real local de API/CRM, renderizado anónimo y cierre controlado en modo de lectura. No se acredita una operación autenticada que escriba ni se habilita el piloto. La base normal V12 continúa requiriendo una migración planificada fuera de esta auditoría.

El BAT imprimió disponibilidad antes de que la captura del comando terminara; el código0 se recogió después de detener los procesos hijos. Se conserva esa observación de vida de los handles de la herramienta, sin presentarla como prueba de retorno inmediato del CLI al iniciar.

Procedimiento de revisión reproducible: [qa_independent_ops.py](qa_independent_ops.py). Su salida es de creación exclusiva; una nueva revisión requiere un destino nuevo, nunca reescribir el informe existente. [Consulta independiente de procesos](qa-independent-ops-processes.json) y [pruebas HTTP](http-checks.json).
