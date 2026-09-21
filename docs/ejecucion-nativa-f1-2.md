# F1.2 - Roles y bases PostgreSQL nativas aisladas

Esta subfase separa la cuenta administrativa local de las cuentas que usa Ruta
Fija. No inicia Spring Boot, Flyway, la API ni el CRM y no aplica V1-V5.

## Resultado esperado

| Recurso | Uso permitido |
| --- | --- |
| `rf_migrator` | Propietario de desarrollo y pruebas; DDL controlado por Flyway. |
| `rf_app` | Conecta solo a `solucion_ruta_fija_1`; no puede crear tablas. |
| `rf_test` | Conecta solo a `ruta_fija_test`; no puede crear tablas. |
| `soporte` | Bootstrap administrativo local; no lo carga Spring Boot. |

La base de recuperación `ruta_fija_recovery_20260919_f04` no se modifica.
Las dos bases de F1.2 se mantienen sin tablas de negocio hasta F1.3.

## Ejecución única o reanudable

Desde la raíz del repositorio:

```powershell
.\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
```

El script parte de la configuración privada creada en F1.1A, genera claves
locales distintas para los tres roles y las guarda solo en
`backend/.local/`, carpeta ignorada por Git. Si una interrupción ocurre luego
de preparar esa configuración, volver a ejecutar el mismo comando reanuda de
forma conservadora; se detiene si observa una creación parcial inesperada.

Después ejecute:

```powershell
.\iniciar_ruta_fija.bat
```

La validación comprueba PostgreSQL 16, UTF8, UTC, las conexiones correctas,
el aislamiento desarrollo/pruebas, la ausencia de privilegios públicos y que
`rf_app` y `rf_test` no puedan ejecutar DDL. También confirma que no quedaron
tablas probe.

Una salida aprobada incluye:

```text
F1_2_ROLE_ISOLATION=PASS
F1.2 no ejecuto Flyway, Spring Boot, API, CRM ni Docker.
```

## Límite de permisos

En F1.2 todavía no existen tablas de negocio. Por ello `rf_app` y `rf_test`
reciben conexión y uso de esquema, pero el otorgamiento DML se hará de forma
selectiva en F1.3, una vez que Flyway haya creado las tablas. Así se evita
otorgar por defecto escritura sobre `flyway_schema_history`.

No se deben editar a mano los archivos de `backend/.local/` ni ejecutar la
aplicación antes de la aprobación explícita de F1.3.
