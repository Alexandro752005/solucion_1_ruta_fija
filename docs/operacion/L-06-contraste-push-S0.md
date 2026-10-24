# L-06 — Contraste documental de canal push S/0

2026-10-20, consulta de documentación oficial. Estado: **VÍA DOCUMENTAL IDENTIFICADA; SIN CONFIGURACIÓN O ENTREGA REAL ACREDITADA**. Q-074 condiciona los intentos push a comprobar vía sin gasto y límites. No selecciona un proveedor operativo por inferencia ni autoriza tráfico a destinatarios.

| Aspecto | Hallazgo y fuente primaria | Límite para Ruta Fija |
|---|---|---|
| Coste | FCM figura entre los productos sin coste; Spark se ofrece sin método de pago. [Precios oficiales](https://firebase.google.com/pricing). | No se usan créditos temporales, Blaze ni otros productos de pago. La disponibilidad comercial de FCM no acredita un proyecto Ruta Fija configurado. |
| Cuotas y fallo | La API aplica cuotas por proyecto y puede responder429 por cuota o sobrecarga. [Cuotas oficiales](https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas). | Comprobar la cuota efectiva del proyecto antes de activarlo; no inferir un número contratado ni prometer entrega. Resultado externo separado de bandeja/lectura, fallo visible y sin reintento ciego. |
| Autenticación | HTTP v1 requiere credenciales del proyecto y autorización OAuth derivada de una cuenta de servicio. [Envío HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api). | No se acreditaron proyecto/credenciales propios. No crear ni leer una clave por inferencia; nunca incluirla en código, argumentos, chat o evidencia. Las credenciales SMTP no sirven para FCM. |
| Cliente y datos | El cliente depende de Servicios de Google Play y la inscripción transmite identificador/configuración a Firebase. Puede deshabilitarse la inicialización automática. [Configuración Android](https://firebase.google.com/docs/cloud-messaging/android/get-started), [dependencias de Play Services](https://firebase.google.com/docs/android/android-play-services). | Falta preparación real de dispositivo/privacidad y validación física L-08. No activar inscripción automática ni añadir Analytics por conveniencia del diagnóstico. |

## Aplicación del contrato vigente

Preparar bandeja propia persistida y un contrato de adaptador separado. Con proveedor no preparado, registrar **no intentado / no configurado**, visible para ADMIN; nunca anunciar entrega push. Las pruebas automáticas usarán dobles y podrán demostrar persistencia, rechazo, fallo y ausencia de bloqueo de servicios. No acreditarán recepción real. La bandeja permanece disponible aunque falte el canal externo.

Antes de activar un proveedor se necesita instalación propia preparada, cuota efectiva comprobada, credenciales privadas, registro autorizado del dispositivo, protección/finalidad de identificadores y ensayo de entrega/fallo autorizado. No se realizan esos actos mediante este documento. Se mantiene S/0, sin correo operativo, servicios contratados, despliegue o habilitación del piloto.
