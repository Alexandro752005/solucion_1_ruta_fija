# Evidencia F5.1 — Regresión integral web–móvil–base de datos

- Fecha: 2026-09-30.
- Estado técnico automático: aprobado.
- Estado de la puerta: aprobada mediante confirmación explícita
  `VERDE — F5.1` del propietario.

## Resultado observado

Se ejecutó desde la raíz:

~~~powershell
.\scripts\Test-RutaFijaF51IntegralRegression.ps1
~~~

Salida final:

~~~text
F5_1_INTEGRAL_REGRESSION=PASS backend_unit=24 backend_integration=33 frontend=typecheck,test,build mobile=analyze,41-tests flow=ADMIN-CONDUCTOR-DB-CRM reports=PDF,XLSX,CSV audit=PASS multitenancy=PASS automation=expiry_only_without_fleet_mutation
RF_POSTGRESQL_STOP=PASS status=Stopped startup=Manual
~~~

## Matriz de resultados

| Comprobación | Resultado |
| --- | --- |
| Backend unitario | 24/24 aprobadas. |
| Backend de integración | 33/33 aprobadas contra PostgreSQL 16 local. |
| Migraciones | Flyway V1–V12 validadas; esquema en versión 12. |
| Angular | Typecheck aprobado, 42/42 pruebas y build de producción aprobado. |
| Flutter | `flutter analyze` sin incidencias y 41/41 pruebas aprobadas. |
| Flujo integral | ADMIN → CONDUCTOR → PostgreSQL → consulta CRM aprobado. |
| Reportes | Consulta operativa y salidas PDF, XLSX y CSV seguro aprobadas. |
| Auditoría | Asignación completada e incidencia móvil trazables por su entidad. |
| Multiempresa | REST y WebSocket permanecen aislados por organización. |
| Automatización | Solo expiración controlada de respuesta pendiente; sin mutación automática de conductor o vehículo. |

## Ajuste de evidencia

La primera lectura encontró un XML histórico de integración dentro de los
reportes de Surefire. No era un fallo funcional, pero alteraba el contador. El
auditor se corrigió para considerar solo archivos generados durante la corrida
actual y excluir pruebas `*IT` del total unitario. La segunda ejecución completa
produjo los conteos limpios registrados arriba.

## Observaciones no bloqueantes

- Mockito/JDK mantiene su advertencia sobre carga dinámica del agente durante
  las pruebas; no produjo fallos.
- Hibernate puede advertir sobre *follow-on locking* en una consulta; las
  regresiones funcionales y de aislamiento aprobaron.
- Flutter informó 17 paquetes con versiones nuevas incompatibles con las
  restricciones actuales. F5.1 no actualizó dependencias para evitar introducir
  cambios fuera de alcance.

## Estado posterior

PostgreSQL quedó detenido con inicio manual. Los puertos 5432, 8080, 4300 y
5037 quedaron libres, sin procesos de ejecución del proyecto.

## Cierre formal

El propietario confirmó `VERDE — F5.1` el 2026-09-30. La fase queda cerrada y
se autoriza avanzar a F5.2 respetando su propia puerta de validación.
