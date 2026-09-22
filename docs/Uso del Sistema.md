# Uso del Sistema

Guía de instalación y operación del CRM Web Administrativo y Reportes de
Ruta Fija. Está orientada a una persona que abre el proyecto en otro equipo
Windows y usa PostgreSQL 16 instalado localmente.

## 1. Qué hace Ruta Fija

El CRM permite gestionar organizaciones, usuarios, grupos, conductores,
vehículos, asignaciones, incidencias, comunicados, reportes y auditoría.

La solución vigente no incluye aplicación móvil, GPS, mapas, aceptación o
rechazo por conductor, notificaciones push, correo ni almacenamiento externo.
Las asignaciones administrativas nacen como SCHEDULED.

## 2. Requisitos

| Requisito | Finalidad |
| --- | --- |
| Windows 10/11 y PowerShell | Scripts de operación local |
| Git | Clonar y actualizar el código |
| PostgreSQL 16 + psql | Base de datos local en puerto 5432 |
| Java 21 | API Spring Boot |
| Node.js 24.16.x + npm | CRM Angular |
| Visual Studio Code | Recomendado para tareas y edición |

Antes de iniciar, confirme que PostgreSQL es un servicio local y que su puerto
no está publicado hacia otras computadoras. La configuración aprobada escucha
solo 127.0.0.1 y ::1.

## 3. Clonar y preparar PostgreSQL

Clone el proyecto:

~~~powershell
git clone https://github.com/Alexandro752005/solucion_1_ruta_fija.git
Set-Location solucion_1_ruta_fija
~~~

En pgAdmin o psql cree una base vacía llamada solucion_ruta_fija_1. Defina un
usuario administrativo local como bootstrap; solo se usa para crear y revisar
los roles técnicos iniciales. No use una clave compartida ni la guarde dentro
del repositorio.

El proyecto separa estos destinos:

| Destino | Propósito | Regla |
| --- | --- | --- |
| solucion_ruta_fija_1 | Desarrollo y CRM local | Nunca usar para pruebas automáticas |
| ruta_fija_test | Integraciones | Se limpia automáticamente |
| ruta_fija_recovery_YYYYMMDD_fNN | Recuperación | No ejecutar la aplicación allí |

## 4. Aprovisionamiento inicial

Estos pasos se ejecutan una sola vez sobre una base de desarrollo vacía.

1. Cree el archivo privado de configuración. La contraseña se solicita de
   forma oculta y se genera un JWT aleatorio.

   ~~~powershell
   .\scripts\Initialize-RutaFijaNativeConfig.ps1
   ~~~

2. Cree roles técnicos y la base de pruebas aislada.

   ~~~powershell
   .\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
   ~~~

3. Aplique Flyway, otorgue solo el DML de la ubicación vigente y compruebe
   estructura, auditoría, constraints, UTC y privilegios.

   ~~~powershell
   .\scripts\Invoke-RutaFijaFlywayF13.ps1
   .\scripts\Grant-RutaFijaF31bApplicationPrivileges.ps1
   .\scripts\Test-RutaFijaF31bMobileSchema.ps1
   ~~~

4. Instale dependencias web.

   ~~~powershell
   Set-Location frontend
   npm.cmd ci
   Set-Location ..
   ~~~

Los archivos creados en backend/.local contienen secretos locales. Están
ignorados por Git y no deben adjuntarse a correos, chats o repositorios.

## 5. Iniciar y detener

Ejecute:

~~~powershell
.\iniciar_ruta_fija.bat
~~~

Cuando termine correctamente, abra:

| Recurso | Dirección |
| --- | --- |
| CRM | http://localhost:4200 |
| API | http://127.0.0.1:8080/api/v1 |
| Estado de API | http://127.0.0.1:8080/actuator/health |
| Contrato OpenAPI | http://127.0.0.1:8080/v3/api-docs |

Para detener solamente procesos administrados por Ruta Fija:

~~~powershell
.\finalizar_ruta_fija.bat
~~~

PostgreSQL se conserva como servicio local y la base de datos no se elimina.
El inicio normal mantiene las semillas desactivadas, por lo que no se publican
usuarios ni contraseñas de demostración.

## 6. Acceso y navegación

El acceso requiere un usuario existente de la organización. La aplicación no
crea cuentas de muestra en el arranque nativo. El primer SUPER_ADMIN debe
provisionarse de forma controlada por el responsable de la base y nunca con
credenciales conocidas.

La cabecera muestra la identidad y sesión arriba, y la navegación de módulos
debajo. En pantallas estrechas, el botón Menú abre la misma navegación.

| Rol vigente | Módulos |
| --- | --- |
| SUPER_ADMIN | Resumen y organizaciones |
| ADMIN | Resumen, usuarios, organización, grupos, conductores, vehículos, asignaciones, incidencias, comunicados, reportes y auditoría |
| CONDUCTOR | Sin acceso al CRM web administrativo; su aplicación móvil se incorpora en una etapa posterior |

El rol ADMIN concentra la administración y operación completa de su tenant.
Las asociaciones históricas de coordinación de grupos no otorgan permisos ni
se administran desde el CRM.

## 7. Operación funcional

### 7.1 Recursos

Use Usuarios, Grupos, Conductores y Vehículos para registrar los recursos de
la organización. El servidor deriva la organización del usuario autenticado:
modificar una URL del navegador no permite acceder a otra empresa.

### 7.2 Estados y asignaciones

| Recurso | Regla |
| --- | --- |
| Conductor | DISPONIBLE → RESERVADO → EN_SERVICIO → DISPONIBLE, con cambios controlados a DESCANSO y NO_DISPONIBLE |
| Vehículo | DISPONIBLE, EN_SERVICIO, MANTENIMIENTO o INACTIVO |
| Asignación | Nace SCHEDULED; se reserva, inicia, completa o cancela desde el CRM |

Una reserva futura no convierte físicamente al vehículo en RESERVADO. La
indisponibilidad futura se calcula a partir de asignaciones programadas.
PostgreSQL y la aplicación bloquean solapamientos de conductor y vehículo.

### 7.3 Incidencias y comunicados

Registre incidencias, haga seguimiento y resuélvalas desde el CRM. Los
comunicados se publican a la organización o a un grupo visible. Las acciones
críticas generan eventos de auditoría que no pueden editarse ni eliminarse.

### 7.4 Reportes y auditoría

Reportes obtiene disponibilidad, asignaciones e incidencias desde datos
persistidos. Puede limitar fechas y descargar resultados cuando el módulo lo
habilite. Auditoría es de solo lectura: filtra, pagina y muestra detalle de
eventos sin permitir modificarlos.

## 8. Verificación técnica

Pruebas unitarias del backend:

~~~powershell
Set-Location backend
.\mvnw.cmd test
Set-Location ..
~~~

Verificación completa nativa:

~~~powershell
.\verificar_ruta_fija.bat
~~~

La salida esperada termina en F3_1B_NATIVE_VERIFY=PASS. Las pruebas usan solo
ruta_fija_test y limpian datos al finalizar.

Validación del CRM unificado en ADMIN:

~~~powershell
.\scripts\Test-RutaFijaF23AdminUi.ps1
~~~

El resultado esperado es F2_3_FRONTEND_AUDIT=PASS. Consulte
[F2.3 - CRM ADMIN](ejecucion-nativa-f2-3.md) y la
[evidencia de cierre](evidencia-f2-3-crm-admin-2026-09-22.md) para el alcance
y las comprobaciones completas.

Para cerrar la consolidacion de roles antes de iniciar la API movil:

~~~powershell
.\scripts\Test-RutaFijaG2AdminClosure.ps1
~~~

Mantenga libres 8080 y 4200. La salida esperada empieza con
G2_ADMIN_CONSOLIDATION=PASS. La guia de controles esta en
[F2.4 / G2](ejecucion-nativa-f2-4-g2.md) y la
[evidencia G2](evidencia-g2-consolidacion-admin-2026-09-22.md).

Para revisar REST, login, refresh y WebSocket desde el proxy Angular:

~~~powershell
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify
~~~

Antes del smoke, cierre el runtime normal para liberar 8080 y 4200.

## 9. Seguridad y backup

Abra PowerShell o Visual Studio Code como Administrador y ejecute una sola vez:

~~~powershell
.\scripts\Set-RutaFijaPostgresqlLoopbackF16.ps1
.\scripts\Test-RutaFijaNativeSecurityF16.ps1
~~~

La primera orden limita PostgreSQL a loopback y reinicia el servicio. La segunda
revisa SCRAM, HBA, roles sin privilegios administrativos, secretos fuera de Git
y automatización nativa.

Para generar evidencia de recuperación post-migración:

~~~powershell
.\scripts\Invoke-RutaFijaRecoveryEvidenceF17.ps1
~~~

El proceso crea un dump en backups/ y una nueva base de recuperación; no
sobrescribe una existente. Guarde una copia del dump fuera del equipo si los
datos son importantes.

## 10. Problemas frecuentes

| Síntoma | Acción |
| --- | --- |
| PostgreSQL no responde | Confirme que el servicio PostgreSQL 16 esté iniciado y que 5432 no pertenezca a otro programa |
| Falta configuración local | Ejecute Initialize-RutaFijaNativeConfig.ps1; no cree archivos manuales con secretos |
| La prueba falla por destino | Verifique que ruta_fija_test exista y que no se haya cambiado su URL |
| CRM no abre | Revise .runtime/backend.stderr.log y .runtime/frontend.stderr.log; use finalizar_ruta_fija.bat antes de un nuevo intento |
| No se puede iniciar sesión | Solicite un usuario existente al responsable de la organización; no habilite semillas solo para adivinar credenciales |
| F1.6 solicita administrador | Cierre la consola, ábrala como Administrador y ejecute nuevamente el script |

## 11. Rutas de referencia

| Ruta | Contenido |
| --- | --- |
| backend/ | API, módulos, Flyway y pruebas |
| frontend/ | CRM Angular, proxy y pruebas de interfaz |
| scripts/ | Operación nativa, seguridad y recuperación |
| .vscode/tasks.json | Tareas integradas del proyecto |
| backups/ | Dumps locales ignorados por Git |
| docs/ | Diseño, evidencia y manuales |

Consulte también el [README principal](../README.md), las guías de
[F1.4](ejecucion-nativa-f1-4.md) y [F1.5](ejecucion-nativa-f1-5.md).
