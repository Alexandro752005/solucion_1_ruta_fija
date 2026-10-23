# Acta de cierre L-05 — Servicios, tramos y ubicación

**2026-10-20, America/Lima. VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Alcance del [contrato0.6.0](../contratos/L-05.md) y registro v3.1 acreditado localmente. Conservados los contratos0.1.0…0.5.0 y los cinco incrementos sellados. Se aplican el OK VERDE de L-05 y las decisiones GPS-L05-01, SRV-L05-02, GPS-RET-L05-03 (máximo siete días), GPS-ACCESS-L05-04, SRV-INT-L05-05 y la autorización L05-CON-01 para máximo dos CONDUCTOR sintéticos. No modifica requisitos de otros lotes ni sus evidencias cerradas.

## Alcance y evidencias

[Matriz final CI-L05-01…10](../evidencia/L-05/matriz-L05.md), [archivo](../evidencia/L-05/cierre/README.md), [resultado](../evidencia/L-05/cierre/resultado.json), [fuentes actuales](../evidencia/L-05/cierre/fuentes.sha256) y [manifest de cierre](../evidencia/L-05/manifest-cierre.sha256).

Inicio/aceptación propios online, consentimiento/permiso GPS, originales por conductor/unidad, ruta finalizada y corroboración diferenciada, interrupción segura/atención separada, correcciones auditadas, relevo atómico, captura limitada/antigüedad y acceso/purga de puntos quedan verificados en las fronteras específicas de la matriz. No se atribuye a un recorrido TCP una corrección/revisión de puntos que se probó en API/PG/componentes.

79 regresiones finales Spring/PG con rollback correctas, incluida una nueva que conserva403 DRIVER_INACTIVE para sesión restringida; unión actual142 casos backend distintos con la evidencia previa válida, sin sumar reejecuciones.16 CRM y16 Flutter dirigidos, typecheck/analyze/build y JAR correctos. [Artefactos locales](../../artifacts/L-05-2026-10-20/manifest.json): JAR actual/CRM development y APK debug conservado con fuentes móviles de producción idénticas. No instalación/despliegue.

## Caso conectado, concurrencia y retorno

RUN **844b3bf6-f2fa-4666-bd0e-b1f7a1188696**. [Resultado contrastado](../evidencia/L-05/cierre/recorrido-conectado-contrastado.json), [resultado original](../evidencia/L-05/conectado-844b3bf6-f2fa-4666-bd0e-b1f7a1188696/resultado.json) y [postflight independiente](../evidencia/L-05/conectado-844b3bf6-f2fa-4666-bd0e-b1f7a1188696/postflight.json).

Cinco etapas reales Angular/Edge y nueve Flutter con repositorios/widgets de producción vía HTTP/TCP, backend y PostgreSQL reales; correo, permiso y sensor simulados. Preparación/aceptación/asunción no heredan el inicio; tramos consecutivos con idéntica frontera temporal y planificación/inicio originales conservados. Dos pérdidas sintéticas de respuesta tras commit200→504 se resolvieron por consulta, sin otra preparación/asunción. Carrera HTTP concurrente: ASSUME200/WITHDRAW409 RESOURCE_VERSION_CONFLICT, una resolución ASSUMED; no se afirma que ambos resultados ganadores hayan ocurrido en esa misma carrera.

Interrupción y revisión ADMIN separada de recursos, cancelación explícita y continuidad seguras acreditadas. [Retorno compatible](../operacion/L-05-retorno-compatible.md): mismas clases compiladas/V42 con nuevas operaciones deshabilitadas, rechazo específico OPERATIONAL_FEATURE_DISABLED, consulta y cierre propio de un servicio iniciado. No binario antiguo, migración inversa o despliegue. Los fallos/reanudaciones se conservan en el [historial](../evidencia/L-05/cierre/historial-diagnostico.json); no se crearon sucesivas cuentas. Ensayo automatizado sintético: no intervención humana nueva, Android físico, transferencia real ni calibración GPS/batería.

## Retiro, conservación y entorno

- Exactamente dos altas CONDUCTOR autorizadas. Cuentas/conductores/unidades/grupos deshabilitados, períodos revocados, sesiones de conductores y cinco familias ADMIN del arnés revocadas, catálogo del caso retirado. Cuatro servicios terminales, ningún tramo abierto/reserva viva del caso; historial/recibos/GPS con su retención conservados.
- Evidencia saneada archivada antes del retiro; secreto privado/marcador ausentes y lock libre. **TEST_CLEANUP_RELEASED=true**. Se verificó otra vez después de las regresiones. No limpiezas globales, recuperaciones de cupo, aceptaciones borradas ni casos anteriores reabiertos.
- Ledger **1 SUPER_ADMIN /3 ADMIN /4 CONDUCTOR**, cupos históricos restantes **0/1/16**, aceptaciones **2**. Hash de cada fila anterior de ledger/aceptaciones/servicios/ofertas conservado; huellas humanas y caso L-02 intactos.
- Solo `ruta_fija_test`, runtime `rf_test`, V42/Flyway final válidos. V36…V42 fueron migraciones aditivas autorizadas de los incrementos; esta revisión final no migró. [Postflight](../evidencia/L-05/cierre/l05-final-postflight-v42.json) y [12 inmutables/cinco sellos/161 archivos L-04](../evidencia/L-05/cierre/l05-final-immutable-check.json) correctos. **Final3 intacto, sin regeneración**.

## Bloqueos antes de uso real

Privacidad/identificación/finalidad/base y medidas efectivas de protección/retiro; ADMIN legítimo y aceptación propia; clave definitiva/custodia independiente y ensayo del titular; infraestructura/base piloto/HTTPS/copia cifrada/restauración; preparación de participantes, unidades y títulos locales. Dispositivo físico, batería y calibración siguen en L-08. Ninguno se declara cumplido mediante este cierre local. S/0, sin offline/colas, RENIEC/MTC, QR, SMTP externo, Git/GitHub, despliegue ni migraciones de desarrollo/piloto.

No quedan criterios obligatorios abiertos del **alcance funcional local de L-05**. [Concordancia documental](../evidencia/L-05/cierre/concordancia.json) contrastada. **Piloto real bloqueado. L-06 NO INICIADA / NO AUTORIZADA; requiere un OK VERDE separado.**
