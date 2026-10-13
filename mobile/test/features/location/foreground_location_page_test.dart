import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/presentation/foreground_location_page.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  Widget subject({
    required FakeMobileDriverGateway driverGateway,
    required FakeMobileDeviceLocationGateway deviceGateway,
  }) {
    return MaterialApp(
      home: ForegroundLocationPage(
        driverGateway: driverGateway,
        deviceLocationGateway: deviceGateway,
      ),
    );
  }

  testWidgets(
    'requests foreground permission in context and sends one current point',
    (tester) async {
      final driverGateway = FakeMobileDriverGateway();
      final deviceGateway = FakeMobileDeviceLocationGateway(
        currentPermission: MobileDeviceLocationPermission.denied,
        requestResult: MobileDeviceLocationPermission.whileInUse,
      );

      await tester.pumpWidget(
        subject(driverGateway: driverGateway, deviceGateway: deviceGateway),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('location-share')));
      await tester.pumpAndSettle();
      expect(find.text('Compartir ubicación actual'), findsOneWidget);
      await tester.tap(find.byKey(const Key('location-consent-confirm')));
      await tester.pumpAndSettle();

      expect(deviceGateway.permissionRequests, 1);
      expect(deviceGateway.locationReads, 1);
      expect(driverGateway.locationConsentChanges, [true]);
      expect(driverGateway.uploadedLocations, hasLength(1));
      expect(find.text('Consentimiento activo'), findsOneWidget);
      expect(find.textContaining('-12.046374'), findsNothing);
      expect(find.textContaining('-77.042793'), findsNothing);
    },
  );

  testWidgets(
    'does not grant consent or send a point when permission is denied',
    (tester) async {
      final driverGateway = FakeMobileDriverGateway();
      final deviceGateway = FakeMobileDeviceLocationGateway(
        currentPermission: MobileDeviceLocationPermission.denied,
        requestResult: MobileDeviceLocationPermission.denied,
      );

      await tester.pumpWidget(
        subject(driverGateway: driverGateway, deviceGateway: deviceGateway),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('location-share')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('location-consent-confirm')));
      await tester.pumpAndSettle();

      expect(deviceGateway.permissionRequests, 1);
      expect(deviceGateway.locationReads, 0);
      expect(driverGateway.locationConsentChanges, isEmpty);
      expect(driverGateway.uploadedLocations, isEmpty);
    },
  );

  testWidgets('lets the conductor clear a point or revoke consent explicitly', (
    tester,
  ) async {
    final driverGateway = FakeMobileDriverGateway(
      profile: const MobileDriverProfile(
        driverId: '22222222-2222-4222-8222-222222222222',
        fullName: 'Conductora de prueba',
        groupName: 'Grupo Norte',
        availabilityStatus: MobileAvailabilityStatus.disponible,
        locationConsent: true,
        primaryVehicle: null,
      ),
    );
    final deviceGateway = FakeMobileDeviceLocationGateway();

    await tester.pumpWidget(
      subject(driverGateway: driverGateway, deviceGateway: deviceGateway),
    );
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byKey(const Key('location-clear')));
    await tester.tap(find.byKey(const Key('location-clear')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('location-clear-confirm')));
    await tester.pumpAndSettle();
    expect(driverGateway.clearLocationCalls, 1);
    expect(driverGateway.locationConsentChanges, isEmpty);

    await tester.ensureVisible(find.byKey(const Key('location-revoke')));
    await tester.tap(find.byKey(const Key('location-revoke')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('location-revoke-confirm')));
    await tester.pumpAndSettle();
    expect(driverGateway.locationConsentChanges, [false]);
    expect(find.text('Consentimiento pendiente'), findsOneWidget);
  });
}
