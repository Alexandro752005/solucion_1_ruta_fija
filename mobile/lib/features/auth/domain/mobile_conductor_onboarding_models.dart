final class MobileConductorOnboardingChallenge {
  const MobileConductorOnboardingChallenge({
    required this.verificationToken,
    required this.otpExpiresAt,
    required this.challengeExpiresAt,
    required this.emailSent,
  });

  final String verificationToken;
  final DateTime otpExpiresAt;
  final DateTime challengeExpiresAt;
  final bool emailSent;

  factory MobileConductorOnboardingChallenge.fromJson(
    Map<String, dynamic> json,
  ) {
    final otpExpiresAt = DateTime.tryParse(
      _requiredString(json, 'otpExpiresAt'),
    );
    final challengeExpiresAt = DateTime.tryParse(
      _requiredString(json, 'challengeExpiresAt'),
    );
    if (otpExpiresAt == null || challengeExpiresAt == null) {
      throw const FormatException('La vigencia de verificación no es válida.');
    }
    return MobileConductorOnboardingChallenge(
      verificationToken: _requiredString(json, 'verificationToken'),
      otpExpiresAt: otpExpiresAt,
      challengeExpiresAt: challengeExpiresAt,
      emailSent: json['emailSent'] == true,
    );
  }
}

final class MobileConductorOnboardingAccepted {
  const MobileConductorOnboardingAccepted({
    required this.requestId,
    required this.status,
  });

  final String requestId;
  final String status;

  factory MobileConductorOnboardingAccepted.fromJson(
    Map<String, dynamic> json,
  ) {
    final requestId = _requiredString(json, 'requestId');
    final status = _requiredString(json, 'status');
    if (status != 'PENDING_REVIEW') {
      throw const FormatException(
        'La incorporación no quedó en el estado de revisión esperado.',
      );
    }
    return MobileConductorOnboardingAccepted(
      requestId: requestId,
      status: status,
    );
  }
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException(
    'Falta el campo $key en la respuesta de incorporación.',
  );
}
