# L07.4 — Retención por finalidad, procedimiento técnico v0.1.0

2026-10-21. AUD-L07-02 y REP-RET-L07-03 aprobadas. Estado de pruebas en la matriz L07; esta especificación no es constancia de purga ni de respaldo real.

## Política y consulta

Auditoría operativa: seis meses calendario desde la hora del evento en America/Lima; ajuste al último día del mes. En el instante del vencimiento queda fuera de listados/detalle ordinarios ADMIN y SUPER_ADMIN, incluso si no se ejecutó la purga. Un hold no reabre esa consulta. La adopción para legados se calcula sin reescribir el evento ni acreditar cumplimiento retrospectivo.

ADMIN de la organización solicita una excepción por cada ID de evento, caso UUID, motivo codificado y vencimiento; responsable técnico confirma con SUPER_ADMIN y reautenticación. Como máximo30 días desde el vencimiento original, una sola solicitud por evento, sin renovación automática ni hold de empresa completa. No protege registros ajenos ni prolonga puntos GPS. SUPER_ADMIN no recibe el contenido operativo mediante este procedimiento.

Emisiones: retirar contenido y archivos controlados a90 días del fin/cierre efectivo del piloto. No se registra ese hito por iniciar backend o pruebas. Lectura/emisión bloqueada al vencer; quedan referencia mínima, serie/versión, huella, corte y constancia. No se generan archivos derivados persistentes en servidor; descargar/corregir no renueva. Copias exportadas fuera del sistema no se declaran retiradas por esta herramienta.

## Plan exacto y ejecución

No hay endpoint HTTP de purga ni temporizador general. `RetentionProcedure` permite ADMIN autenticado/reautenticado con identidad y términos vigentes solicitar un plan de1…1000 referencias distintas de su organización. Contiene categoría, IDs, huellas y vencimientos originales, motivo/caso y huella de selección. La parte de plataforma se limita a eventos sin organización y requiere SUPER_ADMIN. El inventario separa las finalidades; no se asignan seis meses a toda tabla.

Ejecución técnica desactivada por defecto (`app.retention.technical-enabled=false`). Requiere autenticación SUPER_ADMIN, identidad, contraseña actual y confirmación de la huella exacta. PostgreSQL vuelve a comprobar rol, ámbito, contenido, hold, vencimiento y cantidad antes de retirar algo. Una excepción aprobada después del plan lo deja obsoleto; necesita otra selección, no un bypass. Ejecución repetida del plan terminado devuelve su resultado original. UPDATE de auditoría/núcleo de emisión sigue prohibido. DELETE solo se permite para filas de la selección exacta mientras corre su rutina en esa misma transacción/backend; autoridad privada sin permisos DML para runtime y sin variable de sesión reutilizable. No se deshabilitan triggers globalmente.

Las pruebas usan organizaciones nuevas en rollback y tiempo sintético; jamás purgan eventos históricos compartidos ni evidencia sellada. El arnés conectado mantiene su marcador/lock y no ejecuta purgas.

## Herramienta local

Entrada existente en el artefacto backend: `pe.rutafija.retention.cli.RetentionTechnicalCli`, mediante el cargador de Spring Boot `org.springframework.boot.loader.launch.PropertiesLauncher`. Exige consola interactiva y `-DrutaFija.retention.authorized=LOCAL_TEST_EXACT_PLAN`. Cargar únicamente la configuración Test validada mediante `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Test`; no imprimirla. Destino obligatorio127.0.0.1:5432/ruta_fija_test, rf_test, esquema46 o posterior validado. Flyway y mantenimiento automático desactivados. El marcador de cualquier ensayo pendiente impide usarla (se comprueba antes de arrancar).

Argumentos públicos implementados:

```text
request-plan CATEGORY PLAN_UUID CASE_UUID REASON_CODE EXACT_SELECTION ID_UUID[,ID_UUID]
request-platform-plan PLAN_UUID CASE_UUID REASON_CODE ID_UUID[,ID_UUID]
request-hold REQUEST_UUID EVENT_UUID CASE_UUID REASON_CODE UNTIL_ISO
approve-hold REQUEST_UUID
execute-plan PLAN_UUID SELECTION_SHA256
register-synthetic-copy COPY_UUID CATEGORY SOURCE_UUID MEDIUM_REFERENCE_UUID
attest-synthetic-copy COPY_UUID RETIRED|REAPPLIED_BEFORE_ACCESS PROCEDURE_UUID
check-synthetic-copy COPY_UUID
```

Categorías ejecutables: OPERATIONAL_AUDIT y REPORT_EMISSION. Token y contraseña se introducen solo en las entradas ocultas, nunca en argumentos o evidencia. `execute-plan` pide `EJECUTAR_PLAN_EXACTO`. Sin consola/autorización rechaza antes de abrir la aplicación. Su disponibilidad técnica no autoriza ejecutarla contra datos existentes ni habilita el piloto. El ensayo automatizado usa las mismas rutinas con fixtures rollback y distingue la comprobación de reautenticación con actor no persistido de la prueba SQL real.

## Copias y otras finalidades

Una copia controlada tiene medio/referencia, fuente/categoría y el vencimiento original; no una nueva duración desde copiar. Debe retirarse al vencer; una restauración no da acceso antes de reaplicar retiros. El inventario/atestado sintético prueba esa política; no crea una copia cifrada real, no demuestra cifrado, borrado físico, volumen de un piloto real, RTO o RPO. No presupone otro equipo/USB/nube ni modifica final3. Esas preparaciones siguen bloqueando uso real; restauración real corresponde a L08.

GPS máximo7d usa el mecanismo de L05. Expedientes, cuentas ADMIN/CONDUCTOR y HMAC/vínculos30d después del fin; aceptaciones de términos90d; recuperación mínima30d tras finalizar/cancelar caso mantienen políticas independientes. ADMIN solicita antes de perder acceso y técnico ejecuta; SUPER_ADMIN/custodia no se retiran por inferencia. El catálogo etiqueta expresamente las herramientas de esas otras categorías todavía no implementadas; no se declaran retiradas ni se prolongan por aparecer en auditoría. Son preparaciones pendientes antes de usar cuentas reales, conservadas en la matriz y sin reducir los criterios locales de auditoría/reportes.

V46 implementa el protocolo y V48 corrige un alias de su rama de retiro de reportes; las migraciones aplicadas anteriores se conservan. Retorno compatible conserva esquema, datos y política de lectura, desactiva la ejecución técnica y las rutas nuevas de reportes; no SQL inverso ni reinstalación de binario histórico.

## Contraste local posterior — 2026-10-21

[Acta L07](../actas/L-07-cierre-funcional-local-2026-10-21.md) y [matriz final](../evidencia/L-07/matriz-L07.md):verificación funcional local con sus límites. Las notas iniciales de implementación pendiente son históricas. No implica ensayo físico,habilitación del piloto o ejecución de purgas/copia reales.
