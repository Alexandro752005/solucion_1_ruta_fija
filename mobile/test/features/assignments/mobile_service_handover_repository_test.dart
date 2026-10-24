import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_handover_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  final a = testAssignment(
    serviceId: 'synthetic-service',
    handoverId: 'synthetic-handover',
    status: MobileAssignmentStatus.scheduled,
    version: 4,
    acceptedAt: DateTime.utc(2026, 10, 20, 10),
    reservedAt: DateTime.utc(2026, 10, 20, 10),
  );
  MobileServiceHandoverRepository repo(
    http.Client client,
    _Tokens tokens, {
    Future<Map<String, Object>> Function()? proof,
  }) => MobileServiceHandoverRepository(
    MobileApiClient(
      httpClient: client,
      environment: RouteFijaEnvironment.fromApiBaseUrl(
        'https://synthetic.rutafija.test/api/v1',
      ),
      accessTokenProvider: tokens,
    ),
    gpsProof: proof,
    now: () => DateTime.utc(2026, 10, 20, 10, 1),
  );
  Future<Map<String, Object>> proof() async => {
    'permissionGranted': true,
    'locationServiceEnabled': true,
    'foreground': true,
  };
  test('assumption requires explicit readiness and permission before a single online POST', () async {
    var posts = 0;
    final r = repo(
      MockClient((request) async {
        posts++;
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/${a.id}/assume-handover',
        );
        expect(jsonDecode(request.body), {
          'clientEventId': 'synthetic-event',
          'version': 4,
          'occurredAt': '2026-10-20T10:01:00.000Z',
          'gps': await proof(),
          'readyConfirmed': true,
        });
        return http.Response(
          jsonEncode({
            'id': a.id,
            'driverId': a.driverId,
            'driverName': 'CONDUCTOR-SYNTHETIC',
            'groupName': 'GRUPO-SYNTHETIC',
            'vehiclePlate': 'UNIDAD-SYNTHETIC',
            'originText': 'SYNTHETIC_ORIGIN',
            'destinationText': 'SYNTHETIC_END',
            'status': 'EN_SERVICIO',
            'responseMode': 'MOBILE_CONFIRMATION',
            'version': 5,
            'handoverId': a.handoverId,
            'scheduledAt': '2026-10-20T10:00:00Z',
            'scheduledEndAt': '2026-10-20T11:00:00Z',
          }),
          200,
        );
      }),
      _Tokens(),
      proof: proof,
    );
    expect(
      (await r.assumeHandover(
        a,
        eventId: 'synthetic-event',
        readyConfirmed: true,
      )).status,
      MobileAssignmentStatus.enServicio,
    );
    expect(posts, 1);
  });
  test('missing permission or readiness produces no HTTP mutation', () async {
    var posts = 0;
    final r = repo(
      MockClient((_) async {
        posts++;
        return http.Response('{}', 500);
      }),
      _Tokens(),
    );
    await expectLater(
      r.assumeHandover(a, eventId: 'synthetic-event', readyConfirmed: false),
      throwsA(isA<MobileHandoverPreconditionException>()),
    );
    await expectLater(
      r.assumeHandover(a, eventId: 'synthetic-event', readyConfirmed: true),
      throwsA(isA<MobileHandoverPreconditionException>()),
    );
    expect(posts, 0);
  });
  test('an authentication failure does not refresh credentials or resend assumption', () async {
    var posts = 0;
    final tokens = _Tokens();
    final r = repo(
      MockClient((_) async {
        posts++;
        return http.Response(
          jsonEncode({'code': 'AUTH_TOKEN_INVALID', 'message': 'SYNTHETIC'}),
          401,
        );
      }),
      tokens,
      proof: proof,
    );
    await expectLater(
      r.assumeHandover(a, eventId: 'synthetic-event', readyConfirmed: true),
      throwsA(isA<MobileApiException>()),
    );
    expect(posts, 1);
    expect(tokens.refreshes, 0);
  });
  test('own consultation preserves server original start, delay and exact resolution key', () async {
    final r = repo(
      MockClient((request) async {
        expect(request.method, 'GET');
        return http.Response(
          jsonEncode({
            'service': {
              'status': 'IN_SERVICE',
              'originalStartedAt': '2026-10-20T10:00:00Z',
              'startOverdue': false,
            },
            'handovers': [
              {
                'id': 'synthetic-handover',
                'incomingAssignmentId': a.id,
                'outgoingAssignmentId': 'synthetic-outgoing',
                'state': 'ASSUMED',
                'reason': 'SYNTHETIC',
                'pointReference': 'SYNTHETIC_POINT',
                'resolutionKey': 'synthetic-event',
                'resolvedAt': '2026-10-20T10:01:00Z',
                'pauseSeconds': 30,
              },
            ],
          }),
          200,
        );
      }),
      _Tokens(),
    );
    final v = await r.handoverView(a.id);
    expect(v.originalStartedAt, DateTime.utc(2026, 10, 20, 10));
    expect(v.rows.single.resolutionKey, 'synthetic-event');
    expect(v.rows.single.pauseSeconds, 30);
  });
}

class _Tokens implements MobileAccessTokenProvider {
  int refreshes = 0;
  @override
  String get accessToken => 'SYNTHETIC';
  @override
  Future<bool> refreshAccessToken() async {
    refreshes++;
    return true;
  }

  @override
  Future<void> invalidateSession() async {}
}
