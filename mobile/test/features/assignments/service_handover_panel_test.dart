import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_handover_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/service_handover_panel.dart';

import '../../support/mobile_fakes.dart';

class _Gateway implements MobileServiceHandoverGateway {
  int posts = 0;
  bool unknown = false, visible = false, overdue = false;
  String? key;
  final assignment = testAssignment(
    serviceId: 'synthetic-service',
    handoverId: 'synthetic-handover',
    status: MobileAssignmentStatus.scheduled,
    acceptedAt: DateTime.utc(2026, 10, 20, 10),
    reservedAt: DateTime.utc(2026, 10, 20, 10),
  );
  @override
  Future<MobileHandoverView> handoverView(String assignmentId) async =>
      MobileHandoverView(
        status: 'IN_SERVICE',
        originalStartedAt: DateTime.utc(2026, 10, 20, 10),
        startOverdue: overdue,
        rows: [
          MobileHandover(
            id: 'synthetic-handover',
            incomingAssignmentId: assignment.id,
            outgoingAssignmentId: 'synthetic-outgoing',
            state: visible ? 'ASSUMED' : 'ACCEPTED',
            reason: 'SYNTHETIC safe transfer',
            pointReference: 'SYNTHETIC_TRANSFER_POINT',
            resolutionKey: visible ? key : null,
          ),
        ],
      );
  @override
  Future<MobileAssignment> assumeHandover(
    MobileAssignment a, {
    required String eventId,
    required bool readyConfirmed,
  }) async {
    posts++;
    key = eventId;
    expect(readyConfirmed, true);
    if (unknown) throw const MobileNetworkException('SYNTHETIC_UNKNOWN');
    visible = true;
    return testAssignment(status: MobileAssignmentStatus.enServicio);
  }
}

void main() {
  int changes = 0;
  final blocks = <bool>[];
  Future<void> mount(
    WidgetTester tester,
    _Gateway g, {
    bool blocked = false,
  }) async {
    changes = 0;
    blocks.clear();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ServiceHandoverPanel(
              assignment: g.assignment,
              gateway: g,
              onChanged: () => changes++,
              onBlocked: blocks.add,
              externalBlocked: blocked,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> ready(WidgetTester tester) async {
    await tester.ensureVisible(find.byType(CheckboxListTile));
    await tester.tap(find.byType(CheckboxListTile));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Asumir relevo'));
  }

  testWidgets(
    'acceptance does not automatically assume and requires unchecked personal readiness',
    (tester) async {
      final g = _Gateway();
      await mount(tester, g);
      expect(
        tester.widget<CheckboxListTile>(find.byType(CheckboxListTile)).value,
        false,
      );
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(g.posts, 0);
      await ready(tester);
      await tester.tap(find.text('Asumir relevo'));
      await tester.pumpAndSettle();
      expect(g.posts, 1);
      expect(changes, 1);
      expect(blocks, [true, false]);
      expect(find.text('Asumir relevo'), findsNothing);
    },
  );
  testWidgets(
    'unknown assumption is resolved from its exact server key without replay',
    (tester) async {
      final g = _Gateway()..unknown = true;
      await mount(tester, g);
      await ready(tester);
      await tester.tap(find.text('Asumir relevo'));
      await tester.pumpAndSettle();
      expect(g.posts, 1);
      expect(changes, 0);
      expect(blocks.last, true);
      expect(find.textContaining('Relevo sin confirmación'), findsOneWidget);
      g.visible = true;
      await tester.ensureVisible(find.text('Consultar relevo del servicio'));
      await tester.tap(find.text('Consultar relevo del servicio'));
      await tester.pumpAndSettle();
      expect(g.posts, 1);
      expect(changes, 1);
      expect(blocks.last, false);
      expect(find.textContaining('Relevo sin confirmación'), findsNothing);
    },
  );
  testWidgets(
    'an unresolved operation on the same service prevents assumption',
    (tester) async {
      final g = _Gateway();
      await mount(tester, g, blocked: true);
      expect(
        tester
            .widget<CheckboxListTile>(find.byType(CheckboxListTile))
            .onChanged,
        isNull,
      );
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(g.posts, 0);
    },
  );
  testWidgets('the server missed-start alert does not issue any transition', (
    tester,
  ) async {
    final g = _Gateway()..overdue = true;
    await mount(tester, g);
    expect(find.textContaining('Inicio previsto superado'), findsOneWidget);
    expect(g.posts, 0);
  });
}
