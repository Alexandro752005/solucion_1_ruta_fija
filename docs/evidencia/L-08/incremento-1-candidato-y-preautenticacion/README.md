# L08.1 — Evidencia parcial de candidato y preautenticación física

2026-10-21. **L-08 EN_CURSO**, contrato0.4.0. Este archivo no es acta de cierre ni habilitación del piloto. [Resultados](resultados.json), [resúmenes de ejecuciones](resumen-ejecuciones.json), [procedencia](procedencia.json), [fuentes](fuentes.sha256), [cambios respecto de L-07](cambios-desde-L07.json).

## Comprobaciones archivadas

| Área | Resultado comprobado | Límite |
|---|---|---|
| CRM | 238 casos/47 archivos completos, typecheck/build correctos tras mantenimiento de dependencias. | Anterior al arreglo posterior del foco; no atribuir una suite completa de241 a la versión final. |
| Foco de login | Tres nuevas regresiones fallaron antes; cuatro dirigidas, typecheck/build y navegador real pasaron después. | Corrección del foco de campos inválidos, sin cambiar autenticación. |
| Navegadores | Edge155.0.4283.62/Chrome155.0.8059.85,13 comprobaciones públicas cada uno sobre el build corregido. | Sin backend/cuentas; zoomCSS y árbol accesible no sustituyen zoom del navegador, lector humano o flujos protegidos. |
| Dependencias | Angular22.2.3/Vitest4.1.12, override Vite8.3.5; npm audit final0. | Auditoría del árbol npm en esta fecha; no certificación de toda la seguridad. |
| Backend |25casos:14política con reloj sintético,4salud Spring/MockMvc+PostgreSQL y7seguridad/registrooffline. | DB-DOWN usa indicador doble; no se paró la base, no monitor independiente/alerta/guardia reales. |
| Flutter | analyze y29casos dirigidos con dobles correctos, incluidos acceso/formulario/online/sesión/ubicación/no ruta de pendientes. | No son29 pruebas físicas ni conectadas a backend. |
| Android | APK debug .l08 aislado, min29/target36, hash validado; release bloqueado sin firma aprobada también con el flag de aislamiento. | No clave definitiva, candidato release ni instalación sobre la app anterior. |
| Teléfono humano | PocoAndroid13/API33; instalación/arranque, formulario vacío y mensaje de rechazo503 tras único envío ficticio. | Sin backend/PostgreSQL/cuenta, permisos de ubicación, GPS, batería o TalkBack. |

La carrera de foco se debía a consultar `aria-invalid` antes del render de Angular. Se selecciona ahora el control inválido desde el modelo del formulario. Fallos y observaciones anteriores se conservan en los subdirectorios de navegadores; no se reemplazan por el resultado posterior.

## Ensayo físico público

RUN **9f2be6c1-77be-4d2b-8d72-f83dbd6f5247**. [Informe del formulario vacío](telefono/manual-empty-form.json), [informe humano de rechazo](telefono/manual-http-refusal.json), [cierre técnico](telefono/cierre-publico.json). Cero peticiones al enviar vacío; una petición `/api/v1/mobile/auth/login`,503; ninguna adicional durante105s observados. El usuario leyó «Ensayo local sin cuenta operativa. No se inició sesión».

Servidor ligado a127.0.0.1 terminado; únicamente `tcp:4311` creado por el caso fue retirado. Sin borrar datos o desinstalar apps. No consumir cupo ni confundir los valores ficticios con credenciales de una cuenta. El JSON de inspección del APK fue capturado **antes de instalar**; su `installed:false` es histórico. El resultado de instalación y el cierre registran la ejecución posterior.

## Conservación y procedencia

[Preflight posterior](preflight-posterior-V48.json), [comparación de base](base-conservada.json): ruta_fija_test/rf_test,V48/49migraciones válidas; ledger1/3/6,aceptaciones2 y todos los campos preservados del preflight inicial idénticos. Sin migraciones, alta persistente o limpieza global. [Preservación](preservacion.json):144archivos congelados deL07 y12protegidos coincidentes, incluido final3 y términos publicados. Los cuatro documentos globales evolucionan con respaldo en `memory/backup/2026-10-21-L08-incremento-1`.

Fuentes primarias consultadas para límites técnicos: [firma Android](https://developer.android.com/studio/publish/app-signing), [entrega Flutter Android](https://docs.flutter.dev/deployment/android), [contraste W3C](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). No son evidencia de ejecución ni acreditación legal.

## Pendientes del lote

Carga/p95, fallo/retorno y monitoreo/alertas/cobertura24/7 representativos, respaldo/restauración/RTO-RPO y medio independiente real, retiro por otras finalidades, recorrido físico operativo/GPS/batería/lector y preparación real de piloto. [Matriz vigente](../matriz-L08.md). Ninguno se considera cumplido por este incremento.
