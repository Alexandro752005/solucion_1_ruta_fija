# Estado de la Fase 1

- Fase: 1 — Base reproducible y segura
- Madurez objetivo acumulada: 25 %
- Estado: completada técnicamente y aceptada por el propietario como base para iniciar la Fase 2.
- Última actualización: 2026-08-29

## Criterios de salida

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Estructura modular backend y CRM Angular | Verificado | compilación backend y build Angular de producción aprobados |
| Versiones fijadas | Verificado | Maven Wrapper 3.9.15 con checksum, `pom.xml` y `package-lock.json` |
| PostgreSQL 16 en Docker | Verificado en ejecución | PostgreSQL 16.15 saludable en el contenedor local |
| Flyway desde base vacía | Verificado en ejecución | migración `V1__identity_organization_audit` aplicada con éxito |
| Semillas dev con dos organizaciones | Verificado en ejecución | 2 organizaciones y 5 usuarios de demostración cargados sólo en `dev` |
| Perfiles `dev`, `test`, `prod` | Verificado | seeds desactivados fuera de `dev`; pruebas de integración con perfil `test` |
| Login, refresh rotativo, logout y `/me` | Verificado en ejecución | flujo HTTP y 7/7 pruebas `AuthFlowIT` aprobadas contra PostgreSQL real |
| RBAC y organización segura | Verificado en integración | tenant derivado del token y prueba de aislamiento en `AuthFlowIT` aprobada |
| Login/layout/guards/interceptor Angular | Verificado | 22/22 pruebas, lint, typecheck y build aprobados |
| Errores y correlation ID | Verificado en ejecución | respuestas normalizadas y correlation ID en API y preflight CORS |
| Health checks y OpenAPI | Verificado en ejecución | `/actuator/health`, `/v3/api-docs` y Nginx respondieron correctamente |
| Auditoría inicial | Verificado en ejecución | eventos persistidos y trigger `trg_audit_event_append_only` registrado |
| Pruebas backend | Verificado | 20/20 unitarias y 7/7 de integración, sin fallos, errores ni omisiones |
| CI base | Configurada; ejecución remota pendiente | el workflow está presente, pero el directorio aún no es un repositorio Git |
| Documentación de ejecución y decisiones | Actualizada | README, ADR, arquitectura, auditoría y este registro |
| Secretos y dependencias | Verificado localmente | `.env` local ignorado; 0 patrones de secretos y `npm audit` sin vulnerabilidades |

## Regla de avance aplicada

La evidencia técnica quedó completa y el propietario autorizó expresamente el
avance a la Fase 2 el 2026-08-29. La ejecución de CI sobre un repositorio Git
sigue pendiente como control operativo, pero no bloquea el entorno local ni la
construcción aprobada de la Fase 2.

## Verificaciones aprobadas el 2026-08-29

- Docker Desktop: motor 29.7.2 disponible sobre WSL 2.7.12.0.
- Perfil `app` de Compose levantado: `postgres`, `backend` y `frontend` están
  saludables y expuestos sólo en `127.0.0.1` por los puertos configurados.
- PostgreSQL: servidor 16.15; Flyway registró la versión `1` con la descripción
  `identity organization audit` y éxito de migración.
- Semillas de desarrollo: 2 organizaciones y 5 usuarios; no se almacenan ni se
  documentan contraseñas reales en esta evidencia.
- Backend: `mvnw verify` aprobó 20 pruebas unitarias y 7 de integración con
  Testcontainers y PostgreSQL 16.15, sin fallos, errores ni omisiones.
- Flujo HTTP real: login, `/me`, refresh rotativo, detección de reutilización,
  origen no confiable, credenciales inválidas, logout y proxy Nginx aprobados.
- CORS: el preflight desde `http://localhost:4200` devuelve origen, métodos y
  credenciales permitidos; un origen no confiable devuelve 403 JSON normalizado.
- Auditoría: se registraron `LOGIN_SUCCESS`, `LOGIN_FAILED`, `TOKEN_REFRESHED`,
  `REFRESH_REUSE_DETECTED` y `LOGOUT`; el trigger append-only está instalado.
- Frontend: lint y typecheck aprobados; Vitest ejecutó 8 archivos y 22 pruebas,
  todas aprobadas. El build de producción se completó correctamente.
- Dependencias frontend: `npm audit --audit-level=high` informó 0 vulnerabilidades.
- Compose y configuraciones de VS Code validan correctamente. El escaneo local
  no detectó patrones de secretos ni implementaciones fuera de alcance.

## Estado de uso al 25 %

La solución puede usarse como demostración técnica local completa: CRM web en
`http://localhost:4200`, API detrás del proxy y PostgreSQL 16 dentro de Docker.
No es una autorización de producción. La aceptación explícita para Fase 2 ya se
recibió; queda pendiente crear o vincular un repositorio Git autorizado y
ejecutar la CI sobre un commit identificable.

Registro detallado: [evidencia del 2026-08-29](evidencia-fase-1-2026-08-29.md).
