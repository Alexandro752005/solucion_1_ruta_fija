import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_route_models.dart';

void main() {
  test('legacy route never borrows a current catalog definition', () {
    final r = MobileAssignedRoute.fromJson({
      'assignmentId': 'service-a',
      'referenceStatus': 'LEGACY_UNRECORDED',
    });
    expect(r.versionId, isNull);
    expect(r.stops, isEmpty);
    expect(r.declaredAt, isNull);
  });
  test('preserves ordered version and distinguishes declaration from terminal evidence', () {
    final r = MobileAssignedRoute.fromJson({
      'assignmentId': 'service-a',
      'referenceStatus': 'CAPTURED',
      'versionId': 'version-a',
      'route': {
        'name': 'Synthetic route',
        'revision': 1,
        'stops': [
          {'key': 's', 'name': 'Start', 'kind': 'START'},
          {'key': 'm', 'name': 'Middle', 'kind': 'INTERMEDIATE'},
          {'key': 'e', 'name': 'End', 'kind': 'END'},
        ],
      },
      'declaration': {
        'declaredAt': '2026-10-19T12:00:00Z',
        'terminalResult': 'NOT_CORROBORATED_NO_POINT',
      },
    });
    expect(r.stops.map((s) => s.kind), ['START', 'INTERMEDIATE', 'END']);
    expect(r.terminalResult, 'NOT_CORROBORATED_NO_POINT');
    expect(r.declaredAt, isNotNull);
  });
}
