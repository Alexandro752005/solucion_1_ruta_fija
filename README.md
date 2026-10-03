# Ruta Fija

[![CI nativa](https://github.com/Alexandro752005/solucion_1_ruta_fija/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Alexandro752005/solucion_1_ruta_fija/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-blue)](backend/pom.xml)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)](docs/Uso%20del%20Sistema.md)

CRM web administrativo, API operativa y cliente Android para la gestión de Ruta Fija. Este README describe **el código publicado en `main`**: el CRM y la API están implementados; Flutter incluye sesión, perfil, disponibilidad y asignaciones propias (M1–M4). Funciones posteriores que aún estén en desarrollo local no se consideran publicadas ni aprobadas por este documento. La demo pública y la puerta G5 siguen pendientes de validación funcional.

## Arquitectura

```text
Angular CRM (ADMIN / SUPER_ADMIN) ─┐
                                   ├─ HTTPS/REST + WebSocket ─ Spring Boot API
Flutter Android (CONDUCTOR) ────────┘                          │
                                                                ├─ Flyway: migraciones
                                                                └─ PostgreSQL 16: datos y auditoría
```

El backend es un monolito modular Java 21/Spring Boot 3.5: `identity`, `organization`, `fleet`, `operation`, `audit` y `shared`. Controladores exponen HTTP, servicios aplican reglas y transacciones, repositorios persisten, y Flyway modifica el esquema. Hibernate solo valida el esquema. El CRM Angular 22 consume `/api/v1`; Flutter usa `/api/v1/mobile` sin conectarse a PostgreSQL. JWT, permisos por rol y organización, auditoría y protección de idempotencia son responsabilidades del backend.

| Carpeta | Contenido |
| --- | --- |
| `backend/` | API, reglas de negocio, Flyway V1–V10 y pruebas Java |
| `frontend/` | CRM Angular, pruebas Vitest y proxy de desarrollo |
| `mobile/` | Cliente Flutter Android del conductor |
| `scripts/` | Aprovisionamiento, migraciones, seguridad y pruebas nativas |
| `docs/` | Manual de uso, contratos, decisiones y evidencias por fase |
| `.github/` | CI, Dependabot y propietarios de código |

## Requisitos

- Windows 10/11, PowerShell y Git para los scripts de operación local.
- Java 21; Node.js 24.16.x y npm 11; PostgreSQL 16 con `psql` en el puerto local 5432.
- Flutter 3.47.5 y Android SDK solo para ejecutar o compilar `mobile/`.
- Acceso administrativo **local** a PostgreSQL para el aprovisionamiento inicial. No reutilice credenciales ajenas ni publique el puerto 5432.

La ejecución diaria es nativa. La base de desarrollo esperada por los scripts se llama `solucion_ruta_fija_1`; las pruebas usan exclusivamente `ruta_fija_test`. El nombre visible de un servidor en pgAdmin no sustituye al host JDBC `127.0.0.1`.

## Instalación inicial con PostgreSQL

Estos comandos se ejecutan desde PowerShell en la raíz de una copia nueva del repositorio. Lea primero el [manual de uso](docs/Uso%20del%20Sistema.md): los pasos de bootstrap solo son válidos para una base de desarrollo **vacía**.

```powershell
git clone https://github.com/Alexandro752005/solucion_1_ruta_fija.git
Set-Location .\solucion_1_ruta_fija
```

1. Instale PostgreSQL 16 y cree una base vacía llamada `solucion_ruta_fija_1` en `127.0.0.1:5432`. Mantenga el servicio local y cierre el acceso de red externa.
2. Prepare los archivos privados y las cuentas técnicas separadas. Los scripts piden los datos de bootstrap de forma interactiva; nunca los añada a Git.

   ```powershell
   .\scripts\Initialize-RutaFijaNativeConfig.ps1
   .\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
   ```

3. **Solo en una base nueva y vacía**, aplique las migraciones y privilegios de aplicación; valide el contrato resultante.

   ```powershell
   .\scripts\Invoke-RutaFijaFlywayF13.ps1
   .\scripts\Grant-RutaFijaApplicationPrivilegesF13.ps1
   .\scripts\Test-RutaFijaF34ContractReports.ps1
   ```

4. Instale las dependencias web bloqueadas por `package-lock.json`.

   ```powershell
   Set-Location frontend
   npm.cmd ci
   Set-Location ..
   ```

Los secretos quedan en `backend/.local/`, que Git ignora. Use [la plantilla sin credenciales](backend/ruta-fija-native.env.example) solo como referencia. Una base existente se actualiza mediante el procedimiento de migración correspondiente; nunca se reinicializa ni se restaura sin respaldo y revisión.

## Ejecutar y detener

La ruta recomendada inicia backend y frontend, verifica salud y registra únicamente los procesos propios:

```powershell
.\iniciar_ruta_fija.bat
```

| Servicio | URL local |
| --- | --- |
| CRM | http://localhost:4200 |
| API | http://127.0.0.1:8080/api/v1 |
| Salud | http://127.0.0.1:8080/actuator/health |
| OpenAPI | http://127.0.0.1:8080/v3/api-docs |

```powershell
.\finalizar_ruta_fija.bat
```

El cierre detiene los procesos de Ruta Fija, pero no elimina datos ni detiene el servicio PostgreSQL. El inicio normal no crea cuentas de demostración: solicite una cuenta autorizada al responsable de la instancia.

Para desarrollar cada proceso por separado, abra **dos terminales** tras completar el bootstrap. En la primera:

```powershell
.\scripts\Import-RutaFijaNativeEnvironment.ps1 -Scope Runtime
Set-Location backend
.\mvnw.cmd spring-boot:run
```

En la segunda, desde la raíz:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd start -- --host localhost --port 4200
```

La importación inyecta secretos solo en el proceso de PowerShell actual. No imprima su contenido, no suba `.env` ni incluya contraseñas en capturas.

## Qué funciona sin base de datos

El frontend se puede instalar, revisar, probar y compilar sin PostgreSQL ni API:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd run test:ci
npm.cmd run build
```

También se puede abrir su interfaz con `npm.cmd start`, pero el inicio de sesión, las listas, reportes, WebSocket y las acciones reales **no funcionarán** sin API y PostgreSQL. El backend no tiene un modo H2 ni una persistencia en memoria equivalente. Las pruebas de integración Java requieren `ruta_fija_test` aislada; no apunte pruebas a la base de desarrollo.

## Validación y cliente móvil

```powershell
.\verificar_ruta_fija.bat
```

La verificación nativa ejecuta la integración contra `ruta_fija_test`. Para el cliente Android:

```powershell
Set-Location mobile
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

El APK debug no es una publicación productiva. Para usar un dispositivo físico con una API local, consulte [la guía móvil](mobile/README.md) y configure el acceso de red o `adb reverse` de forma explícita.

## Entrega y despliegue

1. Cree una rama corta y un PR; exija las comprobaciones de CI y revisión humana antes de integrar en `main`.
2. Revise cambios de dependencias por ecosistema. Los parches y menores se agrupan; Angular, Spring Boot y otras versiones mayores se migran en PR separados con pruebas de regresión.
3. Antes de publicar, haga un respaldo recuperable de PostgreSQL. Aplique migraciones Flyway con la cuenta `rf_migrator`, nunca con la cuenta de aplicación ni con un superusuario en el runtime.
4. Compile JAR, frontend y APK de la revisión aprobada; entregue secretos por variables/almacenamiento privado y compruebe salud, login, flujo ADMIN–CONDUCTOR–CRM, reportes y auditoría.
5. Si falla la validación, revierta la versión de aplicación y use el procedimiento de recuperación de datos **ensayado**; no revierta SQL de Flyway a mano sobre una base con datos.

El repositorio no define aún un despliegue productivo permanente. Un túnel temporal puede servir para una demostración supervisada, con PostgreSQL privado, pero no sustituye TLS, copias, monitoreo ni una revisión de seguridad de producción. La demo F5.2 y la aprobación G5 no se declaran concluidas aquí.

## Colaborar y obtener ayuda

Lea [CONTRIBUTING.md](CONTRIBUTING.md) antes de abrir un PR y [la guía de mantenimiento](docs/mantenimiento-a1.md) para triage de Dependabot, CI y ramas. Para instalación y operación detalladas use [Uso del Sistema](docs/Uso%20del%20Sistema.md). No publique secretos, datos personales ni volcados de base de datos en issues o PR.
