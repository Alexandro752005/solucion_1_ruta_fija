import '../network/mobile_api_exception.dart';

String mobileErrorMessage(Object error) {
  if (error is MobileNetworkException) {
    return error.message;
  }
  if (error is MobileApiException) {
    switch (error.code) {
      case 'AUTH_INVALID_CREDENTIALS':
        return 'El correo o la contraseña no son correctos.';
      case 'MOBILE_USER_NOT_DRIVER':
        return 'Esta cuenta no está vinculada a un conductor activo.';
      case 'DRIVER_INACTIVE':
        return 'El conductor está inactivo. Comuníquese con un ADMIN.';
      case 'RATE_LIMIT_EXCEEDED':
        return 'Demasiados intentos. Espere un momento antes de volver a intentarlo.';
      case 'AVAILABILITY_INVALID_TRANSITION':
        return 'Ese cambio no está permitido por el estado operativo actual.';
      default:
        return error.message;
    }
  }
  if (error is FormatException) {
    return 'La respuesta de Ruta Fija no pudo validarse. Inténtelo nuevamente.';
  }
  return 'No fue posible completar la operación. Inténtelo nuevamente.';
}
