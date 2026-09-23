# F3.4 — Contrato, matriz de seguridad y reportes reales

## Objetivo cerrado

F3.4 termina los días 5–6 de la Etapa 2 sin introducir una migración nueva ni Flutter. Publica el contrato OpenAPI/DTO, compatibiliza el CRM con los estados móviles y prueba que PENDING_RESPONSE, REJECTED y EXPIRED se reportan desde datos persistidos y aislados por tenant.

## Precondiciones

- F3.1A, F3.1B, F3.2 y F3.3 aprobadas.
- PostgreSQL 16 nativo en 127.0.0.1:5432; Docker no interviene.
- Desarrollo y ruta_fija_test se encuentran en Flyway V1–V10.
- backend/.local/ruta-fija-native.env existe, está ignorado por Git y no se comparte.

F3.4 no cambia el esquema. Crear V11 para documentación, presentación o una consulta de reporte sería una migración innecesaria y queda rechazado por la auditoría.

## Ejecución reproducible

Desde la raíz:

~~~powershell
.\scripts\Test-RutaFijaF34ContractReports.ps1
.\verificar_ruta_fija.bat
~~~

La auditoría F3.4 primero encadena F3.3: PostgreSQL, V1–V10, privilegios mínimos, rutas propias, recibos durables y ausencia de historial. Después revisa contrato CRM–móvil, DTOs, etiquetas Angular, estados de reporte y que las exportaciones reutilicen totales persistidos.

~~~text
F3_3_MOBILE_OPERATIONS_AUDIT=PASS ...
F3_4_CONTRACT_REPORTS_AUDIT=PASS flyway=V1-V10 contract=openapi_crm_mobile reports=persisted privacy=current_only docker=0
F3_4_NATIVE_VERIFY=PASS flyway=V1-V10 contract_reports=PASS docker=0
~~~

verificar_ruta_fija.bat ejecuta pruebas unitarias e integración solo contra ruta_fija_test y la limpia al final. Nunca usa solucion_ruta_fija_1 como base de pruebas.

## Matriz automatizada

MobileContractReportIT usa PostgreSQL 16 real, no mocks de negocio.

| Control | Resultado exigido |
| --- | --- |
| OpenAPI | Rutas móviles, DTOs críticos, Bearer y ausencia de accept/reject CRM. |
| Camino positivo | El conductor vinculado acepta una solicitud propia una vez y el reintento se reproduce. |
| UUID ajena y tenant | Un conductor no obtiene asignaciones de otro conductor ni de otro tenant. |
| Transición inválida | ADMIN_DIRECT no se acepta como respuesta móvil; el rechazo queda durable. |
| Vencimiento | El servidor expira la pendiente y el reporte obtiene el estado persistido. |
| Privacidad | Consentimiento, permiso, UPSERT de un punto, eliminación y ausencia de coordenadas en auditoría. |
| Reportes | Conteos reales de pendiente, rechazada y vencida; ceros explícitos y exportación XLSX. |

## Inicio cotidiano después de F3.4

iniciar_ruta_fija.bat llama a la auditoría F3.4 antes de iniciar backend y Angular. Si el contrato o los controles nativos fallan, no levanta un runtime inconsistente.

- CRM: http://localhost:4200
- API: http://127.0.0.1:8080/api/v1
- OpenAPI: http://127.0.0.1:8080/v3/api-docs

## Puerta G3

F3.4 queda aprobada cuando:

1. La API puede ser consumida por Flutter sin mocks de negocio.
2. No existe falsa aceptación o rechazo desde CRM.
3. Aislamiento, estados, vencimiento, recibo durable y privacidad pasan la matriz integrada.
4. Reportes y exportaciones reflejan datos persistidos del tenant.
5. F3_4_NATIVE_VERIFY=PASS y el build Angular terminan sin errores.

La siguiente autorización abre F4.1 de Flutter Android. F3.4 no crea pantallas móviles, notificaciones push, GPS de fondo, fotos ni almacenamiento externo.
