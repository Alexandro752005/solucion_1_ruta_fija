# Reporte de cumplimiento académico Java

Fecha de medición: 2026-08-30.

## Metodología reproducible

La medición no presupone un porcentaje mínimo ni cuenta dependencias, archivos
generados o resultados de compilación. Se ejecuta desde la raíz del proyecto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\medir-cumplimiento-java.ps1
```

`Bypass` se limita a ese proceso y no cambia la política de ejecución del
equipo; permite que la medición se ejecute también en configuraciones Windows
que bloquean scripts locales por defecto.

El script cuenta líneas físicas no vacías en estas rutas de fuente:

| Lenguaje | Raíz medida | Exclusiones adicionales |
| --- | --- | --- |
| Java | `backend/src/main/java` | `target`, `build`, `generated`, dependencias. |
| TypeScript | `frontend/src` | `*.spec.ts`, `node_modules`, `dist`, `coverage`, `.angular`, `build`, `target`, `generated`. |
| Dart | Todo el proyecto | Las mismas exclusiones; no hay fuentes Dart en este proyecto. |

Las líneas de prueba TypeScript se excluyen para comparar código de producto
del CRM. Las pruebas Java no se encuentran bajo la raíz Java medida, por lo que
tampoco forman parte del numerador. Es una métrica de volumen de código, no una
medida de calidad ni de complejidad.

## Resultado real

| Métrica | Resultado |
| --- | ---: |
| Archivos Java de producción | 158 |
| Líneas Java no vacías | 8 713 |
| Archivos TypeScript de producción | 37 |
| Líneas TypeScript no vacías | 3 967 |
| Archivos / líneas Dart | 0 / 0 |
| Total de líneas no vacías comparables | 12 680 |
| Participación Java | **68,71 %** |

La participación Java se calcula como `líneas Java / (líneas Java + líneas
TypeScript + líneas Dart) × 100`. El resultado se deriva de la ejecución del
script incluido en el repositorio y puede recalcularse después de cualquier
cambio de código.
