# SUPER_ADMIN — Operación y custodia para cerrar L-01

Estado: requisitos de revisión y **propuesta de custodia pendiente de decisión del titular**. No contiene claves, no inscribe dispositivos ni modifica el protocolo del backend.

## 1. Decisiones conservadas

La recuperación exige el código de emergencia y una prueba independiente con la clave previamente inscrita. También verifica el nuevo correo y cambia la contraseña. Al completarse invalida las sesiones y credenciales temporales pertinentes, consume el código anterior y muestra una sola vez otro código de emergencia. La clave pública inscrita se conserva. ADMIN no puede autorizar la recuperación.

El informe recibido indica que el backend ya usa Ed25519 en Java 21. No se propone cambiar de algoritmo ni construir otro protocolo para reemplazarlo.

## 2. Custodia propuesta

| Elemento | Quién y dónde | Condición |
|---|---|---|
| Clave pública inscrita y su huella | Backend y registro de inscripción | Puede usarse para verificar firmas; no es una contraseña ni otorga por sí sola recuperación. Vincularla a titular/instalación según contrato. |
| Clave privada de firma | Equipo confiable del titular, en archivo cifrado fuera de repositorio y servidor | No se carga en la API, `.env`, evidencia, chat ni archivos temporales públicos. Acceso local restringido. |
| Copia de clave privada | Copia cifrada separada y comprobada | Medio existente sin gasto adicional. Confirmar que puede abrirse y usarse antes de confiar en ella. |
| Contraseña de descifrado | Custodia del titular distinta del archivo cifrado | No almacenarla junto a la copia ni enviarla al backend. |
| Código de emergencia | Ubicación independiente de la clave privada y de su contraseña | No guardar ambos secretos en el mismo archivo, ZIP, mensaje o carpeta compartida. El backend debe verificarlo sin conservarlo recuperable en claro. |
| App Password de Gmail | Configuración privada nativa del emisor | Es una credencial de correo, no una prueba de titularidad de SUPER_ADMIN. |

El esquema aporta separación de medios de recuperación; no se presenta como certificación de autenticación multifactor resistente al compromiso de todo el equipo. Si se comprometen conjuntamente clave y código, la recuperación puede ser comprometida. La política de pérdida simultánea o compromiso se debe decidir sin añadir una puerta trasera de soporte.

## 3. Inscripción previa

1. El titular entra por un acceso legítimo SUPER_ADMIN y se reautentica según el contrato. La inscripción inicial no puede hacerse desde un flujo de recuperación que todavía no ha acreditado titularidad.
2. El cliente genera localmente el par de claves mediante una biblioteca estándar. Exporta solamente la clave pública en el formato admitido por la API.
3. Si el protocolo contempla una prueba de posesión durante la inscripción, debe completarla. Revisar la política de sustitución de una clave ya inscrita; no permitir sobreescritura silenciosa con una sesión robada.
4. El titular conserva la clave privada cifrada, prepara la copia y guarda el código de emergencia por separado.
5. La evidencia de inscripción registra identificadores permitidos, huella de clave pública, fecha, resultado y entorno. Nunca contiene la privada ni el código.
6. Antes de dar por lista la recuperación, realizar una prueba de lectura/uso de la copia en un entorno autorizado. No rotar la clave real ni consumir el código del titular solo para probar un script.

La inscripción necesita hacerse antes de perder el acceso. Si hoy la cuenta no dispone de clave inscrita y ya está bloqueada, no debe aceptarse una clave nueva enviada por el solicitante como prueba de control previo.

## 4. Contrato que el cliente debe respetar

Antes de programarlo, leer `docs/contratos/L-01.md` y `docs/evidencia/L-01/recuperacion-superadmin-protocolo.md` actuales. Revisar además controladores/DTO y tests cuando exista una discrepancia.

| Aspecto | Comprobación necesaria |
|---|---|
| Operaciones de la API | Rutas, métodos, payloads, autorización y estados reales. No usar rutas deducidas de conversaciones antiguas. |
| Identificador del reto | El que devuelve el backend coincide con el persistido y se utiliza en validación y contabilización. |
| Datos firmados | Propósito, titular/instalación, correo nuevo y demás vínculos exigidos por el contrato. Ni el cliente ni el atacante deben poder reasignar una firma válida. |
| Representación | Bytes exactos, codificación, algoritmo, formatos de clave y firma. No firmar una reconstrucción JSON distinta del mensaje que valida el servidor. |
| Vida del reto | Caducidad, límites de intentos y estado consumido/cancelado; un solo éxito incluso con peticiones concurrentes. |
| Verificación del correo | Canal nuevo verificado y unido a esa misma recuperación; no sustituye la firma o el código de emergencia. |
| Confirmación | Cambio de correo y contraseña, consumo/rotación de código y revocación atómicos según contrato, con tratamiento seguro del fallo. |

No se fija aquí un TTL o número de intentos de retos que no se recibió del contrato vigente. Los límites OTP de v3 sí siguen aplicándose a la verificación de correo.

## 5. Cliente operativo mínimo

Una utilidad local puede ser suficiente para el piloto; no hace falta exponer la clave privada en el CRM. Debe permitir generar/proteger claves, preparar inscripción, mostrar los datos del reto para revisión y firmarlo localmente. La API decide la aceptación de la prueba; la utilidad nunca decide que el usuario ya recuperó acceso.

Requisitos prácticos:

- Reutilizar Java 21 o bibliotecas estándar disponibles; ninguna nueva suscripción o infraestructura de agentes.
- Recibir la contraseña de descifrado por entrada protegida y evitar secretos en argumentos del proceso, historial del terminal, portapapeles automático o logs.
- Escribir la privada en una ubicación explícita fuera del repositorio con permisos locales apropiados; no usar un cifrado diseñado ad hoc.
- Mostrar propósito, cuenta/instalación, destino y caducidad del reto antes de firmar. Rechazar o advertir incoherencias según contrato.
- Validar que el esquema admitido es Ed25519 y que el material/formatos corresponden al contrato. No degradar algoritmos para conseguir que la prueba pase.
- No imprimir ni guardar la contraseña nueva o el código de emergencia en un transcript de prueba. El nuevo código se entrega al titular una sola vez por el canal previsto y se guarda fuera de la evidencia.
- Documentar comandos con marcadores de ejemplo, no con secretos reales. Los comandos definitivos se publican después de implementar y probar la utilidad.

## 6. Ensayo de aceptación operativo

Usar primero un titular y claves de prueba, sin afectar la cuenta real:

1. Inscribir clave y verificar su huella; una cuenta ADMIN no puede hacerlo para SUPER_ADMIN.
2. Preparar una recuperación simulando pérdida de correo y contraseña anteriores. No exigir esos medios perdidos como requisito del recorrido de emergencia.
3. Obtener el reto, comprobar sus datos y firmarlo localmente con la clave ya inscrita.
4. Presentar firma y código de emergencia por los canales previstos; verificar el correo nuevo y establecer nueva contraseña.
5. Completar una sola vez; registrar que el código anterior deja de servir y que la clave pública conserva su huella.
6. Comprobar que access y refresh tokens anteriores son rechazados, y que las nuevas credenciales funcionan con el flujo autorizado.
7. Comprobar rechazo de firma alterada, reto caducado/reutilizado o para otro contexto, código incorrecto y doble consumo concurrente; usar correo simulado para la batería automatizada.
8. Ensayar el cliente con una copia cifrada de la clave de prueba. Registrar resultado y procedimiento, sin el material secreto.

La prueba real del proveedor SMTP se registra por separado. No hace falta enviar correo real en cada caso negativo de recuperación.

## 7. Después de recuperar o ante una pérdida

- Recuperación normal: conservar la clave inscrita y sustituir únicamente el código de emergencia consumido, además del correo/contraseña acordados. No mezclarla con rotación de claves.
- Sospecha de clave comprometida: procedimiento separado de revocación/rotación con autenticación reforzada y evidencia; no basta con recuperar usando precisamente la clave sospechosa.
- Pérdida de una copia: recuperar desde una copia legítima y comprobar el inventario de custodia.
- Pérdida de todos los medios: informar que falta evidencia; no habilitar recuperación por un ADMIN, una contraseña de base de datos, una captura de pantalla o el correo nuevo aislado. Cualquier procedimiento excepcional requerirá diseño y autorización expresa antes de existir.

## Referencias

[OWASP Key Management](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html) y [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), consultados el 2026-10-14. Orientan los controles; la elección de Ed25519, el cambio de contraseña y la conservación de la clave proceden del proyecto y sus decisiones, no de una afirmación de que OWASP prescribe este diseño concreto.
