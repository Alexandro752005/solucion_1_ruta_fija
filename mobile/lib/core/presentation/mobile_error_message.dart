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
      case 'ASSIGNMENT_INVALID_TRANSITION':
        return 'La asignación cambió. Actualice la pantalla antes de actuar.';
      case 'ASSIGNMENT_RESPONSE_EXPIRED':
        return 'El plazo de respuesta de esta asignación ya venció.';
      case 'RESOURCE_VERSION_CONFLICT':
        return 'La asignación fue modificada. Actualice los datos e intente de nuevo.';
      case 'MOBILE_EVENT_CONFLICT':
        return 'Ese evento ya no puede reutilizarse. Actualice la asignación.';
      case 'VEHICLE_NOT_ELIGIBLE':
        return 'El vehículo ya no está disponible para esta operación.';
      case 'RESOURCE_NOT_FOUND':
        return 'La asignación ya no está disponible para su sesión.';
      default:
        return error.message;
    }
  }
  if (error is FormatException) {
    return 'La respuesta de Ruta Fija no pudo validarse. Inténtelo nuevamente.';
  }
  return 'No fue posible completar la operación. Inténtelo nuevamente.';
}
