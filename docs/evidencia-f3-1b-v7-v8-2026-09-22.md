# Evidencia de F3.1B — V7/V8

- Fecha: 2026-09-22.
- Motor: PostgreSQL 16 local, `127.0.0.1:5432`.
- Desarrollo: `solucion_ruta_fija_1`.
- Alcance: persistencia y verificación del contrato móvil; sin API móvil,
  Flutter, Docker ni exposición de PostgreSQL.

## Ensayo protegido

Con API y CRM detenidos se creó el respaldo pre-V7/V8:

~~~text
backups/f3-1b/solucion_ruta_fija_1_20260922T154740Z_f31b_v6.dump
~~~

Se restauró y verificó la base independiente
`ruta_fija_recovery_20260922_f31b`. El manifiesto privado registra la huella del
origen, los hashes SHA-256 de V7/V8 y el resultado:

~~~text
F3_1B_REHEARSAL_FLYWAY=PASS migrations=V7,V8 target=isolated-recovery
F3_1B_REHEARSAL=PASS recovery=ruta_fija_recovery_20260922_f31b backup=backups/f3-1b/solucion_ruta_fija_1_20260922T154740Z_f31b_v6.dump manifest=backups/f3-1b/solucion_ruta_fija_1_20260922T154740Z_f31b-rehearsal.json flyway=V7,V8 docker=0
~~~

El ensayo comprobó los solapamientos de `PENDING_RESPONSE`, la prohibición de
una aceptación ficticia y el UPSERT de una única ubicación vigente.

## Aplicación protegida y permisos

Las copias activas de V7/V8 coincidieron byte a byte con las candidatas
ensayadas. Flyway las aplicó sobre desarrollo y `rf_app` recibió solo DML en la
nueva tabla:

~~~text
F3_1B_FLYWAY=PASS migrations=V7,V8 target=development
F3_1B_LOCATION_GRANTS=PASS rf_app=DML_current_location_without_DDL
~~~

La primera pasada del auditor identificó que `ruta_fija_test` tenía una
migración repetible exitosa de privilegios sin versión. Se corrigió el auditor
para reconocer explícitamente ese estado seguro; no se reintentó ni modificó
la migración de desarrollo.

## Validaciones finales

~~~text
F3_1B_SCHEMA_AUDIT=PASS flyway=V1-V8 response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only privileges=DML_without_DDL mobile_endpoints=0 docker=0
F3_1B_NATIVE_VERIFY=PASS flyway=V1-V8 docker=0
F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native
F1_1B_RUNTIME=PASS backend=UP frontend=UP ports=8080,4200
F1_1B_STOP=PASS backend=stopped frontend=stopped
~~~

La verificación nativa migró `ruta_fija_test` de V6 a V8 y aprobó 23 pruebas
unitarias y 14 de integración. El runtime validó API y CRM con el nuevo
esquema; ambos procesos se detuvieron después de la comprobación.

## Estado de salida

Desarrollo y pruebas están en Flyway V1–V8. Las asignaciones futuras quedan
preparadas para distinguir creación directa de respuesta móvil, y la ubicación
se conserva solo como estado vigente por conductor. F3.2/F3.3 siguen siendo
necesarias antes de exponer una sesión o comando móvil.
