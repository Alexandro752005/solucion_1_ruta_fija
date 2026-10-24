import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_route_models.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/assignment_detail_page.dart';

import '../../support/mobile_fakes.dart';

final class _RouteFixture
    implements MobileAssignmentGateway, MobileRouteGateway {
  final delegate = FakeMobileAssignmentGateway();
  @override
  Future<MobileAssignment> get(String id) => delegate.get(id);
  @override
  Future<MobileAssignmentPage> list({
    MobileAssignmentStatus? status,
    int page = 0,
    int size = 30,
  }) => delegate.list(status: status, page: page, size: size);
  @override
  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(String id) =>
      delegate.pendingForDriver(id);
  @override
  Future<MobileAssignmentCommandResult> send(
    MobileAssignment a,
    MobileAssignmentCommandType c, {
    String? rejectionReason,
  }) => delegate.send(a, c, rejectionReason: rejectionReason);
  @override
  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand c,
  ) => delegate.retry(c);
  @override
  Future<MobileAssignedRoute> assignedRoute(String id) async =>
      MobileAssignedRoute(
        assignmentId: id,
        referenceStatus: 'CAPTURED',
        versionId: 'version-a',
        name: 'Synthetic captured route',
        revision: 1,
        stops: const [
          MobileRouteStop(key: 's', name: 'Synthetic start', kind: 'START'),
          MobileRouteStop(
            key: 'm',
            name: 'Synthetic middle',
            kind: 'INTERMEDIATE',
          ),
          MobileRouteStop(key: 'e', name: 'Synthetic end', kind: 'END'),
        ],
        declaredAt: DateTime.utc(2026, 10, 19),
        terminalResult: 'NOT_CORROBORATED_NO_POINT',
      );
}

void main() {
  testWidgets(
    'shows captured ordered own route and keeps terminal evidence distinct',
    (tester) async {
      final fixture = _RouteFixture();
      final a = testAssignment();
      await tester.pumpWidget(
        MaterialApp(
          home: AssignmentDetailPage(
            assignmentId: a.id,
            driverId: a.driverId,
            driverGateway: fixture,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Ruta asignada: solo consulta'),
        150,
        scrollable: find.byType(Scrollable).last,
      );
      expect(
        find.text('Synthetic captured route · revisión 1'),
        findsOneWidget,
      );
      expect(find.text('2. Synthetic middle · INTERMEDIATE'), findsOneWidget);
      expect(find.text('Terminal: NOT_CORROBORATED_NO_POINT'), findsOneWidget);
      expect(fixture.delegate.sentCommands, isEmpty);
      expect(find.textContaining('Editar ruta'), findsNothing);
    },
  );
}
