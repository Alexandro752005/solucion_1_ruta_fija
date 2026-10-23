# Agentes de Ruta Fija

Esta carpeta es la fuente local de perfiles y reglas de colaboración para Ruta Fija. Los perfiles son instrucciones reutilizables para agentes o sesiones disponibles en el entorno; crear estos archivos no instala agentes permanentes ni agrega servicios pagados. El coordinador asigna cada tarea explícitamente y verifica sus entregas.

## Reglas comunes

1. `specs/registro.json` v3.1 ya está integrado y es la fuente de decisiones. Preservar IDs, respuestas y antecedentes; no convertir diseños, código exportado o propuestas en política.
2. El producto tiene tres roles activos: `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. Usar solo esos nombres en código y documentación operativa nueva.
3. Mantener el monolito modular, API compartida, autorización en servidor y aislamiento por empresa/objeto.
4. La versión actual del alcance excluye operación y colas offline. Android confirma una acción solo cuando el servidor la confirmó; recuperación de una respuesta perdida consulta el estado existente.
5. No nuevos desembolsos, servicios pagados, infraestructura propia de agentes, secretos en mensajes ni envío externo de correo sin autorización explícita.
6. No ejecutar comandos de Git ni interactuar con GitHub. El trabajo es local. No asumir ramas ni worktrees.
7. El workspace es compartido. Todo agente recibe límites de archivos. Un solo escritor modifica registro, contratos API compartidos, configuración/dependencias comunes y numeración Flyway; los demás entregan propuestas.
8. No modificar bases productivas. Ejecutar pruebas solo con el entorno y base local de pruebas confirmados. No afirmar resultados que no se ejecutaron.
9. Toda tarea de implementación requiere reglas/AC, precondiciones, dueño de datos, estados, permisos, excepciones, contrato API y aceptación definidos. QA revisa trabajo que no escribió.

## Perfiles

| Perfil | Archivo | Superficie |
|---|---|---|
| A-COORD | [A-COORD.md](A-COORD.md) | Coordinación, registro, contratos e integración local |
| A-BACK | [A-BACK.md](A-BACK.md) | Spring Boot, PostgreSQL, JPA y Flyway |
| A-WEB | [A-WEB.md](A-WEB.md) | CRM Angular para `SUPER_ADMIN` y `ADMIN` |
| A-MOB | [A-MOB.md](A-MOB.md) | Android Flutter para `CONDUCTOR` |
| A-QA | [A-QA.md](A-QA.md) | Revisión independiente, seguridad y evidencia |
| A-OPS | [A-OPS.md](A-OPS.md) | Entorno, S/0, recuperación y entrega local |

## Uso

El coordinador entrega al agente el ID de lote/historia, el contrato local y los IDs de reglas/aceptación. El agente debe leer este README y su perfil, además de instrucciones de la superficie asignada. En la primera etapa de L-00 los perfiles backend, CRM y Android hacen inspección estática de solo lectura; el coordinador es dueño de los archivos comunes y ejecuta la baseline secuencialmente.
