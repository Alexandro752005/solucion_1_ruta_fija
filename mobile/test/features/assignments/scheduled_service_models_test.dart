import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

Map<String, dynamic> fixture() => {
  'id': 'synthetic-offer',
  'serviceId': 'synthetic-service',
  'status': 'SCHEDULED',
  'responseMode': 'MOBILE_CONFIRMATION',
  'version': 2,
  'driverId': 'synthetic-driver',
  'driverName': 'CONDUCTOR-synthetic',
  'groupName': 'Synthetic group',
  'vehiclePlate': 'SYN-123',
  'originText': 'Synthetic origin',
  'destinationText': 'Synthetic destination',
  'scheduledAt': '2030-01-01T12:00:00Z',
  'scheduledEndAt': '2030-01-01T13:00:00Z',
  'reservedAt': '2030-01-01T11:00:00Z',
  'acceptedAt': '2030-01-01T11:00:00Z',
};
void main() {
  test('keeps service identity separate from the individual offer', () {
    final a = MobileAssignment.fromJson(fixture());
    expect(a.id, 'synthetic-offer');
    expect(a.serviceId, 'synthetic-service');
    expect(a.canStart, isTrue);
  });
  test('legacy unknown service is not reconstructed', () {
    final data = fixture()..remove('serviceId');
    expect(MobileAssignment.fromJson(data).serviceId, isNull);
  });
  test('an accepted standby requires assumption instead of the ordinary start command', () {
    final data = fixture()..['handoverId'] = 'synthetic-handover';
    final a = MobileAssignment.fromJson(data);
    expect(a.handoverId, 'synthetic-handover');
    expect(a.canStart, isFalse);
    expect(a.allows(MobileAssignmentCommandType.start), isFalse);
  });
  test('transferred participation is terminal without claiming completion', () {
    final a = MobileAssignment.fromJson(fixture()..['status'] = 'TRANSFERRED');
    expect(a.status.isTerminal, isTrue);
    expect(a.canComplete, isFalse);
    expect(a.completedAt, isNull);
  });
  test(
    'reservation without authentic mobile acceptance cannot enable start',
    () {
      final data = fixture()..remove('acceptedAt');
      expect(MobileAssignment.fromJson(data).canStart, isFalse);
      final direct = fixture()..['responseMode'] = 'ADMIN_DIRECT';
      expect(MobileAssignment.fromJson(direct).canStart, isFalse);
    },
  );
  test('a new offer does not inherit the old acceptance or start ability', () {
    final data = fixture()
      ..['id'] = 'synthetic-new-offer'
      ..['status'] = 'PENDING_RESPONSE'
      ..remove('acceptedAt')
      ..remove('reservedAt');
    final a = MobileAssignment.fromJson(data);
    expect(a.serviceId, 'synthetic-service');
    expect(a.requiresMobileResponse, isTrue);
    expect(a.canStart, isFalse);
  });
}
