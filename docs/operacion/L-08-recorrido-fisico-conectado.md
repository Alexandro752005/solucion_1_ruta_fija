# L08-CON-01 — Recorrido físico, por etapas

2026-10-22. Exclusivamente caso sintético autorizado `a999ef52-d87f-498d-9251-1581cbb72562`, ruta_fija_test5432, APK separado `pe.rutafija.conductor.l08`. No participantes ni piloto reales. Máximo un CONDUCTOR histórico6→7; sin nuevas altas ADMIN/SUPER_ADMIN. El caso está preparado con guardas activas y sin ubicación concedida por herramientas.

1. Desde la raíz, en PowerShell interactiva privada: `./scripts/Invoke-RutaFijaL08ConnectedTrial.ps1 -Serve`. Mantener abierta. Si el caso ya existe, **no repetir**; diagnosticar y utilizar únicamente `-Serve -ResumeRunId 'a999ef52-d87f-498d-9251-1581cbb72562'` después de comprobar que su proceso anterior terminó. No borrar marcador, estado o cupos.
2. Esperar la comprobación de marcador/lock, ledger y preparación. En `Ensayo L08>` escribir `credenciales`, después `MOSTRAR_SOLO_LOCAL` cuando lo pida. Consultar credenciales únicamente en esa terminal e introducirlas solo en la app del Poco. El código/emisor SMTP real no participa. Informar solo resultado del login, nunca la credencial.
3. Tras contraste del login HTTP200, declarar personalmente disponibilidad en el móvil. El arnés observará PUT normal; no ADMIN simulando esa declaración. Preparar después la oferta mediante `preparar-servicio` en la terminal y comprobar su única referencia, sin crear otra ante fallo.
4. Recibir y aceptar la oferta personalmente en la app. Revisar consentimiento y permiso de ubicación; concederlos personalmente solo si se desea completar ese ensayo. Captura real solo durante el servicio y primer plano; sin ADB grant, sensor ficticio ni movimiento inseguro. Mantener ubicación fuera de chat/evidencia ordinaria. Terminales de esta ruta sintética no están calibrados: no acreditar corroboración física por distancia.
5. Iniciar, observar captura, completar servicio y cerrar sesión desde el móvil. Ante401/503/red o respuesta incierta, detenerse y consultar estado; no reenviar la mutación por rutina.
6. Solo después del recorrido efectivo, comandos `telefono-ok` y `gps-ok`, introduciendo sus frases de confirmación personales. `verificar` contrasta HTTP y persistencia; puede seguir PENDING. Batería/TalkBack de flujos tienen evidencia separada.
7. Cuando la comprobación pase, `salir` exige retirar exclusivamente este caso, deshabilitar cuenta/recursos, revocar sesiones/periodo y conservar ledger/historial/aceptaciones/antecedentes. Solo después de comprobar lock libre se retira el marcador y aparece `TEST_CLEANUP_RELEASED=true`. No forzar la salida ante un fallo. Archivar antes de reanudar suites; retirar únicamente el reverse USB de este caso.

La preparación usa los controles normales server-side y correo simulado; no acepta términos por el usuario ni cambia documentos publicados. Los comandos muestran secretos sintéticos solo en la consola privada. No ejecutar con redirección, transcripción o grabación. Un resultado físico limitado no sustituye cobertura de batería, calibración, carga25 o requisitos reales de operación.


## Pausa por batería y continuaci?n del mismo caso

Si el teléfono necesita carga, desconectarlo y cargarlo; no iniciar un servicio. En el prompt `Ensayo L08>` usar `pausar` una vez si el proceso sigue abierto. Este comando observa y termina sin retirar la cuenta ni el marcador; no equivale a `TEST_CLEANUP_RELEASED=true`. Si el proceso ya termin?, no lanzar otro caso ni borrar el marcador.

Cuando el teléfono tenga carga, comprobar que el proceso anterior termin? y reanudar en PowerShell interactiva desde la ra?z:

```powershell
.\scripts\Invoke-RutaFijaL08ConnectedTrial.ps1 -Serve -ResumeRunId 'a999ef52-d87f-498d-9251-1581cbb72562'
```

Mantener la terminal abierta. Comprobar protección y conexión USB antes de volver a la app. El login y cambio a Disponible anteriores se conservan; no repetir altas ni conceder ubicación por herramientas. La batería1% comunicada no es una prueba de consumo.
