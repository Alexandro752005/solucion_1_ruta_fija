# F1.1A — Configuración nativa segura y preflight

Esta subfase prepara Ruta Fija para ejecutarse con PostgreSQL 16 instalado en Windows. No inicia Spring Boot, Flyway, la API ni el CRM; por ello no aplica migraciones ni crea datos.

## Archivo local de secretos

El archivo efectivo es `backend/.local/ruta-fija-native.env`. Está excluido de Git de forma explícita y contiene las variables de datasource, JWT, CORS local, cookie HTTP y política de semillas.

Para crearlo por primera vez, desde la raíz del repositorio:

```powershell
.\scripts\Initialize-RutaFijaNativeConfig.ps1
```

El script solicita la clave PostgreSQL mediante entrada oculta, genera un JWT aleatorio de 32 bytes y rechaza sobrescribir un archivo existente. La plantilla versionada `backend/ruta-fija-native.env.example` contiene solo marcadores, nunca secretos reales.

## Preflight nativo

Ejecute:

```powershell
.\iniciar_ruta_fija.bat
```

El preflight valida Java 21+, Node 24+, npm, PostgreSQL 16, UTF8, la URL autorizada, el JWT y el aislamiento del archivo local frente a Git. También ejecuta una conexión JDBC de solo lectura; no inicia el contexto Spring.

El resultado correcto incluye:

```text
NATIVE_PREFLIGHT=PASS
F1.1A no inicio Spring Boot, Flyway, API ni CRM.
```

`finalizar_ruta_fija.bat` es seguro en esta fase porque aún no existen procesos nativos iniciados por el proyecto.

## Límite de la subfase

Spring Boot ejecuta Flyway al arrancar. El arranque real se deja para F1.1B, después de que F1.2 cree los roles y la base de pruebas, y F1.3 aplique V1–V5 de manera controlada.
