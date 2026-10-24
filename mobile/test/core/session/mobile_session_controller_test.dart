import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  test('login stores only the rotating refresh token and keeps access token in memory', () async {
    final store = InMemoryRefreshTokenStore();
    final auth = FakeMobileAuthGateway(
      session: testSession(accessToken: 'a1', refreshToken: 'r1'),
    );
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );

    await controller.login(email: 'driver@test.dev', password: 'password');

    expect(controller.state.status, MobileSessionStatus.authenticated);
    expect(controller.accessToken, 'a1');
    expect(store.value, 'r1');
    expect(auth.refreshedTokens, isEmpty);
  });

  test('verification-required login creates no session until a one-time code is verified', () async {
    final store = InMemoryRefreshTokenStore();
    final auth = FakeMobileAuthGateway(
      session: testSession(
        accessToken: 'verified-access',
        refreshToken: 'verified-refresh',
      ),
      loginChallenge: MobileEmailOtpChallenge(
        verificationToken: 'verification-token',
        otpExpiresAt: DateTime.utc(2026, 10, 14, 12, 5),
        challengeExpiresAt: DateTime.utc(2026, 10, 14, 12, 30),
        emailSent: true,
      ),
    );
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );

    await controller.login(email: 'driver@test.dev', password: 'password');

    expect(
      controller.state.status,
      MobileSessionStatus.emailVerificationRequired,
    );
    expect(controller.accessToken, isNull);
    expect(store.value, isNull);

    await controller.verifyEmailOtp('123456');

    expect(auth.verificationTokens, ['verification-token']);
    expect(auth.verifiedCodes, ['123456']);
    expect(controller.state.status, MobileSessionStatus.authenticated);
    expect(controller.accessToken, 'verified-access');
    expect(store.value, 'verified-refresh');
  });

  test('restore rotates the protected refresh token before exposing an authenticated state', () async {
    final store = InMemoryRefreshTokenStore('r0');
    final auth = FakeMobileAuthGateway(
      session: testSession(accessToken: 'a2', refreshToken: 'r2'),
    );
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );

    await controller.restore();

    expect(auth.refreshedTokens, ['r0']);
    expect(store.value, 'r2');
    expect(controller.accessToken, 'a2');
    expect(controller.state.status, MobileSessionStatus.authenticated);
  });

  test('an invalid mobile refresh token clears the local session instead of retrying it', () async {
    final store = InMemoryRefreshTokenStore('expired-refresh');
    final auth = FakeMobileAuthGateway(
      refreshFailure: const MobileApiException(
        status: 401,
        code: 'AUTH_TOKEN_INVALID',
        message: 'Sesión expirada',
      ),
    );
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );

    final refreshed = await controller.refreshAccessToken();

    expect(refreshed, isFalse);
    expect(store.value, isNull);
    expect(controller.accessToken, isNull);
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
  });

  test(
    'logout revokes the protected refresh family when the server is reachable',
    () async {
      final store = InMemoryRefreshTokenStore('r1');
      final auth = FakeMobileAuthGateway();
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: store,
      );
      await controller.restore();

      await controller.logout();

      expect(auth.loggedOutTokens, ['refresh-token']);
      expect(store.value, isNull);
      expect(controller.state.status, MobileSessionStatus.unauthenticated);
    },
  );

  test(
    'clears owner-scoped protected operations when the session ends',
    () async {
      final clearedOwners = <String?>[];
      final controller = MobileSessionController(
        authGateway: FakeMobileAuthGateway(),
        tokenStore: InMemoryRefreshTokenStore(),
        onLocalSessionCleared: (user) async {
          clearedOwners.add(user?.id);
        },
      );

      await controller.login(email: 'driver@test.dev', password: 'password');
      await controller.invalidateSession();

      expect(clearedOwners, ['11111111-1111-4111-8111-111111111111']);
      expect(controller.state.status, MobileSessionStatus.unauthenticated);
    },
  );
}
