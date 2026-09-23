import 'package:flutter/foundation.dart';

import '../../features/auth/data/mobile_auth_repository.dart';
import '../../features/auth/domain/mobile_auth_models.dart';
import '../network/mobile_access_token_provider.dart';
import '../network/mobile_api_exception.dart';
import 'mobile_secure_token_store.dart';

enum MobileSessionStatus { restoring, unauthenticated, authenticated }

final class MobileSessionState {
  const MobileSessionState._({required this.status, this.user, this.notice});

  const MobileSessionState.restoring()
    : this._(status: MobileSessionStatus.restoring);

  const MobileSessionState.unauthenticated({String? notice})
    : this._(status: MobileSessionStatus.unauthenticated, notice: notice);

  const MobileSessionState.authenticated(MobileAuthenticatedUser user)
    : this._(status: MobileSessionStatus.authenticated, user: user);

  final MobileSessionStatus status;
  final MobileAuthenticatedUser? user;
  final String? notice;
}

/// Owns the in-memory access token and the OS-protected rotating refresh token.
final class MobileSessionController extends ChangeNotifier
    implements MobileAccessTokenProvider {
  factory MobileSessionController({
    required MobileAuthGateway authGateway,
    required MobileRefreshTokenStore tokenStore,
  }) => MobileSessionController._(authGateway, tokenStore);

  MobileSessionController._(this._authGateway, this._tokenStore);

  final MobileAuthGateway _authGateway;
  final MobileRefreshTokenStore _tokenStore;
  MobileSessionState _state = const MobileSessionState.restoring();
  String? _accessToken;
  Future<bool>? _refreshInFlight;
  bool _restored = false;

  MobileSessionState get state => _state;

  @override
  String? get accessToken => _accessToken;

  Future<void> restore() async {
    if (_restored) {
      return;
    }
    _restored = true;

    try {
      final refreshToken = await _tokenStore.readRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        _setState(const MobileSessionState.unauthenticated());
        return;
      }
      final session = await _authGateway.refresh(refreshToken);
      await _establish(session);
    } on MobileApiException catch (exception) {
      if (_invalidatesStoredSession(exception)) {
        await _clearLocalSession();
        return;
      }
      _setState(
        const MobileSessionState.unauthenticated(
          notice:
              'No se pudo restaurar la sesión. Inicie sesión para continuar.',
        ),
      );
    } on MobileNetworkException {
      _setState(
        const MobileSessionState.unauthenticated(
          notice: 'No hay conexión para restaurar la sesión. Intente iniciar sesión.',
        ),
      );
    } on FormatException {
      await _clearLocalSession();
    }
  }

  Future<void> login({required String email, required String password}) async {
    final session = await _authGateway.login(email: email, password: password);
    await _establish(session);
  }

  Future<void> logout() async {
    String? refreshToken;
    try {
      refreshToken = await _tokenStore.readRefreshToken();
      if (refreshToken != null && refreshToken.isNotEmpty) {
        await _authGateway.logout(refreshToken);
      }
    } finally {
      await _clearLocalSession();
    }
  }

  @override
  Future<bool> refreshAccessToken() {
    final inFlight = _refreshInFlight;
    if (inFlight != null) {
      return inFlight;
    }
    return _runSingleRefresh();
  }

  Future<bool> _runSingleRefresh() async {
    final work = _refreshAccessTokenInternal();
    _refreshInFlight = work;
    try {
      return await work;
    } finally {
      if (identical(_refreshInFlight, work)) {
        _refreshInFlight = null;
      }
    }
  }

  Future<bool> _refreshAccessTokenInternal() async {
    try {
      final refreshToken = await _tokenStore.readRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        await _clearLocalSession();
        return false;
      }
      final session = await _authGateway.refresh(refreshToken);
      await _establish(session);
      return true;
    } on MobileApiException catch (exception) {
      if (_invalidatesStoredSession(exception)) {
        await _clearLocalSession();
        return false;
      }
      rethrow;
    } on FormatException {
      await _clearLocalSession();
      return false;
    }
  }

  @override
  Future<void> invalidateSession() => _clearLocalSession();

  Future<void> _establish(MobileAuthSession session) async {
    await _tokenStore.writeRefreshToken(session.refreshToken);
    _accessToken = session.accessToken;
    _setState(MobileSessionState.authenticated(session.user));
  }

  Future<void> _clearLocalSession() async {
    _accessToken = null;
    try {
      await _tokenStore.clear();
    } finally {
      _setState(const MobileSessionState.unauthenticated());
    }
  }

  bool _invalidatesStoredSession(MobileApiException exception) {
    return exception.status == 401 ||
        const {
          'AUTH_REFRESH_REUSE_DETECTED',
          'AUTH_TOKEN_INVALID',
          'MOBILE_USER_NOT_DRIVER',
          'DRIVER_INACTIVE',
        }.contains(exception.code);
  }

  void _setState(MobileSessionState state) {
    _state = state;
    notifyListeners();
  }
}
