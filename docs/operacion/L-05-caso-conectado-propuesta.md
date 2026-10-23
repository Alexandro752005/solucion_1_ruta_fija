# L-05 — Caso conectado propuesto, v0.1.0

2026-10-20. **PROPUESTA; NO AUTORIZA ALTAS NI EJECUCIÓN PERSISTENTE**. Referencia reservada sin escritura en BD: `844b3bf6-f2fa-4666-bd0e-b1f7a1188696`. [Contrato](../contratos/L-05.md), [matriz](../evidencia/L-05/matriz-L05.md), [API relevo](../contratos/L-05-relevo-API-v0.1.0.md).

## Motivo y cupos

L-04 consumió y cerró su autorización de un CONDUCTOR conectado. Los participantes anteriores continúan deshabilitados; no se reabren ni se usa su autorización para L-05. Para comprobar un relevo entre dos clientes autenticados y transacciones HTTP distintas, se solicita **un caso nuevo, máximo dos altas CONDUCTOR sintéticas persistentes** en `ruta_fija_test`.

Ledger contrastado al corte4: SUPER_ADMIN1/1, ADMIN3/4, CONDUCTOR2/20. Si se autoriza y el preflight sigue coincidiendo, quedaría1/3/4 y **16 altas CONDUCTOR restantes**. Deshabilitar al terminar no devuelve cupo. No crear ADMIN, SUPER_ADMIN ni identidades reales; no cambiar términos/aceptaciones existentes.

Se utiliza el ADMIN sintético existente ya elegible y la misma organización sintética autorizada. Se crean recursos propios del caso: grupo, dos unidades, períodos, rutas/tipos de prueba y servicios/ofertas necesarios; no se adoptan vehículos, conductores o credenciales de casos cerrados. IDs y datos sintéticos se guardan con la referencia del caso. Se comprueba ausencia de colisiones antes de cada alta.

## Preparación y recorrido previstos

1. Preflight de solo lectura: base `ruta_fija_test`, runtime `rf_test`, Flyway exitoso vigente, roles/gates/ID-01, contadores/huellas y ausencia de otro caso protegido. Sin migración desde el arnés, desarrollo/piloto ni limpieza global. Estado reanudable y marcador/lock antes de la primera alta.
2. Alta de las dos identidades exclusivamente ficticias por mecanismos normales de incorporación: invitación, correo simulado, revisión estructurada restringida, aprobación, grupo, unidad/período y habilitación. Credenciales temporales solo en configuración privada del arnés; nunca en chat, argumentos, logs públicos o capturas.
3. CRM en navegador real local: emitir oferta para el primer participante. Flutter con repositorios/widgets de producción y HTTP real: aceptar e iniciar mediante acciones propias de esa cuenta e interacción **automatizada sintética**, con permiso/posición dobles claramente identificados. No se atribuye a una persona real ni al hardware.
4. CRM prepara relevo comprobado. Segundo participante acepta por el flujo normal; se acredita que la ejecución no cambia. Después confirma Asumir relevo en Flutter. Contrastar originales, planificación, participantes, autor/hora, snapshot, GPS simulado por tramo y cierre correcto del responsable entrante. Consulta/corrección temporal de varios tramos con originales intactos.
5. Servicio independiente del mismo caso para interrupción/atención: comunicar imposibilidad, detener captura, revisar conductor y unidad por separado y cancelar continuidad expresamente. No generar cuentas adicionales.
6. Carrera real de dos solicitudes HTTP sobre **otro relevo del mismo caso**: asumir contra retiro ADMIN, conservando una única resolución y sin reserva/tramo huérfano. El perdedor debe quedar rechazado por el estado vigente; tras conocer quién ganó, finalizar/interrumpir de forma segura al responsable que realmente quedó ejecutando. No simular resultados ni repetir con nuevas cuentas.
7. Retorno compatible con las mismas clases y esquema aditivo: contexto de pruebas con `app.qualification.operations-enabled=false`; comprobar rechazo específico de nueva operación, consulta y cierre/interrupción seguros de una participación previamente iniciada. No ejecutar un JAR antiguo ni revertir SQL/datos. Restaurar solo el contexto técnico de prueba, sin activar piloto.

Todo correo es simulado. Sensores/primer plano se doblan donde el entorno local no los proporciona; evidencia debe separar navegador real, renderer/repositorio Flutter, TCP/backend/PG y sensor ficticio. GPS físico/batería/calibración siguen en L-08.

## Preservación y salida

No crear sucesivas cuentas ante fallo: detenerse, conservar marcador, diagnóstico saneado y reanudar el mismo caso. No forzar estados por SQL, relajar gates, borrar aceptaciones o reiniciar ledger. La auditoría del flujo normal conserva autoría y referencias.

Archivar resultados, orden temporal y huellas antes de retirar. Limpieza exclusivamente selectiva del caso: deshabilitar sus dos cuentas/identidades y recursos, revocar sesiones/credenciales temporales y períodos según mecanismos previstos; conservar ledger, servicios, ofertas, tramos, incidencias/atenciones resueltas e historial. Coordenadas sintéticas conservan su política máxima de siete días; no se usa el arnés para ampliar su retención. Comprobar hashes de casos ajenos, términos y final3, salida y liberación de protección. Ante fallo no forzar desbloqueo.

La autorización solicitada cubre preparar/ejecutar este caso y sus verificaciones conectadas/race/retorno, con máximo dos altas. No equivale a cierre por número de pruebas: se exige matriz completa. No habilita piloto, SMTP externo, cuentas reales, claves definitivas, despliegue, Git/GitHub, regeneración final3, migraciones de desarrollo/piloto ni L-06.

## Estado actual

Propuesta concreta para revisión. Las pruebas nativas y de componentes existentes no se presentan como ejecución de este caso. El arnés conectado L-05 y su guardia se deben completar y comprobar antes de ejecutar la primera alta. Cualquier alcance adicional requeriría autorización nueva; esta propuesta no lo presume.
