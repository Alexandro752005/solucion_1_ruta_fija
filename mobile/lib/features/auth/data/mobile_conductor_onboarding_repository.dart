import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_conductor_onboarding_models.dart';

abstract interface class MobileConductorOnboardingGateway {
  Future<MobileConductorOnboardingChallenge> start({
    required String email,
    required String password,
    required String fullName,
    String? phone,
    required String invitationCode,
    required String idempotencyKey,
  });

  Future<MobileConductorOnboardingChallenge> resendOtp(
    String verificationToken,
  );

  Future<MobileConductorOnboardingAccepted> verifyOtp({
    required String verificationToken,
    required String code,
  });
}

final class MobileConductorOnboardingRepository
    implements MobileConductorOnboardingGateway {
  MobileConductorOnboardingRepository(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileConductorOnboardingChallenge> start({
    required String email,
    required String password,
    required String fullName,
    String? phone,
    required String invitationCode,
    required String idempotencyKey,
  }) async {
    final response = await _apiClient.postObject(
      'onboarding/requests',
      {
        'email': email.trim(),
        'password': password,
        'fullName': fullName.trim(),
        'phone': phone?.trim().isEmpty == true ? null : phone?.trim(),
        'invitationCode': invitationCode.trim(),
      },
      authenticated: false,
      headers: {'Idempotency-Key': idempotencyKey},
    );
    return MobileConductorOnboardingChallenge.fromJson(response);
  }

  @override
  Future<MobileConductorOnboardingChallenge> resendOtp(
    String verificationToken,
  ) async {
    final response = await _apiClient.postObject('onboarding/otp/resend', {
      'verificationToken': verificationToken,
    }, authenticated: false);
    return MobileConductorOnboardingChallenge.fromJson(response);
  }

  @override
  Future<MobileConductorOnboardingAccepted> verifyOtp({
    required String verificationToken,
    required String code,
  }) async {
    final response = await _apiClient.postObject('onboarding/otp/verify', {
      'verificationToken': verificationToken,
      'code': code,
    }, authenticated: false);
    return MobileConductorOnboardingAccepted.fromJson(response);
  }
}
