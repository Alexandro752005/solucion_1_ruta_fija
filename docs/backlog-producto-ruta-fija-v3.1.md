> **Corte vigente 2026-10-23, L08.5 / contrato 0.9.0:** L-00 a L-08 VERIFICADAS en su alcance funcional local. L-08: contrato 0.9.0, FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL y GO PARA CANDIDATO LOCAL DE ENTREGA. Piloto real: NO_GO; condiciones pendientes separadas en el protocolo de transición. V50 con 51 migraciones; ledger 1/3/8, dos aceptaciones y 85 puntos históricos preservados; TEST_CLEANUP_RELEASED=true. APK release v5 firmada como candidata, con endpoint HTTPS .invalid sin servicio y sin instalar; POCO conserva debug v4. 56 pruebas de ubicación aprobadas, incluidas seis nuevas, analyze correcto y continuidad SQL aislada comprobada. Los cortes inferiores son antecedentes históricos.

> **Corte vigente L08.4/0.8.0,2026-10-23 America/Lima:** FIS02 cerrado y retirado selectivamente; una sola app principal v4 instalada y verificada. Batería 30→28% (2 puntos, 959s sin carga), GPS real y 85 puntos conservados; TalkBack humano, escala150%, negativas y contraste comprobados con límites. Ledger1/3/8 y aceptaciones2; TEST_CLEANUP_RELEASED=true; regla temporal, servidor y ADB TCP retirados. L-08 EN_CURSO; decisión NO_GO para aceptación integral/piloto: señal GPS sin fix físico, continuidad independiente, respaldo/RPO, firma de entrega y preparación real siguen pendientes. [Matriz vigente](evidencia/L-08/matriz-L08.md). Los cortes inferiores son históricos.

> **Estado vigente L08.3, contrato0.7.0,2026-10-22 America/Lima: L-08 EN_CURSO; piloto BLOQUEADO.** Escritorio nativo12combinaciones/66páginas/804comprobaciones acreditado; REST03 V50/105tablas y operación HTTP verificados, intervalo máximo local318,731s, sin equivalencia a pérdida física/RPO. FIS02 RUN3f0ba758-6eb9-4d2f-b948-dd1c7de3c430 activo y protegido: ledger1/3/8,aceptaciones2,TEST_CLEANUP_RELEASED=false; fixtures de base suspendidos. Login/disponibilidad/consentimiento/inicio/GPS reales y relato TalkBack operativo; escala grande y batería pendientes. Incidencia ABIERTA: APK inicial actualizó por error el principal; `.l08` corregido no restituye su binario anterior. Medios independientes/cobertura24/7/preparación real pendientes. [Matriz vigente](evidencia/L-08/matriz-L08.md). Cortes inferiores históricos; no autorizan limpiezas durante el caso actual.

> **Estado vigente,2026-10-22,contrato0.6.2:** CAP25 VERIFICADO_LOCAL_SINTETICO (p95durable343,0684/461,8702ms), carrera de retiro y salida selectiva comprobadas; cluster55439 detenido. Ensayo físico cerrado conservado. Snapshot restaurado íntegro y API V48/actualV50 readonly UP. Faltan los demás criterios indicados abajo; **L-08 EN_CURSO, piloto BLOQUEADO**. Cortes anteriores históricos.

> **Estado vigente,2026-10-22: L-08 EN_CURSO, contrato0.6.1.** Ensayo físico cerrado y retirado; `TEST_CLEANUP_RELEASED=true`. Integridad SQL del snapshot restaurado acreditada por reconciliación de solo lectura contra todas sus huellas originales. No acredita recuperación empresarial completa, RTO/RPO, batería, accesibilidad operativa o piloto. Cortes inferiores históricos. [Matriz vigente](evidencia/L-08/matriz-L08.md).

> **Corte L08-CON-01,2026-10-22: ENSAYO CERRADO Y RETIRADO SELECTIVAMENTE.** PASS_PHYSICAL_FOREGROUND_CASE_ONLY archivado con SHA13661ee0aa87196777bfe5e373dfdd7086c864a95db5c68b5ed449fd0af53e8b. Cuenta/conductor/unidad/grupo deshabilitados,todas las sesiones revocadas,periodo retirado; servicio completado y14puntos conservados. Ledger1/3/7,aceptaciones2 y hashes de antecedentes intactos. TEST_CLEANUP_RELEASED=true; lock libre,marcador/privado ausentes,proceso/puerto cerrados,solo reverseUSB4311 retirado.144L07+36incremento1+12protegidos y final3 verificados. Pueden reanudarse pruebas dirigidas con rollback, nunca limpieza global. Batería,TalkBack operativo,recorrido/terminal calibrado,carga25,RTO/RPO y condiciones reales siguen pendientes. **L-08 EN_CURSO; piloto bloqueado.** [Cierre del caso](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/cierre-caso.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22: PASS_PHYSICAL_FOREGROUND_CASE_ONLY acreditado.** SHA-256 exacto `13661ee0aa87196777bfe5e373dfdd7086c864a95db5c68b5ed449fd0af53e8b`; copia inmutable archivada antes de retiro. Usuario completó login,disponibilidad,aceptación,consentimiento,permisoGPS,inicio,captura real visible,cierre y logout; HTTP/PG contrastados,14puntos,servicio/tramo cerrados,ubicación vigente retirada. Cero envíosGPS durante62,693s observados tras cierre. Sesión utilizada revocada;1sesión anterior pendiente de retiro selectivo. Ledger1/3/7,aceptaciones2 conservados. TEST_CLEANUP_RELEASED=false hasta verificar salida del arnés. No acredita batería,TalkBackoperativo,recorrido físico/terminal completo ni participantes reales. **L-08 EN_CURSO; piloto bloqueado.** [Archivo](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/archivo-manual-previo-retiro/verificacion-archivo.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22T21:53Z:** APKdebugaislado corregido `378bedcf4ee12a1a801ed07c9479b1acc5903246dd78c6f9b40212af9a73a588` actualizado sin borrar datos. Corrección AndroidPosition preserva indicadores medidos;8testsoriginales reprodujeron6fallos,después50dirigidos y analyze pasan. Sin otras actualizaciones de dependencias, sin relajar precisión/timeout15s/cadencia30s. Teléfono envió ubicación HTTP204 y PostgreSQL registra1punto del mismo servicio; inicio original21:28:01Z conservado, cuenta/sesión conservadas, ledger1/3/7 y aceptaciones2. Falta confirmación visual humana, cierre/logout y retiro selectivo; MANUAL_PHONE_RESULT=PENDING, TEST_CLEANUP_RELEASED=false. Sin coordenadas en evidencia. L-08 EN_CURSO; piloto bloqueado. [Evidencia](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/correccion-gps/captura-fisica-tecnica-verificada.json). Cortes inferiores históricos.

> **Corte físico2026-10-22T21:20Z:** mismo caso reanudado2, oferta `6ad87efd-c9a5-4d06-943c-72c863f76772` emitida por HTTP201 y comprobada en PostgreSQL como PENDING_RESPONSE; servicio `961b62f8-1d30-469f-a88d-5db10884e663`. Corrección del arnés comprobada ahora también en emisión real de pruebas. Aceptación/inicio/GPS/cierre personales pendientes en este corte; consentimientofalse. Ledger1/3/7,aceptaciones2,marcador/lock activos y TEST_CLEANUP_RELEASED=false. Sin otra cuenta,envío externo o cambio de regla productiva. L-08 EN_CURSO; piloto bloqueado. [Evidencia](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/oferta-observacion-9a138f03-5d3e-474d-af56-3dc7278146fb.json). Cortes inferiores históricos.

> **Corte L08-CON-01,2026-10-22: caso pausado para cargar corrección del arnés.** POST /assignments rechazado400 por ambos extremos distintos a la ruta; cero oferta/servicio acreditado por lectura exacta. Se deriva el payload de la versión existente, se recupera primero un eventual commit y se bloquean nuevos envíos inciertos.14 pruebas puras pasan; ninguna prueba de fixture/base. Ruta/tipo/cuenta conservados, ledger1/3/7,aceptaciones2,marcador presente,lock de proceso libre,TEST_CLEANUP_RELEASED=false. Login/disponibilidad previos se conservan; emisión corregida y recorrido GPS aún pendientes. L-08 EN_CURSO; piloto bloqueado. [Evidencia](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/correccion-preparacion/resultado-verificado.json). Cortes inferiores históricos.

> **Corte físico 2026-10-22: L-08 EN_CURSO.** Login/disponibilidad HTTP200 acreditados; pausa por batería1% declarada (no benchmark). Caso a999ef52-d87f-498d-9251-1581cbb72562 conservado, ledger1/3/7, aceptaciones2, marcador activo, lock de proceso libre, TEST_CLEANUP_RELEASED=false; suites de base suspendidas. Sin GPS/servicio/oferta ni nueva alta. [Archivo](evidencia/L-08/conectado-a999ef52-d87f-498d-9251-1581cbb72562/pausa-bateria-archivo-v2.json). Piloto bloqueado; cortes inferiores hist?ricos.

> **Corte L08.2,2026-10-22 ? L-08 EN_CURSO,contrato0.6.0.**112backend(81PGrollback),241CRM,59Flutter/analyze;V50/51v?lidas. MonitorAPI real2,804s/13,024s en mismaPC,cadencia1s,sin independencia/24x7. Dos restauraciones55438conservadas;3huellas de esquema pendientes,sinRTO/RPO. Poco503/TalkBacklogin confirmados. Caso f?sico a999ef52-d87f-498d-9251-1581cbb72562 preparado:una alta normalCONDUCTOR,ledger1/3/7,aceptaciones2,marcador/lock activos,TEST_CLEANUP_RELEASED=false; **suitesdebase suspendidas**. Login/GPS/servicio/bater?a,carga25,privacidad/medios/custodia/infraestructura reales pendientes.144L07+36incremento1+12protegidos correctos. **Piloto/despliegue bloqueados;final3intacto. Cortes inferiores hist?ricos.**

> **Corte de autorización L08.2, 2026-10-22 — L-08 EN_CURSO, contrato0.5.0.** L08-REST-01 autorizado en instancia nueva55438 y L08-CON-01 autorizado para máximo un CONDUCTOR6→7, caso nuevo y correo simulado. Preparación/diagnóstico en curso; sin atribuir resultados físicos u operativos por la autorización. Medio de respaldo separado, host de monitor independiente y cobertura/escalamiento24/7 siguen pendientes por confirmación expresa. **Piloto/despliegue bloqueados; final3 y cierres históricos conservados. Cortes inferiores históricos.**

> **Corte L08.1, 2026-10-21 — L-08 EN_CURSO, contrato0.4.0.** Evidencia parcial del candidato y preautenticación física archivada. 238CRM completos antes del ajuste de foco; cuatro dirigidos y dos navegadores después; 29Flutter con dobles; 25backend (cuatro salud/PG,14política temporal con reloj sintético,siete seguridad/registro offline). APK aislado .l08 instalado con autorización; usuario confirmó formulario y único rechazo503,servidor/reverse retirados. Sin GPS/cuenta operativa. Npm audit0. V48/49,ledger1/3/6,aceptaciones2,144archivos L07 y12protegidos conservados. Carga25/p95,RTO-RPO/medio independiente,monitor/alertas/cobertura24/7,retiro por finalidad y recorridos físicos operativos/lector pendientes. **Piloto/despliegue bloqueados;final3 intacto. Cortes inferiores históricos.**

> **Estado vigente2026-10-21: L-08 EN_CURSO**,autorizado por OK VERDE después del cierre funcional local L07. Contrato0.1.0 y matriz abiertos. L07 preservado:148archivos/915fuentes/51artefactos verificados;test V48/49,ledger1/3/6,aceptaciones2,sin escrituras de base al iniciar. Trabajo:candidato/regresiones,seguridad,Chrome/Edge/accesibilidad,carga/RTO-RPO/Android físico según decisiones y medios disponibles. CAP-L08-01,DISP-L08-02,OPS-L08-03 consultadas;no objetivos acreditados por elección o dobles. **Piloto/despliegue bloqueados;final3 intacto.** Cortes inferiores históricos.

> **Estado vigente2026-10-21: L-07 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Contrato0.4.0,CI01…10 acreditados con límites:[acta](actas/L-07-cierre-funcional-local-2026-10-21.md),[matriz](evidencia/L-07/matriz-L07.md),[archivo](evidencia/L-07/cierre/README.md).190 backend distintos (157PG/MockMvc+33unitarios),22CRM/42Flutter con dobles,4Edge/3Flutter HTTP y13 etapas. Una alta CONDUCTOR autorizada retirada;ledger1/3/6,cupos0/1/14,aceptaciones2,V48/49 migraciones válidas,TEST_CLEANUP_RELEASED=true. Final3 y cierres anteriores preservados. Privacidad/retiro de otras finalidades,personas/custodia/infraestructura/canal externo/validación física reales pendientes;piloto bloqueado. **L-08 NO INICIADA/NO AUTORIZADA,OK VERDE separado.** Cortes inferiores históricos.



> **Corte técnico L07.2…4, 2026-10-21 — L-07 EN_CURSO, contrato0.3.0.**187 backend distintos del lote,22 CRM y42 Flutter con dobles; builds backend/CRM/APK y análisis correctos. V48/49 migraciones válidas solo ruta_fija_test, ledger1/3/5,aceptaciones2 y auditoría anterior conservados. Reportes/versiones/retención/resúmenes/inicio/preferencias implementados; falta caso TCP autorizado máximo1 CONDUCTOR5→6, carreras, retorno y retiro/postflight. Piloto bloqueado; L08 no iniciada/no autorizada. Cortes inferiores históricos.





> **Corte L07.1, 2026-10-21 — L-07 EN_CURSO, contrato0.2.0.** Auditoría aislada/metadatos seguros/denegaciones:88 backend (69 PG/MockMvc rollback+19 unitarias),6 CRM con dobles, builds backend/CRM acreditados. V44,ledger1/3/5,aceptaciones2 y auditoría282 filas/hash conservados. Caso L07-CON-01 máximo1 CONDUCTOR y REP-RET-L07-03 emisiones90d aprobados, todavía no ejecutados. Continúan reportes, retención, dashboard, móvil y recorrido conectado. Piloto bloqueado; L08 no iniciada/no autorizada. Cortes inferiores históricos.







> **Estado vigente 2026-10-21: L-07 EN_CURSO, autorizado por OK VERDE después de L06.** Contrato0.1.0 y matriz abiertos. Cierre L06 preservado:191 archivos/838 fuentes verificados; preflight ruta_fija_test/rf_test,V44 y45 migraciones válidas,ledger1/3/5,aceptaciones2. Sin escrituras de base/migraciones o altas persistentes nuevas en el inicio. Trabajar reportes/versiones, auditoría/retención e inicio/perfil por incrementos; conservar PII oculta y decisiones previas. Piloto bloqueado; **L-08 NO INICIADA / NO AUTORIZADA**. Los cortes inferiores son históricos.









> **Estado vigente 2026-10-21: L-06 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](actas/L-06-cierre-funcional-local-2026-10-21.md), [matriz](evidencia/L-06/matriz-L06.md), [archivo](evidencia/L-06/cierre/README.md). Contrato0.3.0, CI01…10 acreditados localmente;72 backend/14 CRM/49 Flutter,6 etapas CRM/5 Flutter reales con correo simulado. Una alta CONDUCTOR retirada:ledger1/3/5,cupos0/1/15,aceptaciones2. Retorno/postflight y preservación correctos;TEST_CLEANUP_RELEASED=true. Final3 intacto. Canal push real/VT-Q074 y condiciones previas de piloto siguen pendientes; no se acreditan mediante dobles. **Piloto bloqueado; L-07 NO INICIADA / NO AUTORIZADA, OK VERDE separado.** Los cortes inferiores son históricos.











> **Estado vigente 2026-10-21: L-06 EN_CURSO, incremento1 acreditado en API/componentes.**72 backend distintos,14 CRM,49 Flutter; JAR/CRM/APK correctos. V43/V44 solo test, ledger1/3/4 y dos aceptaciones preservados. INC-L06-01/02 aprobadas. L06-CON-01 autorizado para máximo un CONDUCTOR nuevo; todavía sin ejecutar. Faltan conectado/carreras/retorno/retirada y cierre. Push real no configurado ni acreditado; piloto bloqueado y L-07 NO INICIADA. Los cortes inferiores son históricos.











> **Estado vigente2026-10-20: L-06 EN_CURSO, inicio por OK VERDE.** [Contrato0.1](contratos/L-06.md), [matriz](evidencia/L-06/matriz-L06.md), [ejecución](ejecucion/L-06.md). L-05 y sus sellos conservados. Se trabaja en incidencias/comunicados; PostgreSQL actual detenido y dos precisiones operativas pendientes, sin impedir avances independientes. **Piloto bloqueado y L-07 NO INICIADA/NO AUTORIZADA.** Los cortes inferiores son históricos.











> **Estado vigente2026-10-20: L-05 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](actas/L-05-cierre-funcional-local-2026-10-20.md), [contrato0.6](contratos/L-05.md), [matriz CI01..10](evidencia/L-05/matriz-L05.md), [archivo](evidencia/L-05/cierre/README.md). V42 test;79 regresiones backend finales,16 CRM/16 Flutter finales,5 etapas CRM/9 Flutter conectadas; dos altas autorizadas retiradas y TEST_CLEANUP_RELEASED=true. Ledger1/3/4,cupos0/1/16,aceptaciones2; finales/anteriores preservados y final3 intacto. **Piloto bloqueado; L-06 NO INICIADA/NO AUTORIZADA. OK VERDE separado.** Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, relevo y multitramos acreditados localmente.** [Contrato0.5](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [incremento5](evidencia/L-05/incremento-5-relevo/README.md). V42 test;141 backend/47 CRM/69 Flutter y builds correctos. Ledger1/3/2, aceptaciones2/final3 conservados. [L05-CON-01](operacion/L-05-caso-conectado-autorizacion.md) autorizado para máximo dos CONDUCTOR, sin ejecutar todavía. Faltan carrera HTTP/recorrido conectado/retorno/postflight/cierre; piloto bloqueado y **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, interrupción y atención acreditadas localmente.** [Contrato0.4](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [incremento4](evidencia/L-05/incremento-4-interrupcion/README.md). V40 test;124 backend/36 CRM/59 Flutter, análisis y builds/JAR/APK correctos. Ledger1/3/2, aceptaciones2 y final3 conservados, cero altas persistentes. Restan relevo/multitramos, carreras de negocio, alerta de inicio perdido, conectado autorizado/retorno/cierre. Piloto bloqueado; **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-20: L-05 EN_CURSO, tiempos y concurrencia verificados localmente.** [Contrato0.3](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [incremento3](evidencia/L-05/incremento-3-tiempos/README.md). V39 test;46 backend/20 CRM/24 Flutter y builds/JAR/APK correctos. Correcciones ADMIN/historial original y solicitudes propias; interbloqueo y traducción401 corregidos. Ledger1/3/2 y aceptaciones2 intactos, cero altas persistentes. Interrupción SRV-INT-L05-05 aprobada, pendiente implementar. Restan multitramos/relevo, carreras de negocio/conectado/retorno/cierre. Piloto bloqueado; **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO, GPS L05.4 verificado localmente.** [Contrato](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [evidencia](evidencia/L-05/incremento-2-gps/README.md). V38 test;77 backend/38 Flutter/20 CRM, builds/JAR/APK correctos. GPS30s foreground sin cola, acceso propio verificado y purga≤7d; CI01/03/07/08 acreditados con límites de matriz. Ledger1/3/2 y aceptaciones2 conservados, cero altas persistentes; final3 intacto. Pendientes interrupción/correcciones/relevo/carreras/conectados/retorno/cierre. Piloto bloqueado; **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO, L05.1 acreditado en API/PG.** [Contrato](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [evidencia](evidencia/L-05/incremento-1-ejecucion/README.md). V36 test; inicio propio con consentimiento y permiso, tramos originales y captura solo durante ejecución.57 backend distintos,18 Flutter,11 CRM/typecheck/build y analyze correctos. Ledger1/3/2, aceptaciones2, sin altas persistentes; L-04/final3 intactos. Acceso a puntos GPS-ACCESS-L05-04 aprobado. Pendientes interrupción/correcciones/relevo y GPS30s/historial/acceso/purga/conectados/cierre. Piloto bloqueado; **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente 2026-10-19: L-05 EN_CURSO por OK VERDE.** [Contrato](contratos/L-05.md), [matriz](evidencia/L-05/matriz-L05.md), [decisiones confirmadas](../specs/decisiones-L05-2026-10-19.json). L-04 cerrado se conserva como antecedente. GPS obligatorio para todos los servicios; relevo asumido online por el entrante; retención de puntos máximo siete días, también incidentales. Piloto bloqueado; **L-06 NO INICIADA / NO AUTORIZADA**. Los cortes siguientes son históricos.











> **Estado vigente al 2026-10-19: L-04 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO, CI01…10 acreditados.** [Acta](actas/L-04-cierre-funcional-local-2026-10-19.md), [contrato0.3.0](contratos/L-04.md), [matriz final](evidencia/L-04/matriz-L04.md). V35 solo pruebas;121 backend,28 CRM,15 Flutter y recorrido conectado automatizado Angular/Edge + repositorio Flutter por HTTP/PG reales, correo simulado. Un CONDUCTOR nuevo autorizado retirado; ledger1/3/2, cupos0/1/18, aceptaciones2 e historia preservados. Retorno/postflight/12 huellas correctos; final3 intacto. **L-05 NO INICIADA / NO AUTORIZADA**, requiere OK VERDE separado. Piloto bloqueado. Los cortes siguientes son históricos.











> **Estado vigente al 2026-10-19: L-04 EN_CURSO, CI01…04 verificados localmente.** [Contrato0.2.0](contratos/L-04.md), [ejecución](ejecucion/L-04.md), [matriz](evidencia/L-04/matriz-L04.md). Aceptación/plazo y márgenes por tipo/candidatos implementados; V34 solo pruebas. L-01…03 y final3 conservados. Pendientes servicio/oferta/reasignación e integración final. Piloto bloqueado; **L-05 NO INICIADA / NO AUTORIZADA**. Los cortes anteriores se conservan como antecedentes.











> **Estado vigente al 2026-10-17: L-01 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](actas/L-01-cierre-funcional-local-2026-10-17.md) y [matriz final](evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Los cortes anteriores siguientes se conservan como antecedentes. Piloto bloqueado; L-02 sin iniciar y sin autorizar.











# Backlog de producto — Ruta Fija V3.1











**Corte:** 14/10/2026 (America/Lima)  





**Estado:** L-00 VERIFICADA tras aprobación provisional de Gmail SMTP y excepción de dominio para el piloto. Configuración SMTP/credenciales y entrega controlada se reservan a L-01; validación física a L-08. L-01…L-08 siguen sin iniciar; avanzar requiere solicitar y recibir OK VERDE.





**Alcance:** integrar las reglas de `Ruta_Fija_Login_Inicio_Onboarding_Fase3_2026-10-12` con el proyecto local `solucion_1_ruta_fija_3`  





**Modo de trabajo:** local, sin comandos de Git, commits, ramas, worktrees ni GitHub











## 1. Objetivo











Completar y verificar el producto Ruta Fija conforme al registro v3.1, preservando lo que ya funciona en el monolito modular y cerrando el recorrido de acceso, incorporación y operación entre backend, CRM, Android y PostgreSQL. El dossier cubre más que login: contiene 109 reglas vigentes para 17 módulos y 171 criterios registrados. Por eso el backlog abarca el proceso de producto completo definido por los lotes L-00 a L-08.











El orden de trabajo prioriza contratos y reglas antes de cambios de código; cada lote termina en un comportamiento observable con evidencia. No se considera terminado un módulo por tener pantallas, endpoints, builds o resultados históricos.











## 2. Relación entre el dossier y el proyecto abierto





| Evidencia | Lectura para este backlog |





|---|---|





| `Ruta_Fija_Reglas_Negocio_Fase_3.md` y `06_Metodo_de_trabajo/ruta_fija_kit/specs/registro.json` | Fuente normativa v3.1. Distingue reglas vigentes, decisiones sustituidas, asuntos abiertos y aceptación. El registro incluido tiene la misma huella que el registro v3.1 incluido en los documentos fuente. |





| 06_Metodo_de_trabajo/ruta_fija_kit/docs/seguimiento/tareas.json | Define nueve lotes L-00…L-08, dependencias y condiciones de salida. El archivo del kit se conserva; el tablero local registra L-00 como VERIFICADA con la decisión Gmail y evidencia local. |





| `docs/plan_7_dias.md` del kit | Calendario propuesto, no estimación validada ni promesa de entrega. Se conserva la secuencia por dependencias, sin asumir que cabe en siete días. |





| `05_Fuentes_relacionadas/` | Comparé localmente sus 207 fuentes con las rutas correspondientes del proyecto mediante SHA-256: 207 coinciden y 0 difieren. `LEEME.md` y `manifest_fuentes.json` son los dos archivos de paquete que no aparecen en el proyecto. El dossier no es un parche de código independiente. |





| `REFERENCIAS_NO_RECUPERADAS.md` | Las 12 rutas que el dossier declara no recuperadas existen en el proyecto abierto. Se consideran localizadas; hay que reconciliar su contenido y vigencia dentro de L-00, no reconstruirlas desde memoria. |





| Código y documentación local | La base actual es monolito modular: Java 21/Spring Boot, Angular, Flutter Android, PostgreSQL/Flyway y JPA. El login web/móvil, sesiones, roles y controles de empresa ya tienen implementación. |











### Estado observado y límites de evidencia











- **Reutilizar y volver a verificar:** login separado para web y móvil, refresh rotativo, logout, roles `SUPER_ADMIN`, `ADMIN`, `CONDUCTOR`, autorización por rol y organización, y filtros por usuario/empresa. La baseline local de L-00 ejecutó backend, CRM y Flutter; resultados y límites están en `docs/evidencia/L-00/baseline.md`.





- **Brecha de onboarding:** en el código inspeccionado no encontré autorregistro/invitación, verificación de correo por OTP, recuperación/cambio de contraseña ni aceptación de términos. La creación de una organización y su primer ADMIN están separadas; el usuario CONDUCTOR y su ficha también se crean/vinculan en pasos separados. Se trata de hallazgo estático, no de una afirmación de ausencia universal.





- **Integración operativa:** la revisión local del 12/10 registra que el formulario CRM solo crea `ADMIN_DIRECT`; el camino de `MOBILE_CONFIRMATION` existe en API y app, pero no está conectado desde ese formulario. También registra validación funcional, recuperación y entrega todavía pendientes.





- **Conflicto de alcance:** el proyecto contiene cola/capacidades offline documentadas en M8/V12; el registro V3.1 excluye funcionamiento y colas offline. Su inventario y retiro/desactivación controlados pertenecen a los lotes posteriores; no se amplían ni se tratan como requisito vigente.





- **Roles:** el contrato operativo vigente contiene exclusivamente `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. Los documentos previos que describen otros roles quedan identificados como históricos, no como autorización o backlog vigente. V6 permanece como transformación histórica de datos.





- El registro contiene 118 reglas para 17 módulos (**109 vigentes y 9 sustituidas**) y 171 criterios (**162 activos `ESPECIFICADO_NO_EJECUTADO` y 9 sustituidos**). La matriz inicial traza sus 289 registros; las suites del 2026-10-13 son baseline del checkout y no prueban por sí mismas aceptación funcional V3.1.





- El PostgreSQL local de pruebas responde al preflight y se mantuvo separado de la base de desarrollo. No se envió correo OTP; Gmail está seleccionado pero SMTP no está configurado y la entrega no está verificada. No se conectó un dispositivo Android; la prueba física queda en L-08.











La revisión general local `docs/REVISION_GENERAL_2026-10-12.md` se utiliza como diagnóstico fechado, no como una medición viva. No ejecuté Git y no traslado al backlog sus datos históricos de ramas o commits.











## 3. Reglas que gobiernan todos los lotes











| Tema | Regla de planificación |





|---|---|





| Arquitectura | Mantener el monolito modular Spring Boot/PostgreSQL y los clientes CRM Angular y Android Flutter. No introducir microservicios ni infraestructura por conveniencia. |





| Roles y empresas | Solo `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`; una empresa activa y un rol activo por persona. `ADMIN` opera dentro de su empresa; el tenant se deriva y valida en servidor. |





| Acceso | Preservar separación de sesión web/móvil, revocación efectiva, controles de origen, refresh seguro y auditoría sin secretos. No registrar contraseñas, OTP, tokens ni códigos de emergencia. |





| OTP e invitación | OTP por correo: 5 min; 3 envíos por persona en ventana móvil de 60 min; 2 fallos y bloqueo de 30 min. Invitación: 6 dígitos, un destinatario/uso y máximo 5 días; límites y bloqueo de invitación independientes de OTP. El envío debe ser real, con cuota S/0 comprobada. |





| Operación | El conductor debe aceptar la oferta; vence a los 10 min o al inicio previsto, lo que ocurra primero. No empezar un servicio sin asignación confirmada. Cerrar un servicio libera ocupación, no el vínculo de unidad. |





| Flota y mantenimiento | Unidad aprobada/vinculada antes de ofertar; resolver cardinalidad conductor/unidad antes de imponer una restricción no decidida. Mantenimiento bloquea el día completo en America/Lima y exige resolver servicios solapados. |





| Ubicación | Consentimiento y primer plano durante un servicio iniciado; intento cada 30 s y detalle por 7 días. No prometer captura con app cerrada ni seguimiento que no esté autorizado. Los umbrales de terminal requieren calibración/prueba. |





| Comunicación, datos y cierre | Una incidencia cerrada no se reabre en el MVP. `Leído` no equivale a aceptación. Auditoría operativa: 6 meses, con proceso de purga y tratamiento de respaldos aún por definir. |





| Restricciones de producto | S/0; sin RENIEC, MTC, QR, búsqueda pública ni cola/funcionamiento offline. No copiar como reglas vigentes alternativas antiguas de pantallas, manuales o diagramas. Stitch es referencia visual; no se modifica en esta tarea. |





| Trazabilidad local | Sustituir el supuesto de commit del kit por registro de fecha/hora local, rutas afectadas, comandos y resultados ejecutados, huellas SHA-256 del snapshot relevante y respaldo/retorno. No ejecutar comandos de Git ni publicar a GitHub. |











Los asuntos abiertos son bloqueos únicamente para los lotes que dependen de ellos. No se deben llenar por inferencia ni copiar del catálogo histórico.











## 4. Fases y dependencias











| Fase / lote | Prioridad | Dependencia | Resultado que permite avanzar | Situación inicial |





|---|---:|---|---|---|





| Fase 0 — L-00 Base verificable y entorno | P0 | — | Mapa del checkout y requisitos, contratos iniciales, baseline local y bloqueos asignados. | Baseline backend, CRM y Flutter pasa; Gmail SMTP S/0 seleccionado provisionalmente, con excepción de dominio aprobada; configuración y envío en L-01. VERIFICADA. |





| **1 — L-01 Acceso, empresa, usuarios y permisos** | P0 | L-00 | Recorridos de acceso e identidad seguros por rol, OTP/invitación/recuperación con límites y aislamiento comprobado. | VERIFICADA: FUNCIONAL_LOCAL_PILOTO_BLOQUEADO; acta y adenda SC03/SC07 1.1.0, requisitos reales previos al piloto pendientes. |





| **2 — L-02 Evolución de incorporación y elegibilidad** | P1 | L-01 VERIFICADA y OK VERDE recibido | Los requisitos de acceso y aprobación personal se verifican en L-01; la evolución posterior no los sustituye. | **VERIFICADA 2026-10-19 — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**; diez criterios locales acreditados, períodos/revisión/vigencia/suspensión/corrección y clientes conectados. [Acta](actas/L-02-cierre-funcional-local-2026-10-19.md). |





| **3 — L-03 Grupos, flota, mantenimiento y rutas** | P1 | L-01/L-02 cerrados; OK VERDE recibido | Vínculos y disponibilidad coherentes; mantenimiento y rutas preservan reglas e historial. | **VERIFICADA 2026-10-19**, FUNCIONAL_LOCAL_PILOTO_BLOQUEADO; diez criterios, MNT-01A/02A, V32 solo pruebas y caso técnico cerrado selectivamente. |





| **4 — L-04 Ofertas y asignaciones** | P1 | L-02 y L-03 cerrados; OK VERDE recibido | ADMIN origina oferta, CONDUCTOR acepta y servidor resuelve vencimientos/conflictos. | **VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**, [acta](actas/L-04-cierre-funcional-local-2026-10-19.md); criterio local completo. |





| **5 — L-05 Servicios, tramos y ubicación** | P1 | L-04 cerrado y OK VERDE recibido | Inicio/cierre/relevo online, historia consistente y GPS limitado. | **VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**. CI01..10 acreditados; [acta](actas/L-05-cierre-funcional-local-2026-10-20.md) y [matriz](evidencia/L-05/matriz-L05.md). L-06 recibió autorización posterior; véase su estado vigente. |





| **6 — L-06 Incidencias y comunicados** | P1 | L-01 y L-05 | Incidencias, avisos y lectura tienen estados observables y no alteran aceptación/servicio indebidamente. | **VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**. [Acta](actas/L-06-cierre-funcional-local-2026-10-21.md) y [matriz](evidencia/L-06/matriz-L06.md); conectado/carreras/retorno/retirada acreditados. Canal externo real pendiente antes de habilitar; L-07 no iniciada. |





| **7 — L-07 Control, reportes, auditoría e inicio** | P2 | L-02…L-06 | Paneles,perfil,reportes/auditoría reflejan datos reales,aislamiento,versiones y retención. | **VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**,contrato0.4.0;[acta](actas/L-07-cierre-funcional-local-2026-10-21.md). L08 EN_CURSO, contrato0.4.0. |

| **8 — L-08 Aceptación, restauración y entrega local** | P0 de salida | L-07 | Candidato local reproducible, regresión aprobada, recuperación probada y checklist sin bloqueos. | **EN_CURSO**, contrato0.7.0; [matriz](evidencia/L-08/matriz-L08.md). Escritorio/CAP25 acreditados con límites; FIS02 protegido e incidencia principal abierta, piloto bloqueado. |











L-02 y L-03 pueden ejecutarse en paralelo después de L-01 si sus contratos están fijados. L-04 espera a ambos; L-05 a L-04; L-06 a L-01 y L-05; L-07 integra L-02…L-06; L-08 cierra evidencias. El calendario de siete días del kit no altera estas dependencias ni constituye compromiso de duración.











## 5. Product backlog detallado











### Fase 0 — L-00: Base verificable y contrato común











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-00.1 | Como equipo, quiero clasificar cada capacidad de los 17 módulos frente al código real como reutilizable, incompatible, parcial o ausente. | [`L-00-trazabilidad.csv`](ejecucion/L-00-trazabilidad.csv) contiene una fila por registro fuente: 118 reglas (109 vigentes y 9 sustituidas) y 171 criterios (162 activos y 9 sustituidos), 289 filas en total. Cada fila incluye módulo, lote, ruta candidata, evaluación estática y evidencia; la guía [`L-00-trazabilidad.md`](ejecucion/L-00-trazabilidad.md) define límites y tratamiento de sustituciones. La conformidad funcional se verifica en cada lote; no se infiere de una ruta. |





| PB-00.2 | Como responsable de integración, quiero reconciliar dossier, kit y documentación ya presente en el proyecto. | Se registran las 207 coincidencias de fuentes, los dos archivos de paquete ausentes y las 12 referencias localizadas; cada fuente conserva precedencia/fecha y el código exportado no se confunde con una implementación nueva. |





| PB-00.3 | Como consumidor CRM/Android, quiero un contrato por proceso antes de repartir cambios. | Cada contrato define reglas/AC, precondiciones, propietario de datos, estados/transiciones, permisos, tenant, errores, DTO/API, consumidores, migración si aplica, excepciones y pruebas. Una persona mantiene cada artefacto compartido. |





| PB-00.4 | Como responsable local, quiero una línea base atribuible sin Git. | Se capturan versiones desde manifests, configuración, rutas, disponibilidad del entorno y comandos ejecutados; un snapshot local de fuentes tiene manifiesto SHA-256, huella del archivo y prueba de extracción que compara los hashes. Se excluyen dependencias, artefactos generados y configuración secreta; el retorno de base de datos/migraciones se valida en L-08. Todo campo no observado queda explícitamente pendiente. |











**Salida L-00:** mapa e inventario local documentados; matriz de 289 filas (271 vigentes y 18 sustituidas); baseline backend/CRM/Flutter ejecutada; 10 asuntos abiertos y 21 verificaciones distribuidos por lote; contrato, decisión provisional Gmail, snapshot fuente y orden de cambios registrados. L-00 está VERIFICADA; la configuración SMTP y entrega OTP real se ejecutan en L-01, y la prueba física en L-08. Ver [ejecución](ejecucion/L-00.md), [matriz](ejecucion/L-00-trazabilidad.md), [evidencia base](evidencia/L-00/baseline.md) y [decisión Gmail](evidencia/L-00/decision-Gmail-2026-10-14.md).











### Fase 1 — L-01: Acceso, empresa, usuarios y permisos











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-01.1 | Como SUPER_ADMIN o ADMIN autorizado, quiero conservar el login web y la administración de organizaciones/usuarios existentes. | Login, refresh, logout, sesión revocada, rol actualizado, cuenta/empresa inactiva y acceso por tenant conservan contrato y se cubren con regresión. No se reimplementa la sesión existente sin una brecha demostrada. |





| PB-01.2 | Como titular de la plataforma, quiero un aprovisionamiento controlado de SUPER_ADMIN, organización y primer ADMIN. | Se documenta quién puede ejecutar cada alta/recuperación; ADMIN no crea ni habilita su propia cuenta; un tenant o ID manipulado no amplía alcance. OPEN-SUPERADMIN-RECOVERY se resuelve antes de piloto y sin autorización por ADMIN subordinado. |





| PB-01.3 | Como persona invitada, quiero verificar mi canal y completar el primer acceso. | OTP real, expiración y límites exactos; aceptación de términos registrada con versión/fecha según contrato; no se activa acceso si falla la verificación. OPEN-OTP-PROVIDER se valida con condiciones/cupo real S/0. |





| PB-01.4 | Como titular de una invitación, quiero usar un código individual y recuperarme de forma segura. | Código de invitación de un uso, destinatario único, máximo 5 días y control separado de OTP; bloqueo/rate-limit conforme al registro; recuperación revoca sesiones según el caso y nunca expone secretos. |





| PB-01.5 | Como ADMIN, quiero revisar y resolver la incorporación personal del conductor en L-01. | Identidad preliminar separada, expediente restringido, observaciones/subsanación, rondas, aprobación/rechazo e historial; provisión posterior con grupo activo, sin habilitar conducción por correo/invitación. Q-003 y las aclaraciones del titular hacen obligatorio este tramo antes de cerrar L-01. Las referencias PB-02.1…3 no permiten diferirlo. |











**Salida L-01:** recorrido por los tres roles y aprobación personal documental; denegaciones cruzadas entre empresas; límites OTP/invitación; recuperación autorizada; pruebas negativas y de revocación. Ningún total de pruebas sustituye términos aprobados, ensayo del titular o evidencias obligatorias pendientes.











### Fase 2 — L-02: evolución de incorporación, VERIFICADA LOCAL











**Corrección de alcance confirmada el 2026-10-15:** los tramos de acceso, solicitud, revisión y aprobación/rechazo personal descritos en PB-02.1…3 se implementan y verifican obligatoriamente en L-01 (PB-01.5). Se conservan sus IDs para trazabilidad. Esta tabla histórica no es autorización para iniciar L-02 ni para trasladar pendientes de L-01; la separación entre aprobación personal, grupo, unidad y operación permanece vigente.











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-02.1 | Como conductor invitado, quiero completar mi cuenta y expediente desde Android. | Una misma identidad recorre invitación, primer acceso, verificación y términos sin duplicar cuenta, empresa o rol; pantalla y estados derivan del contrato v3.1. |





| PB-02.2 | Como ADMIN, quiero revisar documentos y datos presentados por conductores de mi empresa. | OPEN-REQUIRED-DOCUMENTS se cierra con modalidad y catálogo exacto, campos, vigencias y excepciones antes de marcar documentos obligatorios. No se inventa OCR, foto de placa ni verificación externa. |





| PB-02.3 | Como ADMIN, quiero observar/subsanar, aprobar o rechazar una incorporación. | Estados de cuenta, documentos, solicitud de incorporación, aprobación y membresía no se mezclan; queda responsable, motivo y auditoría; solo el ADMIN de la empresa decide. |





| PB-02.4 | Como operador, quiero asociar la identidad CONDUCTOR con la ficha operativa del conductor. | Se valida cuenta activa, rol y tenant; un conductor no aprobado o sin unidad vigente no recibe oferta. No se habilita un selector de pertenencia simultánea. |











El inventario histórico de 14 vistas CRM sirve para rastrear cobertura, no obliga a recrear catorce pantallas si el flujo v3.1 las reemplaza. Cada vista conservada debe mapearse a usuario, estado, acción, error y referencia visual Stitch.











**Salida acreditada L-02, 2026-10-19:** CI-L02-01…10 verificados en la [matriz final](evidencia/L-02/matriz-final-funcional-local-2026-10-19.md). Modelo temporal exclusivo, revisión separada y habilitación explícita sin perder consulta limitada; prueba conectada CRM/Flutter/HTTP/PG con correo simulado. PB-02.1…3 se reutilizan desde L-01; PB-02.4 y su evolución quedan comprobados. Piloto bloqueado y L-03 sin iniciar.











### Fase 3 — L-03: Grupos, flota, mantenimiento y rutas











**Inicio y cierre local 2026-10-19.** [Contrato por incrementos0.2.0](contratos/L-03.md). Q-026 permite varios grupos por conductor; CARD-01 mantiene una sola unidad vigente. **MNT-01A/MNT-02A aprobadas e implementadas**: bloqueo hasta retorno ADMIN y liberación el día siguiente al retorno real. No se repiten esas decisiones. [Matriz final](evidencia/L-03/matriz-final-funcional-local-2026-10-19.md), sin ampliar el piloto ni iniciar L-04.











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-03.1 | Como ADMIN, quiero gestionar grupos, conductores, vehículos y disponibilidad dentro de mi empresa. | Autorización tenant en API y acceso por objeto; no se filtra ni modifica información de otra empresa. Estados no se cambian desde un formulario si pertenecen a una transición operativa. |





| PB-03.2 | Como ADMIN, quiero vincular unidades elegibles a conductores y conservar historial de placa/vínculo. | Fechas Lima y CARD-01 confirmada: una sola unidad vigente por CONDUCTOR y exclusividad inversa. Fin de vínculo, placa textual/snapshots y consulta histórica; regresiones y carreras L-02 reutilizadas y delta de unicidad canónica/corrección material verificado en L-03. |





| PB-03.3 | Como ADMIN, quiero programar mantenimiento y evitar una asignación incompatible. | Bloqueo de día completo y resolución de cada servicio solapado antes de confirmar; retorno efectivo al servicio; pruebas de bordes y concurrencia preservan la regla vigente. |





| PB-03.4 | Como operador, quiero conservar rutas versionadas y sus relaciones con servicios. | Cambios crean historia consultable y no alteran asignaciones pasadas; ubicación de rutas/mantenimiento en navegación se decide con la matriz de vistas, sin hacerlos desaparecer. |











**Salida acreditada PB-03.1…4:** grupos múltiples sin permisos/vínculos implícitos; retirada con compromisos; placa/unidad/períodos conservados; mantenimiento preventivo/urgente y retorno expreso; catálogo/captura/sustitución de ruta con nueva aceptación y consulta propia; declaración de disponibilidad persistente. Backend97 distintos, CRM20, Flutter28/analyze/builds, navegador CRM real y rechazo móvilTCP por rol. Consulta móvil positiva acreditada por backend/PG y cliente separados, no TCP/Android físico. Ledger1/3/1 y aceptaciones2 preservados; sin SMTP externo ni limpieza global. [Acta](actas/L-03-cierre-funcional-local-2026-10-19.md). Piloto bloqueado, L-04 no iniciada.











### Fase 4 — L-04: Ofertas y asignaciones











**Inicio autorizado 2026-10-19.** [Contrato por incrementos](contratos/L-04.md), [API inicial](contratos/L-04-ofertas-API-v0.1.0.md), [márgenes/candidatos0.2.0](contratos/L-04-margenes-API-v0.2.0.md). L-03 cerrado se reutiliza sin repetir ensayos ni reabrir CARD-01/MNT-01A/MNT-02A. Los incrementos1/2 no equivalen al cierre de PB-04.1…4; separación servicio/oferta/reasignación e integración final siguen en la matriz.











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-04.1 | Como ADMIN, quiero emitir una oferta para conductor y unidad elegibles desde el CRM. | El CRM permite la modalidad `MOBILE_CONFIRMATION`; valida conductor aprobado, unidad vigente, disponibilidad, ruta y tenant antes de persistir. |





| PB-04.2 | Como CONDUCTOR, quiero aceptar o rechazar explícitamente una oferta propia. | Aceptar y empezar el servicio son transiciones separadas; no se inicia por leer un aviso ni por acción de ADMIN. La app solo modifica sus propias ofertas. |





| PB-04.3 | Como sistema, quiero resolver vencimientos y concurrencia sin depender de notificaciones. | La oferta expira a los 10 min o al inicio previsto, lo que ocurra primero; doble aceptación/solape se resuelve en servidor con transacción/constraint e idempotencia; un fallo de aviso no extiende la oferta. |





| PB-04.4 | Como ADMIN, quiero cancelar/reasignar sin perder historia ni vínculo de unidad. | Motivo y actor auditados; reservas coherentes; el cierre/cancelación del servicio no elimina el vínculo vigente conductor-unidad. |











### Fase 5 — L-05: Servicios, tramos y ubicación











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-05.1 | Como CONDUCTOR, quiero iniciar y finalizar un servicio confirmado cuando estoy conectado. | Inicio online solo tras aceptación y precondiciones; declarar cierre y corroboración de llegada son datos distintos; reintento no duplica operación ni altera historia. |





| PB-05.2 | Como ADMIN autorizado, quiero registrar interrupción, corrección o relevo sin reescribir el pasado. | Responsable y tiempos por tramo se conservan; conflictos/acciones tardías se validan en servidor; no se fabrican cierres ni estados de disponibilidad. |





| PB-05.3 | Como conductor, quiero compartir ubicación solo con consentimiento durante servicio activo. | Permiso explicado; captura/envío solo en primer plano y con servicio iniciado; objetivo de intento cada 30 s, datos detallados por 7 días, solicitud de puntos según verificación. No hay captura con app cerrada. |





| PB-05.4 | Como equipo, quiero alinear las funciones offline existentes con V3.1. | Se inventarían consumidores/tablas/recibos M8/V12; se define migración controlada para retirar o desactivar colas y acciones offline sin perder auditoría necesaria; desconexión presenta estado claro y no confirma acciones no guardadas. |





| PB-05.5 | Como operador, quiero corroborar terminales con evidencia acotada. | Probar radio ≤200 m, antigüedad ≤120 s y precisión ≤100 m; registrar que la evidencia no demuestra recorrido ni fraude. OPEN-TRACKED-SERVICES define qué tipos de servicio requieren GPS antes de bloquear inicio. |











### Fase 6 — L-06: Incidencias y comunicados











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-06.1 | Como conductor/ADMIN, quiero reportar y atender incidencias según rol y empresa. | Riesgo y responsable siguen la regla; cierre terminal no se reabre. OPEN-CLOSED-INCIDENT-PROCEDURE define recurrencia/cierre erróneo por caso nuevo e historial, nunca por reabrir el anterior. |





| PB-06.2 | Como ADMIN, quiero enviar comunicados a una audiencia autorizada. | Audiencia aislada por empresa/grupo; entrega, fallo y lectura son estados distintos; leer no acepta oferta, no reemplaza OTP y no bloquea el servicio por falta de lectura. |





| PB-06.3 | Como operador, quiero conocer el fallo real de entrega del aviso. | Canal, cuotas y recursos se comprueban; el sistema no registra entrega si el proveedor falló ni depende de pago no autorizado. |











### Fase 7 — L-07: Control, reportes, auditoría e inicio











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-07.1 | Como SUPER_ADMIN/ADMIN/CONDUCTOR, quiero ver el resumen y perfil que corresponden a mi rol. | Cada indicador deriva de operaciones persistidas y alcance permitido; información personal/operativa se limita por empresa y conductor. |





| PB-07.2 | Como ADMIN, quiero consultar y exportar reportes reproducibles. | Filtros/versión/corte identificados; CSV/XLSX/PDF conservan el contenido; exportación no cruza empresas ni incluye datos no autorizados. |





| PB-07.3 | Como auditor, quiero revisar eventos íntegros y protegidos. | Eventos de alta, acceso, decisión, oferta, servicio y cambio tienen actor/empresa/fecha; acceso de solo lectura por rol; sin contraseñas, OTP o tokens. |





| PB-07.4 | Como responsable de datos, quiero aplicar retención por finalidad. | GPS detallado 7 días y auditoría operativa 6 meses; OPEN-RETENTION-PURPOSE define categorías, purga y respaldos; las tareas VT especifican términos, documentos y evidencia sin extender plazos por copia accidental. |





| PB-07.5 | Como equipo, quiero documentación coherente con el producto actual. | Manuales, estados de fase, matrices de trazabilidad y contratos usan los tres roles actuales, contratos/rutas presentes y exclusiones V3.1; referencias históricas quedan etiquetadas. |











### Fase 8 — L-08: Aceptación, restauración y entrega local











| ID | Historia / trabajo | Criterio de aceptación |





|---|---|---|





| PB-08.1 | Como QA, quiero ejecutar regresión por proceso y superficie. | Backend unitario + integración PostgreSQL; CRM tests/typecheck/build; Flutter tests/analyze/build; contrato común y pruebas negativas de autorización por objeto/empresa, fallo de red y concurrencia. Se registra comando, ambiente, salida y fecha; no se infieren resultados. |





| PB-08.2 | Como responsable de operación, quiero probar actualización y retorno de base de datos. | Flyway aplica desde base vacía y desde esquema previo; respaldo aislado restaura datos y versión; se ensaya recuperación/retorno para migraciones antes de usarlas con datos. Preferir migraciones aditivas y restauración documentada a una reversión destructiva sin validar. |





| PB-08.3 | Como propietario del piloto, quiero verificar restricciones y objetivos medibles. | OPEN-PILOT-RESOURCES/DEVICE-INVENTORY cerrados; probar S/0, 3 empresas, 30 conductores y 3 ADMIN concurrentes total + 1 SUPER_ADMIN, p95 ≤2 s, objetivo de interrupción ≤30 min en horario definido, RTO 4 h y RPO 1 h. Android/navegadores y accesibilidad se prueban en dispositivos reales acordados. |





| PB-08.4 | Como responsable local, quiero un candidato restaurable y decisión de salida. | Snapshot local reproducible con manifests/huellas, evidencia completa y copia restaurada; release checklist sin bloqueos. Si falla un criterio, declarar candidato no aceptado con causa y siguiente acción; no afirmar producción. |











## 6. Asuntos abiertos y verificaciones obligatorias











### Diez asuntos abiertos del registro











| ID | Asignación | Bloquea hasta resolverse |





|---|---|---|





| `OPEN-OTP-PROVIDER` | L-00/L-01 | Gmail SMTP S/0 y cuenta autorizada elegidos en L-00, con excepción explícita para gmail.com; L-01 verifica credenciales disponibles, cuotas/control, entrega y comportamiento al agotar límites. |





| `OPEN-SUPERADMIN-RECOVERY` | L-01/L-08 | Evidencia independiente, custodia del código de emergencia y procedimiento ensayado. |





| `OPEN-REQUIRED-DOCUMENTS` | L-01/L-02; títulos reales antes del piloto | [Catálogo aprobado Chiclayo](contratos/L-01-documental-Chiclayo-v0.1.0.md) y su revisión personal/vehículo/empresa implementados. Desconocido/POR_CONTRASTAR bloquea; la conformidad de títulos reales no se acredita con expedientes sintéticos. |





| `OPEN-RETENTION-PURPOSE` | L-05/L-07/L-08 | Finalidad, categorías, excepciones, purga y respaldos. |





| `OPEN-PILOT-RESOURCES` | L-00/L-06/L-08 | Correo seleccionado en L-00 y entrega en L-01; push, almacenamiento, horario, cuotas de operación, carga, latencia y recuperación con recursos reales siguen pendientes para L-06/L-08. |





| `OPEN-DEVICE-INVENTORY` | L-00/L-08 | Android, Flutter, navegadores, versiones, zoom y dispositivos del piloto. |





| `OPEN-UNIT-CARDINALITY` | L-02, antecedente para L-03 | **RESUELTO por CARD-01:** una sola unidad vigente por CONDUCTOR y exclusividad inversa; exclusiones temporales y dos carreras PostgreSQL verificadas. No volver a preguntar. |





| `OPEN-TRACKED-SERVICES` | L-05 | Qué clases de servicio requieren GPS; el inicio se bloquea solo donde corresponda según decisión. |





| `OPEN-CLOSED-INCIDENT-PROCEDURE` | L-06 | **RESUELTO_LOCAL:** INC-L06-01; nuevo caso vinculado, sin reapertura. [Matriz](evidencia/L-06/matriz-L06.md). |





| `OPEN-MODULE-CONTRACTS` | L-00…L-07 | Contratos por módulo, excepciones y mapeo de pantalla/variante a Stitch. |











### Veintiuna verificaciones del registro











| ID | Lote | Cierre verificable |





|---|---|---|





| `VT-V3-OPEN-SUPERADMIN-RECOVERY-01` | L-01/L-08 | Definir y probar evidencia independiente, custodia y procedimiento de emergencia. |





| `VT-V3-OPEN-OTP-PROVIDER-01` | L-01 | Verificar proveedor/recurso, condiciones, cuotas y entrega real. |





| `VT-V3-OPEN-REQUIRED-DOCUMENTS-01` | L-01/L-02 | Catálogo/decisiones aprobados y mecanismo verificado localmente; conservar revisión de aplicabilidad/títulos reales como condición antes del piloto, sin convertir desconocidos en conformes. |





| `VT-V3-Q-074-01` | L-06/L-08 | **PENDIENTE_OPERATIVO antes de habilitar canal/piloto:** cuota/disponibilidad efectiva, proyecto/dispositivo y entrega. L06 acredita solo contraste documental S/0, no envío/fallo visible y bloqueo; no se registra el requisito externo como cumplido. |





| `VT-V3-Q-075-01` | L-05 | Definir aviso de privacidad, finalidad y base aplicable. |





| `VT-V3-Q-077-01` | L-05/L-08 | Medir consumo, batería y capacidad con carga acordada. |





| `VT-V3-Q-079-01` | L-05/L-07 | Contrastar finalidad/obligaciones GPS y respaldo antes de fijar política. |





| `VT-V3-Q-106-01` | L-03/L-04/L-05 | Probar geometría de terminal y precisión; no usarla como prueba de recorrido. |





| `VT-V3-Q-086-01` | L-07/L-08 | Definir retención de términos, seguridad, documentos/evidencia y expiración de respaldos. |





| `VT-V3-Q-092-01` | L-08 | Ejecutar concurrencia representativa con aislamiento, ofertas y GPS. |





| `VT-V3-Q-093-01` | L-08 | Medir p95 bajo carga Q-092. |





| `VT-V3-Q-094-01` | L-08 | Definir horario de operación y medir el objetivo de interrupción ordinaria. |





| `VT-V3-Q-095-01` | L-08 | Ensayar restauración total y registrar quién/tiempo (RTO). |





| `VT-V3-Q-096-01` | L-08 | Probar retención de backups, borrado e integridad/restauración (RPO). |





| `VT-V3-Q-097-01` | L-08 | Inventariar Android y verificar Flutter/dependencias/requisitos de seguridad. |





| `VT-V3-Q-098-01` | L-08 | Probar navegadores, versiones, tamaño útil y zoom acordados. |





| `VT-V3-Q-099-01` | L-08 | Probar teclado, escalado y lector de pantalla en flujos críticos. |





| `VT-V3-MAINTENANCE-DAYS` | L-03 | Verificar varios días, bloqueo completo y retorno real. |





| `VT-V3-GPS-ACCESS` | L-05/L-07 | Definir actor, plazo y alcance para solicitudes de puntos dentro de retención. |





| `VT-V3-AUDIT-RETENTION` | L-07/L-08 | Definir seis meses calendario, eventos, excepciones y copias. |





| `VT-V3-INCIDENT-CLOSED` | L-06 | **VERIFICADO_LOCAL:** API/PG y caso CRM conectado vinculado, sin reabrir. [Matriz](evidencia/L-06/matriz-L06.md). |











Los 21 estados iniciales del registro son pendientes de verificación o de detalle de proceso. Una pregunta respondida no cierra su verificación técnica/operativa.











## 7. Forma de ejecución y reglas de terminado











Antes de iniciar cada historia, el contrato de lote debe registrar: IDs de regla/AC, alcance incluido y excluido, precondiciones, dueño de datos, estados, permisos/tenant, API y errores, dependencias, archivos, plan Flyway/retorno y comandos de verificación. El responsable del contrato y de archivos compartidos integra una sola definición; backend, CRM y Android implementan contra ella; QA revisa el flujo completo de forma independiente.











El método del kit presupone ramas/worktrees y evidencia con commit. Para este proyecto se conserva su control de cambios y calidad en forma local: asignación explícita por archivos, un solo escritor para contrato/migración/registro, integración secuencial, snapshot y huellas SHA-256, reporte de comandos efectivamente ejecutados y respaldo/retorno. No se ejecuta Git.











Estados para el tablero de este backlog:











- `POR_AUDITAR`: no existe inspección suficiente del código o evidencia.





- `LISTA`: contrato, regla, dependencia y criterio de aceptación están cerrados.





- `EN_CURSO`: hay implementación local identificable.





- `IMPLEMENTADA`: código escrito; todavía no acredita aceptación.





- `REVISADA`: revisión técnica independiente completada.





- `VERIFICADA`: criterio ejecutado en entorno definido y evidencia local adjunta.





- `BLOQUEADA`: falta una decisión/recurso específico y se identifica el lote dependiente.











### Criterio de cumplimiento del 100%











La iniciativa se considera completa cuando:











1. Las 109 reglas vigentes y los 162 criterios activos tienen trazabilidad a proceso, código y evidencia; las 9 reglas y 9 criterios sustituidos siguen marcados como sustituidos y no se reactivan.





2. Los lotes L-00…L-08 pasan a `VERIFICADA` con aceptación end-to-end por backend, CRM y Android donde aplique.





3. Se cierran con evidencia las 10 decisiones de detalle y las 21 verificaciones; no quedan bloqueos escondidos en notas o pantallas.





4. Se aprueban pruebas unitarias, integración PostgreSQL, seguridad/tenant, concurrencia, clientes, UX/dispositivo y recuperación, según cada criterio.





5. Migraciones, backup y retorno son reproducibles localmente; S/0 y objetivos de operación se comprueban con recursos reales.





6. El checklist de entrega se satisface. Si algún objetivo no se cumple, el estado final es candidato no aceptado con causa y acción concreta, no producción completada.











## 8. Fuentes de trabajo











- Dossier: `D:\UTP\CICLO X UTP\INTEGRADOR I\APF3\Ruta_Fija_Login_Inicio_Onboarding_Fase3_2026-10-12\Ruta_Fija_Reglas_Negocio_Fase_3.md`





- Registro/plan/kit: `…\06_Metodo_de_trabajo\ruta_fija_kit\specs\registro.json`, `docs\seguimiento\tareas.json`, `docs\plan_7_dias.md`, `docs\release_checklist.md`.





- Proyecto: `docs/REVISION_GENERAL_2026-10-12.md`, `docs/README.md`, `docs/matriz-trazabilidad-final.md`, código de `backend/`, `frontend/`, `mobile/` y migraciones en `backend/src/main/resources/db/migration/`.





- Referencias visuales: carpeta `02_Diseno_y_evidencia/` y referencias Stitch descritas en el kit. Las imágenes/diagramas históricos no son aceptación funcional.











**Primera entrega (2026-10-13):** comparación documental/código estática y creación del backlog; en ese corte aún no se habían ejecutado suites ni builds.











**Actualización L-00 (2026-10-14):** se mantienen las baselines backend, CRM y Flutter, la matriz de 289 filas, el inventario y el snapshot fuente final3. El usuario aprobó Gmail SMTP S/0 como solución provisional, aceptando gmail.com aunque el dominio pertenece a Google y no hay dominio propio. L-00 queda VERIFICADA; no se envió correo y no se configuró SMTP. L-01 conserva la comprobación de contraseña de aplicación, integración y entrega controlada; L-08 conserva el dispositivo físico. Ver [ejecución](ejecucion/L-00.md), [evidencia](evidencia/L-00/baseline.md) y [decisión Gmail](evidencia/L-00/decision-Gmail-2026-10-14.md).

















## Cierre funcional local acreditado — 2026-10-17











**L-01 VERIFICADA; tipo FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Se aplican SC-03/SC-07 y la [precision aprobada SC-03 1.1.0](contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md), [registro de aplicacion](contratos/SC-L01-LOCAL-01-aplicacion-v1.1.0-2026-10-17.json). El contrato anterior y la aprobacion condicionada quedan conservados como antecedentes; no se cambian silenciosamente ni se solicita otra aprobacion equivalente.











- CI03: aceptacion humana sintetica original intacta (c496b63c), gate anterior omitido en ese ensayo conservado como historia. [Complemento automatizado separado](evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/README.md): capacidad de activacion valida, POST complete409 RESOURCE_CONFLICT especificamente por terminos pendientes, aceptacion automatizada de un unico ADMIN adicional y mismo POST200 COMPLETED; login/grupos200. Resultado compuesto **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**; no se inventa PASS_MANUAL_SYNTHETIC_ONLY ni se atribuye al titular la aceptacion automatizada.





- CI07: **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado y contrastado, sin repetir recuperacion. Copia en la misma PC; no independencia de custodia. CI01 permanece VERIFICADO sin nuevo SMTP.





- Los doce criterios se revisaron con evidencia, entorno y limites en la [matriz final](evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Se reutilizan regresiones validas y se ejecutaron27 unitarias dirigidas del arnes y el complemento real HTTP/PG, separados de la evidencia humana. Ningun criterio obligatorio del alcance local queda pendiente.





- Archivo previo a salida del arnes: **TEST_CLEANUP_RELEASED=true**, marcador ausente/lock libre, servidores cerrados. Limpieza selectiva del ADMIN adicional por API normal, inactivo y sesiones revocadas403 FORBIDDEN_ROLE; aceptacion e historial conservados. Sin limpieza global, estados forzados o reinicio de cupos. Ledger1/2/0 pasa a **1/3/0**, cupos **0/1/20**, aceptaciones2; ADMIN manual, SUPER_ADMIN y caso CI01 intactos. Las futuras pruebas autorizadas que limpian solo ruta_fija_test pueden reanudarse tras el archivo.





- Requisitos originales pendientes **antes del piloto**: aceptacion propia de ADMIN legitimo; preparacion, medios/custodia independiente e inscripcion/ensayo del titular real; privacidad/identificacion y condiciones de conservacion; infraestructura/base/HTTPS/copias/restauracion y preparacion de participantes/recursos/titulos. No se registran como satisfechos por equivalencia. Solo una PC Windows confirmada; no se presupone medio adicional.





- Terminos1.0.0 SHA20FFD952... y final3 ZIP38065106.../manifiestoE78D7002... comprobados sin cambios. Sin SMTP adicional, claves definitivas, migraciones de desarrollo/piloto, despliegue o Git/GitHub. S/0 y sin offline. **L-02 NO INICIADA / NO AUTORIZADA; OK VERDE separado.**











El [acta](actas/L-01-cierre-funcional-local-2026-10-17.md) contiene el alcance, referencias, limites y bloqueos. Las observaciones historicas PENDING del ensayo humano anterior al complemento no representan un pendiente actual del alcance local tras aplicar la adenda.

















## Inicio L-02 autorizado y cardinalidad confirmada — 2026-10-18











El titular entrego **OK VERDE** para L-02 y confirmo **una sola unidad vigente por CONDUCTOR** (CARD-01 / OPEN-UNIT-CARDINALITY). Se conserva la exclusividad de unidad, periodos sucesivos sin solapamiento e historial; no hay sustitucion automatica. La decision ya esta resuelta y no debe preguntarse de nuevo.











La parte de PB-02.1...3 ya acreditada en L-01 se reutiliza, con PB-01.5 y la matriz del cierre como antecedentes. La evolucion pendiente y PB-02.4 se concretan en [contrato L-02](contratos/L-02.md), [subfases](ejecucion/L-02.md) y [matriz de ejecucion](evidencia/L-02/matriz-L02.md). L02.0 implementa consulta sin PII del estado de incorporacion y proteccion del modelo heredado de vinculos; el modelo temporal, revision de unidad/servicio, suspension, correccion material y continuidad Android se mantienen pendientes de sus incrementos y verificacion. No se da por cumplido un criterio por compilar.











L-01 mantiene su cierre FUNCIONAL_LOCAL_PILOTO_BLOQUEADO; aceptacion humana, CI01 y CI07 conservadas. Requisitos reales de piloto no satisfechos por este OK VERDE. S/0, sin offline, sin envios externos, Git/GitHub, nuevas claves definitivas, migraciones de desarrollo/piloto, despliegue o regeneracion de final3. **No se inicia L-03 sin su propia puerta de autorizacion.**











## Cierre L-02 acreditado — 2026-10-19











**VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](actas/L-02-cierre-funcional-local-2026-10-19.md), [matriz final](evidencia/L-02/matriz-final-funcional-local-2026-10-19.md) y [archivo final](evidencia/L-02/aceptacion-final-V28/README.md). Todos los criterios del alcance local acreditados; los cortes anteriores EN_CURSO son históricos.











- Backend: 53 comprobaciones dirigidas correctas, incluidas 44 HTTP/PG y dos carreras reales; CRM: 20; Flutter: 86 y dos optativas omitidas. Builds/análisis correctos, artefactos locales archivados. El recorrido conectado se acredita por separado, sin presentarlo como ensayo humano o dispositivo físico.





- RUN_ID conectado `44b3d67a-69ce-4169-89b4-62ebe8a40160`: un solo CONDUCTOR nuevo por flujo HTTP normal, mismo ADMIN/organización sintéticos; CRM navegador real y Flutter repositorios con HTTP real. Correo simulado, sin SMTP adicional.





- V26→V27→V28 únicamente en `ruta_fija_test`; postflight Flyway/ledger/aceptaciones correcto. Limpieza selectiva: cuenta/Driver inactivos, sesiones revocadas, período revocado e historial retenido; **TEST_CLEANUP_RELEASED=true**. Histórico **1/3/1**, disponibles **0/1/19**, dos aceptaciones intactas. La guardia contra limpieza global de históricos continúa activa; no truncar ni recuperar cupos.





- final3, términos publicados y evidencias humanas L-01 conservan sus huellas. Continúan bloqueos reales de privacidad, infraestructura, preparación de participantes, aceptación propia de ADMIN legítimo y custodia/ensayo del titular real.





- **Piloto bloqueado. L-03 NO INICIADA; su OK VERDE permanece separado.**







## Corte posterior a CAP25 - 2026-10-22

[Revisión integral vigente](evidencia/L-08/revision-integral-2026-10-22.md). CAP25 y la carrera acotada de retiro están acreditados; dataset cerrado, ledger1/4/20 conservado en55439 detenida. Caso físico cerrado y ledger1/3/7 de5432 conservados. API preservadaV48 y candidato empaquetadoV50 comprobados en readonly. CI-L08-03 VERIFICADO_LOCAL_SINTETICO; restantes criterios según matriz, L-08 EN_CURSO y piloto BLOQUEADO. Sin reabrir casos, correo adicional, Git/GitHub o regenerar final3. Siguiente trabajo: complementos físicos/escritorio, recuperación empresarial completa/retorno y dependencias reales ya declaradas pendientes. Consulta de disponibilidad Wi-Fi/sesión enviada, sin autorizar LAN o una cuenta nueva.

## L08.3 — Escritorio, restauración V50 y complemento físico protegido

Contrato0.7.0. Carga25 y caso físico anterior permanecen cerrados. Se conservan sus manifiestos, contadores e historial; no se reabrieron.

- Escritorio: Chrome155.0.8059.86/Edge155.0.4283.62,12combinaciones ADMIN/SUPER_ADMIN y zoom nativo100/200/300%, área útil1280×720.66páginas/804checks con backend/PG reales en readonly; bootstrap de sesión sintético, no login/refresh ni lector humano. API/tablas/hash íntegros antes/después.
- REST03 autorizado, nuevo55440/ruta_fija_test, snapshotV50 GCM con105tablas/esquema/permisos reconciliados.401/403 y creación/reconsulta/retiro normal de un grupo; cero cuentas nuevas. Fallo de arnés recuperado sobre el mismo grupo. Máximo318,730778s desde origen conservador anterior al fallo de salud; primer journal nulo conservado, sin reiniciar tiempos. No pérdida física real, medio independiente, RPO ni reconexión móvil.55440 detenido;55438/55439 conservados/detenidos.
- Pruebas dirigidas:21de respaldo y21del arnés (no42nuevas de producto), sin fixtures. Guarda compartida APK rechaza el binario de IDprincipal y admite `.l08`; sin instalar en la regresión. JAR V50 actual conserva sus672entradas de aplicación iguales.
- FIS02 autorizado: máximo una alta7→8, regla temporal Privada/Java/TCP4311/origenPoco y filtro de peer. Caso3f0ba758-6eb9-4d2f-b948-dd1c7de3c430 activo, login/disponibilidad/aceptación/consentimiento/inicio personales y GPS reales. Usuario informó uso TalkBack satisfactorio. Font_scale0,81 al corte, pendiente escala grande y medición15min sin alimentaciónUSB (criterio≤5puntos ya aprobado). No se fabrican estados o resultados humanos.
- Error propio de instalación: el primer APK actualizó el principal `pe.rutafija.conductor`; datos sin borrar, binario previo sin copia/huella. Principal cerrado. Paquete `.l08` correcto instalado/verificado después, SHAe74dd49725499e3514c1fd853d81a1479f0614a6c5bfd07ddfa1a9b1c764609b; no volvió a modificar el principal. Incidencia abierta, sin declarar restitución o preservación del binario anterior.
- Ledger1/3/8 y aceptaciones2; marcador/lock activos, TEST_CLEANUP_RELEASED=false. Mantener fixtures suspendidos. Antes de liberar: archivo humano, retiro exacto, postflight y eliminación de regla temporal. Casos anteriores no se repiten. Final3/términos/manifiestos congelados intactos.

Pendientes concretos: completar FIS02 (escala, negativas físicas previstas, batería, cierre/logout y archivo/retiro), resolver el binario principal, medios independientes/monitor/alertas/cobertura24/7 y preparación de privacidad/participantes/títulos/ADMIN legítimo/custodia/infraestructura. Sin acta, despliegue o piloto activo.
