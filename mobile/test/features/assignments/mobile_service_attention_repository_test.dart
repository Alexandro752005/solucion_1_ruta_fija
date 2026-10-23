import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_attention_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  final a = testAssignment(
    status: MobileAssignmentStatus.enServicio,
    version: 5,
  );
  MobileServiceAttentionRepository repo(http.Client client, _Tokens tokens) =>
      MobileServiceAttentionRepository(
        MobileApiClient(
          httpClient: client,
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://synthetic.rutafija.test/api/v1',
          ),
          accessTokenProvider: tokens,
        ),
        now: () => DateTime.utc(2026, 10, 20, 10),
      );
  Map<String, Object> response() => {
    'id': a.id,
    'driverId': a.driverId,
    'driverName': 'CONDUCTOR-SYNTHETIC',
    'groupName': 'GRUPO-SYNTHETIC',
    'vehiclePlate': 'UNIDAD-SYNTHETIC',
    'status': 'INTERRUPTED',
    'responseMode': 'MOBILE_CONFIRMATION',
    'version': 6,
    'scheduledAt': '2026-10-20T10:00:00Z',
    'scheduledEndAt': '2026-10-20T11:00:00Z',
    'originText': 'SYNTHETIC_ORIGIN',
    'destinationText': 'SYNTHETIC_DESTINATION',
  };
  test('submits versioned own interruption without claiming safe stop or completion', () async {
    final r = repo(
      MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/${a.id}/interrupt',
        );
        expect(jsonDecode(request.body), {
          'clientEventId': 'synthetic-key',
          'version': 5,
          'occurredAt': '2026-10-20T10:00:00.000Z',
          'reason': 'UNSAFE_BREAKDOWN',
          'detail': 'SYNTHETIC unsafe continuation',
          'safeStopConfirmed': false,
        });
        return http.Response(jsonEncode(response()), 200);
      }),
      _Tokens(),
    );
    expect(
      (await r.interruptService(
        a,
        eventId: 'synthetic-key',
        reason: 'UNSAFE_BREAKDOWN',
        detail: 'SYNTHETIC unsafe continuation',
        safeStopConfirmed: false,
      )).status,
      MobileAssignmentStatus.interrupted,
    );
  });
  test(
    'does not refresh or replay an interruption rejected by authentication',
    () async {
      final tokens = _Tokens();
      var posts = 0;
      final r = repo(
        MockClient((request) async {
          posts++;
          return http.Response(
            jsonEncode({'code': 'AUTH_TOKEN_INVALID', 'message': 'SYNTHETIC'}),
            401,
          );
        }),
        tokens,
      );
      await expectLater(
        r.interruptService(
          a,
          eventId: 'synthetic-key',
          reason: 'UNSAFE_BREAKDOWN',
          detail: 'SYNTHETIC',
          safeStopConfirmed: false,
        ),
        throwsA(isA<MobileApiException>()),
      );
      expect(posts, 1);
      expect(tokens.refreshes, 0);
    },
  );
  test('mismatched successful response remains unconfirmed rather than fabricating a stop', () async {
    final r = repo(
      MockClient(
        (_) async => http.Response(
          jsonEncode({...response(), 'id': 'another-synthetic-offer'}),
          200,
        ),
      ),
      _Tokens(),
    );
    await expectLater(
      r.interruptService(
        a,
        eventId: 'synthetic-key',
        reason: 'UNSAFE_BREAKDOWN',
        detail: 'SYNTHETIC',
        safeStopConfirmed: false,
      ),
      throwsFormatException,
    );
  });
  test('own attention consultation contains resource decisions without any command', () async {
    final r = repo(
      MockClient((request) async {
        expect(request.method, 'GET');
        expect(
          request.url.path,
          '/api/v1/mobile/assignments/${a.id}/attention',
        );
        return http.Response(
          jsonEncode([
            {
              'id': 'synthetic-attention',
              'operationKey': 'synthetic-key',
              'assignmentId': a.id,
              'segmentId': 'synthetic-segment',
              'incidentId': 'synthetic-incident',
              'reason': 'UNSAFE_BREAKDOWN',
              'detail': 'SYNTHETIC',
              'interruptedAt': '2026-10-20T10:00:00Z',
              'source': 'CONDUCTOR',
              'safeStopConfirmed': false,
              'driverPending': false,
              'vehiclePending': true,
            },
          ]),
          200,
        );
      }),
      _Tokens(),
    );
    final row = (await r.ownAttention(a.id)).single;
    expect(row.vehiclePending, true);
    expect(row.driverPending, false);
    expect(row.safeStopConfirmed, false);
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
