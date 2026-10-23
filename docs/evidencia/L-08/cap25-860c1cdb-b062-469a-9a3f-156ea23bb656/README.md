# L08-CAP25-01 — Carga sintética local y retiro selectivo

**Caso cerrado y conservado. L-08 continúa EN_CURSO; piloto BLOQUEADO.** RUN `860c1cdb-b062-469a-9a3f-156ea23bb656`. [Autorización exacta](../CAP25-01-autorizacion.json), [propuesta aprobada](../../../operacion/L-08-CAP25-01-validacion-aislada-propuesta.md).

## Entorno y preparación

Instancia nativa nueva exclusivamente `ruta_fija_test@127.0.0.1:55439`, backend local4314; cuatro organizaciones sintéticas. Se aplicaron V1…V50 mediante migrador propio. El bootstrap de privilegios de pruebas se ejecutó **antes** de esas migraciones, para conservar sus revocaciones de tablas protegidas. Runtime `rf_test`, sin DDL;17 tablas protegidas y dos autoridades privadas verificadas. No se usó el cargador de5432 ni se migraron5432,55438, desarrollo o piloto. [Preflight](preflight/resultado.json), [inventario de esquema/permisos](preflight/infraestructura.json).

Se crearon exactamente1 SUPER_ADMIN,4 ADMIN y20 CONDUCTOR, con identidades distintas y ledger propio1/4/20. SUPER_ADMIN utiliza el bootstrap sintético de pruebas existente; ID-01, alta ADMIN inactiva, contraseña, OTP simulado, gate negativo, términos publicados1.0.0, aceptación automática explícitamente sintética y habilitación normal quedan registrados. CONDUCTOR usa invitación, identidad preliminar, revisión/aprobación, provisión, documentos, grupo, unidad, activación y disponibilidad normales. No se alteraron los cupos de5432 ni se reabrieron casos retirados.

Los cuatro registros de aceptación son **automatizados para identidades ficticias**, sin intervención o aceptación atribuida al usuario y sin equivalencia a personas ADMIN legítimas. El documento publicado no se modificó.

## Medición ejecutada

Registro real del2026-10-22, hora de Perú; muestras UTC entre2026-10-23T01:00:43.798Z y01:00:55.070Z. [Resultado completo](medicion.json), [muestras append-only](medicion-http.jsonl), [estado y claves de operación](medicion-state.json).

| Operación | Muestras válidas con commit observado | p95 HTTP | p95 hasta observar commit |
|---|---:|---:|---:|
| Creación |100|342,4614ms|343,0684ms|
| Aceptación |100|461,0518ms|461,8702ms|

**PASS_MEASURED_LOCAL_SYNTHETIC_ONLY**: ambos p95≤2000ms, cero errores y pico real25 solicitudes HTTP en vuelo también dentro de las ondas medidas. Cinco ciclos;25 workers liberados por barrera en cada onda. El pico varía por onda; no se afirma que cada solicitud tenga25 escrituras competidoras.

Se conservan783 muestras:8 de preparación de catálogos,25 de calentamiento,200 escrituras medidas y550 lecturas de acompañamiento. Preparación, calentamiento y lecturas no entran en los percentiles de escritura. Método nearest-rank `ceil(p*n)`, sin descartar la cola lenta. La conexión observadora PostgreSQL es física, independiente del pool del backend, de solo lectura y autocommit; comprueba referencias, estados y recibos después de la respuesta. `fsync`, `synchronous_commit` y `full_page_writes` activos.

El tiempo HTTP incluye serialización cliente, loopback y transacción del servidor. El tiempo durable añade espera/SELECT de la conexión observadora. **No se midieron por separado latencia exclusiva de servidor o de red.** El resultado corresponde a una PC Windows y actores sintéticos, no a25 personas/equipos, redes distintas o producción. No se utilizó GPS físico ni correo externo.

## Carrera de retiro

[Evidencia](retiro-concurrencia.json): `PASS_ISOLATED_COMMITTED_RETIREMENT_RACE_SYNTHETIC_ONLY`. Recurso técnico nuevo exclusivo del dataset: evidencia mínima de un reto consumido ficticio, con fecha pasada declarada expresamente sintética. No se ejecuta recuperación SUPER_ADMIN ni se inscribe una clave.

- Un bloqueo previo del inventario provoca rechazo SQLSTATE55P03 sin consumir el recurso o crear ejecución.
- Dos ejecuciones del procedimiento Java normal, con JWT y reautenticación sintéticos, usan conexiones/transacciones independientes. La segunda espera, comprobado con `pg_blocking_pids`.
- Tras confirmar la primera transacción, la segunda retorna `replay=true` con la misma fecha y cantidad. Se comprueba una ejecución, una auditoría y un tombstone persistidos; recurso retirado.
- Ledger intacto. No había credencial SUPER_ADMIN inscrita: se comprueba que esa ausencia se conserva. **No se presenta como prueba de conservación de una clave inscrita.**

El caso demuestra la carrera de `RECOVERY_CASE_MINIMUM` y el bloqueo del procedimiento Java. No acredita por equivalencia todas las carreras de inserción de hijos de otras finalidades. Las regresiones previas con rollback se conservan como evidencia separada.

## Salida y comprobación independiente

[Retiro](retiro-selectivo.json), [postflight SELECT-only](postflight-solo-lectura.json) y [cierre](cierre-caso.json).

Las25 cuentas y20 conductores/unidades/grupos quedaron inactivos; todas las sesiones refresh revocadas y periodos retirados. Los100 servicios se cancelaron mediante API normal, conservando ofertas, recibos e historial. Cuatro aceptaciones y ledger1/4/20 intactos. El postflight vuelve a calcular ambos p95 desde las muestras y contrasta cada oferta/recibo conservado en PostgreSQL.

`TEST_CLEANUP_RELEASED=true`, marcador ausente y lock libre, comprobados después de terminar la JVM del ensayo. Backend4314 cerrado e instancia propia55439 detenida; directorio y datos conservados. No se ejecutó limpieza global ni se recuperaron cupos. No relanzar el dataset cerrado para nuevas pruebas.

## Fallos previos conservados

1. El lanzador intentaba volver a establecer el propietario de una carpeta privada existente; Windows exigió un privilegio adicional. Se sustituyó esa acción por verificación estricta del propietario y ACL ya restringidos.
2. El observador concatenaba `inet_server_addr()` y comparaba el texto con un host sin máscara. Se corrigió a `host(inet_server_addr())` y columnas separadas, conservando dirección/puerto/rol exactos. [Fallo antes de carga](diagnostico-observador-inicial/fallo.json); cero muestras antes de corregir.

La reanudación reutilizó exactamente las25 cuentas, cuatro organizaciones y mismas referencias, sin reenviar OTP, recrear participantes o resetear ledger. Las34 pruebas puras previas y11 dirigidas posteriores constan en preflight; no son34+11 casos distintos de producto ni sustituyen esta ejecución real.

## Huellas principales

- Medición: `6df1c7bd679c2684b827cca7d10710de4f396ad748272313f6ee11c5e7672ec3`.
- Carrera: `4d31df0d94923ba6f79641c18e41e9f5b5ffe354c54d5c65bea6d15fa1c34694`.
- Postflight: `9bb71805781a3883c0bb0a4e3d1a57e49081fa5b2e4f5f40fd53a6b54239819b`.
- [Fuentes](fuentes.sha256), [límites de trazabilidad](trazabilidad-fuentes.json). El sello del caso registra evidencias saneadas; no contiene la configuración o credenciales privadas.
