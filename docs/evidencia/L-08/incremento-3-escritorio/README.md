# Incremento 3 — Escritorio, recuperación V50 y complemento físico

2026-10-22 America/Lima. **L-08 EN_CURSO; piloto BLOQUEADO.** Este incremento conserva los dos anteriores y los casos cerrados. No constituye acta de cierre.

## Resultados acreditados

### Escritorio conectado de solo lectura

[Resultado](observacion-5303599d-1e1d-4b49-92d7-8babb7f11753/resultado.json), [observaciones y solicitudes](observacion-5303599d-1e1d-4b49-92d7-8babb7f11753/navegadores.json).

- Edge `155.0.4283.62` y Chrome `155.0.8059.86`, doce combinaciones de navegador/rol/zoom. ADMIN: resumen, usuarios, grupos, vehículos, asignaciones, reportes y auditoría. SUPER_ADMIN: resumen, organizaciones, cuentas ADMIN y auditoría de plataforma.
- **66 observaciones de página y 804 comprobaciones**, no 804 pruebas unitarias distintas. Área física útil1280×720; zoom nativo100/200/300%, `devicePixelRatio`1/2/3, zoomCSS1 y escala visual1. Redimensionamiento de ventana real, sin emulación de viewport/DPR o pinch. Tablas acotadas, reflujo, controles con nombre, apertura por teclado y contención/restauración de foco de modal.
- Lecturas HTTP reales contra JAR V50 y PostgreSQL `ruta_fija_test/rf_test@5432`, con gates y autorización normales. Todas las huellas de filas/esquema iguales antes/después; cero altas, aceptaciones, migraciones o correo.
- La sesión del navegador se prepara **explícitamente como fixture sintético** con `JwtService` sobre las cuentas elegibles existentes. El puente de arranque responde al refresh del CRM con esa sesión de prueba; **no acredita login o rotación refresh reales**. Las operaciones empresariales no se simulan. El ticket transitorio del socket se emite por el backend normal, solo en memoria; no es un comando de escritura de base.
- Navegadores sin interfaz visible y perfiles desechables; no es ensayo humano con lector, no certifica WCAG y no prueba otros navegadores. La versión estable disponible al habilitar el piloto deberá volver a inventariarse.
- Fallos iniciales del arnés conservados en las otras carpetas de observación: calibración de área útil, rechazo de ticket por el límite readonly, evento Enter incompleto y salida de sockets. No hubo escrituras ni cuentas sucesivas.

El mecanismo nativo de zoom se contrastó con las fuentes primarias de [Chromium, preferencias HostZoomMap](https://raw.githubusercontent.com/chromium/chromium/main/chrome/browser/ui/zoom/chrome_zoom_level_prefs.cc) y [conversión entre nivel y factor](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/common/page/page_zoom.cc). La prueba exige también la observación del factor aplicado; escribir una preferencia por sí solo no produce PASS.

### L08-REST-03, destino autorizado55440

[Resumen](rest03-8ba8e8bc-ea46-40bd-9848-11a11a79b9fd/resultado-resumen.json), [integridad](rest03-8ba8e8bc-ea46-40bd-9848-11a11a79b9fd/equivalence-result.json), [operación normal](rest03-8ba8e8bc-ea46-40bd-9848-11a11a79b9fd/business-e2899286-791c-4e1a-a8ee-3a1d4ee24f4d.json).

- Respaldo real cifrado GCM del test compartido, leído sin escrituras; restauración única en instancia nueva `ruta_fija_test@127.0.0.1:55440`, UTF8/Spanish_Peru.1252,105 tablas, V50. SHA del archivo `f75d041e44dc5dd09a8553a42de63476bce165d52624049e15a126a602edc951`.
- Se exigieron todas las huellas originales. Las únicas equivalencias son cambios reversibles de escritura de casts literales y los siete permisos implícitos del propietario de las dos autoridades privadas, contrastados individualmente. No se omitieron tablas, restricciones, permisos u otras categorías ni se reescribió el manifiesto original.
- Aplicación del JAR V50 trazado: readiness/DB200; grupos sin autenticación401 y con SUPER_ADMIN403; ADMIN con términos aceptados consulta resumen, crea/reconsulta/retira un grupo propio por API normal. Un fallo del arnés al leer `content` en lugar de `items` se conservó; se recuperó el mismo grupo ya confirmado, sin crear otro ni forzar SQL.
- Únicas categorías modificadas por esa operación: grupo y auditoría. Cuentas, ledger, aceptaciones y demás tablas preservados; cero cuentas nuevas. La instancia quedó detenida conservando datos y roles.
- Intervalo máximo observado hasta comprobación empresarial: **318,731 segundos**, inferior a4h. El primer archivo de cronómetro quedó nulo por un error PowerShell y se conserva. El origen reconciliado es la creación de la carpeta, anterior a la consulta de salud fallida; incluye preparación/fallos y no reinicia el tiempo. No es el instante exacto de detección ni una caída del servidor de origen: el destino nuevo nunca había servido la aplicación. **Acredita este ensayo local de recuperación de datos/API; no promete recuperación de una pérdida física de la PC, RPO, respaldo independiente o reconexión real de clientes.**

### Regresiones y artefactos

[Resultados dirigidos](pruebas/resultado.json):21 de respaldo/cifrado/guardas/comparación y21 de política del complemento/preparación de servicio. Selecciones puras, cero fixtures de base, sin sustituir observaciones humanas. [Procedencia actual](candidato-procedencia.json):672 entradas del JAR preservado siguen idénticas a las clases/recursos de aplicación compilados. No se regeneró el artefacto ni final3.

## L08-FIS-02 — En intervención humana, sin resultado de batería todavía

[Autorización literal](../complementos-02-03-autorizacion.json), [propuesta aprobada](../../../operacion/L-08-complementos-pendientes-propuesta.md), [transporte real desde el Poco](fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/network-preflight.json).

RUN `3f0ba758-6eb9-4d2f-b948-dd1c7de3c430`. Cuenta nueva preparada por incorporación/revisión/habilitación normales; ledger compartido **1/3/8**, aceptaciones2. Marcador `l08-connected.active` y advisory lock activos, `TEST_CLEANUP_RELEASED=false`. **No ejecutar fixtures de base, limpiezas o retiros mientras esté activo.** No reabrir el caso anterior ni repetir `Serve` sin recuperar el mismo RUN.

**Incidencia de paquete conservada:** el primer APK `dc26a0d0...` se registró incorrectamente como separado sin comprobar metadatos y actualizó `pe.rutafija.conductor`. No se borraron datos ni se desinstaló. El [registro inicial no validado](../../../../artifacts/L-08-fis02-2026-10-22/manifest-inicial-no-validado.json) y el APK rechazado se conservan; [incidencia abierta](fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/incidencia-paquete.json). La app principal quedó detenida y falta su APK/huella anterior para restituir exactamente el binario; el usuario indicó el nombre de la app instalada, no un archivo.

APK **corregido**: `artifacts/L-08-fis02-2026-10-22/correccion-paquete/ruta-fija-l08-fis02-debug.apk`, SHA `e74dd49725499e3514c1fd853d81a1479f0614a6c5bfd07ddfa1a9b1c764609b`. Gradle recibe explícitamente `--android-project-arg=rutaFijaL08Isolated=true`; aapt comprueba `.l08` antes de instalar y el hash instalado se contrasta después. [Resultado](fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/instalacion-corregida.json): el principal no volvió a cambiar durante esta corrección; esto **no** equivale a restaurar su versión anterior. Login/disponibilidad del mismo caso ya llegaron HTTP200 con el paquete correcto. Sin otra cuenta, clave definitiva o release.

La regla de red fue creada por el usuario con elevación; esta sesión no pudo releer la política de Windows. El transporte real teléfono→Java se comprobó y el proxy rechaza pares distintos del Poco. Se deberá retirar exactamente la regla al cerrar el caso; no se da por retirada ahora.

Intervención acreditada del mismo caso: login/disponibilidad200, aceptación de oferta, consentimiento/inicio personales y puntosGPS reales. El usuario confirmó uso TalkBack satisfactorio; [observación](fis02-3f0ba758-6eb9-4d2f-b948-dd1c7de3c430/accesibilidad-observacion.json) separa relato humano y configuración actual. Pendiente: escala física grande (font_scale0,81 al corte), negativas de permisos/GPS/red/ciclo de vida previstas por la misión, medición15min sin alimentaciónUSB, cierre/logout, archivo y retiro selectivo. Criterio aprobado: caída≤5 puntos porcentuales, sin fallo térmico o pérdida persistente de captura. El arnés contrasta frescura con el umbral120s ya existente en `ServiceLocationStatus`; no inventa una autonomía diaria ni utiliza `dumpsys battery unplug` para simular desconexión. El porcentaje procede de la lectura personal del indicador del teléfono.

## Preservación y salida

[Verificación](preservacion-previa-fis02.json):144L07+36incremento1+451casofísico+22CAP25+75incremento2 y archivos protegidos intactos. Los cuatro documentos globales mutables conservan su respaldo. El siguiente cierre de evidencia se realizará tras archivar la intervención humana, sin modificar estos antecedentes.

Monitor/alertas independientes, medio de respaldo externo a la PC, RPO horario/comprobación diaria, cobertura/escalamiento24/7 y preparación real del piloto siguen pendientes. No se convierten en PASS por documentación o pruebas sintéticas. **Sin acta de cierre L-08 ni piloto activo.**
