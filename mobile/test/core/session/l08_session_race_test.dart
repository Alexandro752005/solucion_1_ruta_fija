import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';

import '../../support/mobile_fakes.dart';

final class _DeferredAuth implements MobileAuthGateway {
  final refreshResult = Completer<MobileAuthSession>();
  final loginResult = Completer<MobileAuthLoginResult>();
  final refreshStarted = Completer<void>();
  final loginStarted = Completer<void>();
  final loggedOut = <String>[];
  @override
  Future<MobileAuthSession> refresh(String token) {
    if (!refreshStarted.isCompleted) refreshStarted.complete();
    return refreshResult.future;
  }

  @override
  Future<MobileAuthLoginResult> login({
    required String email,
    required String password,
  }) {
    if (!loginStarted.isCompleted) loginStarted.complete();
    return loginResult.future;
  }

  @override
  Future<void> logout(String token) async {
    loggedOut.add(token);
  }

  @override
  Future<MobileAuthSession> verifyEmailOtp({
    required String verificationToken,
    required String code,
  }) => throw UnimplementedError();
  @override
  Future<MobileEmailOtpChallenge> resendEmailOtp(String token) =>
      throw UnimplementedError();
}

final class _DeferredStore implements MobileRefreshTokenStore {
  String? value;
  final writing = Completer<void>(), releaseWrite = Completer<void>();
  @override
  Future<String?> readRefreshToken() async => value;
  @override
  Future<void> clear() async {
    value = null;
  }

  @override
  Future<void> writeRefreshToken(String token) async {
    if (!writing.isCompleted) writing.complete();
    await releaseWrite.future;
    value = token;
  }
}

void main() {
  test('L08 logical generation changes on login/logout and remains stable during legitimate rotation', () async {
    final controller = MobileSessionController(
      authGateway: FakeMobileAuthGateway(),
      tokenStore: InMemoryRefreshTokenStore(),
    );
    final initial = controller.sessionGeneration;
    await controller.login(
      email: 'synthetic@rutafija.test',
      password: 'SYNTHETIC_ONLY',
    );
    final loggedIn = controller.sessionGeneration;
    expect(loggedIn, greaterThan(initial));
    expect(await controller.refreshAccessToken(), true);
    expect(controller.sessionGeneration, loggedIn);
    await controller.logout();
    expect(controller.sessionGeneration, greaterThan(loggedIn));
    controller.dispose();
  });
  test('L08 refresh finishing after logout cannot recreate a session or stored token', () async {
    final auth = _DeferredAuth(),
        store = InMemoryRefreshTokenStore('SYNTHETIC_OLD_REFRESH');
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final refresh = controller.refreshAccessToken();
    await auth.refreshStarted.future;
    await controller.logout();
    auth.refreshResult.complete(testSession());
    expect(await refresh, false);
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    expect(controller.accessToken, isNull);
    expect(store.value, isNull);
    expect(auth.loggedOut, ['SYNTHETIC_OLD_REFRESH']);
    controller.dispose();
  });
  test('L08 logout revokes the rotated family during a delayed protected-store write', () async {
    final auth = _DeferredAuth(),
        store = _DeferredStore()..value = 'SYNTHETIC_OLD_REFRESH';
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final refresh = controller.refreshAccessToken();
    await auth.refreshStarted.future;
    auth.refreshResult.complete(
      testSession(
        accessToken: 'SYNTHETIC_ROTATED_ACCESS',
        refreshToken: 'SYNTHETIC_ROTATED_REFRESH',
      ),
    );
    await store.writing.future;
    final logout = controller.logout();
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    store.releaseWrite.complete();
    expect(await refresh, false);
    await logout;
    expect(auth.loggedOut, ['SYNTHETIC_ROTATED_REFRESH']);
    expect(store.value, isNull);
    expect(controller.accessToken, isNull);
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    controller.dispose();
  });
  test('L08 pending logout never revokes a newer login family', () async {
    final auth = _DeferredAuth(),
        store = _DeferredStore()..value = 'SYNTHETIC_OLD_REFRESH';
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final refresh = controller.refreshAccessToken();
    await auth.refreshStarted.future;
    auth.refreshResult.complete(
      testSession(
        accessToken: 'SYNTHETIC_ROTATED_ACCESS',
        refreshToken: 'SYNTHETIC_ROTATED_REFRESH',
      ),
    );
    await store.writing.future;
    final logout = controller.logout();
    final login = controller.login(
      email: 'synthetic.new-family@rutafija.test',
      password: 'SYNTHETIC_ONLY',
    );
    await auth.loginStarted.future;
    auth.loginResult.complete(
      MobileAuthLoginResult.authenticated(
        testSession(
          accessToken: 'SYNTHETIC_NEW_ACCESS',
          refreshToken: 'SYNTHETIC_NEW_REFRESH',
        ),
      ),
    );
    store.releaseWrite.complete();
    expect(await refresh, false);
    await Future.wait([logout, login]);
    expect(auth.loggedOut, ['SYNTHETIC_ROTATED_REFRESH']);
    expect(store.value, 'SYNTHETIC_NEW_REFRESH');
    expect(controller.accessToken, 'SYNTHETIC_NEW_ACCESS');
    expect(controller.state.status, MobileSessionStatus.authenticated);
    controller.dispose();
  });
  test(
    'L08 late restore cannot overwrite an explicitly cleared session',
    () async {
      final auth = _DeferredAuth(),
          store = InMemoryRefreshTokenStore('SYNTHETIC_OLD_REFRESH');
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: store,
      );
      final restoring = controller.restore();
      await auth.refreshStarted.future;
      await controller.invalidateSession();
      auth.refreshResult.complete(testSession());
      await restoring;
      expect(controller.state.status, MobileSessionStatus.unauthenticated);
      expect(store.value, isNull);
      controller.dispose();
    },
  );
  test(
    'L08 late login result cannot resurrect the cancelled local session',
    () async {
      final auth = _DeferredAuth(), store = InMemoryRefreshTokenStore();
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: store,
      );
      final login = controller.login(
        email: 'synthetic@rutafija.test',
        password: 'SYNTHETIC_ONLY',
      );
      await auth.loginStarted.future;
      await controller.invalidateSession();
      auth.loginResult.complete(
        MobileAuthLoginResult.authenticated(testSession()),
      );
      await login;
      expect(controller.state.status, MobileSessionStatus.unauthenticated);
      expect(store.value, isNull);
      expect(controller.accessToken, isNull);
      controller.dispose();
    },
  );
  test('L08 old restore cannot replace a newer successful login', () async {
    final auth = _DeferredAuth(),
        store = InMemoryRefreshTokenStore('SYNTHETIC_OLD_REFRESH');
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final restoring = controller.restore();
    await auth.refreshStarted.future;
    final login = controller.login(
      email: 'synthetic@rutafija.test',
      password: 'SYNTHETIC_ONLY',
    );
    await auth.loginStarted.future;
    auth.loginResult.complete(
      MobileAuthLoginResult.authenticated(
        testSession(accessToken: 'NEW_ACCESS', refreshToken: 'NEW_REFRESH'),
      ),
    );
    await login;
    auth.refreshResult.complete(
      testSession(accessToken: 'OLD_ACCESS', refreshToken: 'OLD_REFRESH'),
    );
    await restoring;
    expect(controller.accessToken, 'NEW_ACCESS');
    expect(store.value, 'NEW_REFRESH');
    controller.dispose();
  });
  test('L08 delayed protected-store write is cleared before session invalidation completes', () async {
    final auth = FakeMobileAuthGateway(), store = _DeferredStore();
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final login = controller.login(
      email: 'synthetic@rutafija.test',
      password: 'SYNTHETIC_ONLY',
    );
    await store.writing.future;
    final clear = controller.invalidateSession();
    expect(controller.state.status, MobileSessionStatus.unauthenticated);
    store.releaseWrite.complete();
    await Future.wait([login, clear]);
    expect(store.value, isNull);
    expect(controller.accessToken, isNull);
    controller.dispose();
  });
  test('L08 disposed controller ignores a pending login result', () async {
    final auth = _DeferredAuth(), store = InMemoryRefreshTokenStore();
    final controller = MobileSessionController(
      authGateway: auth,
      tokenStore: store,
    );
    final login = controller.login(
      email: 'synthetic@rutafija.test',
      password: 'SYNTHETIC_ONLY',
    );
    await auth.loginStarted.future;
    controller.dispose();
    auth.loginResult.complete(
      MobileAuthLoginResult.authenticated(testSession()),
    );
    await login;
    expect(store.value, isNull);
    expect(controller.accessToken, isNull);
  });
}
