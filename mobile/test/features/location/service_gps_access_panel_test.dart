import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/location/data/mobile_service_gps_repository.dart';
import 'package:ruta_fija_conductor/features/location/presentation/service_gps_access_panel.dart';

class _Gateway implements MobileServiceGpsGateway {
  int requests = 0, deliveries = 0;
  String state = 'PENDING';
  Completer<MobileGpsDelivery>? pendingDelivery;
  MobileGpsRequest get row => MobileGpsRequest(
    id: 'synthetic-request',
    requestKey: 'synthetic-key',
    status: state,
    fromAt: DateTime.utc(2026, 10, 19),
    toAt: DateTime.utc(2026, 10, 19, 1),
    reviewDueAt: DateTime.utc(2026, 10, 21),
    grantExpiresAt: state == 'APPROVED'
        ? DateTime.now().toUtc().add(const Duration(minutes: 1))
        : null,
  );
  @override
  Future<List<MobileGpsRequest>> gpsRequests(
    String serviceId, {
    int offset = 0,
  }) async => [row];
  @override
  Future<MobileGpsRequest> requestGps(
    String serviceId,
    String requestKey,
  ) async {
    requests++;
    return row;
  }

  @override
  Future<MobileGpsDelivery> deliverGps(
    String requestId,
    String password, {
    int offset = 0,
  }) async {
    deliveries++;
    expect(password, 'SYNTHETIC_LOCAL_ONLY');
    return pendingDelivery == null ? data() : pendingDelivery!.future;
  }

  MobileGpsDelivery data() => MobileGpsDelivery(
    expiresAt: DateTime.now().toUtc().add(const Duration(seconds: 30)),
    items: [MobileGpsPoint(-6.77, -79.84, 5, DateTime.utc(2026, 10, 19))],
  );
}

void main() {
  setUp(
    () => TestWidgetsFlutterBinding.ensureInitialized()
        .handleAppLifecycleStateChanged(AppLifecycleState.resumed),
  );
  Future<void> mount(WidgetTester tester, _Gateway gateway) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ServiceGpsAccessPanel(
              serviceId: 'synthetic-service',
              gateway: gateway,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> unlock(WidgetTester tester) async {
    await tester.tap(find.text('Acceso restringido'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'SYNTHETIC_LOCAL_ONLY');
    await tester.tap(find.text('Verificar y consultar'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'normal summary displays no coordinates or direct download, and pending requests cannot deliver',
    (tester) async {
      final gateway = _Gateway();
      await mount(tester, gateway);
      expect(find.textContaining('-6.770000'), findsNothing);
      expect(find.text('Acceso restringido'), findsNothing);
      await tester.tap(find.byKey(const Key('gps-own-request')));
      await tester.pumpAndSettle();
      expect(gateway.requests, 1);
      expect(gateway.deliveries, 0);
    },
  );
  testWidgets(
    'authorized points require explicit password entry and are hidden when the temporary view expires',
    (tester) async {
      final gateway = _Gateway()..state = 'APPROVED';
      await mount(tester, gateway);
      expect(gateway.deliveries, 0);
      await unlock(tester);
      expect(gateway.deliveries, 1);
      expect(find.textContaining('-6.770000'), findsOneWidget);
      await tester.pump(const Duration(seconds: 31));
      await tester.pumpAndSettle();
      expect(find.textContaining('-6.770000'), findsNothing);
    },
  );
  testWidgets(
    'a response arriving in background is discarded without persisting or displaying the points',
    (tester) async {
      final gateway = _Gateway()
        ..state = 'APPROVED'
        ..pendingDelivery = Completer<MobileGpsDelivery>();
      await mount(tester, gateway);
      await tester.tap(find.text('Acceso restringido'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'SYNTHETIC_LOCAL_ONLY');
      await tester.tap(find.text('Verificar y consultar'));
      await tester.pumpAndSettle();
      expect(gateway.deliveries, 1);
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      gateway.pendingDelivery!.complete(gateway.data());
      await tester.pumpAndSettle();
      expect(find.textContaining('-6.770000'), findsNothing);
      expect(gateway.deliveries, 1);
    },
  );
}
