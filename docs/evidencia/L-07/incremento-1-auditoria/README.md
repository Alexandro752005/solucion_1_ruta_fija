# L07.1 — Auditoría, incremento verificado

2026-10-21. **L07 EN_CURSO**, sin piloto ni L08. [Pruebas](pruebas.json), [postflight](postflight-V44.json), [preservación](preservacion.json), [contrato API](../../../contratos/L-07-auditoria-API-v0.1.0.md).

88 pruebas distintas:69 Spring/MockMvc + PostgreSQL reales en transacciones rollback y19 unitarias. Incluyen aislamiento ADMIN/SUPER_ADMIN/CONDUCTOR, plataforma sin selector empresarial, denegaciones401/403/404 auditadas una sola vez, proyección recursiva de metadatos personales/secretos tanto al escribir como al leer legados y correlación durable UUID. Regresiones de incidencias, atención segura y comunicados. Se conservó el contenido anterior de282 eventos de auditoría; no se reescribieron legados. Seis pruebas CRM con dobles API; typecheck/build CRM y empaquetado backend pasan. No equivalen a navegador/Flutter conectados.

V44 permanece;45 migraciones válidas. Ledger1/3/5 y dos aceptaciones conservados, cero cuentas persistentes nuevas y ningún correo externo/purga global. Los12 archivos protegidos y187 archivos congelados del cierre L06 coinciden; cuatro documentos globales evolucionan expresamente. Artefactos nuevos separados de los cerrados; fuente de este corte sellada.

Límites:catálogo completo de eventos/diferencias de negocio todavía por revisar; aislamiento API/componentes acreditado, recorrido conectado pendiente. Retención/purga por finalidad, reportes versionados/KPI, dashboard, inicio/preferencias móvil y cierre completo todavía sin acreditar en este corte. No hay pruebas físicas nuevas ni habilitación de foto personal.
