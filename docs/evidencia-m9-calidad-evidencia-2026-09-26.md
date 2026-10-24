# Evidencia M9 - Calidad, APK y trazabilidad

- Fecha de ejecución: 2026-09-26.
- Estado automático: aprobado.
- Estado de puerta M9: aprobado por confirmación `VERDE` explícita del usuario
  después de ejecutar la aplicación en un dispositivo Android real.

## Resultado reproducible observado

Desde la raíz se ejecutó:

~~~powershell
.\scripts\Test-RutaFijaM9QualityEvidence.ps1
~~~

La salida final observada fue:

~~~text
M9_QUALITY_EVIDENCE_AUDIT=PASS m9=9/80 total=80/80 accessibility=PASS android=PASS apk_v2=PASS apk_sha256=8e7689f66f92fd95e9a2d40536211d37104d8a39de927a2bcac27d1b5ad7c2c2 git_head=087202418bafb220749c2b1558ce173e114892b5 git_worktree=DIRTY flutter_test_cases=41
~~~

La APK debug `0.1.0+2` se generó en
`mobile/build/app/outputs/flutter-apk/app-debug.apk`; su firma se verificó con
el esquema APK v2. El auditor encadenó M8 y, por tanto, ejecutó `mvn verify`
con 24 pruebas unitarias y 33 pruebas de integración contra PostgreSQL local,
además de `flutter analyze` sin incidencias y 41 pruebas Flutter aprobadas.

## Trazabilidad honesta

El hash SHA-256 identifica exactamente la APK generada. El commit Git de
referencia fue `087202418bafb220749c2b1558ce173e114892b5`, pero el auditor
detectó `git_worktree=DIRTY`. Por ello esta evidencia **no** afirma que el APK
corresponda exclusivamente a ese commit. La asociación a un commit limpio y la
publicación remota requieren revisión y autorización explícita del propietario.

## Observaciones no bloqueantes

- Android informó que una herramienta entiende XML de SDK hasta versión 3 y
  encontró versión 4; la APK se construyó y verificó correctamente.
- Mockito/JDK emitieron advertencias de agente dinámico durante pruebas Java;
  las 24 pruebas unitarias y 33 integraciones finalizaron sin fallos.
- Flutter informó paquetes con versiones más recientes disponibles; no se
  actualizó ninguna dependencia durante M9.

## Cierre de puerta

La confirmación explícita `VERDE` del propietario fue recibida después de la
ejecución en un dispositivo Android real. M9 queda cerrado con **80/80 puntos**
y G4 puede considerarse aprobado. La lista reproducible de validación física se
conserva en [Ejecución M9](ejecucion-m9-calidad-evidencia.md) para futuras
regresiones; este cierre no elimina las observaciones no bloqueantes anteriores.
