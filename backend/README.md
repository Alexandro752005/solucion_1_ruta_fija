# Ruta Fija — backend

Backend del CRM web administrativo y reportes, construido como monolito modular con Java 21 y Spring Boot 3.5.16. La Fase 4 integra `identity`, `organization`, `fleet`, `operation`, `audit` y `shared`, con PostgreSQL 16 como única persistencia.

## Requisitos

- Java 21 si se ejecuta fuera de contenedores.
- Docker Desktop y Docker Compose para el entorno local recomendado.
- No es necesario instalar Maven: el wrapper fija Maven 3.9.15.

## Configuración

No hay contraseñas ni claves privadas en el repositorio. Estas variables deben suministrarse desde el entorno o desde el archivo `.env` de Docker Compose ubicado en la raíz del proyecto.

| Variable | Obligatoria | Descripción |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | Sí | JDBC de PostgreSQL, por ejemplo `jdbc:postgresql://postgres:5432/ruta_fija` |
| `SPRING_DATASOURCE_USERNAME` | Sí | Usuario de PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `JWT_SECRET_BASE64` | Sí | Al menos 32 bytes aleatorios codificados en Base64 |
| `APP_CORS_ALLOWED_ORIGINS` | Producción | Orígenes exactos separados por coma; en desarrollo usa `http://localhost:4200` |
| `DEMO_USER_PASSWORD` | Para seed dev | Contraseña de al menos 12 caracteres, elegida localmente |
| `APP_SEED_ENABLED` | No | `true` solo en desarrollo; por defecto está activo bajo el perfil `dev` |
| `REFRESH_COOKIE_SECURE` | No | `false` en HTTP local; `true` por defecto con el perfil `prod` |
| `LOGIN_RATE_LIMIT_ENABLED` | No | Activa la protección local del login; `true` por defecto |
| `LOGIN_RATE_LIMIT_ACCOUNT_ATTEMPTS` | No | Intentos costosos permitidos por cuenta y ventana; `5` por defecto |
| `LOGIN_RATE_LIMIT_GLOBAL_ATTEMPTS` | No | Intentos costosos permitidos por instancia y ventana; `300` por defecto |

Ejemplo para generar una clave JWT en PowerShell, sin guardarla en el historial del repositorio:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
[Convert]::ToBase64String($jwtBytes)
```

## Ejecución recomendada

Desde la raíz del repositorio:

```text
docker compose --profile app up --build
```

El contenedor del backend usa los perfiles `dev,docker`, espera a PostgreSQL y ejecuta Flyway automáticamente. Sus puntos de diagnóstico son:

- Health: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Para ejecutar el backend directamente en Windows, configure las variables anteriores, inicie PostgreSQL y use:

```powershell
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Usuarios de desarrollo

El inicializador `dev` es idempotente y solo crea datos cuando `DEMO_USER_PASSWORD` está configurada. Si falta, el backend inicia normalmente y omite el seed. La contraseña no se registra en logs.

| Organización | Usuario | Rol |
|---|---|---|
| Global | `superadmin@rutafija.local` | `SUPER_ADMIN` |
| Ruta Norte | `admin.norte@rutafija.local` | `ADMINISTRADOR` |
| Ruta Norte | `coordinador.norte@rutafija.local` | `COORDINADOR` |
| Ruta Sur | `admin.sur@rutafija.local` | `ADMINISTRADOR` |
| Ruta Sur | `coordinador.sur@rutafija.local` | `COORDINADOR` |

Estos datos son exclusivamente locales y no se crean con el perfil `prod`.

## API de sesión

Base URL: `/api/v1`.

| Método | Endpoint | Autenticación |
|---|---|---|
| `POST` | `/auth/login` | Pública |
| `POST` | `/auth/refresh` | Cookie de refresh válida |
| `POST` | `/auth/logout` | Cookie de refresh; idempotente y no requiere un access token vigente |
| `GET` | `/auth/me` | Bearer access token |

El access token JWT dura 15 minutos por defecto. El refresh token es opaco, rotativo y solo se entrega en una cookie `HttpOnly`, `SameSite=Strict`, limitada a `/api/v1/auth`. En PostgreSQL se conserva exclusivamente su hash SHA-256. La reutilización de un token ya rotado revoca toda su familia.

Esta cookie es una decisión de seguridad deliberada respecto del ejemplo inicial del contrato, que mostraba el refresh token dentro del JSON. El frontend no debe almacenarlo en `localStorage`, `sessionStorage` ni memoria JavaScript; debe enviar solicitudes de login, refresh y logout con credenciales habilitadas.

Login, refresh y logout validan el encabezado `Origin` contra la lista CORS
cuando la petición proviene de un navegador. Las herramientas CLI pueden operar
sin ese encabezado. El login aplica cuotas acotadas por cuenta anonimizada y por
instancia antes de ejecutar BCrypt; una respuesta `429` incluye `Retry-After`.

Todas las respuestas de error siguen el contrato normalizado e incluyen `correlationId`. El cliente puede enviar `X-Correlation-ID` con un valor seguro de hasta 64 caracteres; de lo contrario, el backend genera uno.

## Aislamiento y autorización

- El JWT firmado contiene el identificador de usuario, rol y, salvo para `SUPER_ADMIN`, la organización.
- La organización ordinaria se deriva únicamente de la autenticación; no se acepta desde headers ni DTO del cliente.
- Las consultas tenant-aware reciben siempre `organization_id` desde `CurrentUserProvider`.
- Un `SUPER_ADMIN` global no puede adoptar implícitamente una organización. Los futuros endpoints que lo requieran deberán implementar selección explícita y autorizada.
- Los roles del MVP son `SUPER_ADMIN`, `ADMINISTRADOR`, `COORDINADOR` y `CONDUCTOR`.

## Base de datos y migraciones

Flyway crea el esquema desde una base PostgreSQL 16 vacía. La primera migración contiene:

- `organization`
- `app_user`
- `refresh_token`, con familia, rotación y hashes
- `audit_event`, protegida como tabla append-only mediante trigger
- restricciones de roles, organización, índices y claves foráneas

Hibernate usa `ddl-auto=validate`: el modelo Java nunca modifica el esquema. Todo cambio posterior debe incorporarse como una nueva migración versionada; una migración ya aplicada no se edita.

## Pruebas

Pruebas unitarias, siempre disponibles:

```powershell
./mvnw.cmd test
```

Verificación completa, incluida integración sobre PostgreSQL efímero mediante Testcontainers:

```powershell
./mvnw.cmd verify
```

`test` no selecciona clases `*IT`, por lo que sigue siendo útil sin Docker.
`verify`, en cambio, exige Docker y falla si PostgreSQL/Testcontainers no puede
iniciarse; nunca convierte una integración omitida en una puerta verde. Con
Docker activo valida Flyway, login, cookie HttpOnly, `/me`, rotación, detección
de reutilización, logout, origen confiable, contrato HTTP y aislamiento entre
organizaciones.

Los reportes JaCoCo se generan en `target/site/jacoco/` durante `verify`.

## Perfiles

- `dev`: habilita el seed condicionado y logs de aplicación.
- `test`: deshabilita seed y Swagger UI; las pruebas suministran una base efímera.
- `prod`: deshabilita seed y OpenAPI público, exige CORS explícito y usa cookie segura.
- `docker`: ajustes de ejecución dentro del contenedor; normalmente se combina con `dev` o `prod`.

## Estructura modular

```text
pe.rutafija/
├── identity/      autenticación, JWT, refresh, usuarios y RBAC
├── organization/  organización y frontera tenant
├── audit/         eventos críticos append-only
└── shared/        seguridad, errores, configuración y observabilidad
```

Los controladores se limitan al contrato HTTP; la lógica transaccional permanece en servicios de aplicación y el acceso a datos en repositorios de infraestructura.
