import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';

import '../../support/mobile_fakes.dart';

final class _PlatformFailingTokenStore implements MobileRefreshTokenStore {
  _PlatformFailingTokenStore({
    this.failRead = false,
    this.failWrite = false,
    this.failClear = false,
  });

  final bool failRead;
  final bool failWrite;
  final bool failClear;
  String? value = 'synthetic-stored-refresh';
  int clearCalls = 0;

  @override
  Future<String?> readRefreshToken() async {
    if (failRead) {
      throw PlatformException(
        code: 'SYNTHETIC_KEYSTORE_UNAVAILABLE',
        message: 'Sensitive platform implementation detail must not reach UI',
      );
    }
    return value;
  }

  @override
  Future<void> writeRefreshToken(String token) async {
    if (failWrite) {
      throw PlatformException(code: 'SYNTHETIC_KEYSTORE_UNAVAILABLE');
    }
    value = token;
  }

  @override
  Future<void> clear() async {
    clearCalls++;
    if (failClear) {
      throw PlatformException(code: 'SYNTHETIC_KEYSTORE_UNAVAILABLE');
    }
    value = null;
  }
}

final class _DeferredReadTokenStore implements MobileRefreshTokenStore {
  final readResult = Completer<String?>();
  String? value;

  @override
  Future<String?> readRefreshToken() => readResult.future;

  @override
  Future<void> writeRefreshToken(String token) async {
    value = token;
  }

  @override
  Future<void> clear() async {
    value = null;
  }
}

final class _DeferredClearTokenStore implements MobileRefreshTokenStore {
  final clearing = Completer<void>();
  final clearResult = Completer<void>();
  String? value = 'synthetic-stored-refresh';

  @override
  Future<String?> readRefreshToken() async => value;

  @override
  Future<void> writeRefreshToken(String token) async {
    value = token;
  }

  @override
  Future<void> clear() async {
    clearing.complete();
    await clearResult.future;
    value = null;
  }
}

void main() {
  for (final refreshFailure in <Object>[
    const MobileApiException(
      status: 401,
      code: 'AUTH_TOKEN_INVALID',
      message: 'Synthetic expired session',
    ),
    const FormatException('Synthetic invalid session response'),
  ]) {
    test(
      'TDD-002 restore handles cleanup storage failure after ${refreshFailure.runtimeType}',
      () async {
        final store = _PlatformFailingTokenStore(failClear: true);
        final auth = FakeMobileAuthGateway(refreshFailure: refreshFailure);
        final controller = MobileSessionController(
          authGateway: auth,
          tokenStore: store,
        );
        addTearDown(controller.dispose);

        await expectLater(controller.restore(), completes);

        expect(controller.state.status, MobileSessionStatus.unauthenticated);
        expect(controller.accessToken, isNull);
        expect(controller.state.notice, isNotEmpty);
        expect(controller.state.notice, isNot(contains('SYNTHETIC_KEYSTORE')));
        expect(store.clearCalls, 1);
        expect(store.value, 'synthetic-stored-refresh');
        expect(auth.refreshedTokens, ['synthetic-stored-refresh']);
      },
    );
  }

  test(
    'TDD-002 failed cleanup from an old restoration cannot clear a newer login',
    () async {
      final store = _DeferredClearTokenStore();
      final auth = FakeMobileAuthGateway(
        refreshFailure: const MobileApiException(
          status: 401,
          code: 'AUTH_TOKEN_INVALID',
          message: 'Synthetic expired session',
        ),
        session: testSession(
          accessToken: 'synthetic-new-access',
          refreshToken: 'synthetic-new-refresh',
        ),
      );
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: store,
      );
      addTearDown(controller.dispose);
      final restoring = controller.restore();
      final restorationExpectation = expectLater(restoring, completes);
      await store.clearing.future;
      final login = controller.login(
        email: 'synthetic@example.test',
        password: 'SYNTHETIC_ONLY',
      );
      store.clearResult.completeError(
        PlatformException(code: 'SYNTHETIC_KEYSTORE_UNAVAILABLE'),
      );
      await Future.wait([restorationExpectation, login]);

      expect(controller.state.status, MobileSessionStatus.authenticated);
      expect(controller.accessToken, 'synthetic-new-access');
      expect(controller.state.notice, isNull);
      expect(store.value, 'synthetic-new-refresh');
    },
  );

  test('TDD-002 restore handles protected-store read failure without staying restoring', () async {
    final store = _PlatformFailingTokenStore(failRead: true);
    final auth = FakeMobileAuthGateway();
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    addTearDown(controller.dispose);

    await expectLater(controller.restore(), completes);

    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    expect(controller.accessToken, isNull);
    expect(controller.state.notice, isNotEmpty);
    expect(controller.state.notice, isNot(contains('SYNTHETIC_KEYSTORE')));
    expect(auth.refreshedTokens, isEmpty);
    expect(store.clearCalls, 0);
    expect(store.value, 'synthetic-stored-refresh');
  });

  test('TDD-002 restore does not expose authentication when protected rotation write fails', () async {
    final store = _PlatformFailingTokenStore(failWrite: true);
    final auth = FakeMobileAuthGateway(
      session: testSession(
        accessToken: 'synthetic-new-access',
        refreshToken: 'synthetic-new-refresh',
      ),
    );
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    addTearDown(controller.dispose);

    await expectLater(controller.restore(), completes);

    expect(auth.refreshedTokens, ['synthetic-stored-refresh']);
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    expect(controller.accessToken, isNull);
    expect(controller.state.notice, isNotEmpty);
    expect(store.clearCalls, 0);
  });

  test(
    'TDD-002 delayed protected-store failure cannot replace a newer login',
    () async {
      final store = _DeferredReadTokenStore();
      final auth = FakeMobileAuthGateway(
        session: testSession(
          accessToken: 'synthetic-new-access',
          refreshToken: 'synthetic-new-refresh',
        ),
      );
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: store,
      );
      addTearDown(controller.dispose);
      final restoring = controller.restore();
      final restorationExpectation = expectLater(restoring, completes);

      await controller.login(
        email: 'synthetic@example.test',
        password: 'SYNTHETIC_ONLY',
      );
      store.readResult.completeError(
        PlatformException(code: 'SYNTHETIC_KEYSTORE_UNAVAILABLE'),
      );
      await restorationExpectation;

      expect(controller.state.status, MobileSessionStatus.authenticated);
      expect(controller.accessToken, 'synthetic-new-access');
      expect(store.value, 'synthetic-new-refresh');
      expect(auth.refreshedTokens, isEmpty);
    },
  );

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
