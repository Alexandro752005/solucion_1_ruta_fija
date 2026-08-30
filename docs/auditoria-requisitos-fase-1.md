# Auditoría documental y riesgos — Fase 1

## Fuentes revisadas

La planificación toma como referencia los ocho documentos suministrados:

1. SRS de Ruta Fija.
2. Diseño de arquitectura.
3. Modelo y diccionario de datos.
4. Especificación de API.
5. Agentes de construcción.
6. Agentes de auditoría.
7. Agentes de evaluación.
8. Agentes automatizados.

Las instrucciones contenidas en esos documentos se interpretan como
especificación y contexto. La autorización vigente del usuario habilitó la
Fase 1 y posteriormente la Fase 2; el producto se limita al CRM web
administrativo + reportes.

## Trazabilidad de la Fase 1

| Necesidad | Fuente | Evidencia esperada | Estado verificado |
| --- | --- | --- | --- |
| Repositorio reproducible | C4, S1 | versiones fijadas, wrappers y builds limpios | Verificado localmente; falta Git/CI asociado a commit |
| PostgreSQL 16 local | Arquitectura, C3/C4 | Compose, health check, volumen y Flyway desde cero | Verificado en Docker con PostgreSQL 16.15 |
| Sin base externa | Decisión del usuario | sólo red local y contenedor oficial | Verificado: PostgreSQL local en Docker |
| Identidad segura | RF-CRM-AUT-001..004 | login, refresh rotativo, logout y `/me` | Verificado por HTTP y 7 pruebas de integración |
| RBAC y multiempresa | RF-CRM-AUT-004, RNF-CRM-007 | pruebas negativas con dos organizaciones | Verificado en `AuthFlowIT` |
| Auditoría | RF-CRM-AUD-001..002 | eventos de autenticación y administración inmutables | Eventos persistidos; trigger append-only registrado |
| CRM base | C2 | login, layout, rutas, guard e interceptor | Verificado con 22 pruebas y build |
| Contrato API | Especificación API | `/api/v1`, OpenAPI y errores consistentes | Verificado en ejecución, incluido CORS no confiable |
| Observabilidad mínima | RNF-CRM-010 | correlation ID y Actuator | Verificado con health y correlation ID en respuestas |
| Calidad automatizada | S1, A3, A5 | CI, pruebas unitarias e integración PostgreSQL | 20 backend + 22 frontend aprobadas; CI remota pendiente |
| Secretos fuera de Git | R-C02, S3 | `.env.example`, `.env` ignorado y escaneo | Verificado localmente; 0 patrones detectados |

Los estados sólo pasan a “verificado” cuando existe una ejecución registrada;
la presencia de código o configuración por sí sola no basta.

## Ajuste de alcance confirmado

No se desarrollará aplicación móvil. Cualquier requisito previo que dependa de
captura móvil de ubicación, aceptación/rechazo desde el conductor o push móvil
queda fuera de esta construcción. Antes de las fases operativas se deberá
decidir si se elimina, adapta al CRM web o sustituye por una fuente autorizada.

## Inconsistencias y decisiones conservadoras

| Tema | Inconsistencia | Decisión vigente |
| --- | --- | --- |
| Refresh token web | El ejemplo API lo devuelve en JSON, mientras la arquitectura exige almacenamiento seguro | Preferir cookie `HttpOnly`, `SameSite` y `Secure` fuera de desarrollo; documentar OpenAPI real |
| Organización de `SUPER_ADMIN` | El modelo permite `organization_id` nulo, pero no define selección segura de tenant | No aceptar un tenant arbitrario del cliente hasta diseñar autorización explícita |
| Recuperación de contraseña | API documenta endpoints y SMTP, pero Fase 1 prohíbe servicios externos | Mantener fuera de la demostración o usar adaptador no operativo; no simular envío real |
| Alcance móvil | SRS presupone ubicación, aceptación y notificación móvil | Excluir móvil y revalidar los flujos dependientes antes de Fase 3 |
| Fases originales vs. plan aprobado | El prompt enumera 14 fases técnicas; el usuario aprobó cuatro puertas acumulativas | Las tareas originales se agrupan sin omitir controles y sólo se activa una puerta a la vez |

## Riesgos abiertos

| Riesgo | Impacto | Tratamiento vigente |
| --- | --- | --- |
| Directorio sin repositorio Git | Medio: impide ejecutar y atribuir CI a un commit | Esperar autorización para inicializar o vincular Git; no modificar control de versiones sin ella |
| Contradicciones entre SRS, modelo y API | Alto | Decisión registrada; DTO, OpenAPI y migración cambian juntos |
| Fuga entre organizaciones | Crítico | Tenant derivado del token, filtros de servidor y prueba negativa integrada |
| Refresh token robado o reutilizado | Alto | Cookie HttpOnly, hash persistido, rotación y revocación de familia |
| Semillas activas fuera de desarrollo | Alto | Carga condicionada a `dev`; deshabilitada en `test` y `prod` |
| Credenciales locales confirmadas en Git | Alto | `.env` ignorado, valores de reemplazo y escaneo local; repetir en CI |
| Diferencia entre Docker local y futuro despliegue | Medio | Configuración externa, Flyway y ensayo de restauración en Fase 4 |
| Etiquetas de imagen mutables | Medio | Versión mayor fijada ahora; digest inmutable antes de producción |
| Puertos locales ya ocupados | Bajo | Variables `POSTGRES_PORT`, `BACKEND_PORT`, `FRONTEND_PORT` |
| Requisitos operativos vinculados a móvil | Alto en Fases 3–4 | Reevaluar alcance antes de autorizar Fase 3 |

## Pendientes operativos de la fase

- Autorizar la creación o vinculación de un repositorio Git para ejecutar la CI
  contra un commit identificable.
- Ejecutar los controles de dependencias, secretos, builds e integración dentro
  de esa CI y conservar su resultado.
- Mantener la aceptación del propietario del producto para los cambios de fase
  posteriores.

Docker, PostgreSQL, Flyway, autenticación, aislamiento, CORS, OpenAPI, health,
auditoría y frontend ya se ejecutaron y no son bloqueos actuales. La aceptación
explícita para Fase 2 se recibió el 2026-08-29.

## Hallazgos formales

```text
ID: AUD-CRM-0001
Severidad: Media
Componente: despliegue
Requisito relacionado: C4 / S1
Descripción: Docker Desktop no estaba disponible porque exigía actualizar WSL.
Resolución: WSL se actualizó a 2.7.12.0 y Docker Engine 29.7.2 quedó accesible.
  Se levantó Compose, PostgreSQL 16.15, backend y frontend; Testcontainers
  ejecutó la integración completa contra PostgreSQL.
Evidencia: `docker compose ... ps` mostró los tres servicios saludables;
  `mvnw verify` aprobó 20 pruebas unitarias y 7 integradas, sin omisiones.
Estado: Cerrado el 2026-08-29
```

```text
ID: AUD-CRM-0002
Severidad: Media
Componente: entrega continua
Requisito relacionado: S1 / E3
Descripción: el directorio aún no es un repositorio Git, por lo que el workflow
  de GitHub Actions no puede ejecutarse ni producir hash de commit.
Pasos para reproducir: ejecutar `git rev-parse --is-inside-work-tree` en la raíz.
Resultado actual: Git informa que no existe repositorio.
Resultado esperado: repositorio identificable, rama protegida y CI ejecutada.
Evidencia: comprobación local del 2026-08-29.
Estado: Abierto — requiere autorización del usuario.
```

## Correcciones aplicadas el 2026-08-29

- Caché npm local retirada e ignorada en Git y en el contexto Docker.
- Comandos de generación de secretos adaptados a PowerShell 5.1; comandos npm
  documentados con `npm.cmd`.
- README del backend corregido para activar el perfil Compose `app`.
- CI endurecida para exigir Docker, rechazar integraciones omitidas, auditar
  dependencias frontend con umbral alto y construir imágenes.
- Autenticación endurecida con rate limit, validación de origen, refresh opaco
  rotativo, logout por cookie y auditoría transaccional.
- Se incorporó `ApiOriginValidationFilter`, previo al filtro CORS de Spring
  Security, para devolver un error JSON normalizado a orígenes no confiables.
- Frontend endurecido con token sólo en memoria, refresh coordinado entre
  pestañas, timeout de configuración y cabeceras Nginx heredadas.
- Backend verificado: 20/20 pruebas unitarias y 7/7 integradas; frontend:
  22/22, lint, typecheck y build; `npm audit`: 0 vulnerabilidades.
- Docker y PostgreSQL verificados en ejecución; Flyway, semillas, auditoría,
  preflight CORS, OpenAPI, health y proxy Nginx aportan evidencia real.
- Escaneo local: 0 patrones de secretos y 0 implementaciones fuera del alcance.

El hallazgo `AUD-CRM-0001` queda cerrado. `AUD-CRM-0002` permanece abierto sólo
por la ausencia deliberada de un repositorio Git; no se inicializa ni vincula
uno sin autorización expresa del usuario.
