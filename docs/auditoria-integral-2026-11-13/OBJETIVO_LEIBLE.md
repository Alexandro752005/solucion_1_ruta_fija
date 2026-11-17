AUDITAR, VERIFICAR, CORREGIR Y VALIDAR INTEGRALMENTE EL PROYECTO RUTA FIJA, ABARCANDO CÓDIGO FUENTE, ARQUITECTURA, CONTRATOS, CONEXIONES, FUNCIONALIDADES, SEGURIDAD, BASE DE DATOS Y PRUEBAS DE SOFTWARE. APLICAR TDD AUTÉNTICO (RED → GREEN → REFACTOR) A LAS CORRECCIONES FUNCIONALES QUE LO PERMITAN, DOCUMENTAR LAS EVIDENCIAS Y PRESERVAR EL COMPORTAMIENTO EXISTENTE. CONTINUAR HASTA CUMPLIR LOS CRITERIOS VERIFICABLES O DOCUMENTAR LOS BLOQUEOS REALES.

1. ROL Y MISIÓN

Actúa como ARQUITECTO PRINCIPAL DE SOFTWARE, AUDITOR TÉCNICO SÉNIOR Y COORDINADOR DE AGENTES ESPECIALIZADOS.

Trabajarás sobre la totalidad del repositorio actual del proyecto Ruta Fija:

https://github.com/Alexandro752005/solucion_1_ruta_fija

Ruta Fija es una plataforma integral de administración y coordinación de transportistas que utiliza:

•	Backend centralizado Java y Spring Boot.

•	CRM web Angular y TypeScript.

•	Aplicativo móvil Flutter y Dart.

•	PostgreSQL como base de datos.

•	APIs REST y otros mecanismos de comunicación existentes.

•	Autenticación, autorización, gestión de organizaciones, conductores, vehículos, asignaciones, incidencias, comunicados, reportes y geolocalización.

Las versiones, dependencias y capacidades reales deben obtenerse directamente del código actual. No asumas que las versiones documentadas anteriormente siguen vigentes.

TU MISIÓN NO CONSISTE ÚNICAMENTE EN ENCONTRAR ERRORES.

Debes comprobar las funcionalidades implementadas, identificar defectos, verificar sus causas, ejecutar correcciones controladas, realizar pruebas y entregar evidencias reproducibles.

La auditoría debe comprender todos los módulos y superficies del sistema, no únicamente los archivos modificados recientemente.

2. MODALIDAD DE EJECUCIÓN CONTINUA

Mantén el objetivo activo durante todas las fases.

Antes de escribir código, realiza una planificación interna basada en el estado real del repositorio.

Después ejecuta las fases autorizadas sin detenerte para entregar solamente planes o informes preliminares.

No solicites confirmación para cada corrección local, reversible y comprendida dentro del alcance.

Cuando se presente un problema:

1.	Investiga su causa raíz.

2.	Determina su impacto.

3.	Identifica una solución mínima.

4.	Escribe o actualiza la prueba correspondiente.

5.	Ejecuta la prueba y registra su resultado.

6.	Implementa la corrección.

7.	Repite las pruebas.

8.	Revisa las regresiones.

9.	Documenta el resultado.

10.	Continúa con la siguiente actividad.

No utilices ciclos infinitos ni simules resultados.

Cuando una tarea no pueda completarse debido a infraestructura, permisos, límites de ejecución o decisiones no autorizadas, registra el bloqueo, continúa con las tareas independientes y conserva el estado para su posterior reanudación.

No declares finalizado el Goal mientras existan criterios pendientes que puedan comprobarse con los recursos disponibles.

3. ORGANIZACIÓN MULTIAGENTE

Utiliza subagentes especializados si el entorno de Codex ofrece esa capacidad.

Establece las siguientes responsabilidades:

AGENTE A — ARQUITECTURA

Responsable de revisar arquitectura modular, dependencias, separación de responsabilidades, principios SOLID, MVC, DAO/Repository, consistencia de contratos y acoplamientos.

AGENTE B — BACKEND

Responsable de Java, Spring Boot, APIs, controladores, servicios, DTO, excepciones, validaciones, transacciones y reglas de negocio.

AGENTE C — BASE DE DATOS

Responsable de PostgreSQL, JPA, JdbcTemplate, migraciones Flyway, relaciones, restricciones, índices, consultas y consistencia transaccional.

AGENTE D — CRM

Responsable de Angular, TypeScript, componentes, servicios HTTP, formularios, navegación, estados visuales y contratos de comunicación.

AGENTE E — FLUTTER

Responsable de Dart, Android, navegación, almacenamiento seguro, permisos, funcionalidades móviles, GPS y comunicación con el backend.

AGENTE F — SEGURIDAD

Responsable de autenticación, JWT, autorización por roles, aislamiento de organizaciones, protección de información, consentimiento GPS y análisis de dependencias.

AGENTE G — TDD Y QA

Responsable de diseñar pruebas, ejecutar RED → GREEN → REFACTOR, analizar cobertura, implementar escenarios de regresión y revisar resultados de compilación.

AGENTE H — AUDITOR INDEPENDIENTE

Responsable de revisar las modificaciones realizadas por otros agentes, validar evidencias y detectar errores no identificados durante la implementación.

Si no existen herramientas de subagentes, ejecuta estas responsabilidades secuencialmente y no declares una revisión como independiente cuando no lo sea.

Cada agente debe tener un ámbito definido. Evita escrituras simultáneas sobre los mismos archivos.

4. FASE 0 — PREPARACIÓN Y LÍNEA BASE

Antes de realizar cualquier modificación:

1.	Identifica el repositorio, rama activa y revisión Git.

2.	Comprueba si existen modificaciones locales anteriores.

3.	Preserva los cambios del usuario.

4.	Identifica los directorios productivos, pruebas, documentación, código generado y snapshots.

5.	Revisa AGENTS.md y las instrucciones existentes del repositorio, si las hay.

6.	Identifica dependencias y versiones instaladas.

7.	Comprueba la disponibilidad real de Java, Maven, Node.js, Angular, Flutter, Dart y PostgreSQL.

8.	Define una línea base de compilación y pruebas.

9.	Registra los errores, advertencias y pruebas fallidas antes de corregir.

10.	Identifica cuáles de esos problemas ya existían y cuáles podrían haberse introducido recientemente.

Presta especial atención a:

docs/mapa-en-vivo/verificacion/baseline/fuentes/

Determina si esa ruta contiene copias históricas de fuentes y si los analizadores de VS Code están interpretándolas equivocadamente como proyectos productivos.

No modifiques evidencias históricas ni ocultes errores del código activo.

SALIDA DE LA FASE:

Inventario del repositorio, estado Git, herramientas disponibles, resultados iniciales y matriz de riesgos.

5. FASE 1 — AUDITORÍA DE ARQUITECTURA

Analiza integralmente:

•	Organización del monolito modular.

•	Dependencias entre módulos.

•	Separación entre controladores, servicios, dominio e infraestructura.

•	Aplicación real de MVC y sus límites.

•	Aplicación de SOLID, identificando cumplimiento, cumplimiento parcial e incumplimiento.

•	Aplicación de DAO y Repository.

•	Inyección de dependencias.

•	Responsabilidades de clases e interfaces.

•	Duplicación de lógica.

•	Código muerto o innecesario.

•	Acoplamiento excesivo.

•	Coherencia de DTO y entidades.

•	Manejo de excepciones.

•	Reutilización de componentes.

•	Consistencia de nomenclatura y estructura.

No realices refactorizaciones cosméticas.

Para cada hallazgo, identifica:

Archivo, símbolo, ubicación, comportamiento afectado, gravedad, evidencia y solución propuesta.

Clasifica cada resultado como:

CONFIRMADO, PARCIAL, RIESGO CONDICIONAL, NO VERIFICABLE o SIN HALLAZGO.

6. FASE 2 — AUDITORÍA DE CONTRATOS E INTEGRACIONES

Reconstruye los recorridos reales de comunicación:

Angular → Spring Boot → PostgreSQL.

Flutter → Spring Boot → PostgreSQL.

Backend → mecanismos de notificación y actualización existentes.

Verifica:

1.	Rutas REST.

2.	Métodos HTTP.

3.	Parámetros.

4.	DTO de entrada y salida.

5.	Serialización JSON.

6.	Campos obligatorios y opcionales.

7.	Tipos de datos.

8.	Fechas y zonas horarias.

9.	Códigos HTTP.

10.	Manejo de errores.

11.	Autenticación y autorización.

12.	Compatibilidad entre clientes y backend.

13.	Gestión de sesiones.

14.	Actualizaciones y reconexiones permitidas.

15.	Contratos WebSocket, cuando existan.

16.	Contratos de almacenamiento y migración.

Detecta:

•	Endpoints inexistentes utilizados por clientes.

•	Campos incompatibles.

•	Estados interpretados de manera diferente.

•	Validaciones incompletas.

•	Respuestas no contempladas.

•	Dependencias entre módulos que dificulten las pruebas.

•	Inconsistencias entre el contrato documentado y el comportamiento implementado.

Genera una matriz de trazabilidad:

CLIENTE → OPERACIÓN → ENDPOINT → CONTROLADOR → SERVICIO → REPOSITORIO → TABLA → PRUEBA.

No inventes relaciones que no puedas verificar.

7. FASE 3 — AUDITORÍA FUNCIONAL COMPLETA

Examina todos los módulos funcionales encontrados en el repositorio.

Como mínimo, comprueba las capacidades que realmente existan relacionadas con:

•	Inicio y cierre de sesión.

•	Recuperación de acceso.

•	Verificación de correo.

•	Usuarios, roles y organizaciones.

•	Conductores.

•	Vehículos.

•	Grupos.

•	Disponibilidad operativa.

•	Creación y gestión de asignaciones.

•	Aceptación y rechazo de asignaciones.

•	Inicio, ejecución y finalización de servicios.

•	Incidencias.

•	Comunicados.

•	Notificaciones.

•	Reportes y exportaciones.

•	Geolocalización.

•	Mapa personal del conductor.

•	Mapa en vivo del CRM.

•	Consentimiento y permisos GPS.

•	Configuraciones y auditoría.

Contrasta los flujos reales de los tres componentes de la plataforma.

No declares que una funcionalidad está implementada solo porque existe una clase o endpoint.

Comprueba sus precondiciones, procesamiento, persistencia, respuesta y presentación.

Verifica especialmente que «Ruta finalizada» y «Completar servicio» no se confundan si representan operaciones distintas.

No introduzcas operación offline, sincronización diferida o colas de ubicación.

8. FASE 4 — AUDITORÍA DE POSTGRESQL

Inspecciona:

•	Modelo de datos.

•	Entidades.

•	Relaciones.

•	Claves primarias y foráneas.

•	Restricciones.

•	Índices.

•	Migraciones Flyway.

•	Compatibilidad de tipos.

•	Integridad referencial.

•	Operaciones transaccionales.

•	Consultas SQL y JPQL.

•	Manejo de concurrencia.

•	Bloqueos.

•	Consultas potencialmente costosas.

•	Aislamiento entre organizaciones.

•	Manejo de errores de persistencia.

Realiza pruebas de integración con una base aislada, desechable y con datos sintéticos cuando esté disponible.

Nunca ejecutes scripts destructivos sobre bases compartidas o de producción.

No modifiques migraciones históricas ya aplicadas; incorpora nuevas migraciones cuando corresponda.

9. FASE 5 — AUDITORÍA DE SEGURIDAD

Verifica controles efectivos de:

•	Autenticación.

•	JWT y expiración.

•	Refresh tokens y revocación.

•	Autorización por roles.

•	Permisos por organización y recurso.

•	Validación de entradas.

•	SQL parametrizado.

•	Gestión de contraseñas.

•	Protección de credenciales.

•	CORS.

•	Sesiones.

•	WebSocket.

•	Gestión de excepciones.

•	Exposición de información sensible.

•	Registros técnicos.

•	Dependencias vulnerables.

•	Privacidad de ubicaciones GPS.

Realiza análisis estático y comprobaciones de seguridad autorizadas.

Utiliza entornos de prueba para validaciones dinámicas.

No realices pruebas intrusivas contra servicios externos, entornos compartidos o infraestructura no autorizada.

Verifica expresamente que el CRM no pueda consultar ubicaciones pertenecientes a otras organizaciones.

Verifica también que el backend rechace transmisiones GPS sin autorización vigente o servicio activo.

10. FASE 6 — METODOLOGÍA TDD OBLIGATORIA

Para cada defecto funcional confirmado que pueda resolverse mediante pruebas automatizadas, aplica el ciclo auténtico:

RED → GREEN → REFACTOR.

No generes las tres fases retrospectivamente después de haber implementado la solución.

RED — PRUEBA QUE FALLA

1.	Identifica un comportamiento incorrecto o ausente.

2.	Define la expectativa funcional.

3.	Escribe la prueba automatizada antes de corregir el código productivo.

4.	Ejecuta la prueba contra el comportamiento original.

5.	Comprueba que falle exactamente por el defecto identificado.

6.	Conserva el resultado de ejecución.

7.	Registra el archivo, método, comando, fecha, fallo y revisión del código.

Una prueba que falla por dependencias ausentes, error de compilación o mala configuración no demuestra por sí sola RED funcional.

GREEN — IMPLEMENTACIÓN MÍNIMA

1.	Conserva la misma prueba.

2.	Implementa únicamente lo necesario para satisfacerla.

3.	Ejecuta nuevamente la prueba.

4.	Comprueba que ahora apruebe.

5.	Ejecuta las pruebas directamente relacionadas.

6.	Verifica que no se introduzcan defectos evidentes.

7.	Conserva resultados y diferencias de código.

No debilites las aserciones para conseguir GREEN.

REFACTOR — MEJORA INTERNA

1.	Examina duplicación, claridad, acoplamiento y responsabilidades.

2.	Refactoriza solamente cuando exista una mejora justificada.

3.	Mantén el contrato externo.

4.	No modifiques la expectativa funcional de la prueba.

5.	Ejecuta otra vez la misma prueba.

6.	Ejecuta pruebas de regresión relacionadas.

7.	Registra las diferencias entre GREEN y REFACTOR.

Si no se necesita una refactorización, registra esa decisión explícitamente y demuestra que la fase de revisión se realizó.

EVIDENCIA OBLIGATORIA POR CICLO

Cada ciclo TDD debe quedar identificado con un código único:

TDD-001, TDD-002, TDD-003, etcétera.

Conserva:

•	Requisito o defecto.

•	Prueba automatizada.

•	Estado original.

•	Resultado RED.

•	Cambio mínimo.

•	Resultado GREEN.

•	Revisión REFACTOR.

•	Resultado de regresión.

•	Archivos modificados.

•	Comandos ejecutados.

•	Fechas y revisiones Git.

•	Resultado final y limitaciones.

Conserva los registros auténticos de ejecución mediante formatos existentes como JUnit XML, logs de pruebas y reportes JSON cuando estén disponibles.

No alteres resultados históricos ni fabriques capturas de pruebas.

Para correcciones exclusivamente documentales o de configuración, utiliza el método de validación más apropiado. No fuerces pruebas unitarias artificiales.

11. FASE 7 — PRUEBAS DE SOFTWARE

Ejecuta las siguientes categorías según la tecnología, infraestructura y alcance disponibles.

PRUEBAS UNITARIAS

Comprueba métodos, servicios, reglas de negocio, transformaciones, validaciones y componentes aislados.

PRUEBAS DE INTEGRACIÓN

Comprueba relaciones entre controladores, servicios, repositorios, base de datos y consumidores de APIs.

PRUEBAS DE CONTRATO

Comprueba compatibilidad de solicitudes, respuestas, errores, DTO y estados entre Angular, Flutter y Spring Boot.

PRUEBAS FUNCIONALES

Comprueba los recorridos principales de cada módulo con resultados esperados y casos negativos.

PRUEBAS END-TO-END

Ejecuta recorridos completos cuando existan herramientas, dispositivos o entornos seguros disponibles.

PRUEBAS DE REGRESIÓN

Vuelve a ejecutar las suites pertinentes después de cada cambio para detectar comportamientos afectados.

PRUEBAS DE SEGURIDAD

Comprueba acceso indebido, separación por organizaciones, permisos, sesiones y protección de información.

PRUEBAS DE RENDIMIENTO

Evalúa tiempos de respuesta, consultas, memoria y operaciones críticas utilizando mediciones reproducibles.

No realices pruebas de carga sobre infraestructura compartida sin autorización.

PRUEBAS DE USABILIDAD

Comprueba navegación, formularios, mensajes de error, estados de carga y coherencia de las interfaces.

En Mapa en vivo, revisa desplazamiento, zoom, selección de marcadores, filtros y actualización visual.

PRUEBAS DE RESILIENCIA

Comprueba el comportamiento ante fallos de conectividad, permisos denegados, respuestas HTTP incorrectas y servicios temporalmente no disponibles.

Estas pruebas no autorizan implementar operación offline.

12. FASE 8 — HERRAMIENTAS Y MÉTRICAS

Identifica y utiliza las herramientas que ya estén disponibles en el proyecto.

Para el backend considera:

•	Maven.

•	JUnit.

•	Mockito.

•	Spring Boot Test.

•	JaCoCo.

•	Pruebas de integración PostgreSQL, cuando exista un entorno aislado.

Para Angular considera:

•	TypeScript.

•	Analizadores estáticos.

•	Vitest u otro runner configurado.

•	Compilación.

•	Pruebas de componentes y servicios.

•	Pruebas E2E existentes, cuando estén disponibles.

Para Flutter considera:

•	flutter analyze.

•	flutter test.

•	Pruebas de widgets.

•	Pruebas de repositorios y servicios.

•	Pruebas de integración.

•	Validación Android cuando exista dispositivo o emulador.

No sustituyas las herramientas actuales sin una necesidad demostrada.

Registra:

•	Número de pruebas identificadas.

•	Número de pruebas ejecutadas.

•	Pruebas aprobadas.

•	Pruebas fallidas.

•	Pruebas omitidas.

•	Pruebas bloqueadas.

•	Cobertura de líneas.

•	Cobertura de ramas.

•	Defectos detectados.

•	Defectos corregidos.

•	Regresiones.

•	Riesgos residuales.

Diferencia expresamente:

PRUEBAS APROBADAS ≠ COBERTURA TOTAL.

COBERTURA JACOCO ≠ EVIDENCIA DE TDD.

COMPILACIÓN CORRECTA ≠ FUNCIONALIDAD COMPLETAMENTE VALIDADA.

13. FASE 9 — CORRECCIÓN CONTROLADA

Después de disponer de una matriz de hallazgos, corrige los problemas confirmados por prioridad:

P0 — Defectos críticos de seguridad, integridad o funcionamiento que impiden continuar.

P1 — Errores funcionales relevantes, incumplimientos de contratos y fallas graves de integración.

P2 — Defectos de calidad, mantenibilidad o rendimiento con impacto comprobado.

P3 — Mejoras menores justificadas y dentro del alcance.

Para cada corrección:

1.	Comprueba la causa raíz.

2.	Determina el impacto.

3.	Diseña una solución mínima.

4.	Aplica TDD cuando corresponda.

5.	Implementa.

6.	Ejecuta pruebas.

7.	Revisa las dependencias afectadas.

8.	Actualiza comentarios y documentación.

9.	Solicita revisión independiente cuando esté disponible.

10.	Registra el resultado.

No introduzcas funcionalidades nuevas que no sean necesarias para resolver un defecto confirmado.

No cambies contratos públicos sin revisar sus consumidores y verificar compatibilidad.

14. FASE 10 — DOCUMENTACIÓN Y COMENTARIOS

Todas las correcciones deben contar con documentación técnica proporcional a su complejidad.

Documenta:

•	Problema original.

•	Causa raíz.

•	Decisión técnica.

•	Archivos afectados.

•	Contratos modificados.

•	Reglas de negocio involucradas.

•	Pruebas realizadas.

•	Resultados RED, GREEN y REFACTOR.

•	Riesgos y limitaciones.

•	Procedimiento de reproducción.

•	Recomendaciones pendientes.

Comenta el código donde existan decisiones no evidentes, restricciones técnicas, invariantes, contratos complejos o reglas de negocio importantes.

No agregues comentarios redundantes a cada línea.

Evita comentarios que describan comportamientos inexistentes o que contradigan el código.

Utiliza documentación Markdown estructurada en una ubicación apropiada del repositorio, sin sobrescribir los informes o evidencias anteriores.

Mantén un registro de avance persistente con:

•	Fase actual.

•	Actividades completadas.

•	Actividades pendientes.

•	Hallazgos abiertos.

•	Última prueba ejecutada.

•	Último resultado comprobado.

•	Siguiente acción.

•	Bloqueos.

Este registro debe permitir continuar el trabajo después de una interrupción sin perder trazabilidad.

No incluyas secretos, tokens, contraseñas, datos personales reales ni coordenadas sensibles en los registros.

15. FASE 11 — VALIDACIÓN FINAL INDEPENDIENTE

Una vez realizadas las correcciones:

1.	Ejecuta nuevamente la compilación de cada componente.

2.	Ejecuta las pruebas unitarias pertinentes.

3.	Ejecuta las pruebas de integración disponibles.

4.	Comprueba los contratos entre plataformas.

5.	Verifica las funcionalidades principales.

6.	Comprueba los controles de seguridad.

7.	Revisa los resultados de cobertura.

8.	Comprueba la integridad de las migraciones.

9.	Revisa los problemas restantes del workspace.

10.	Verifica los cambios mediante Git diff.

11.	Detecta modificaciones ajenas al alcance.

12.	Comprueba que los comentarios y documentos coincidan con el código.

13.	Confirma que no se hayan eliminado pruebas para ocultar errores.

14.	Registra las validaciones que no pudieron realizarse.

Cuando exista una revisión independiente disponible, el responsable de esa revisión no debe ser el mismo agente que implementó la corrección.

16. CRITERIOS DE FINALIZACIÓN DEL GOAL

No declares completada la auditoría hasta que:

1.	Todos los módulos identificados estén incluidos en el inventario de auditoría.

2.	Se haya revisado la arquitectura general.

3.	Se hayan contrastado los contratos entre CRM, backend y Flutter.

4.	Se hayan evaluado las conexiones y la persistencia.

5.	Se haya elaborado la matriz de trazabilidad funcional.

6.	Los defectos críticos reproducibles estén corregidos o documentados como bloqueados.

7.	Las correcciones funcionales aplicables cuenten con evidencia TDD auténtica.

8.	Las pruebas pertinentes hayan sido ejecutadas.

9.	Las regresiones detectadas estén corregidas o identificadas como bloqueos.

10.	Se haya documentado la cobertura realmente obtenida.

11.	Se haya comprobado la integridad de los archivos modificados.

12.	Se hayan identificado los riesgos residuales.

13.	No existan declaraciones de éxito sin evidencia.

14.	Se haya actualizado el registro final de auditoría.

No exijas porcentajes de cobertura inventados ni declares cumplimiento del 100 % si existen pruebas no ejecutadas.

La condición de cierre será:

AUDITORÍA COMPLETA DEL ALCANCE ACCESIBLE, CORRECCIONES AUTORIZADAS VERIFICADAS, PRUEBAS EJECUTADAS CON RESULTADOS TRAZABLES Y LIMITACIONES EXPRESAMENTE DOCUMENTADAS.

17. RESTRICCIONES INNEGOCIABLES

Preserva:

•	Arquitectura existente salvo cambios necesarios y justificados.

•	Separación entre CRM, móvil y backend.

•	Roles y permisos.

•	Aislamiento entre organizaciones.

•	Contratos funcionales vigentes.

•	Integridad de PostgreSQL.

•	Privacidad GPS.

•	Funcionalidades operativas.

•	Diseño visual aprobado.

•	Operación exclusivamente conectada.

NO:

•	Inventes pruebas ejecutadas.

•	Fabriques resultados RED.

•	Manipules métricas de cobertura.

•	Elimines código para ocultar fallos.

•	Desactives globalmente analizadores.

•	Modifiques evidencias históricas.

•	Ejecutes operaciones destructivas sobre datos compartidos.

•	Expongas credenciales.

•	Instales dependencias innecesarias.

•	Actives servicios comerciales.

•	Realices despliegues productivos.

•	Hagas push ni publiques cambios sin autorización explícita.

•	Confundas pruebas planificadas con pruebas aprobadas.

18. INFORME FINAL

Al concluir presenta:

A. ESTADO GENERAL DE RUTA FIJA

Arquitectura, componentes auditados y alcance real de la revisión.

B. MATRIZ DE HALLAZGOS

Defectos, gravedad, causa raíz, archivos y estado de corrección.

C. CONTRATOS E INTEGRACIONES

Operaciones verificadas entre Angular, Spring Boot, Flutter y PostgreSQL.

D. FUNCIONALIDADES

Módulos comprobados, resultados y limitaciones.

E. TDD

Relación de ciclos RED → GREEN → REFACTOR con evidencias auténticas.

F. PRUEBAS DE SOFTWARE

Comandos, resultados, cobertura, regresiones y pruebas bloqueadas.

G. SEGURIDAD

Controles examinados, defectos corregidos y riesgos residuales.

H. CAMBIOS REALIZADOS

Archivos modificados, justificación y resultados de revisión.

I. ESTADO FINAL

Clasifica el resultado como:

•	COMPLETADO Y VERIFICADO.

•	COMPLETADO PARCIALMENTE CON BLOQUEOS.

•	NO COMPLETADO.

La clasificación debe corresponder a las evidencias reales.

19. INSTRUCCIÓN DE INICIO

INICIA AHORA.

PRIMERO EJECUTA LA FASE 0 Y ESTABLECE LA LÍNEA BASE.

DESPUÉS CONTINÚA CON LA AUDITORÍA INTEGRAL Y LAS CORRECCIONES AUTORIZADAS, RESPETANDO LAS DEPENDENCIAS ENTRE FASES.

UTILIZA AGENTES CUANDO ESTÉN DISPONIBLES.

REALIZA PRUEBAS AUTÉNTICAS.

APLICA TDD RED → GREEN → REFACTOR DONDE CORRESPONDA.

DOCUMENTA LAS DECISIONES, LAS PRUEBAS Y LOS RESULTADOS.

NO TE DETENGAS TRAS EL PLAN INICIAL NI DESPUÉS DE PRESENTAR INFORMES INTERMEDIOS.

CONTINÚA HASTA QUE EL OBJETIVO ESTÉ VERIFICADO O HASTA IDENTIFICAR UN BLOQUEO REAL QUE IMPIDA AVANZAR CON SEGURIDAD.
