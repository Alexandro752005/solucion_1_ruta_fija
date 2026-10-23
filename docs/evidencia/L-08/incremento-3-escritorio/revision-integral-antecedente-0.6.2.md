# Revisión integral L-08 — Corte posterior a CAP25

2026-10-22 America/Lima. Contrato0.6.2. Este informe registra un avance verificado; **no es acta de cierre ni autorización de piloto**. [Matriz vigente de doce criterios](matriz-L08.md).

## 1. Estado de L-08

**EN_CURSO.** CI-L08-03 queda VERIFICADO_LOCAL_SINTETICO con el perfil25 aprobado. Los otros criterios conservan resultados parciales o dependencias; no se redujeron para cerrar el lote. [Caso de carga](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/README.md), [caso físico cerrado](conectado-a999ef52-d87f-498d-9251-1581cbb72562/cierre-caso.json).

## 2. Estado del piloto

**BLOQUEADO.** No existen acreditación de respaldo independiente/RPO horario, monitor independiente y alertas entregadas con cobertura/escalamiento24/7, preparación real de privacidad/participantes/títulos, aceptación propia de ADMIN legítimo o custodia/ensayo del titular real. También faltan las comprobaciones técnicas y físicas precisadas en la matriz. No se desplegó ni activó el piloto.

## 3. Cambios implementados

- `RetentionProcedure.execute`: advisory lock transaccional por plan **antes** de consultar `replay`, evitando doble clasificación/auditoría entre llamadas Java concurrentes. Sin conceder UPDATE sobre planes inmutables, sin migración y sin reemplazar el bloqueo SQL existente.
- `L08CapacityTrial`, `L08CapacityMeasurement`, `L08CapacityDatabaseSetup`, `L08CapacityRetirementRace`, `L08CapacityPostflight` y [lanzador](../../../scripts/Invoke-RutaFijaL08CapacityTrial.ps1): entorno cerrado a55439, dataset máximo1/4/20, flujos normales, medición HTTP/commit, carrera de retiro y salida selectiva. Fallos del arnés y su reanudación se conservan.
- Reconciliación de restauración: contraste reversible de casts literales, permisos implícitos exactos y colación original del hash. Se conservaron todas las huellas originales; no se repitió la restauración ni se ignoraron categorías.
- Probes de API V48 restaurada y candidato V50: clases de aplicación de cada JAR preservado/actual, sin mezcla de versiones; readonly, sin seed, schedulers, correo o migraciones.
- Corrección móvil anterior conservada: parche local AndroidPosition propaga sus indicadores medidos. Diagnóstico tipado y mensajes estáticos, manteniendo precisión, timeout15s y cadencia30s. No se repitió el ensayo físico ya acreditado.

## 4. Pruebas ejecutadas y evidencia

| Comprobación | Resultado real | Evidencia y límites |
|---|---|---|
| Retiro dirigido, `mvnw.cmd --offline -Dtest=RetentionProcedureSecurityTest,L08RetentionAdvisoryLockTest,PersonalRetirementIntegrationTest,RetentionIntegrationTest test` |92 correctas:9 unitarias,2PG readonly entre conexiones,81PG rollback | [Resultado](incremento-2-mision-y-controles/pruebas/retiro-serializacion/resultado.json). No sumar81 otra vez a ejecuciones previas. |
| Preflight CAP25, unitarias de medición/procedimiento/builder |34 correctas; después11 de medición tras corregir observador | [Archivo](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/preflight/resultado.json). Dobles y pruebas puras, separados del ensayo real. |
| CAP25 backend y PostgreSQL reales |100 creaciones+100 aceptaciones,0 errores,25 actores/pico HTTP25 | [Medición](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/medicion.json):p95 durable343,0684ms/461,8702ms. Una PC y actores sintéticos. |
| Carrera de retiro real con commit |Inventario55P03; segunda conexión espera; una ejecución/auditoría/tombstone y replay | [Resultado](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/retiro-concurrencia.json). Solo evidencia mínima de recuperación ficticia; no todas las carreras de hijos de otras finalidades. |
| Postflight independiente SELECT-only |PASS; cada oferta/recibo conservado y p95 recalculados | [Resultado](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/postflight-solo-lectura.json).25 cuentas inactivas, sesiones revocadas,4 aceptaciones,100 servicios/ofertas conservados, ledger1/4/20. |
| Snapshot V48 restaurado |Integridad SQL completa reconciliada, archivo GCM reautenticado | [Explicación y fuentes](incremento-2-mision-y-controles/restauracion/reconciliacion-integridad.md). Cero escrituras de diagnóstico. |
| API V48 restaurada y JAR V50 actual |Readiness conDB y liveness200UP; todas las huellas antes/después iguales | [V48](incremento-2-mision-y-controles/restauracion/arranque-v48-solo-lectura.json), [V50](incremento-2-mision-y-controles/candidato-v50-solo-lectura.json). No operación empresarial o RTO completo. |
| Auditoría npm de este corte |0 vulnerabilidades reportadas, exit0, lock intacto | [Resultado](incremento-2-mision-y-controles/pruebas/npm-audit-2026-10-22.json). No se ejecutó fix ni actualización; no certificación integral de seguridad. |
| Regresiones anteriores reutilizadas |112backend,241CRM,59Flutter y50 dirigidasGPS/analyze conservadas con sus solapamientos | [Incremento2](incremento-2-mision-y-controles/README.md), [GPS](conectado-a999ef52-d87f-498d-9251-1581cbb72562/correccion-gps/pruebas-y-fuentes.json). No se repitieron suites completas sin cambio que lo justificara. |

## 5. Login, GPS, batería y TalkBack

| Área | Estado | Fundamento |
|---|---|---|
| Login operativo físico |APROBADO dentro del caso sintético | Usuario entró al panel, declaró disponibilidad y cerró sesión; HTTP/PG contrastados. |
| GPS físico en primer plano |APROBADO dentro del caso | Consentimiento y permiso personales, captura visible,14 puntos del servicio, cierre y detención de envíos observada. No acredita todo el recorrido o llegada calibrada a terminal. |
| Batería |NO EJECUTADO como benchmark válido | El reporte de1% motivó pausa; no mide consumo. USB con carga no acredita descarga real. Falta criterio de aceptación autorizado y sesión/condiciones medibles. |
| TalkBack |PARCIAL | Confirmación humana del login público. Recorridos operativos, escala y lectura de controles restantes no acreditados. Consulta de disponibilidad de sesión/Wi-Fi pendiente, sin crear otra cuenta o exponer API enLAN. |

Caso físico RUN `a999ef52-d87f-498d-9251-1581cbb72562`: aceptación manual, cierre y archivo intactos. `TEST_CLEANUP_RELEASED=true`; no reabrirlo para completar otro criterio.

## 6. HTTP503

El rechazo público original provenía de un doble local deliberadamente indisponible, sin cuenta operativa o base. Su primera redacción podía parecer un rechazo de autenticación; el manejo corregido distingue503 de401 y de errores internos. El usuario confirmó el mensaje canónico «Ruta Fija no está disponible temporalmente…» y una única petición quedó registrada.

La secuencia de campos requeridos reportada después de503 no se reprodujo en los dos casos dirigidos; no se inventó una corrección. El ensayo original tuvo105s observados sin peticiones adicionales. No se extiende ese intervalo a ejecuciones posteriores. [Resultados humanos y técnicos](incremento-2-mision-y-controles/telefono/resultado-humano.json).

## 7. Monitoreo y recuperación

Caída/reinicio reales de la API local, con PostgreSQL real y monitor en otra JVM **de la misma PC**: detección2,804s y retorno de salud13,024s, cadencia acelerada1s. El error original al cerrar el journal se conserva; la salida cooperativa fue corregida y su regresión pasó. [Reconciliación](incremento-2-mision-y-controles/disponibilidad/reconciliation.json).

La restauración V48 ya tiene integridad SQL y arranque readonly acreditados. **No hay aún recuperación empresarial completa, RTO≤4h total, RPO≤1h, monitor independiente, alertas externas entregadas o cobertura humana24/7 acreditados.** No se reinicia el cronómetro de los fallos anteriores para declarar éxito. Las instancias propias55438 y55439 quedaron detenidas; sus datos se conservan y el servicio compartido5432 no se detuvo.

## 8. Versión y artefactos

**Versión liberada: ninguna.** Candidato local actualizado:

- [JAR V50](../../../artifacts/L-08-post-cap25-2026-10-22/manifest.json), SHA-256 `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`.672 entradas iguales a las clases/recursos compilados y arranque readonly del binario empaquetado correcto. `-DskipTests package` se registra como empaquetado, no prueba.
- APK debug aislado GPS: `artifacts/L-08-gps-correccion-2026-10-22/ruta-fija-l08-gps-debug.apk`, SHA-256 `378bedcf4ee12a1a801ed07c9479b1acc5903246dd78c6f9b40212af9a73a588`. Probado en el Poco; no release definitiva.
- Commit: no consultado ni creado por la restricción expresa de Git/GitHub. Identificación mediante huellas. Artefactos anteriores, términos publicados y final3 conservados.

## 9. Reversión

Se conserva el JAR L07 V48, SHA-256 `23a949ace14161e7e3d7110be31836cc03e26c1df59549fa78e303738d5abaa1`, y se comprobó su arranque sobre **su snapshot V48** restaurado. Esto aporta evidencia parcial de compatibilidad de retorno; no autoriza usarlo por inferencia sobre V50 ni acredita rollback empresarial completo.

Las migraciones aplicadas V49/V50 no se editaron; no se hizo downgrade. La aplicación Android anterior se conserva separada de `.l08` y los APK previos siguen archivados. No se declara que volver a un APK con el defecto GPS conocido sea una reversión funcional aprobada. La entrega/retorno completo se debe ensayar con sus datos, versión y condiciones autorizadas antes del piloto.

## 10. Bloqueos y siguientes acciones concretas

1. **Batería/lector/escala físicos:** confirmar disponibilidad de red local y sesión sin carga; definir el criterio de consumo antes de aprobarlo. Preparar caso complementario con autorización específica si requiere cuenta nueva; los casos retirados permanecen cerrados.
2. **Escritorio:** ejecutar zoom real y tablas/recorridos operativos Chrome/Edge en un caso autorizado, sin presentar zoomCSS como evidencia del navegador.
3. **Restauración/retorno:** preparar el ensayo completo compatible y su cronometraje desde detección, conservando ambos destinos actuales. No repetir restauración, sobrescribir bases o migrar una copia por inferencia.
4. **Entrega:** reproducción completa y preparación de firma bajo credenciales/condiciones autorizadas. No generar clave definitiva, publicar o desplegar automáticamente.
5. **Dependencias externas confirmadas pendientes:** disponer realmente de medio separado, host independiente y cobertura/escalamiento24/7; probar backups horarios/RPO, comprobación diaria, alertas y restauración. Completar privacidad, participantes/títulos y aceptaciones/custodia reales. No volver a pedir confirmación como si esos medios ya existieran.

CAP25 y el ensayo físico ya cerrados **no son pendientes que deban repetirse**. La matriz identifica qué parte de cada criterio permanece sin acreditar. Ninguna aprobación de este corte reduce esos requisitos.

## 11. Documentación y preservación

Actualizados contrato, matriz, ejecución, backlog, tablero, decisiones y `memory/current.md`/`target.md`, con respaldo previo en `memory/backup/2026-10-22-L08-cap25-acreditado`. [Preservación posterior](incremento-2-mision-y-controles/preservacion-post-cap25.json):144 archivos congeladosL07,36 incremento1,451 caso físico y12 protegidos coinciden, incluidos final3 y términos1.0.0.

Los dos casos cerraron mediante retiro selectivo con ledger separado conservado:5432 mantiene el histórico1/3/7 del caso físico; CAP25 conservó1/4/20 en55439. Sin limpieza global, recuperación de cupos, SMTP adicional, claves definitivas, Git/GitHub o despliegue. **No se emite acta de cierre L-08 ni se registra PILOTO ACTIVO.**

### Sellos y salida del corte

[CAP25:22 archivos congelados](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/manifest-caso-cerrado.sha256), SHA-256 `1a72d34aaeb94b67328dafbb92af57c0b5459808dbc2ebdbef0a08de6975fdcb`. [Incremento2:75 archivos congelados y1086 fuentes inventariadas](incremento-2-mision-y-controles/manifest-corte-post-cap25.sha256), SHA-256 `7091e94d1d3f26416ca4e9831202a2f8488aa4566b1a2f648fe056d47fc1afe2`. [Postflight global](postflight-global-2026-10-22.json):puertos propios cerrados, servicio PostgreSQL compartido iniciado y ambos casos conservados. El siguiente trabajo genera otro incremento y conserva estos archivos.
