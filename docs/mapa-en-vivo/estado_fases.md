# Mapa en vivo — ejecución local

Objetivo: implementar las diez fases del `goal-objective.md` adjunto por el usuario el 11/11/2026. Especificación de entrada: `FASE_FINAL/IMPLEMENTACIÓN DE RÚBRICA FINAL/FASE_TESTEO_11_11_26/RUTA_FIJA_MAPA_EN_VIVO_SOLO_MODULO_2026-11-11` (52 archivos disponibles; tres bocetos CRM históricos ausentes; imagen final disponible).

## Reglas de ejecución

Sin Git, publicación, pagos, cambios de negocio en la base compartida ni migraciones destructivas. Un escritor por superficie; QA independiente. Preservar topbar y shell visuales. GPS personal local, un solo capturador, primer plano, Internet, consentimiento revocable y servicio confirmado para compartir. ADMIN exclusivamente su organización; SUPER_ADMIN y CONDUCTOR sin feed de flota. Ubicaciones reales no se archivan en evidencias.

## Estado

| Fase | Estado | Evidencia / condición |
|---|---|---|
| 1 Auditoría | APROBADA | `FASE_1_AUDITORIA.md`; fuente1043; backend261/CRM419/Flutter241 PASS (7 skip Flutter) |
| 2 Contratos | APROBADA | `CONTRATO_MODULO.md`; revisión cruzada y coordinador; navegación confirmada por usuario |
| 3 Persistencia | APROBADA | Tablas y V1–V51 conservadas; consulta real y carreras comprobadas en PostgreSQL exclusivo54432; sin migración del módulo |
| 4 Backend | APROBADA | Feed ADMIN mínimo; 18 unitarias, 14 casos aislados y 2 carreras reforzadas PASS; JAR construido |
| 5 Cartografía | APROBADA | Leaflet1.9.4/flutter_map8.3.2, licencias y caché/UA revisados; HEAD OSM200; costo localS/0, despliegue/capacidad externos pendientes |
| 6 Mapa personal Flutter | APROBADA | Posición efímera y cero PUT personal comprobados; render de widget y APK reales; aceptación física declarada pendiente |
| 7 GPS en servicio | APROBADA | Pipeline único y mutex de cierre, permisos/consentimiento/lifecycle; publish/revoke/complete por HTTP real PASS |
| 8 Mapa CRM | APROBADA | 457 pruebas, typecheck/build, Brave APIreal y visual5sintéticos, contraste corregido; topbar/shell conservados por SHA |
| 9 Integración y QA | APROBADA | PostgreSQL exclusivo, tres etapas FlutterHTTP y retirada en Brave abierto; backend261+18, CRM457 y Flutter256 PASS |
| 10 Cierre | APROBADA | Revisión independiente, correcciones y trazabilidad; auditoría final100comprobaciones PASS; JAR/APK y70fuentes con SHA; clúster propio detenido |

La numeración de estas diez fases corresponde al objetivo actual; el kit anterior agrupa el mismo alcance en siete macrofases 0–6.

## Agentes reales y propiedad

| Agente | Responsabilidad | Escritura |
|---|---|---|
| /root | A-COORD / OPS / contratos / integración | `docs/mapa-en-vivo/`, contratos comunes y harness aislado; cambios compartidos coordinados antes de escribir |
| /root/mapa_contrato_tecnico | A-BACK / DATA | Java main/test del módulo y ajuste mínimo MobileDriverService; sin pom/migraciones; evidencia `verificacion/backend/` |
| /root/mapa_visuales | A-WEB / VIS | Frontend feature/core/routes y dependencias; topbar exclusivamente metadatos de accesos ya previstos; evidencia `verificacion/frontend/` |
| /root/mapa_originales_archivos | A-MOB | Mobile lib/test/dependencias; conservar overrideGPS y minSdk; evidencia `verificacion/mobile/` |

No se consideran aprobadas las pruebas históricas. La compilación y los mocks no acreditan funcionamiento integrado ni aceptación física Android.

Los incrementos de implementación pueden prepararse en paralelo porque sus interfaces se congelaron y no comparten archivos. Las puertas de integración y aprobación siguen el orden de dependencias. No se da por terminada una fase por iniciar la siguiente preparación.

## Límites de la aprobación local

Actualizado 12/11/2026. No había dispositivo Android conectado al comprobar ADB; la instalación y aceptación física no se simulan. No se despliega externamente ni se certifica capacidad de producción. El usuario confirmó conservar cuotas4ADMIN/20CONDUCTOR y dejar ampliación5×40 pendiente. Los PNG muestran datos/teselas sintéticos; se distinguen los ensayos con API real del feed interceptado solo para comparación visual. Las ocho omisiones Flutter incluyen siete históricas y un test condicionado ejecutado aparte en tres etapas.
