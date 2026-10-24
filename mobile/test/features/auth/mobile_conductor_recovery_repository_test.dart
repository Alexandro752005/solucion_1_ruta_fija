import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_recovery_repository.dart';

void main() {
  test(
    'verifies the recovery OTP without sending an authenticated session',
    () async {
      late http.Request captured;
      final repository = MobileConductorRecoveryRepository(
        MobileApiClient(
          httpClient: MockClient((request) async {
            captured = request;
            return http.Response(
              jsonEncode({
                'recoveryId': 'synthetic-recovery-id',
                'status': 'PASSWORD_RESET_PENDING',
                'requestedEmail': 'driver@example.test',
                'passwordResetRequired': true,
                'passwordResetToken': 'synthetic-reset-token',
                'passwordResetExpiresAt': '2026-10-15T12:30:00Z',
              }),
              200,
            );
          }),
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
        ),
      );

      final result = await repository.verifyOtp(
        recoveryId: 'synthetic-recovery-id',
        verificationToken: 'synthetic-handoff-token',
        code: '001234',
      );

      expect(captured.method, 'POST');
      expect(captured.url.path, '/api/v1/auth/conductor-recovery/otp/verify');
      expect(captured.headers['authorization'], isNull);
      expect(jsonDecode(captured.body), {
        'recoveryId': 'synthetic-recovery-id',
        'verificationToken': 'synthetic-handoff-token',
        'code': '001234',
      });
      expect(result.passwordResetRequired, isTrue);
      expect(result.passwordResetToken, 'synthetic-reset-token');
    },
  );

  test(
    'submits the one-time password reset token only in the request body',
    () async {
      late http.Request captured;
      final repository = MobileConductorRecoveryRepository(
        MobileApiClient(
          httpClient: MockClient((request) async {
            captured = request;
            return http.Response(
              jsonEncode({
                'recoveryId': 'synthetic-recovery-id',
                'status': 'COMPLETED',
              }),
              200,
            );
          }),
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
        ),
      );

      await repository.resetPassword(
        recoveryId: 'synthetic-recovery-id',
        passwordResetToken: 'synthetic-reset-token',
        newPassword: 'synthetic-new-password-123',
      );

      expect(captured.method, 'POST');
      expect(
        captured.url.path,
        '/api/v1/auth/conductor-recovery/synthetic-recovery-id/password-reset',
      );
      expect(captured.headers['authorization'], isNull);
      expect(jsonDecode(captured.body), {
        'passwordResetToken': 'synthetic-reset-token',
        'newPassword': 'synthetic-new-password-123',
      });
    },
  );
}
