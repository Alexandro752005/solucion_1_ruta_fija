# Inventario inicial por módulo — L-00

La tabla vincula los 17 módulos del registro con carpetas localizadas en el checkout. «Presente» describe código encontrado, no aceptación funcional ni cobertura total de las reglas. La [matriz de trazabilidad](L-00-trazabilidad.csv) y su [guía](L-00-trazabilidad.md) registran una fila por cada uno de los 289 registros fuente, incluidos 18 sustituidos, con módulo, lote, rutas candidatas, evaluación estática y evidencia pendiente. Los detalles por proceso, datos, tenant, API, migración y pruebas se cierran en el contrato de cada lote.

| ID | Módulo | Backend localizado | CRM localizado | Android localizado | Hallazgo inicial / derivación |
|---|---|---|---|---|---|
| MOD-ORG | Empresas, usuarios y permisos | `organization/`, `identity/` | `organizations/`, `users/` | sesión bajo `auth/` | Alta de organización y primer ADMIN separadas; revisar provisión y tenant en L-01. |
| MOD-AUTH | Login e incorporación | `identity/` | `auth/` | `auth/` | Login, refresh y logout presentes; OTP, invitación, recuperación guiada y términos versionados no localizados. L-01. |
| MOD-DRV | Conductores y membresías | `fleet/`, identidad en `identity/` | `drivers/`, `users/` | `profile/` | Perfil y gestión básica presentes; falta proceso V3.1 de incorporación/revisión. L-02. |
| MOD-GRP | Grupos | `fleet/` | `groups/` | Datos de perfil/bootstrap | Gestión CRM presente; autorización vigente por organización. Confirmar contrato y lectura por rol en L-03. |
| MOD-VEH | Vehículos y flota | `fleet/` | `vehicles/` | Perfil/asignaciones | Capacidad existente; validar aprobación, vínculo y vigencia antes de oferta. L-03/L-04. |
| MOD-MNT | Mantenimiento | `fleet/` | No hay carpeta de feature dedicada localizada | No hay feature dedicada localizada | El módulo debe localizarse dentro del dominio real o declararse ausente; validar bloqueo de día completo y retorno en L-03. |
| MOD-RTE | Rutas | No hay paquete dedicado localizado | No hay feature de rutas localizada | No hay feature dedicada localizada | Ruta histórica/versión y edición requieren inventario funcional en L-03. |
| MOD-AVL | Disponibilidad del conductor | `fleet/`, contratos móviles en `operation/` | Acciones de conductor/operación | `profile/` | Existe disponibilidad; el flujo móvil actual puede encolarla offline, incompatible con V3.1. L-03/L-05. |
| MOD-ASG | Asignaciones operativas | `operation/` | `assignments/` | `assignments/` | CRM crea `ADMIN_DIRECT`; API/app reconocen confirmación móvil, pero el alta CRM no la ofrece. L-04. |
| MOD-SRV | Servicios y ejecución | `operation/` | Asignaciones | `assignments/` | Inicio/cierre móvil conectados al soporte offline heredado; migrar a confirmación online en L-05. |
| MOD-INC | Incidencias | `operation/` | `incidents/` | `incidents/` | Flujos base presentes; validar cierre terminal, tenant y recurrencia en L-06. |
| MOD-COM | Comunicados y notificaciones | `operation/` | `announcements/` | `announcements/` | Lectura explícita existe; proveedor/canal y entrega real quedan por comprobar en L-06. |
| MOD-GPS | Monitoreo y ubicación | `operation/` | Sin flujo de seguimiento CRM verificado | `location/` | Punto manual foreground presente; el criterio exige servicio iniciado además de consentimiento. L-05. |
| MOD-REP | Reportes | `operation/`, `audit/` | `reports/` | Sin módulo de reportes dedicado | API/CRM presentes; revisar filtros, versiones y exportación bajo tenant en L-07. |
| MOD-AUD | Auditoría | `audit/` | `audit/` | Sin módulo de auditoría dedicado | Modelo de solo anexado presente; validar eventos, alcance y retención en L-07. |
| MOD-UX | Dashboard, inicio y perfil | Agregados en módulos de dominio | `dashboard/` | `bootstrap/`, `profile/` | Vistas base presentes; conciliar indicadores con estados y permisos en L-07. |
| MOD-NFR | Requisitos transversales no funcionales | `shared/`, seguridad y configuración | núcleo compartido | `core/`, transporte y almacenamiento | PostgreSQL de pruebas disponible. Correo no configurado. SDK Android presente; licencias aún no aceptadas y sin dispositivo conectado; validación física piloto L-08. |

## Lectura de alcance

- Los seis módulos Java observados son `identity`, `organization`, `fleet`, `operation`, `audit` y `shared`; no se proponen microservicios.
- Flyway presenta V1–V12. V6 transforma roles históricos; V12 materializa recibos de sincronización offline y requiere decisión/plan de retiro en coordinación con backend.
- El registro marca 109 reglas como `CONFIRMADA_USUARIO`, nueve como `SUSTITUIDA`, 162 criterios como `ESPECIFICADO_NO_EJECUTADO` y nueve criterios como `SUSTITUIDO`. Se conservaron todos sus IDs.
- Esta matriz es un mapa de descubrimiento. No marca como aprobados los criterios de aceptación solo porque exista una pantalla, entidad, endpoint o prueba.
