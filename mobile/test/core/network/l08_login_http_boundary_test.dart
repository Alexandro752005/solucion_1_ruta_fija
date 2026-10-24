import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/presentation/mobile_error_message.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';

import '../../support/mobile_fakes.dart';

// The inherited method constructs Dart's real socket client. Calling the
// HttpClient factory from inside its own override would recurse instead.
final class _LoopbackHttpOverrides extends HttpOverrides {}

void main() {
  for (final status in [503, 401]) {
    test(
      'L08 loopback HTTP $status is one failed public login, no session or retry',
      () async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        // Only this client's construction bypasses the widget binding's HTTP
        // double. The global override and the other tests remain unchanged.
        final client = HttpOverrides.runZoned(
          () => IOClient(
            HttpClient()
              ..connectionTimeout = const Duration(seconds: 2)
              ..findProxy = (_) => 'DIRECT',
          ),
          createHttpClient: _LoopbackHttpOverrides().createHttpClient,
        );
        var requests = 0;
        final listener = server.listen((request) async {
          requests++;
          expect(request.method, 'POST');
          expect(request.uri.path, '/api/v1/mobile/auth/login');
          expect(request.uri.query, isEmpty);
          await request.drain<void>();
          request.response.statusCode = status;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'status': status,
              'code': status == 401
                  ? 'AUTH_INVALID_CREDENTIALS'
                  : 'SERVICE_UNAVAILABLE',
              'message': 'SYNTHETIC_DIAGNOSTIC_DO_NOT_SHOW',
              'correlationId': '00000000-0000-4000-8000-000000000001',
              'errors': [],
            }),
          );
          await request.response.close();
        });
        final store = InMemoryRefreshTokenStore();
        final controller = MobileSessionController(
          authGateway: MobileAuthRepository(
            MobileApiClient(
              httpClient: client,
              environment: RouteFijaEnvironment.fromApiBaseUrl(
                'http://127.0.0.1:${server.port}/api/v1',
              ),
            ),
          ),
          tokenStore: store,
        );
        try {
          await controller.restore();
          Object? failure;
          try {
            await controller.login(
              email: 'synthetic.no-account@rutafija.test',
              password: 'SYNTHETIC_NOT_A_CREDENTIAL',
            );
          } catch (error) {
            failure = error;
          }
          expect(
            failure,
            isA<MobileApiException>().having(
              (error) => error.status,
              'status',
              status,
            ),
          );
          final message = mobileErrorMessage(failure!);
          expect(message, isNot(contains('SYNTHETIC_DIAGNOSTIC')));
          expect(
            message,
            status == 503
                ? contains('no está disponible temporalmente')
                : contains('no son correctos'),
          );
          expect(requests, 1);
          expect(controller.state.status, MobileSessionStatus.unauthenticated);
          expect(controller.accessToken, isNull);
          expect(store.value, isNull);
        } finally {
          controller.dispose();
          client.close();
          await server.close(force: true);
          await listener.cancel();
        }
      },
    );
  }
  test('L08 transport exception is a safe network failure, no credentials classification', () async {
    var requests = 0;
    final client = MockClient((_) async {
      requests++;
      throw http.ClientException('SYNTHETIC_PRIVATE_DIAGNOSTIC');
    });
    final api = MobileApiClient(
      httpClient: client,
      environment: RouteFijaEnvironment.fromApiBaseUrl(
        'https://api.example.test/api/v1',
      ),
    );
    Object? failure;
    try {
      await MobileAuthRepository(api)
          .login(email: 'synthetic@rutafija.test', password: 'SYNTHETIC');
    } catch (error) {
      failure = error;
    }
    expect(failure, isA<MobileNetworkException>());
    expect(mobileErrorMessage(failure!), isNot(contains('PRIVATE_DIAGNOSTIC')));
    expect(requests, 1);
    client.close();
  });
  test('L08 malformed 503 envelope stays service failure without exposing raw body', () async {
    final client = MockClient(
      (_) async => http.Response(
        '<h1>SYNTHETIC_PRIVATE_DIAGNOSTIC</h1>',
        503,
        headers: {'content-type': 'text/html'},
      ),
    );
    final api = MobileApiClient(
      httpClient: client,
      environment: RouteFijaEnvironment.fromApiBaseUrl(
        'https://api.example.test/api/v1',
      ),
    );
    Object? failure;
    try {
      await api.postObject('mobile/auth/login', {
        'email': 'synthetic@rutafija.test',
        'password': 'SYNTHETIC',
      }, authenticated: false);
    } catch (error) {
      failure = error;
    }
    expect(
      failure,
      isA<MobileApiException>().having((error) => error.status, 'status', 503),
    );
    expect(
      mobileErrorMessage(failure!),
      contains('no está disponible temporalmente'),
    );
    expect(mobileErrorMessage(failure), isNot(contains('PRIVATE_DIAGNOSTIC')));
    client.close();
  });
  testWidgets(
    'L08 request timeout is bounded to15s and never replays the login',
    (tester) async {
      var requests = 0;
      final held = Completer<http.Response>();
      final client = MockClient((_) {
        requests++;
        return held.future;
      });
      final api = MobileApiClient(
        httpClient: client,
        environment: RouteFijaEnvironment.fromApiBaseUrl(
          'https://api.example.test/api/v1',
        ),
      );
      Object? failure;
      bool done = false;
      final work = MobileAuthRepository(api)
          .login(email: 'synthetic@rutafija.test', password: 'SYNTHETIC')
          .then<void>(
            (_) {
              done = true;
            },
            onError: (Object error, StackTrace _) {
              failure = error;
              done = true;
            },
          );
      await tester.pump();
      await tester.pump(const Duration(seconds: 14));
      expect(done, false);
      await tester.pump(const Duration(seconds: 2));
      await work;
      expect(done, true);
      expect(failure, isA<MobileNetworkException>());
      expect(mobileErrorMessage(failure!), contains('tardó demasiado'));
      await tester.pump(const Duration(minutes: 2));
      expect(requests, 1);
      held.complete(http.Response('{}', 503));
      await tester.pump();
      client.close();
    },
  );
}
