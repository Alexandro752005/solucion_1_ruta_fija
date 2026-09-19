# Respaldo local PostgreSQL — F0.3

El 19 de septiembre de 2026 se creó un respaldo lógico de la base nativa aprobada para desarrollo, `solucion_ruta_fija_1`, en una ruta hermana al repositorio:

`D:\UTP\CICLO X UTP\INTEGRADOR I\APF1\respaldo_postgresql_ruta_fija`

El archivo se generó con formato custom de PostgreSQL (`pg_dump -Fc`), sin propietarios ni privilegios, y su manifiesto externo registra nombre, tamaño, SHA-256 y límites de recuperación. No se guardan claves en este documento ni en el repositorio.

## Resultado verificable

| Verificación | Resultado |
|---|---|
| Archivo no vacío | Sí, 875 bytes |
| Integridad | SHA-256 registrado y recalculado sin cambios |
| Lectura del archivo | `pg_restore --list` finalizó correctamente |
| Contenido de origen | Base vacía: cero tablas en `public`, sin historial Flyway |
| Objetos restaurables | Cero; quedan cuatro entradas técnicas de catálogo |

Este respaldo certifica solo el estado inicial vacío de PostgreSQL nativo. No es una copia de datos del antiguo entorno Docker y no reemplaza una prueba de recuperación.

## Siguiente control

F0.4, todavía pendiente de autorización explícita, restaurará el archivo en una base temporal de recuperación distinta de `solucion_ruta_fija_1`. Nunca se restaurará sobre la base de desarrollo.
