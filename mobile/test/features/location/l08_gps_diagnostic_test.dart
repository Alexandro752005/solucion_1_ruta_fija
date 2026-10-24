import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/data/geolocator_device_location_gateway.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/location/presentation/service_location_status.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

const _privateSentinel = 'SYNTHETIC_EXCEPTION_BODY_NOT_FOR_UI';

MobileAssignment _active() => testAssignment(
  serviceId: 'synthetic-service',
  status: MobileAssignmentStatus.enServicio,
  acceptedAt: DateTime.utc(2026, 10, 22),
  startedAt: DateTime.utc(2026, 10, 22),
);

void main() {
  test(
    'legacy device failures remain compatible and do not print their text',
    () {
      const failure = MobileDeviceLocationFailure(_privateSentinel);
      expect(failure.message, _privateSentinel);
      expect(failure.kind, MobileDeviceLocationFailureKind.unknown);
      expect(failure.toString(), isNot(contains(_privateSentinel)));
    },
  );

  for (final entry in const {
    MobileDeviceLocationFailureKind.timeout: ServiceLocationFailureKind.timeout,
    MobileDeviceLocationFailureKind.provider:
        ServiceLocationFailureKind.provider,
    MobileDeviceLocationFailureKind.invalidAccuracy:
        ServiceLocationFailureKind.invalidAccuracy,
  }.entries) {
    testWidgets(
      'classifies ${entry.key.name} at capture without upload or another read',
      (tester) async {
        final driver = _Driver();
        final device = _Device()
          ..failure = MobileDeviceLocationFailure(
            _privateSentinel,
            kind: entry.key,
          );
        final controller = _controller(driver, device);
        await _start(tester, controller);
        expect(controller.phase, ServiceLocationPhase.unavailable);
        expect(controller.diagnostic?.step, ServiceLocationStep.capture);
        expect(controller.diagnostic?.kind, entry.value);
        expect(device.reads, 1);
        expect(device.permissionRequests, 0);
        expect(driver.uploadAttempts, 0);
        expect(controller.lastCaptureAt, isNull);
        expect(
          controller.diagnostic.toString(),
          isNot(contains(_privateSentinel)),
        );
        expect(find.textContaining(_privateSentinel), findsNothing);
        await tester.pump(const Duration(seconds: 29));
        expect(device.reads, 1);
        controller.dispose();
      },
    );
  }

  for (final accuracy in <double?>[null, -1, double.nan]) {
    testWidgets('invalid measured accuracy $accuracy never reaches upload', (
      tester,
    ) async {
      final driver = _Driver();
      final device = _Device()
        ..read = () async => fromGeolocatorPosition(_position(accuracy));
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(
        controller.diagnostic?.kind,
        ServiceLocationFailureKind.invalidAccuracy,
      );
      expect(controller.diagnostic?.step, ServiceLocationStep.capture);
      expect(device.reads, 1);
      expect(driver.uploadAttempts, 0);
      expect(find.textContaining('No se envió ese punto'), findsOneWidget);
      controller.dispose();
    });
  }

  testWidgets(
    'upload network failure is discarded and only next ordinary tick reads again',
    (tester) async {
      final driver = _Driver()
        ..uploadFailure = const MobileNetworkException(_privateSentinel);
      final device = _Device();
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(controller.phase, ServiceLocationPhase.noConnection);
      expect(controller.diagnostic?.kind, ServiceLocationFailureKind.network);
      expect(controller.diagnostic?.step, ServiceLocationStep.upload);
      expect(device.reads, 1);
      expect(driver.uploadAttempts, 1);
      expect(driver.uploaded, isEmpty);
      expect(controller.lastCaptureAt, isNull);
      expect(
        find.textContaining('No se confirmó la entrega del punto'),
        findsOneWidget,
      );
      expect(find.textContaining(_privateSentinel), findsNothing);
      await tester.pump(const Duration(seconds: 29));
      expect(device.reads, 1);
      driver.uploadFailure = null;
      await tester.pump(const Duration(seconds: 1));
      expect(device.reads, 2);
      expect(driver.uploadAttempts, 2);
      expect(driver.uploaded, hasLength(1));
      expect(controller.phase, ServiceLocationPhase.sharing);
      expect(controller.diagnostic, isNull);
      controller.dispose();
    },
  );

  testWidgets(
    'known API capture-time rejection has a fixed message without server text',
    (tester) async {
      final driver = _Driver()
        ..uploadFailure = const MobileApiException(
          status: 400,
          code: 'LOCATION_CAPTURE_TOO_OLD',
          message: _privateSentinel,
          correlationId: _privateSentinel,
        );
      final device = _Device();
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(controller.diagnostic?.step, ServiceLocationStep.upload);
      expect(
        controller.diagnostic?.kind,
        ServiceLocationFailureKind.apiRejected,
      );
      expect(controller.diagnostic?.httpStatus, 400);
      expect(
        controller.diagnostic?.apiCause,
        ServiceLocationApiCause.captureTime,
      );
      expect(
        find.textContaining('El servidor rechazó la hora de captura'),
        findsOneWidget,
      );
      expect(find.textContaining(_privateSentinel), findsNothing);
      expect(device.reads, 1);
      expect(driver.uploadAttempts, 1);
      expect(controller.lastCaptureAt, isNull);
      controller.dispose();
    },
  );

  testWidgets(
    'unknown API codes and arbitrary text are not retained in diagnostic',
    (tester) async {
      final driver = _Driver()
        ..uploadFailure = const MobileApiException(
          status: 409,
          code: _privateSentinel,
          message: _privateSentinel,
          fieldErrors: {_privateSentinel: _privateSentinel},
        );
      final controller = _controller(driver, _Device());
      await _start(tester, controller);
      expect(controller.diagnostic?.apiCause, ServiceLocationApiCause.other);
      expect(
        controller.diagnostic.toString(),
        isNot(contains(_privateSentinel)),
      );
      expect(find.textContaining(_privateSentinel), findsNothing);
      expect(
        find.textContaining('Ruta Fija no confirmó la operación de ubicación'),
        findsOneWidget,
      );
      controller.dispose();
    },
  );

  testWidgets(
    'unknown capture exceptions never expose raw text or become successful sharing',
    (tester) async {
      final driver = _Driver();
      final device = _Device()..failure = StateError(_privateSentinel);
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(controller.diagnostic?.step, ServiceLocationStep.capture);
      expect(controller.diagnostic?.kind, ServiceLocationFailureKind.unknown);
      expect(
        controller.diagnostic.toString(),
        isNot(contains(_privateSentinel)),
      );
      expect(find.textContaining(_privateSentinel), findsNothing);
      expect(controller.phase, ServiceLocationPhase.unavailable);
      expect(controller.lastCaptureAt, isNull);
      expect(device.reads, 1);
      expect(driver.uploadAttempts, 0);
      controller.dispose();
    },
  );

  testWidgets(
    'invalid discovery response is distinguished without reading the device',
    (tester) async {
      final driver = _Driver();
      final device = _Device();
      final assignments = FakeMobileAssignmentGateway(assignments: [_active()])
        ..failure = const FormatException(_privateSentinel);
      final controller = MobileServiceLocationController(
        assignments: assignments,
        driver: driver,
        device: device,
      );
      await _start(tester, controller);
      expect(controller.diagnostic?.step, ServiceLocationStep.discovery);
      expect(
        controller.diagnostic?.kind,
        ServiceLocationFailureKind.invalidResponse,
      );
      expect(device.reads, 0);
      expect(driver.profileReads, 0);
      expect(driver.uploadAttempts, 0);
      expect(
        controller.diagnostic.toString(),
        isNot(contains(_privateSentinel)),
      );
      controller.dispose();
    },
  );

  testWidgets(
    '503 while reading profile never becomes a sensor failure or capture',
    (tester) async {
      final driver = _Driver()
        ..profileFailure = const MobileApiException(
          status: 503,
          code: _privateSentinel,
          message: _privateSentinel,
        );
      final device = _Device();
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(controller.diagnostic?.step, ServiceLocationStep.profile);
      expect(
        controller.diagnostic?.kind,
        ServiceLocationFailureKind.apiRejected,
      );
      expect(controller.diagnostic?.httpStatus, 503);
      expect(device.reads, 0);
      expect(driver.uploadAttempts, 0);
      expect(
        find.textContaining('Ruta Fija no está disponible temporalmente'),
        findsOneWidget,
      );
      expect(find.textContaining(_privateSentinel), findsNothing);
      controller.dispose();
    },
  );

  testWidgets(
    'late capture failure after logout cannot populate another session diagnostic',
    (tester) async {
      final pending = Completer<MobileCurrentLocation>();
      final driver = _Driver();
      final device = _Device()..read = () => pending.future;
      final controller = _controller(driver, device);
      await _start(tester, controller);
      expect(device.reads, 1);
      controller.setSession(null);
      pending.completeError(
        const MobileDeviceLocationFailure(
          _privateSentinel,
          kind: MobileDeviceLocationFailureKind.provider,
        ),
      );
      await tester.pump();
      expect(controller.phase, ServiceLocationPhase.idle);
      expect(controller.diagnostic, isNull);
      expect(controller.lastCaptureAt, isNull);
      expect(driver.uploadAttempts, 0);
      controller.dispose();
    },
  );

  testWidgets(
    'late capture failure after leaving foreground cannot restore an old diagnostic',
    (tester) async {
      final pending = Completer<MobileCurrentLocation>();
      final driver = _Driver();
      final device = _Device()..read = () => pending.future;
      final controller = _controller(driver, device);
      await _start(tester, controller);
      controller.setForeground(false);
      pending.completeError(
        const MobileDeviceLocationFailure(
          _privateSentinel,
          kind: MobileDeviceLocationFailureKind.timeout,
        ),
      );
      await tester.pump();
      expect(controller.phase, ServiceLocationPhase.paused);
      expect(controller.diagnostic, isNull);
      expect(controller.lastCaptureAt, isNull);
      expect(device.reads, 1);
      expect(driver.uploadAttempts, 0);
      controller.dispose();
    },
  );
}

MobileServiceLocationController _controller(_Driver driver, _Device device) =>
    MobileServiceLocationController(
      assignments: FakeMobileAssignmentGateway(assignments: [_active()]),
      driver: driver,
      device: device,
    );

Future<void> _start(
  WidgetTester tester,
  MobileServiceLocationController controller,
) async {
  await tester.pumpWidget(
    MaterialApp(
      home: ServiceLocationStatus(
        controller: controller,
        child: const Scaffold(body: Text('Synthetic service')),
      ),
    ),
  );
  controller.setSession(_active().driverId);
  controller.setForeground(true);
  await tester.pump();
}

Position _position(double? accuracy) => Position(
  latitude: -6.77,
  longitude: -79.84,
  timestamp: DateTime.utc(2026, 10, 22),
  accuracy: accuracy ?? 0,
  hasAccuracy: accuracy != null,
  altitude: 0,
  altitudeAccuracy: 0,
  heading: 0,
  headingAccuracy: 0,
  speed: 0,
  speedAccuracy: 0,
);

final class _Device implements MobileDeviceLocationGateway {
  Object? failure;
  Future<MobileCurrentLocation> Function()? read;
  int reads = 0;
  int permissionRequests = 0;
  @override
  Future<MobileCurrentLocation> currentPosition() async {
    reads++;
    if (failure != null) throw failure!;
    return read == null
        ? FakeMobileDeviceLocationGateway().nextLocation
        : await read!();
  }

  @override
  Future<bool> isLocationServiceEnabled() async => true;
  @override
  Future<MobileDeviceLocationPermission> checkPermission() async =>
      MobileDeviceLocationPermission.whileInUse;
  @override
  Future<MobileDeviceLocationPermission> requestPermission() async {
    permissionRequests++;
    return MobileDeviceLocationPermission.whileInUse;
  }

  @override
  Future<bool> openLocationSettings() async => false;
  @override
  Future<bool> openAppSettings() async => false;
}

final class _Driver implements MobileDriverGateway {
  Object? profileFailure;
  Object? uploadFailure;
  int profileReads = 0;
  int uploadAttempts = 0;
  final uploaded = <MobileCurrentLocation>[];
  final delegate = FakeMobileDriverGateway(
    profile: MobileDriverProfile(
      driverId: _active().driverId,
      fullName: 'Synthetic',
      groupName: 'Synthetic',
      availabilityStatus: MobileAvailabilityStatus.enServicio,
      locationConsent: true,
      primaryVehicle: null,
    ),
  );
  @override
  Future<MobileDriverProfile> profile() async {
    profileReads++;
    if (profileFailure != null) throw profileFailure!;
    return delegate.profile();
  }

  @override
  Future<void> upsertCurrentLocation(MobileCurrentLocation point) async {
    uploadAttempts++;
    if (uploadFailure != null) throw uploadFailure!;
    uploaded.add(point);
  }

  @override
  Future<MobileAvailability> availability() => delegate.availability();
  @override
  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  ) => delegate.changeAvailability(status);
  @override
  Future<MobileDriverProfile> changeLocationConsent(bool consent) =>
      delegate.changeLocationConsent(consent);
  @override
  Future<void> clearCurrentLocation() => delegate.clearCurrentLocation();
}
