import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/announcements/data/mobile_announcement_repository.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  MobileAnnouncementRepository repository(http.Client client) {
    return MobileAnnouncementRepository(
      MobileApiClient(
        httpClient: client,
        environment: environment,
        accessTokenProvider: const _StaticTokenProvider(),
      ),
    );
  }

  test('lists only the authenticated audience communications', () async {
    final client = MockClient((request) async {
      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/mobile/announcements');
      expect(request.url.queryParameters, {
        'page': '0',
        'size': '20',
        'sort': 'createdAt,desc',
      });
      expect(request.headers['authorization'], 'Bearer access-token');
      return http.Response(
        jsonEncode({
          'items': [_announcementJson()],
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
    expect(page.items.single.requiresReadConfirmation, isTrue);
  });

  test(
    'confirms reading only through the idempotent server endpoint',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/mobile/announcements/66666666-6666-4666-8666-666666666666/read',
        );
        expect(jsonDecode(request.body), <String, dynamic>{});
        expect(request.headers['authorization'], 'Bearer access-token');
        return http.Response(
          jsonEncode({
            'announcementId': '66666666-6666-4666-8666-666666666666',
            'deliveredAt': '2026-09-24T14:01:00Z',
            'readAt': '2026-09-24T15:00:00Z',
          }),
          200,
        );
      });

      final result = await repository(client)
          .markRead('66666666-6666-4666-8666-666666666666');

      expect(result.announcementId, '66666666-6666-4666-8666-666666666666');
      expect(result.readAt, DateTime.utc(2026, 9, 24, 15));
    },
  );
}

Map<String, dynamic> _announcementJson() {
  return {
    'id': '66666666-6666-4666-8666-666666666666',
    'title': 'Comunicado de prueba',
    'body': 'Revise esta información operativa.',
    'requireReadAck': true,
    'createdAt': '2026-09-24T14:00:00Z',
    'deliveredAt': '2026-09-24T14:01:00Z',
    'readAt': null,
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
