# Revisión integral L-08 — Escritorio, REST03 y complemento físico en curso

2026-10-22 America/Lima; observaciones UTC hasta 2026-10-23T02:49Z. Contrato **0.7.0**. **No es acta de cierre.** [Matriz de doce criterios](matriz-L08.md), [incremento3](incremento-3-escritorio/README.md). El [informe anterior exacto](incremento-3-escritorio/revision-integral-antecedente-0.6.2.md) se conserva como antecedente; sus rutas relativas corresponden a su ubicación original.

## 1. Estado de L-08

**EN_CURSO.** Carga25, regresión dirigida y escritorio nativo están acreditados dentro de sus límites. FIS02 continúa protegido; batería, escala física y salida no están terminadas. La continuidad independiente y los demás criterios obligatorios permanecen pendientes. No se modifica el alcance para aparentar cierre.

## 2. Estado del piloto

**BLOQUEADO.** Faltan medios independientes, monitor/alertas y atención/escalamiento24/7 reales, privacidad y preparación de participantes/títulos/cuentas, aceptación ADMIN legítima, custodia/ensayo del titular y entrega autorizada. Hay además una incidencia de instalación propia todavía abierta. No se desplegó ni habilitó el piloto.

## 3. Cambios implementados

- Se conserva la corrección de `RetentionProcedure.execute`: bloqueo transaccional por plan antes de consultar replay; sin migración ni privilegios ampliados. Se conserva el parche AndroidPosition de GPS con indicadores medidos, timeout15s y cadencia30s.
- Escritorio: [lanzador readonly](../../../scripts/Invoke-RutaFijaL08DesktopReadOnly.ps1), `L08DesktopReadOnlyTrial` y `frontend/e2e/l08-operational-accessibility.mjs`; dos navegadores reales, perfiles temporales, zoom nativo, API real y comparación íntegra antes/después.
- REST03: [lanzador aislado](../../../scripts/Invoke-RutaFijaL08Rest03Database.ps1), equivalencia estricta del snapshot y [ensayo empresarial](../../../scripts/Invoke-RutaFijaL08Rest03Business.ps1), exclusivamente55440. Recuperación del mismo grupo tras fallo del arnés, sin duplicación.
- FIS02: extensión del mismo `L08ConnectedTrial`, [lanzador](../../../scripts/Invoke-RutaFijaL08PhysicalComplement.ps1), observaciones SELECT-only y registro personal de batería/lector/escala; una sola alta autorizada7→8. Regla temporal Privada y filtro de origen concreto; no acceso público.
- Tras el error de instalación se añadió [validación compartida del paquete APK](../../../scripts/RutaFijaL08ApkIdentity.ps1), invocada por el [builder](../../../scripts/Build-RutaFijaL08PhysicalApk.ps1), y regresión con los dos APK reales. La prevención nueva no restituye por sí sola el binario principal anterior.
- Este incremento modifica facilidades de ensayo, scripts y documentación; las672 entradas de aplicación del JAR actual siguen iguales. No se atribuyen cambios de pruebas a producción.

## 4. Pruebas ejecutadas y evidencia

| Procedimiento | Resultado acreditado | Límites y archivo |
|---|---|---|
| Regresiones anteriores reutilizadas |112backend,241CRM,59Flutter y50dirigidasGPS/analyze;92de retiro con81solapadas | [Incremento2](incremento-2-mision-y-controles/README.md). No se repitieron suites completas ni se sumaron solapamientos. |
| Perfil25 real HTTP/PostgreSQL aislado |100creaciones+100aceptaciones,0errores; p95durable343,0684/461,8702ms | [CAP25 cerrado](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/README.md).25actores sintéticos, unaPC, commit observado independientemente; no25personas/redes reales. |
| Carrera de retiro con commit |Una ejecución/auditoría/tombstone y replay; espera entre conexiones | [Resultado](cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/retiro-concurrencia.json). Solo evidencia mínima de recuperación sintética, no todas las finalidades/hijos. |
| Chrome/Edge nativos |12combinaciones,66páginas,804comprobaciones | [Resultado](incremento-3-escritorio/observacion-5303599d-1e1d-4b49-92d7-8babb7f11753/resultado.json). Zoom100/200/300%, área útil1280×720, tablas/teclado/modal. Headless y bootstrap de sesión explícitamente sintético; GET empresariales reales. No login/refresh ni lector humano. |
| Respaldo/restauración V50 |105tablas, filas/esquema/permisos íntegros con prueba reversible estricta | [REST03](incremento-3-escritorio/rest03-8ba8e8bc-ea46-40bd-9848-11a11a79b9fd/resultado-resumen.json). GCM real, origen5432readonly, destino55440. Ninguna categoría de hash descartada. |
| Operación restaurada |401sin sesión,403por rol; ADMIN crea/consulta/retira un grupo por API normal | [Business](incremento-3-escritorio/rest03-8ba8e8bc-ea46-40bd-9848-11a11a79b9fd/business-e2899286-791c-4e1a-a8ee-3a1d4ee24f4d.json). No cuentas nuevas; solo grupo/auditoría cambian en la copia. |
| Pruebas puras de respaldo y arnés |21seleccionadas de respaldo;21seleccionadas de complemento/preparación | [Resumen saneado](incremento-3-escritorio/pruebas/resultado.json). No42casos nuevos de producto, sin fixtures de base. |
| Guarda de APK con binarios reales |APK principal rechazado y `.l08` aceptado por la función del builder | [Resultado](incremento-3-escritorio/pruebas/guardas-apk-compartida.json). Sin instalar, conectar a BD o restaurar el principal; seis scripts analizados sin errores sintácticos. |
| Preservación previa a FIS02 |144L07+36incremento1+451caso físico+22CAP25+75incremento2; cinco archivos protegidos | [Resultado](incremento-3-escritorio/preservacion-previa-fis02.json). Final3 y términos intactos; no se regeneran manifiestos históricos. |

Todas las pruebas que usan fixtures de base siguen suspendidas durante la intervención humana. Observaciones exactas de lectura no equivalen a una nueva suite ni limpian datos.

## 5. Login, GPS, batería y TalkBack

| Área | Estado y fundamento |
|---|---|
| Login/disponibilidad físico |APROBADO en el caso anterior retirado y en la primera parte del FIS02 actual; interacción humana y HTTP200. Rechazo físico401 de contraseña inválida queda pendiente, distinto del503 público ya probado. |
| GPS real en primer plano |APROBADO en el caso anterior (14puntos) y observado también en FIS02 con consentimiento/permiso e inicio personales. FIS02 sigue En servicio, un tramo abierto;18puntos al corte02:49Z. No se acredita todo un recorrido o terminal geográfico. |
| Negativas de ubicación/red y ciclo de vida |PENDIENTES de contraste físico según misión: permiso denegado, GPS apagado, señal no disponible, pérdida de red y salida/retorno al primer plano. Las pruebas con dobles se conservan aparte; no se inventa una falta de señal real ni una cola offline. Hacer las perturbaciones fuera del intervalo de batería. |
| Batería |NO EJECUTADA aún. Criterio ya aprobado: caída≤5puntos porcentuales en15min sin alimentaciónUSB, fallo térmico o pérdida persistente de captura. No se vuelve a solicitar ese criterio. |
| TalkBack operativo |El usuario confirmó personalmente uso satisfactorio en los pasos solicitados. [Observación](incremento-3-escritorio/fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/accesibilidad-observacion.json) separa su relato del estado actual del servicio de accesibilidad; estar desactivado después no desmiente su uso previo. Falta confirmación prevista del arnés. |
| Escala física |PENDIENTE: font_scale0,81 al corte, igual que la configuración inicial. Se pidió seleccionar tamaño grande y comprobar los controles sin finalizar el servicio. No atribuir esta prueba al zoom de escritorio. |

Caso anterior `a999ef52-d87f-498d-9251-1581cbb72562` cerrado, no reabierto. Caso actual `3f0ba758-6eb9-4d2f-b948-dd1c7de3c430` protegido, ledger1/3/8 y aceptaciones2; `TEST_CLEANUP_RELEASED=false`. No emitir resultado físico global antes de cierre/logout, archivo y retiro del caso actual.

## 6. HTTP503

El rechazo público original fue un doble local intencionalmente indisponible, sin cuenta o base. Mensaje corregido distingue indisponibilidad503 de credenciales401; el usuario confirmó el texto canónico y petición única, sin reintentos durante105s observados. Los campos requeridos reportados después no se reprodujeron en dos widgets; no se inventó una corrección. [Evidencia](incremento-2-mision-y-controles/telefono/resultado-humano.json).

El primer error de conexión FIS02 tuvo otra causa comprobada: la app `.l08` seguía con el APK anterior mientras se había actualizado por error el principal. No llegó una petición de login al caso. Tras instalar el paquete correcto, login/disponibilidad200 y GPS reales quedaron observados.

## 7. Monitoreo y recuperación

Monitor local en otra JVM de la mismaPC: detección2,804s y retorno de salud13,024s, sondeo acelerado1s. [Resultado](incremento-2-mision-y-controles/disponibilidad/reconciliation.json). No acredita monitor externo, entrega de alertas o personas24/7.

REST03 verificó restauración íntegra y operación de grupo: intervalo máximo318,730778s (5min18,731s). El primer journal quedó nulo por error de PowerShell y se conserva. El origen reconciliado es la creación del directorio antes de la consulta de salud fallida, no una marca exacta de detección. No se reinició el cronómetro. La instancia nueva nunca había servido, el origen no se interrumpió y todo ocurrió en la mismaPC. **Es un simulacro local SQL/API, no RTO integral acreditado ante pérdida física del servidor, RPO horario o reconexión real de clientes.**

55438(V48),55439(CAP25) y55440(REST03) detenidos, datos conservados. Servicio compartido5432 permanece iniciado; no migraciones de desarrollo/piloto.

## 8. Versión y artefactos

**Versión liberada: ninguna.** No se consultó ni creó commit por prohibición expresa.

- [Candidato JAR V50](../../../artifacts/L-08-post-cap25-2026-10-22/manifest.json), SHA-256 `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`;672entradas iguales a la aplicación compilada actual. [Procedencia](incremento-3-escritorio/candidato-procedencia.json).
- [APK FIS02 corregido](../../../artifacts/L-08-fis02-2026-10-22/correccion-paquete/manifest.json), SHA-256 `e74dd49725499e3514c1fd853d81a1479f0614a6c5bfd07ddfa1a9b1c764609b`, paquete `pe.rutafija.conductor.l08`. Verificado antes de instalar y en el teléfono; debug, no release/firma definitiva.
- APK inicial erróneo conservado como rechazado; su presencia en el principal se documenta como incidencia, sin atribuirle aislamiento que no tenía.
- Términos ADMIN1.0.0 y final3 intactos; no publicación, correo adicional o despliegue.

## 9. Reversión e incidencia de instalación

JAR L07 V48 preservado y arranque sobre su propio snapshot V48 comprobado; no autoriza usarlo sobreV50 o degradar migraciones aplicadas. APK anteriores archivados no equivalen por inferencia al binario que estaba instalado originalmente en el principal.

**INCIDENCIA ABIERTA:** instalé por error un APK con ID `pe.rutafija.conductor` sobre la app principal. No se desinstaló ni se borraron datos, pero su binario previo fue sustituido. Se detuvo el principal y no volvió a modificarse al corregir `.l08`. [Registro](incremento-3-escritorio/fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/incidencia-paquete.json), [corrección separada](incremento-3-escritorio/fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/instalacion-corregida.json).

El usuario identificó el nombre «Ruta Fija Conductor», no un archivoAPK anterior. No existe copia/huella previa disponible que demuestre restitución exacta. Mantener el principal cerrado y conservar sus datos. Se requiere localizar ese APK identificable o aprobar una reparación alternativa concreta, sin presentar equivalencia no probada. CI-L08-10 sigue bloqueado.

## 10. Bloqueos y siguientes acciones concretas

1. **FIS02:** completar escala y negativas físicas pertinentes, medir batería sin carga, finalizar servicio/logout y rechazo401; registrar las confirmaciones personales del arnés. Archivar antes de retiro selectivo; comprobar cuentas/sesiones/periodo/contadores/marcador/lock y retirar solo la regla temporal. Sin otra alta ni reabrir casos cerrados.
2. **Principal Android:** localizar APK anterior verificable o revisar una reparación alternativa concreta; no sobrescribir nuevamente por inferencia ni declarar preservado el binario.
3. **Continuidad:** disponer realmente de medio separado y host independiente, configurar respaldo horario/verificación diaria/retiros, alertas y responsables/escalamiento24/7; ensayar recuperación integral representativa con esos recursos. No repetir ensayos locales para ocultar esta dependencia.
4. **Preparación del piloto:** privacidad/identificación, participantes y títulos/recursos, aceptación propia ADMIN legítimo, custodia independiente y ensayo del titular real; infraestructura/base/HTTPS y canales operativos según alcance. Confirmados pendientes, no satisfechos por casos sintéticos.
5. **Entrega:** reproducción/revisión y firma bajo condiciones autorizadas; no crear clave definitiva ni publicar. Resolver todos los criterios obligatorios antes de acta o piloto.

## 11. Actualizaciones documentales

Contrato0.7.0, matriz, ejecución, tablero, backlog, decisiones, memoria actual/objetivo y este informe se actualizan con el mismo corte. Antecedentes exactos preservados en `memory/backup/2026-10-22-L08-escritorio-rest03-fis02` y copias públicas indicadas. Archivo nuevo en [incremento3](incremento-3-escritorio/README.md); no altera los archivos congelados de L07, incrementos anteriores, casos retirados o final3.

**Conclusión comprobada: L-08 EN_CURSO; piloto BLOQUEADO; FIS02 protegido.** No hay acta de cierre ni ampliación implícita de autorizaciones.
