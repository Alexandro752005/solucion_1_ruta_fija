# API compartida L-02 — 0.2.0

Contrato técnico del alcance autorizado y **VERIFICADO como FUNCIONAL_LOCAL_PILOTO_BLOQUEADO** el 2026-10-19. No habilita piloto. Base `/api/v1`. JSON camelCase. Todos los GET no-store. Mutaciones con `operationKey`8..100, `expectedVersion`long y `reason`1..500 sin PII/secretos.409 por versión/idempotencia/estado en conflicto;404 objeto ajeno;403 rol indebido. Backend decide tenant. ADMIN tiene gates de identidad/términos, CONDUCTOR solo sesión MOBILE propia. No archivos.

## Consultas

- ADMIN: GET `/drivers/{driverId}/qualification`, referencias/estado sin PII.
- ADMIN: GET `/drivers/{driverId}/qualification/expediente`, vista restringida con documentos estructurados personales y revisión unidad/empresa.
- CONDUCTOR: GET `/mobile/qualification`, estado propio; disponible con cuenta provisionada/aprobada aunque Driver aún inactivo. Token MOBILE con `accessScope=INCORPORATION` no autoriza endpoints operativos. Refresh/login relee estado antes de emitir scope OPERATIONS.

`Qualification`:

```text
{driverId,accountId,requestId,version,suspended,operationalEnabled,
 personalDocumentsStatus,personalValidUntil,blockers:[string],
 periods:[Period],unitRequests:[UnitRequestView],
 catalog:[Requirement],reviews:[Review],personalDocuments:[Document],
 observedAt}
```

`Requirement`: `{scope:"VEHICLE"|"COMPANY",id,title,mandatory,fieldKeys:[string]}`.
`Review`: `{scope,requirementId,vehicleId|null,fields:{string:string},expiryMode:"DATED"|"NO_EXPIRY"|"UNKNOWN",validUntil|null,applicability:"CONFIRMED"|"POR_CONTRASTAR",condition|null,sourceReference|null,result:"CONFORME"|"OBSERVADO"|"NO_APLICA_JUSTIFICADO"|"POR_CONTRASTAR",reviewReference|null,version}`. Fields solo en expediente ADMIN; consultas ordinarias/móviles retornan fields vacío y referencias de revisión ocultas. Personal `Document` conserva forma de ConductorDocumentaryContract.Document solo en expediente ADMIN; fuera de esa vista personalDocuments vacío.
`Period`: `{id,vehicleId,kind:"OWN"|"COMPANY",startsAt,endsAt|null,status:"AUTHORIZED"|"REVOKED",reason,version}`. Límites `[startsAt,endsAt)` UTC; estado temporal derivado por reloj del servidor.
`UnitRequestView`: `{id,vehicleId|null,kind,status:"PENDING_REVIEW"|"APPROVED"|"REJECTED",reference,createdAt}`. Solo referencias sin PII en consulta ordinaria.

## Mutaciones ADMIN

- POST `/drivers/{driverId}/qualification/reviews`: `{expectedVersion,operationKey,reason,vehicleId|null,scope,documents:[ReviewInput]}`. ReviewInput usa campos de Review salvo scope/vehicleId/version; catálogo separado empresa/vehículo. Revisión no activa Driver ni crea vínculo.
- POST `/drivers/{driverId}/qualification/periods`: `{expectedVersion,operationKey,reason,vehicleId,kind,startAt|null,startDate|null,endAt|null,endDate|null}`. Exactamente inicio fecha o instante; fin fecha sin hora incluye todo ese día Lima; empresarial exige fin. Solo unidad/servicio revisados; no requiere Driver activo y no lo activa. Una unidad vigente por conductor y viceversa, exclusión temporal en PostgreSQL. No adoptar links heredados como periodos revisados.
- POST `/drivers/{driverId}/qualification/periods/{periodId}/revoke`: comando básico. Preserva periodo/historial; no revocar en conducción incompatible; invalida ofertas/asignaciones futuras afectadas, sin sustitución automática.
- POST `/drivers/{driverId}/qualification/suspend`: comando básico más `{evidenceReference}`. Motivo/evidencia de revisión requerida; no suspende cuenta ni elimina aprobación personal. Invalida pendientes/futuras y registra atención segura del servicio en curso.
- POST `/drivers/{driverId}/qualification/resume`: comando básico. Levanta suspensión solo tras revisión vigente y periodo; no cambia persona/historial/cupos.
- POST `/drivers/{driverId}/qualification/activate`: comando básico. Requiere aprobación, identidad, grupo, vigencias, revisión unidad/empresa y periodo actual; habilita Driver, no concede disponibilidad automáticamente ni acepta ofertas.
- POST `/drivers/{driverId}/qualification/personal-documents`: `{expectedVersion,operationKey,reason,documents:[DocumentChange],samePersonConfirmed:true,originalReviewed:true,reviewReference}`. Renovación/corrección material restringida tras aprobación, preservando IDs/historial. DNI raíz no se sustituye: conflicto explícito si se intenta cambiar la persona/huella. Corrección de discrepancia material del registro Driver al DNI ya revisado requiere cotejo, no sustituir HMAC. Cambios de nombre/licencia/documentos afectados quedan revisados, antiguos conservados en revisiones restringidas. No acepta documentos desconocidos por inferencia.
- POST `/drivers/{driverId}/qualification/unit-requests/{id}/decision`: comando básico más `{approved:boolean,vehicleId|null}`. ADMIN propio decide; elegir unidad existente revisada no equivale a autorizar periodo o servicio.

## Mutaciones CONDUCTOR

- POST `/mobile/qualification/unit-requests`: `{expectedVersion,operationKey,reason,vehicleId|null,kind,reference}`. Solicita unidad existente o incorporación referenciada de unidad propia; ADMIN revisa el original presencial sin carga. Una solicitud abierta por conductor; reintento misma clave es idempotente. No crea Vehicle automáticamente ni habilita operación.
- POST `/mobile/qualification/documents`: `{expectedVersion,operationKey,reason,documents:[DocumentChange]}`. Solo presenta datos estructurados para revisión, resultados PENDING/POR_CONTRASTAR; nunca se autoaprueba. Permanece acceso limitado.

Todas las mutaciones retornan `Qualification` según rol/vista permitida. ADMIN reviews/personal-documents usan respuesta restringida; resto no devuelve PII. Eventos con actor/tenant/IDs/versión/hora sin documentos/copias/secretos. Ninguna pantalla interpreta HTTP no confirmado como éxito ni reenvía automáticamente una mutación.

## Compatibilidad y habilitación

La activación heredada de Driver se redirige a la misma política; enlace heredado sin vigencias/revisión rechaza y remite al flujo nuevo. Reserva, aceptación e inicio comprueban licencia/documentación/vínculo durante todo el periodo previsto. La pérdida de vigencia bloquea operación conservando aprobación/cuenta/consulta; servicios futuros quedan pendientes de reasignación y los que ya están en curso requieren incidencia/relevo seguro. Cierre de servicio no rompe vínculo.

## Precisiones del incremento implementado (2026-10-19)

- `Qualification.serviceAttention`: referencias `{id,assignmentId,kind,status,scheduledAt,scheduledEndAt}`; conserva el servicio pendiente de reasignación o relevo seguro, sin terminarlo automáticamente.
- `Qualification.personalDocumentStates`: `{requirementId,result,applicability,observation}` sin campos personales; las indicaciones de subsanación pasan el control de PII conocida del expediente.
- GET `/mobile/qualification/personal-catalog`: catálogo personal vigente `{requirementId,title,mandatory,fieldKeys}`, sin valores del expediente.
- POST `/drivers/{driverId}/qualification/vehicle-correction`: comando básico más `vehicleId`, `plate`, `originalReviewed:true`, `reviewReference`. Cotejo restringido; conserva Vehicle/placas previas y snapshot de servicios nuevos; vuelve a exigir revisión compatible. Servicios heredados sin snapshot permanecen explícitamente no registrados, sin reconstruir placas pasadas.
- La sesión móvil JSON incluye `accessScope`; un JWT INCORPORATION no adquiere operación automáticamente. Login/refresh relee la elegibilidad.
- Fecha documental sin hora se expresa como medianoche Lima del día siguiente (límite exclusivo), compatible con el último día incluido de un período.

## Incidencia automática y compatibilidad V28

`Incident.source` admite `CRM_WEB`, `MOBILE_APP` y `SYSTEM`. La detección automática de pérdida de elegibilidad crea `SYSTEM`, con `reportedById` nulo/omitido y etiqueta `Sistema`; no se atribuye al creador ni a otro usuario. No añade un rol. Los orígenes humanos mantienen reportante obligatorio en persistencia. El cierre móvil de incidencias propias sigue limitado a `MOBILE_APP` del conductor; no permite cerrar una incidencia automática como propia.

Los campos opcionales nulos pueden omitirse por la configuración Jackson; los clientes no deben exigir la presencia de `reportedById` para `SYSTEM`. La misma regla se aplica a otros miembros opcionales del contrato. CRM/Flutter conservan el origen sin fabricar una referencia `CUENTA-null`.
