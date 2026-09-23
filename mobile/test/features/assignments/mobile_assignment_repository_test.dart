import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  MobileAssignmentRepository repository(
    http.Client client,
    InMemoryAssignmentCommandStore store, {
    String Function() clientEventIdFactory = _createEventId,
  }) {
    return MobileAssignmentRepository(
      MobileApiClient(
        httpClient: client,
        environment: environment,
        accessTokenProvider: const _StaticTokenProvider(),
      ),
      store,
      clientEventIdFactory: clientEventIdFactory,
      utcClock: () => DateTime.utc(2026, 9, 23, 11, 57),
    );
  }

  test(
    'lists only the authenticated conductor assignment route and filters',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/mobile/assignments');
        expect(request.url.queryParameters, {
          'page': '0',
          'size': '30',
          'sort': 'scheduledAt,desc',
          'status': 'PENDING_RESPONSE',
        });
        expect(request.headers['authorization'], 'Bearer access-token');
        return http.Response(
          jsonEncode({
            'items': [_assignmentJson()],
            'page': 0,
            'size': 30,
            'totalItems': 1,
            'totalPages': 1,
          }),
          200,
        );
      });

      final page = await repository(
        client,
        InMemoryAssignmentCommandStore(),
      ).list(status: MobileAssignmentStatus.pendingResponse);

      expect(page.items, hasLength(1));
      expect(page.items.single.status, MobileAssignmentStatus.pendingResponse);
      expect(page.items.single.requiresMobileResponse, isTrue);
    },
  );

  test(
    'keeps exactly the same durable event after an unknown network result',
    () async {
      final bodies = <Map<String, dynamic>>[];
      var attempts = 0;
      final client = MockClient((request) async {
        attempts++;
        bodies.add(Map<String, dynamic>.from(jsonDecode(request.body)));
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333/accept',
        );
        if (attempts == 1) {
          throw http.ClientException('network interrupted');
        }
        return http.Response(
          jsonEncode(
            _assignmentJson(
              status: 'SCHEDULED',
              version: 1,
              reservedAt: '2026-09-23T12:00:00Z',
              acceptedAt: '2026-09-23T12:00:00Z',
            ),
          ),
          200,
          headers: {'x-idempotent-replay': 'true'},
        );
      });
      final store = InMemoryAssignmentCommandStore();
      final gateway = repository(client, store);
      final assignment = MobileAssignment.fromJson(_assignmentJson());

      await expectLater(
        gateway.send(assignment, MobileAssignmentCommandType.accept),
        throwsA(isA<MobileNetworkException>()),
      );
      final pending = await store.readForDriver(assignment.driverId);
      expect(pending, hasLength(1));
      expect(pending.single.clientEventId, _createEventId());
      expect(pending.single.version, 0);

      final recovered = await gateway.retry(pending.single);

      expect(recovered.replayed, isTrue);
      expect(recovered.assignment.status, MobileAssignmentStatus.scheduled);
      expect(bodies, hasLength(2));
      expect(bodies[0], bodies[1]);
      for (final body in bodies) {
        expect(body.containsKey('driverId'), isFalse);
        expect(body.containsKey('organizationId'), isFalse);
      }
      expect(await store.readForDriver(assignment.driverId), isEmpty);
    },
  );

  test(
    'removes a command when the server gives a definitive version conflict',
    () async {
      final client = MockClient((_) async {
        return http.Response(
          jsonEncode({
            'status': 409,
            'code': 'RESOURCE_VERSION_CONFLICT',
            'message': 'Actualice',
            'errors': [],
          }),
          409,
        );
      });
      final store = InMemoryAssignmentCommandStore();
      final assignment = MobileAssignment.fromJson(_assignmentJson());

      await expectLater(
        repository(
          client,
          store,
        ).send(assignment, MobileAssignmentCommandType.accept),
        throwsA(isA<MobileApiException>()),
      );

      expect(await store.readForDriver(assignment.driverId), isEmpty);
    },
  );

  test(
    'keeps the optional rejection reason inside the idempotent command only',
    () async {
      final client = MockClient((request) async {
        final body = Map<String, dynamic>.from(jsonDecode(request.body));
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333/reject',
        );
        expect(body['reason'], 'Imprevisto familiar');
        expect(body.containsKey('driverId'), isFalse);
        expect(body.containsKey('organizationId'), isFalse);
        return http.Response(
          jsonEncode(_assignmentJson(status: 'REJECTED', version: 1)),
          200,
        );
      });
      final assignment = MobileAssignment.fromJson(_assignmentJson());

      final result = await repository(client, InMemoryAssignmentCommandStore())
          .send(
            assignment,
            MobileAssignmentCommandType.reject,
            rejectionReason: '  Imprevisto familiar  ',
          );

      expect(result.assignment.status, MobileAssignmentStatus.rejected);
    },
  );
}

String _createEventId() => '44444444-4444-4444-8444-444444444444';

Map<String, dynamic> _assignmentJson({
  String status = 'PENDING_RESPONSE',
  int version = 0,
  String? reservedAt,
  String? acceptedAt,
}) {
  return {
    'id': '33333333-3333-4333-8333-333333333333',
    'status': status,
    'responseMode': 'MOBILE_CONFIRMATION',
    'responseDeadlineAt': '2026-09-24T12:00:00Z',
    'acceptedAt': acceptedAt,
    'rejectedAt': null,
    'rejectionReason': null,
    'expiredAt': null,
    'version': version,
    'driverId': '22222222-2222-4222-8222-222222222222',
    'driverName': 'Conductora de prueba',
    'groupId': 'group-1',
    'groupName': 'Grupo Norte',
    'vehicleId': 'vehicle-1',
    'vehiclePlate': 'ABC-123',
    'originText': 'Terminal Norte',
    'destinationText': 'Terminal Sur',
    'scheduledAt': '2026-09-24T14:00:00Z',
    'scheduledEndAt': '2026-09-24T15:00:00Z',
    'reservedAt': reservedAt,
    'startedAt': null,
    'completedAt': null,
    'cancelledAt': null,
    'cancellationReason': null,
    'notes': 'Prueba',
    'createdById': 'user-1',
    'createdByName': 'ADMIN',
    'createdAt': '2026-09-23T10:00:00Z',
    'updatedAt': '2026-09-23T10:00:00Z',
  };
}

final class _StaticTokenProvider implements MobileAccessTokenProvider {
  const _StaticTokenProvider();

  @override
  String get accessToken => 'access-token';

  @override
  Future<void> invalidateSession() async {}

  @override
  Future<bool> refreshAccessToken() async => false;
}
