# Documentación de Ruta Fija

Índice de orientación actualizado el **2026-10-13**. La captura de conservación es WIP: estas referencias no certifican una instalación limpia ni una versión aprobada.

## Documentación vigente para trabajar

- [Plan de consolidación y registro de ejecución](fase3/plan-consolidacion_pre_fase_3.md): resguardo, conservación, higiene y condiciones pendientes.
- [Uso del Sistema](<Uso del Sistema.md>) y guías de [backend](../backend/README.md), [CRM](../frontend/README.md) y [Flutter](../mobile/README.md).
- Contratos API [ADMIN](contrato-api-f2-3-admin.md) y [móvil](contrato-api-f3-4-movil.md).
- [Cierre de incidencias por conductor / V11](ajuste-f4-4-cierre-incidencias-conductor.md).
- Alcance móvil: [ubicación foreground M7](ejecucion-m7-ubicacion-foreground.md), [offline mínimo M8](ejecucion-m8-offline-minimo.md) y [calidad M9](ejecucion-m9-calidad-evidencia.md).
- Decisiones de referencia: [ADR-007](decisiones/ADR-007-ubicacion-foreground-m7.md), [ADR-008](decisiones/ADR-008-offline-minimo-m8.md) y [ADR-009](decisiones/ADR-009-calidad-evidencia-m9.md).
- Procedimientos [F5.1 / regresión](ejecucion-f5-1-regresion-integral.md) y [F5.2 / demo temporal](ejecucion-f5-2-demo-temporal.md): ejecución posterior, con las guardas y limitaciones del plan. Los BAT de demo actuales requieren reparación.

## Evidencia histórica

- [Revisión del 12 de octubre](REVISION_GENERAL_2026-10-12.md): diagnóstico anterior a la conservación.
- Evidencias [F5.1](evidencia-f5-1-regresion-integral-2026-09-30.md), [F5.2](evidencia-f5-2-demo-temporal-2026-09-30.md) y [M9](evidencia-m9-calidad-evidencia-2026-09-26.md): resultados fechados, sin nueva validación en esta higiene.
- Los documentos de estado/diseño de fases anteriores, evidencias y ensayos F1/F2/F3 permanecen en su ubicación. Su nombre o fecha no autoriza su retiro.

## Pendientes y candidatos a revisión para retiro

| Elemento | Condición actual y decisión |
|---|---|
| [Aplicador V11](../scripts/Invoke-RutaFijaF44DriverIncidentClosureMigration.ps1) y [runner V11](../scripts/RunRutaFijaF44Flyway.java) | Receta histórica que exige un árbol V1–V11; hoy existe V12. Mantener ambos porque las tareas y documentación los referencian. Revisar su sustitución o retiro solo junto con sus consumidores. |
| [Auditor F1.3](../scripts/Test-RutaFijaMigratedSchemaF13.ps1) y [G2](../scripts/Test-RutaFijaG2AdminClosure.ps1) | Dependen del ciclo de vida de una BD temporal de recuperación. Mantenerlos; resolver ese contrato antes de decidir su retiro. |
| Exportaciones manuales y listado de estructura | Archivados externamente con hashes, sin referencias operativas detectadas. El origen/destino de cada archivo consta en el plan y en el manifiesto privado. |
| Integración de main y reproducción | Pendientes: conciliar README/manifiesto npm/lock, incorporar CI/Wrapper y validar desde una copia limpia. |
| Validación funcional y PostgreSQL | Pendientes: corregir guardas/BAT, ejecutar suites y recorrido humano, consultar historial Flyway y ensayar respaldo/restauración actuales en aislamiento. |

Se comprobaron referencias de los README, tareas, rutas de CI y destinos literales de scripts; todos los destinos comprobados se conservaron. Las rutas dinámicas, requisitos instalados y comportamiento de ejecución necesitan validación posterior. No se retiró ningún documento o script por prefijo, fecha o nombre.
