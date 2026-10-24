import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_service_time_repository.dart';

void main() {
  MobileServiceTimeRepository repo(http.Client client, _Tokens tokens) =>
      MobileServiceTimeRepository(
        MobileApiClient(
          httpClient: client,
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://synthetic.rutafija.test/api/v1',
          ),
          accessTokenProvider: tokens,
        ),
      );
  test('conductor submits only request key and reason, never corrected times', () async {
    final s = repo(
      MockClient((r) async {
        expect(
          r.url.path,
          '/api/v1/mobile/assignments/synthetic-offer/time-correction-requests',
        );
        expect(r.method, 'POST');
        expect(jsonDecode(r.body), {
          'requestKey': 'synthetic-key',
          'reason': 'SYNTHETIC timing request',
        });
        return http.Response(
          jsonEncode({
            'id': 'synthetic-request',
            'requestKey': 'synthetic-key',
            'reason': 'SYNTHETIC timing request',
            'requestedAt': '2026-10-20T10:00:00Z',
            'correctionId': null,
          }),
          200,
        );
      }),
      _Tokens(),
    );
    expect(
      (await s.requestTimeCorrection(
        'synthetic-offer',
        'synthetic-key',
        'SYNTHETIC timing request',
      )).key,
      'synthetic-key',
    );
  });
  test(
    'authorization failure does not refresh or repeat the correction request',
    () async {
      final tokens = _Tokens();
      var calls = 0;
      final s = repo(
        MockClient((r) async {
          calls++;
          return http.Response(
            jsonEncode({'code': 'AUTH_TOKEN_INVALID', 'message': 'SYNTHETIC'}),
            401,
          );
        }),
        tokens,
      );
      await expectLater(
        s.requestTimeCorrection(
          'synthetic-offer',
          'synthetic-key',
          'SYNTHETIC',
        ),
        throwsA(isA<MobileApiException>()),
      );
      expect(calls, 1);
      expect(tokens.refreshes, 0);
    },
  );
  test('own history preserves original and effective time values without an edit command', () async {
    final s = repo(
      MockClient((r) async {
        expect(r.method, 'GET');
        return http.Response(
          jsonEncode({
            'status': 'COMPLETED',
            'segments': [
              {
                'ordinal': 1,
                'originalStartedAt': '2026-10-20T10:00:00Z',
                'originalEndedAt': '2026-10-20T10:30:00Z',
                'startedAt': '2026-10-20T10:01:00Z',
                'endedAt': '2026-10-20T10:29:00Z',
              },
            ],
            'corrections': [],
            'requests': [],
          }),
          200,
        );
      }),
      _Tokens(),
    );
    final h = await s.timeHistory('synthetic-offer');
    expect(h.segments.single.originalStart, DateTime.utc(2026, 10, 20, 10));
    expect(h.segments.single.start, DateTime.utc(2026, 10, 20, 10, 1));
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
