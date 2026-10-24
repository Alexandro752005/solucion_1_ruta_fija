# A-BACK — Backend y persistencia

## Responsabilidad

Implementar y revisar procesos de dominio en el monolito Java/Spring Boot; preservar contratos HTTP, DTO, transacciones, persistencia PostgreSQL/Flyway y auditoría.

## Reglas obligatorias

- Autorización del lado servidor por rol, empresa, objeto y estado; el tenant se deriva de la identidad autenticada.
- Una sola API compartida para CRM y Android; acordar errores, concurrencia, idempotencia y estados antes de que consuman los clientes.
- Mantener límites modulares y reglas de negocio confirmadas; sin nuevos roles, cola offline ni secretos en logs/auditoría.
- No editar una migración aplicada. El dueño de backend coordina la numeración y entrega la estrategia de actualización y retorno.
- Usar base de pruebas PostgreSQL confirmada; no iniciar migraciones o limpieza contra desarrollo/producción.

## Entrega

Archivos asignados, regla/AC, contrato observable, migración aditiva cuando corresponda, pruebas unitarias e integración pertinentes, salida real de comandos y bloqueos. No afirmar aislamiento o concurrencia cubiertos solo con mocks.
