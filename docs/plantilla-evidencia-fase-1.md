# Plantilla de evidencia — Puerta de Fase 1

> Copiar esta plantilla a un informe fechado. No marcar una prueba como aprobada
> sin adjuntar salida reproducible y sanitizada; nunca registrar secretos,
> cookies ni tokens completos.

El registro parcial vigente se encuentra en
[evidencia-fase-1-2026-08-29.md](evidencia-fase-1-2026-08-29.md). Esta plantilla
permanece sin aprobar hasta ejecutar la ronda completa con Docker disponible.

## Identificación

- Fecha y zona horaria:
- Commit:
- Responsable:
- Sistema operativo:
- Docker / Compose:
- Java / Maven:
- Node / npm:
- Navegador:

## Inventario

| Elemento | Versión esperada | Versión observada | Evidencia | Resultado |
| --- | --- | --- | --- | --- |
| Java | 21 |  |  | Pendiente |
| Maven Wrapper | 3.9.15 |  |  | Pendiente |
| Spring Boot | 3.5.16 |  |  | Pendiente |
| Node | 24.16.x |  |  | Pendiente |
| Angular | 22.x exacta |  |  | Pendiente |
| PostgreSQL | 16.x |  |  | Pendiente |

## Construcción y pruebas

| Caso | Comando o pasos | Evidencia | Resultado |
| --- | --- | --- | --- |
| Validación de Compose | `docker compose --profile app config --quiet` |  | Pendiente |
| Backend limpio | `backend\\mvnw.cmd clean verify` |  | Pendiente |
| Frontend dependencias | `npm.cmd ci` |  | Pendiente |
| Frontend pruebas | `npm.cmd run test:ci` |  | Pendiente |
| Frontend build | `npm.cmd run build` |  | Pendiente |
| Imágenes | `docker compose --profile app build` |  | Pendiente |

## Base de datos desde cero

1. Confirmar que el volumen objetivo es exclusivamente el volumen local de este
   proyecto.
2. Eliminarlo de forma deliberada.
3. Iniciar PostgreSQL y backend.
4. Registrar migraciones Flyway, versión de esquema y health checks.
5. Confirmar que las semillas aparecen solo con `dev`.

- Evidencia:
- Resultado:

## Seguridad funcional

| Caso | Respuesta esperada | Evidencia | Resultado |
| --- | --- | --- | --- |
| Login válido | sesión y token corto |  | Pendiente |
| Login inválido | error genérico, sin enumeración |  | Pendiente |
| Refresh válido | rotación del token |  | Pendiente |
| Reutilizar refresh anterior | familia revocada / acceso denegado |  | Pendiente |
| Logout | refresh revocado y cookie eliminada |  | Pendiente |
| `/me` autenticado | identidad, rol y organización correctos |  | Pendiente |
| UUID inválido | error normalizado |  | Pendiente |
| Rol insuficiente | `403` sin datos sensibles |  | Pendiente |
| Organización A consulta B | sin fuga ni enumeración |  | Pendiente |

## API y observabilidad

| Comprobación | Evidencia | Resultado |
| --- | --- | --- |
| `/actuator/health` saludable |  | Pendiente |
| `/v3/api-docs` válido |  | Pendiente |
| Errores con contrato común |  | Pendiente |
| `correlationId` en respuesta/log |  | Pendiente |
| Eventos auditables generados |  | Pendiente |
| Logs sin secretos/tokens |  | Pendiente |

## Hallazgos

| ID | Severidad | Descripción | Responsable | Estado / fecha |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## Decisión de puerta

- [ ] Backend y frontend compilan.
- [ ] PostgreSQL y migraciones funcionan desde cero.
- [ ] Identidad y aislamiento pasan todas las pruebas críticas.
- [ ] No hay secretos versionados ni hallazgos críticos abiertos.
- [ ] Documentación y OpenAPI coinciden con el comportamiento.
- [ ] Propietario del producto acepta la demostración.

Decisión: **Pendiente / Aprobada / Rechazada**

Observaciones y firma:
