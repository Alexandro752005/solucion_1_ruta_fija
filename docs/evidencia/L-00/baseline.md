# Evidencia de baseline local — L-00

**Fecha:** 2026-10-13, America/Lima  
**Checkout:** `D:\UTP\CICLO X UTP\INTEGRADOR I\APF3\solucion_1_ruta_fija_3`  
**Resultado:** suites y compilaciones documentadas pasan; los resultados no equivalen a aceptación funcional V3.1.

## Backend y PostgreSQL

| Comando ejecutado | Resultado observado |
|---|---|
| `.\scripts\Invoke-RutaFijaNativeTest.ps1 -Action Preflight` | `F1_4_PREFLIGHT=PASS`; PostgreSQL 16; base `ruta_fija_test`; Docker 0. |
| `.\scripts\Invoke-RutaFijaNativeTest.ps1 -Action Verify` | Exit 0, `BUILD SUCCESS`; 24 unitarias + 33 integraciones = 57 pruebas, 0 fallos/errores/omitidas. Flyway validó V1–V12. `F3_3_MOBILE_OPERATIONS_AUDIT=PASS`, `F3_4_CONTRACT_REPORTS_AUDIT=PASS`, `F3_4_NATIVE_VERIFY=PASS`. |

El runner validó destino local y rol antes de limpiar exclusivamente `ruta_fija_test`; la base de desarrollo no fue el objetivo. Servicio PostgreSQL nativo 16.15 observado en ejecución. No se imprimieron secretos.

## CRM Angular

| Comando ejecutado desde `frontend/` | Resultado observado |
|---|---|
| `npm.cmd run lint` | Exit 0; ejecuta `typecheck`; sin errores de tipos. |
| `npm.cmd run test:ci` | Exit 0; 17 archivos de prueba, 42 pruebas aprobadas. |
| `npm.cmd run build` | Exit 0; compilación de producción generada. |

Versiones del entorno/manifiestos inspeccionados: Node 24.16.0, npm 11.13.0, Angular 22.1.4, Angular CLI/build 22.1.6, TypeScript 6.0.3 y Vitest 4.1.11. `lint` no es ESLint; está definido como comprobación de tipos.

## Android Flutter

| Comando ejecutado desde `mobile/` | Resultado observado |
|---|---|
| `flutter analyze` | Exit 0; `No issues found`. |
| `flutter test` | Exit 0; 41 pruebas aprobadas. |
| `flutter build apk --debug` | Exit 0; APK generado en `mobile/build/app/outputs/flutter-apk/app-debug.apk` en 154,7 s. |

Artefacto local: 161.486.751 bytes; SHA-256 `A56AC7368927502ACAFD419ADEC8FE54799F140BEA9430F5EE8EE95867A2374A`.

Versiones: Flutter 3.47.5, Dart 3.13.4, Android SDK 36.0.0, Java 21.0.10. `adb devices -l` no informó dispositivos conectados. `flutter doctor -v` indicó licencias Android sin aceptar. El warning de lectura de XML SDK y advertencias Java source/target 8 se registran sin tratarlos como fallos de suites.

## Snapshot de fuentes y comprobación de retorno

Se ejecutó `& .\scripts\New-RutaFijaL00Snapshot.ps1`. El archivo [source-snapshot.zip](../../../backups/L-00-2026-10-13-final3/source-snapshot.zip) contiene 4.496 archivos de código y metodología; tamaño 292.096.103 bytes; SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`. El [manifiesto](source-manifest.sha256) conserva un SHA-256 por archivo y su huella es `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`.

La verificación extrajo el ZIP en un temporal local y comparó tamaño de inventario y SHA-256 de los 4.496 archivos: `PASS`; luego eliminó solo ese temporal creado para la comprobación. El snapshot excluye dependencias y productos generados, configuraciones locales que pueden contener secretos, `memory/`, `backups/`, el mapa, el tablero y esta evidencia/manifest para evitar referencias circulares. Es una copia de fuentes/documentación, no una copia de base de datos ni una prueba de reversión de Flyway; restauración de datos y rollback por lote permanecen en L-08.

## Evaluación del recurso de correo S/0

El árbol actual no tiene proveedor ni configuración SMTP/API de correo. Consulté fuentes oficiales el 2026-10-13 y encontré dos candidatos sin costo base:

| Candidato | Cuota publicada | Límite relevante para OTP | Requisito observado | Estado local |
|---|---|---|---|---|
| Brevo Free | 300 envíos/día; incluye correo transaccional | Al superar la cuota puede retener hasta 1.000 correos transaccionales en una cola de reintentos; un OTP vencido no debe salir tarde | Requiere dominio de envío que la organización controle y autentique | No hay cuenta, dominio ni DNS configurados para este proyecto |
| Resend Free | 3.000 envíos/mes y 100/día; el envío transaccional usa `/emails` | La cuota mensual y diaria debe aplicarse antes del envío; no habilitar plan pagado/overage | Verificación de dominio y registros DNS de envío | No hay cuenta, dominio ni DNS configurados para este proyecto |

Fuentes del proveedor: [precios de Resend](https://resend.com/pricing), [autenticación de dominios Resend](https://resend.com/changelog/domain-verification-events), [plan Free de Brevo](https://help.brevo.com/hc/es/articles/208580669-FAQ-Cu%C3%A1les-son-los-l%C3%ADmites-del-plan-Gratis) y [dominio de envío Brevo](https://help.brevo.com/hc/en-us/articles/17286219877778-FAQs-About-domain-authentication-Brevo-code-DKIM-DMARC).

La evaluación de cuotas es documental; no acredita propiedad de cuenta/dominio ni entrega real. No se selecciona proveedor sin confirmar que el usuario/organización controla cuenta y dominio. La prueba de entrega OTP real corresponde a L-01 (`VT-V3-OPEN-OTP-PROVIDER-01`). El bloqueo actual de L-00 es la decisión/recurso de proveedor S/0 sin resolver, no una prueba funcional adelantada.

## Límites y observaciones

- No se ejecutó envío de correo: el backend no declara dependencia/configuración SMTP ni flujo OTP y no hay proveedor/cuenta/dominio de envío presente en las configuraciones locales revisadas. No se mostraron valores secretos.
- No hubo prueba manual en hardware Android ni en emulador; no se conectó dispositivo. La elección de hardware del piloto y su validación corresponden a `OPEN-DEVICE-INVENTORY`/L-08 y no bloquean la baseline de L-00.
- La suite móvil actual incluye pruebas de sincronización offline heredada. Su éxito acredita el comportamiento actual de ese código, no su conformidad con el objetivo V3.1, que excluye colas offline.
- Backend mostró advertencias no bloqueantes sobre API deprecada en exportación PDF, self-attach de Mockito/JDK y locks Hibernate con consultas posteriores.
- Las tres comprobaciones Flutter terminaron con exit 0. El build debug no sustituye la validación física: no había emulador ni dispositivo conectado.

## Actualización de decisión del 2026-10-14

La evaluación de candidatos del 2026-10-13 quedó sustituida para el piloto por la decisión autorizada de Gmail SMTP y la excepción de dominio documentadas en [decision-Gmail-2026-10-14.md](decision-Gmail-2026-10-14.md). No se hizo envío ni integración SMTP en L-00. La huella del snapshot final3 se verificó nuevamente y permanece idéntica; no se generó otro snapshot. Esta decisión se registró después de capturar final3 y queda como delta documental posterior; el ZIP se conserva sin modificaciones.