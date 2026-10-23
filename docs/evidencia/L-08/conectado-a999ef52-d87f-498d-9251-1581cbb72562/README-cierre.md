# L08-CON-01 — Cierre del caso físico sintético

2026-10-22, America/Lima. RUN `a999ef52-d87f-498d-9251-1581cbb72562`.

**Resultado: PASS_PHYSICAL_FOREGROUND_CASE_ONLY.** Este cierre corresponde al caso autorizado; **L-08 permanece EN_CURSO y el piloto bloqueado**.

## Recorrido acreditado

Interacción personal en Poco X3 Pro Android13: login, declaración de disponibilidad, aceptación de oferta, consentimiento y permiso de ubicación, inicio, captura real en primer plano, finalización y logout. El backend de pruebas y PostgreSQL `ruta_fija_test@127.0.0.1:5432` confirman cada transición. Correo simulado; una sola alta CONDUCTOR autorizada.

- Aceptación: `2026-10-22T21:22:43.446836Z`.
- Inicio original: `2026-10-22T21:28:01.474477Z`, conservado tras actualizar el APK.
- Cierre: `2026-10-22T21:59:25.508493Z`.
- Logout HTTP204: `2026-10-22T22:01:18.046729600Z`.
- Catorce puntos GPS históricos; ubicación vigente retirada y ningún segmento abierto. Cero solicitudes de posición durante62,693 segundos observados después del cierre.

## Evidencia y correcciones

- [Evidencia manual inmutable](archivo-manual-previo-retiro/resultado-manual.json), SHA-256 `13661ee0aa87196777bfe5e373dfdd7086c864a95db5c68b5ed449fd0af53e8b`; [contraste previo al retiro](archivo-manual-previo-retiro/verificacion-archivo.json).
- [Preparación corregida](correccion-preparacion/README.md): rechazo400 inicial conservado; extremos derivados de la versión de ruta y guarda de envío incierto,14 pruebas puras.
- [Conversión Android y regresiones](correccion-gps/pruebas-y-fuentes.json): reproducción roja6/8 por indicadores de medición descartados; conversión local corregida conservando validación,50 pruebas dirigidas y analyze correctos. [APK](correccion-gps/apk-verificado.json) actualizado sin borrar datos. [Primer envío físico204/persistencia](correccion-gps/captura-fisica-tecnica-verificada.json).
- La huella `192acf5242cb653594853e514a310d629ad6cfde0c5d8e62116c77bf23d7dd80` comunicada al salir pertenece a la observación previa a la limpieza; su archivo append-only se conserva. La salida posterior registra `TEST_CLEANUP_RELEASED=true`; la huella final distinta corresponde al estado posterior.

## Retiro y preservación

[Postflight independiente de solo lectura](postflight-retiro-selectivo.json): cuenta/conductor/unidad/grupo deshabilitados; todos los refresh tokens del caso revocados, incluido uno de una sesión anterior; ningún periodo autorizado, compromiso o tramo abierto. Servicio, oferta y14 puntos conservados; ledger1/3/7 y dos aceptaciones intactos. Los hashes de todas las filas antecedentes de los ocho conjuntos comprobados coinciden.

`TEST_CLEANUP_RELEASED=true`: lock libre, marcador y archivo privado del fixture ausentes. Proceso y puerto4311 ausentes; [solo el reverse USB4311 retirado](usb-postflight.json). [Preservación](preservacion-postflight.json):144 archivos congelados L07,36 del incremento1 y12 protegidos, incluido final3 y términos publicados.

Pueden reanudarse comprobaciones dirigidas con rollback y sus preflights. **Las suites que limpian globalmente la base siguen prohibidas.** No reabrir este caso, reactivar la cuenta ni recuperar su cupo histórico.

## Límites

No acredita batería, TalkBack en recorridos operativos, independencia de medios, participantes reales, recorrido físico completo o llegada a un terminal calibrado. Conserva GPS con retiro máximo de siete días; ninguna coordenada o secreto se incorpora a la evidencia ordinaria. No se declara íntegra L-08 por este PASS ni se habilita despliegue/piloto.

[Resumen de cierre y huellas](cierre-caso.json).
