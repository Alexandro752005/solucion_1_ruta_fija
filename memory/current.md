> **Corte vigente 2026-10-23, L08.5 / contrato 0.9.0:** L-00 a L-08 VERIFICADAS en su alcance funcional local. L-08: contrato 0.9.0, FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL y GO PARA CANDIDATO LOCAL DE ENTREGA. Piloto real: NO_GO; condiciones pendientes separadas en el protocolo de transición. V50 con 51 migraciones; ledger 1/3/8, dos aceptaciones y 85 puntos históricos preservados; TEST_CLEANUP_RELEASED=true. APK release v5 firmada como candidata, con endpoint HTTPS .invalid sin servicio y sin instalar; POCO conserva debug v4. 56 pruebas de ubicación aprobadas, incluidas seis nuevas, analyze correcto y continuidad SQL aislada comprobada. Los cortes inferiores son antecedentes históricos.

> **Corte vigente L08.4/0.8.0,2026-10-23 America/Lima:** FIS02 cerrado y retirado selectivamente; una sola app principal v4 instalada y verificada. Batería 30→28% (2 puntos, 959s sin carga), GPS real y 85 puntos conservados; TalkBack humano, escala150%, negativas y contraste comprobados con límites. Ledger1/3/8 y aceptaciones2; TEST_CLEANUP_RELEASED=true; regla temporal, servidor y ADB TCP retirados. L-08 EN_CURSO; decisión NO_GO para aceptación integral/piloto: señal GPS sin fix físico, continuidad independiente, respaldo/RPO, firma de entrega y preparación real siguen pendientes. [Matriz vigente](../docs/evidencia/L-08/matriz-L08.md). Los cortes inferiores son históricos.

> **Estado vigente L08.3, contrato0.7.0,2026-10-22 America/Lima: L-08 EN_CURSO; piloto BLOQUEADO.** Escritorio nativo12combinaciones/66páginas/804comprobaciones acreditado; REST03 V50/105tablas y operación HTTP verificados, intervalo máximo local318,731s, sin equivalencia a pérdida física/RPO. FIS02 RUN3f0ba758-6eb9-4d2f-b948-dd1c7de3c430 activo y protegido: ledger1/3/8,aceptaciones2,TEST_CLEANUP_RELEASED=false; fixtures de base suspendidos. Login/disponibilidad/consentimiento/inicio/GPS reales y relato TalkBack operativo; escala grande y batería pendientes. Incidencia ABIERTA: APK inicial actualizó por error el principal; `.l08` corregido no restituye su binario anterior. Medios independientes/cobertura24/7/preparación real pendientes. [Matriz vigente](../docs/evidencia/L-08/matriz-L08.md). Cortes inferiores históricos; no autorizan limpiezas durante el caso actual.

> **Estado vigente,2026-10-22,contrato0.6.2:** CAP25 VERIFICADO_LOCAL_SINTETICO (p95durable343,0684/461,8702ms), carrera de retiro y salida selectiva comprobadas; cluster55439 detenido. Ensayo físico cerrado conservado. Snapshot restaurado íntegro y API V48/actualV50 readonly UP. Faltan los demás criterios indicados abajo; **L-08 EN_CURSO, piloto BLOQUEADO**. Cortes anteriores históricos.

> **Estado vigente,2026-10-22: L-08 EN_CURSO, contrato0.6.1.** Ensayo físico cerrado y retirado; `TEST_CLEANUP_RELEASED=true`. Integridad SQL del snapshot restaurado acreditada por reconciliación de solo lectura contra todas sus huellas originales. No acredita recuperación empresarial completa, RTO/RPO, batería, accesibilidad operativa o piloto. Cortes inferiores históricos. [Matriz vigente](../docs/evidencia/L-08/matriz-L08.md).

> **Corte L08-CON-01,2026-10-22: ENSAYO CERRADO Y RETIRADO SELECTIVAMENTE.** PASS_PHYSICAL_FOREGROUND_CASE_ONLY archivado con SHA13661ee0aa87196777bfe5e373dfdd7086c864a95db5c68b5ed449fd0af53e8b. Cuenta/conductor/unidad/grupo deshabilitados,todas las sesiones revocadas,periodo retirado; servicio completado y14puntos conservados. Ledger1/3/7,aceptaciones2 y hashes de antecedentes intactos. TEST_CLEANUP_RELEASED=true; lock libre,marcador/privado ausentes,proceso/puerto cerrados,solo reverseUSB4311 retirado.144L07+36incremento1+12protegidos y final3 verificados. Pueden reanudarse pruebas dirigidas con rollback, nunca limpieza global. Batería,TalkBack operativo,recorrido/terminal calibrado,carga25,RTO/RPO y condiciones reales siguen pendientes. **L-08 EN_CURSO; piloto bloqueado.** [Cierre del caso](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/cierre-caso.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22: PASS_PHYSICAL_FOREGROUND_CASE_ONLY acreditado.** SHA-256 exacto `13661ee0aa87196777bfe5e373dfdd7086c864a95db5c68b5ed449fd0af53e8b`; copia inmutable archivada antes de retiro. Usuario completó login,disponibilidad,aceptación,consentimiento,permisoGPS,inicio,captura real visible,cierre y logout; HTTP/PG contrastados,14puntos,servicio/tramo cerrados,ubicación vigente retirada. Cero envíosGPS durante62,693s observados tras cierre. Sesión utilizada revocada;1sesión anterior pendiente de retiro selectivo. Ledger1/3/7,aceptaciones2 conservados. TEST_CLEANUP_RELEASED=false hasta verificar salida del arnés. No acredita batería,TalkBackoperativo,recorrido físico/terminal completo ni participantes reales. **L-08 EN_CURSO; piloto bloqueado.** [Archivo](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/archivo-manual-previo-retiro/verificacion-archivo.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22T21:53Z:** APKdebugaislado corregido `378bedcf4ee12a1a801ed07c9479b1acc5903246dd78c6f9b40212af9a73a588` actualizado sin borrar datos. Corrección AndroidPosition preserva indicadores medidos;8testsoriginales reprodujeron6fallos,después50dirigidos y analyze pasan. Sin otras actualizaciones de dependencias, sin relajar precisión/timeout15s/cadencia30s. Teléfono envió ubicación HTTP204 y PostgreSQL registra1punto del mismo servicio; inicio original21:28:01Z conservado, cuenta/sesión conservadas, ledger1/3/7 y aceptaciones2. Falta confirmación visual humana, cierre/logout y retiro selectivo; MANUAL_PHONE_RESULT=PENDING, TEST_CLEANUP_RELEASED=false. Sin coordenadas en evidencia. L-08 EN_CURSO; piloto bloqueado. [Evidencia](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/correccion-gps/captura-fisica-tecnica-verificada.json). Cortes inferiores históricos.

> **Corte físico2026-10-22T21:20Z:** mismo caso reanudado2, oferta `6ad87efd-c9a5-4d06-943c-72c863f76772` emitida por HTTP201 y comprobada en PostgreSQL como PENDING_RESPONSE; servicio `961b62f8-1d30-469f-a88d-5db10884e663`. Corrección del arnés comprobada ahora también en emisión real de pruebas. Aceptación/inicio/GPS/cierre personales pendientes en este corte; consentimientofalse. Ledger1/3/7,aceptaciones2,marcador/lock activos y TEST_CLEANUP_RELEASED=false. Sin otra cuenta,envío externo o cambio de regla productiva. L-08 EN_CURSO; piloto bloqueado. [Evidencia](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/oferta-observacion-9a138f03-5d3e-474d-af56-3dc7278146fb.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22: caso pausado para cargar corrección del arnés.** POST /assignments rechazado400 por ambos extremos distintos a la ruta; cero oferta/servicio acreditado por lectura exacta. Se deriva el payload de la versión existente, se recupera primero un eventual commit y se bloquean nuevos envíos inciertos.14 pruebas puras pasan; ninguna prueba de fixture/base. Ruta/tipo/cuenta conservados, ledger1/3/7,aceptaciones2,marcador presente,lock de proceso libre,TEST_CLEANUP_RELEASED=false. Login/disponibilidad previos se conservan; emisión corregida y recorrido GPS aún pendientes. L-08 EN_CURSO; piloto bloqueado. [Evidencia](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/correccion-preparacion/resultado-verificado.json). Cortes inferiores históricos.

> **L08-CON-01 reanudado,2026-10-22.** RUN a999ef52-d87f-498d-9251-1581cbb72562, resumeCount1; marcador/lock activos, ledger1/3/7, aceptaciones2; USB restablecido. Login/disponibilidad conservados; oferta y GPS pendientes. TEST_CLEANUP_RELEASED=false, suites de base suspendidas; L-08 EN_CURSO y piloto bloqueado. Cortes inferiores históricos.

> **Corte físico 2026-10-22: L-08 EN_CURSO.** Login/disponibilidad HTTP200 acreditados; pausa por batería1% declarada (no benchmark). Caso a999ef52-d87f-498d-9251-1581cbb72562 conservado, ledger1/3/7, aceptaciones2, marcador activo, lock de proceso libre, TEST_CLEANUP_RELEASED=false; suites de base suspendidas. Sin GPS/servicio/oferta ni nueva alta. [Archivo](../docs/evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/pausa-bateria-archivo-v2.json). Piloto bloqueado; cortes inferiores hist?ricos.

> **Corte L08.2,2026-10-22 ? L-08 EN_CURSO,contrato0.6.0.**112backend(81PGrollback),241CRM,59Flutter/analyze;V50/51v?lidas. MonitorAPI real2,804s/13,024s en mismaPC,cadencia1s,sin independencia/24x7. Dos restauraciones55438conservadas;3huellas de esquema pendientes,sinRTO/RPO. Poco503/TalkBacklogin confirmados. Caso f?sico a999ef52-d87f-498d-9251-1581cbb72562 preparado:una alta normalCONDUCTOR,ledger1/3/7,aceptaciones2,marcador/lock activos,TEST_CLEANUP_RELEASED=false; **suitesdebase suspendidas**. Login/GPS/servicio/bater?a,carga25,privacidad/medios/custodia/infraestructura reales pendientes.144L07+36incremento1+12protegidos correctos. **Piloto/despliegue bloqueados;final3intacto. Cortes inferiores hist?ricos.**

> **Corte de autorización L08.2, 2026-10-22 — L-08 EN_CURSO, contrato0.5.0.** L08-REST-01 autorizado en instancia nueva55438 y L08-CON-01 autorizado para máximo un CONDUCTOR6→7, caso nuevo y correo simulado. Preparación/diagnóstico en curso; sin atribuir resultados físicos u operativos por la autorización. Medio de respaldo separado, host de monitor independiente y cobertura/escalamiento24/7 siguen pendientes por confirmación expresa. **Piloto/despliegue bloqueados; final3 y cierres históricos conservados. Cortes inferiores históricos.**

> **Corte L08.1, 2026-10-21 — L-08 EN_CURSO, contrato0.4.0.** Evidencia parcial del candidato y preautenticación física archivada. 238CRM completos antes del ajuste de foco; cuatro dirigidos y dos navegadores después; 29Flutter con dobles; 25backend (cuatro salud/PG,14política temporal con reloj sintético,siete seguridad/registro offline). APK aislado .l08 instalado con autorización; usuario confirmó formulario y único rechazo503,servidor/reverse retirados. Sin GPS/cuenta operativa. Npm audit0. V48/49,ledger1/3/6,aceptaciones2,144archivos L07 y12protegidos conservados. Carga25/p95,RTO-RPO/medio independiente,monitor/alertas/cobertura24/7,retiro por finalidad y recorridos físicos operativos/lector pendientes. **Piloto/despliegue bloqueados;final3 intacto. Cortes inferiores históricos.**

> **Estado vigente2026-10-21: L-08 EN_CURSO**,autorizado por OK VERDE después del cierre funcional local L07. Contrato0.1.0 y matriz abiertos. L07 preservado:148archivos/915fuentes/51artefactos verificados;test V48/49,ledger1/3/6,aceptaciones2,sin escrituras de base al iniciar. Trabajo:candidato/regresiones,seguridad,Chrome/Edge/accesibilidad,carga/RTO-RPO/Android físico según decisiones y medios disponibles. CAP-L08-01,DISP-L08-02,OPS-L08-03 consultadas;no objetivos acreditados por elección o dobles. **Piloto/despliegue bloqueados;final3 intacto.** Cortes inferiores históricos.

> **Estado vigente2026-10-21: L-07 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Contrato0.4.0,CI01…10 acreditados con límites:[acta](../docs/actas/L-07-cierre-funcional-local-2026-10-21.md),[matriz](../docs/evidencia/L-07/matriz-L07.md),[archivo](../docs/evidencia/L-07/cierre/README.md).190 backend distintos (157PG/MockMvc+33unitarios),22CRM/42Flutter con dobles,4Edge/3Flutter HTTP y13 etapas. Una alta CONDUCTOR autorizada retirada;ledger1/3/6,cupos0/1/14,aceptaciones2,V48/49 migraciones válidas,TEST_CLEANUP_RELEASED=true. Final3 y cierres anteriores preservados. Privacidad/retiro de otras finalidades,personas/custodia/infraestructura/canal externo/validación física reales pendientes;piloto bloqueado. **L-08 NO INICIADA/NO AUTORIZADA,OK VERDE separado.** Cortes inferiores históricos.



> **Corte técnico L07.2…4, 2026-10-21 — L-07 EN_CURSO, contrato0.3.0.**187 backend distintos del lote,22 CRM y42 Flutter con dobles; builds backend/CRM/APK y análisis correctos. V48/49 migraciones válidas solo ruta_fija_test, ledger1/3/5,aceptaciones2 y auditoría anterior conservados. Reportes/versiones/retención/resúmenes/inicio/preferencias implementados; falta caso TCP autorizado máximo1 CONDUCTOR5→6, carreras, retorno y retiro/postflight. Piloto bloqueado; L08 no iniciada/no autorizada. Cortes inferiores históricos.





> **Corte L07.1, 2026-10-21 — L-07 EN_CURSO, contrato0.2.0.** Auditoría aislada/metadatos seguros/denegaciones:88 backend (69 PG/MockMvc rollback+19 unitarias),6 CRM con dobles, builds backend/CRM acreditados. V44,ledger1/3/5,aceptaciones2 y auditoría282 filas/hash conservados. Caso L07-CON-01 máximo1 CONDUCTOR y REP-RET-L07-03 emisiones90d aprobados, todavía no ejecutados. Continúan reportes, retención, dashboard, móvil y recorrido conectado. Piloto bloqueado; L08 no iniciada/no autorizada. Cortes inferiores históricos.







> **Estado vigente 2026-10-21: L-07 EN_CURSO, autorizado por OK VERDE después de L06.** Contrato0.1.0 y matriz abiertos. Cierre L06 preservado:191 archivos/838 fuentes verificados; preflight ruta_fija_test/rf_test,V44 y45 migraciones válidas,ledger1/3/5,aceptaciones2. Sin escrituras de base/migraciones o altas persistentes nuevas en el inicio. Trabajar reportes/versiones, auditoría/retención e inicio/perfil por incrementos; conservar PII oculta y decisiones previas. Piloto bloqueado; **L-08 NO INICIADA / NO AUTORIZADA**. Los cortes inferiores son históricos.









> **Estado vigente 2026-10-21: L-06 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../docs/actas/L-06-cierre-funcional-local-2026-10-21.md), [matriz](../docs/evidencia/L-06/matriz-L06.md), [archivo](../docs/evidencia/L-06/cierre/README.md). Contrato0.3.0, CI01…10 acreditados localmente;72 backend/14 CRM/49 Flutter,6 etapas CRM/5 Flutter reales con correo simulado. Una alta CONDUCTOR retirada:ledger1/3/5,cupos0/1/15,aceptaciones2. Retorno/postflight y preservación correctos;TEST_CLEANUP_RELEASED=true. Final3 intacto. Canal push real/VT-Q074 y condiciones previas de piloto siguen pendientes; no se acreditan mediante dobles. **Piloto bloqueado; L-07 NO INICIADA / NO AUTORIZADA, OK VERDE separado.** Los cortes inferiores son históricos.











> **Estado vigente 2026-10-21: L-06 EN_CURSO, incremento1 acreditado en API/componentes.**72 backend distintos,14 CRM,49 Flutter; JAR/CRM/APK correctos. V43/V44 solo test, ledger1/3/4 y dos aceptaciones preservados. INC-L06-01/02 aprobadas. L06-CON-01 autorizado para máximo un CONDUCTOR nuevo; todavía sin ejecutar. Faltan conectado/carreras/retorno/retirada y cierre. Push real no configurado ni acreditado; piloto bloqueado y L-07 NO INICIADA. Los cortes inferiores son históricos.











> **Estado vigente2026-10-20: L-06 EN_CURSO por OK VERDE separado.** [Contrato0.1](../docs/contratos/L-06.md), [matriz](../docs/evidencia/L-06/matriz-L06.md), [ejecución](../docs/ejecucion/L-06.md). L-05 cerrado conservado:410 archivos/871 fuentes verificados antes de metadatos. PostgreSQL detenido; inicio rechazado, usuario informado; no preflight/escrituras/migraciones L06 todavía. Último ledger histórico1/3/4 y aceptaciones2, no observación actual. INC-L06-01/02 propuestos, no aprobados; continuar trabajos independientes. No reabrir casos/cuentas, sin SMTP/Git/final3 nuevo/piloto/L-07. Los cortes inferiores son históricos.











> **Estado vigente2026-10-20: L-05 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../docs/actas/L-05-cierre-funcional-local-2026-10-20.md), [contrato0.6](../docs/contratos/L-05.md), [matriz CI01..10](../docs/evidencia/L-05/matriz-L05.md), [archivo](../docs/evidencia/L-05/cierre/README.md). V42 solo test;79 regresiones backend finales (1 nueva; unión142 sin duplicados),16 CRM/16 Flutter finales;5 etapas CRM/Edge y9 Flutter HTTP reales con correo/sensor/permisos dobles. Relevo/interrupción, dos respuestas perdidas sin replay, carrera asumir200/retiro409 y retorno específico acreditados. Dos CONDUCTOR autorizados retirados; ledger1/3/4,cupos0/1/16,aceptaciones2; postflight independiente/lock libre/TEST_CLEANUP_RELEASED=true. No reabrir casos ni devolver cupos; sin limpieza global. Cinco sellos/12 inmutables/final3 conservados. Piloto bloqueado; L-06 NO INICIADA/NO AUTORIZADA, requiere OK VERDE separado. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, incremento5 de relevo verificado localmente.** [Contrato0.5](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md), [evidencia](../docs/evidencia/L-05/incremento-5-relevo/README.md). V42 test;141 backend/47 CRM/69 Flutter, analyze/typecheck/build/JAR/APK correctos. CI05/06 acreditados en API/componentes: tramos atómicos, pausa/inicio/planificación originales, GPS y atenciones separados, corrección multitramos; alerta inicio perdido. Ledger1/3/2 y aceptaciones2/casos cerrados/final3 intactos. L05-CON-01 AUTORIZADO, caso844b3bf6-f2fa-4666-bd0e-b1f7a1188696, máximo dos CONDUCTOR sintéticos (2→4 históricas), ADMIN existente y recursos nuevos; no ejecutado. Preparar/reanudar mismo caso, correo simulado, sin cuentas sucesivas ante fallo. Restan carrera HTTP/CRM-Flutter conectado/retorno/postflight/cierre. Piloto bloqueado y L-06 no iniciada. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, incremento4 de interrupción verificado localmente.** [Contrato0.4](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md), [evidencia](../docs/evidencia/L-05/incremento-4-interrupcion/README.md). V40 test;124 backend/36 CRM/59 Flutter y analyze/typecheck/build/JAR/APK correctos. SRV-INT-L05-05: cierre interrumpido sin cumplimiento, GPS detenido, autoría propia/ADMIN real y atención separada bloqueante, READY no reactiva unidad y retorno conserva MNT-01A/MNT-02A. CI04 verificado localmente; CI05 parcial multitramos. Ledger1/3/2 y aceptaciones2/casos cerrados/sellos/final3 intactos; cero altas persistentes, SMTP o limpieza global. Restan relevo atómico, carreras de negocio, alerta inicio perdido, conectado autorizado/retorno/cierre. No reabrir decisiones/casos. Piloto bloqueado y L-06 no iniciada. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, incremento3 de tiempos verificado localmente.** [Contrato0.3](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md), [evidencia](../docs/evidencia/L-05/incremento-3-tiempos/README.md). V39 test;46 backend/20 CRM/24 Flutter, analyze/typecheck/build/JAR/APK correctos. Correcciones ADMIN append-only, solicitudes propias y clientes sin cola; originales/GPS y ledger1/3/2/aceptaciones2 intactos. Carrera de autorización40P01 corregida, sesión revocada desde gate responde401. CI05 PARCIAL hasta varios tramos/terminales. SRV-INT-L05-05 aprobado: implementar interrupción propia o ADMIN comprobada con autoría real y atención separada bloqueante. No reabrir cinco decisiones aprobadas ni casos previos. Cero cuentas persistentes nuevas; final3 intacto. Restan interrupción/relevo, carreras de negocio, conectado/retorno/cierre. Piloto bloqueado y L-06 no iniciada. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO, GPS L05.4 acreditado localmente.** [Contrato](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md), [evidencia](../docs/evidencia/L-05/incremento-2-gps/README.md). V38 test;77 backend/38 Flutter/20 CRM, analyze/typecheck/build/JAR/APK correctos. GPS foreground30s sin cola, hora/precisión del sensor; aviso cierre, acceso propio verificado ADMIN48h/entrega≤15min y puntos/purga≤7d incluso incidentales. CI01/03/07/08 verificados localmente; pendientes02/04/05/06/09/10. Ledger1/3/2 y aceptaciones2 intactos, cero altas persistentes; final3/L-04 conservados. Cuatro decisiones L05 aprobadas: no reabrirlas. Interrupción/correcciones/relevo, carreras/conectados/retorno/cierre pendientes. Piloto bloqueado, L-06 no iniciada. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO, L05.1 acreditado localmente.** [Contrato](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md), [evidencia](../docs/evidencia/L-05/incremento-1-ejecucion/README.md). Inicio propio/consentimiento/contexto GPS, tramos originales y snapshot ligado a servicio; ADMIN no declara Ruta finalizada. V36 solo test;57 backend,18 Flutter,11 CRM/typecheck/build, analyze sin incidencias y JAR incremental. Ledger1/3/2 y aceptaciones2 intactos, cero cuentas persistentes nuevas; final3/L-04 preservados. GPS-ACCESS-L05-04 aprobado (ADMIN propio48h/entrega restringida15min dentro de7d); no reabrir las cuatro decisiones GPS/relevo. Pendientes interrupción/correcciones/relevo, captura30s/historial/acceso/purga, conectados/concurrencia/retorno/cierre. Piloto bloqueado y L-06 no iniciada. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO por OK VERDE.** [Contrato](../docs/contratos/L-05.md), [matriz](../docs/evidencia/L-05/matriz-L05.md). L-04 cerrado y preservado; piloto bloqueado, L-06 no iniciada ni autorizada. GPS obligatorio en todos los servicios; relevo asumido online por sustituto; puntos incluso de incidencias máximo siete días. V35 inicial, ledger1/3/2 y aceptaciones2 sujetos al preflight actual. Primera entrega: inicio y tramos, guardas de consentimiento/permiso y captura solo durante servicio. Sin nuevas cuentas persistentes por inferencia, SMTP externo, limpieza global o regeneración de final3. Los cortes siguientes son históricos.











> **Estado actual 2026-10-19: L-04 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../docs/actas/L-04-cierre-funcional-local-2026-10-19.md), [matriz](../docs/evidencia/L-04/matriz-L04.md), [archivo](../docs/evidencia/L-04/cierre/resultado.json). **L-05 NO INICIADA / NO AUTORIZADA**, requiere su propio OK VERDE. Piloto bloqueado. Los cortes siguientes conservan la historia.











## Cierre vigente L-04











- CI01…10 acreditados conforme al contrato0.3.0. Servicio programado separado de cada oferta; rechazo/expiración/cancelación conservan programación/vínculo/historia, pendiente de reasignación expresa. Conductor/unidad/ruta/horario nuevos requieren otra aceptación; cancelación motivada del servicio es otro acto. Inicio real bloquea sustitución; inicio previsto perdido admite reprogramación futura expresa.





- V35 solo `ruta_fija_test`, runtime rf_test/migrador explícito rf_migrator. Capturas/buffers, candidatos, bloqueo por empresa y exclusiones de recursos conservados; una sola oferta viva por servicio. Sin backfill de legados ni parámetros del piloto inferidos.





- 121 casos backend distintos:116 Spring/HTTP/PG rollback,4 política temporal,1 carrera nueva PG. Se reutiliza evidencia válida y no se suman reejecuciones; dos carreras V34 anteriores conservadas. CRM28/typecheck/build; Flutter15/analyze/APK debug y JAR correctos. No dispositivo físico, nueva intervención humana ni GPS.





- RUN conectado **bbd70822-8f52-45a6-b617-9b896ff90a2c**: Angular/Edge emite/reoferta/cancela servicio; repositorio Flutter acepta/rechaza por HTTP real y consulta ruta. Correo simulado. Retorno con mismas clases/operaciones deshabilitadas y rollback exacto; blocker específico comprobado aparte. Último borde de inicio previsto perdido cubierto por regresión nativa posterior, sin repetir el caso.





- Un único CONDUCTOR nuevo expresamente autorizado, ya deshabilitado: cuenta0794eb2a-bc9a-4334-b617-427bd7f28ef1, driverc4567be0-3d33-4674-8762-5429f68d1c46. Ledger **1/3/2**, cupos **0/1/18**, aceptaciones **2**. Se conserva la huella del ledger original excluyendo solo esa cuenta nueva. Casos cerrados L-01…03 no reabiertos.





- Cuenta/driver/unidad/grupo retirados, período revocado, tipo/ruta retirados, refresh conductor y tres familias ADMIN de prueba revocados. Dos ofertas e historial preservados, cero reservas vivas, archivo privado/marcador ausentes, lock libre y **TEST_CLEANUP_RELEASED=true**. La carrera técnica queda cancelada con recursos sin cuenta inactivos e historia/recibo retenidos. No limpieza global ni NativePostgresIntegrationTest.





- Doce huellas correctas: final3/términos/CLI/actas/ensayos previos y cuatro sellos L-03. Final3 intacto; no regenerado. Archivos históricos de evidencia L04.1/L04.2 conservados. [Artefactos locales](../artifacts/L-04-2026-10-19/manifest.json), sin despliegue.





- Piloto pendiente de ADMIN legítimo/aceptación propia, titular real/custodia independiente/ensayo, privacidad/identificación/protección/retiro, infraestructura/base/HTTPS/copias/restauración y preparación de participantes/recursos/títulos. S/0, sin offline/colas, SMTP externo, Git/GitHub, desarrollo/piloto o claves definitivas. GPS L-05 y calibración/dispositivo L-08.











> **Estado actual 2026-10-19: L-04 EN_CURSO por OK VERDE.** [Contrato](../docs/contratos/L-04.md), [matriz](../docs/evidencia/L-04/matriz-L04.md), [ejecución](../docs/ejecucion/L-04.md). L-01…03 conservados como cierres FUNCIONAL_LOCAL_PILOTO_BLOQUEADO. Piloto bloqueado; **L-05 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes conservan la historia.











## Corte vigente L-04 — Márgenes y candidatos











- L04.2 verificado localmente, continuación por OK VERDE sin iniciar L-05. [API0.2.0](../docs/contratos/L-04-margenes-API-v0.2.0.md), [evidencia](../docs/evidencia/L-04/incremento-2-margenes/README.md). CI01…04 verificados; CI05…10 pendientes en su integridad. L-04 EN_CURSO y piloto bloqueado.





- V34 solo ruta_fija_test, runtime rf_test/migrador rf_migrator. Tipos y márgenes configurados por ADMIN, versiones/capturas inmutables; reserva `[inicio−antes,fin+después)` para conductor/unidad. Ningún tipo/margen universal, ceros explícitos solo en fixtures; históricos sin configuración no se acreditan retrospectivamente ni se usan como margen cero.





- Candidatos por IDs propios y paginación, sin PII ni reserva implícita; escritura/aceptación/inicio revalidan documentos, período, mantenimiento y conflictos con buffer. Mantenimiento detecta margen cruzando medianoche. Varias futuras confirmadas compatibles conservan RESERVADO hasta retirar la última; un RESERVADO sin compromiso acreditado sigue rechazado.





- **101 casos backend distintos**:99 regresión final (18 nuevos de márgenes,14 ofertas,54 mantenimiento/elegibilidad,9 rutas/asignación,4 política) y2 carreras PG; CRM20/typecheck/build y JAR correctos. No sumar81 del corte anterior. Sin nuevo Flutter, navegador/TCP, dispositivo o ensayo humano.





- Carreras técnicas sin cuentas: recursos retirados exactamente; dos tipos inactivos con revisión/recibo conservados. Postflight independiente correcto. Ledger1/3/1, aceptaciones2 y hashes/casos previos intactos; ocho inmutables y evidencia del incremento1 conservados. No limpieza global, cuentas comprometidas, SMTP externo o nuevo final3.





- Siguiente trabajo: separación servicio/oferta y reasignación, cancelación expresa del servicio, cambios completos de conductor/unidad/horario con nueva aceptación, incertidumbre/integración CRM/Flutter y recorrido conectado/retorno/cierre. Sin reabrir decisiones ni solicitar otro OK VERDE para continuar dentro del L-04 ya autorizado.











## Corte histórico L-04 — Frontera de oferta/aceptación











- Oferta nueva solo MOBILE_CONFIRMATION, también por omisión; ADMIN_DIRECT explícito rechazado409 sin reescribir registros históricos. ADMIN no reserva/acepta/inicia por el conductor; móvil no inicia un legado directo sin aceptación auténtica.





- Deadline servidor min(emisión+10min,inicio); el campo cliente heredado no manda. V33 solo ruta_fija_test permite igualdad exacta con el inicio sin editarV7 ni relajar otras condiciones de ciclo de vida. Al crear otra reserva se normaliza expiración bajo el mismo lock de organización; repetir alta idempotente no renueva plazo ni revive oferta.





- Preflight/migración conservan ledger1/3/1, aceptaciones2 y huellas humanas/L-02. Regresiones con rollback; sin altas comprometidas, SMTP externo o limpieza global. Se adaptaron dos pruebas heredadas que legitimaban reserva/inicio ADMIN a los rechazos de la regla vigente; cobertura documental móvil se conserva.





- CRM emite oferta pendiente, no fija deadline, oculta reserva/inicio y edición material heredada, conserva formulario tras conflicto. Frontera backend/PG y componentes comprobados en el [incremento1](../docs/evidencia/L-04/incremento-1/README.md). Son pruebas sintéticas automatizadas, no intervención humana/dispositivo ni recorrido conectado nuevo.





- Próximo trabajo: configuración de márgenes por tipo y candidatos, separación servicio/oferta/reasignación, cambios de recursos/horario con nueva aceptación, incertidumbre/recorrido conectado/carreras y matriz final. L-04 no cerrado ni piloto habilitado. No repetir CI-L01-01/07, casos conectados cerrados, ni regenerar final3.











## Cierre histórico L-03 — Conservado











- CI-L03-01…10 acreditados: grupos múltiples/compromisos; flota/placa/vigencia/exclusividad; mantenimiento multifecha/urgencia/retorno; catálogo y captura/sustitución de rutas con nueva aceptación; consulta propia/declaración final y disponibilidad propia persistente. **MNT-01A/MNT-02A aprobadas**, no volver a preguntarlas.





- Esquema **V32 solo ruta_fija_test**, runtime rf_test/migrador rf_migrator. Flyway final válido. Ledger **1/3/1**, cupos0/1/19, dos aceptaciones y hashes humanos/L-02 preservados; cero altas comprometidas nuevas. Fixtures de regresión con cuentas/ledger se revierten con rollback, sin recuperar cupos.





- **97 casos backend distintos correctos**:87 Spring/PG con rollback,6 carreras con dos conexiones PG y4 geometría. CRM20/typecheck/build; Flutter28/analyze sin incidencias/APK. [Archivo/comandos/límites](../docs/evidencia/L-03/aceptacion-V32/README.md). No sumar reejecuciones. Últimos cambios de locks/caché cubiertos por suite final y JAR reempaquetado.





- RUN conectado **2e734706-f7ef-4947-b940-1f64fd13f86a**: navegador Angular/Edge real para rutas/preview/preventivo/cancelación y FlutterTCP real403 por rol. **No consulta móvil positivaTCP ni Android físico**; consulta cubierta por Spring/PG y modelos/widgets por separado. Smoke anterior al último endurecimiento de locks, cubierto por suite nativa posterior. No reabrir CONDUCTOR cerrado L-02 ni repetir este caso cerrado.





- Retiro selectivo verificado: unidad inactiva, ruta retirada/revisión conservada, plan cancelado, familia refresh revocada, marcador ausente/lock libre. **TEST_CLEANUP_RELEASED=true**. Historial y contadores conservados; **no autoriza limpieza global**, barrera histórica activa.





- Final3/términos/CLI/actas y ensayos manuales previos verificados por huella sin cambios. No repetir CI-L01-01/07. [Ocho inmutables](../docs/evidencia/L-03/aceptacion-V32/inmutables-preservados.json). [Artefactos locales](../artifacts/L-03-2026-10-19/manifest.json), no despliegue.





- Logger de pruebas corregido para no producir DEBUG de credenciales generadas. Logs/XML propios L-03 saneados con journal/hashes; primera pasada interrumpida por codificación sin journal completo, limitación explícita. No se reescribieron evidencias cerradas L-01/L-02 ni resultados históricos.





- Mantener requisitos reales de piloto pendientes: ADMIN legítimo/aceptación propia; titular real/clave definitiva/custodia independiente/ensayo; privacidad/identificación/protección/retiro; infraestructura/base/HTTPS/copias/restauración; participantes/unidades/títulos. S/0, sin offline/SMTP externo/Git/GitHub/desarrollo-piloto/claves definitivas/final3 nuevo. GPS L-05, dispositivo/calibración L-08.











## Corte histórico L-03 — Primer incremento de grupos











- Q-026 ya autoriza varios grupos por conductor; se implementaron membresías de conductores/unidades, sin crear vínculos ni cambiar permisos. Control de versión/idempotencia/historial y compromisos visibles antes de retirada lógica. API L-03 0.1.0.





- V28→**V29 solo ruta_fija_test**, runtime rf_test/migrador rf_migrator. Preflight/migración conservan ledger **1/3/1**, dos aceptaciones, huellas humanas/términos y caso cerrado L-02. Sin altas persistentes ni truncado; las regresiones L-02 sí crean cuentas/ledger sintéticos dentro de sus transacciones rollback, sin recuperar cupos comprometidos.





- **9** pruebas nuevas HTTP/PostgreSQL con rollback; **44** regresiones de elegibilidad correctas; **2** carreras L-02 y **2** nuevas de snapshots/retirada correctas. Las dos primeras ejecuciones de carreras L-02 rechazaron V29 antes de crear fixtures por su precondición histórica V28; se actualizó el test para el esquema aditivo autorizado y se preservó ese resultado fallido.





- CRM **5 pruebas correctas**, typecheck/build correctos. Son componentes con dobles, no navegador conectado nuevo. Pruebas nuevas de concurrencia son fixtures técnicos sin cuentas y retirada exacta, no incorporación personal ni mantenimiento acreditados.





- **MNT-01A/MNT-02A ya aprobadas** sobre planificación/retorno anticipado en [registro](../specs/decisiones-L03-mantenimiento-2026-10-19.json). V30 catálogo de rutas aplicado,7 HTTP/PG y6 CRM correctas. V31 mantenimiento/unicidad de unidad/declaración separada aplicada solo pruebas con preservación; pruebas e integración aún en curso. El primer intento V31 se canceló por lock de lectura del propio preflight, sin aplicar esquema ni repair; el arnés libera esa transacción antes del DDL manteniendo el lock de protección, y la continuación pasó. L-03 no está cerrada.











## Corte vigente L-02 — 2026-10-19











- Diez criterios locales acreditados: revisión separada, vigencias/renovación, suspensión/atención segura, corrección material sin sustituir persona, períodos y cardinalidad CARD-01, consulta móvil limitada y activación expresa. Incidencia automática de origen SYSTEM sin falso autor humano; no añade rol.





- PostgreSQL **ruta_fija_test**, runtime **rf_test**, esquema **V28**. V27/V28 aplicadas solo en pruebas; Flyway validado después de la suite. No desarrollo/piloto. Retorno con mismo binario y `app.qualification.operations-enabled=false`, conservando datos; no down migration.





- Backend **53 PASS** (44 HTTP, 2 carreras reales y 7 regresiones/guardia); CRM **20 PASS**, typecheck/build; Flutter **86 PASS, 2 optativas omitidas**, analyze/APK. [Archivo](../docs/evidencia/L-02/aceptacion-final-V28/README.md). Sin dispositivo físico ni nueva intervención humana.





- Caso conectado **44b3d67a-69ce-4169-89b4-62ebe8a40160**: navegador CRM y repositorios Flutter, HTTP/PG reales, correo simulado. Un solo CONDUCTOR nuevo, mismo ADMIN/organización sintéticos. Cuenta **3fb1f499-81d2-45dd-8e60-b53f6eb7ff79**, Driver **5712d0db-e92b-4988-9809-a49d04a194ef**.





- Limpieza selectiva comprobada: cuenta/Driver deshabilitados, sesiones revocadas, período revocado e historial/aprobación retenidos; archivo privado/marcador ausentes, lock libre. **TEST_CLEANUP_RELEASED=true**. Histórico **1/3/1**, cupos **0/1/19**, aceptaciones **2** intactas. **No reanudar limpieza global**: la guardia de históricos la bloquea; usar rollback o retiro selectivo verificado.





- CI-L01-01/03/07, términos publicados y final3 preservados sin repetir. La copia de la clave temporal CI07 sigue siendo de la misma PC, sin acreditar independencia. [Siete hashes](../docs/evidencia/L-02/verificacion-final/inmutables-preservados.json).





- Piloto bloqueado por aceptación propia de ADMIN legítimo, custodia/ensayo del titular real, privacidad/identificación y retiro/protección, infraestructura/base/HTTPS/copias/restauración y participantes/títulos reales. S/0, sin offline/SMTP externo/Git/GitHub/despliegue/claves definitivas. [Artefactos locales](../artifacts/L-02-2026-10-19/manifest.json).











## Cortes históricos conservados











> **Estado vigente al 2026-10-17: L-01 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../docs/actas/L-01-cierre-funcional-local-2026-10-17.md) y [matriz final](../docs/evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Los cortes anteriores siguientes se conservan como antecedentes. Piloto bloqueado; L-02 sin iniciar y sin autorizar.











# Memoria actual — Ruta Fija APF3











**Corte:** 2026-10-14, America/Lima





**Raíz:** `D:\UTP\CICLO X UTP\INTEGRADOR I\APF3\solucion_1_ruta_fija_3`  





**Hito:** L-00 VERIFICADA; L-01 EN_CURSO tras recibir `OK VERDE`. Gmail SMTP S/0 sigue siendo la solución provisional del piloto; la entrega real permanece pendiente. Dispositivo físico L-08.











## Proyecto observado











- Monolito modular backend Java 21 / Spring Boot 3.5.16 / JPA, PostgreSQL 16.15 y Flyway V1–V14 (V14 validada en pruebas; desarrollo no migrado); paquetes: `identity`, `organization`, `fleet`, `operation`, `audit`, `shared`.





- CRM Angular 22.1.4 y Android Flutter 3.47.5 / Dart 3.13.4.





- Código actual usa `SUPER_ADMIN`, `ADMIN`, `CONDUCTOR`. V6 conserva transformación histórica de datos; no es una autorización vigente.





- PostgreSQL de pruebas `ruta_fija_test` preflight aprobado; base de desarrollo no fue objetivo de prueba. No se imprimieron secretos.





- Login web/móvil, refresh y logout presentes. Auditorías de superficie encontraron gaps de invitación, OTP, recuperación, onboarding y provisión del primer ADMIN.





- CRM expone solo `ADMIN_DIRECT` al crear asignación aunque lista modos de confirmación móvil. Android cuenta con login, asignaciones, perfil, incidencias, comunicados y ubicación foreground.





- Código offline móvil, endpoint/contratos de sincronización y V12 existen en el checkout; v3.1 excluye offline. Preservar idempotencia de comandos inciertos y decidir el tratamiento de eventos locales antes de retirar.





- Perfil actual de ubicación permite compartir desde más estados que servicio iniciado; reconciliar en L-05.





- Trazabilidad inicial: 289 filas para 118 reglas (109 vigentes, 9 sustituidas) y 171 criterios (162 activos, 9 sustituidos); 271 activos y 18 sustituidos. El mapeo de carpetas es estático, no aceptación requisito por requisito.











## Baseline del 2026-10-13











- Backend: runner nativo preflight `PASS`; Verify `BUILD SUCCESS`, 57 pruebas (24 unitarias, 33 integración), cero fallos/errores/omitidas; Flyway V1–V12 validado.





- CRM: `npm.cmd run lint`, `test:ci`, `build` con exit 0; 17 specs/42 pruebas.





- Android: `flutter analyze`, `flutter test`, `flutter build apk --debug` exit 0; 41 pruebas; APK generado.





- Android SDK existe; licencias pendientes de aceptación; ADB sin dispositivo o emulador.





- Gmail SMTP fue seleccionado provisionalmente para el piloto el 2026-10-14. La cuenta emisora está bajo control de la organización por confirmación del usuario; gmail.com pertenece a Google y no se exige dominio propio para este piloto. No se integró SMTP, no se configuraron credenciales y no se envió correo. La elegibilidad de contraseña de aplicación está pendiente de L-01.





- Snapshot fuente: `backups/L-00-2026-10-13-final3/source-snapshot.zip`, 4.496 archivos, 292.096.103 bytes, SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`. Manifiesto SHA-256 `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`; extracción temporal y comparación de todos los hashes `PASS`.











## Método local y agentes











- Perfil de trabajo: Arquitecto/Tech Lead; mantener monolito modular, contratos backend/CRM/Android/DB, seguridad por rol y organización, un solo escritor para artefactos compartidos, cambios locales y evidencia reproducible.





- Se crearon seis perfiles prompt en `agents/`. Agentes temporales revisaron backend, CRM y Android sin editar ni ejecutar sus pruebas; coordinador ejecutó baseline.





- Trabajar solo en local; no ejecutar operaciones de Git, commits ni publicar a GitHub. Los cambios SMTP/OTP pertenecen al trabajo actual de L-01.











## Estado y bloqueos











- L-00: VERIFICADA el 2026-10-14. Inventario, matriz, baseline y snapshot final3 conservados; la decisión Gmail S/0 y la excepción de dominio están en `docs/evidencia/L-00/decision-Gmail-2026-10-14.md`.





- L-01: EN_CURSO. Login/OTP web y móvil, adaptador Gmail separado y V13 están implementados. OTP usa seis dígitos, cinco minutos, uso único, tres envíos/persona/60 min, dos fallos y bloqueo de 30 min; hashes persistidos. SUPER_ADMIN recovery backend y V14 están implementados: clave pública Ed25519 inscrita una vez, código de emergencia BCrypt, firma de reto temporal, verificación de correo nuevo, cambio de contraseña, rotación única de código y revocación de sesiones.





- Evidencia L-01: la regresión backend más reciente pasó `mvn verify`: 29 unitarias y 40 integraciones; Flyway V1–V14 en `ruta_fija_test`. CRM typecheck/tests 43/43 y Flutter analyze/tests 43/43 provienen de la validación anterior y no se repitieron en la última actualización. La suite incluye la regresión heredada `MobileOfflineSyncIT`, sin aceptación de funcionalidad offline.





- No se envió correo real. La suite de integración cargó internamente `backend/.local/ruta-fija-native.env` para obtener variables de PostgreSQL de pruebas; no mostró ni usó valores SMTP y el archivo no se modificó. Falta introducir localmente `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`, verificar elegibilidad de contraseña de aplicación, STARTTLS/recepción controlada y cuota S/0. No enviar la contraseña por chat, argumentos de comandos ni registros.





- L-01 conserva pendientes: alta/gestión de organización y usuarios por rol, aislamiento por organización, aprobación/registro de términos, invitaciones, recuperación del conductor y entrega SMTP. El borrador ADMIN v0.1.0 no está aprobado ni vigente. SUPER_ADMIN recovery está implementado y probado en backend; falta cliente operativo, decisión concreta de custodia separada de clave privada y prueba SMTP. Ver `docs/contratos/L-01.md`, `docs/ejecucion/L-01.md` y `docs/evidencia/L-01/decision-recuperacion-superadmin-2026-10-14.md`.





- OPEN-DEVICE-INVENTORY permanece asignado a L-08; no había dispositivo conectado. Recursos de piloto restantes se asignan a L-06/L-08. OPEN-SUPERADMIN-RECOVERY: backend probado; siguen pendientes cliente operativo de inscripción/firma, custodia concreta y validación SMTP. OPEN-RETENTION-PURPOSE y OPEN-MODULE-CONTRACTS se mantienen en sus lotes dependientes.





- L-02 a L-08 continúan POR_AUDITAR. No se iniciará ni se pasará a otro lote hasta solicitar y recibir del usuario una nueva señal literal `OK VERDE`.





- Snapshot vigente: backups/L-00-2026-10-13-final3/source-snapshot.zip. Se verificó nuevamente su SHA-256 y el del manifiesto; no se generó otro snapshot.





## Límites de certeza











Los resultados de prueba acreditan la condición del checkout en esta fecha, no aceptación integral V3.1. Suites móviles actuales contienen pruebas de cola offline heredada. No hay validación Android física, envío OTP real, piloto ni restauración de respaldo ejecutada como parte de L-00.











## Actualizacion inicial L-01 — 2026-10-14 (estado previo a implementar recuperación)











Se verifico presencia de las variables SMTP en backend/.local/ruta-fija-native.env y que el usuario coincide con el remitente autorizado. El importador cargo las variables al proceso; los valores no se imprimieron y el archivo no se modifico. La prueba controlada con SmtpOtpEmailSender fallo y la conexion diagnostica recibio AuthenticationFailedException; no hay entrega confirmada. El indicador local de formato esperado no sensible fue falso. Detalle: docs/evidencia/L-01/prueba-smtp-controlada-2026-10-14.md.











En ese corte estaban pendientes la provision de cuentas ADMIN por SUPER_ADMIN, terminos aprobados/versionados, invitaciones y recuperacion. El usuario definio el metodo independiente de recuperacion SUPER_ADMIN; la implementacion posterior y evidencia vigente se agregan a continuacion. El borrador ADMIN v0.1.0 fue preparado para revision; no esta aprobado ni vigente. L-01 no esta cerrado; L-02 no se inicio. El usuario corregira localmente la contrasena de aplicacion y avisara cuando este lista. No se solicita que comparta secretos por chat.











## Actualización de implementación — recuperación SUPER_ADMIN — 2026-10-14











El usuario confirmó cambio de contraseña y rotación del código de emergencia al completar, con nueva visualización única y conservación de la clave pública. Backend y V14 pasaron `SuperAdminRecoveryFlowIT` 4/4 y regresión completa `mvn verify` 29/40; PostgreSQL de pruebas quedó en V14. El historial detallado, comandos y límites están en `docs/evidencia/L-01/estado-tecnico.md`, `decision-recuperacion-superadmin-2026-10-14.md` y `recuperacion-superadmin-protocolo.md`.











Pendientes L-01: entrega SMTP controlada (no reintentada; Gmail rechazó la autenticación), cliente operativo de recuperación y decisión de custodia separada, provisión ADMIN, términos sin aprobar, invitaciones y recuperación de CONDUCTOR. CRM/Flutter no se volvieron a ejecutar en esta actualización. Snapshot `final3` se mantiene vigente. No iniciar L-02 sin solicitar y recibir `OK VERDE`.











## Actualización de continuación L-01 — 2026-10-14











Se recibieron y revisaron los documentos `Ruta_Fija_Cierre_L01`. El borrador ADMIN v0.2.0 se copió byte a byte a `docs/legal/terminos-admin-v0.2.0-borrador.md` (SHA-256 `1E8E73FBAAA5BF3B3C8AEBC3FEC4C30EC63A88F075336FE5B3D6845AD06B8334`); sigue sin aprobación ni vigencia. La matriz y la propuesta operativa quedaron archivadas en `docs/evidencia/L-01/`.











Se reforzó `UserManagementService`: ADMIN solo gestiona CONDUCTOR; no puede crear/listar/editar/activar/desactivar cuentas ADMIN. La edición general ahora rechaza el cambio directo de correo de CONDUCTOR para impedir el bypass de Q-015. Se modificó la prueba obsoleta de cambio de rol para que solo compruebe el rechazo de token cuando el rol persistido cambia; la autorización positiva del cambio por SUPER_ADMIN aún no está implementada.











Validación nueva: `backend: .\mvnw.cmd --batch-mode --no-transfer-progress verify` BUILD SUCCESS, 36 unitarias + 42 integración; PostgreSQL `ruta_fija_test` V14, sin migraciones nuevas. CRM `typecheck`, `test:ci` (43/43) y `build` exitosos. Flutter `analyze` sin incidencias y `test` 43/43. Las suites incluyen pruebas heredadas de offline, que no alteran la exclusión de offline en v3.1. Suite completa no acredita los flujos faltantes.











Preflight local SMTP no reveló valores y no usó red: el archivo existe, la contraseña está presente y su formato local es compatible con el patrón, pero `SPRING_MAIL_USERNAME` no coincide con el remitente permitido. No hubo intento ni envío. Para reanudar el único ensayo autorizado, corregir localmente el nombre de usuario y avisar; no enviar secretos por chat.











Un CLI Java de recuperación SUPER_ADMIN tiene 7 pruebas con clave temporal y API HTTP simulada. No se usó clave del titular ni API real. Sigue pendiente aprobar custodia separada. También están pendientes los flujos positivos de alta/reemplazo ADMIN, aceptación de términos, invitaciones y recuperación CONDUCTOR, más recepción OTP real. El borrador conserva datos del piloto sin completar. L-01 permanece EN_CURSO; `final3` mantiene SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`; L-02 no se inició.





## Continuación de L-01 — 2026-10-14, corte 2











El lote permanece EN_CURSO. No se inició L-02 ni se cerró L-01.











- SMTP fue revalidado sin imprimir valores: `backend/.local/ruta-fija-native.env` tiene una definición de `SPRING_MAIL_USERNAME` y una de `SPRING_MAIL_PASSWORD`; el usuario configurado todavía no coincide con `app.otp.email.allowed-sender` de `backend/src/main/resources/application-local.properties`. El importador Runtime carga estas variables al proceso y el perfil Test las omite. Al diagnóstico final no había backend en 8080 ni proceso Java Ruta Fija. No se conectó a Gmail ni se envió correo. Corrección local única: variable `SPRING_MAIL_USERNAME` en el archivo privado.





- Backend `mvnw verify` pasó con 46 pruebas unitarias y 62 de integración; Flyway migró solo `ruta_fija_test` de V16 a V17. Se usó `Import-RutaFijaNativeEnvironment.ps1 -Scope Test`. La integración de recuperación SUPER_ADMIN inicia Tomcat de perfil Test en puerto aleatorio y usa cuenta y claves temporales fuera del repositorio más correo simulado; no se creó ni custodió clave del titular.





- CRM: `npm.cmd run typecheck`, `test:ci` (22 archivos, 62 pruebas) y `build` exitosos. Flutter: `analyze` sin incidencias, `test` 47/47 reportado por el agente del incremento offline y prueba dirigida de ruta pendiente-sync 1/1 repetida por coordinación.





- Invitaciones backend/CRM permiten emitir/listar/revocar; código textual de 6 dígitos, HMAC, TTL 120 h y límites separados comprobados, con fronteras exactas. Q-018, aceptación autenticada, consumo transaccional/idempotente y modelo de solicitud/cuenta no están implementados.





- Recuperación asistida CONDUCTOR Q-015 pasa pruebas con doble de correo, mantiene email/contraseña/sesiones previas hasta completar cambio atómico y rechaza token vencido/reutilizado. El TTL de 30 min es provisional; cliente y retención de evidencia pendientes.





- Términos se prueban en perfil Test con documento explícito SYNTHETIC-TEST-ONLY; registra actor, organización, documento, versión, hash y hora del servidor. El borrador v0.2.0 no está aprobado, no hubo aceptación real y el backend no aplica un gate transversal a todas las operaciones ADMIN.





- El código offline legado permanece. El endpoint backend requiere perfil test y propiedad habilitada; la configuración normal lo desactiva. Flutter no registra la pantalla ni la acción; ruta desconocida regresa a la pantalla normal. El test MobileOfflineSyncIT es regresión Test, no capacidad de producto.





- Pendientes: discrepancia/entrega SMTP; alta y reemplazo positivos ADMIN; ficha y aprobación final de términos más gate de elegibilidad; aceptación Q-018; UI de recuperación CONDUCTOR, TTL y retención; custodia y ensayo del titular SUPER_ADMIN; prueba física L-08.











Snapshot `final3` sin regenerar: ZIP SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`; manifiesto de fuente SHA-256 `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`. El respaldo anterior de `current.md`/`target.md` quedó en `memory/backup/2026-10-14/` con huellas en su manifiesto.











Detalle técnico: `docs/evidencia/L-01/corte-incremento-2-2026-10-14.md`.











## Actualización L-01 — 2026-10-15











La ficha factual del titular fue contrastada contra el checkout y reconciliada con respuestas posteriores: máximo de 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR en el piloto; cinco organizaciones ficticias aún sin referencias ni activación; 2026-10-26 estimado, con cierre una semana después del despliegue; equipo hospedador en Lima; no hay base/túnel piloto activos, backup cifrado ni restauración probada. El titular confirmó ocultar globalmente datos personales en CRM/app. Los mapas van en lotes posteriores y offline permanece excluido. Ver `docs/evidencia/L-01/reconciliacion-ficha-factual-2026-10-15.md`.











Se implementó un gate backend de términos ADMIN en `/api/v1/**`, con excepciones para consulta/aceptación de términos y `GET /api/v1/auth/me`; compara aceptación con documento actual, organización, versión y SHA-256. La configuración normal queda activada por defecto y perfil `test` desactivado salvo prueba dedicada. `AdminTermsGateFlowIT` usa documento sintético y pasó 2/2 dentro de `mvnw verify`: 46 unitarias + 64 integraciones, BUILD SUCCESS, PostgreSQL `ruta_fija_test` V17 sin migración nueva. La suite mantiene 3 pruebas offline heredadas. Fuente estática: `MobileOfflineSyncController` requiere perfil `test` + propiedad `true`, desactivada por defecto; Flutter no registra la ruta/página.











SMTP fue revisado en memoria sin exponer valores: una definición por variable, `SPRING_MAIL_USERNAME` no coincide con el remitente aprobado; allowlist intacta, no había backend/listener 8080 y no se conectó ni envió. Única corrección manual indicada: `SPRING_MAIL_USERNAME` en `backend/.local/ruta-fija-native.env`.











L-01 sigue EN_CURSO; no iniciar L-02. `final3` no se regeneró. Pendientes: entrega SMTP real; invitación Q-018 atómica/idempotente; alta/reemplazo ADMIN; límites del piloto; ocultamiento global PII; aprobación términos; custodia/ensayo del titular SUPER_ADMIN; confirmar contacto legal distinto del emisor y función de aprobación “Transporte”; conectividad desde redes distintas no aclarada.





## Continuación de L-01 — 2026-10-14, corte 2











El lote permanece EN_CURSO. No se inició L-02 ni se cerró L-01.











- SMTP fue revalidado sin imprimir valores: `backend/.local/ruta-fija-native.env` tiene una definición de `SPRING_MAIL_USERNAME` y una de `SPRING_MAIL_PASSWORD`; el usuario configurado todavía no coincide con `app.otp.email.allowed-sender` de `backend/src/main/resources/application-local.properties`. El importador Runtime carga estas variables al proceso y el perfil Test las omite. Al diagnóstico final no había backend en 8080 ni proceso Java Ruta Fija. No se conectó a Gmail ni se envió correo. Corrección local única: variable `SPRING_MAIL_USERNAME` en el archivo privado.





- Backend `mvnw verify` pasó con 46 pruebas unitarias y 62 de integración; Flyway migró solo `ruta_fija_test` de V16 a V17. Se usó `Import-RutaFijaNativeEnvironment.ps1 -Scope Test`. La integración de recuperación SUPER_ADMIN inicia Tomcat de perfil Test en puerto aleatorio y usa cuenta y claves temporales fuera del repositorio más correo simulado; no se creó ni custodió clave del titular.





- CRM: `npm.cmd run typecheck`, `test:ci` (22 archivos, 62 pruebas) y `build` exitosos. Flutter: `analyze` sin incidencias, `test` 47/47 reportado por el agente del incremento offline y prueba dirigida de ruta pendiente-sync 1/1 repetida por coordinación.





- Invitaciones backend/CRM permiten emitir/listar/revocar; código textual de 6 dígitos, HMAC, TTL 120 h y límites separados comprobados, con fronteras exactas. Q-018, aceptación autenticada, consumo transaccional/idempotente y modelo de solicitud/cuenta no están implementados.





- Recuperación asistida CONDUCTOR Q-015 pasa pruebas con doble de correo, mantiene email/contraseña/sesiones previas hasta completar cambio atómico y rechaza token vencido/reutilizado. El TTL de 30 min es provisional; cliente y retención de evidencia pendientes.





- Términos se prueban en perfil Test con documento explícito SYNTHETIC-TEST-ONLY; registra actor, organización, documento, versión, hash y hora del servidor. El borrador v0.2.0 no está aprobado, no hubo aceptación real y el backend no aplica un gate transversal a todas las operaciones ADMIN.





- El código offline legado permanece. El endpoint backend requiere perfil test y propiedad habilitada; la configuración normal lo desactiva. Flutter no registra la pantalla ni la acción; ruta desconocida regresa a la pantalla normal. El test MobileOfflineSyncIT es regresión Test, no capacidad de producto.





- Pendientes: discrepancia/entrega SMTP; alta y reemplazo positivos ADMIN; ficha y aprobación final de términos más gate de elegibilidad; aceptación Q-018; UI de recuperación CONDUCTOR, TTL y retención; custodia y ensayo del titular SUPER_ADMIN; prueba física L-08.











Snapshot `final3` sin regenerar: ZIP SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`; manifiesto de fuente SHA-256 `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`. El respaldo anterior de `current.md`/`target.md` quedó en `memory/backup/2026-10-14/` con huellas en su manifiesto.











Detalle técnico: `docs/evidencia/L-01/corte-incremento-2-2026-10-14.md`.





## L-01: corte de incorporación Q-018 — 2026-10-15











Se incorporó una identidad preliminar independiente de AppUser/Driver, con OTP, contraseña hasheada, aceptación de invitación en una transacción y resultado PENDING_REVIEW. Las solicitudes se listan sin PII; el ADMIN verificado obtiene nombre/correo/teléfono solo por el endpoint de expediente de su organización y cada lectura queda auditada. La aprobación posterior requiere documentos aprobados y grupo activo; no se emite acceso operativo en este incremento.











La prueba dirigida en ruta_fija_test aplicó V18/V19 y quedó en V19. Backend compile/test-compile: BUILD SUCCESS. Backend verify dirigido: 46 unitarias y 4 integraciones dirigidas, sin fallos. Correo simulado; cero envíos externos.











Decisiones confirmadas: límite de creación piloto de 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR; agotados los cuatro ADMIN, no hay reemplazo. El postulante define contraseña preliminar sin acceso hasta aprobación; ADMIN elige grupo al aprobar. Falta cerrar la lista documental concreta y decidir almacenamiento de originales.











Detalle y límites: docs/evidencia/L-01/corte-onboarding-Q018-2026-10-15.md. L-01 continúa EN_CURSO; final3 no se regenera; L-02 no iniciado.











## Continuación Q-018 y cierre documental — 2026-10-15











Flutter ahora incorpora hasta PENDING_REVIEW: invitación, OTP, reenvío tokenizado y confirmación sin acceso operativo; backend separa identidad preliminar, no crea AppUser/Driver y rechaza login web/móvil. `ConductorOnboardingFlowIT` dirigido final: 46 unitarias + 4 IT; `flutter analyze` limpio y `flutter test` 54/54. La suite backend completa anterior pasó 46 unitarias + 68 IT en ruta_fija_test V19; offline solo apareció como prueba heredada de perfil test.











Q-003 hace obligatoria en L-01 la aprobación/rechazo documental; no se traslada a L-02. OPEN-REQUIRED-DOCUMENTS bloquea esa implementación: se eligió preparar un contrato versionado separado para conductor/vehículo, sin definir modalidad, lista de documentos, campos/vigencias/excepciones o conservación de originales. El grupo se elige tras aprobar; sin grupo no hay acceso operativo. CRM aún no integra el listado/expediente.











SMTP sigue discrepante: corrección manual únicamente en `backend/.local/ruta-fija-native.env` → `SPRING_MAIL_USERNAME`. El recheck no mostró secretos ni abrió red; no había Runtime local en 8080. No se envió correo. `final3` permanece intacto con SHA-256 ZIP `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B` y manifiesto `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`. L-01 sigue EN_CURSO; L-02 no iniciado.











## Estado vigente — continuación Chiclayo 2026-10-15











Este corte posterior prevalece sobre pendientes históricos anteriores; no los borra. Decisiones trazables en specs/aclaraciones-L01-Chiclayo-2026-10-15.json. DD-07…15 aprobados: taxi/colectivo Chiclayo, M1 documentado por modalidad/título (colectivo sedán/station wagon), base documental con POR_CONTRASTAR bloqueante, estructurado+cotejo visual sin copias y eliminación treinta días tras fin salvo excepción justificada; responsable ADMIN. No hay acreditación de títulos reales ni eliminación ejecutada.











OP-ADMIN-01/02 aprobados: pendiente inactivo consume cupo histórico; contraseña propia por credencial 24 h/un uso, OTP y términos antes de habilitar; sustitución verificada y revocación atómica/preventiva, sin imponer ADMIN único. Canal del proyecto rutafija.dev1@gmail.com, revisado cada dos o tres días sin disponibilidad permanente. Función del aprobador: titular/desarrollador/responsable técnico. Contacto operativo CONDUCTOR–ADMIN por número que ADMIN registrará en incidencias de su organización; aún no se proporcionó número ni se habilitó otro lote.











SMTP: la discrepancia nueva estaba en allowed-sender antiguo, no en el username privado actual. Configuración permitida corregida al remitente autorizado; un único envío aceptado y destinatario confirmó entrada. No se consumió ese OTP mediante HTTP/BD ni se repitió envío automáticamente.











Backend/CRM/Flutter tienen consulta scoped oa1, expediente ADMIN restringido, documentos personales, rondas/subsanación, aprobación/rechazo y provisión con grupo activo, sin conducción habilitada. Ledger de altas históricas global 1/4/20 y guardas bloquean bypass de altas/identidad. V25 mantiene presupuesto OTP y credenciales actuales al pasar de preliminar a AppUser. No equiparar correo único con unicidad física transversal.











Términos 0.3.0 preparados con datos confirmados; siguen BORRADOR, no aprobados/publicados/aceptados realmente. Persisten ensayo/custodia del titular SUPER_ADMIN, conservación efectiva y límites de privacidad/textos libres e identidad entre roles. Evidencia y resultados finales: docs/evidencia/L-01/incremento-Chiclayo-2026-10-15.md y matriz de cierre. No inferir éxito del checkout por el total de pruebas anterior.











L-01 EN_CURSO, L-02 NO INICIADA. final3 intacto; sin Git/GitHub, despliegue ni migraciones de desarrollo/piloto. Respaldo previo de esta memoria en memory/backup/2026-10-15-pre-chiclayo/.











## Relevo vigente — V26 y candidato para revisión completa, 2026-10-15











Prevalece este corte sobre los pendientes históricos de ID-01/PERSON-01 y RET-02. Ambas decisiones aprobadas expresamente; no repetir preguntas ni confundir identificación de persona con información del responsable del tratamiento.











- ID-01 implementado en backend/CRM: HMAC DNI común para tres roles, revisión manual restringida, gate obligatorio fuera de test aislado, enrolamiento propio SA con reautenticación/declaración, revisión ADMIN y regularización CONDUCTOR existente sin duplicar cuenta/cupo. Coincidencia con DNI legado impide nueva cuenta sin adoptarla automáticamente. Primer registro del conductor desactiva Driver/revoca; mismo DNI ya registrado es idempotente. HMAC vinculable no es anonimización irreversible ni verificación estatal.





- Solo ruta_fija_test en V26. Completa `l01-v26-final.log`: **56 unitarias +155 IT PASS**; dedicada gate identidad 10 y ambos gates 1. Posterior refuerzo documental `l01-document-privacy-final.log`: **56 unitarias +21 IT PASS**, tras corregir emisor omitido en fixture de intento fallido conservado. No afirmar suite completa 156 ejecutada ni sumar casos repetidos.





- CRM final: typecheck/build y **27 archivos/121 tests PASS**. Flutter analyze/74 tests reutilizados, sin cambios móviles posteriores. Dobles; no E2E UI conectado ni físico. Observaciones/motivos visibles no pueden repetir nombre revisado de otro requisito; texto libre de terceros desconocidos sigue límite explícito.





- Validador OTP interactivo `scripts/Invoke-RutaFijaL01OtpManual.ps1` preparado: `-Prepare` y chequeo por defecto pasan sin DB/SMTP; IT HTTP/Tomcat/PostgreSQL real con correo doble valida persistencia/consumo/limpieza. **No -Execute ni correo nuevo**. Entrega real anterior recibida sigue válida solo para transporte; consumo del OTP real pendiente. No reintentar automáticamente.





- Términos: elección «Función/canal técnico; privacidad separada antes del piloto». Candidato **1.0.0** `docs/legal/terminos-admin-v1.0.0-candidato.md`, SHA **48179D0B95DDDAE14363E9BCF006EFF1FC0B7484B296016A2647A2E74C09FCAF**. Ficha `docs/legal/revision-candidato-terminos-admin-1.0.0.md`, SHA **A07F8165964AD0B8E48CA95CFF94F24BFC3494AA554F178F3153202F08757167**. Usuario pide texto completo y ficha antes de decidir. **No aprobado/publicado/aceptado realmente; conservar bytes**. QA encontró que la ficha sobrestima la separación expresa persona/grupo/unidad en el texto: presentar observación fuera del candidato, eventual cambio exige nueva huella/revisión.





- RET-02 aprobado; procedimiento preparado/no ejecutado, no ampliar L-01 a toda purga futura. Privacidad/identificación separadas no preparadas, condición antes de datos reales. CLI recuperación empaquetado y probado contra backend test temporal; no generar/inscribir/custodiar clave real del titular.











Próximas dependencias humanas: revisión/aprobación de términos exactos, luego publicación local y aceptación expresa; validación manual de OTP real con guardas; custodia/inscripción/ensayo titular. Acreditar recorridos conectados faltantes sin confundir pruebas con dobles. Matriz única y evidencia: docs/evidencia/L-01/matriz-cierre-L01-revision-2026-10-14.md e incremento-Chiclayo-2026-10-15.md. final3 ZIP/manifiesto nuevamente iguales. L-01 EN_CURSO; no iniciar L-02 ni solicitar cambio de lote antes de cumplir criterios y OK VERDE posterior.











## Relevo vigente — T-03…06, candidato corregido y clientes conectados











Prevalece sobre el relevo anterior respecto a candidato, conexión y custodia; addendum0.8.0. T-03 confirmado: cinco referencias/al menos una sin operar por4 ADMIN históricos. T-04: HMAC/vínculos ADMIN/CONDUCTOR se retiran también a30 días del fin, IDs/historial mínimo/cupos preservados y excepción con finalidad/vencimiento; SUPER_ADMIN excluido. T-05: ADMIN identifica/solicita antes de baja, técnico ejecuta restringido/auditado sin reactivar permisos. T-06: inicio al habilitar y fin/cierre mismo acto siete días después. No repetir preguntas ni confundir estas decisiones con aprobación de términos.











Candidato canónico1.0.0 corregido SHA **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**; ficha SHA **5E702CD8BCB79DB4A9C01D00EA22466357C47737C5F96B55B9F8B6A4C8C2F9B3**. Anteriores48179d0b…/a07f8165… preservados en docs/legal/historico. Entregar nuevo texto completo/diferencias para aprobación expresa, NO publicar/aceptar por ADMIN. Aviso separado de privacidad preparado incompleto/no aprobado; datos reales/piloto no habilitados.











Lanzador scripts/Test-RutaFijaL01ConnectedClients.ps1 ejecutado completo PASS: build CRM + **57 unitarias/6 IT**, sin omitidas. Log backend/target/l01-connected-wrapper-final.log SHA **C9AED29D18338CC4333B9C9CFC73C0A2634B9D103B9A171893FDA18AE4B5FF60**. Navegador Angular/pantallas Flutter con HTTP/PostgreSQL reales: alta/activación/reemplazo, invitación/registro pendiente, devolución/subsanación/reenvío, aprobación/rechazo, grupo requerido/Driver inactivo, recuperación asistida sin sesión, denegación rol/tenant/CONDUCTOR CRM. Solo mail/almacén de dispositivo dobles. No físico/todos los módulos. Flutter analyze final limpio; CRM121/Flutter74, completa56/155 y posterior56/21 previas reutilizadas, no nuevas ni sumadas.











CLI check-key offline añadido y ocho unitarias CLI;3 IT nuevas contexto/caducidad exacta/concurrencia. Recuperación HTTP real con copia cifrada TEMPORAL prueba contraseña/correo nuevos, access/refresh revocados, código/reto consumidos, pública conservada y dos códigos mostrados una vez cada uno. JAR vigente SHA **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A**, solo CLI/JDK; guard sin consola exit2. NO generar/custodiar clave real: titular confirmó solo PC Windows; medio independiente/clave/inscripción/copia/código pendientes. Protocolo detalla pasos personales y lectura de copia sin API/SMTP.











OTP helper -Prepare/guardas PASS sin DB/SMTP. NO -Execute/envío real por agente. Solicitado al titular tras terminar limpiezasDB: ejecutar una vez interactivo y comunicar solo RUN_ID/RESULT/cleanup. NO correr arneses que limpien ruta_fija_test mientras él lo realiza. Q018 tiene recibo idempotente: repetir capacidad devuelve mismo requestId sin segunda verificación; negativas/reenvío consumido probados solo con doble, no modificar contrato para exigir4xx al recibo.











Pendientes obligatorios: CI01 consumo OTP real integral, CI03 aprobación/publicación/aceptación real, CI07 medios/custodia/inscripción/ensayo titular. CI11 conectado ya respaldado en sus casos. Matriz y evidencia canónicas actualizadas. Solo ruta_fija_testV26, sin migración nueva; final3 ZIP/manifiesto iguales. No Git/GitHub, despliegue, L02 ni solicitud de paso.











Respuesta posterior del titular sobre OTP: «Lo ejecutaré localmente y comunicaré solo el resultado saneado». No se ha comunicado RUN_ID/RESULT/cierre todavía; es intención, no evidencia de ejecución. Conservar CI01 integral pendiente y no ejecutar pruebas que limpien ruta_fija_test mientras realiza ese paso.











## Relevo vigente — OTP manual fallido y continuación selectiva, 2026-10-16











Prevalece sobre el aviso de intención anterior. Titular reportó RUN_ID **3e9c0bf5-237b-4370-a87f-cea684e0b1d8**: HTTP/SMTP/persistencia/recepción correctos; Enter vacío en entrada oculta produjo FAIL CONSOLE_OTP_INPUT / OTP_INPUT_FORMAT_REJECTED antes de verificar HTTP. Limpieza PASS y ledger preservado. Dos relanzamientos nuevos rechazados en PREFLIGHT por identidad preliminar existente; no emitieron ni crearon otro ADMIN.











Inspección JDBC **solo lectura**, sin Spring/Flyway/HTTP/SMTP, únicamente ruta_fija_test/rf_test y caso exacto. A las **21:40 Lima del 16** (2026-10-17T02:40:23Z) mantiene organización INACTIVE, ADMIN inactive, ledger propio/global ADMIN1, envío1 de21:15:30 Lima, send2/3null, erroresHTTP0, sin OTP/capacidad/correo verificado/consumo/solicitud/cuenta operativa/accesos activos ni recursos inesperados. **2 envíos disponibles** en ventana entonces; recalcular al ejecutar. El agente no alteró el caso real ni envió correo durante diagnóstico. Las inspecciones antes/después conservan el estado.











Arnés ampliado: `-InputSelfTest` sin configuración privada/DB/red; consola oculta explica ausencia de caracteres/asteriscos y permite hasta3 lecturas locales únicamente por vacío, conservando vencimiento original y máximo1 verificaciónHTTP real. Mal formato no vacío/cancelación/caducidad se detienen. Comprobación ConPTY real pasó con Enter vacío y seis cifras ficticias, sin mostrar la entrada, HTTP0/SMTP0/DBfalse.











`-InspectRunId` no modifica; `-Execute -ResumeRunId '3e9c0bf5-237b-4370-a87f-cea684e0b1d8'` prepara la continuación exacta después de sus guardas y confirmación local. Reutiliza identidad/ADMIN/ledger, **cero alta ADMIN adicional**; invitación nueva, anterior sigue revocada; ADMIN no se activa. POST ordinario conserva ventana/contadores, una emisión nueva. Imprime RUN_ID nuevo y CASE_RUN_ID raíz. No reset/DELETE/TRUNCATE ni preflight general relajado. Cleanup exacto ahora contrasta límites, expiración, accesos y ledger. Flyway desactivado solo en este proceso manual; JPA valida esquema existente. Lock asesor coopera con limpiezas estándar, sin autorizar correrlas durante el paso humano.











Dirigida final: **12 unitarias +4 IT PASS**, sin omitidas, `backend/target/l01-otp-recovery-final-2026-10-16.log` SHA **7E226CF798D347EB1127FCBB00DE3446A7B4D09B44CE328401D8E90780D8DCBB**. Nuevas IT NO heredan base que limpia; servicio/PG reales y mail doble, todo en rollback incluidos ledger/envíos sintéticos, sin Flyway ni datos confirmados. No atribuirles HTTP remoto/Gmail ni suma con regresiones previas. No se rerunearon los arneses de limpieza global. Wrapper AST y modo estático PASS; BOM UTF8 para mensajes PowerShell Windows. Evidencia y pasos: **docs/evidencia/L-01/otp-recuperacion-selectiva-2026-10-16.md**; guía anterior enlaza el corte nuevo y matriz CI01 actualizada. Spec0.8.1 registra resultado fallido, no aprobado.











**CI01 integral sigue pendiente de consumoHTTP/cierre del siguiente intento humano**. CI03 yCI07 siguen pendientes sin cambios. Candidato20FFD952… y final3 ZIP/manifiesto iguales; no nuevos términos/aceptaciones, claves reales, Git/GitHub, migraciones desarrollo/piloto, despliegue oL02. No ejecutar más pruebas con limpieza mientras el titular realiza la continuación; no auto-reintentar SMTP. Respaldo previo de memoria: memory/backup/2026-10-16-pre-otp-selectivo/.











## Relevo vigente — 2026-10-17: CI01 VERIFICADO, términos publicados, dos criterios abiertos











Este corte prevalece sobre pendientes históricos anteriores. **CI-L01-01 VERIFICADO**: titular comunicó PASS del run **fa616262-500e-4490-9292-7ee1927d2257**, raíz **3e9c0bf5-237b-4370-a87f-cea684e0b1d8**. Verificador JDBC solo lectura en ruta_fija_test/rf_test confirma claves de operación en desafío/solicitud, requeste0f3de05-5b8a-4f26-93d8-89e6ebc693dd PENDING_REVIEW sin provisión, consumo **2026-10-17T05:02:28.601315Z** a81,364s de emitir, tiempos atómicos de servidor y limpieza con ledger1/ADMIN inactivo/organización INACTIVE/OTP retirado/capacidad expirada/sin accesos activos. Marca reportada05:02:28.625877600Z es observación posterior24,5626ms, no timestamp exacto de fila. Antigua invitación revocada conservada; el envío viejo ya estaba fuera de60min al reanudar, ventana actual1 no es reset manual.











Evidencia durable fuera de target: **docs/evidencia/L-01/cierre-parcial-CI01-y-terminos-2026-10-17.md**; consulta principalSHA **F8D773D92A5457C14E5891CB4951226231E98385F24708DA1A510A79DAE397F2**, finalSHA **BF29E95475701A7D6012FE23C449DCF16231CBCDEC66C6249A497C2E4DC6DE0A**. Inspecciones del fallo anterior copiadas a otp-manual-historico sin alterar bytes. Wrapper nuevo Test-RutaFijaL01OtpEvidence.ps1 no tiene modo envío/limpieza;7unitarias pasaron y se incluyen otra vez en la dirigida20/1, no sumar ejecuciones. Arnés original de SMTP sigueSHA0B4B61B6…/wrapperAF696134…: no cambió para acomodar la evidencia.











**Suspensión de limpiezas test por CI01 levantada tras archivo**, bloqueo asesor libre en consulta final. Pueden reanudarse las autorizadas solo en ruta_fija_test; no se ejecutó limpieza global en esta interacción. Antes de futuros pasos humanos de aceptación/recuperación volver a suspenderlas. No repetir SMTP de CI01 ni reabrir discrepancia antigua sin evidencia nueva. Borrar fixtures mediante aislamiento test futuro no elimina la evidencia ya archivada.











**CI03: titular aprobó expresamente** «Apruebo ese contenido exacto para publicación local», SHA **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**. NO volver a pedir aprobación. Fuente en **docs/legal/publicado/terminos-admin-v1.0.0.md**, registro.aprobacion.json y manifiesto, sin sobrescritura/solo lectura; candidato/ficha previos intactos. Registro aprobaciónSHA **EA0348497927CE671A2A9CE91144226C4D835C38C9B2BC75697C3218132774B1**. Base únicamente ruta_fija_test tiene documento **f590e228-5d2e-47e4-b144-b0df1198bd49**, ADMIN_TERMS1.0.0, syntheticfalse, creado05:16:43.071422Z; hash PostgreSQL/contenido/versión vigente comprobados. **Cero aceptaciones ADMIN**, no se actuó por ellos. Sigue pendiente aceptación personal por ADMIN legítimo. Pregunta async enviada para saber si existe ADMIN preparado: no pedir DNI/correo/secretos por chat ni inventar cuenta real. Privacidad/identificación separadas siguen pendientes antes de nuevos datos reales.











Bug corregido en **AdminTermsDocument**: strip() recortaba contenido pero el hash tomaba argumento original. Se conserva texto exacto al validar no nulo/no blanco. Dirigida **20unitarias+1IT PASS**, logdurableSHA **5B9FFC6D14EE09F06B42701585ACDFB20E04D000AE26B5C24A702142F2345685**; IT documento sintético rollback-only comprueba CRLF/Unicode/hash/triggers UPDATE/DELETE, sin Flyway/limpieza/aceptación. No regresión completa nueva. Publish-RutaFijaAdminTermsTest.ps1 valida aprobación/bytes y solo testDB, rechaza colisión distinta y no crea cuentas/aceptaciones; default consulta sin escribir. Tras limpiezas test puede registrar otra vez el mismo aprobado con -Publish, sin inventar aceptación.











**CI07 sigue pendiente**: respuesta17 «siguen pendientes, por ahora solo tengo mi PC windows». No medios, clave/inscripción/copia/código/ensayo acreditados. Kit público **artifacts/ruta-fija-superadmin-titular-2026-10-17.zip**, SHA **7739912D5BB0732DA59E583A27E501ABD111EF287718CEB1BDABB053E82AC2AF**, solo JAR91913FF7…, guía/plantilla pendiente/manifiesto comprobados. No secretos ni configuración, ninguna generación/inscripción/recuperación real. No volver a preguntar algoritmo ni inferir custodia de otra carpeta del servidor; esperar medios reales y seguir protocolo personal.











Matriz, ejecución, tablero y spec **0.8.2** actualizados. **Solo faltan aceptación real CI03 y custodia/ensayo CI07**. L-01 EN_CURSO; L-02 no iniciada y no pedir OK VERDE de paso todavía. Ningún envío externo del agente, migración nueva/fuera de test, despliegue, Git/GitHub o cambio final3. Respaldo anterior de memoria: memory/backup/2026-10-17-pre-cierre-CI01/.











## Respuesta sobre ADMIN legítimo — 2026-10-17











El titular confirma **aún no hay ADMIN legítimo preparado** y que su cuenta prevista será **SUPER_ADMIN**. Resuelve la pregunta async anterior: no volver a pedir disponibilidad ni aprobación de términos ya concedida. Es rol previsto, no evidencia de cuenta creada/activa. No crearle ADMIN, asignarle doble rol ni aceptar por otra persona. CI03 requiere preparar a otra persona ADMIN legítima, sus condiciones de información/identidad y aceptación expresa; mientras no esté preparada, conservar pendiente sin sustituirla por fixtures.











Nota: docs/evidencia/L-01/confirmacion-ADMIN-y-titular-2026-10-17.md. Matriz/tablero/ejecución/spec0.8.3 reflejan la respuesta. Cambio solo documental, sin DB/SMTP/cuentas/claves ni pruebas de ejecución nuevas; evidencias archivadas y términos publicados intactos. CI01 VERIFICADO, CI03 yCI07 pendientes; solo PC Windows confirmada para custodia. L01 EN_CURSO/L02 no iniciada. Respaldo previo: memory/backup/2026-10-17-pre-confirmacion-roles/.











## Ensayos humanos sintéticos autorizados y preparados — 2026-10-17











El titular autoriza operar personalmente ADMIN y SUPER_ADMIN sintéticos; mantiene su rol real previsto SUPER_ADMIN. No representa otra persona real ni autoriza doble rol real. Autoriza claves/copia TEMPORALES en misma PC para ensayo técnico, no definitiva del titular ni custodia independiente. Leer guía **docs/operacion/L-01-ensayos-humanos-sinteticos.md**. Ambos recorridos siguen PENDIENTES de intervención personal; no declarar PASS por preparar herramientas.











Nuevo **scripts/Invoke-RutaFijaL01HumanTrial.ps1**: default inspección JDBC solo lectura, -Prepare compila sin DB/tests, -Smoke arranque backend real sin fixture y -Serve exige consola personal. Confirmado ruta_fija_test/rf_testV26, ledger0SA/1ADMIN/0CONDUCTOR; quedan1/3/20, cero ADMIN sintético pendiente elegible. Términos aprobados1.0.0/docf590e228… SHA20FFD952… actuales, aceptaciones0. Caso OTP finalizado NO reutilizar/alterar. Sin cuenta/claves temporales creadas aún por esta preparación.











Lanzador test únicamente: bootstrap de un SA sintético con contraseña oculta personal y una org ficticia activa, correo SA precomprobado como fixture; ID-01 se opera manualmente enCRM. Nuevo ADMIN por alta normal con cupo/credencial/contraseña/OTP/términos, no automático. CRM/proxy http://127.0.0.1:4311, backend interno8091. CLI apuntará al PROXY4311 para observar respuestas y revocación, no al puerto interno. Mail en memoria exactas3direcciones del caso, sin JavaMail/autoconfiguración, ambos gates activos, offline/seed/Flyway/schedulers deshabilitados, JPAvalidate.











**10 unitarias y arranque real smoke PASS**, sin fixture/migración/limpieza/SMTP. Evidencia durable: **docs/evidencia/L-01/preparacion-ensayos-humanos-CI03-CI07-2026-10-17.md** y carpeta ensayos-humanos-preparacion-2026-10-17/: unitariasSHA E84D31B26A3FE6739063FBA991365F22DAAAD4D0DBA85865CE92840511700B24; arranqueSHA B18106A544F6D985A14D4936C277A6138E71EF3B9714ADEEFE6F5153AFF30188; inspecciónSHA0F5C9583EEFCADF0A4CE2D70E1B3760C249E4F20C672C7B2A3F8EB59D166D044. No sumar con regresiones previas ni afirmar ensayo GUI/clave humano completado.











Al iniciar -Serve: lock existente7518440514 y marcador **backend/.local/l01-human-trial.active**. NativePostgresTestDatabase rechaza cleanup antes/después del lock si hay marcador. NO ejecutar limpiezas durante intervención, borrar marcador o resetear ledger. Console gate antes de aceptar debe409 por términos y401 sin sesión; admin-ok es confirmación humana, no aceptación automática. correo muestra soloOTPsimulado enconsola personal; sesion-cli token normal por login oculto para pegar enCLI, no compartir. generar/check-key/enroll/recover solo por titular con JAR vigenteSHA91913FF7…, usar copiaTEMPORAL fuera de app/repo pero mismoPC no independencia.











verificar archiva solo IDs/flags/huellas públicas/marcas servidor, sin secretos. Resultados esperados PASS_MANUAL_SYNTHETIC_ONLY y PASS_LOCAL_TEMPORARY_COPY_ONLY exigen fila/HTTP/gates/auditoría/confirmación/login, pública/códigos/reto/revocación. salir archiva otra vez y libera marcador propio únicamente si ambos PASS; fallo/interrupción deja caso pendiente protegido. Archivar/contrastar antes de reanudar pruebas; no aceptar por usuario ni generar clave definitiva. No preguntar medios otra vez: solo PC confirmada.











Spec0.8.4/matriz/tablero/ejecución actualizados. Contrato aún exige CI03 ADMIN legítimo y CI07 custodia independiente/ensayo legítimo. **docs/contratos/L-01-propuesta-validacion-funcional-local.md**, SC-L01-LOCAL-01 / SC-03/SC-07, borrador SIN APROBAR: revisar después de ambos resultados manuales para posible cierre funcional local con piloto bloqueado. No alterar silenciosamente AC ni cerrar originales por equivalencia. CI01 sigue VERIFICADO, no repetir. L01 EN_CURSO/L02 no iniciada; JAR/aprobados/manifiesto histórico/final3 intactos. Respaldo memoria previo: memory/backup/2026-10-17-pre-ensayos-humanos/.











## Seguimiento por etapas y aprobación SC recibida — 2026-10-17











**Cambio de decisión explícito posterior:** el titular YA APRUEBA SC-03 y SC-07 de SC-L01-LOCAL-01 para cierre **FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**, bajo condición de contrastar PASS_MANUAL_SYNTHETIC_ONLY y PASS_LOCAL_TEMPORARY_COPY_ONLY archivados y demás criterios. NO pedir otra aprobación equivalente dentro de ese alcance. Registro: **docs/contratos/SC-L01-LOCAL-01-aprobacion-condicionada-2026-10-17.json**; propuesta originalSHA453B44105D7E0548808CE86F2B0FAA5B18E31152A69469907E813A9958365F57 intacta como antecedente. **Aún no aplicada**: no hay resultados manuales ni RUN_ID acreditados; no formalizar adenda/acta antes de ambos PASS. Originales ADMIN legítimo/custodia real no cumplidos, permanecerán como requisitos bloqueantes del piloto cuando se aplique adenda. No despliegue/cuentasreales/clavesdefinitivas/correo/migracionesdesarrollo/piloto/L02 autorizados.











Solicita acompañamiento en etapas cortas1…8 con mecanismo existente, sin nuevo arnés. Leídos guía/lanzador/matriz completa/protocolo. Default inspección nuevamente sololectura a **2026-10-17T07:11:07.319886900Z**: test/rf_testV26, ledger0SA/1ADMIN/0CONDUCTOR, cupos1/3/20, ceroADMINsintético pendiente elegible; términos1.0.0SHA20FFD952… y aceptaciones0; al inicio no STATE, ACTIVE, JWTprivate ni listener4311/8091. No tocar casoOTP/contadores. CI01 no repetir. Términos/JAR/final3ZIP/manifiesto y manifiestos19/11archivos verificados iguales.











**Etapa2 solicitada por request_user_input_async**: ejecutar una vez `scripts/Invoke-RutaFijaL01HumanTrial.ps1 -Serve` desde raíz, terminal interactiva sin grabación/redirección, contraseña inicialSA 12…64ASCII oculta, mantener terminalA abierta, cargar4311/login; comunicar solo RUN_ID y si carga o error saneado. ESPERANDO esa intervención; no avanzar identidad/alta/aceptación ni keygen por él. Tras arranque comprobar protección: STATE y ACTIVE del mismoRUN_ID, lockPG, listeners loopback y login; solo luego etapa4 identidad90000001/fichaSYNTHETIC y altaADMIN40000001/parametrizada con guía. No preguntar secretos. Si falla conservarcaso, diagnosticar y no crearotro/repetir automáticamente.











**Revisión integral de12CI realizada**, documento **docs/evidencia/L-01/revision-integral-previa-SC-2026-10-17.md**: evidencia/entorno/resultado/límite porcriterio y lista única restante (dos ensayos; archivo/salida/ledger; formalización dependiente). Revisado mapeo oficial y PB01.1…5, Q003/documental NO trasladable. LogsV26completo56/155 y documental56/21 y CRM121 hashes coinciden; conectadoC9AED… coincide, Flutter analyze/tests disponibles y correctos; reutilizados, NO ejecuciones nuevas. No otro faltante técnico nuevo identificado en la evidencia, con límites de cobertura; no afirmar prueba humana por esas suites.











Spec0.8.5/tablero/matriz/ejecución registranaprobacióncondicional sin aplicarla, etapaespera y L01EN_CURSO/L02sin iniciar. Sin suites ni limpiezas ejecutadas. Protección NO se declaraactiva por preparación: comprobar despuésdel arranquepersonal. Respaldo previo memoria **memory/backup/2026-10-17-pre-seguimiento-ensayos-SC/**.











**Avance posterior de etapa3:** detectado marcador **140c10a1-be0f-4d08-a6d4-c796e2ed107a**, PID16512 escucha127.0.0.1:4311/8091. Default inspección07:15:43.335958300Z confirma **cleanupLockHeld=true**, ledger aún0SA/1ADMIN/0CONDUCTOR y aceptaciones0. No STATE del bootstrap; una consulta login con timeout3s no respondió: preparación inicial personal pendiente, NO atribuir a fallo de contraseña ni reiniciar. Se indicó completar entradas ocultas en terminalA ya abierta. Limpiezas ahora SUSPENDIDAS por este ensayo. No ejecutar -Serve otra vez o borrar ACTIVE. Verificar luego STATE del mismoRUN y login antes de guiar identidad/alta. Spec/tablero/matriz/revisión/ejecución reflejan este avance; ambasPASS siguen sin acreditarse.











## Bootstrap humano interrumpido — diagnóstico y continuación implementada











El titular reporta primer REFUSED_DETAILS_REDACTED al capturar/confirmar contraseña y segundo -Serve con ANOTHER_HUMAN_CASE_PENDING; no más intentos hasta diagnóstico. Inspección07:27:23.721351700Z **ruta_fija_test/rf_testV26**, mismoRUN **140c10a1-be0f-4d08-a6d4-c796e2ed107a**, marcadorSHA **83A91866767A8EFC56CBF7ED553191D53610F5A264E0E9A6C0D86CBBE79FF205** intacto. Proceso16512 terminado/sin listeners; Java4612 no es lanzador, no tocar. LockPGfalse por cierre, pero **standardCleanupRefusedByMarker=true**; limpieza sigueSUSPENDIDA. CuentaSA0/cupoSA0/orgcaso0/cuentascaso0/identidad0/credrecuperación0, STATE/SECRET ausentes, ledgerglobal0SA/1ADMIN/0CONDUCTOR y aceptaciones0 conservados. Ninguna contraseña preparada.











**Primera causa exacta NO REGISTRADA, no inferir confirmación distinta:** código antiguo agrupaba longitud/mismatch y ocultaba IArg, sin diario previo; otras fallas preescritura no pueden excluirse. Segundo rechazo síexplicado: sinSTATE/SECRET -Serve generabaotroUUID y chocaba conACTIVEoriginal. No solicitar contraseña/historia de terminal ni recrear el secreto.











Corrección mínima **en el mismo arnés test** (no API/protocolo normal): wrapper admite **-InspectRunId GUID** (read-only) y **-Serve -ResumeRunId GUID** (solo consola personal). Main --inspect-case y --serve --resume-case. Resume adopta exclusivamente mismo marcador yUUID si no hay cuenta/cupoSA/orgcaso/identidad/clave previos y hay cupoADMIN; no borra marcador/ledger, ni recaptura sobre credencial preparada. GuardaSTATEpúblico atómico y bootstrapHistory antes de pedir entrada, preservandohistorial; razones futuras PASSWORD_INPUT_CANCELLED/PASSWORD_LENGTH_INVALID/PASSWORD_UTF8_LIMIT_EXCEEDED/PASSWORD_CONFIRMATION_MISMATCH. Código saneado no incluyeentrada/hash/longitud. WrapperBOMUTF8Windows. Límite real12…72posiciones y máximo72bytesUTF8; **ASCII12…72**, la instrucción12…64 era recomendaciónmenor, no máximo ni causa comprobada.











**19 unitarias dirigidas PASS**, sin fallos/errores/omitidas;10previas+9nuevas, no sumar ejecuciones. LogdurableSHA **FCFBD835727AF4F8F8316B7859CC56A59688C20B271BF678489FBC845F1EFE83**. Inspeccionesbefore/after sololectura, elegibilidad **ELIGIBLE_SAME_CASE_NO_PREPARED_ACCOUNT**; afterSHA **6165DC79FEA2C01F04E1C12FD5302119563D897D5D5A0F0D78997F2745F1DDFC**. No captura personal o escrituraBD por agente, sin SMTP/Flyway/limpieza. No suitecompleta; guarda no interactiva del comandoResume y ASTguía5bloques pasan. Evidencia **docs/evidencia/L-01/bootstrap-recuperacion-140c10a1-2026-10-17.md**, carpeta homónima conlogs/copiaUUIDpúblico/fuentes. Antecedentesoriginales/codeprevio preservados.











**Siguiente paso personal exacto, ya compilado:** `.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'`. Titular introduce/confirmacontraseña oculta12…72ASCII, mantiene terminalAabierta, confirma mismoRUNylogin4311. No -Serve solo/nuevocaso, no sobrescribircredentials, no eliminarACTIVE. Sierror, código saneado y revisar, no autoreintentos. Después verificarprotección yseguiridentidad/alta, gateantesaceptar, etc. **Aún no reanudación humana/PASS**. Ambosensayos pendientes, L01 EN_CURSO, SCyaaprobadoscondicionalmente/noaplicados, originalesrealespendientespiloto, L02sininiciar. CI01/JAR/términos/final3intactos, sin correoreal. Spec0.8.6/tablero/matriz/guíaconcordantes. Respaldo previo: memory/backup/2026-10-17-pre-bootstrap-recovery-140c10a1/.











## Estado vigente posterior — ADMIN completado; gate ejecutado tarde











Titular reanudó caso **140c10a1-be0f-4d08-a6d4-c796e2ed107a**, revisó SUPER_ADMINsintético90000001 y creó ADMINsintético40000001/emailadmin.human.140c10a1@rutafija.test. IDs: SA088518c1-821c-4316-9f87-090f51ac44e0, organización1a759298-7831-4aaf-9299-c3920a9cc28a, ADMIN92ebc07c-1319-4850-a734-f792cb299979. No datos de identidad reales.











Diagnóstico **solo lectura2026-10-17T08:46:39.783406400Z test/rf_testV26** confirma contraseña08:36:45.327Z/correo08:38:55.179Z/aceptación08:39:10.296Z/activación08:39:15.743Z. Aceptación **c496b63c-735f-48cc-be56-90f7ec1464e1**, docf590e228…1.0.0SHA20FFD952…, cuenta/tenant/huella/hora/orden/auditoría correctos, ADMINactivoCOMPLETED con capacidadactivaciónrevocada. STATEobserva alta inactiva+acceptedtrueHTTP+login activado. No admin-ok/preAcceptanceGate archivados todavía. Titular confirmó expresamente que **gate** fue el comandoque produjo PASO_NO_COMPLETADO **después de aceptar/acceder**: guard EXPECTED_VERIFIED_INACTIVE_ADMIN_NO_ACCEPTANCE rechaza antes deHTTPpor aceptación1. NO inferir falloOTP o gatebypass. No repetirgate/correo ni revertir porSQL loaceptado.











**CI03local PARCIAL/PENDING:** aceptación manual sintética persistida acreditada técnicamente, falta declaraciónpersonaladmin-ok si corresponde y gate previo no observado. No alterar adminEvidenceComplete/preAcceptanceGate para producirPASSni equivalencia conlogincorrecto. SC03/07 siguen aprobadoscondicionalessinaplicar. CI07 aún sincredenrollment/ensayohumano. Piloto bloqueado, originalesADMINlegítimo/custodiaindependiente pendientes, L01EN_CURSO/L02sininiciar. CI01VERIFICADO,no repetir.











Ledger **1SA/2ADMIN/0CONDUCTOR**, cupos **0/2/20**. PID25932 sirve4311/8091loopback, lockPGactivo+marcadororiginalmismoRUN, TEST_CLEANUP_RELEASED=false. No detener/relanzar ni ejecutarlimpiezas. Fuente diagnóstica soloSELECTejecutada sinHTTP/Spring/SMTPescrituras/migraciones, archivadajunto alresultadoSHA **EA79C24154ED20DCD105F942DCB1BAF659DF9F89403D011067CA029C8A249921** en **docs/evidencia/L-01/gate-omitido-140c10a1-2026-10-17/**. Captura inicialstdoutincluía avisoCommonsLogging y parsefallido; preservadaloghistórico, JSONextraído mismasalida sinrepetirconsulta. Informe **docs/evidencia/L-01/gate-omitido-140c10a1-2026-10-17.md**. No código aplicación/arnés interactivo cambiado, ni tests/suites nuevos. Términos/final3ZIP/manifiesto nuevamenteSHAcorrectos. Guía/matriz/tablero/spec0.8.7/ejecución concordantes;backup7archivosenmemory/backup/2026-10-17-pre-gate-tardio-140c10a1/.











Siguiente intervención corta: terminalA **admin-ok** solo sioperadorleyóyaceptópersonalmente; luego **verificar**, conservarresultadoPENDINGyevidencias, no salir ni recrearcuentas. Elgatefaltante nopuede reconstruirseretroactivamente sinfalsificarhistoria: no crear automáticamenteotroADMIN/consumircupo ni reducirSC03. Cualquier continuación conotrocaso requiere concretarla y autorizarla; noreabrirdecisionesaprobadasdelmismoalcance.











## Archivo humano recibido y contrastado — observación1792227077200











Titular reporta MANUAL_ADMIN_RESULT=PENDING y archivo **docs/evidencia/L-01/ensayos-humanos-140c10a1-be0f-4d08-a6d4-c796e2ed107a/observacion-1792227077200.json**, SHA **9319F045F65FD4EE61B9A07EDEC3000E5EA6AC0E75618D99E2C4896843B8A719**. Hash calculado y lateral coinciden; mismoRUN/cuenta92ebc07c/org1a759298/aceptaciónc496b63c/docf590e2281.0.0hash20FFD952…/orden/auditoría válidos. **humanAdminConfirmation=true yaarchivado** a2026-10-17T08:51:17.141883900Z; no pedir nuevamente admin-ok/aceptación ni sobrescribirarchivo. Flagsalta/acceptedtrue/login true; gateprevioausente, ADMINresultPENDING. CI03localparcial **solo gatepreviofaltante**, no PASS, SC03sinaplicar. CI07PENDING, retos0/credencialfalse/copiadeclaraciónausente.











Observaciónafterledger1SA/2ADMIN/0CONDUCTOR y slots0/2/20/locktrue. Comprobación actualSO marcadorpropio y PID25932loopback4311/8091; protegercaso, TEST_CLEANUP_RELEASED=false, noreiniciar/limpiar/reenviar. Ninguna nuevaconsultaBD/HTTP/testomutación durantelcontraste; soloarchivopúblico/metadatos. JAR91913FF7… y términos20FFD952… verificadosiguales. Informe **docs/evidencia/L-01/contraste-observacion-1792227077200-2026-10-17.md**; actualizadosspec0.8.8/tablero/matriz/ejecución/estado. Backup6files **memory/backup/2026-10-17-pre-contraste-observacion-1792227077200/**.











**PropuestaREC-CI03-01 concreta**, todavíaSINautorización/ejecución: otroADMINficticio/identidaddistinta bajo mismoSA/org/RUN mediante adaptacióntestmínimadelmismoarnés; operadorrepitetramocompleto congateantesdeaceptar. Consume1de2cuposADMINdisponibles (ledgeresperado1/3/0, quedaría1ADMIN); preservarprimeraceptación ycasoOTP, noresetSQL/nuevoSA. Guíalimitóautorizaciónaloscasosprevistos: pedirsoloestaampliaciónnecesaria, noSC03/07yaaprobados. No comandosnuevosinventadosantesdeimplementar/probar. EntretantoCI07independiente puedeprepararsepersonalmenteconclavestemporales/copiaenmismaPC, noinscripciónmaterialreal; algoritmoyprotocolo vigentes. CI01VERIFICADO/L01EN_CURSO/pilotobloqueado/L02sininiciar.











## Instrucción vigente — NO autoriza REC-CI03-01; historial revisado sin gate suficiente











Titular **NO AUTORIZA porahora ADMIN sintético adicionalREC-CI03-01**, conservar cuenta/aceptación/historial/cupos. No volver a pedirlo por rutina ni implementarlo/crearlo por inferencia; no cambiarSC03 ni reabriraceptaciónporSQL. Ordena buscar evidenciaanteriorenregistrosmismoensayo ycontinuarCI07independiente.











Revisión completada **ruta_fija_test/rf_testV26 read-only**, consultas08:58:08Z y09:00:13.190769400Z. Mismocaso140c10a1, audit_event21 yadmin_activation_event6, NOsumarlecturas. Dos **LOGIN_FAILED ADMIN92ebc07c previos**:08:20:11.878Z y08:34:52.873Z, **metadatareasonINVALID_CREDENTIALS** efectivamenteextraídomediantevalorespermitidos; nopruebagatedetérminosni inferirmalcontraseña. Ambosanterioresaclaim/password08:36:45Z/correoverificado08:38:55Z. Aceptación08:39:10.296Z/auditoria08:39:10.300Z yactivación08:39:15.743Z; **LOGIN_SUCCESS ADMIN08:39:54.982Z** realposterior. No trazaHTTPdelrechazoterminespecífico ni GEToperativopermitidopersistido; login/screenshotnocuentanporHTTPprotegido.











Códigoinspectado: observerManualL01HumanTrialServer devuelveantesparano2xx, novierterechtx4xx; gateexplicito guard rechazóalhaberaceptación. AdminLifecycleService.complete sícompruebatérminosbackend409peroestático/probadoenotroscasosnotransformaobservaciónhumanafaltante. AdminTermsGateAuthorizationManager/JsonAccessDeniedHandler noviertenauditoríadelrechazo; GlobalExceptionHandleraplicaciónigual. Angularcomplete()retornasinHTTPsi!token/!verified/!accepted; frontendbloqueovisualnocuenta. Archivos10coincidenciasidentificadormásJSONsdeconsulta,nosHAR/traces/access/requestlogsenámbitosworkspaceinspeccionados; no perfilesbrowser/historiaterminal/env/privatekeys. Noclaimausenciafueraámbito.











Evidencia **docs/evidencia/L-01/revision-registros-gate-140c10a1-2026-10-17.md** ycarpetahomónima: JSONv2SHA **6906E24A75C1B5C222AEFDBB0D17E272EB1E12AD76175A37D6FEAD121F7DD7EF**, versión1previa0349041B…preservada, stdout/sourcev1/v2 e inventariofuentes. SoloSELECTs/rollback,nuevacuentas0/escrituras0/HTTP0/SMTP0; noregtests/códigoproducción/arnésinteractivocambiado. Originalobservación1792227077200 SHA9319F045…intacta/humanAdminConfirmationtrue. **CI03gateúnicopendiente**, NO PASS/SCaplicación. Sioperadorposeetrazaoriginalprevia, revisarsolométodo/ruta/status/motivo/tiempo/IDsnosecretos; no HAR/headers/tokensni simularpasado, noasumirqueexiste. Sinella gatependientehastadecisiónposterior, sincuentaadicionalocambiocriterio.











CI07independiente: sepidióporasyncsoloreporte **LOCAL_ENCRYPTED_KEY_CHECK principal/copia** ycoincidenciahuellaspúblicasdelapartado4guía, o si noejecutado/error. **AúnSINrespuesta**enestecorte; no inferirquehayclave/copia yno accederasusarchivosprivados. Despuésguiarsesion-cli/enroll/recoverusandocopia, correosficticios, no keysreales. Ledger1/2/0,slots0/2/20,locktrue/markerpropioactivo,TEST_CLEANUP_RELEASED=false. PilotooriginalADMINlegítimo/custodiaindependientependientes; CI01VERIFICADOnorepetir, SC03/07condicionalessinaplicar,L01EN_CURSO/L02sininiciar. Spec0.8.9/guía/tablero/matriz/ejecución/estadoactualizados;backup8filesmemory/backup/2026-10-17-pre-revision-registros-gate-140c10a1/.











Respuesta posteriorCI07recibida: operador ejecutóbloqueapartado4 y está en **Read-Host 'L01_HUMAN_RUN_ID mostrado en terminal A:'**. Se indicó escribir **140c10a1-be0f-4d08-a6d4-c796e2ed107a** yEnter, entrada pública. Luegocontraseñacifradotemporalmín12ocultasinasteriscos; élcompletabloquehastaamboscheck-key, detieneantesenroll. No pedirsecretos/copiaconsolecompleta. **Resultadoscheck-keytodavíano reportados**, nogeneración/lecturaverificada poralcanzarprompt. Board/spec/ejecución/informe reflejan etapaReadHost. CuandoenvíeresultadosconfirmarambosPASS/huellacoincidente yguiarsesion-cli/enroll conentradasprotegidas, nopruebarealSMTP.











AvanceCI07posterior: el operador reporta **«listo me otorgó los 2 LOCAL_ENCRYPTED»**. Registrar doslíneascomunicadas, sin inventarvaloresexactos ni igualdadhuellas (todavía no afirmada explícitamente). Guía/CLI revisados de nuevo yJAR91913FF7… igual; STATEsigueRUN140c10a1/adminPENDING/recoveryPENDING,sinenrollmentobservado; marcador/listeners4311/8091PID25932conservados. Próximapasocorto: comprobarlocalmenteambosLOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE yPUBLIC_KEY_SHA256idénticos; **solo si coinciden** terminalA sesion-cli conpasswordSA, terminalB existente `java -jar $trialJar enroll 'http://127.0.0.1:4311' $trialPrimary`. Clientepideporordenpasswordarchivoclave, tokenSAdeA, passwordactualSAdearranque. Entradasocultas, códigos/tokenningunoalchat. Parartrasenrollment,compararhuellapública yconservarcódigoúnicamentepararecuperaciónlocal, sin iniciarrecuperaciónhasta siguienteinstrucción. No inscripciónporagente/no keyreal/nocorreoexterno. REC-CI03-01continúaNOautorizado, gateúnicopendienteCI03/SCsinaplicar/L01EN_CURSO/L02sininiciar.











## CI07 — enroll401; credencial no inscrita y captura consola en revisión











Operador reporta401 delCLIenroll («no se imprimió cuerpo») y afirma haber obtenido/pegado solo token de sesion-cli. No causaexacta reconstruible: bodydescartado/observerdescarta4xx; no afirmar tokenexpirado ni contraseñamala comocausaoriginal. Reautenticaciónfail intenta auditoríamismatx quefalla; ausenciaeventopersistidonoexcluyearama. No alterar códigoapp por inferencia.











Inspecciones read-only **test/rf_testV26** a09:20:16Z/09:23:34Z/09:28:00Z: **sincredenrollment**, SA088518c1activo/correoverificado/orgnull/sessVersion0/hashpresent(boolean, no valor). Dos LOGIN_FAILEDsameSA a09:16:26.626Z y09:22:15.159Z conINVALID_CREDENTIALS. Usuarioejectuó una vezsesion-cli solicitado y volvióPASO_NO_COMPLETADO; confirmórecuerdacerteza contraseña. ComprobaciónindividualCRM http4311/login/sa.human.140c10a1@rutafija.test ingresóSA y **LOGIN_SUCCESS09:27:03.664Z** auditado. Contraseña/rol/cuenta funcionan sinreset; diferenciaentradaconsolaaúnporlocalizar. Angularnormalizaemailpero NO passwordtrim, arnésnewString(chars)sintrim; nointroducirtrim ni comparaciones realesoffline.











**Nueva intervención asyncpendiente**: tras éxitoCRM, revisarBloqMayús/distribucióndetecladoA, unasesion-climanualmenteconmismapasswordsincomillas/espaciosañadidos; devolver mensaje tokennuevooPASO, no token. **Si falla de nuevo, NO másintentosauth**: revisar capturaficticia sinDB/HTTP/SMTP. Existente scripts/Invoke-RutaFijaL01OtpManual.ps1 -InputSelfTest es SOLOconsolasintética6dígitos antesdeconfig, nocontactaDB/mail/HTTP ni reabreCI01; no sedio aún esa instrucción ni se ejecutó aquí. No diagnostica por sísolocapturaletras/símbolos. No nuevomecanismo/protocolo, cuentas,reset o keygenporagente.











Fuenteactual CLIvalidapareja privada/pública local antesHTTP; recibir401implicaque ese tramoocurrióen intento deloperador, peronoconfirmarhuellascopiaiguales hastadeclaración. CuandoobtengasesióndeSA válida, enrollelceroexistente unavezconmismo$trialPrimary, passwordcifrado/tokenoculto/passwordSA, firmaEd25519sin cambios. Luegorecovercopia conOTPsimuladoynewpwd. Privados permanecenfuera repo/app,noleerlos/agente,nopedirvaloresalchat.











Informe **docs/evidencia/L-01/ci07-enrollment-401-140c10a1-2026-10-17.md**, carpeta homónima conhistoria inicialSHA11BAC300… yestado-ownerSHA12A09A6B…, trasCRM **3746FCDD121E4D40483954DE55B6F8CE32677AF6D68DBBFCC8C3A074D090ACAC**. SELECTs/rollback sinwritesHTTP/SMTP. Ledger1/2/0,slots0/2/20,1aceptaciónADMINvigente,lock/markertrue/PID25932loopback4311/8091; TEST_CLEANUP_RELEASED=false. OriginalhumanSHA9319F045…intacto; CI03gatefaltanteyREC-CI03-01NOautorizado. Spec0.8.10/guía/tablero/matriz/ejecución/estadoactualizados;backup8filespre-ci07-enrollment-401. SCcondicionadosinaplicar/L01EN_CURSO/pilotobloqueado/L02sininiciar. No suites/nuevasmigraciones/reset/clavesrealessnapshot/Git.











**Último avance diagnóstico de consola:** operador informó varios Comando no reconocido en **EnsayoL01>**. **NO reproducir ni guardar sus textos de entrada.** Se preguntó si eran contraseñas y respondió **No**; no presumir exposición ni restablecer porinferencia. Snapshot09:31:19.653Z (archivoestado-tras-ultimo-error, SHADDBD0519…) conserva2fallosprevios/éxitoCRM, ningúnnuevoeventologin traséxitoCRM; mensajesdecomandos inválidos noequivalenafalloAPI.











Operador ejecutó exclusivamente **scripts/Invoke-RutaFijaL01OtpManual.ps1 -InputSelfTest**, terminalCPSroot, Entervacío+6dígitosficticios: **MANUAL_OTP_INPUT_SELF_TEST=PASS reportado**. Solo capturalocal; DB/API/SMTP0, nocaso/CI01reabierto, norealpwdprobada. No afirmarcapturasímbolos/passwordrealpor6dígitos. Código actualmodoantesconfigrevisado; javap no disponibleenPATH (no se afirma inspección bytecode), no nuevas pruebas suite u Otpdelivery.











Luego se pidiósólo`sesion-cli`unEnterenA, **SINpasswordtodavía**; operador confirmó prompt exacto **«Contraseña actual del SUPER_ADMIN sintético (entrada invisible):»** esperando. **Asyncactualpendiente:** ahora introducireneseprotegido la mismapasswordSAqueCRM yEnteruna vez; devolver sólo mensaje tokennuevooPASO, SINVALORES. No enrolarhasta tenerloginválido. No usarvariablespublicadasenchatni pedirpwd. Siobtienetoken, guiarB`enroll`con$trialPrimary actual (passwordarchivo/token/passwordSA), verificarpúblicaycódigounavez; si falla pedir sólo error saneado ydiagnosticar, no nuevosauthautomáticos. Caseyledger/intactos, CI03gatependiente/REC-CI03-01NOautorizado/CI07PENDING/SCsinaplicar/L01EN_CURSO/L02sininiciar.











**Respuesta recibida, consola autentica ya:** traspromptocultoconfirmado, operadorreportó mensajecontokennuevo. Read-only09:38:40.238Zverifica **LOGIN_SUCCESS SA09:37:09.373Z**, mismaID088518c1/role/case, recuperacióncredencialtodavía0. Archivo **estado-tras-sesion-cli-solo-lectura.json** en carpetaci07-enrollment-401..., SHA **08E0B6C02FBFCB9B1F8D085ADCBE361ED420FB7E496573D0E9DE5B5F33916CC7**. No másrepeticionesloginnecesarias antesenroll, tokenfresco a09:37. Noatribuircausaexacta401originalsincuerpo/correlación; intervenciónprocedimientopromptresolviólogin, código/cuentas/contraseña/keysinmutaciones.











**Asyncahorapendiente de inscripción controlada:** BmismaPowerShellconvariables$trialJar/$trialPrimary: `java -jar $trialJar enroll 'http://127.0.0.1:4311' $trialPrimary` unavez; inputsocultospororden**passwordcifradoarchivo → sólotokenA(sinBearer/comillas/mensajes) → passwordSAqueacabódefuncionar**. Pararantesrecover, compararhuellainscritaconamboscheck-key, mantenerBabiertacódigoemergúnicoparasiguientepaso. Reportarsolo«Clave públicainscrita yhuellacoincideconambas»oerrorsaneado. NO rawtokens/códigos/contraseñasalchat/evidencia. SiinscripciónPASScontrastarSTATEenrollmentResponseCount1/publicKeyFingerprint/sessionVersionBeforeRecovery0 yDB; luegoanticiparflujoenterorecovercopia(condreto5min) sinpausaschatentremedio: emails sa.human.140c10a1@rutafija.test→sa.recovered.140c10a1@rutafija.test,passwordcopia,Ssiinstalación/cuenta/correocorrectos,códigoemergoculto,EnterSINreenvío,AcorreoOTPsimulado,BOTP/newpwd12…72ASCII/confirmar, nuevocódigo1vez, loginCRMnewcorreo+newpwd, Acopia-ok/recuperacion-ok/verificar. Conservar AdminPENDING/marker porquegateNOcomprobado, no saliryforzarlimpieza. SCsinaplicar/L01EN_CURSO/L02sin iniciar.











## Prioridad actual — CI07 inscrito y contrastado, recuperación humana pendiente











El operador respondió: clave pública inscrita y huella coincide con principal/copia. Inspección SELECT2026-10-17T09:44:17.040007200Z confirma en ruta_fija_test/rf_test/V26 una credencial de SA088518c1 del RUN_ID140c10a1-be0f-4d08-a6d4-c796e2ed107a. Enrolled09:40:47.738Z; un audit SUPER_ADMIN_RECOVERY_ENROLLED09:40:48.051Z. Instalación f0cd888b-57df-4def-9d08-7882ffadebae; pública SHA5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c igual al observadorHTTP, enrollmentResponseCount1/sessionVersion0. Copias solo declaradas/leídas por humano, no por agente. En ese corte aún cero retos/completados. No inferir PASS de recuperación por enrollment.











Nueva evidencia docs/evidencia/L-01/ci07-inscripcion-verificada-140c10a1-2026-10-17.md y carpeta con SELECT JSON SHA08789DFBA7CEE65B5653994AA8520CF68911222CEEF6FD3B90E3AD908BF6FCBB, fuente/salida saneadas/manifiesto. Diagnóstico401 previo/manifiesto intactos, causa exacta original sin probar. Ocho documentos respaldados antes de actualizar spec0.8.11/tablero/matriz/guía/ejecución/estado/memoria. No código normal/protocolo/cliente modificado; consulta target compilada read-only, DBwrites/HTTP/SMTP0 y sin seleccionar hashes secretos. JAR91913FF7.../terms20FFD952... intactos.











Async actual: en MISMA terminalB PS> ejecutar una vez java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy, SIN reenroll ni keygen. Correos sa.human.140c10a1@rutafija.test → sa.recovered.140c10a1@rutafija.test. Password de cifrado oculta, revisar instalación/cuenta/newmail/expiración, S solo si corresponde, código emergencia anterior oculto43, Enter SIN reenvío; A EnsayoL01> correo una vez para OTP simulado, B OTP/newpwd de prueba12…72ASCII/confirmar. Después código rotado una vez, nueva ventana privada CRM login con correo/password nuevos y /platform-admins. Solo tras uso efectivo de copia+recuperación+login: A copia-ok, recuperacion-ok, verificar. Se dieron todos pasos antes del reto5min para evitar pausas. Pedidos únicamente MANUAL_RECOVERY_RESULT/EVIDENCE_FILE/EVIDENCE_SHA256. Si falla detener y diagnosticar sin reintento o reenvío automático.











Mantener A/proceso25932/loopback4311-8091 y protección. Ledger1/2/0, cupos0/2/20; aceptación sintética CI03 original preservada. Gate previo NO acreditado; REC-CI03-01 NO autorizado. ADMIN seguirá PENDING aunque recuperación obtenga PASS; no salir, borrar marker o forzar limpieza. TEST_CLEANUP_RELEASED=false. Contrastar nueva evidencia humana antes de actualizar resultados. SC03/07 aprobados condicionalmente SIN APLICAR hasta ambosPASS; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Solo PC Windows; no inferir independencia de custodia ni clave definitiva por prueba temporal. CI01 no se repite, final3 no se regenera, sin Git/GitHub/despliegues/migraciones externas.











## Prioridad actual CI07 — detener antes de firmar; localizar el código original











Operador está en «¿Firmar y continuar?» de recover y no identifica el código de emergencia de enroll. No significa pérdida confirmada. Se indicó N+Enter en B para cancelar antes de firma/proof, mantener A y buscar SOLO en salida visible de enroll de mismaB: tras HuellaSHA256 y encabezado «Código de emergencia (mostrar una sola vez; guardar aparte de la clave privada):», línea43caracteres. No pedir/capturar valor ni foto/log, no es password/token/OTP. Async pendiente: «Localizado» o «No disponible» y si volvióPS>. No cancelación humana confirmada todavía.











SELECT09:56:36.782Z test/rf_test/V26 confirma reto b4429e57-7a6c-409b-a2da-d6380685b0e8 creado09:50:04.922Z, vencido09:55:04.922Z por plazo original; failedProof0, proofVerified/consumednull, recoveryCompleted0. Inscripción temporal/pública verificadas conservadas, sessionVersion0, ledger1/2/0 cupos0/2/20/aceptación intactos; lock/marcador activos. Evidencia nueva ci07-reto-sin-codigo-140c10a1-2026-10-17.md y folder JSON SHA06F0EAA79C85B6102086E4AB9EA52BFAFC434E11E1D6FF76137548A9EF292A4D/fuente/salida/manifiesto. Reutilizó consulta read-only sinHTTP/SMTP/writes; no endpoints/protocolo/harness cambiados ni búsqueda de secretos.











Si código localizado y cliente cancelado, preparar de nuevo secuencia existente recover misma copia/caso con reto nuevo (no reusar vencido), conservando historial/límites; primero todos pasos antes TTL5min. No reenroll/keygen/rotar ni resetear por inferencia. Si no disponible de la salida original o custodia humana, el protocolo tiene solo hash/reinscripción bloqueada y no permite rescatarlo; mantenerCI07pendiente, no extraer secreto de memoria del arnés ni SQL para alterar credencial. Otra vía requeriría decisión explícita, no ejecutar ni construirla ahora. No afirmarFAILdefinitivo antes de saber disponibilidad.











Se respaldaron8docs pre-ci07-reto-sin-codigo y actualizó spec0.8.12/tablero/guía/matriz/ejecución/estado. AmbosPASSmanuales siguen pendientes; SC03/07condicional ya aprobado SINAPLICAR, CI03gatefaltante/otroADMINNOautorizado, TEST_CLEANUP_RELEASED=false. L01EN_CURSO/piloto bloqueado/L02sininiciar. SoloPC y materialtemporal no acreditan custodiareal. CI01noSMTP/repetición, final3 sinregenerar/Git/despliegues/migraciones no.











## Prioridad actual CI07 — Código NO visible; no atribuirlo a pérdida del operador











Última respuesta humana afirma que no aparece en ninguna línea. Aceptar ese dato; no insistir en buscar otra vez ni declarar código entregado. Solo inscripción está acreditada. Diagnóstico nuevo ci07-codigo-no-visible-140c10a1-2026-10-17.md: JAR canónico91913FF7... verificado mediante javap local incluye leer/validar código43 antes de éxito/huella y println encabezado+valor; ConsoleTerminal usa PrintWriter.println/flush. No demuestra salida visible real. Proceso java -jar recover no encontrado; no demuestra cómo acabó ni permite comprobar su archivo/runtime efectivo. No causa exacta probada.











Se ejecutó SOLO la unitaria existente SuperAdminRecoveryCliTest#enrollsOnlyThePublicKeyAndShowsTheEmergencyCodeOnce en Maven offline:1PASS, terminal ScriptedTerminal/HTTPfalso/keys completamente ficticias, sin Spring/DB/SMTP. La aserción contiene el código, no demuestra recepción única en Windows. No cuenta como ensayo humano ni usa claves del caso. Postconsulta10:05:31.649Z test/rf_test/V26 confirma credencial intacta, reto vencido/failedProof0/completed0/sessionVersion0, ledger1/2/0 y cupos0/2/20, aceptación y lock/marcador preservados. JSON SHA CB98F33EB195BB43EBB2C07C7662A5E8367F3A717C13C4FC217D04764F1862DD.











Async actual: cuando B muestre PS> (si sigue¿Firmar? N+Enter), ejecutar SOLO `(Get-FileHash -LiteralPath $trialJar -Algorithm SHA256).Hash` y `java -version`; devolver hash/versión públicos. Sin password/códigos, nueva inscripción/recover o SMTP. No asumir que la variable B apunta al mismo binario hasta esa respuesta. No volver a preguntar dónde guardó el código ni repetir instrucciones de búsqueda. Si no quedó disponible, no puede recuperarse del hash ni omitirse factor; cualquier alternativa de procedimiento requiere decisión explícita, no crearla ni rotar por inferencia. No extraer el oldEmergencyCode de memoria del arnés.











Spec0.8.13/tablero/guía/matriz/ejecución/estado actualizados y8docs respaldados pre-ci07-codigo-no-visible. CI07PENDING/CI03gatePENDING/otroADMINNOautorizado; SC03/07 condicional aprobado SINAPLICAR, TEST_CLEANUP_RELEASED=false. Mantener A/marker/ledger/case y no salir/resetear. CI01 no se repite; final3/términos intactos, piloto bloqueado/L01EN_CURSO/L02sin iniciar. Clave real/custodia independiente siguen pendientes; copia temporal mismaPC no las acredita.











## Prioridad vigente CI07 — JDK confirmado, salida ficticia técnica PASS, reparación propuesta











Usuario reportó desde B SHA91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A y OracleJava21.0.10+8-LTS-217. No volver a pedirlos. No atribuir código ausente a pérdida del operador: entrega original sin acreditar. Fuente/binario println+flush ya comprobados. Se lanzó SOLO diagnóstico técnico de salida del adaptador exacto del JAR con ese mismo JDK en toolConPTY: bloque21b2af, exit0, PRINTF_BEGIN/END, PRINTLN_BEGIN/END y43X visibles. No API/DB/SMTP/keys/códigos del caso; no ensayo humano y no demuestra salida previa de B. No causa exacta probada; no cambio del cliente. Buscaron issues oficiales sin hallazgo aplicable; no se afirma bug ni inexistencia de él.











NUEVO estado importante: corte SQL15:34:27.598Z test/rf_test/V26, PID25932 ausente y0listeners4311/8091; lockPGfalse. Marcador del mismo RUN140c10a1 SIGUEprotegiendo limpiezas: TEST_CLEANUP_RELEASED=false. PublicSTATEúltimo09:50 y solo observación1792227077200 archivada. Credencial/pública/aceptación/ledger1/2/0/cupos0/2/20 intactos, reto vencido failedProof0/completed0/sessionVersion0. Clave de firma del runtime existe por metadata solamente, NO leída por agente. open() SÍ soporta prepared resume con STATE+SECRET existentes y -Serve -ResumeRunId; no es solo bootstrap. No se relanzó. Observaciones/secretos en memoria (enrolledKey, oldEmergencyCode, listas access/refresh) no se restablecen por cargarSTATE; no reconstruir resultados históricos.











Nueva evidencia docs/evidencia/L-01/ci07-consola-java21-140c10a1-2026-10-17.md y folder con JSONSQLSHA DEEB7A547B597107F67E2C1E4A5A86EF22D6FE098290AE87C67685EFCC3F016D, observación técnica/source SafeConsoleOutputCheck.java. Fuente diagnóstico también en backend/target/l01-console-output-readonly, SHA757D449D051EFD014531E3712BBE98F4D38865905D3A4E56A143B2F7346B0516. Usa reflexión solo printf/println del ConsoleTerminal privado existente; no main CLI ni request recovery. Java source launcher con classpathJAR, valores ficticios; no claves ni passwords.











PROPUESTA concreta REC-CI07-02 v0.1.0: docs/operacion/L-01-REC-CI07-02-reparacion-sintetica-propuesta.md, SHA B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2. NO APROBADA/IMPLEMENTADA/EJECUTADA. Prohíbe ejecutar rotación sin decisión nueva. Propone ampliar mismo arnés con única reparación de fixture exactotest/case/owner/install/pub, auth+reauth humanas y límites, mostrar una vez un sustituto aleatorio y pedir repetirlo OCULTO ANTES DE GUARDAR suhash; si cancela/no coincide ningún cambio. Transacción guardada y auditada, syntheticFixtureCodeRepairCount1, no cambio de pública/cuentas/password/correo/cupos/aceptación ni protocolo normal. Reanudación mismo caso, nuevas sesiones observadas como baseline del segmento reanudado; no reconstruir las viejas. Después protocolo normal completo manual con copia/OTPsimulado/sinreenvío/passwordnueva/rotación/revocación/login/archivo; reparación sola noPASS ni prueba entregaoriginal. Alcance requiere autorización explícita por instrucción de no rotar por inferencia y guía actual sin esta operación.











ASYNC ACTUAL DOS preguntas: (1) B PS> ejecutar `java --class-path $trialJar .\backend\target\l01-console-output-readonly\SafeConsoleOutputCheck.java`, indicar PRINTF+PRINTLN+valor43X visibles, soloPRINTF o error. No passwords ni API; Aservidoractualoff no relanzar todavía. (2) revisar propuesta con hash y autorizar REC-CI07-02 condicionada a checkB positivo, o conservar pendiente sin reparación. No inferir aprobación de un diagnósticoPASS. Si aprobación ycheckPASS, implementar/probar única reparación y ruta de observación/resume dentroalcance antes de guiar acción consecretos; NO ejecutar contraseña/código por agente. Si negativa/error, conservar caso/marker/criterios y diagnosticar sin nuevascuentas/rotaciones.











Spec0.8.14/tablero/guía/matriz/ejecución/estado actualizados,8docs respaldados pre-ci07-consola-java21. SC03/07 yaaprobadosCONDICIONALsinaplicar; CI03gateNOacreditado/REC-CI03-01NOautorizado, ambosPASSmanualesfaltan, L01EN_CURSO/pilotobloqueado/L02sininiciar. SoloPC custodiaindependientependiente. Mantener CI01VERIFICADO sinSMTP, términos20FFD952.../final3 intactos, sinGit/despliegues/migraciones externas.











## PRIORIDAD ACTUAL — REC-CI07-02 aprobada/implementada/probada; esperar reanudación personal











Usuario respondió PRINTF,PRINTLN y valor ficticio visibles y AUTORIZÓ REC-CI07-02 con condiciones. No pedir esa aprobación otra vez. Propuesta0.1.0 SHA B1B22C7... permanece inmutable como antecedente. Autorización nueva `docs/operacion/L-01-REC-CI07-02-autorizacion-2026-10-17.json`, SHA EF4E1B5B5C03C448F77B4F2F9B744BC5E710DF75A36902D2FCE91B97EBE9CFC4, mismaRUN140c10a1/owner088518c1/org1a759298/installf0cd888b/pub5b727e9a... diagnóstico humanoPASS, máximo1reparación y repetición oculta ANTESdeescribir. Clase test valida ambos archivos por hash hardcoded. Propuesta no se reescribe porque está en manifiestos previos.











Implementado SOLOtest: ManualL01RecoveryFixtureRepair.java +Test; modificado ManualL01HumanTrialServer/ManualL01HumanTrial/ManualL01HumanTrialTest. Testsfinal35PASS=21harness+14repair, log backend/target/rec-ci07-02-directed-unit-final.log. Logs previos compilación/dobles corregidos preservados en evidencia. No pruebas con limpieza. Nuevo comando A `reparar-codigo`: guardas exactas14 (DBtest/rf_test/V26, RUN/IDs/marker, lock propio, identidades/ID01, pública, términos/aceptación,ledger1/2/0,sincompletados/proofvalidado/livechallenge/priorrepair, una inscripción,observaciónsana); auth+reauth mediante dosPOST/auth/login normales SIN imprimir tokens, sesiónGET/auth/me vigente. Muestra43aleatorios una vez, readPassword repite oculto. Nulo/vacío/diferente implica0DML; buffers vaciados. CodificaCharBuffer conPasswordEncoderactual y txREQUIRES_NEW bloquea/revalida; cambia solohash/updated_at y audita L01_SYNTHETIC_RECOVERY_FIXTURE_REPAIRED conmotivo ycount1, IDs/fingerprintsinsecretos. DBauditnoPrior impide dobleinclusosi STATEpersist fallara. Retorna PASS_PRECONDITION_ONLY, COUNT1 ySEGMENT_ID; noPASSCI07.











Importante: primer preflightLOCKTABLEledgerSHARE falló42501 porprivilegios protegidos; NO se dieron grants. Se reusaron pg_advisory_xact_lock(751844,role1..3) deV22/V23, antesde rowlocks, sinmodificarmigraciones. Native preflight oneoff backend/target/rec-ci07-02-preflight/ManualRecoveryRepairPreflight.java toma CLEANUP_LOCKsolo si libre,SELECT FOR UPDATE/share/advisorycuotas+Store.inspect real14true; EXPLAIN UPDATE yINSERT SINANALYZE, sinaplicarlos,rollback yunlockpropio. PASS15:54:38.700Z, SHAJSON8893D08A7F7714375BDFF0860D6F77923D344F798F350F100D7014AAA05E4CF5. Cero códigos delcaso generados/rotaciones reales/DMLapp/SMTP/HTTP; ledger1/2/0 slots0/2/20/aceptación intactos. No ejecutar lockingpreflight si elusuarioya tieneharness: estaráocupado; usarconsultas SELECTsinadquirirlockpara verificar.











Resume EXISTENTE -Serve -ResumeRunId ahora exige explícito si STATE+SECRET preparado; no otraidentidad/pwd. Tras levantarSpringmaildoble/gatestrue, restorePreparedRecoveryReferences carga pública persistida guardada y baselineversion, beginRecoverySegment archiva SOLOafirmaciones públicas previasen recoverySegmentHistory, marcaPENDING y nuevasobservaciones; no inventa sesionesperdidas ni cambiaADMIN/humanAdminConfirmation/gate. approvalvalid necesario para este casoenrolled. Auditrepair recarga count/id/ack/proposal publicsi yaexistía; nunca código. Si proceso se pierde trasrepair y antesrecover, oldEmergencyCode se puede capturar SOLOenmemoria del request/proof normalexitoso depropioflujo; no desdearchivo/DBhash. El cliente seguirá pidiendo sustituto humano. Nuevaautenticaciónvia comando registra2access/refresh enlistasmemoria para chequear401trasrecuperación. La comparaciónoldhash/plainvive solopara observers permitidos, nada deagentreadsecret. recoveryEvidenceComplete sigue todascondicionesnormales y adicionalmentepara countrepair1 exigeack/ID/hashpropuesta/segmento ysesionescomprobadas>0. No relajóSC07.











Evidencia nueva `docs/evidencia/L-01/REC-CI07-02-implementacion-2026-10-17.md` yfolderREC-CI07-02-2026-10-17, guía `docs/operacion/L-01-REC-CI07-02-ejecucion.md`. Spec0.8.15/tablero/matriz/guía/ejecución/estadoactualizados,10backups enmemory/backup/2026-10-17-pre-REC-CI07-02-implementacion. final3ZIP3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B /manifestE78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD leídos yvalidossinregenerar; JAR91913FF7.../terms20FFD952... intactos.











ASYNC ACTUAL: Usuario debe EN A INTERACTIVA root ejecutar UNA `.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'`; Bconservar variables/keys. Debe aparecerEnsayoL01>, SINcrearpasswordarranque; si pideotraoerrorparar. Abrir4311/loginSINlogin yreportarsolo RUN_ID/carga/error. ESPERARcomprobaciónlock/markerantes reparar-codigo. No usaragentepara -Serve/interactsecrets. Trasreply, verificarenlectura proceso/listenersloopback/ACTIVEexacto/snapshotpglocktrue/currentpublicSTATEsegID yconteos/audit0; noalgoritmo/tokens. Si todoOK, siguienteetapaguiareparar-codigo enA: DOSpasswordSAocultas (mismaquefuncionó, noADMIN/nokeycipher), leerSUSTITUTOfixture43onetime yguardar local separado, repetiroculto; MATCHanteswrite. Pedir solo L01_FIXTURE_REPAIR/SYNTHETIC_FIXTURE_CODE_REPAIR_COUNT/RECOVERY_SEGMENT_ID ydetenerantesrecover. Si fallo no repetirauth/reparar. Agentverificar luegoaudiocount1/pubinstall/cuotas/actualcredmetadataSINselecthash paraacreditarprecondición, noCI07PASS.











Después de repair humana verificada dar secuencia COMPLETA antes reto5min: Bjava -jar$trialJar recoverloopback4311$trialCopy, sa.human...→sa.recovered..., passwordcopia, revisarretoS, CÓDIGO SUSTITUTO confirmado (nooriginalausente), EnterSINreenvío, Acorreo OTPdoble, Botp/newpwd12..72ASCII/confirmar, nuevoemergódigo1vezlocal, loginprivadoSAnewmail/newpwd/platform-admins. Acopia-ok/recuperacion-ok/verificar soloefectivo; pedirMANUAL_RECOVERY_RESULT/EVIDENCE_FILE/SHA. Contrastar bools/audit/consumo/códigos/publica/sesionessegmentoantesPASS. Guardar TODOsaneado sinsecretos. CI03siguesoloGATEPENDIENTE, NOotroADMIN (REC-CI03-01no autorizado), aceptaciónpreservada; aunqueCI07PASSNOsalir ni liberarhastaADMINPASS. SC03/07 yaaprobadosCONDICIONALsinaplicar, L01EN_CURSO/pilotobloqueado/L02sininiciar. Custodiareal/personaslegitimasnocompletadas. CI01noSMTP/Git/proddeploy/migracionesno/final3regeneraciónno.

















## REC-CI07-02 — Reanudación personal y protección comprobadas











Corte **2026-10-17T16:04:33.653016700Z**: [evidencia de reanudación](../docs/evidencia/L-01/REC-CI07-02-reanudacion-2026-10-17.md). El titular confirmó el mismo RUN_ID140c10a1 y el login cargado. Solo lectura en ruta_fija_test/rf_test/V26 confirma marcador exacto, advisory lock activo y rechazo de limpiezas por la guarda; listeners127.0.0.1:4311/8091 del PID18632. Nuevo segmento **82bffcf1-6fab-41fd-a57b-3cbbbf18b123**; las observaciones anteriores perdidas al terminar el proceso no se reconstruyen. Historial1/2/0, cupos0/2/20, una inscripción, clave pública y aceptación ADMIN preservados; no hay reparación previa, reto vigente ni recuperación completada. Inspección archivada SHA B4F61E07DA643891DA13BF5A14270F9394441458D0765F54B6E421DEEE453F5B. El inspector separado usa PID0: su guarda de lock propio=false es esperada y no contradice cleanupLockHeld=true; el comando interactivo vuelve a exigir su propio lock.











Siguiente intervención: en A, `Ensayo L01>`, ejecutar una vez `reparar-codigo`; dos entradas ocultas de la contraseña SA, leer y conservar el sustituto local y repetirlo oculto antes de guardar. Comunicar solo tres resultados públicos; detener ante fallo y esperar contraste antes de recover. Reparación aún no ejecutada; ADMIN PENDING por gate previo y recuperación PENDING. TEST_CLEANUP_RELEASED=false; SC03/07 sin aplicar, L01 EN_CURSO, piloto bloqueado y L02 sin iniciar. No se ejecutaron nuevas pruebas, correo externo, cuentas, cambios de términos/final3 ni escrituras de base por el agente. Este corte prevalece sobre el estado histórico de servidor detenido.

















## REC-CI07-02 — Reparación personal persistida, recuperación pendiente











Corte **2026-10-17T16:11:48.111015900Z**: [contraste archivado](../docs/evidencia/L-01/REC-CI07-02-reparacion-verificada-2026-10-17.md), SHA del JSON **33BF7FEF8AF9C7F1099205B7A9654A17F45A442DACC9AF5D7788CE4915F81FDE**. Operador comunicó PASS_PRECONDITION_ONLY, contador1 y segmento **82bffcf1-6fab-41fd-a57b-3cbbbf18b123**; SELECT test/rf_test/V26 confirma auditoría única **d791653a-6379-4f02-ac1d-1db020dde403**, fecha servidor **2026-10-17T16:10:02.365128Z**, propuesta aprobada y confirmación oculta antes de escribir. La reparación fue personal mediante el arnes, no una rotación inferida por el agente. Una inscripción, misma cuenta/instalación/pública, aceptación ADMIN y términos publicados conservados; ledger1/2/0 y cupos0/2/20 intactos. Reto viejo vencido sin fallos/consumo; sessionVersion0, cero recuperaciones completas, marcador y lock activos. TEST_CLEANUP_RELEASED=false.











Continuación personal ya indicada: en B `java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy`; mismo correo inicial y nuevo sintéticos, revisar reto5min, sustituto confirmado oculto, Enter sin reenvío, `correo` en A para OTP simulado, contraseña nueva/confirmación y conservar código final una vez. Login recuperado en ventana privada y /platform-admins; solo tras realizarlo, `copia-ok`, `recuperacion-ok`, `verificar`. Comunicar solo MANUAL_RECOVERY_RESULT/EVIDENCE_FILE/EVIDENCE_SHA256, detener ante fallo sin repetir o reenviar; no `salir`. Guía REC-CI07-02-ejecucion v1.0.1, pasos1/2 ya realizados.











Reparación sola no acredita CI07 ni entrega original. Recuperación manual PENDING, CI03 gate previo PENDING sin otro ADMIN autorizado. SC03/07 condicionales sin aplicar; L01 EN_CURSO, piloto bloqueado, L02 sin iniciar. Copia misma PC no acredita custodia independiente. CI01 VERIFICADO sin repetir, sin SMTP externo, Git/GitHub, limpieza global, migraciones de desarrollo/piloto o cambios de final3. Solo consultas y compilación del lector; no nuevas suites ni secretos en evidencia.

















## CI07 — Apertura local de la copia pendiente, sin reto nuevo











Corte **2026-10-17T16:34:14.586167200Z**: [diagnóstico de solo lectura](../docs/evidencia/L-01/ci07-clave-local-140c10a1-2026-10-17T163414Z.json), SHA **B4784672DA91521564C96CB11921AC5FD404DFE20A97AEB6BA87BC9F92B292F3**. El operador informó que introdujo la contraseña de acceso SUPER_ADMIN donde se solicitaba la del archivo cifrado. El cliente vigente descifra la clave antes de crear el reto HTTP; no se capturó la excepción criptográfica exacta. SELECT ruta_fija_test/rf_test/V26 confirma solo el reto anterior vencido, cero fallos de prueba, sin prueba validada/consumo/completado; sustituto sin consumir. Reparación única, pública e instalación iguales, aceptación preservada, ledger1/2/0 y cupos0/2/20 intactos, lock y marcador activos.











Siguiente paso exclusivamente local: si recuerda con certeza la contraseña elegida en keygen y usada en check-key/enroll, ejecutar una vez en B `java -jar $trialJar check-key $trialCopy`; introducirla oculta y comunicar solo LOCAL_ENCRYPTED_KEY_CHECK/PUBLIC_KEY_SHA256 o error saneado. Sin API/correo/código de emergencia. Si no la recuerda, comunicarlo sin compartir secretos ni probar combinaciones; conservar archivos y caso, no regenerar/inscribir ni repetir reparación. Esperar contraste antes de recover. Recuperación manual PENDING, CI03 gate PENDING, TEST_CLEANUP_RELEASED=false, SC sin aplicar, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar; CI01 no se repite.

















## CI07 — Check-key fallido aclarado; cifrado recordado, verificación local pendiente











[Diagnóstico y límites](../docs/evidencia/L-01/ci07-check-key-diagnostico-140c10a1-2026-10-17.md). Operador aclaró que el check-key fallido se hizo con la contraseña de acceso SUPER_ADMIN y luego confirmó recordar con certeza la de cifrado elegida en keygen. No se conoce ni solicita el valor. Se indicó una única comprobación local con la contraseña de cifrado confirmada; resultado todavía PENDING, sin API/correo/código de emergencia. No repetir recover, reparación, inscripción o generación hasta contrastar.











Metadatos a2026-10-17T16:37:59.3668730Z: principal y copia previstos existen con477 bytes, archivos públicos coinciden con la huella inscrita y JAR canónico verificado. Sin abrir privadas ni calcular sus hashes; esto no prueba igualdad/integridad del contenido cifrado ni la variable efectiva de B. Mensaje genérico no distingue lectura/descifrado/par y no permite atribuir una causa criptográfica exacta. Evidencia JSON SHA **DACB4D89F597B8CD88D7E166461D87B41E9FED4C006957DCB27F0B0996B53F9B**. Se reutiliza la lectura DB16:34, sin nueva consulta por este comando offline. Reparación única y anterior evidencia preservadas; CI07 manual PENDING, CI03 gate PENDING, TEST_CLEANUP_RELEASED=false, SC sin aplicar, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. No nuevas suites, envíos, cuentas ni cambios de final3/términos/protocolo.

















## CI07 — Cifrado no identificado; REC-CI07-03 propuesto, sin autorizar











[Estado y límites](../docs/evidencia/L-01/ci07-cifrado-no-identificado-140c10a1-2026-10-17.md). Aclaración vigente del operador: solo identifica las contraseñas de acceso SUPER_ADMIN/ADMIN y no la del archivo cifrado; sustituye la declaración previa de recuerdo. No más intentos de contraseña ni comandos de recuperación/generación/inscripción. El protocolo no permite restablecer ese cifrado mediante login/código, ni una segunda inscripción o cambio de pública durante recuperación. No se afirma corrupción o causa criptográfica exacta.











SELECT **2026-10-17T16:42:08.244527300Z**, ruta_fija_test/rf_test/V26, JSON SHA **E8FA6BA7F8082B7CF4285A23DB61F44C065F27D86C35ACB5C884F028014502E6**: misma credencial/pública/instalación, una reparación REC02 preservada, solo reto antiguo vencido sin fallos/prueba/consumo/completado, versión0, aceptación ADMIN e historial1/2/0/cupos0/2/20 intactos; lock y marcador activos, TEST_CLEANUP_RELEASED=false. No nuevas escrituras, HTTP, SMTP, privadas leídas, códigos generados o suites.











Propuesta **REC-CI07-03 v0.1.0**, docs/operacion/L-01-REC-CI07-03-clave-temporal-propuesta.md, SHA **1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8**: nueva clave/copia temporales generadas y comprobadas personalmente; única sustitución de pública por mantenimiento del mismo fixture con auth/reauth/guardas/auditoría y preservación de cuenta, instalación, código, aceptación, archivos, historial y cupos. Se requiere aceptar expresamente esa preparación excepcional; la nueva pública no se afirmará inscrita mediante un segundo HTTP. Después sigue obligatorio todo el protocolo normal y conservación de la nueva pública durante esa recuperación. **NO APROBADA, NO IMPLEMENTADA, NO EJECUTADA.** La autorización REC02 preservaba la pública y no cubre esto. Alternativa: mantener caso pendiente hasta identificar personalmente el cifrado original, sin intentos por rutina.











No reaprobación de SC03/07 originales: siguen condicionales sin aplicar y falta acreditar ambos ensayos. CI03 gate PENDING sin otro ADMIN autorizado, CI07 manual PENDING; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Originales reales pendientes para piloto; CI01 no se repite. Sin cambios de términos, final3 o controles normales.

















## REC-CI07-03 — Autorizada, implementada y probada; intervención personal pendiente











[Implementación y límites](../docs/evidencia/L-01/REC-CI07-03-implementacion-2026-10-17.md). Titular autorizó REC-CI07-03 v0.1.0 SHA1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8; registro público docs/operacion/L-01-REC-CI07-03-autorizacion-2026-10-17.json SHA434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6. No volver a pedir esta aprobación. Implementación solo test con comando preparar-clave: auth/reauth normales, pública Ed25519 y huella, confirmación humana de principal/copia y disponibilidad del sustituto;17 guardas, transacción, UPDATE solo pública/timestamp y auditoría única, sin otro código/cuenta/cupo/instalación o inscripción HTTP. Baseline pública nueva anclada a auditoría aprobada; recuperación normal y humana completa todavía exigida.











Última ejecución dirigida **54/54** (23 arnes/17 preparación/14 regresión REC02), SHA log F774BF52FB613DBF39D96DAEC08D1A0D6E7ECF2BE929AC4F84E8DFC7B780A9E1. Fallo inicial del doble JDBC corregido y preservado; logs intermedios no se suman. Concurrencia/rollback con dobles, no commits reales de preparación. Consulta PostgreSQL **2026-10-17T17:01:03.669095800Z** test/rf_test/V26:17/17 guardas con referencia prestada del PID titular39116, sin adquirir/desbloquear el lock; EXPLAIN UPDATE/INSERT sin ANALYZE, sin DML/DDL/commit/HTTP/SMTP/privadas/códigos. JSON SHAB92FC8A4B938C9D928597CC880C756A4947C3ABADCC650FC995248D9467B7231. El comando real volverá a exigir lock de su propia sesión. Ledger1/2/0, cupos0/2/20, aceptación, originalREC02 y pública anterior intactos. Manifiesto17 archivos SHA2B210DB1880CA1693C9A88FC6834F0DA9ABDF70E157BB2C715039D6E914934A3. final3 ZIP38065106.../manifiestoE78D7002..., JAR91913FF7..., términos20FFD952... y observación original9319F045... verificados sin regenerar.











Guía docs/operacion/L-01-REC-CI07-03-ejecucion.md v1.0.0. **Etapa personal actual solicitada:** A EnsayoL01> verificar una vez y salir coordinadamente para recarga; debe conservar marcador y TEST_CLEANUP_RELEASED=false porque ambos PENDING. En PS> ejecutar una vez .\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'. MismoRUN, sin password de arranque, lista incluye preparar-clave y login4311 carga; reportar solo esos hechos. Esperar comprobación de marcador/lock antes de nuevas claves. Esta salida coordinada permitida archiva y NO habilita limpiezas, no es un cierre ni retirada manual del marcador.











Después: destino nuevo REC-CI07-03 fuera del repositorio, operador keygen/copia y dos check-key separados (no pegar varios comandos interactivos juntos); variables trialNewPrimary/trialNewCopy preservan los originales. Comunicar dos checks y huella antes de preparar. A preparar-clave exige DOS passwords de ACCESO SA, línea pública .pub, SHA64 y literal CLAVE_COPIA_Y_CODIGO_CONFIRMADOS; nunca recibe privada/passwordcifrado/código. Contrastar auditoría antes de recover con trialNewCopy; el sustituto REC02 sigue vigente. La inspección existente -InspectRunId ya informa auditoría pública nueva/huella y correspondencia; no reutilizar tras cambio el lector oneoff que exige la pública original. No enroll ni reparar-codigo nuevos.











**Preparación real todavía NO ejecutada; clave nueva del operador NO generada.** CI07 manual PENDING y CI03 gate PENDING, otro ADMIN no autorizado. TEST_CLEANUP_RELEASED=false, SC03/07 condicionales sin aplicar; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Originales reales pendientes para piloto, solo PC confirmada. CI01 VERIFICADO sin repetir; sin Git/GitHub, despliegue, SMTP externo o migraciones de desarrollo/piloto. Este corte sustituye las solicitudes pendientes de autorización REC03 anteriores sin alterar sus antecedentes.

















## REC-CI07-03 - Recarga contrastada y error de pegado corregido (2026-10-17 Lima)











[Diagnostico y limites](../docs/evidencia/L-01/rec03-error-pegado-140c10a1-2026-10-17.md). Corte UTC **2026-10-18T00:24:49.089356600Z**: mismo RUN_ID140c10a1-be0f-4d08-a6d4-c796e2ed107a, test/rf_test/V26, segmento **e140a035-8211-4037-82e6-388329f1cfc8**, PID22648 loopback4311/8091. Marcador y lock activos; ledger1/2/0, cupos0/2/20, aceptacion sintetica1, REC02 unica y publica original conservados. Recarga y archivos previos contrastados; el operador comunico solo RUN_ID, sin reconstruir salida historica de limpieza ni prueba visual del navegador.











Bloque pegado en una linea sin separadores: **ParserError/UnexpectedToken**, antes de ejecutar ese envio. Destino nuevo/clave/copia ausentes al corte00:24:47Z. GuiaREC03 **1.0.1** agrega separadores y parada segura: analisis sintactico0errores multilinea y0 en una linea, sin ejecutar el bloque ni generar claves; ACL aun no comprobada en ejecucion. **Siguiente paso humano: solo ejecutar el bloque corregido de directorio una vez, comunicar L01_TEMPORARY_KEY_WORKSPACE y detenerse antes de keygen.** Ante fallo no repetir/borrar; A permanece abierta.











CI01 VERIFICADO sin repetir correo; CI03 PENDING solo por gate previo, aceptacion preservada y otro ADMIN no autorizado; CI07 PENDING, sustitucion publica0. SC03/07 condicionales sin aplicar, **TEST_CLEANUP_RELEASED=false**, limpieza suspendida, L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Sin SMTP externo, pruebas que limpien, claves definitivas, cambios de final3/terminos/protocolo, Git/GitHub, despliegue o migraciones de desarrollo/piloto. JSON SHA **11614CEE91A9460225D8A125554F863895AF955C3E2A62B2A698CE38F961C6D2**.

















## REC-CI07-03 - Directorio y ACL verificados; keygen personal pendiente (2026-10-17 Lima)











[Directorio listo y limites](../docs/evidencia/L-01/rec03-directorio-ready-140c10a1-2026-10-17.md). El operador comunico **L01_TEMPORARY_KEY_WORKSPACE=READY_NO_KEYS_CREATED**. Metadatos UTC2026-10-18T00:32:21Z confirman directorio nuevo, herencia protegida y unica regla FullControl del propietario; principal/publica/copia ausentes. SELECT00:32:22Z en test/rf_test/V26 confirma marcador/lock activos, ledger1/2/0, cupos0/2/20, aceptacion1, REC02 unica y publica original conservados; sustituciones0, sin reto vigente ni recuperacion completa.











GuiaREC03 **1.0.2**: mantener A abierta, en la misma B ejecutar **solo** `java -jar $trialJar keygen $trialNewPrimary`, elegir/confirmar/conservar personalmente el cifrado nuevo y comunicar exito/error antes de copiar o comprobar. No repetir preparacion del directorio. Generacion por operador todavia no reportada; el agente no genero claves, no abrio privadas y no ejecuto suites ni SMTP. CI01 VERIFICADO, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Evidencia JSON SHA **1F2B7EA85B99C559F9FE00A67C5703BABE0A75516B57C4E203F22D4412D18686**.

















## REC-CI07-03 - Keygen personal comunicado; copia y lectura pendientes (2026-10-17 Lima)











[Generacion comunicada y limites](../docs/evidencia/L-01/rec03-keygen-personal-140c10a1-2026-10-17.md). Operador comunico **Par Ed25519 creado** y conservacion del cifrado nuevo. Corte UTC2026-10-18T00:36:57Z: principal477 bytes por metadatos, publica Ed25519 canonica SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**, copia ausente. Solo publica/metadatos consultados, no privada ni hash del archivo cifrado; descifrado todavia no acreditado. SELECT00:36:58Z test/rf_test/V26 conserva marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02. Publica inscrita original5b727e9a... intacta y sustituciones0.











GuiaREC03 **1.0.3**: operador crea copia local con destinos desocupados y overwrite=false; solo tras exito comprueba una vez el **principal** con el cifrado nuevo y comunica resultados/huella. Esperar antes de comprobar copia o preparar publica. Instruccion de copia validada por analizador, con y sin saltos, sin ejecutarla. No repetir keygen; dos archivos mismaPC no acreditan independencia. CI01 VERIFICADO sin repetir correo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**. L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpiezas, SMTP, cambios de final3/terminos/protocolo, Git/GitHub, despliegue o migraciones ajenas a test. JSON SHA **5928DA632E6F801DEF31F9C4C8D8298CA84AD83D4D096AF887AB1F4781FFCC04**.

















## REC-CI07-03 - Principal PASS reportado; comprobar copia (2026-10-17 Lima)











[Resultado y limites](../docs/evidencia/L-01/rec03-principal-pass-140c10a1-2026-10-17.md). Operador reporto **LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE** del principal, SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. Verificacion basada en resultado humano del CLI solicitado y coincidencia del archivo publico; el agente no descifro. Metadatos UTC2026-10-18T00:41:54Z confirman principal/copia477bytes y publicas iguales; no igualdad de privadas ni lectura de copia. Linea de creacion de copia no reportada, no reconstruirla. SELECT00:41:55Z test/rf_test/V26 confirma marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02 conservados, publica inscrita original y sustitucion0.











GuiaREC03 **1.0.4**: mantener A, ejecutar personalmente una vez en B **solo `java -jar $trialJar check-key $trialNewCopy`** con el cifrado nuevo que paso en principal; comunicar resultado/huella y detenerse antes de preparar publica. No repetir copia/generacion/principal. CI01 VERIFICADO sin correo nuevo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpieza, SMTP, claves por agente o cambios de final3/terminos/protocolo. Copia mismaPC no acredita independencia. JSON SHA **14B7D678FCF1961A5DD7F6C2D19CC1855CFF9556A9820ADD5A73A79D8B09BA2F**.

















## REC-CI07-03 - Dos checks locales PASS; preparar publica (2026-10-17 Lima)











[Dos lecturas y limites](../docs/evidencia/L-01/rec03-copia-pass-140c10a1-2026-10-17.md). Operador reporto **LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE** tambien para la copia nueva; SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433** igual a principal y ambas publicas. Se conserva evidencia previa del principal SHA14B7D678...; resultados humanos del cliente solicitado, no descifrado por agente ni comparacion de privadas. SELECT UTC2026-10-18T00:45:27Z test/rf_test/V26 confirma marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02; publica inscrita original, sustitucion0, sin reto vigente ni recuperacion completa.











GuiaREC03 **1.0.5**, solo apartado4: operador confirma personalmente disponibilidad del sustituto vigente, consulta .pub en B y ejecuta una vez **preparar-clave** en A con autenticacion/reautenticacion de ACCESO SA, publica, huella y confirmacion humana; esperar contraste de auditoria antes de recuperar. Si falta sustituto, detenerse. No nueva reparacion del codigo/inscripcion HTTP. Ambos checks **no acreditan PASS_LOCAL_TEMPORARY_COPY_ONLY** ni independencia de custodia. CI01 VERIFICADO sin correo nuevo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpiezas, SMTP o cambios de final3/terminos/protocolo. JSON SHA **83682662898A7F2D8B980A526BFCDC3202A4382F48B6E7651126995141BA05B5**.

















## REC-CI07-03 - Sustitucion ejecutada y auditada; recuperacion normal pendiente (2026-10-17 Lima)











[Sustitucion contrastada y limites](../docs/evidencia/L-01/rec03-preparacion-verificada-140c10a1-2026-10-17.md). Operador ejecuto **preparar-clave**: PASS_PRECONDITION_ONLY, sustitucion1 y segmento e140a035-8211-4037-82e6-388329f1cfc8. SELECT UTC2026-10-18T01:19:53.372925200Z en test/rf_test/V26 verifica auditoria unica **58b926a7-bf2d-4ff1-a978-feea67d1e9c4**, fecha servidor01:18:58.039Z, aprobacion/propuesta/cuenta/instalacion/caso/segmento correctos y publica actual **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**; correspondencia persistida true. Confirmacion humana previa incluye ambos checks y disponibilidad del sustituto. Inscripcion HTTP original1 con publica historica; sustitucion1 contabilizada por separado. REC02 unica/ledger1/2/0/cupos0/2/20/aceptacion/marcador/lock conservados. Sin reto vigente/recuperacion completa y sessionVersion0 al corte. Guardas de no repetir preparacion quedan false de forma esperada; publica/auditoria actuales validadas por separado.











GuiaREC03 **1.0.6**, apartado5: recuperacion normal personal una vez con copia nueva/cifrado nuevo/sustituto vigente, reto5min, OTP simulado sin reenvio, password nueva/codigo final mostrado una vez/login; solo despues copia-ok, recuperacion-ok y verificar. Esperar resultados y archivo antes de acreditar PASS_LOCAL_TEMPORARY_COPY_ONLY. La preparacion ya verificada no lo sustituye. CI01 VERIFICADO sin nuevo correo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC03/07 sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites/limpiezas/SMTP ni escrituras de inspeccion; codigo/hash/privadas no consultados. Copia mismaPC no acredita independencia. Sin cambios de final3/terminos/protocolo normal, Git/GitHub, despliegue o migraciones de desarrollo/piloto. JSON SHA **88F6A6B64A3655616409C07B96AC2C4C772FD6E7CACFAA200C51406D8001053F**.

















## CI07 - PASS manual local contrastado; unico gate local pendiente CI03 (2026-10-17 Lima)











[Informe y revision de los doce criterios](../docs/evidencia/L-01/CI07-manual-PASS-2026-10-17.md). **PASS_LOCAL_TEMPORARY_COPY_ONLY verificado**: archivo humano1792287275662, SHA **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**, mismoRUN140c10a1 y segmento e140a035. Predicado vigente del arnes true; native SELECT test/rf_test/V26 UTC2026-10-18T01:40:55Z confirma reto fa9aa2ae consumido01:30:47.240995Z, OTP/capacidad retirados, misma cuenta, publica5569... conservada, sesion0a1 y login. Observador archivado rechaza3access+3refresh; auditoria revoca5refresh,14previos figuran revocados. Comparaciones de codigo anterior/nuevo proceden del observador ya archivado, sin consultar secretos. Archivo publica milisegundos, nativo conserva995microsegundos adicionales; no se altera archivo. **Hubo2OTP simulados y1reenvio**, dentro3/60: se documenta desviacion de instruccion sin reenviar, sin afirmar cero ni repetir el ensayo.











Ledger1/2/0, cupos0/2/20, aceptacion1, reparacionREC02=1, sustitucionREC03=1 e inscripcionHTTP original1 conservados. Doce hashes de archivos (final3/terminos/JAR/aceptacion/archivo nuevo y6logs previos) coinciden; no se repiten suites. **CI07 es ensayo tecnico local VERIFICADO, no custodia real cumplida.** Los doce criterios se revisan en el informe enlazado. **CI03 sigue PENDING solo por gate previo**, sin otro ADMIN autorizado. Condicion SC07 acreditada, SC03 no; adenda conjunta sin aplicar, originales reales pendientes de piloto. **TEST_CLEANUP_RELEASED=false**, marcador/lock y suspension de limpiezas conservados; mantener A y no repetir recovery/salir. L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Siguiente accion: via expresamente autorizada para gate; despues adenda/archivo/salida/revisionfinal, sin reaprobacion de lo ya autorizado. Consolidado SHA **C50BB7B7864491D6841B1616445137AF37A11A6F0365A3CA5B856D059254769E**, nativo SHA **A746503800344BB3EBA2C699A665156E4FF1C9931549092156F3C431B913F2EC**.

















## Cierre funcional local acreditado — 2026-10-17











**L-01 VERIFICADA; tipo FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Se aplican SC-03/SC-07 y la [precision aprobada SC-03 1.1.0](../docs/contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md), [registro de aplicacion](../docs/contratos/SC-L01-LOCAL-01-aplicacion-v1.1.0-2026-10-17.json). El contrato anterior y la aprobacion condicionada quedan conservados como antecedentes; no se cambian silenciosamente ni se solicita otra aprobacion equivalente.











- CI03: aceptacion humana sintetica original intacta (c496b63c), gate anterior omitido en ese ensayo conservado como historia. [Complemento automatizado separado](../docs/evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/README.md): capacidad de activacion valida, POST complete409 RESOURCE_CONFLICT especificamente por terminos pendientes, aceptacion automatizada de un unico ADMIN adicional y mismo POST200 COMPLETED; login/grupos200. Resultado compuesto **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**; no se inventa PASS_MANUAL_SYNTHETIC_ONLY ni se atribuye al titular la aceptacion automatizada.





- CI07: **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado y contrastado, sin repetir recuperacion. Copia en la misma PC; no independencia de custodia. CI01 permanece VERIFICADO sin nuevo SMTP.





- Los doce criterios se revisaron con evidencia, entorno y limites en la [matriz final](../docs/evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Se reutilizan regresiones validas y se ejecutaron27 unitarias dirigidas del arnes y el complemento real HTTP/PG, separados de la evidencia humana. Ningun criterio obligatorio del alcance local queda pendiente.





- Archivo previo a salida del arnes: **TEST_CLEANUP_RELEASED=true**, marcador ausente/lock libre, servidores cerrados. Limpieza selectiva del ADMIN adicional por API normal, inactivo y sesiones revocadas403 FORBIDDEN_ROLE; aceptacion e historial conservados. Sin limpieza global, estados forzados o reinicio de cupos. Ledger1/2/0 pasa a **1/3/0**, cupos **0/1/20**, aceptaciones2; ADMIN manual, SUPER_ADMIN y caso CI01 intactos. Las futuras pruebas autorizadas que limpian solo ruta_fija_test pueden reanudarse tras el archivo.





- Requisitos originales pendientes **antes del piloto**: aceptacion propia de ADMIN legitimo; preparacion, medios/custodia independiente e inscripcion/ensayo del titular real; privacidad/identificacion y condiciones de conservacion; infraestructura/base/HTTPS/copias/restauracion y preparacion de participantes/recursos/titulos. No se registran como satisfechos por equivalencia. Solo una PC Windows confirmada; no se presupone medio adicional.





- Terminos1.0.0 SHA20FFD952... y final3 ZIP38065106.../manifiestoE78D7002... comprobados sin cambios. Sin SMTP adicional, claves definitivas, migraciones de desarrollo/piloto, despliegue o Git/GitHub. S/0 y sin offline. **L-02 NO INICIADA / NO AUTORIZADA; OK VERDE separado.**











El [acta](../docs/actas/L-01-cierre-funcional-local-2026-10-17.md) contiene el alcance, referencias, limites y bloqueos. Las observaciones historicas PENDING del ensayo humano anterior al complemento no representan un pendiente actual del alcance local tras aplicar la adenda.

















## Inicio L-02 y decision CARD-01 — 2026-10-18











Autorizacion directa **OK VERDE** despues del cierre local L01. [Registro](../specs/decisiones-L02-2026-10-18.json), [contrato0.1.0](../docs/contratos/L-02.md), [ejecucion por subfases](../docs/ejecucion/L-02.md), [matriz](../docs/evidencia/L-02/matriz-L02.md). No pedir de nuevo la misma autorizacion para incrementos dentro de L02 ni iniciar otro lote.











- **Una sola unidad vinculada vigente por CONDUCTOR**, decision explicita posterior al inicio. La unidad tampoco se comparte entre conductores en periodos superpuestos. Periodos sucesivos e historial conservados; finalizar servicio no rompe vinculo; sin sustitucion automatica. Fuente registro v3.1 conservado, resuelto por suplemento CARD-01; implementacion temporal y verificacion aun pendientes.





- Reutilizar invitacion/identidad preliminar/expediente/subsanacion/aprobacion/provision ya cerrados en L01. No reconstruirlos ni trasladar su aceptacion a L02. No repetir OTP real ni recuperacion humana o terminos aprobados.





- L02.0: nueva consulta ordinaria sin PII backend/CRM separa aprobacion personal, cuenta, grupo, documentacion y Driver; mantiene **operationalEnabled=false** mientras siga pendiente revision unidad/servicio. Proteccion heredada de parejas activas con locks Driver->Vehicle; mas de un vinculo ya existente exige revision, sin borrado automatico. No equivale a periodos completos o constraint persistente probado.





- Detectada dependencia circular heredada: Driver provisionado inactivo, activacion bloqueada por unidad/servicio y vinculo exige Driver activo. Resolver mediante transiciones explicitas en L02.2/L02.3; no desactivar gates o forzar estados SQL.





- L02.1 vigencia/suspension/correccion; L02.2 unidad/revision/vigencias; L02.3 continuidad CRM/Android; L02.4 verificacion conectada/retorno/cierre. Dispositivo fisico permanece L08.





- Inspeccion inicial de archivos:49/50 hashes L01 iguales; backlog mutable distinto antes de este trabajo, preservado en respaldo. No se atribuye causa; acta, evidencia, terminos y final3 intactos. No se reescribe manifiesto historico al evolucionar documentos mutables.





- Entorno no iniciado/consultado ni migrado en este incremento. Contadores1/3/0 y cupos0/1/20 son el ultimo corte archivado L01, **no una consulta actual**; conservarlos y comprobar antes de futuras pruebas. Sin pruebas nuevas o suites ejecutadas en el arranque; compilacion se registra por separado, no como validacion funcional.





- Respaldo publico `memory/backup/2026-10-18-inicio-L02`, sin secretos. Requisitos originales de piloto siguen pendientes; solo PCWindows confirmada, sin custodia independiente acreditada. Piloto/SMTP/cuentas reales/despliegue/migraciones desarrollo-piloto no autorizados. Sin Git/GitHub, offline ni regeneracionfinal3. **L03 no iniciada**.

















### Resultado de compilacion del incremento L02.0











Backend `mvnw.cmd --offline compile`, CRM `npm.cmd run typecheck` y `npm.cmd run build:development`: **exit0** sobre las fuentes finales del incremento. Logs y fuentes congelados en `docs/evidencia/L-02/incremento-0/`; informe `docs/evidencia/L-02/incremento-L02.0-2026-10-18.md`. Sin tests ejecutados, sin conexion a PostgreSQL/SMTP, sin migracion ni limpieza. Validacion funcional/concurrencia, revision independiente y restantes subfases pendientes. **L-02 EN_CURSO**, piloto bloqueado; L-03 no autorizada.

















### Incremento L02.1?4 en verificaci?n (2026-10-19)











V27 aplicada solo a `ruta_fija_test`, runtime `rf_test`; preservaci?n de L-01 verificada. Backend: 30 pruebas HTTP con rollback y 2 carreras PostgreSQL correctas; CRM: 19 pruebas mock/build/typecheck; Flutter: 30 dirigidas/an?lisis y recorrido de repositorios con HTTP real correcto. [Evidencia parcial](../docs/evidencia/L-02/incremento-1/resultado-parcial.json). No acredita navegador completo ni dispositivo f?sico.











Caso conectado L-02 `44b3d67a-69ce-4169-89b4-62ebe8a40160`: un CONDUCTOR sint?tico creado/provisionado mediante HTTP normal; hist?rico 1/3/0 ? 1/3/1, sin reinicio ni nuevo ADMIN. Flutter pas?; CRM pendiente de completar el mismo caso. Marcador `l02-connected.active` conserva protecci?n contra limpieza global. L-02 EN_CURSO; piloto bloqueado, L-03 no iniciado. No se repiten OTP real/recuperaci?n L-01, no Git/GitHub ni regeneraci?n de final3.







## Corte posterior a CAP25 - 2026-10-22

[Revisión integral vigente](../docs/evidencia/L-08/revision-integral-2026-10-22.md). CAP25 y la carrera acotada de retiro están acreditados; dataset cerrado, ledger1/4/20 conservado en55439 detenida. Caso físico cerrado y ledger1/3/7 de5432 conservados. API preservadaV48 y candidato empaquetadoV50 comprobados en readonly. CI-L08-03 VERIFICADO_LOCAL_SINTETICO; restantes criterios según matriz, L-08 EN_CURSO y piloto BLOQUEADO. Sin reabrir casos, correo adicional, Git/GitHub o regenerar final3. Siguiente trabajo: complementos físicos/escritorio, recuperación empresarial completa/retorno y dependencias reales ya declaradas pendientes. Consulta de disponibilidad Wi-Fi/sesión enviada, sin autorizar LAN o una cuenta nueva.

Sellos del corte CAP25: caso22archivos SHA1a72d34a...; incremento2 75archivos SHA7091e94d.../1086fuentes. No editar archivos congelados; siguiente trabajo en incremento nuevo. [Postflight global](../docs/evidencia/L-08/postflight-global-2026-10-22.json).

## L08.3 — Escritorio, restauración V50 y complemento físico protegido

Contrato0.7.0. Carga25 y caso físico anterior permanecen cerrados. Se conservan sus manifiestos, contadores e historial; no se reabrieron.

- Escritorio: Chrome155.0.8059.86/Edge155.0.4283.62,12combinaciones ADMIN/SUPER_ADMIN y zoom nativo100/200/300%, área útil1280×720.66páginas/804checks con backend/PG reales en readonly; bootstrap de sesión sintético, no login/refresh ni lector humano. API/tablas/hash íntegros antes/después.
- REST03 autorizado, nuevo55440/ruta_fija_test, snapshotV50 GCM con105tablas/esquema/permisos reconciliados.401/403 y creación/reconsulta/retiro normal de un grupo; cero cuentas nuevas. Fallo de arnés recuperado sobre el mismo grupo. Máximo318,730778s desde origen conservador anterior al fallo de salud; primer journal nulo conservado, sin reiniciar tiempos. No pérdida física real, medio independiente, RPO ni reconexión móvil.55440 detenido;55438/55439 conservados/detenidos.
- Pruebas dirigidas:21de respaldo y21del arnés (no42nuevas de producto), sin fixtures. Guarda compartida APK rechaza el binario de IDprincipal y admite `.l08`; sin instalar en la regresión. JAR V50 actual conserva sus672entradas de aplicación iguales.
- FIS02 autorizado: máximo una alta7→8, regla temporal Privada/Java/TCP4311/origenPoco y filtro de peer. Caso3f0ba758-6eb9-4d2f-b948-dd1c7de3c430 activo, login/disponibilidad/aceptación/consentimiento/inicio personales y GPS reales. Usuario informó uso TalkBack satisfactorio. Font_scale0,81 al corte, pendiente escala grande y medición15min sin alimentaciónUSB (criterio≤5puntos ya aprobado). No se fabrican estados o resultados humanos.
- Error propio de instalación: el primer APK actualizó el principal `pe.rutafija.conductor`; datos sin borrar, binario previo sin copia/huella. Principal cerrado. Paquete `.l08` correcto instalado/verificado después, SHAe74dd49725499e3514c1fd853d81a1479f0614a6c5bfd07ddfa1a9b1c764609b; no volvió a modificar el principal. Incidencia abierta, sin declarar restitución o preservación del binario anterior.
- Ledger1/3/8 y aceptaciones2; marcador/lock activos, TEST_CLEANUP_RELEASED=false. Mantener fixtures suspendidos. Antes de liberar: archivo humano, retiro exacto, postflight y eliminación de regla temporal. Casos anteriores no se repiten. Final3/términos/manifiestos congelados intactos.

Pendientes concretos: completar FIS02 (escala, negativas físicas previstas, batería, cierre/logout y archivo/retiro), resolver el binario principal, medios independientes/monitor/alertas/cobertura24/7 y preparación de privacidad/participantes/títulos/ADMIN legítimo/custodia/infraestructura. Sin acta, despliegue o piloto activo.
