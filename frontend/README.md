# CRM web de Ruta Fija

Frontend standalone construido con Angular 22 y TypeScript 6 para la Fase 4.

## Requisitos

- Node.js 24.15 o 24.16
- npm 11
- Backend disponible en `http://localhost:8080`

## Ejecución local

```powershell
npm.cmd ci
npm.cmd start
```

El servidor de desarrollo publica el CRM en `http://localhost:4200` y redirige
`/api` al backend mediante `proxy.conf.json`.

## Verificación

```powershell
npm.cmd run lint
npm.cmd run test:ci
npm.cmd run build
```

La configuración que puede cambiar después del build se encuentra en
`public/config/runtime-config.json`. No debe contener secretos. El token de acceso
se conserva únicamente en memoria; la renovación depende de una cookie `HttpOnly`
emitida por el backend.

El interceptor no adjunta Bearer a login, refresh ni logout. Las renovaciones se
serializan entre pestañas con Web Locks y un lease efímero de respaldo que no
almacena credenciales. La URL de API debe ser una ruta no raíz del mismo origen;
la carga de configuración aborta tras cinco segundos y conserva `/api/v1` como
valor local seguro.
