# Evidencia F3.1A - ADR de contrato móvil y estados

- Fecha: 2026-09-22.
- Fase: F3.1A, día 5 de la Etapa 2.
- Alcance validado: decisiones de contrato. No se aplicaron V7/V8 ni se
  publicaron endpoints móviles.

## Cambios entregados

| Artefacto | Evidencia |
| --- | --- |
| Decisión arquitectónica | `docs/decisiones/ADR-004-contrato-movil-estados-y-ubicacion.md` fija modos, estados, privacidad y límites. |
| Guía de ejecución | `docs/ejecucion-f3-1a-adr-contrato-movil.md` declara entrada, alcance, salida y transferencia. |
| Control reproducible | `scripts/Test-RutaFijaF31aMobileContract.ps1` valida el contrato y la preimplementación. |
| Acceso desde VS Code | Tarea `Ruta Fija: validar ADR movil y estados (F3.1A)`. |

## Resultados ejecutados

Se ejecutó con PowerShell desde la raíz, sin iniciar Docker:

~~~powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-RutaFijaF31aMobileContract.ps1 -RequirePreImplementation
~~~

```text
F3_1A_CONTRACT=PASS phase=pre_implementation response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only consent=required vehicle_reserved=0 docker=0
```

También se comprobó el esquema nativo con API y CRM detenidos:

~~~powershell
.\scripts\Test-RutaFijaMigratedSchemaF13.ps1
~~~

```text
F2_2_SCHEMA_AUDIT=PASS
flyway=V1-V6 roles=SUPER_ADMIN,ADMIN,CONDUCTOR btree_gist=activo audit=append-only exclusion_constraints=activas
rf_app=DML_selectivo_sin_DDL historia_Flyway=protegida
F2.2 no inicio Spring Boot, API, CRM ni Docker.
```

## Conclusión de la subfase

La base continúa en V1-V6 y el contrato queda congelado antes de la primera
migración móvil. El siguiente trabajo autorizado, solo después de verde del
usuario, es F3.1B: implementar y ensayar V7/V8 con actualización segura desde
la línea histórica y desde una base vacía.
