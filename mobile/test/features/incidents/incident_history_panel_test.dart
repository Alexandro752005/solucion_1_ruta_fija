import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/incidents/data/mobile_incident_repository.dart';
import 'package:ruta_fija_conductor/features/incidents/domain/mobile_incident_models.dart';
import 'package:ruta_fija_conductor/features/incidents/presentation/incident_history_panel.dart';

import '../../support/mobile_fakes.dart';

// Widget boundary with an explicit API double. Not an HTTP/PostgreSQL assertion.
class _InformationGateway implements MobileIncidentGateway {
  int posts = 0;
  bool uncertain = false, committedBeforeTimeout = false;
  String? key;
  List<MobileIncidentAction> rows = [];
  @override
  Future<List<MobileIncidentAction>> history(String id) async => rows;
  @override
  Future<MobileIncident> information(
    String id, {
    required int version,
    required String operationKey,
    required String note,
  }) async {
    posts++;
    key = operationKey;
    if (committedBeforeTimeout) {
      rows = [
        MobileIncidentAction(
          operationKey: operationKey,
          actorId: 'synthetic-conductor',
          kind: 'INFORMATION',
          detail: {'note': note},
          recordedAt: DateTime.utc(2026, 10, 20),
          resultingVersion: 1,
        ),
      ];
    }
    if (uncertain) {
      throw const MobileNetworkException('SYNTHETIC response unavailable');
    }
    return testIncident();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('Unused gateway method in this test');
}

void main() {
  Future<void> render(
    WidgetTester tester,
    _InformationGateway gateway, {
    MobileIncidentStatus status = MobileIncidentStatus.open,
    VoidCallback? changed,
    ValueChanged<bool>? blocked,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: IncidentHistoryPanel(
              incident: testIncident(status: status),
              gateway: gateway,
              onChanged: changed ?? () {},
              onBlocked: blocked ?? (_) {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> submit(WidgetTester tester) async {
    await tester.enterText(
      find.byKey(const Key('incident-information-note')),
      'SYNTHETIC additional fact',
    );
    await tester.pump();
    await tester.ensureVisible(
      find.byKey(const Key('incident-information-submit')),
    );
    await tester.tap(find.byKey(const Key('incident-information-submit')));
    await tester.pumpAndSettle();
  }

  testWidgets('records own additional information only after an explicit tap', (
    t,
  ) async {
    final gateway = _InformationGateway();
    int changes = 0;
    final blocked = <bool>[];
    await render(t, gateway, changed: () => changes++, blocked: blocked.add);
    expect(gateway.posts, 0);
    await submit(t);
    expect(gateway.posts, 1);
    expect(changes, 1);
    expect(blocked, [true, false]);
  });
  testWidgets(
    'keeps uncertainty and does not resubmit when no receipt is available',
    (t) async {
      final gateway = _InformationGateway()..uncertain = true;
      int changes = 0;
      await render(t, gateway, changed: () => changes++);
      await submit(t);
      expect(gateway.posts, 1);
      expect(changes, 0);
      expect(
        find.textContaining('No se confirmó la información'),
        findsOneWidget,
      );
      final button = t.widget<FilledButton>(
        find.byKey(const Key('incident-information-submit')),
      );
      expect(button.onPressed, isNull);
      await t.tap(find.text('Consultar historial y confirmación'));
      await t.pumpAndSettle();
      expect(gateway.posts, 1);
    },
  );
  testWidgets(
    'reconciles a committed information receipt without repeating its POST',
    (t) async {
      final gateway = _InformationGateway()
        ..uncertain = true
        ..committedBeforeTimeout = true;
      int changes = 0;
      await render(t, gateway, changed: () => changes++);
      await submit(t);
      expect(gateway.posts, 1);
      expect(changes, 1);
      expect(find.textContaining('Información del conductor'), findsOneWidget);
      expect(find.textContaining('note:'), findsNothing);
      expect(find.textContaining('Nota:'), findsOneWidget);
    },
  );
  testWidgets('closed original has no information form or reopening action', (
    t,
  ) async {
    final gateway = _InformationGateway();
    await render(t, gateway, status: MobileIncidentStatus.driverClosed);
    expect(find.byKey(const Key('incident-information-submit')), findsNothing);
    expect(gateway.posts, 0);
    expect(find.textContaining('no se permite reapertura'), findsOneWidget);
  });
}
