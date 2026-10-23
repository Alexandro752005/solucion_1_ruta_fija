# L-08: respaldo cifrado y recuperación del candidato local

2026-10-23 America/Lima. Responsable técnico: A-OPS; aprobación de condiciones reales: titular y operador designados. CI-L08-05/06. S/ 0. Procedimiento técnico preparado para el candidato local; no acredita por sí mismo un medio independiente ni una operación continua en campo.

## Alcance y precondiciones

El adaptador [Invoke-RutaFijaL08IndependentBackup.ps1](../../scripts/Invoke-RutaFijaL08IndependentBackup.ps1) usa `pg_dump` PostgreSQL 16 en formato custom y el componente Java 21 existente `AuthenticatedBackupArchive`: AES-256/GCM, nonce aleatorio de 96 bits, tag de 128 bits, cabecera versionada autenticada y clave de 32 bytes. Nunca escribe un dump sin cifrar; verifica el tag y la firma `PGDMP` antes de declarar éxito. Antes de restaurar autentica el archivo completo en memoria; el límite de esta herramienta local es 64 MiB de plaintext. Un backup mayor se rechaza y exige ampliar/diseñar un mecanismo de staging autenticado revisado; no es un respaldo ilimitado para producción.

La fuente habilitada es exclusivamente `ruta_fija_test@127.0.0.1:5432`, rol `rf_migrator`, modo `PGOPTIONS=-c default_transaction_read_only=on`. El ensayo usa solo su nuevo clúster 55441 y sus bases sintéticas. Ningún comando de este procedimiento migra ni limpia la base compartida. No ejecutar durante un arnés protegido. El archivo DPAPI de clave debe existir, ser custodiado por el operador y quedar fuera del destino de dumps; `PGPASSWORD` existe solo en el entorno del proceso, nunca en línea de comandos, logs o manifiestos.

El destino es parametrizable y debe existir. Se rechazan ancestros con reparse points. Una ruta en la misma unidad que el workspace requiere la bandera explícita `-AllowLocalCandidateCopy` y se registra como copia local. Una letra distinta o un volumen secundario **no demuestra independencia física**: antes del piloto el operador registra identificador físico, medio/custodio, prueba de lectura desde otro equipo y posibilidad de recuperar la clave sin depender del servidor perdido. Para disco USB: rotación de dos medios, conectar solamente para copiar/verificar y desacoplar al finalizar. Un medio permanentemente conectado al mismo servidor comparte riesgos y no acredita la condición fuera del servidor.

## Comandos reproducibles

Los modos por defecto son Plan y no abren conexiones ni crean archivos:

```powershell
./scripts/Invoke-RutaFijaL08IndependentBackup.ps1
./scripts/Test-RutaFijaL08Continuity.ps1
```

Con una clave DPAPI ya custodiada y variables de proceso preparadas sin imprimirlas, el operador sustituye rutas externas validadas. No copiar el ejemplo con sus placeholders:

```powershell
./scripts/Invoke-RutaFijaL08IndependentBackup.ps1 -Action Backup -Execute `
  -DestinationDirectory 'E:/RF-respaldo' -KeyFile 'F:/custodia/backup-key.dpapi' `
  -EvidenceFile 'D:/evidencias/backup-UTC-unico.json'

./scripts/Invoke-RutaFijaL08IndependentBackup.ps1 -Action Verify -Execute `
  -ArchiveFile 'E:/RF-respaldo/archivo.rfdb' -KeyFile 'F:/custodia/backup-key.dpapi' `
  -EvidenceFile 'D:/evidencias/verify-UTC-unico.json'
```

DPAPI CurrentUser protege la prueba local, pero depende de la identidad Windows y sus secretos de sistema: copiar solamente `backup-key.dpapi` a otro equipo/usuario no permite recuperarla. **No es la ceremonia de custodia independiente definitiva.** Antes del piloto, exportar la clave AES original a una envoltura portable autenticada con un protector independiente acordado con el titular; custodiar esa envoltura offline en USB cifrado separado del archivo de base, con recuperación del protector fuera del servidor. Ensayar efectivamente el descifrado del dump desde una identidad/host destino distintos, sin recurrir al perfil DPAPI ni a secretos del servidor perdido, y conservar solo hashes/resultados de la prueba. No guardar claves/protectores en `artifacts/`, documentos públicos, correo ni junto al archivo cifrado. Esta validación no crea/exporta esa clave definitiva ni acredita esa ceremonia.

## Programación, RPO y verificación diaria

Directiva: al menos un respaldo válido por hora. Propuesta concreta para el Programador de tareas de Windows disponible: cada 30 minutos, duración máxima de trabajo 15 minutos, impedir instancias simultáneas y conservar la salida/manifest de cada ejecución. No se instala una tarea contra un medio ficticio o ausente. El custodio debe confirmar un medio disponible, la cuenta de ejecución y su acceso al material de clave antes de activar la tarea.

RPO se mide desde el inicio conservador del snapshot del último archivo **copiado, autenticado y disponible fuera del servidor**, no desde la fecha de un log ni desde el fin de un intento. En funcionamiento sin fallos, `intervalo 1800 s + duración máxima 900 s = 2700 s`, inferior a 3600 s. Un fallo invalida esa garantía: la sonda debe comprobar cada minuto `ahoraUTC - sourceSnapshotUpperBoundAt <= 3600 s`; un archivo ausente, hash distinto, fecha futura o edad mayor abre incidente y bloquea la autorización de encendido. No declarar RPO garantizado únicamente por haber programado tareas.

Cada día: verificar SHA-256 y tag GCM de los archivos del último día, registrar fechas/resultado en evidencia nueva, comprobar custodia/desacople y espacio libre. Una vez por semana y después de cambiar PostgreSQL/clave/formato: restaurar en un clúster aislado vacío, comparar tablas, claves, índices, conteos y hashes; después probar API/clientes según la matriz de recuperación. Retención propuesta del candidato: 48 copias horarias y 7 copias diarias; no borrar respaldos congelados ni históricos de L-00/L-08. La política definitiva de datos personales se aprueba antes de operar con participantes reales.

[Test-RutaFijaL08BackupAge.ps1](../../scripts/Test-RutaFijaL08BackupAge.ps1) implementa la puerta de antigüedad: verifica el SHA del archivo exacto, rechaza fecha futura, edad de snapshot>3600 s y autenticación>86400 s. Su `DailyVerificationManifest` opcional debe ser una verificación GCM posterior que coincida con el mismo SHA. Un backup recién creado ya incluye esa autenticación. Se registra incidente sin autorizar un RPO operativo si no hay medio independiente. Su modo Plan no lee archivos; `Check -Execute -BackupManifest ... -ArchiveDirectory ... -EvidenceFile ...` evalúa una vez y conserva evidencia nueva.

## Restauración y reloj de RTO

1. Registrar incidente y arrancar un único reloj al declarar pérdida o iniciar el ensayo. Conservar cualquier fallo y reintento en ese mismo intervalo.
2. Aislar el origen, recuperar último medio/clave válidos y verificar hash/tag; rechazar manipulación o clave equivocada antes de pasar plaintext a `pg_restore`.
3. Crear un clúster PostgreSQL 16 vacío con puerto propio y loopback; nunca sobrescribir 5432 ni los clústeres históricos 55438/55439/55440. El ensayo automatizado nuevo reserva 55441 y se niega si ya está ocupado.
4. Restaurar de forma transaccional con errores fatales. Comparar esquema, contenido, constraints, índices y permisos pertinentes. El ensayo sintético usa `--no-owner --no-acl` y no acredita ACL de la aplicación; la prueba REST03 histórica sí verificó su copia V50 con esos límites documentados.
5. Arrancar API y clientes con la configuración de recuperación autorizada y probar la operación normal. Cerrar el reloj solo al recuperar la operación requerida. Un RTO SQL aislado no es por sí mismo un RTO hasta operación completa.
6. Detener exclusivamente los procesos del ensayo y conservar el caso, manifiestos, tiempos y hashes. Si hay un fallo, preservar y diagnosticar; no reiniciar el reloj para reducir el resultado.

Comando ejecutable del ensayo nuevo:

```powershell
./scripts/Test-RutaFijaL08Continuity.ps1 -Execute
```

El script crea dos tablas sintéticas, sus claves e índice y 101 filas totales; cifra el dump, rechaza un tag alterado y una clave equivocada, añade dos filas después del snapshot, restaura el snapshot y compara contenido. Mide un RTO nuevo incluyendo aprovisionamiento desde la entrada al script, además de la antigüedad del snapshot y las dos filas posteriores que no corresponden al punto recuperado. Prueba sondas HTTP sintéticas y PostgreSQL real, los detiene y mide la detección. No usa datos personales ni escribe 5432.

## Evidencia y límite de aceptación

La evidencia de este incremento se deposita bajo [incremento-5/ops](../evidencia/L-08/incremento-5-candidato-final/ops/README.md). REST03 = **318,731 s** es un resultado histórico ya conservado; no se presenta como una nueva ejecución. La nueva prueba da cobertura ejecutable a cifrado, rechazo de corrupción, copia parametrizable, restauración sintética y cálculo de RPO/RTO para el candidato local. Permanecen como condiciones del piloto: medio físicamente independiente comprobado, recuperación de clave independiente, calendario realmente activado/verificado y ensayo representativo de pérdida del servidor hasta API/clientes recuperados. Ninguna cifra local convierte esas condiciones en hechos.
