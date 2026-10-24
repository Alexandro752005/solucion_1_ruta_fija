# L-06 — Precisiones operativas propuestas

Versión0.1.0,2026-10-20. **PROPUESTA, SIN APROBACIÓN**. No cambia Q-067/SRV-INT-L05-05, la prohibición de reapertura, los roles ni las condiciones del piloto.

## INC-L06-01 — Recurrencia o cierre erróneo

Q-070 prohíbe reabrir; OPEN-CLOSED-INCIDENT-PROCEDURE deja pendiente el caso posterior. Se propone que ADMIN cree una incidencia nueva de la misma organización, vinculada de forma inmutable a la incidencia cerrada, con motivo `RECURRENCE` o `CLOSURE_ERROR` y explicación. La incidencia original y su cierre no se modifican. La nueva recibe responsable, investigación y cierre propios; nunca libera atención de seguridad, documentos o mantenimiento por herencia. El conductor puede reportar de nuevo y referenciar una incidencia propia; ADMIN verifica el vínculo al atenderla. No se permiten vínculos a otra empresa, ciclos ni alterar retrospectivamente el cierre original.

## INC-L06-02 — Gravedad y efecto separados

Q-066 exige ambos por separado, pero no enumera los niveles. Se propone gravedad `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, como evaluación explícita del reportante/ADMIN, sin deducirla automáticamente del tipo. ADMIN puede rectificarla con motivo e historial. No es una certificación de riesgo físico.

El efecto operativo se obtiene de hechos, no de la gravedad: `NO_INTERRUPTION_RECORDED` si no hay interrupción vinculada, o `INTERRUPTION_RECORDED` cuando existe el hecho de seguridad de L-05. El primer valor no declara que sea seguro continuar. Una incidencia crítica no completa ni inicia un servicio automáticamente. Para accidente con riesgo, avería insegura o imposibilidad real se utiliza la acción expresa de interrupción ya aprobada, con su detención/autoría/atención; retraso o GPS no corroborado no la provocan por sí solos.

Alternativa: proporcionar otra lista concreta de niveles, conservando el efecto derivado de hechos y todas las reglas de seguridad anteriores. No se asigna retrospectivamente una gravedad conocida a registros legados sin evaluación: figurarán `UNASSESSED` hasta revisión autorizada.

## Avisos y canal externo

Q-074 ya exige bandeja para todos los avisos y condiciona el intento push a comprobar una vía sin gasto y sus límites. La implementación deberá distinguir entrega persistida en bandeja, lectura y resultado externo. La ausencia de proveedor/dispositivo registrado no acredita push; debe ser visible para ADMIN. No se enviará correo operativo, no se reutilizará la autorización SMTP/OTP para ello ni se activará un proveedor externo por inferencia. La investigación del canal no equivale a disponibilidad real de credenciales/dispositivos ni a ensayo físico L-08.
