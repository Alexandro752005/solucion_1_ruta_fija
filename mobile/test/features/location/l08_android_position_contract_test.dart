// This exercises the actual Android wrapper selected by the local dependency
// override. All platform-channel values below are synthetic, never sensor data.
// ignore_for_file: depend_on_referenced_packages, implementation_imports

import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator_android/src/types/android_position.dart';
import 'package:ruta_fija_conductor/features/location/data/geolocator_device_location_gateway.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';

void main() {
  final capturedAt = DateTime.utc(2026, 10, 22, 12);

  Map<String, Object> nativeMap() => {
    'latitude': 0.0,
    'longitude': 0.0,
    'timestamp': capturedAt.millisecondsSinceEpoch,
    'is_mocked': false,
  };

  test(
    'Android measured accuracy reaches the adapter with sensor timestamp',
    () {
      final native = nativeMap()..['accuracy'] = 8.25;
      final position = AndroidPosition.fromMap(native);

      expect(position.hasAccuracy, isTrue);
      expect(position.accuracy, 8.25);
      expect(position.timestamp, capturedAt);

      final point = fromGeolocatorPosition(position);
      expect(point.accuracyMeters, 8.25);
      expect(point.capturedAt, capturedAt);
    },
  );

  test('Android missing accuracy remains unmeasured and is rejected', () {
    final position = AndroidPosition.fromMap(nativeMap());

    expect(position.hasAccuracy, isFalse);
    expect(position.accuracy, 0.0);
    expect(
      () => fromGeolocatorPosition(position),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
  });

  test('explicit false presence flag cannot be turned into a measurement', () {
    final native = nativeMap()
      ..['accuracy'] = 8.25
      ..['has_accuracy'] = false;
    final position = AndroidPosition.fromMap(native);

    expect(position.hasAccuracy, isFalse);
    expect(
      () => fromGeolocatorPosition(position),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
  });

  test(
    'a measured zero is preserved rather than confused with missing data',
    () {
      final native = nativeMap()..['accuracy'] = 0.0;
      final position = AndroidPosition.fromMap(native);

      expect(position.hasAccuracy, isTrue);
      expect(fromGeolocatorPosition(position).accuracyMeters, 0.0);
    },
  );

  test('negative measured accuracy still fails application validation', () {
    final native = nativeMap()..['accuracy'] = -1.0;
    final position = AndroidPosition.fromMap(native);

    expect(position.hasAccuracy, isTrue);
    expect(
      () => fromGeolocatorPosition(position),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
  });

  test('non-finite measured accuracy still fails application validation', () {
    for (final value in [
      double.nan,
      double.infinity,
      double.negativeInfinity,
    ]) {
      final native = nativeMap()..['accuracy'] = value;
      final position = AndroidPosition.fromMap(native);

      expect(position.hasAccuracy, isTrue);
      expect(
        () => fromGeolocatorPosition(position),
        throwsA(isA<MobileDeviceLocationFailure>()),
      );
    }
  });

  test('all reported presence indicators survive the Android wrapper', () {
    final native = nativeMap()
      ..addAll({
        'accuracy': 8.25,
        'altitude': 0.0,
        'altitude_accuracy': 1.0,
        'heading': 0.0,
        'heading_accuracy': 2.0,
        'speed': 0.0,
        'speed_accuracy': 3.0,
      });
    final position = AndroidPosition.fromMap(native);

    expect(position.hasAccuracy, isTrue);
    expect(position.hasAltitude, isTrue);
    expect(position.hasAltitudeAccuracy, isTrue);
    expect(position.hasHeading, isTrue);
    expect(position.hasHeadingAccuracy, isTrue);
    expect(position.hasSpeed, isTrue);
    expect(position.hasSpeedAccuracy, isTrue);
    expect(position.isMocked, isFalse);
  });

  test('round trip retains absent optional fields without inventing flags', () {
    final native = nativeMap()
      ..['accuracy'] = 8.25
      ..['is_mocked'] = true;
    final first = AndroidPosition.fromMap(native);
    final second = AndroidPosition.fromMap(first.toJson());

    expect(second.hasAccuracy, isTrue);
    expect(second.hasAltitude, isFalse);
    expect(second.hasAltitudeAccuracy, isFalse);
    expect(second.hasHeading, isFalse);
    expect(second.hasHeadingAccuracy, isFalse);
    expect(second.hasSpeed, isFalse);
    expect(second.hasSpeedAccuracy, isFalse);
    expect(second.isMocked, isTrue);
    expect(second.timestamp, capturedAt);
  });
}
