import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

MobileAssignment activeMapService() => testAssignment(
  serviceId: 'synthetic-map-service',
  status: MobileAssignmentStatus.enServicio,
  acceptedAt: DateTime.utc(2026, 11, 11),
  startedAt: DateTime.utc(2026, 11, 11),
);

void main() {
  testWidgets(
    'personal map captures through the existing scheduler without consent or uploads',
    (tester) async {
      final driver = FakeMobileDriverGateway();
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: []),
        driver: driver,
        device: device,
      );
      addTearDown(controller.dispose);
      controller.setSession(driver.profileValue.driverId);
      controller.setForeground(true);
      await tester.pump();
      expect(device.locationReads, 0);
      controller.setMapVisible(true);
      await tester.pump();
      expect(device.locationReads, 1);
      expect(controller.mapPoint, same(device.nextLocation));
      expect(driver.uploadedLocations, isEmpty);
      expect(driver.locationConsentChanges, isEmpty);
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, 2);
      controller.setMapVisible(false);
      await tester.pump(const Duration(seconds: 30));
      expect(controller.mapPoint, isNull);
      expect(device.locationReads, 2);
      controller.setForeground(false);
    },
  );

  testWidgets(
    'opening personal tab during a service reuses the same capture and reports sharing honestly',
    (tester) async {
      final row = activeMapService();
      final driver = FakeMobileDriverGateway(
        profile: MobileDriverProfile(
          driverId: row.driverId,
          fullName: 'Synthetic',
          groupName: 'Synthetic',
          availabilityStatus: MobileAvailabilityStatus.enServicio,
          locationConsent: true,
          primaryVehicle: null,
        ),
      );
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [row]),
        driver: driver,
        device: device,
      );
      addTearDown(controller.dispose);
      controller.setSession(row.driverId);
      controller.setMapVisible(true);
      controller.setForeground(true);
      await tester.pump();
      expect(device.locationReads, 1);
      expect(driver.uploadedLocations, hasLength(1));
      expect(controller.phase, ServiceLocationPhase.sharing);
      expect(controller.mapPoint, isNotNull);
      controller.setMapVisible(false);
      expect(controller.mapPoint, isNull);
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, 2);
      expect(driver.uploadedLocations, hasLength(2));
      expect(controller.mapPoint, isNull);
      controller.setForeground(false);
    },
  );

  testWidgets(
    'revocation invalidates an in-flight point immediately and never uploads it',
    (tester) async {
      final row = activeMapService();
      final driver = FakeMobileDriverGateway(
        profile: MobileDriverProfile(
          driverId: row.driverId,
          fullName: 'Synthetic',
          groupName: 'Synthetic',
          availabilityStatus: MobileAvailabilityStatus.enServicio,
          locationConsent: true,
          primaryVehicle: null,
        ),
      );
      final device = DelayedMapDevice();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [row]),
        driver: driver,
        device: device,
      );
      addTearDown(controller.dispose);
      controller.setSession(row.driverId);
      controller.setMapVisible(true);
      controller.setForeground(true);
      await tester.pump();
      controller.consentChanged(false);
      expect(controller.mapPoint, isNull);
      device.fix.complete(FakeMobileDeviceLocationGateway().nextLocation);
      await tester.pump();
      expect(driver.uploadedLocations, isEmpty);
      expect(controller.phase, ServiceLocationPhase.noConsent);
      controller.setForeground(false);
      expect(controller.mapPoint, isNull);
      controller.setSession(null);
      expect(controller.mapPoint, isNull);
    },
  );

  testWidgets(
    'turning GPS off invalidates a delayed service fix before upload',
    (tester) async {
      final row = activeMapService();
      final driver = FakeMobileDriverGateway(
        profile: MobileDriverProfile(
          driverId: row.driverId,
          fullName: 'Synthetic',
          groupName: 'Synthetic',
          availabilityStatus: MobileAvailabilityStatus.enServicio,
          locationConsent: true,
          primaryVehicle: null,
        ),
      );
      final device = SignallingMapDevice();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [row]),
        driver: driver,
        device: device,
      );
      controller.setSession(row.driverId);
      controller.setMapVisible(true);
      controller.setForeground(true);
      await tester.pump();
      device.enabled = false;
      device.status.add(false);
      await tester.pump();
      expect(controller.mapPoint, isNull);
      device.fix.complete(FakeMobileDeviceLocationGateway().nextLocation);
      await tester.pump();
      expect(driver.uploadedLocations, isEmpty);
      expect(controller.mapPhase, MapLocationPhase.gpsDisabled);
      controller.dispose();
      await tester.pump();
      unawaited(device.status.close());
      await tester.pump();
    },
  );

  testWidgets(
    'OS permission removed during a personal fix never renders the result',
    (tester) async {
      final driver = FakeMobileDriverGateway();
      final device = SignallingMapDevice();
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: []),
        driver: driver,
        device: device,
      );
      controller.setSession(driver.profileValue.driverId);
      controller.setMapVisible(true);
      controller.setForeground(true);
      await tester.pump();
      device.permission = MobileDeviceLocationPermission.denied;
      device.fix.complete(FakeMobileDeviceLocationGateway().nextLocation);
      await tester.pump();
      expect(controller.mapPoint, isNull);
      expect(controller.mapPhase, MapLocationPhase.noPermission);
      expect(driver.uploadedLocations, isEmpty);
      controller.dispose();
      await tester.pump();
      unawaited(device.status.close());
      await tester.pump();
    },
  );

  testWidgets(
    'completion owns the single-flight lock during GET and capture so periodic ticks cannot duplicate GPS',
    (tester) async {
      final row = activeMapService();
      final driver = FakeMobileDriverGateway(
        profile: MobileDriverProfile(
          driverId: row.driverId,
          fullName: 'Synthetic',
          groupName: 'Synthetic',
          availabilityStatus: MobileAvailabilityStatus.enServicio,
          locationConsent: true,
          primaryVehicle: null,
        ),
      );
      final assignments = BlockedMapAssignments(row);
      final device = FakeMobileDeviceLocationGateway();
      final controller = MobileServiceLocationController(
        assignments: assignments,
        driver: driver,
        device: device,
      );
      controller.setSession(row.driverId);
      controller.setForeground(true);
      await tester.pump();
      final baseline = device.locationReads;
      final complete = controller.beforeComplete(row);
      await tester.pump();
      final periodic = controller.tick();
      await tester.pump(const Duration(seconds: 30));
      expect(device.locationReads, baseline);
      expect(identical(complete, periodic), isTrue);
      assignments.lookup.complete(row);
      await tester.pump();
      await complete;
      expect(device.locationReads, baseline + 1);
      expect(driver.uploadedLocations, hasLength(baseline + 1));
      controller.dispose();
    },
  );
}

final class DelayedMapDevice implements MobileDeviceLocationGateway {
  final fix = Completer<MobileCurrentLocation>();
  @override
  Future<bool> isLocationServiceEnabled() async => true;
  @override
  Future<MobileDeviceLocationPermission> checkPermission() async =>
      MobileDeviceLocationPermission.whileInUse;
  @override
  Future<MobileDeviceLocationPermission> requestPermission() =>
      checkPermission();
  @override
  Future<MobileCurrentLocation> currentPosition() => fix.future;
  @override
  Future<bool> openAppSettings() async => true;
  @override
  Future<bool> openLocationSettings() async => true;
}

final class SignallingMapDevice
    implements MobileDeviceLocationGateway, MobileDeviceLocationSignals {
  final fix = Completer<MobileCurrentLocation>();
  final status = StreamController<bool>();
  bool enabled = true;
  MobileDeviceLocationPermission permission =
      MobileDeviceLocationPermission.whileInUse;
  @override
  Stream<bool> get locationServiceChanges => status.stream;
  @override
  Future<bool> isLocationServiceEnabled() async => enabled;
  @override
  Future<MobileDeviceLocationPermission> checkPermission() async => permission;
  @override
  Future<MobileDeviceLocationPermission> requestPermission() =>
      checkPermission();
  @override
  Future<MobileCurrentLocation> currentPosition() => fix.future;
  @override
  Future<bool> openAppSettings() async => true;
  @override
  Future<bool> openLocationSettings() async => true;
}

final class BlockedMapAssignments implements MobileAssignmentGateway {
  BlockedMapAssignments(this.row);
  final MobileAssignment row;
  final lookup = Completer<MobileAssignment>();
  @override
  Future<MobileAssignment> get(String id) => lookup.future;
  @override
  Future<MobileAssignmentPage> list({
    MobileAssignmentStatus? status,
    int page = 0,
    int size = 30,
  }) async => MobileAssignmentPage(
    items: [row],
    page: 0,
    size: size,
    totalItems: 1,
    totalPages: 1,
  );
  @override
  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(
    String driverId,
  ) async => [];
  @override
  Future<MobileAssignmentCommandResult> send(
    MobileAssignment assignment,
    MobileAssignmentCommandType command, {
    String? rejectionReason,
  }) => throw UnimplementedError();
  @override
  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand command,
  ) => throw UnimplementedError();
}
