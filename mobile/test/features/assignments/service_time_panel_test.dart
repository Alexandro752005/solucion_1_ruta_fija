import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_time_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/service_time_panel.dart';

class _Gateway implements MobileServiceTimeGateway {
  int posts = 0;
  String? key;
  bool fail = false, visible = false;
  String status = 'COMPLETED';
  @override
  Future<MobileServiceTimeHistory> timeHistory(String assignmentId) async =>
      MobileServiceTimeHistory(
        status: status,
        segments: [
          MobileSegmentTime(
            ordinal: 1,
            originalStart: DateTime.utc(2026, 10, 20, 10),
            originalEnd: DateTime.utc(2026, 10, 20, 11),
            start: DateTime.utc(2026, 10, 20, 10, 1),
            end: DateTime.utc(2026, 10, 20, 10, 59),
          ),
        ],
        corrections: [],
        requests: visible ? [row()] : [],
      );
  MobileTimeRequest row() => MobileTimeRequest(
    id: 'synthetic-request',
    key: key!,
    reason: 'SYNTHETIC timing request',
    at: DateTime.utc(2026, 10, 20, 12),
  );
  @override
  Future<MobileTimeRequest> requestTimeCorrection(
    String assignmentId,
    String requestKey,
    String reason,
  ) async {
    posts++;
    key = requestKey;
    expect(reason, 'SYNTHETIC timing request');
    if (fail) throw const MobileNetworkException('SYNTHETIC_UNKNOWN');
    visible = true;
    return row();
  }
}

void main() {
  Future<void> mount(WidgetTester tester, _Gateway g) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ServiceTimePanel(
              assignmentId: 'synthetic-offer',
              gateway: g,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'shows original versus effective values and permits only a request reason',
    (tester) async {
      final g = _Gateway();
      await mount(tester, g);
      expect(find.textContaining('Original:'), findsOneWidget);
      expect(find.textContaining('Vigente para revisión:'), findsOneWidget);
      expect(find.byType(TextField), findsOneWidget);
      expect(g.posts, 0);
      await tester.enterText(
        find.byType(TextField),
        'SYNTHETIC timing request',
      );
      await tester.ensureVisible(find.text('Solicitar revisión de tiempos'));
      await tester.tap(find.text('Solicitar revisión de tiempos'));
      await tester.pumpAndSettle();
      expect(g.posts, 1);
      expect(find.textContaining('Pendiente de revisión'), findsOneWidget);
    },
  );
  testWidgets('unknown request is consulted by key without a second mutation', (
    tester,
  ) async {
    final g = _Gateway()..fail = true;
    await mount(tester, g);
    await tester.enterText(find.byType(TextField), 'SYNTHETIC timing request');
    await tester.ensureVisible(find.text('Solicitar revisión de tiempos'));
    await tester.tap(find.text('Solicitar revisión de tiempos'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Solicitud sin confirmación'), findsOneWidget);
    expect(g.posts, 1);
    g.visible = true;
    await tester.ensureVisible(find.text('Consultar tiempos y solicitudes'));
    await tester.tap(find.text('Consultar tiempos y solicitudes'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Solicitud sin confirmación'), findsNothing);
    expect(g.posts, 1);
  });
  testWidgets(
    'an ongoing service is consultation only with no correction form',
    (tester) async {
      final g = _Gateway()..status = 'IN_SERVICE';
      await mount(tester, g);
      expect(find.byType(TextField), findsNothing);
      expect(find.text('Solicitar revisión de tiempos'), findsNothing);
      expect(g.posts, 0);
    },
  );
  testWidgets(
    'a cancelled interrupted continuity permits a reason request with originals retained',
    (tester) async {
      final g = _Gateway()..status = 'CANCELLED';
      await mount(tester, g);
      expect(find.byType(TextField), findsOneWidget);
      expect(find.textContaining('Original:'), findsOneWidget);
      expect(g.posts, 0);
    },
  );
}
