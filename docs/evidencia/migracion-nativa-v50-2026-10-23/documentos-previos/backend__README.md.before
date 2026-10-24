# Backend — instrucciones vigentes 2026-10-23

Este corte sustituye las secciones históricas de ejecución y pruebas conservadas debajo. Java21/Spring Boot3.5.16 y PostgreSQL16 se mantienen. La base normal `solucion_ruta_fija_1` sigue en V12 y el código actual exige V50: el launcher bloquea antes de arrancar Java, sin migraciones automáticas. La fixture `ruta_fija_test` contiene V1 a V50 más una repetible: 51 éxitos, no V0.

Pruebas unitarias seguras desde la raíz:

```powershell
./scripts/Test-RutaFijaBackendUnitSafe.ps1
./verificar_ruta_fija.bat
```

El primer comando es Plan; el BAT ejecuta el runner con `-Execute`. El runner limita las clases a una allowlist inspeccionada, compila fuentes reales y tests en salidas aisladas, conserva JaCoCo y evita SpringBootTest/JPA/DriverManager/infraestructura real. Resultado observado del runner: 50 clases y 257 pruebas, sin fallos/errores/omitidas. No presenta ese resultado como integración completa. Los comandos Maven generales del antecedente no son la entrada segura: Surefire puede incluir clases de integración y tocar la fixture.

`Invoke-RutaFijaNativeTest.ps1 -Action Clean` está bloqueado y Verify delega al runner seguro antes de importar secretos. Las pruebas con PostgreSQL requieren un arnés aislado expresamente autorizado, fuera de esta auditoría. No ejecutar limpieza global ni migrar las bases desde el IDE.

El smoke explícito `iniciar_ruta_fija.bat --smoke-read-only --test-database` conserva la configuración persistente y limita la conexión a lectura con tareas de mantenimiento desactivadas solo durante esa sesión. No permite operaciones de usuarios; el uso normal mantiene las reglas del producto. El cierre conserva PostgreSQL si no posee una lease válida o hay otros clientes/observabilidad incompleta.

[Manual vigente](<../docs/Uso del Sistema.md>) y [evidencia nueva](../docs/evidencia/auditoria-ide-2026-10-23/launchers/README.md). Los archivos/actas/manifiestos de los lotes previos no se reescriben; copia exacta de este README previo en la auditoría nueva.

<details>
<summary>README anterior conservado como antecedente técnico; comandos y conteos antiguos no vigentes.</summary>

# Ruta Fija — backend

API del CRM Web Administrativo y Reportes, construida como monolito modular con
Java 21 y Spring Boot 3.5.16. Usa PostgreSQL 16 nativo como única
persistencia. La ejecución diaria no depende de contenedores.

## Módulos

~~~text
pe.rutafija/
├── identity/      autenticación, usuarios, JWT, refresh y RBAC
├── organization/  organizaciones y frontera multiempresa
├── fleet/         grupos, conductores, vehículos y disponibilidad
├── operation/     asignaciones, incidencias, comunicados y tiempo real
├── audit/         eventos críticos de solo anexado
└── shared/        seguridad, errores, configuración y observabilidad
~~~

Los controladores exponen el contrato HTTP, los servicios contienen la lógica
transaccional y los repositorios realizan el acceso a datos. Hibernate solo
valida el esquema; Flyway es la única vía para modificarlo.

## Configuración local

La configuración privada vive exclusivamente en:

~~~text
backend/.local/ruta-fija-native.env
backend/.local/ruta-fija-bootstrap.env
~~~

Ambos archivos están ignorados por Git. El primero contiene las credenciales de
aplicación, migración y pruebas; el segundo solo se usa para tareas
administrativas de PostgreSQL. No copie esos archivos entre equipos ni registre
secretos en incidencias, commits o capturas.

Variables de ejecución:

| Variable | Uso |
| --- | --- |
| SPRING_DATASOURCE_URL | JDBC de desarrollo, siempre 127.0.0.1:5432/solucion_ruta_fija_1 |
| SPRING_DATASOURCE_USERNAME | rf_app, con DML selectivo y sin DDL |
| SPRING_FLYWAY_URL / USERNAME | Desarrollo con rf_migrator |
| JWT_SECRET_BASE64 | Secreto local de al menos 32 bytes |
| APP_SEED_ENABLED | false para el runtime normal |
| APP_CORS_ALLOWED_ORIGINS | http://localhost:4300,http://127.0.0.1:4300 |
| REFRESH_COOKIE_SECURE | false solo para HTTP local |

## Esquema y roles

Flyway V1–V6 crea 12 tablas de negocio, la extensión btree_gist, la auditoría
append-only y los constraints de solapamiento de asignaciones. La zona horaria
de base de datos es UTC y la codificación es UTF8.

V6 consolida los roles administrativos en `ADMIN`; los únicos roles de
aplicación son `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. `group_coordinator` se
mantiene solo como historia técnica y no participa en autorización.

| Cuenta | Responsabilidad |
| --- | --- |
| rf_migrator | DDL y Flyway controlado |
| rf_app | Operación de desarrollo con DML selectivo |
| rf_test | Pruebas aisladas sobre ruta_fija_test |

Ninguna de estas cuentas es superusuario ni puede crear bases, roles o
replicar. PostgreSQL debe escuchar únicamente en 127.0.0.1 y ::1.

## Ejecución

Desde la raíz del repositorio se usa:

~~~powershell
.\iniciar_ruta_fija.bat
~~~

El iniciador activa PostgreSQL 16 con inicio manual, empaqueta el JAR solo si
las fuentes cambiaron, levanta la API con memoria limitada en 127.0.0.1:8080 y
comprueba:

- Salud: http://127.0.0.1:8080/actuator/health
- OpenAPI: http://127.0.0.1:8080/v3/api-docs
- Swagger local: http://127.0.0.1:8080/swagger-ui.html

Para detener procesos administrados:

~~~powershell
.\finalizar_ruta_fija.bat
~~~

El cierre también detiene PostgreSQL 16 sin eliminar la base ni sus datos.

## Pruebas

Las pruebas unitarias:

~~~powershell
Set-Location backend
.\mvnw.cmd test
~~~

La verificación completa usa exclusivamente ruta_fija_test, con Flyway ejecutado
por rf_migrator y limpieza protegida antes y después de cada integración:

~~~powershell
Set-Location ..
.\verificar_ruta_fija.bat
~~~

La puerta aprobada termina con F2_2_NATIVE_VERIFY=PASS. No se permite que una
prueba apunte a desarrollo o a una base de recuperación.

## Seguridad HTTP

El access token JWT tiene vida corta. El refresh token es opaco, rotativo,
almacenado únicamente como hash en PostgreSQL y enviado en una cookie HttpOnly,
SameSite=Strict, limitada a la ruta de autenticación. El backend valida Origin
para solicitudes del navegador y limita intentos costosos de login.

Todos los errores usan el contrato normalizado con correlationId. Un cliente
puede suministrar X-Correlation-ID seguro; de lo contrario se genera uno.

## Referencias

- [Manual operativo](<../docs/Uso del Sistema.md>)
- [Ejecución nativa F1.4](../docs/ejecucion-nativa-f1-4.md)
- [Proxy y WebSocket F1.5](../docs/ejecucion-nativa-f1-5.md)
- [Consolidación backend ADMIN F2.2](../docs/ejecucion-nativa-f2-2.md)
- [Auditoría backend F2.2](../docs/auditoria-f2-2-backend-admin.md)
- [Contrato API ADMIN F2.3](../docs/contrato-api-f2-3-admin.md)


</details>
