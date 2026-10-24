import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_access_repository.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );
  const token = 'oa1.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ';

  test('uses separate purpose headers without bearer, refresh, or credentials in URLs', () async {
    final provider = _OperationalTokenProvider();
    final paths = <String>[];
    final client = MockClient((request) async {
      paths.add(request.url.path);
      expect(request.url.query, isEmpty);
      expect(request.headers['authorization'], isNull);
      expect(request.headers['cache-control'], 'no-store');
      if (request.url.path.endsWith('/login')) {
        expect(request.headers['x-onboarding-token'], isNull);
        expect(jsonDecode(request.body), {
          'email': 'synthetic@example.test',
          'password': 'preliminary-secret',
        });
        return http.Response(
          jsonEncode({
            'accessToken': token,
            'expiresAt': '2026-10-16T12:30:00Z',
          }),
          200,
        );
      }
      expect(request.headers['x-onboarding-token'], token);
      if (request.method == 'GET') {
        expect(request.body, isEmpty);
        return http.Response(
          jsonEncode([
            {
              'requestId': 'request-1',
              'organizationId': 'org-1',
              'status': 'PENDING_REVIEW',
              'reviewRounds': 0,
              'submittedAt': '2026-10-15T12:00:00Z',
              'email': 'must-not-reach-the-view@example.test',
            },
          ]),
          200,
        );
      }
      return http.Response('', 204);
    });
    final repository = MobileApplicantAccessRepository(
      MobileApiClient(
        httpClient: client,
        environment: environment,
        accessTokenProvider: provider,
      ),
    );

    final access = await repository.login(
      email: ' Synthetic@Example.Test ',
      password: 'preliminary-secret',
    );
    final requests = await repository.requests(access.token);
    await repository.logout(access.token);

    expect(requests.single.requestId, 'request-1');
    expect(requests.single.status, 'PENDING_REVIEW');
    expect(paths, [
      '/api/v1/onboarding/access/login',
      '/api/v1/onboarding/applicant/requests',
      '/api/v1/onboarding/access/logout',
    ]);
    expect(provider.refreshCalls, 0);
    expect(provider.invalidations, 0);
  });

  test('expired applicant access fails once without refreshing operational session', () async {
    final provider = _OperationalTokenProvider();
    var requests = 0;
    final repository = MobileApplicantAccessRepository(
      MobileApiClient(
        httpClient: MockClient((request) async {
          requests++;
          return http.Response(
            jsonEncode({'code': 'AUTH_TOKEN_INVALID', 'message': 'Expired'}),
            401,
          );
        }),
        environment: environment,
        accessTokenProvider: provider,
      ),
    );

    await expectLater(
      repository.requests(token),
      throwsA(
        isA<MobileApiException>().having(
          (error) => error.status,
          'status',
          401,
        ),
      ),
    );
    expect(requests, 1);
    expect(provider.refreshCalls, 0);
    expect(provider.invalidations, 0);
  });

  test('rejects a JWT or refresh-style result as applicant access', () async {
    final repository = MobileApplicantAccessRepository(
      MobileApiClient(
        httpClient: MockClient(
          (request) async => http.Response(
            jsonEncode({
              'accessToken': 'a.jwt.token',
              'expiresAt': '2026-10-16T12:30:00Z',
            }),
            200,
          ),
        ),
        environment: environment,
      ),
    );
    await expectLater(
      repository.login(
        email: 'synthetic@example.test',
        password: 'preliminary-secret',
      ),
      throwsFormatException,
    );
  });
}

final class _OperationalTokenProvider implements MobileAccessTokenProvider {
  int refreshCalls = 0;
  int invalidations = 0;

  @override
  String get accessToken => 'operational-token-must-not-be-used';

  @override
  Future<bool> refreshAccessToken() async {
    refreshCalls++;
    return true;
  }

  @override
  Future<void> invalidateSession() async {
    invalidations++;
  }
}
