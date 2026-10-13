import 'dart:async';

import 'package:geolocator/geolocator.dart';

import '../domain/mobile_foreground_location.dart';

/// Android adapter. It obtains one foreground point only after the UI asks for
/// it; no stream, service, notification, or background execution is created.
final class GeolocatorDeviceLocationGateway
    implements MobileDeviceLocationGateway {
  static const _captureTimeout = Duration(seconds: 15);

  @override
  Future<bool> isLocationServiceEnabled() {
    return Geolocator.isLocationServiceEnabled();
  }

  @override
  Future<MobileDeviceLocationPermission> checkPermission() async {
    return _mapPermission(await Geolocator.checkPermission());
  }

  @override
  Future<MobileDeviceLocationPermission> requestPermission() async {
    return _mapPermission(await Geolocator.requestPermission());
  }

  @override
  Future<MobileCurrentLocation> currentPosition() async {
    try {
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: _captureTimeout,
        ),
      );
      return MobileCurrentLocation(
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters: position.accuracy,
        // The server accepts only a recent capture. Mark it at the completion
        // of this one-shot acquisition rather than retaining a prior point.
        capturedAt: DateTime.now().toUtc(),
      );
    } on TimeoutException {
      throw const MobileDeviceLocationFailure(
        'No se obtuvo la ubicación a tiempo. Verifique el GPS e inténtelo nuevamente.',
      );
    } catch (_) {
      throw const MobileDeviceLocationFailure(
        'No fue posible obtener la ubicación actual del dispositivo.',
      );
    }
  }

  @override
  Future<bool> openAppSettings() {
    return Geolocator.openAppSettings();
  }

  @override
  Future<bool> openLocationSettings() {
    return Geolocator.openLocationSettings();
  }

  MobileDeviceLocationPermission _mapPermission(LocationPermission permission) {
    if (permission == LocationPermission.deniedForever) {
      return MobileDeviceLocationPermission.deniedForever;
    }
    if (permission == LocationPermission.always) {
      return MobileDeviceLocationPermission.always;
    }
    if (permission == LocationPermission.whileInUse) {
      return MobileDeviceLocationPermission.whileInUse;
    }
    return MobileDeviceLocationPermission.denied;
  }
}
