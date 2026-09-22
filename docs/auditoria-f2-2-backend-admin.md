# Auditoria F2.2 - Backend ADMIN

Fecha de auditoria: 2026-09-22.

## Dictamen

La consolidacion de roles en backend es consistente con la regla de negocio:
hay un unico rol administrativo por organizacion, `ADMIN`. La separacion entre
organizaciones se conserva y la asociacion historica `group_coordinator` no
concede permisos ni puede modificarse desde la aplicacion.

## Matriz de comprobacion

| Control | Evidencia de implementacion | Resultado |
| --- | --- | --- |
| Roles persistentes | `UserRole` y la constraint V6 admiten `SUPER_ADMIN`, `ADMIN`, `CONDUCTOR` | Conforme |
| Conversion segura | V6 revoca refresh tokens de roles antiguos y luego los transforma a `ADMIN` | Conforme |
| JWT legado | El convertidor y `CurrentUserProvider` rechazan claims `ADMINISTRADOR`/`COORDINADOR` | Conforme |
| Alcance de ADMIN | Servicios y repositorios eliminan filtros por coordinador; las consultas permanecen por organizacion | Conforme |
| Frontera tenant | Integracion cubre ADMIN de la misma organizacion y rechazo de otra organizacion | Conforme |
| Grupos historicos | Sin endpoints, DTOs de escritura ni repositorio con `save`/`delete`; acceso solo de lectura tecnica | Conforme |
| Tiempo real | Broadcast solo a `ADMIN` activo de la misma organizacion; smoke verifica ticket y WebSocket | Conforme |
| Base nativa | Flyway V1--V6 y auditoria de schema ejecutados contra PostgreSQL 16 sin Docker | Conforme |

## Hallazgo resuelto durante F2.2

La auditoria de esquema heredada esperaba que `ruta_fija_test` no tuviera
tablas. Como las pruebas de integracion dejan una estructura migrada valida,
esa condicion podia producir un falso negativo. El control ahora acepta solo
dos estados seguros para la base de pruebas: catalogo vacio o esquema migrado
aislado. Nunca acepta la base de desarrollo ni una recuperacion.

## Residuales clasificados

| Residual encontrado por busqueda | Clasificacion | Tratamiento |
| --- | --- | --- |
| Claims antiguos en dos pruebas unitarias | Intencional | Prueban que JWT legado no obtiene acceso; no son roles activos |
| SQL candidata y fixture F2.1B | Historico y necesario | Conservados como prueba reproducible de la conversion V6 |
| Tabla, entidad y repositorio `GroupCoordinatorHistoryRepository` | Historia tecnica | Solo lectura; sin autorizacion ni escritura |
| Roles antiguos en Angular | Diferido de forma expresa | F2.3 actualiza interfaz, rutas, tipos y pruebas antes de su uso con ADMIN |

No se detectaron usos activos de `ADMINISTRADOR` o `COORDINADOR` en el codigo
productivo Java. No se detectaron endpoints ni metodos productivos para crear,
quitar o usar coordinadores de grupo como control de acceso.

## Criterio de salida de F2.2

F2.2 queda lista para aprobacion solo cuando consten: migracion V6 aplicada,
auditoria de esquema aprobada, pruebas unitarias e integracion sin errores,
smoke REST/WebSocket nativo aprobado, documentacion de contrato transitorio y
un commit trazable. La aprobacion no habilita F2.3 automaticamente.
