import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_attention_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/service_attention_panel.dart';

import '../../support/mobile_fakes.dart';

class _Gateway implements MobileServiceAttentionGateway {
  int posts = 0;
  bool unknown = false, visible = false;
  String? key;
  bool? safeStop;
  String source = 'CONDUCTOR';
  final assignment = testAssignment(
    status: MobileAssignmentStatus.enServicio,
    serviceId: 'synthetic-service',
    startedAt: DateTime.utc(2026, 10, 20, 10),
  );
  MobileSafetyAttention row() => MobileSafetyAttention(
    id: 'synthetic-attention',
    operationKey: key ?? 'synthetic-admin-key',
    assignmentId: assignment.id,
    segmentId: 'synthetic-segment',
    incidentId: 'synthetic-incident',
    reason: 'UNSAFE_BREAKDOWN',
    detail: 'SYNTHETIC unsafe continuation',
    at: DateTime.utc(2026, 10, 20, 10, 1),
    source: source,
    safeStopConfirmed: false,
    driverPending: true,
    vehiclePending: true,
  );
  @override
  Future<List<MobileSafetyAttention>> ownAttention(String assignmentId) async =>
      visible ? [row()] : [];
  @override
  Future<MobileAssignment> interruptService(
    MobileAssignment a, {
    required String eventId,
    required String reason,
    required String detail,
    required bool safeStopConfirmed,
  }) async {
    posts++;
    key = eventId;
    safeStop = safeStopConfirmed;
    expect(reason, 'UNSAFE_BREAKDOWN');
    expect(detail, 'SYNTHETIC unsafe continuation');
    if (unknown) throw const MobileNetworkException('SYNTHETIC_UNKNOWN');
    visible = true;
    return testAssignment(status: MobileAssignmentStatus.interrupted);
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
            child: ServiceAttentionPanel(
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

  Future<void> fill(WidgetTester tester) async {
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Avería insegura').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField),
      'SYNTHETIC unsafe continuation',
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Registrar interrupción propia'));
  }

  testWidgets(
    'own interruption permits reporting risk before confirmation of safe stop',
    (tester) async {
      final g = _Gateway();
      await mount(tester, g);
      expect(g.posts, 0);
      await fill(tester);
      await tester.tap(find.text('Registrar interrupción propia'));
      await tester.pumpAndSettle();
      expect(g.posts, 1);
      expect(g.safeStop, false);
      expect(changes, 1);
      expect(blocks, [true, false]);
      expect(find.text('Registrar interrupción propia'), findsNothing);
      expect(find.textContaining('atención pendiente'), findsOneWidget);
    },
  );
  testWidgets('unknown stop is consulted by exact key without another post', (
    tester,
  ) async {
    final g = _Gateway()..unknown = true;
    await mount(tester, g);
    await fill(tester);
    await tester.tap(find.text('Registrar interrupción propia'));
    await tester.pumpAndSettle();
    expect(g.posts, 1);
    expect(changes, 0);
    expect(blocks.last, true);
    expect(
      find.textContaining('Interrupción sin confirmación'),
      findsOneWidget,
    );
    g.visible = true;
    await tester.ensureVisible(find.text('Consultar atención del servicio'));
    await tester.tap(find.text('Consultar atención del servicio'));
    await tester.pumpAndSettle();
    expect(g.posts, 1);
    expect(changes, 1);
    expect(blocks.last, false);
    expect(find.textContaining('Interrupción sin confirmación'), findsNothing);
  });
  testWidgets(
    'an interruption reported by ADMIN is consulted without impersonating that actor',
    (tester) async {
      final g = _Gateway()
        ..visible = true
        ..source = 'ADMIN_VERIFIED_REPORT';
      await mount(tester, g);
      expect(g.posts, 0);
      expect(changes, 1);
      expect(find.text('Registrar interrupción propia'), findsNothing);
      expect(
        find.textContaining('Comprobación ADMIN comunicada'),
        findsOneWidget,
      );
    },
  );
  testWidgets(
    'a pending external operation disables the interruption command',
    (tester) async {
      final g = _Gateway();
      await mount(tester, g, blocked: true);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(g.posts, 0);
    },
  );
}
