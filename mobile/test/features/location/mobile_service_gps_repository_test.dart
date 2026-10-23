import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/location/data/mobile_service_gps_repository.dart';

Map<String, Object?> requestJson() => {
  'id': 'synthetic-request',
  'requestKey': 'synthetic-key',
  'status': 'PENDING',
  'effectiveStatus': 'PENDING',
  'fromAt': '2026-10-19T10:00:00Z',
  'toAt': '2026-10-19T11:00:00Z',
  'reviewDueAt': '2026-10-21T11:00:00Z',
  'grantExpiresAt': null,
};
void main() {
  MobileServiceGpsRepository subject(http.Client client, _Tokens tokens) =>
      MobileServiceGpsRepository(
        MobileApiClient(
          httpClient: client,
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://synthetic.rutafija.test/api/v1',
          ),
          accessTokenProvider: tokens,
        ),
      );
  test(
    'ordinary consultation receives request metadata without a GPS track',
    () async {
      final repo = subject(
        MockClient((r) async {
          expect(r.method, 'GET');
          expect(r.url.queryParameters, {
            'serviceId': 'synthetic-service',
            'limit': '50',
            'offset': '0',
          });
          return http.Response(jsonEncode([requestJson()]), 200);
        }),
        _Tokens(),
      );
      final rows = await repo.gpsRequests('synthetic-service');
      expect(rows.single.status, 'PENDING');
    },
  );
  test('request identifies only the service and stable key, leaving its actual interval to the server', () async {
    final repo = subject(
      MockClient((r) async {
        expect(jsonDecode(r.body), {
          'serviceId': 'synthetic-service',
          'requestKey': 'synthetic-key',
        });
        return http.Response(jsonEncode(requestJson()), 200);
      }),
      _Tokens(),
    );
    expect(
      (await repo.requestGps('synthetic-service', 'synthetic-key')).requestKey,
      'synthetic-key',
    );
  });
  test('reauthentication failure is neither refreshed nor resubmitted and does not silently end consultation', () async {
    final tokens = _Tokens();
    var calls = 0;
    final repo = subject(
      MockClient((r) async {
        calls++;
        return http.Response(
          jsonEncode({
            'code': 'AUTH_INVALID_CREDENTIALS',
            'message': 'SYNTHETIC_REAUTH_FAILED',
          }),
          401,
        );
      }),
      tokens,
    );
    await expectLater(
      repo.deliverGps('synthetic-request', 'SYNTHETIC_WRONG'),
      throwsA(isA<MobileApiException>()),
    );
    expect(calls, 1);
    expect(tokens.refreshes, 0);
    expect(tokens.invalidations, 0);
  });
  test('restricted delivery decodes its expiry and never renders coordinates via toString', () async {
    final repo = subject(
      MockClient((r) async {
        expect(jsonDecode(r.body), {
          'currentPassword': 'SYNTHETIC_LOCAL_ONLY',
          'limit': 1000,
          'offset': 0,
        });
        return http.Response(
          jsonEncode({
            'grantExpiresAt': '2026-10-19T11:15:00Z',
            'items': [
              {
                'latitude': -6.77,
                'longitude': -79.84,
                'accuracyM': 5,
                'capturedAt': '2026-10-19T11:00:00Z',
              },
            ],
          }),
          200,
        );
      }),
      _Tokens(),
    );
    final result = await repo.deliverGps(
      'synthetic-request',
      'SYNTHETIC_LOCAL_ONLY',
    );
    expect(result.items.single.latitude, -6.77);
    expect(result.items.single.toString(), isNot(contains('-6.77')));
    expect(result.expiresAt, DateTime.utc(2026, 10, 19, 11, 15));
  });
}

final class _Tokens implements MobileAccessTokenProvider {
  int refreshes = 0, invalidations = 0;
  @override
  String get accessToken => 'SYNTHETIC_TOKEN';
  @override
  Future<bool> refreshAccessToken() async {
    refreshes++;
    return true;
  }

  @override
  Future<void> invalidateSession() async {
    invalidations++;
  }
}
