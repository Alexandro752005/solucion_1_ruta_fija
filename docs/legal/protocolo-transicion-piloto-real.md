# Protocolo vinculante de transición del candidato local al piloto real

Versión 1.0.0. 2026-10-23, America/Lima. Adoptado por la instrucción maestra de cierre local L-08. **Estado: protocolo preparado; piloto no habilitado.** Este anexo gobierna la decisión de encendido del proyecto; no constituye una certificación jurídica ni una aceptación prestada por participantes.

La aprobación `GO PARA CANDIDATO LOCAL DE ENTREGA` permite archivar, revisar y sustentar la implementación con datos sintéticos. No acredita disponibilidad 24/7, custodia independiente, pérdida física de señal GPS, consentimiento personal, firma del titular ni infraestructura pública. Los requisitos originales de la matriz 0.8.0 siguen siendo condiciones de campo, conservados en su antecedente. El cambio de alcance al candidato local se registra expresamente en el contrato 0.9.0.

## Condiciones previas obligatorias

| Gate | Responsable funcional | Evidencia requerida antes del encendido | Estado actual |
|---|---|---|---|
| PR-01 Identificación y privacidad | Titular y responsable efectivo del tratamiento | Identidad/contacto reales, finalidades, destinatarios, ubicación y proveedores, información a participantes, conservación/retiro por categoría y copias, canal efectivo; documento completo revisado y aprobado | PENDIENTE_REAL |
| PR-02 ADMIN legítimo | SUPER_ADMIN y cada ADMIN propio | Organización y representante legítimos, cuenta activa/correo verificado, validación inicial y aceptación personal del texto publicado y su huella | PENDIENTE_REAL |
| PR-03 Recuperación del titular | Titular SUPER_ADMIN | Ceremonia Ed25519, privada cifrada en USB extraíble fuera de servidor/repositorio, copia independiente, código separado y ensayo real con revocación verificada | PENDIENTE_REAL |
| PR-04 HTTPS y distribución | Responsable técnico y titular del dominio | Dominio existente/controlado, DNS, cadena TLS válida/renovación, URL API definitiva, versión Android firmada y política de actualización/custodia | PENDIENTE_REAL |
| PR-05 Continuidad | Responsable operativo designado | Host de monitor independiente, alertas entregadas, responsables y suplentes 24/7, respaldo horario independiente, verificación diaria y recuperación representativa cronometrada | PENDIENTE_REAL |
| PR-06 Participantes y campo | ADMIN de cada organización | Personas/unidades/títulos y aplicabilidad comprobados, permisos e información GPS, ruta/terminal reales y ensayo físico no-fix | PENDIENTE_REAL |

## Aceptación personal de ADMIN

1. SUPER_ADMIN verifica la legitimidad y alcance de la organización y del ADMIN por el procedimiento aprobado; una cuenta de arnés no acredita identidad real.
2. Cada ADMIN entra con su propia cuenta, consulta el texto completo publicado de términos 1.0.0 y realiza la acción expresa. Sin casilla premarcada ni aceptación automatizada o delegada al agente. El login no equivale a aceptar.
3. El servidor registra cuenta, organización, documento, versión, SHA-256 y hora del servidor. Se verifica correspondencia con `20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417` sin alterar el documento publicado.
4. Se comprueba que rechazar/no aceptar mantiene el gate de organización y que las dos aceptaciones históricas de pruebas siguen intactas. No crear otra aceptación sintética para simular la aceptación real.

## Ceremonia personal Ed25519 en medio extraíble

Se aplica el [kit público de recuperación](../../artifacts/L-01-superadmin-titular-2026-10-17/LEEME.md). Se mantiene la separación entre la clave Ed25519 de recuperación, la firma Android del candidato local y cualquier firma técnica de archivo.

El titular confirma dos medios de custodia independientes existentes y el lugar separado del código de emergencia. Ejecuta personalmente `keygen` con contraseña oculta y guarda el contenedor cifrado fuera del servidor/repositorio. Comprueba principal y copia mediante `check-key`, compara exclusivamente huellas públicas, extrae y reconecta el USB y repite su lectura. Inscribe la pública mediante `enroll` en el entorno legítimo autorizado; el agente no conserva privada ni código.

El ensayo posterior `recover` usa la copia desconectable, un reto contextual y los canales legítimos; comprueba consumo único, revocación de sesiones/credenciales anteriores y rotación del código. Evidencia saneada: fecha, resultado, huella pública y medio genérico; sin secretos, OTP, firmas de retos, códigos, rutas privadas o documentos personales. La generación de una clave local de compilación no cumple esta ceremonia.

## HTTPS y paso desde laboratorio

El titular proporciona dominio/control DNS y entorno expresamente autorizado, sin contratar recursos por inferencia. El responsable configura terminación TLS con certificado válido para ese dominio, cadena de confianza y renovación comprobada; protege los secretos fuera de artefactos, limita la exposición de PostgreSQL y prueba HTTPS desde los clientes previstos. Se verifica SAN/hostname, vigencia, cadena, rechazo de HTTP o redirección sin credenciales y acceso público mínimo de salud sin detalles sensibles. No se desactiva la validación de certificados.

El candidato release conserva un endpoint reservado `.invalid`: evita distribuir la IP privada del laboratorio y **no ofrece un servicio conectado**. La sustitución por la URL HTTPS efectiva exige nueva compilación firmada, comprobación del certificado y prueba integrada. La APK v4 instalada en POCO mantiene su firma debug y su API de laboratorio; el candidato usa otra firma y no puede actualizarla directamente. Definir antes el alta limpia/migración y respaldo consentido, sin desinstalar ni borrar datos automáticamente.

## Decisión de encendido y reversión

Se necesita un acta posterior que identifique responsables reales, gates PR-01…06 con evidencia, binarios/huellas, infraestructura y fechas efectivas. Mientras exista un gate pendiente, `pilotEnabled=false`, `productionDeployment=false` y `NO_GO_PILOTO_REAL` siguen vigentes. No se activan plazos de conservación por la fecha de esta aprobación local. Ante fallo de gates se suspende el inicio de nuevas operaciones y se sigue el procedimiento seguro de cierre/consulta existente, sin colas offline, purga global o reapertura de casos retirados.

Los registros de pruebas y documentación técnica pueden verificarse ahora; la firma personal, disponibilidad de medios e identidad real no se sustituyen por este protocolo.
