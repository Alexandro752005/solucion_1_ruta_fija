import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';

void main() {
  group('RouteFijaEnvironment', () {
    test('normalizes a valid API base URL and creates endpoint URLs', () {
      final environment = RouteFijaEnvironment.fromApiBaseUrl(
        'http://10.0.2.2:8080/api/v1/',
      );

      expect(environment.apiBaseUrl, 'http://10.0.2.2:8080/api/v1');
      expect(
        environment.endpoint('/mobile/me').toString(),
        'http://10.0.2.2:8080/api/v1/mobile/me',
      );
    });

    test('rejects an API URL with credentials, query or an incorrect path', () {
      expect(
        () => RouteFijaEnvironment.fromApiBaseUrl(
          'https://user:secret@example.test/api/v1',
        ),
        throwsArgumentError,
      );
      expect(
        () => RouteFijaEnvironment.fromApiBaseUrl(
          'https://example.test/api/v1?token=secret',
        ),
        throwsArgumentError,
      );
      expect(
        () =>
            RouteFijaEnvironment.fromApiBaseUrl('https://example.test/mobile'),
        throwsArgumentError,
      );
    });
  });
}
