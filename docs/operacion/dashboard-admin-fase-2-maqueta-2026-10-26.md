# Dashboard ADMIN — cierre de fase 2: maqueta aislada

Fecha: **26/10/2026**, America/Lima. Resultado: **MAQUETA_IMPLEMENTADA_Y_VERIFICADA_PARA_REVISION_VISUAL**. Autorización: «OK VERDE» después de cerrar la fase 1. No se atribuye una aprobación visual humana del resultado.

La maqueta implementa el [contrato DA-01](../contratos/DA-01-dashboard-admin-v0.1.0.md): cabecera, franja de prioridades, cinco KPI, seis paneles, tres donas SVG, esquema de ruta, conductores por código, avisos, detalles secundarios y pie. Los estados disponibles para revisión son sin movimiento, operación mixta, atención prioritaria, datos no disponibles y carga.

## Abrir la revisión local

En esta sesión: **http://127.0.0.1:4312/**. Es un servidor exclusivo en loopback, con datos sintéticos y conexiones de aplicación bloqueadas por su política de contenido. La barra superior identifica expresamente la maqueta y permite elegir el escenario.

La vista normal de CRM sigue en `http://127.0.0.1:4300`; su ruta `/dashboard` conserva la implementación anterior. Esta entrega no sustituye aún el Resumen operativo, no requiere iniciar sesión para ver los ejemplos y no da acceso a cuentas o recursos reales. El punto de entrada separado no importa configuración del CRM, autenticación, cliente HTTP ni router.

Reproducción desde la raíz del proyecto, usando las herramientas ya instaladas:

```powershell
Set-Location frontend
npm.cmd run build -- --configuration=dashboard-preview
Set-Location ..
python -m http.server 4312 --bind 127.0.0.1 --directory .runtime/dashboard-admin-fase-2-20261026/preview-dist/browser
```

Ese comando de servidor estático es la alternativa reproducible básica. El servidor de esta sesión añade `no-store` y `connect-src 'none'`; su implementación, registro de PID y capturas de consola quedan en `.runtime/dashboard-admin-fase-2-20261026/`. Si 4312 está ocupado, comprobar la identidad del proceso o elegir otro puerto local; no terminar un proceso ajeno para liberar el puerto. Un servidor iniciado en primer plano se detiene con Ctrl+C en su propia consola.

## Implementación y aislamiento

- `frontend/src/app/features/dashboard/admin-dashboard/`: vista de presentación, modelos, tarjeta KPI, dona, iconos SVG y detalles secundarios. Los componentes reciben datos y emiten intenciones; no consultan la API ni ejecutan transiciones.
- `frontend/src/preview/`: escenarios sintéticos y aplicación de revisión. `frontend/src/dashboard-preview.main.ts` es su entrada independiente.
- `frontend/tsconfig.dashboard-preview.json` y configuración `dashboard-preview` aditiva en `angular.json`: compilación separada hacia `.runtime/`, sin cambiar las configuraciones normales.
- Producción conserva `src/main.ts`. Se contrastaron marcadores exclusivos de la maqueta en ambos resultados: presentes en la compilación de revisión y ausentes del bundle normal del CRM.

No se modificaron el componente Resumen actual, rutas, shell, servicios de API, permisos, fuentes de backend, Android, dependencias ni migraciones. Los 20 archivos registrados por la evidencia de fase 1 conservaron sus hashes; esa evidencia también preservaba los 34 archivos de la evaluación inicial.

La maqueta muestra códigos sintéticos inequívocos con referencia completa accesible; no nombres personales. El esquema usa paradas de ejemplo, no GPS personal ni vehículos en movimiento. El selector cambia solo el escenario visual de fecha: todavía no consulta cohortes reales. Los enlaces abren una explicación de su destino en la revisión, sin navegar a la aplicación real. La exportación abre el diálogo de alcance, pero no crea emisiones ni descarga archivos. El visor de ruta de esta fase presenta sus paradas; los controles definitivos se completan en las fases de integración e interacción.

## Verificación ejecutada

| Comando o comprobación | Resultado final |
| --- | --- |
| `npm.cmd run typecheck` | Código 0 |
| `npm.cmd run test:ci` | **48 archivos / 279 pruebas aprobadas**: 265 previas y 14 nuevas de presentación/dona |
| `npm.cmd run build -- --configuration=dashboard-preview` | Código 0, sin advertencias; tamaño inicial 186,76 kB en este corte |
| `npm.cmd run build` | Código 0, sin advertencias; la maqueta y sus fixtures no aparecen en el resultado de producción |
| Revisión automatizada en Edge real | **7 capturas**, cinco KPI y seis paneles presentes, distribución responsiva y sin desbordamiento horizontal |
| Aislamiento del navegador | **0 solicitudes API, 0 WebSocket operativos, 0 errores JavaScript** |
| Diálogos | Foco dentro del diálogo, cierre con Escape y alcance de exportación/ruta comprobados mediante entradas de ratón/teclado |

Los 14 casos nuevos cubren composición, denominador cero, categoría de atención segura, prioridad de seguridad, fallos sin ceros ficticios, carga, diferencia entre incidencias actuales y cohorte, selección de fecha, intenciones expresas, identidad por código, partición canónica de la dona, rechazo de cantidades inválidas y redondeo de tercios sin forzar 100 %.

La primera compilación aislada detectó 267 bytes de exceso sobre el presupuesto de estilos de 8 kB. Se separaron los detalles secundarios en un componente propio y se repitieron la suite y las compilaciones: sin advertencias. No se elevaron los límites de tamaño. La automatización inicial de Escape usaba un clic programático; la comprobación final empleó entrada confiable de ratón y códigos de teclado completos, manteniendo el diálogo nativo.

No se ejecutaron pruebas de PostgreSQL ni se inició/reinició el runtime operativo. Al cierre seguían identificados los mismos procesos de API 8080, CRM 4300 y puente móvil 4311 observados antes de la maqueta; solo se añadió el servidor sintético 4312.

## Capturas para revisión

Los nombres indican ancho de ventana; el JSON conserva las dimensiones exactas del recorte principal, que pueden ser menores por la barra de desplazamiento del navegador. No se declara igualdad píxel a píxel con la referencia. Las alturas se adaptan al contrato de legibilidad, categorías completas y prioridades.

- [Escritorio, sin movimiento](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-1106-normal.png).
- [Escritorio, operación mixta](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-1106-actividad.png).
- [Atención prioritaria](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-1106-prioridad.png).
- [Datos no disponibles](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-1106-error.png).
- [Ventana móvil de 390 px](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-390.png).
- [Ventana de 320 px](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-320.png).
- [Ampliación CSS al 150 %](../evidencia/dashboard-admin-fase-2-2026-10-26/maqueta-admin-zoom150.png).

[Resultados de navegador](../evidencia/dashboard-admin-fase-2-2026-10-26/browser-validation.json) y [manifiesto de fuentes/evidencias](../evidencia/dashboard-admin-fase-2-2026-10-26/manifest.json). Los registros son de este corte; no certifican todavía la futura integración con datos reales ni sustituyen la revisión visual del propietario.

## Paso siguiente

**Fase 3 pendiente:** conectar la vista a las fuentes reales mediante adapter/facade, añadir el contexto temporal aditivo y conservar los estados y autorizaciones de DA-01. Esta fase 2 deja componentes reutilizables y una revisión concreta, sin adelantar esa conexión ni publicar cambios en GitHub.
