import 'package:flutter/foundation.dart';

import '../../features/auth/data/mobile_auth_repository.dart';
import '../../features/auth/domain/mobile_auth_models.dart';
import '../network/mobile_access_token_provider.dart';
import '../network/mobile_api_exception.dart';
import 'mobile_secure_token_store.dart';

enum MobileSessionStatus {
  restoring,
  unauthenticated,
  emailVerificationRequired,
  authenticated,
}

final class MobileSessionState {
  const MobileSessionState._({
    required this.status,
    this.user,
    this.notice,
    this.verificationToken,
    this.verificationExpiresAt,
    this.verificationEmailSent = false,
    this.accessScope = MobileAccessScope.incorporation,
  });

  const MobileSessionState.restoring()
    : this._(status: MobileSessionStatus.restoring);

  const MobileSessionState.unauthenticated({String? notice})
    : this._(status: MobileSessionStatus.unauthenticated, notice: notice);

  const MobileSessionState.authenticated(
    MobileAuthenticatedUser user, {
    MobileAccessScope accessScope = MobileAccessScope.incorporation,
  }) : this._(
         status: MobileSessionStatus.authenticated,
         user: user,
         accessScope: accessScope,
       );

  const MobileSessionState.emailVerificationRequired({
    required String verificationToken,
    required DateTime expiresAt,
    required bool emailSent,
  }) : this._(
         status: MobileSessionStatus.emailVerificationRequired,
         verificationToken: verificationToken,
         verificationExpiresAt: expiresAt,
         verificationEmailSent: emailSent,
       );

  final MobileSessionStatus status;
  final MobileAuthenticatedUser? user;
  final String? notice;
  final String? verificationToken;
  final DateTime? verificationExpiresAt;
  final bool verificationEmailSent;
  final MobileAccessScope accessScope;
}

/// Owns the in-memory access token and the OS-protected rotating refresh token.
final class MobileSessionController extends ChangeNotifier
    implements MobileAccessTokenProvider, MobileSessionGenerationProvider {
  factory MobileSessionController({
    required MobileAuthGateway authGateway,
    required MobileRefreshTokenStore tokenStore,
    Future<void> Function(MobileAuthenticatedUser? user)? onLocalSessionCleared,
  }) =>
      MobileSessionController._(authGateway, tokenStore, onLocalSessionCleared);

  MobileSessionController._(
    this._authGateway,
    this._tokenStore,
    this._onLocalSessionCleared,
  );

  final MobileAuthGateway _authGateway;
  final MobileRefreshTokenStore _tokenStore;
  final Future<void> Function(MobileAuthenticatedUser? user)?
  _onLocalSessionCleared;
  MobileSessionState _state = const MobileSessionState.restoring();
  String? _accessToken;
  // Keep only the current family's refresh reference for explicit revocation.
  // A pending protected-store write can be cleared before logout reads it.
  String? _refreshTokenForRevocation;
  Future<bool>? _refreshInFlight;
  int? _refreshGeneration;
  bool _restored = false;
  int _generation = 0;
  bool _disposed = false;
  Future<void> _tokenMutation = Future<void>.value();

  bool _current(int generation) => !_disposed && generation == _generation;

  // Serialize protected-store writes and clears. A delayed OS write must not
  // leave a refresh token behind after a subsequent local session clear.
  Future<void> _serializeTokenMutation(Future<void> Function() work) {
    final result = _tokenMutation.then((_) => work());
    _tokenMutation = result.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return result;
  }

  MobileSessionState get state => _state;

  @override
  String? get accessToken => _accessToken;

  @override
  int get sessionGeneration => _generation;

  Future<void> restore() async {
    if (_restored) {
      return;
    }
    _restored = true;
    final generation = _generation;

    try {
      final refreshToken = await _tokenStore.readRefreshToken();
      if (!_current(generation)) return;
      if (refreshToken == null || refreshToken.isEmpty) {
        _setState(const MobileSessionState.unauthenticated());
        return;
      }
      _refreshTokenForRevocation = refreshToken;
      final session = await _authGateway.refresh(refreshToken);
      await _establish(session, generation);
    } on MobileApiException catch (exception) {
      if (!_current(generation)) return;
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
      if (!_current(generation)) return;
      _setState(
        const MobileSessionState.unauthenticated(
          notice: 'No hay conexión para restaurar la sesión. Intente iniciar sesión.',
        ),
      );
    } on FormatException {
      if (_current(generation)) await _clearLocalSession();
    }
  }

  Future<void> login({required String email, required String password}) async {
    final generation = ++_generation;
    final result = await _authGateway.login(email: email, password: password);
    if (!_current(generation)) return;
    switch (result) {
      case MobileAuthenticatedLoginResult(:final session):
        await _establish(session, generation);
      case MobileEmailVerificationRequiredResult(:final challenge):
        await _clearLocalSession();
        if (!_current(generation + 1)) return;
        _setState(
          MobileSessionState.emailVerificationRequired(
            verificationToken: challenge.verificationToken,
            expiresAt: challenge.otpExpiresAt,
            emailSent: challenge.emailSent,
          ),
        );
    }
  }

  Future<void> verifyEmailOtp(String code) async {
    final generation = _generation;
    final current = _state;
    final token = current.verificationToken;
    if (current.status != MobileSessionStatus.emailVerificationRequired ||
        token == null) {
      throw const FormatException(
        'No hay una verificación de correo pendiente.',
      );
    }
    final session = await _authGateway.verifyEmailOtp(
      verificationToken: token,
      code: code,
    );
    await _establish(session, generation);
  }

  Future<void> resendEmailOtp() async {
    final generation = _generation;
    final current = _state;
    final token = current.verificationToken;
    if (current.status != MobileSessionStatus.emailVerificationRequired ||
        token == null) {
      throw const FormatException(
        'No hay una verificación de correo pendiente.',
      );
    }
    final challenge = await _authGateway.resendEmailOtp(token);
    if (!_current(generation)) return;
    _setState(
      MobileSessionState.emailVerificationRequired(
        verificationToken: challenge.verificationToken,
        expiresAt: challenge.otpExpiresAt,
        emailSent: challenge.emailSent,
      ),
    );
  }

  Future<void> cancelEmailOtp() async {
    await _clearLocalSession();
  }

  Future<void> logout() async {
    final user = _state.user;
    // Capture this family before an await or a later login can replace it.
    String? refreshToken = _refreshTokenForRevocation;
    _refreshTokenForRevocation = null;
    ++_generation;
    _accessToken = null;
    _setState(const MobileSessionState.unauthenticated());
    try {
      await _serializeTokenMutation(() async {
        refreshToken ??= await _tokenStore.readRefreshToken();
        await _tokenStore.clear();
      });
    } finally {
      try {
        if (refreshToken?.isNotEmpty == true) {
          await _authGateway.logout(refreshToken!);
        }
      } finally {
        await _onLocalSessionCleared?.call(user);
      }
    }
  }

  @override
  Future<bool> refreshAccessToken() {
    final inFlight = _refreshInFlight;
    if (inFlight != null && _refreshGeneration == _generation) {
      return inFlight;
    }
    return _runSingleRefresh();
  }

  Future<bool> _runSingleRefresh() async {
    final work = _refreshAccessTokenInternal();
    _refreshInFlight = work;
    _refreshGeneration = _generation;
    try {
      return await work;
    } finally {
      if (identical(_refreshInFlight, work)) {
        _refreshInFlight = null;
        _refreshGeneration = null;
      }
    }
  }

  Future<bool> _refreshAccessTokenInternal() async {
    final generation = _generation;
    try {
      final refreshToken = await _tokenStore.readRefreshToken();
      if (!_current(generation)) return false;
      if (refreshToken == null || refreshToken.isEmpty) {
        await _clearLocalSession();
        return false;
      }
      _refreshTokenForRevocation = refreshToken;
      final session = await _authGateway.refresh(refreshToken);
      await _establish(session, generation);
      return _current(generation);
    } on MobileApiException catch (exception) {
      if (!_current(generation)) return false;
      if (_invalidatesStoredSession(exception)) {
        await _clearLocalSession();
        return false;
      }
      rethrow;
    } on FormatException {
      if (_current(generation)) await _clearLocalSession();
      return false;
    }
  }

  @override
  Future<void> invalidateSession() => _clearLocalSession();

  Future<void> _establish(MobileAuthSession session, int generation) async {
    if (!_current(generation)) return;
    // A successful server rotation already exists even while its OS write waits.
    _refreshTokenForRevocation = session.refreshToken;
    await _serializeTokenMutation(() async {
      if (!_current(generation)) return;
      await _tokenStore.writeRefreshToken(session.refreshToken);
      if (!_current(generation)) await _tokenStore.clear();
    });
    if (!_current(generation)) return;
    _accessToken = session.accessToken;
    _setState(
      MobileSessionState.authenticated(
        session.user,
        accessScope: session.accessScope,
      ),
    );
  }

  Future<void> _clearLocalSession() async {
    final user = _state.user;
    _refreshTokenForRevocation = null;
    ++_generation;
    _accessToken = null;
    _setState(const MobileSessionState.unauthenticated());
    try {
      await _serializeTokenMutation(_tokenStore.clear);
    } finally {
      await _onLocalSessionCleared?.call(user);
    }
  }

  bool _invalidatesStoredSession(MobileApiException exception) {
    return exception.status == 401 ||
        const {
          'AUTH_REFRESH_REUSE_DETECTED',
          'AUTH_TOKEN_INVALID',
          'MOBILE_USER_NOT_DRIVER',
        }.contains(exception.code);
  }

  void _setState(MobileSessionState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    _accessToken = null;
    _refreshTokenForRevocation = null;
    super.dispose();
  }
}
