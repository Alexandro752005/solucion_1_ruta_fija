import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/features/announcements/data/mobile_notice_repository.dart';

void main() {
  Map<String, Object?> notice() => {
    'id': 'synthetic-notice',
    'eventType': 'announcement.published',
    'references': {'announcementId': 'synthetic-announcement'},
    'createdAt': '2026-10-20T12:00:00Z',
    'externalStatus': 'NOT_REQUIRED',
  };
  MobileNoticeRepository repository(http.Client client) =>
      MobileNoticeRepository(
        MobileApiClient(
          httpClient: client,
          accessTokenProvider: const _SyntheticToken(),
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
        ),
      );
  test(
    'consultation only uses own notices GET and does not acknowledge a message',
    () async {
      int count = 0;
      final repo = repository(
        MockClient((r) async {
          count++;
          expect(r.method, 'GET');
          expect(r.url.path, '/api/v1/mobile/notices');
          return http.Response(
            jsonEncode({
              'items': [notice()],
              'page': 0,
              'totalPages': 1,
            }),
            200,
          );
        }),
      );
      final result = await repo.list();
      expect(count, 1);
      expect(result.items.single.readAt, isNull);
      expect(
        result.items.single.references['announcementId'],
        'synthetic-announcement',
      );
    },
  );
  test(
    'an explicit notice reading does not invoke announcement Leido',
    () async {
      final repo = repository(
        MockClient((r) async {
          expect(r.method, 'POST');
          expect(r.url.path, '/api/v1/mobile/notices/synthetic-notice/read');
          expect(jsonDecode(r.body), isEmpty);
          return http.Response(
            jsonEncode({...notice(), 'readAt': '2026-10-20T12:01:00Z'}),
            200,
          );
        }),
      );
      final result = await repo.read('synthetic-notice');
      expect(result.readAt, DateTime.utc(2026, 10, 20, 12, 1));
    },
  );
  test('invalid inputs are rejected without an HTTP request', () async {
    final repo = repository(
      MockClient((_) async => throw StateError('Unexpected HTTP')),
    );
    await expectLater(repo.list(page: -1), throwsArgumentError);
    await expectLater(repo.read(' '), throwsArgumentError);
  });
}

class _SyntheticToken implements MobileAccessTokenProvider {
  const _SyntheticToken();
  @override
  String get accessToken => 'synthetic-token';
  @override
  Future<bool> refreshAccessToken() async => false;
  @override
  Future<void> invalidateSession() async {}
}
