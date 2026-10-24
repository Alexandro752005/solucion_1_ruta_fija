import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_onboarding_repository.dart';

void main() {
  test(
    'uses the public API contract and parses pending review response',
    () async {
      final paths = <String>[];
      final client = MockClient((request) async {
        paths.add(request.url.path);
        expect(request.method, 'POST');
        expect(request.headers.containsKey('authorization'), isFalse);
        switch (request.url.path) {
          case '/api/v1/onboarding/requests':
            expect(
              request.headers['idempotency-key'],
              'synthetic-idempotency-key',
            );
            final body = jsonDecode(request.body) as Map<String, dynamic>;
            expect(body['email'], 'synthetic.driver@example.test');
            expect(body['password'], 'Synthetic-Password-2026');
            expect(body['fullName'], 'Synthetic Driver');
            expect(body['phone'], isNull);
            expect(body['invitationCode'], '004207');
            return http.Response(
              jsonEncode({
                'verificationToken': 'synthetic-token-1',
                'otpExpiresAt': '2026-10-15T18:05:00Z',
                'challengeExpiresAt': '2026-10-15T18:30:00Z',
                'emailSent': true,
              }),
              202,
            );
          case '/api/v1/onboarding/otp/resend':
            expect(jsonDecode(request.body), {
              'verificationToken': 'synthetic-token-1',
            });
            return http.Response(
              jsonEncode({
                'verificationToken': 'synthetic-token-2',
                'otpExpiresAt': '2026-10-15T18:05:00Z',
                'challengeExpiresAt': '2026-10-15T18:30:00Z',
                'emailSent': true,
              }),
              200,
            );
          case '/api/v1/onboarding/otp/verify':
            expect(jsonDecode(request.body), {
              'verificationToken': 'synthetic-token-2',
              'code': '001234',
            });
            return http.Response(
              jsonEncode({
                'requestId': '85c60b70-17d7-4785-9c6a-5e8190f498ad',
                'status': 'PENDING_REVIEW',
              }),
              202,
            );
          default:
            throw StateError('Unexpected route ${request.url.path}');
        }
      });
      final repository = MobileConductorOnboardingRepository(
        MobileApiClient(
          httpClient: client,
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
        ),
      );

      final first = await repository.start(
        email: ' synthetic.driver@example.test ',
        password: 'Synthetic-Password-2026',
        fullName: ' Synthetic Driver ',
        invitationCode: '004207',
        idempotencyKey: 'synthetic-idempotency-key',
      );
      final resent = await repository.resendOtp(first.verificationToken);
      final accepted = await repository.verifyOtp(
        verificationToken: resent.verificationToken,
        code: '001234',
      );

      expect(first.verificationToken, 'synthetic-token-1');
      expect(resent.verificationToken, 'synthetic-token-2');
      expect(accepted.status, 'PENDING_REVIEW');
      expect(paths, [
        '/api/v1/onboarding/requests',
        '/api/v1/onboarding/otp/resend',
        '/api/v1/onboarding/otp/verify',
      ]);
    },
  );
}
