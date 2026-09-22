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
| APP_CORS_ALLOWED_ORIGINS | http://localhost:4200 |
| REFRESH_COOKIE_SECURE | false solo para HTTP local |

## Esquema y roles

Flyway V1–V5 crea 12 tablas de negocio, la extensión btree_gist, la auditoría
append-only y los constraints de solapamiento de asignaciones. La zona horaria
de base de datos es UTC y la codificación es UTF8.

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

El iniciador empaqueta el JAR, levanta la API en 127.0.0.1:8080 y comprueba:

- Salud: http://127.0.0.1:8080/actuator/health
- OpenAPI: http://127.0.0.1:8080/v3/api-docs
- Swagger local: http://127.0.0.1:8080/swagger-ui.html

Para detener procesos administrados:

~~~powershell
.\finalizar_ruta_fija.bat
~~~

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

La puerta aprobada termina con F1_4_NATIVE_VERIFY=PASS. No se permite que una
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
