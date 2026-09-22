# Cierre técnico F1.4 + F1.5 — 2026-09-21

## Resultado

Los dos bloques aprobados se ejecutaron sin Docker:

| Puerta | Resultado | Evidencia |
| --- | --- | --- |
| F1.4 integración nativa | Aprobada | `mvn verify`: 21 unitarias y 13 integraciones, sin fallos ni errores. |
| PostgreSQL de pruebas | Aprobada | PostgreSQL 16.15, Flyway V1–V5 y migración repetible de privilegios en `ruta_fija_test`. |
| F1.5 frontend | Aprobada | lint y 39 pruebas Angular aprobadas. |
| F1.5 proxy real | Aprobada | Login, `/me`, refresh, ticket WebSocket, `stream.ready` y rechazo de replay desde `http://localhost:4200`. |
| Aislamiento | Aprobado | Solo se limpió `ruta_fija_test`; no se apuntó a desarrollo ni recuperación. |
| Cierre de recursos | Aprobado | El smoke cerró 8080 y 4200 al terminar. |

## Decisiones aplicadas

- Se eliminaron las dependencias de Testcontainers del backend y las tres
  pruebas `*IT` comparten una estrategia nativa protegida.
- La validación de URL, base y roles impide que una prueba use por accidente
  `solucion_ruta_fija_1`.
- `rf_test` puede realizar DML necesario para los fixtures, pero no crear
  tablas; Flyway conserva DDL bajo `rf_migrator`.
- Angular ahora reenvía `/ws` con soporte WebSocket y mismo origen.
- El smoke de tiempo real usa una semilla temporal aislada; no habilita datos
  demo en el runtime normal.

## Límites conservados

- README y manuales heredados con instrucciones Docker se actualizan en F1.6.
- Los archivos Docker heredados se preservan hasta la evidencia de recuperación
  posterior de F1.7; no se usan para operar ni verificar estas puertas.
- El proyecto mantiene los roles funcionales actuales. La unificación futura
  de roles corresponde al segundo entregable, no a F1.4/F1.5.
