import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_auth_models.dart';

abstract interface class MobileAuthGateway {
  Future<MobileAuthLoginResult> login({
    required String email,
    required String password,
  });

  Future<MobileAuthSession> refresh(String refreshToken);

  Future<MobileAuthSession> verifyEmailOtp({
    required String verificationToken,
    required String code,
  });

  Future<MobileEmailOtpChallenge> resendEmailOtp(String verificationToken);

  Future<void> logout(String refreshToken);
}

final class MobileAuthRepository implements MobileAuthGateway {
  MobileAuthRepository(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileAuthLoginResult> login({
    required String email,
    required String password,
  }) async {
    final response = await _apiClient.postObject('mobile/auth/login', {
      'email': email.trim(),
      'password': password,
    }, authenticated: false);
    if (response['emailVerificationRequired'] == true) {
      final rawChallenge = response['verification'];
      if (rawChallenge is! Map) {
        throw const FormatException(
          'Falta la solicitud de verificación de correo.',
        );
      }
      return MobileAuthLoginResult.verificationRequired(
        MobileEmailOtpChallenge.fromJson(
          Map<String, dynamic>.from(rawChallenge),
        ),
      );
    }
    return MobileAuthLoginResult.authenticated(
      MobileAuthSession.fromJson(response),
    );
  }

  @override
  Future<MobileAuthSession> verifyEmailOtp({
    required String verificationToken,
    required String code,
  }) async {
    final response = await _apiClient.postObject('mobile/auth/otp/verify', {
      'verificationToken': verificationToken,
      'code': code,
    }, authenticated: false);
    return MobileAuthSession.fromJson(response);
  }

  @override
  Future<MobileEmailOtpChallenge> resendEmailOtp(
    String verificationToken,
  ) async {
    final response = await _apiClient.postObject('mobile/auth/otp/resend', {
      'verificationToken': verificationToken,
    }, authenticated: false);
    return MobileEmailOtpChallenge.fromJson(response);
  }

  @override
  Future<MobileAuthSession> refresh(String refreshToken) async {
    final response = await _apiClient.postObject('mobile/auth/refresh', {
      'refreshToken': refreshToken,
    }, authenticated: false);
    return MobileAuthSession.fromJson(response);
  }

  @override
  Future<void> logout(String refreshToken) {
    return _apiClient.postNoContent('mobile/auth/logout', {
      'refreshToken': refreshToken,
    }, authenticated: false);
  }
}
