import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/incidents/data/mobile_incident_repository.dart';
import 'package:ruta_fija_conductor/features/incidents/domain/mobile_incident_models.dart';

void main() {
  for (final category in MobileIncidentCategory.values) {
    test('catalog accepts explicit ${category.apiValue} without coercion', () {
      expect(MobileIncidentCategory.fromApi(category.apiValue), category);
      expect(category.label, isNotEmpty);
    });
  }
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  MobileIncidentRepository repository(http.Client client) {
    return MobileIncidentRepository(
      MobileApiClient(
        httpClient: client,
        environment: environment,
        accessTokenProvider: const _StaticTokenProvider(),
      ),
      utcClock: () => DateTime.utc(2026, 9, 24, 15, 30),
    );
  }

  test('system validity incident is not attributed to a human reporter', () {
    final data = _incidentJson();
    data['source'] = 'SYSTEM';
    data.remove('reportedById');
    data['reportedByName'] = 'Sistema';
    final incident = MobileIncident.fromJson(data);
    expect(incident.source, MobileIncidentSource.system);
    expect(incident.reportedById, isNull);
    expect(incident.reportedByName, 'Sistema');
    expect(incident.canBeClosedBy('current-user-id'), isFalse);
  });

  test(
    'lists only authenticated conductor incidents in reverse report order',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/v1/mobile/incidents');
        expect(request.url.queryParameters, {
          'page': '0',
          'size': '20',
          'sort': 'reportedAt,desc',
        });
        expect(request.headers['authorization'], 'Bearer access-token');
        return http.Response(
          jsonEncode({
            'items': [_incidentJson()],
            'page': 0,
            'size': 20,
            'totalItems': 1,
            'totalPages': 1,
          }),
          200,
        );
      });

      final page = await repository(client).list();

      expect(page.items, hasLength(1));
      expect(page.items.single.category, MobileIncidentCategory.averia);
      expect(page.items.single.source, MobileIncidentSource.mobileApp);
    },
  );

  test(
    'reports only permitted incident fields for the authenticated conductor',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/mobile/incidents');
        final body = Map<String, dynamic>.from(jsonDecode(request.body));
        expect(body, {
          'category': 'RETRASO',
          'description': 'Demora confirmada en el recorrido.',
          'occurredAt': '2026-09-24T15:30:00.000Z',
          'severity': 'LOW',
          'operationKey': '72ce5b57-e282-467e-8d80-676da990b88e',
        });
        for (final forbidden in const [
          'driverId',
          'userId',
          'organizationId',
          'tenantId',
          'source',
          'status',
        ]) {
          expect(body.containsKey(forbidden), isFalse);
        }
        return http.Response(
          jsonEncode(_incidentJson(category: 'RETRASO')),
          201,
        );
      });

      final created = await repository(client).create(
        category: MobileIncidentCategory.retraso,
        severity: MobileIncidentSeverity.low,
        operationKey: '72ce5b57-e282-467e-8d80-676da990b88e',
        description: '  Demora confirmada en el recorrido.  ',
      );

      expect(created.category, MobileIncidentCategory.retraso);
      expect(created.status, MobileIncidentStatus.open);
    },
  );

  test(
    'closes an existing own incident without sending identity or status',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/mobile/incidents/44444444-4444-4444-8444-444444444444/close',
        );
        expect(request.headers['authorization'], 'Bearer access-token');
        final body = Map<String, dynamic>.from(jsonDecode(request.body));
        expect(body, {'version': 3});
        for (final forbidden in const [
          'driverId',
          'userId',
          'organizationId',
          'tenantId',
          'source',
          'status',
          'driverClosedAt',
        ]) {
          expect(body.containsKey(forbidden), isFalse);
        }
        return http.Response(
          jsonEncode(
            _incidentJson(
              status: 'CLOSED_BY_DRIVER',
              version: 4,
              driverClosedById: '11111111-1111-4111-8111-111111111111',
              driverClosedByName: 'Conductora de prueba',
              driverClosedAt: '2026-09-24T15:31:00Z',
            ),
          ),
          200,
        );
      });

      final closed = await repository(client)
          .close('44444444-4444-4444-8444-444444444444', version: 3);

      expect(closed.status, MobileIncidentStatus.driverClosed);
      expect(closed.driverClosedByName, 'Conductora de prueba');
      expect(
        closed.canBeClosedBy('11111111-1111-4111-8111-111111111111'),
        isFalse,
      );
    },
  );

  test(
    'rejects a blank or oversized description before calling the API',
    () async {
      final client = MockClient(
        (_) async => throw StateError('No debe llamarse'),
      );
      final gateway = repository(client);

      await expectLater(
        gateway.create(
          category: MobileIncidentCategory.otro,
          severity: MobileIncidentSeverity.low,
          description: '   ',
        ),
        throwsArgumentError,
      );
      await expectLater(
        gateway.create(
          category: MobileIncidentCategory.otro,
          severity: MobileIncidentSeverity.low,
          description: 'a' * 1001,
        ),
        throwsArgumentError,
      );
    },
  );
}

Map<String, dynamic> _incidentJson({
  String category = 'AVERIA',
  String status = 'OPEN',
  int version = 0,
  String? driverClosedById,
  String? driverClosedByName,
  String? driverClosedAt,
}) {
  return {
    'id': '44444444-4444-4444-8444-444444444444',
    'version': version,
    'driverId': '22222222-2222-4222-8222-222222222222',
    'driverName': 'Conductora de prueba',
    'assignmentId': null,
    'category': category,
    'source': 'MOBILE_APP',
    'status': status,
    'description': 'Demora confirmada en el recorrido.',
    'reportedById': '11111111-1111-4111-8111-111111111111',
    'reportedByName': 'Conductora de prueba',
    'reportedAt': '2026-09-24T15:30:00Z',
    'followUpNote': null,
    'followedUpById': null,
    'followedUpByName': null,
    'followedUpAt': null,
    'resolvedAt': null,
    'driverClosedById': driverClosedById,
    'driverClosedByName': driverClosedByName,
    'driverClosedAt': driverClosedAt,
    'createdAt': '2026-09-24T15:30:00Z',
    'updatedAt': '2026-09-24T15:30:00Z',
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
