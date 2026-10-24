import 'dart:convert';
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  MobileAssignmentRepository repository(
    http.Client client,
    MobileAssignmentCommandStore store, {
    String Function() clientEventIdFactory = _createEventId,
    bool enableInMemoryRetryQueue = false,
    MobileStartGpsProofProvider? startGpsProofProvider,
    Future<void> Function(MobileAssignment)? beforeComplete,
    void Function(MobileAssignment)? onStateChanged,
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
      enableInMemoryRetryQueue: enableInMemoryRetryQueue,
      startGpsProofProvider: startGpsProofProvider,
      beforeComplete: beforeComplete,
      onStateChanged: onStateChanged,
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

  test('does not retain an unknown result when the runtime retry queue is disabled', () async {
    final client = MockClient((request) async {
      expect(request.method, 'POST');
      throw http.ClientException('network interrupted');
    });
    final store = InMemoryAssignmentCommandStore();
    final gateway = repository(client, store);
    final assignment = MobileAssignment.fromJson(_assignmentJson());

    await expectLater(
      gateway.send(assignment, MobileAssignmentCommandType.accept),
      throwsA(isA<MobileNetworkException>()),
    );

    expect(await store.readForDriver(assignment.driverId), isEmpty);
    expect(await gateway.pendingForDriver(assignment.driverId), isEmpty);
    await expectLater(
      gateway.retry(
        MobilePendingAssignmentCommand(
          driverId: assignment.driverId,
          assignmentId: assignment.id,
          command: MobileAssignmentCommandType.accept,
          clientEventId: _createEventId(),
          version: assignment.version,
          occurredAt: DateTime.utc(2026, 9, 23, 11, 57),
          rejectionReason: null,
        ),
      ),
      throwsA(isA<StateError>()),
    );
  });

  test('retains the legacy in-memory idempotency event only when explicitly enabled', () async {
    final bodies = <Map<String, dynamic>>[];
    final methods = <String>[];
    var attempts = 0;
    final client = MockClient((request) async {
      methods.add(request.method);
      if (request.method == 'GET') {
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333',
        );
        return http.Response(jsonEncode(_assignmentJson()), 200);
      }
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
    final gateway = repository(client, store, enableInMemoryRetryQueue: true);
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
    expect(methods, ['POST', 'GET', 'POST']);
    expect(bodies, hasLength(2));
    expect(bodies[0], bodies[1]);
    for (final body in bodies) {
      expect(body.containsKey('driverId'), isFalse);
      expect(body.containsKey('organizationId'), isFalse);
    }
    expect(await store.readForDriver(assignment.driverId), isEmpty);
  });

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

  test(
    'sends start and completion to their online command endpoints',
    () async {
      final paths = <String>[];
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        paths.add(request.url.path);
        if (request.url.path.endsWith('/start')) {
          return http.Response(
            jsonEncode(
              _assignmentJson(
                status: 'EN_SERVICIO',
                version: 2,
                reservedAt: '2026-09-23T12:00:00Z',
                acceptedAt: '2026-09-23T12:00:00Z',
                startedAt: '2026-09-23T12:01:00Z',
              ),
            ),
            200,
          );
        }
        return http.Response(
          jsonEncode(
            _assignmentJson(
              status: 'COMPLETED',
              version: 3,
              reservedAt: '2026-09-23T12:00:00Z',
              acceptedAt: '2026-09-23T12:00:00Z',
              startedAt: '2026-09-23T12:01:00Z',
              completedAt: '2026-09-23T13:00:00Z',
            ),
          ),
          200,
        );
      });
      final store = InMemoryAssignmentCommandStore();
      final gateway = repository(client, store);
      final scheduled = MobileAssignment.fromJson(
        _assignmentJson(
          status: 'SCHEDULED',
          version: 1,
          reservedAt: '2026-09-23T12:00:00Z',
          acceptedAt: '2026-09-23T12:00:00Z',
        ),
      );
      final started = await gateway.send(
        scheduled,
        MobileAssignmentCommandType.start,
      );
      final completed = await gateway.send(
        started.assignment,
        MobileAssignmentCommandType.complete,
      );

      expect(paths, [
        '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333/start',
        '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333/complete',
      ]);
      expect(completed.assignment.status, MobileAssignmentStatus.completed);
      expect(await store.readForDriver(scheduled.driverId), isEmpty);
    },
  );

  test(
    'queries the current assignment before resending an uncertain command',
    () async {
      final store = InMemoryAssignmentCommandStore();
      final assignment = MobileAssignment.fromJson(
        _assignmentJson(status: 'SCHEDULED', version: 1),
      );
      final pending = MobilePendingAssignmentCommand(
        driverId: assignment.driverId,
        assignmentId: assignment.id,
        command: MobileAssignmentCommandType.accept,
        clientEventId: _createEventId(),
        version: 0,
        occurredAt: DateTime.utc(2026, 9, 23, 11, 57),
        rejectionReason: null,
      );
      await store.write(pending);
      var requests = 0;
      final client = MockClient((request) async {
        requests++;
        expect(request.method, 'GET');
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/33333333-3333-4333-8333-333333333333',
        );
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
        );
      });

      final recovered = await repository(
        client,
        store,
        enableInMemoryRetryQueue: true,
      ).retry(pending);

      expect(requests, 1);
      expect(recovered.replayed, isTrue);
      expect(recovered.assignment.status, MobileAssignmentStatus.scheduled);
      expect(await store.readForDriver(assignment.driverId), isEmpty);
    },
  );

  test('a new app session does not restore an uncertain command', () async {
    final assignment = MobileAssignment.fromJson(_assignmentJson());
    final command = MobilePendingAssignmentCommand(
      driverId: assignment.driverId,
      assignmentId: assignment.id,
      command: MobileAssignmentCommandType.accept,
      clientEventId: _createEventId(),
      version: assignment.version,
      occurredAt: DateTime.utc(2026, 9, 23, 11, 57),
      rejectionReason: null,
    );
    final activeSessionStore = MobileSessionAssignmentCommandStore();
    await activeSessionStore.write(command);

    final recreatedSessionStore = MobileSessionAssignmentCommandStore();

    expect(
      await activeSessionStore.readForDriver(assignment.driverId),
      hasLength(1),
    );
    expect(
      await recreatedSessionStore.readForDriver(assignment.driverId),
      isEmpty,
    );
  });
  test('completion waits for its punctual attempt and never stores a queued intention', () async {
    final ready = Completer<void>();
    var posts = 0;
    final store = InMemoryAssignmentCommandStore();
    final current = MobileAssignment.fromJson(
      _assignmentJson(
        status: 'EN_SERVICIO',
        version: 2,
        reservedAt: '2026-09-23T12:00:00Z',
        acceptedAt: '2026-09-23T12:00:00Z',
        startedAt: '2026-09-23T12:01:00Z',
      ),
    );
    final gateway = repository(
      MockClient((r) async {
        posts++;
        expect(ready.isCompleted, isTrue);
        return http.Response(
          jsonEncode(_assignmentJson(status: 'COMPLETED', version: 3)),
          200,
        );
      }),
      store,
      beforeComplete: (_) async => ready.future,
    );
    final command = gateway.send(current, MobileAssignmentCommandType.complete);
    await Future<void>.delayed(Duration.zero);
    expect(posts, 0);
    expect(await store.readForDriver(current.driverId), isEmpty);
    ready.complete();
    await command;
    expect(posts, 1);
    expect(await store.readForDriver(current.driverId), isEmpty);
  });
  test(
    'start carries a fresh OS proof without coordinates or a stored command',
    () async {
      var probes = 0;
      final store = InMemoryAssignmentCommandStore();
      final scheduled = MobileAssignment.fromJson(
        _assignmentJson(
          status: 'SCHEDULED',
          version: 1,
          reservedAt: '2026-09-23T12:00:00Z',
          acceptedAt: '2026-09-23T12:00:00Z',
        ),
      );
      final client = MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['gps'], {
          'permissionGranted': true,
          'locationServiceEnabled': true,
          'foreground': true,
        });
        expect(body.containsKey('latitude'), isFalse);
        return http.Response(
          jsonEncode(_assignmentJson(status: 'EN_SERVICIO', version: 2)),
          200,
        );
      });
      await repository(
        client,
        store,
        startGpsProofProvider: () async {
          probes++;
          return {
            'permissionGranted': true,
            'locationServiceEnabled': true,
            'foreground': true,
          };
        },
      ).send(scheduled, MobileAssignmentCommandType.start);
      expect(probes, 1);
      expect(await store.readForDriver(scheduled.driverId), isEmpty);
    },
  );
  test('a denied start proof performs no HTTP command and keeps no offline intention', () async {
    var calls = 0;
    final store = InMemoryAssignmentCommandStore();
    final scheduled = MobileAssignment.fromJson(
      _assignmentJson(
        status: 'SCHEDULED',
        version: 1,
        reservedAt: '2026-09-23T12:00:00Z',
        acceptedAt: '2026-09-23T12:00:00Z',
      ),
    );
    final client = MockClient((request) async {
      calls++;
      return http.Response('{}', 500);
    });
    await expectLater(
      repository(
        client,
        store,
        startGpsProofProvider: () async => throw StateError('SYNTHETIC_DENIED'),
      ).send(scheduled, MobileAssignmentCommandType.start),
      throwsA(isA<StateError>()),
    );
    expect(calls, 0);
    expect(await store.readForDriver(scheduled.driverId), isEmpty);
  });
  test('consulting a confirmed interrupted assignment notifies GPS without another mutation', () async {
    final seen = <MobileAssignment>[];
    var gets = 0;
    final gateway = repository(
      MockClient((request) async {
        gets++;
        expect(request.method, 'GET');
        return http.Response(
          jsonEncode(_assignmentJson(status: 'INTERRUPTED')),
          200,
        );
      }),
      InMemoryAssignmentCommandStore(),
      onStateChanged: seen.add,
    );
    final result = await gateway.get('33333333-3333-4333-8333-333333333333');
    expect(gets, 1);
    expect(seen.single.id, result.id);
    expect(seen.single.status, MobileAssignmentStatus.interrupted);
  });
}

String _createEventId() => '44444444-4444-4444-8444-444444444444';

Map<String, dynamic> _assignmentJson({
  String status = 'PENDING_RESPONSE',
  int version = 0,
  String? reservedAt,
  String? acceptedAt,
  String? startedAt,
  String? completedAt,
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
    'startedAt': startedAt,
    'completedAt': completedAt,
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
