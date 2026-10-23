/// Purpose-limited access; never a conductor session or a persisted credential.
final class MobileApplicantAccess {
  const MobileApplicantAccess({required this.token, required this.expiresAt});

  final String token;
  final DateTime expiresAt;

  factory MobileApplicantAccess.fromJson(Map<String, dynamic> json) {
    final token = _string(json, 'accessToken');
    final expiresAt = DateTime.tryParse(_string(json, 'expiresAt'));
    if (!RegExp(r'^oa1\.[A-Za-z0-9_-]{43}$').hasMatch(token) ||
        expiresAt == null) {
      throw const FormatException('El acceso de postulante no es válido.');
    }
    return MobileApplicantAccess(token: token, expiresAt: expiresAt);
  }
}

/// Only identifiers and review metadata are available in the applicant list.
final class MobileApplicantRequest {
  const MobileApplicantRequest({
    required this.requestId,
    required this.organizationId,
    required this.status,
    required this.reviewRounds,
    required this.submittedAt,
  });

  final String requestId;
  final String organizationId;
  final String status;
  final int reviewRounds;
  final DateTime submittedAt;

  factory MobileApplicantRequest.fromJson(Map<String, dynamic> json) {
    final rounds = json['reviewRounds'];
    final submittedAt = DateTime.tryParse(_string(json, 'submittedAt'));
    final status = _string(json, 'status');
    if (rounds is! int ||
        rounds < 0 ||
        submittedAt == null ||
        !const {
          'PENDING_REVIEW',
          'RETURNED',
          'APPROVED',
          'REJECTED',
        }.contains(status)) {
      throw const FormatException('El estado de la solicitud no es válido.');
    }
    return MobileApplicantRequest(
      requestId: _string(json, 'requestId'),
      organizationId: _string(json, 'organizationId'),
      status: status,
      reviewRounds: rounds,
      submittedAt: submittedAt,
    );
  }
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('Falta $key en la respuesta de postulante.');
  }
  return value.trim();
}
