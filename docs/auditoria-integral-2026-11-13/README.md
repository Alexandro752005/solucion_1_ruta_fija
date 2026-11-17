# Auditoría integral de Ruta Fija

**Estado: en curso. No es una certificación de cierre.** El [registro de avance](avance.json) conserva el siguiente paso y los límites. El [checkpoint Native](postgresql/latest-native-checkpoint.json) identifica el conjunto seleccionado y las clases realmente terminadas; una ejecución focal de cinco clases no acredita el conjunto completo de 25.

El objetivo completo está identificado por archivo y hash en los registros de alcance. Las 297 filas enumeradas se complementan con la prosa, responsabilidades, diez categorías de pruebas y catorce criterios de cierre. La revisión comprende backend, CRM, móvil, contratos, PostgreSQL, seguridad y funcionalidades, preservando arquitectura, roles, organizaciones, diseño visual y operación conectada.

- [Arquitectura y DAO/SOLID](arquitectura/README.md).
- [Inventario y recorridos funcionales](funcional/README.md).
- [Trazabilidad estática y límites de resolución](trazabilidad/resumen.json).
- [Hallazgos clasificados](hallazgos.json).
- [Pruebas actuales y reproducción](validacion-final/README.md).
- [Última suite unitaria backend](validacion-final/backend-unit/latest.json).
- [Inventario actual de clases de prueba](validacion-final/inventario-clases-prueba-actual.json).
- [TDD-008: totales omitidos de interrupciones y relevos](backend-reportes/tdd-008/README.md).
- [Revisión independiente de TDD-008](backend-reportes/tdd-008/qa-independiente.md).
- [Navegación física Android de solo lectura](validacion-final/mobile-device-navigation.json).
- [Borrador A–I anterior al cierre](validacion-final/REPORTE_AUDITORIA_EN_CURSO.md).

Los resultados y borradores tienen fecha y alcance. TDD-008 cambió `ReportService`; la suite unitaria posterior aprobó 395 casos en 68 clases. Las mediciones combinadas y suites de una revisión productiva anterior no se presentan como una certificación global de las fuentes nuevas. El inventario actual contiene 136 clases con pruebas, frente a 135 antes de agregar la regresión de reportes. Un inventario clasifica fuentes; los XML JUnit prueban ejecuciones.

Las bases de integración son desechables y sus identidades se comprueban antes de operar. La auditoría no ejecuta integraciones sobre el PostgreSQL compartido ni modifica migraciones históricas o evidencias anteriores. Las credenciales y logs que pueden contener datos están en el árbol privado `.runtime`; los documentos públicos contienen referencias y datos sintéticos mínimos.

No se ha leído programáticamente el contador visual de PROBLEMAS de VS Code. Los más de 8000 problemas son una observación inicial del usuario; los resultados verificables de analizadores, compilaciones y pruebas se documentan por separado. No se inventa un contador final del IDE.
