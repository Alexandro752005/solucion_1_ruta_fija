# SC-L01-LOCAL-01 — Adenda 1.0.0 (antecedente aprobado)

Fecha de registro: 2026-10-17. Tipo autorizado: **FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**.

Esta adenda conserva la aprobación expresa registrada en `SC-L01-LOCAL-01-aprobacion-condicionada-2026-10-17.json` y la propuesta `L-01-propuesta-validacion-funcional-local.md`, sin reescribirlas. Su aplicación conjunta estaba condicionada a las evidencias de ambos ensayos.

| ID | Requisito original | Sustitución exclusivamente para cierre funcional local |
|---|---|---|
| SC-03 | Aceptación expresa propia de ADMIN legítimo y gate de términos. | Ensayo manual de ADMIN sintético con alta parametrizada, activación, contraseña, OTP simulado, gate previo, aceptación expresa persistida y habilitación. |
| SC-07 | Custodia independiente, inscripción y ensayo del titular real. | Ensayo manual con SUPER_ADMIN y clave temporales; copia cifrada leída y utilizada en la misma PC; protocolo Ed25519, recuperación, rotación y revocación comprobados. |

El ensayo CI03 conserva su aceptación manual pero omitió comprobar el gate antes de aceptarlos; ese faltante no se reconstruye. CI07 dispone de `PASS_LOCAL_TEMPORARY_COPY_ONLY`. La precisión posterior se recoge en la adenda 1.1.0. Esta versión no declara por sí sola cumplida la condición original de ambos ensayos completos.

Los requisitos originales continúan pendientes para el piloto. No se modifican términos publicados, ID-01, cupos históricos, seguridad ni otros criterios. No se autorizan piloto, cuentas reales, claves definitivas, SMTP adicional, migraciones de desarrollo/piloto, despliegue ni L-02.
