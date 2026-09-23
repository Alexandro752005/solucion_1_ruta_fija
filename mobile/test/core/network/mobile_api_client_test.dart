import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  test(
    'retries a protected request once after rotating the mobile access token',
    () async {
      final provider = _TokenProvider();
      final authorizations = <String?>[];
      final correlations = <String?>[];
      var requests = 0;
      final client = MockClient((request) async {
        requests++;
        authorizations.add(request.headers['authorization']);
        correlations.add(request.headers['x-correlation-id']);
        if (requests == 1) {
          return http.Response(
            jsonEncode({
              'status': 401,
              'code': 'AUTH_TOKEN_EXPIRED',
              'message': 'Expirado',
              'errors': [],
            }),
            401,
          );
        }
        return http.Response(
          jsonEncode({'driverId': 'driver-1', 'status': 'DISPONIBLE'}),
          200,
        );
      });
      final api = MobileApiClient(
        httpClient: client,
        environment: environment,
        accessTokenProvider: provider,
        correlationIdFactory: () => '00000000-0000-4000-8000-000000000001',
      );

      final response = await api.getObject('mobile/availability');

      expect(response['status'], 'DISPONIBLE');
      expect(provider.refreshCalls, 1);
      expect(authorizations, ['Bearer expired-access', 'Bearer fresh-access']);
      expect(correlations.toSet(), hasLength(1));
      expect(provider.invalidated, isFalse);
    },
  );

  test(
    'maps the server error envelope without placing credentials in the URL',
    () async {
      final client = MockClient((request) async {
        expect(
          request.url.toString(),
          'https://api.example.test/api/v1/mobile/auth/login',
        );
        expect(request.headers.containsKey('authorization'), isFalse);
        return http.Response(
          jsonEncode({
            'status': 400,
            'code': 'VALIDATION_ERROR',
            'message': 'Datos inválidos',
            'correlationId': 'corr-1',
            'errors': [
              {'field': 'email', 'message': 'El correo es obligatorio'},
            ],
          }),
          400,
        );
      });
      final api = MobileApiClient(httpClient: client, environment: environment);

      await expectLater(
        () => api.postObject('mobile/auth/login', {
          'email': '',
          'password': 'not-persisted',
        }, authenticated: false),
        throwsA(
          isA<MobileApiException>()
              .having((error) => error.code, 'code', 'VALIDATION_ERROR')
              .having(
                (error) => error.fieldErrors['email'],
                'email',
                'El correo es obligatorio',
              )
              .having((error) => error.correlationId, 'correlation', 'corr-1'),
        ),
      );
    },
  );
}

final class _TokenProvider implements MobileAccessTokenProvider {
  String _accessToken = 'expired-access';
  int refreshCalls = 0;
  bool invalidated = false;

  @override
  String get accessToken => _accessToken;

  @override
  Future<void> invalidateSession() async {
    invalidated = true;
    _accessToken = '';
  }

  @override
  Future<bool> refreshAccessToken() async {
    refreshCalls++;
    _accessToken = 'fresh-access';
    return true;
  }
}
