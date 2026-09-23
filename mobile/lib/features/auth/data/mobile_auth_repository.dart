import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_auth_models.dart';

abstract interface class MobileAuthGateway {
  Future<MobileAuthSession> login({
    required String email,
    required String password,
  });

  Future<MobileAuthSession> refresh(String refreshToken);

  Future<void> logout(String refreshToken);
}

final class MobileAuthRepository implements MobileAuthGateway {
  MobileAuthRepository(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileAuthSession> login({
    required String email,
    required String password,
  }) async {
    final response = await _apiClient.postObject('mobile/auth/login', {
      'email': email.trim(),
      'password': password,
    }, authenticated: false);
    return MobileAuthSession.fromJson(response);
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
