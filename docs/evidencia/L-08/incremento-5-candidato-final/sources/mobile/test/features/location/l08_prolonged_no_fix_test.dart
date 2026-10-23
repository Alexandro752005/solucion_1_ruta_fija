import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/location/presentation/service_location_status.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

// Controlled missing fixes with the real controller/timer/widget. These are
// local simulations; they do not prove satellite signal loss on the POCO.
void main() {
  testWidgets(
    'prolonged missing fixes warn strictly after120s without replacing the active service or inventing points',
    (tester) async {
      final h = _Harness(tester);
      await h.start();
      final first = h.driver.uploaded.single;
      h.device.noFix = true;

      for (var second = 30; second <= 120; second += 30) {
        await tester.pump(const Duration(seconds: 30));
        expect(h.controller.hasActiveService, isTrue);
        expect(h.controller.isSignalDegraded, isFalse);
        expect(h.driver.uploaded, [first]);
      }
      expect(h.controller.diagnostic?.kind, ServiceLocationFailureKind.timeout);
      await tester.pump(const Duration(seconds: 1));
      await h.controller.tick();
      await tester.pump();

      expect(h.controller.captureAge, const Duration(seconds: 121));
      expect(h.controller.isSignalDegraded, isTrue);
      expect(
        find.textContaining('GPS sin captura reciente: señal degradada'),
        findsOneWidget,
      );
      expect(find.textContaining('El servicio continúa'), findsOneWidget);
      expect(find.textContaining('121s'), findsOneWidget);
      expect(find.text('Servicio sintético en curso'), findsOneWidget);
      expect(find.textContaining('-6.77'), findsNothing);
      expect(h.controller.activeAssignmentId, h.rows.single.id);
      expect(h.driver.uploaded, [first]);
      expect(h.assignments.sentCommands, isEmpty);
      expect(h.assignments.pending, isEmpty);
      expect(h.device.permissionRequests, 0);

      // Recovery performs only the next normal fresh read, never catch-up.
      h.device.noFix = false;
      await tester.pump(const Duration(seconds: 29));
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.controller.phase, ServiceLocationPhase.sharing);
      expect(h.driver.uploaded, hasLength(2));
      expect(
        h.driver.uploaded.last.capturedAt.difference(first.capturedAt),
        const Duration(seconds: 150),
      );
      expect(
        h.driver.uploaded.every((p) => p.latitude != 0 && p.longitude != 0),
        isTrue,
      );
      expect(h.assignments.pending, isEmpty);
      expect(find.textContaining('señal degradada'), findsNothing);
      h.controller.dispose();
    },
  );

  testWidgets('no initial fix also becomes degraded and never fabricates0,0', (
    tester,
  ) async {
    final h = _Harness(tester)..device.noFix = true;
    await h.start();
    for (var second = 30; second <= 120; second += 30) {
      await tester.pump(const Duration(seconds: 30));
    }
    expect(h.controller.isSignalDegraded, isFalse);
    await tester.pump(const Duration(seconds: 1));
    await h.controller.tick();
    await tester.pump();
    expect(h.controller.isSignalDegraded, isTrue);
    expect(h.controller.lastCaptureAt, isNull);
    expect(h.driver.uploaded, isEmpty);
    expect(h.controller.hasActiveService, isTrue);
    expect(find.textContaining('Sin captura confirmada'), findsOneWidget);
    expect(find.textContaining('señal degradada'), findsOneWidget);
    expect(h.assignments.sentCommands, isEmpty);
    expect(h.assignments.pending, isEmpty);
    h.controller.dispose();
  });

  testWidgets('missing fix permits safe completion then stops periodic reads', (
    tester,
  ) async {
    final h = _Harness(tester)..device.noFix = true;
    await h.start();
    await tester.pump(const Duration(seconds: 150));
    await h.controller.beforeComplete(h.rows.single);
    expect(h.controller.hasActiveService, isTrue);
    expect(h.controller.isSignalDegraded, isTrue);
    expect(h.driver.uploaded, isEmpty);
    expect(h.assignments.sentCommands, isEmpty);
    h.rows[0] = testAssignment(
      serviceId: 'synthetic-service',
      status: MobileAssignmentStatus.completed,
    );
    h.controller.observe(h.rows.single);
    final reads = h.device.reads;
    await tester.pump(const Duration(minutes: 3));
    expect(h.controller.hasActiveService, isFalse);
    expect(h.controller.isSignalDegraded, isFalse);
    expect(h.device.reads, reads);
    expect(h.driver.uploaded, isEmpty);
    expect(find.byKey(const Key('service-gps-status')), findsNothing);
    h.controller.dispose();
  });

  testWidgets(
    'background and logout remove degraded state without deferred capture',
    (tester) async {
      final h = _Harness(tester)..device.noFix = true;
      await h.start();
      await tester.pump(const Duration(seconds: 150));
      expect(h.controller.isSignalDegraded, isTrue);
      h.controller.setForeground(false);
      final reads = h.device.reads;
      await tester.pump(const Duration(minutes: 3));
      expect(h.controller.phase, ServiceLocationPhase.paused);
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.device.reads, reads);
      h.controller.setSession(null);
      await tester.pump();
      expect(h.controller.phase, ServiceLocationPhase.idle);
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.controller.hasActiveService, isFalse);
      expect(h.driver.uploaded, isEmpty);
      expect(h.assignments.pending, isEmpty);
      h.controller.dispose();
    },
  );

  testWidgets(
    'a failed recovery upload is discarded and the next tick gets a new fix',
    (tester) async {
      final h = _Harness(tester)..device.noFix = true;
      await h.start();
      await tester.pump(const Duration(seconds: 150));
      h.device.noFix = false;
      h.driver.failUpload = true;
      await tester.pump(const Duration(seconds: 30));
      expect(h.controller.phase, ServiceLocationPhase.noConnection);
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.driver.uploaded, isEmpty);
      expect(h.driver.attempted, hasLength(1));
      final failedFix = h.driver.attempted.single;
      h.driver.failUpload = false;
      await tester.pump(const Duration(seconds: 29));
      expect(h.driver.uploaded, isEmpty);
      await tester.pump(const Duration(seconds: 1));
      expect(h.driver.uploaded, hasLength(1));
      expect(h.driver.attempted, hasLength(2));
      expect(
        h.driver.uploaded.single.capturedAt.difference(failedFix.capturedAt),
        const Duration(seconds: 30),
      );
      expect(h.driver.uploaded.single, isNot(same(failedFix)));
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.assignments.pending, isEmpty);
      h.controller.dispose();
    },
  );

  testWidgets(
    'a new service never inherits a prior service missing-fix interval',
    (tester) async {
      final h = _Harness(tester)..device.noFix = true;
      await h.start();
      await tester.pump(const Duration(seconds: 150));
      expect(h.controller.isSignalDegraded, isTrue);
      h.rows[0] = _active(id: 'synthetic-next-offer');
      h.controller.observe(h.rows.single);
      await tester.pump();
      expect(h.controller.isSignalDegraded, isFalse);
      expect(h.controller.activeAssignmentId, 'synthetic-next-offer');
      expect(h.controller.lastCaptureAt, isNull);
      expect(h.driver.uploaded, isEmpty);
      h.controller.dispose();
    },
  );
}

MobileAssignment _active({String id = 'synthetic-offer'}) => testAssignment(
  id: id,
  serviceId: 'synthetic-service',
  status: MobileAssignmentStatus.enServicio,
  acceptedAt: DateTime.utc(2026, 10, 23),
  startedAt: DateTime.utc(2026, 10, 23),
);

final class _Harness {
  _Harness(this.tester) {
    assignments = FakeMobileAssignmentGateway(assignments: rows);
    device = _Device(() => tester.binding.clock.now().toUtc());
    controller = MobileServiceLocationController(
      assignments: assignments,
      driver: driver,
      device: device,
      clock: () => tester.binding.clock.now().toUtc(),
    );
  }
  final WidgetTester tester;
  final rows = [_active()];
  final driver = _Driver();
  late final FakeMobileAssignmentGateway assignments;
  late final _Device device;
  late final MobileServiceLocationController controller;

  Future<void> start() async {
    await tester.pumpWidget(
      MaterialApp(
        home: ServiceLocationStatus(
          controller: controller,
          child: const Scaffold(body: Text('Servicio sintético en curso')),
        ),
      ),
    );
    controller.setSession(rows.single.driverId);
    controller.setForeground(true);
    await tester.pump();
  }
}

final class _Device implements MobileDeviceLocationGateway {
  _Device(this.clock);
  final DateTime Function() clock;
  bool noFix = false;
  int reads = 0;
  int permissionRequests = 0;
  @override
  Future<MobileCurrentLocation> currentPosition() async {
    reads++;
    if (noFix) {
      throw const MobileDeviceLocationFailure(
        'SYNTHETIC_NO_FIX',
        kind: MobileDeviceLocationFailureKind.timeout,
      );
    }
    return MobileCurrentLocation(
      latitude: -6.77,
      longitude: -79.84,
      accuracyMeters: 8,
      capturedAt: clock(),
    );
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
  Future<bool> openAppSettings() async => false;
  @override
  Future<bool> openLocationSettings() async => false;
}

final class _Driver implements MobileDriverGateway {
  bool failUpload = false;
  final attempted = <MobileCurrentLocation>[];
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
  Future<MobileDriverProfile> profile() => delegate.profile();
  @override
  Future<void> upsertCurrentLocation(MobileCurrentLocation point) async {
    attempted.add(point);
    if (failUpload) throw const MobileNetworkException('SYNTHETIC_OFFLINE');
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
