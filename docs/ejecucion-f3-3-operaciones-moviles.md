# F3.3 - Operaciones moviles, V9/V10 e idempotencia

## Objetivo cerrado

F3.3 habilita la API real que utilizara el conductor: perfil, disponibilidad,
ubicacion vigente, asignaciones propias, respuesta autentica, inicio,
finalizacion, incidencias, comunicados y acuses. La base queda en Flyway
V1-V10 y no se requiere Docker.

## Precondiciones

- F3.1A, F3.1B y F3.2 estan aprobadas.
- PostgreSQL 16 funciona localmente en `127.0.0.1:5432`.
- Existe `backend/.local/ruta-fija-native.env` y continua ignorado por Git.
- La base de desarrollo esta exactamente en V1-V8 antes de aplicar la
  migracion; si ya esta en V10, no se ejecuta de nuevo el migrador.
- API y CRM estan detenidos durante backup, ensayo y aplicacion de V9/V10.

## Aplicacion protegida para una base en V8

Desde la raiz del repositorio, ejecute una sola vez:

~~~powershell
.\scripts\Invoke-RutaFijaF33V9V10Migration.ps1
~~~

El comando se detiene si detecta que los puertos 8080/4200 estan activos, si
el destino no es `solucion_ruta_fija_1`, si faltan V1-V8 o si una base de
recuperacion elegida ya existe. Su secuencia es:

1. Generar un `pg_dump` previo bajo `backups/f3-3/`, ruta ignorada por Git.
2. Crear una base de recuperacion nueva y restaurar el dump alli.
3. Ensayar V9/V10 con Flyway solo en esa copia.
4. Verificar esquema, privilegios y manifiesto de datos.
5. Aplicar exactamente V9/V10 sobre desarrollo y conceder DML minimo a
   `rf_app`.

La salida correcta contiene:

~~~text
F3_3_MIGRATION=PASS ... flyway=V1-V10 ... privileges=DML_without_DDL docker=0
~~~

No escriba contrasenas en la consola, en documentos ni en Git. La base de
recuperacion se conserva como evidencia y el comando nunca la sobrescribe.

## Auditoria y pruebas reproducibles

Con la migracion ya aplicada:

~~~powershell
.\scripts\Test-RutaFijaF33MobileOperations.ps1
.\verificar_ruta_fija.bat
~~~

La primera orden revisa rutas propias, canal movil, idempotencia, no historial
de ubicacion, ausencia de coordenadas en auditoria, V1-V10 y privilegios. La
segunda ejecuta unidades e integracion contra `ruta_fija_test`, no contra la
base de desarrollo, y limpia sus datos temporales al finalizar.

~~~text
F3_3_MOBILE_OPERATIONS_AUDIT=PASS ...
F3_3_NATIVE_VERIFY=PASS flyway=V1-V10 mobile_operations=PASS docker=0
~~~

En VS Code tambien estan disponibles las tareas **Ruta Fija: aplicar V9 y V10
protegidas (F3.3)** y **Ruta Fija: auditar operaciones moviles (F3.3)**.

## Inicio cotidiano

Una vez aprobada la auditoria, use `iniciar_ruta_fija.bat`. El arranque nativo
ejecuta la auditoria F3.3 antes de iniciar Spring Boot y Angular. Las URLs son:

- CRM: `http://localhost:4200`
- API: `http://127.0.0.1:8080/api/v1`
- Salud: `http://127.0.0.1:8080/actuator/health`

## Limite de la fase

F3.3 no crea Flutter, notificaciones push, GPS en segundo plano, fotos,
historial de ubicaciones ni reportes nuevos. F3.4 debera publicar el contrato
OpenAPI completo, ampliar la matriz de pruebas de aislamiento y agregar
reportes reales para `PENDING_RESPONSE`, `REJECTED` y `EXPIRED`.
