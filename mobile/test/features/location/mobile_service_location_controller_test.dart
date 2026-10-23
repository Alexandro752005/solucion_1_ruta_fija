import 'dart:async';

import 'package:flutter/widgets.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/data/geolocator_device_location_gateway.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

MobileAssignment active() => testAssignment(
  serviceId: 'synthetic-service',
  status: MobileAssignmentStatus.enServicio,
  acceptedAt: DateTime.utc(2026, 10, 19),
  startedAt: DateTime.utc(2026, 10, 19),
);
FakeMobileDriverGateway driver({bool consent = true}) =>
    FakeMobileDriverGateway(
      profile: MobileDriverProfile(
        driverId: active().driverId,
        fullName: 'Synthetic only',
        groupName: 'Synthetic only',
        availabilityStatus: MobileAvailabilityStatus.enServicio,
        locationConsent: consent,
        primaryVehicle: null,
      ),
    );

void main() {
  testWidgets(
    'attempts once on foreground service discovery and then each30s without catch-up',
    (tester) async {
      final d = driver();
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [active()]),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      expect(device.locationReads, 1);
      expect(d.uploadedLocations, hasLength(1));
      await tester.pump(const Duration(seconds: 29));
      expect(device.locationReads, 1);
      await tester.pump(const Duration(seconds: 1));
      expect(device.locationReads, 2);
      controller.setForeground(false);
      await tester.pump(const Duration(minutes: 3));
      expect(device.locationReads, 2);
      controller.setForeground(true);
      await tester.pump();
      expect(device.locationReads, 3);
    },
  );
  testWidgets(
    'does not capture pending/scheduled offers or an accepted legacy without a traced service',
    (tester) async {
      final d = driver();
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(
          assignments: [
            testAssignment(),
            testAssignment(status: MobileAssignmentStatus.scheduled),
          ],
        ),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      expect(device.locationReads, 0);
      expect(d.uploadedLocations, isEmpty);
      expect(controller.phase, ServiceLocationPhase.idle);
    },
  );
  testWidgets(
    'never grants consent or asks OS permission automatically during periodic attempts',
    (tester) async {
      final d = driver(consent: false);
      final device = FakeMobileDeviceLocationGateway(
        currentPermission: MobileDeviceLocationPermission.denied,
      );
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [active()]),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      expect(controller.phase, ServiceLocationPhase.noConsent);
      expect(d.locationConsentChanges, isEmpty);
      expect(device.locationReads, 0);
      d.profileValue = driver().profileValue;
      await tester.pump(const Duration(seconds: 30));
      expect(controller.phase, ServiceLocationPhase.noPermission);
      expect(device.permissionRequests, 0);
      expect(device.locationReads, 0);
    },
  );
  testWidgets(
    'a fix obtained after pausing or logout is discarded and never uploaded later',
    (tester) async {
      final d = driver();
      final device = _DelayedDevice();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [active()]),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      controller.setForeground(false);
      controller.setSession(null);
      device.fix.complete(FakeMobileDeviceLocationGateway().nextLocation);
      await tester.pump();
      expect(d.uploadedLocations, isEmpty);
      expect(controller.lastCaptureAt, isNull);
    },
  );
  testWidgets(
    'a failed online upload is discarded and the next attempt obtains another fix',
    (tester) async {
      final d = driver();
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [active()]),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      d.failure = const MobileNetworkException('SYNTHETIC_NO_CONNECTION');
      await tester.pump(const Duration(seconds: 30));
      expect(controller.phase, ServiceLocationPhase.noConnection);
      expect(d.uploadedLocations, hasLength(1));
      d.failure = null;
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, 2);
      expect(d.uploadedLocations, hasLength(2));
    },
  );
  testWidgets(
    'completion attempts a punctual fix and a confirmed terminal transition stops further capture',
    (tester) async {
      final d = driver();
      final device = FakeMobileDeviceLocationGateway();
      final rows = [active()];
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: rows),
        driver: d,
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      await controller.beforeComplete(active());
      expect(device.locationReads, 2);
      rows[0] = testAssignment(
        serviceId: 'synthetic-service',
        status: MobileAssignmentStatus.completed,
      );
      controller.observe(rows[0]);
      expect(controller.hasActiveService, isFalse);
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, 2);
    },
  );
  test('sensor timestamp is preserved instead of renewing an old location at read time', () {
    final captured = DateTime.utc(2020, 1, 1);
    final point = fromGeolocatorPosition(
      Position(
        latitude: -6.77,
        longitude: -79.84,
        timestamp: captured,
        accuracy: 80,
        hasAccuracy: true,
        altitude: 0,
        altitudeAccuracy: 0,
        heading: 0,
        headingAccuracy: 0,
        speed: 0,
        speedAccuracy: 0,
      ),
    );
    expect(point.capturedAt, captured);
    expect(point.accuracyMeters, 80);
  });
  testWidgets(
    'an interrupted segment stops capture without stopping an unrelated current segment',
    (tester) async {
      final device = FakeMobileDeviceLocationGateway();
      final rows = [active()];
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: rows),
        driver: driver(),
        device: device,
      );
      await tester.pumpWidget(_ControllerOwner(controller));
      controller.setSession(active().driverId);
      controller.setForeground(true);
      await tester.pump();
      expect(controller.hasActiveService, true);
      final reads = device.locationReads;
      controller.observe(
        testAssignment(
          id: 'synthetic-earlier-offer',
          status: MobileAssignmentStatus.interrupted,
        ),
      );
      expect(controller.hasActiveService, true);
      rows[0] = testAssignment(
        serviceId: 'synthetic-service',
        status: MobileAssignmentStatus.interrupted,
      );
      controller.observe(rows[0]);
      expect(controller.hasActiveService, false);
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, reads);
    },
  );
  test('a sensor fix without measured accuracy is rejected instead of treated as zero metres', () {
    expect(
      () => fromGeolocatorPosition(
        Position(
          latitude: -6.77,
          longitude: -79.84,
          timestamp: DateTime.utc(2020, 1, 1),
          accuracy: 0,
          altitude: 0,
          altitudeAccuracy: 0,
          heading: 0,
          headingAccuracy: 0,
          speed: 0,
          speedAccuracy: 0,
        ),
      ),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
  });
}

class _ControllerOwner extends StatefulWidget {
  const _ControllerOwner(this.controller);
  final MobileServiceLocationController controller;
  @override
  State<_ControllerOwner> createState() => _ControllerOwnerState();
}

class _ControllerOwnerState extends State<_ControllerOwner> {
  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }
}

final class _DelayedDevice implements MobileDeviceLocationGateway {
  final fix = Completer<MobileCurrentLocation>();
  @override
  Future<MobileCurrentLocation> currentPosition() => fix.future;
  @override
  Future<bool> isLocationServiceEnabled() async => true;
  @override
  Future<MobileDeviceLocationPermission> checkPermission() async =>
      MobileDeviceLocationPermission.whileInUse;
  @override
  Future<MobileDeviceLocationPermission> requestPermission() async =>
      MobileDeviceLocationPermission.whileInUse;
  @override
  Future<bool> openLocationSettings() async => false;
  @override
  Future<bool> openAppSettings() async => false;
}
