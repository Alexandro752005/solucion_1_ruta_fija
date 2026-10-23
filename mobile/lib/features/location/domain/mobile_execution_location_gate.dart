import 'mobile_foreground_location.dart';

/// Reads OS state immediately before an online start. Never captures or stores a pre-service point.
final class MobileExecutionLocationGate {
  const MobileExecutionLocationGate({
    required this.device,
    required this.isForeground,
  });

  final MobileDeviceLocationGateway device;
  final bool Function() isForeground;

  Future<Map<String, Object>> beforeStart() async {
    if (!isForeground()) {
      throw const MobileDeviceLocationFailure(
        'Abra Ruta Fija en primer plano para iniciar el servicio.',
      );
    }
    if (!await device.isLocationServiceEnabled()) {
      throw const MobileDeviceLocationFailure(
        'Active la ubicación del dispositivo antes de iniciar.',
      );
    }
    var permission = await device.checkPermission();
    if (permission == MobileDeviceLocationPermission.denied) {
      permission = await device.requestPermission();
    }
    if (!permission.grantsForeground ||
        !isForeground() ||
        !await device.isLocationServiceEnabled() ||
        !isForeground()) {
      throw const MobileDeviceLocationFailure(
        'Iniciar requiere permiso de ubicación con Ruta Fija abierta. Puede seguir consultando su cuenta.',
      );
    }
    return {
      'permissionGranted': true,
      'locationServiceEnabled': true,
      'foreground': true,
    };
  }
}
