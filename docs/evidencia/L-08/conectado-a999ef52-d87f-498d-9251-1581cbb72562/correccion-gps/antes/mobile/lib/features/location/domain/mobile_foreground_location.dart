/// A single point captured while the conductor is actively using the app.
/// It is deliberately not a stream, a route, or a persisted local history.
final class MobileCurrentLocation {
  const MobileCurrentLocation({
    required this.latitude,
    required this.longitude,
    required this.accuracyMeters,
    required this.capturedAt,
  });

  final double latitude;
  final double longitude;
  final double accuracyMeters;
  final DateTime capturedAt;

  Map<String, Object> toRequestBody() {
    return {
      'latitude': latitude,
      'longitude': longitude,
      'accuracyM': accuracyMeters,
      'capturedAt': capturedAt.toUtc().toIso8601String(),
      'permissionGranted': true,
      'locationServiceEnabled': true,
      'foreground': true,
    };
  }
}

enum MobileDeviceLocationPermission {
  denied,
  deniedForever,
  whileInUse,
  always;

  bool get grantsForeground {
    return this == whileInUse || this == always;
  }
}

abstract interface class MobileDeviceLocationGateway {
  Future<bool> isLocationServiceEnabled();

  Future<MobileDeviceLocationPermission> checkPermission();

  Future<MobileDeviceLocationPermission> requestPermission();

  Future<MobileCurrentLocation> currentPosition();

  Future<bool> openLocationSettings();

  Future<bool> openAppSettings();
}

enum MobileDeviceLocationFailureKind { timeout, provider, invalidAccuracy, unknown }

final class MobileDeviceLocationFailure implements Exception {
  const MobileDeviceLocationFailure(
    this.message, {
    this.kind = MobileDeviceLocationFailureKind.unknown,
  });

  final String message;
  final MobileDeviceLocationFailureKind kind;

  @override
  String toString() => 'MobileDeviceLocationFailure(${kind.name})';
}
