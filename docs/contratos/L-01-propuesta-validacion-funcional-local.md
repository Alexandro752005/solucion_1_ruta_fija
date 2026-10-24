# SC-L01-LOCAL-01 — Propuesta de cierre funcional local

Estado: **BORRADOR NO APROBADO; NO MODIFICA EL CONTRATO**. Fecha: 2026-10-17.

El titular solicita evaluar este cambio después de ambos ensayos. Se prepara la comparación ahora como trabajo independiente; **los ensayos humanos siguen pendientes** y sus resultados deberán añadirse antes de solicitar una decisión. L-01 permanece EN_CURSO.

## Contrato vigente y cambio concreto propuesto

Fuentes: [contrato L-01](L-01.md), [matriz vigente](../evidencia/L-01/matriz-cierre-L01-revision-2026-10-14.md), decisiones OP-ADMIN-01, ID-01 y protocolo de recuperación aprobado. Las últimas actualizaciones mantienen explícitamente aceptación real CI03 y custodia/ensayo real CI07 como obligatorios. El contrato vigente **no permite** cerrar esos requisitos por equivalencia con personas/claves sintéticas.

| ID | Requisito original que permanece abierto | Cambio propuesto para un cierre exclusivamente funcional local | Evidencia disponible hoy y límite |
|---|---|---|---|
| SC-03 | CI-L01-03: términos exactos aprobados/publicados y aceptación expresa persistida por ADMIN legítimo elegible. | Conservar aprobación/publicación inmutables. Para el cierre funcional local, admitir **además de las pruebas técnicas**, un ensayo manual satisfactorio del ADMIN sintético operado por el titular, con gate previo, contraseña/OTP, lectura/aceptación explícita, habilitación y contraste de la fila. Mantener aceptación operativa del ADMIN legítimo como condición bloqueante del piloto real. | Documento aprobado/publicado; motor y clientes normales ya probados con datos sintéticos; lanzador manual preparado. **Aceptación humana sintética aún no ejecutada; aceptación operativa legítima inexistente.** |
| SC-07 | CI-L01-07: custodia elegida fuera del servidor, copia cifrada independiente comprobada, código separado, inscripción legítima previa y ensayo operativo del titular. | Para cierre funcional local, admitir ensayo manual satisfactorio con SUPER_ADMIN y clave temporales, copia cifrada leída/usada en la misma PC, protocolo Ed25519 vigente, nuevo correo simulado/contraseña, consumo/rotación y revocación comprobados. Mantener toda custodia independiente y preparación del titular real como condición bloqueante antes de inscribir su clave definitiva y operar el piloto. | Backend/CLI temporales contra HTTP/PG reales probados automáticamente; kit vigente y lanzador manual disponibles. **Ensayo humano temporal aún no ejecutado; solo PC Windows confirmada y ninguna independencia acreditada.** |

Los restantes criterios aprobados conservan alcance y evidencia. CI-L01-01 ya VERIFICADO no se repite. No se propone cambiar términos 1.0.0, cupos, ID-01, estados, aislamiento, OTP, autoría de la aceptación o algoritmo de recuperación.

## Condiciones que seguirían bloqueando cuentas y piloto reales

1. Persona ADMIN legítima preparada con identidad/organización elegibles y aceptación expresa propia de la versión vigente; no actuar por ella ni conceder doble rol real al titular.
2. Custodia real fuera del servidor, medios independientes confirmados y copia comprobada, código separado, inscripción legítima previa y ensayo del titular sin intervención del agente sobre su clave privada.
3. Información separada de identificación/privacidad y condiciones de protección y conservación antes de nuevos datos reales; su borrador no queda aprobado por esta propuesta.
4. Entorno piloto, base, acceso, copias y restauración preparados y autorizados cuando corresponda. No se autoriza migración, despliegue, correo adicional o creación de cuentas reales por aprobar un criterio local.
5. Recursos/títulos/requisitos documentales desconocidos siguen bloqueantes; sin offline, RENIEC/MTC, QR ni ampliación de S/0 o cupos.

## Evidencia y decisión necesarias antes de aplicar la propuesta

- Archivar y contrastar `PASS_MANUAL_SYNTHETIC_ONLY` y `PASS_LOCAL_TEMPORARY_COPY_ONLY`, sus IDs/huellas/fecha de servidor y límites, antes de solicitar aprobación de SC-03 y SC-07.
- Solicitar aprobación explícita de los IDs y del nuevo tipo de cierre **FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**. La autorización para ejecutar los ensayos no aprueba estos cambios.
- Solo después de esa aprobación, modificar contrato/matriz por adenda versionada y conservar los requisitos originales como **pendientes de habilitación del piloto**, nunca como cumplidos.
- No usar la propuesta para iniciar L-02: el gate independiente **OK VERDE** sigue vigente y se solicitará en su momento.

Sin aprobación, prevalece el contrato original y L-01 continúa EN_CURSO aunque ambos ensayos técnicos pasen.
