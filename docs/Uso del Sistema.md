# Uso del Sistema

Guía de instalación, puesta en marcha y operación del **CRM Web Administrativo
y Reportes — Ruta Fija**. Está escrita para que una persona nueva pueda clonar
el proyecto y ejecutarlo desde otro equipo sin instalar PostgreSQL, pgAdmin ni
una aplicación móvil.

## 1. Qué incluye la solución

El sistema es un CRM web para administrar organizaciones, usuarios, grupos,
conductores, vehículos, asignaciones, incidencias, comunicados, reportes y
auditoría. Usa:

| Componente | Tecnología |
| --- | --- |
| CRM | Angular 22 servido por Nginx |
| API | Java 21, Spring Boot 3.5 |
| Base de datos | PostgreSQL 16 dentro de Docker Desktop |
| Migraciones | Flyway |
| Contenedores | Docker Compose |

El alcance no incluye aplicación móvil, GPS, mapas, aceptación/rechazo del
conductor, FCM, SMTP, S3 ni una base de datos externa. Las asignaciones se
crean de forma administrativa como `SCHEDULED`, sin simular una respuesta de
conductor.

## 2. Requisitos del equipo nuevo

Instale y compruebe lo siguiente antes de clonar el proyecto:

| Requisito | Uso |
| --- | --- |
| Git | Descargar actualizaciones desde GitHub. |
| Docker Desktop | Ejecutar PostgreSQL, API y CRM. Debe estar iniciado. |
| Docker Compose v2 | Incluido normalmente con Docker Desktop. |
| Windows 10/11 + PowerShell | Recomendado para los scripts `.bat`. |
| Visual Studio Code + extensión Docker | Recomendado, no obligatorio. |

Java, Maven, Node.js y npm no son necesarios para ejecutar la solución con
Docker. Solo se requieren para desarrollar o ejecutar las pruebas localmente.

En PowerShell, valide Docker:

```powershell
docker version
docker compose version
```

Si Docker Desktop solicita actualizar WSL, ejecute PowerShell como administrador:

```powershell
wsl --update --web-download
```

Reinicie el equipo si Windows lo solicita y abra Docker Desktop antes de
continuar.

## 3. Clonar el proyecto desde GitHub

Clone el repositorio público desde GitHub:

```powershell
git clone https://github.com/Alexandro752005/solucion_1_ruta_fija.git
Set-Location solucion_1_ruta_fija
```

No copie el archivo `.env` de otra persona por correo, chat o Git. Cada equipo
debe crear sus propios secretos locales en el siguiente paso.

## 4. Configurar el archivo `.env`

Desde la raíz clonada, cree la configuración local:

```powershell
Copy-Item .env.example .env
```

Abra `.env` en VS Code y complete como mínimo estas variables:

| Variable | Qué colocar |
| --- | --- |
| `POSTGRES_PASSWORD` | Contraseña local exclusiva para PostgreSQL. |
| `JWT_SECRET_BASE64` | Secreto aleatorio Base64 de al menos 32 bytes. |
| `DEMO_USER_PASSWORD` | Contraseña de al menos 12 caracteres para las cuentas de demostración. |
| `POSTGRES_PORT` | `5432`, salvo que otro servicio ya ocupe ese puerto. |
| `BACKEND_PORT` | `8080`, salvo conflicto. |
| `FRONTEND_PORT` | `4200`, salvo conflicto. |
| `APP_CORS_ALLOWED_ORIGINS` | La URL exacta del CRM; por defecto `http://localhost:4200`. |

Para generar `JWT_SECRET_BASE64`, ejecute una sola vez:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
[Convert]::ToBase64String($jwtBytes)
```

Copie el resultado en `JWT_SECRET_BASE64=`. No publique, capture ni comparta
el contenido de `.env`. El archivo está excluido por `.gitignore`.

Si cambia `FRONTEND_PORT`, actualice también
`APP_CORS_ALLOWED_ORIGINS=http://localhost:<PUERTO_FRONTEND>`.

## 5. Iniciar por primera vez

1. Abra Docker Desktop y espere que su motor esté activo.
2. Desde el Explorador de Windows o desde una consola en la raíz del proyecto,
   ejecute:

   ```powershell
   .\iniciar_ruta_fija.bat
   ```

3. El script valida Docker, Compose y `.env`; después construye y espera los
   tres servicios. La primera construcción descarga dependencias y puede tomar
   varios minutos. Las siguientes son normalmente más rápidas.
4. Cuando se muestre `Entorno disponible`, abra:

   | Recurso | Dirección |
   | --- | --- |
   | CRM | [http://localhost:4200](http://localhost:4200) |
   | API | [http://localhost:8080/api/v1](http://localhost:8080/api/v1) |
   | Salud | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
   | OpenAPI | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |

La salud correcta devuelve `{"status":"UP"}`. Para comprobar los
contenedores en cualquier momento:

```powershell
docker compose --profile app ps
```

Los tres servicios deben aparecer como `healthy`: `postgres`, `backend` y
`frontend`.

## 6. Iniciar sesión y roles

En el primer arranque con una base nueva, el perfil local crea cuentas de
demostración. Todas usan la contraseña que se escribió en
`DEMO_USER_PASSWORD` **al crear esa base**.

| Usuario | Rol | Uso principal |
| --- | --- | --- |
| `superadmin@rutafija.local` | `SUPER_ADMIN` | Gestión de organizaciones. |
| `admin.norte@rutafija.local` | `ADMINISTRADOR` | Administración, reportes y auditoría de Ruta Norte. |
| `coordinador.norte@rutafija.local` | `COORDINADOR` | Operación de los grupos visibles de Ruta Norte. |
| `admin.sur@rutafija.local` | `ADMINISTRADOR` | Administración, reportes y auditoría de Ruta Sur. |
| `coordinador.sur@rutafija.local` | `COORDINADOR` | Operación de los grupos visibles de Ruta Sur. |

Use inicialmente `admin.norte@rutafija.local` para conocer todas las pantallas
administrativas. Si se modifica `DEMO_USER_PASSWORD` después de que las cuentas
ya existan, sus contraseñas no se cambian automáticamente: no borre datos para
resolverlo salvo que la base sea solo de demostración.

### Navegación de la interfaz

La cabecera posee dos franjas: identidad y sesión en la parte superior, y una
topbar de módulos debajo. La línea amarilla indica el módulo activo. En
pantallas estrechas, el botón **Menú** abre la misma navegación en la parte
superior; no existe un menú lateral.

| Rol | Navegación disponible |
| --- | --- |
| ADMINISTRADOR | Resumen, Usuarios, Organización, Grupos, Conductores, Vehículos, Asignaciones, Incidencias, Comunicados, Reportes y Auditoría. |
| COORDINADOR | Resumen, Grupos, Conductores, Vehículos, Asignaciones, Incidencias y Comunicados. |
| SUPER_ADMIN | Resumen y Organizaciones. |

Las vistas comienzan con filtros y tabla. **Nuevo**, **Editar** o **Ver detalle**
abren ventanas dentro del sistema. Puede cerrarlas con **Cancelar**, el botón
de cierre o la tecla `Escape`. Las acciones de riesgo piden confirmación y los
datos ingresados se conservan si el servidor devuelve un error de validación.

## 7. Recorrido funcional recomendado

### 7.1 Administración inicial

1. Ingrese como administrador.
2. Revise **Usuarios**, **Grupos**, **Conductores** y **Vehículos**.
3. Cree o edite recursos dentro de la organización correspondiente.
4. Asigne coordinadores a los grupos que podrán operar.

El aislamiento entre organizaciones se aplica en el servidor: un usuario de
una organización no puede consultar ni modificar filas de otra, incluso si
altera la URL del navegador.

### 7.2 Estados operativos

Respete estas reglas durante la operación:

| Recurso | Estados y regla |
| --- | --- |
| Conductor | `DISPONIBLE → RESERVADO → EN_SERVICIO → DISPONIBLE`; cambios administrativos controlados a `DESCANSO` y `NO_DISPONIBLE`. |
| Vehículo | `DISPONIBLE`, `EN_SERVICIO`, `MANTENIMIENTO`, `INACTIVO`. Una reserva futura no lo cambia a `RESERVADO`. |
| Asignación | Nace `SCHEDULED`; se puede reservar, iniciar, completar o cancelar. |

Una reserva es una acción del coordinador, no una aceptación del conductor. El
sistema bloquea solapamientos de conductor y vehículo tanto en la aplicación
como en PostgreSQL.

### 7.3 Operación diaria

1. En **Asignaciones**, cree el servicio con conductor, vehículo, origen,
   destino y horario.
2. Reserve cuando corresponda; el conductor pasa a `RESERVADO`.
3. Inicie el servicio; conductor y vehículo pasan a `EN_SERVICIO`.
4. Complete o cancele con motivo. El sistema registra auditoría y actualiza
   los estados permitidos.
5. En **Incidencias**, registre, haga seguimiento y resuelva eventos reales
   reportados desde el CRM.
6. En **Comunicados**, publique avisos a la organización o a un grupo visible.

### 7.4 Reportes y auditoría

Solo el rol `ADMINISTRADOR` puede ver **Reportes** y **Auditoría**.

- Reportes muestra disponibilidad actual, asignaciones e incidencias desde
  filas persistidas de PostgreSQL.
- El rango máximo es 90 días y 10 000 registros por consulta.
- Las descargas PDF y Excel (`.xlsx`) se generan en el backend; CSV neutraliza
  fórmulas potencialmente peligrosas.
- Auditoría es solo de lectura: permite filtrar, paginar y consultar detalles
  sin crear, editar ni eliminar eventos.

## 8. Detener, reiniciar y actualizar

Para detener sin perder la base local:

```powershell
.\finalizar_ruta_fija.bat
```

El script usa `docker compose down` sin `--volumes`; por ello conserva
`postgres_data`. Para volver a iniciar, ejecute `iniciar_ruta_fija.bat`.

Al recibir cambios del repositorio:

```powershell
git pull
.\iniciar_ruta_fija.bat
```

Flyway aplica automáticamente migraciones nuevas y versionadas. Antes de
actualizar una instalación con datos importantes, respalde la información con
el procedimiento aprobado por su equipo; el proyecto no configura por sí solo
una política de copias, retención o restauración de producción.

### Restablecer una demostración desde cero

Solo si no necesita los datos locales, ejecute:

```powershell
docker compose --profile app down --volumes
.\iniciar_ruta_fija.bat
```

> Advertencia: `--volumes` elimina de forma irreversible la base local. No lo
> use para resolver un problema de contraseña en un entorno que tenga datos que
> deban conservarse.

## 9. Verificación y desarrollo opcional

Con Java 21, Node.js 24 y Docker Desktop disponibles, ejecute las puertas de
calidad desde las terminales integradas:

```powershell
Set-Location backend
.\mvnw.cmd clean verify
```

```powershell
Set-Location ..\frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd run test:ci
npm.cmd run build
npm.cmd audit --audit-level=high
```

La validación backend utiliza PostgreSQL 16 efímero mediante Testcontainers;
por eso Docker debe estar activo incluso para `verify`.

## 10. Solución de problemas

| Síntoma | Causa probable y acción |
| --- | --- |
| El `.bat` indica que Docker no responde | Abra Docker Desktop, espere su estado activo y ejecute de nuevo el script. |
| Falta `.env` | Ejecute `Copy-Item .env.example .env`, complete los tres secretos requeridos y vuelva a iniciar. |
| Puerto ocupado | Cambie `POSTGRES_PORT`, `BACKEND_PORT` o `FRONTEND_PORT` en `.env`; si cambia el frontend, ajuste también CORS. |
| El primer inicio tarda | Es normal durante descarga/compilación. El iniciador espera hasta 360 s por el backend. Revise logs si excede ese tiempo. |
| No se puede iniciar sesión | Verifique el usuario y la contraseña que se usó en `DEMO_USER_PASSWORD` al crear la base. Si la base se preservó, cambiar ahora `.env` no cambia la cuenta existente. |
| El CRM no abre, pero API está sana | Ejecute `docker compose --profile app ps` y revise `frontend`; luego use los logs indicados abajo. |
| Una asignación es rechazada | Compruebe estados de conductor/vehículo, horarios y solapamientos existentes. |

Para revisar los últimos registros sin modificar datos:

```powershell
docker compose --profile app logs --tail 200 postgres backend frontend
```

## 11. Estructura y documentación de referencia

| Ruta | Contenido |
| --- | --- |
| `backend/` | API Spring Boot, migraciones Flyway y pruebas. |
| `frontend/` | CRM Angular y pruebas de interfaz. |
| `compose.yaml` | Definición de contenedores locales. |
| `.env.example` | Plantilla segura de configuración local. |
| `iniciar_ruta_fija.bat` | Inicio validado para Windows. |
| `finalizar_ruta_fija.bat` | Cierre preservando la base local. |
| `docs/auditoria-final-fase-4.md` | Dictamen y observaciones de cierre. |
| `docs/evidencia-fase-4-2026-08-30.md` | Pruebas y evidencia ejecutada. |
| `docs/fase-5-estado.md` | Alcance y resultado del rediseño integral. |
| `docs/auditoria-final-fase-5.md` | Dictamen técnico y riesgos residuales de Fase 5. |
| `docs/evidencia-fase-5-2026-08-30.md` | Evidencia de pruebas y ejecución de Fase 5. |

Consulte además el [README principal](../README.md), la
[matriz de trazabilidad](matriz-trazabilidad-final.md) y el
[reporte de cumplimiento Java](../REPORTE_CUMPLIMIENTO_JAVA.md).
