# Cierre técnico F1.1B — arranque nativo

Fecha: 2026-09-21

Estado: **aprobado técnicamente; pendiente de confirmación verde del propietario**

## Resultado

Ruta Fija inició de forma nativa contra PostgreSQL 16 local. Spring Boot quedó
saludable en 127.0.0.1:8080 y Angular quedó disponible en
http://localhost:4200. El control verificó los PIDs que poseían ambos puertos,
su ejecutable y su línea de comando antes de registrar el estado local.

El iniciador ejecutó primero la auditoría F1.3, que confirmó Flyway V1-V5,
btree_gist, restricciones de exclusión, auditoría append-only y privilegios
selectivos de rf_app. No se ejecutaron Docker, Compose ni Testcontainers.

## Evidencia aprobada

- F1_1B_START=PASS y F1_1B_RUNTIME=PASS.
- Health HTTP devolvió estado UP y el CRM respondió con su documento HTML.
- runtime-config.json se sirvió como local-native.
- Un segundo inicio fue idempotente: conservó los PIDs existentes y no abrió
  procesos duplicados.
- finalizar_ruta_fija.bat detuvo solo los PIDs verificados y liberó 8080 y
  4200.
- 21 pruebas unitarias backend: sin fallos ni errores.
- Angular: typecheck aprobado, 37 pruebas aprobadas y build de producción
  generado.

## Artefactos

- scripts/Invoke-RutaFijaNativeRuntime.ps1: inicio, prueba y cierre con
  verificación de PID.
- iniciar_ruta_fija.bat y finalizar_ruta_fija.bat: interfaz operativa para
  Windows.
- .runtime/: estado y logs locales ignorados por Git.
- .vscode/tasks.json: tareas de inicio, validación y cierre F1.1B.

## Próxima puerta

F1.4 podrá comenzar solo con una nueva señal verde. Su objetivo es reemplazar
las pruebas de integración dependientes de Testcontainers por pruebas protegidas
contra la base nativa ruta_fija_test.
