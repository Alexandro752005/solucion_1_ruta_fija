# Documentación de Ruta Fija

Índice de trabajo actualizado el **2026-10-14**. El alcance vigente es el registro v3.1; este índice separa sus reglas de la evidencia histórica para que una fase antigua no reactive decisiones sustituidas.

## Base de trabajo vigente

- [Backlog de producto V3.1](backlog-producto-ruta-fija-v3.1.md): fases L-00 a L-08, dependencias y criterios de salida.
- [Registro normativo v3.1 en JSON](../specs/registro.json) y [lectura Markdown](../specs/registro.md): copia local del kit; conservar IDs, estados y contenido normativo.
- [Mapa real del repositorio](repo-map.json), [seguimiento de lotes](seguimiento/README.md) y [estado local de tareas](seguimiento/tareas.json).
- [Ejecución L-00](ejecucion/L-00.md), [inventario por módulo](ejecucion/L-00-inventario.md), [matriz CSV](ejecucion/L-00-trazabilidad.csv) con su [guía de lectura](ejecucion/L-00-trazabilidad.md), [línea base](evidencia/L-00/baseline.md), [decisión Gmail del piloto](evidencia/L-00/decision-Gmail-2026-10-14.md) y [contrato L-00](contratos/L-00.md).
- [Perfiles locales de trabajo](../agents/README.md) y [memoria del proyecto](../memory/README.md).
- Guías técnicas observadas: [backend](../backend/README.md), [CRM](../frontend/README.md) y [Android](../mobile/README.md).

## Reglas de vigencia

- Los únicos roles del producto son `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. La migración histórica V6 conserva su función de transformar datos previos; no añade roles al contrato vigente.
- La versión v3.1 excluye funcionamiento y colas offline. El código M8/V12 que todavía existe se inventaría y retirará de forma controlada en el lote correspondiente, no se tomará como requisito vigente.
- Las decisiones, tareas y criterios del registro conservan sus IDs. Las propuestas técnicas locales no cambian reglas de negocio aprobadas.
- El proyecto se trabaja y evidencia localmente. No se ejecutan comandos de Git ni se publica a GitHub.

## Referencias históricas

- [Plan de consolidación previo a fase 3](fase3/plan-consolidacion_pre_fase_3.md): documento de APF1 fechado 2026-10-12; no describe el checkout APF3 abierto ni su estado actual.
- Los estados, diseños, auditorías y evidencias de F1–F5/M1–M9 documentan cortes anteriores. Se conservan como procedencia, pero no gobiernan la especificación v3.1.
- [Offline mínimo M8](ejecucion-m8-offline-minimo.md) y [ADR-008](decisiones/ADR-008-offline-minimo-m8.md): antecedente del comportamiento presente en el árbol. La exclusión v3.1 tiene precedencia.
- [Revisión general del 12 de octubre](REVISION_GENERAL_2026-10-12.md): diagnóstico fechado, útil como antecedente y no como resultado de la baseline actual.

## Lotes

La línea base local de L-00 pasó backend, CRM y Flutter. L-00 está VERIFICADA tras aprobar Gmail SMTP S/0 como solución provisional y la excepción de dominio del piloto. No se envió correo y SMTP no está integrado; contraseña de aplicación y entrega controlada corresponden a L-01. La prueba física Android corresponde a L-08. No se inicia L-01 sin solicitar y recibir OK VERDE.