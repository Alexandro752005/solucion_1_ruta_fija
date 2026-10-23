import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/location/presentation/service_location_status.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  testWidgets(
    'shows capture age and an explicit stale notice without coordinates',
    (tester) async {
      final now = DateTime.utc(2026, 10, 19);
      final a = testAssignment(
        serviceId: 'synthetic-service',
        status: MobileAssignmentStatus.enServicio,
        acceptedAt: now,
        startedAt: now,
      );
      final d = FakeMobileDriverGateway(
        profile: MobileDriverProfile(
          driverId: a.driverId,
          fullName: 'Synthetic',
          groupName: 'Synthetic',
          availabilityStatus: MobileAvailabilityStatus.enServicio,
          locationConsent: true,
          primaryVehicle: null,
        ),
      );
      final device = FakeMobileDeviceLocationGateway(
        nextLocation: MobileCurrentLocation(
          latitude: -6.77,
          longitude: -79.84,
          accuracyMeters: 5,
          capturedAt: now.subtract(const Duration(seconds: 121)),
        ),
      );
      final controller = MobileServiceLocationController(
        assignments: FakeMobileAssignmentGateway(assignments: [a]),
        driver: d,
        device: device,
        clock: () => now,
      );
      await tester.pumpWidget(_Host(controller));
      controller.setSession(a.driverId);
      controller.setForeground(true);
      await tester.pumpAndSettle();
      expect(find.textContaining('GPS sin captura reciente'), findsOneWidget);
      expect(find.textContaining('121s'), findsOneWidget);
      expect(find.textContaining('-6.77'), findsNothing);
      controller.setSession(null);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('service-gps-status')), findsNothing);
    },
  );
}

class _Host extends StatefulWidget {
  const _Host(this.controller);
  final MobileServiceLocationController controller;
  @override
  State<_Host> createState() => _HostState();
}

class _HostState extends State<_Host> {
  @override
  Widget build(BuildContext context) => MaterialApp(
    home: ServiceLocationStatus(
      controller: widget.controller,
      child: const Scaffold(body: Text('Synthetic service')),
    ),
  );
  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }
}
