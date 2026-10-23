import '../../../core/network/mobile_api_client.dart';

abstract interface class MobileConductorRecoveryGateway {
  Future<MobileConductorRecoveryOtpResult> verifyOtp({
    required String recoveryId,
    required String verificationToken,
    required String code,
  });

  Future<void> resetPassword({
    required String recoveryId,
    required String passwordResetToken,
    required String newPassword,
  });
}

final class MobileConductorRecoveryRepository
    implements MobileConductorRecoveryGateway {
  MobileConductorRecoveryRepository(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileConductorRecoveryOtpResult> verifyOtp({
    required String recoveryId,
    required String verificationToken,
    required String code,
  }) async {
    final response = await _apiClient.postObject(
      'auth/conductor-recovery/otp/verify',
      {
        'recoveryId': recoveryId.trim(),
        'verificationToken': verificationToken.trim(),
        'code': code,
      },
      authenticated: false,
    );
    return MobileConductorRecoveryOtpResult.fromJson(response);
  }

  @override
  Future<void> resetPassword({
    required String recoveryId,
    required String passwordResetToken,
    required String newPassword,
  }) async {
    await _apiClient.postObject(
      'auth/conductor-recovery/${Uri.encodeComponent(recoveryId.trim())}/password-reset',
      {'passwordResetToken': passwordResetToken, 'newPassword': newPassword},
      authenticated: false,
    );
  }
}

final class MobileConductorRecoveryOtpResult {
  const MobileConductorRecoveryOtpResult({
    required this.passwordResetRequired,
    this.passwordResetToken,
  });

  final bool passwordResetRequired;
  final String? passwordResetToken;

  factory MobileConductorRecoveryOtpResult.fromJson(Map<String, dynamic> json) {
    final resetRequired = json['passwordResetRequired'] == true;
    final token = json['passwordResetToken'];
    if (resetRequired && (token is! String || token.trim().isEmpty)) {
      throw const FormatException(
        'La respuesta no contiene el token temporal de restablecimiento.',
      );
    }
    return MobileConductorRecoveryOtpResult(
      passwordResetRequired: resetRequired,
      passwordResetToken: token is String ? token : null,
    );
  }
}
