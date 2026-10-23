import 'dart:convert';

enum MobileAccessScope { incorporation, operations }

final class MobileAuthenticatedUser {
  const MobileAuthenticatedUser({
    required this.id,
    required this.driverId,
    required this.fullName,
    required this.role,
  });

  final String id;
  final String driverId;
  final String fullName;
  final String role;

  factory MobileAuthenticatedUser.fromJson(Map<String, dynamic> json) {
    final role = _requiredString(json, 'role');
    if (role != 'CONDUCTOR') {
      throw const FormatException('La sesión no pertenece a un conductor.');
    }

    return MobileAuthenticatedUser(
      id: _requiredString(json, 'id'),
      driverId: _requiredString(json, 'driverId'),
      fullName: _requiredString(json, 'fullName'),
      role: role,
    );
  }
}

final class MobileAuthSession {
  const MobileAuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.refreshExpiresIn,
    required this.user,
    this.accessScope = MobileAccessScope.incorporation,
  });

  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final int refreshExpiresIn;
  final MobileAuthenticatedUser user;
  final MobileAccessScope accessScope;

  factory MobileAuthSession.fromJson(Map<String, dynamic> json) {
    final tokenType = _requiredString(json, 'tokenType');
    if (tokenType != 'Bearer') {
      throw const FormatException('El tipo de token móvil no es válido.');
    }
    final rawUser = json['user'];
    if (rawUser is! Map) {
      throw const FormatException(
        'La respuesta de sesión no contiene usuario.',
      );
    }

    return MobileAuthSession(
      accessToken: _requiredString(json, 'accessToken'),
      refreshToken: _requiredString(json, 'refreshToken'),
      expiresIn: _requiredPositiveInt(json, 'expiresIn'),
      refreshExpiresIn: _requiredPositiveInt(json, 'refreshExpiresIn'),
      user: MobileAuthenticatedUser.fromJson(
        Map<String, dynamic>.from(rawUser),
      ),
      accessScope: _accessScope(json),
    );
  }
}

/// This is a conservative presentation hint only. API authorization remains
/// server-side. Unknown/malformed claims never unlock operational navigation.
MobileAccessScope _accessScope(Map<String, dynamic> json) {
  var claim = json['accessScope'];
  if (claim == null) {
    try {
      final token = _requiredString(json, 'accessToken').split('.');
      if (token.length == 3) {
        final payload = jsonDecode(
          utf8.decode(base64Url.decode(base64Url.normalize(token[1]))),
        );
        if (payload is Map) claim = payload['accessScope'];
      }
    } on FormatException {
      claim = null;
    }
  }
  return claim == 'OPERATIONS'
      ? MobileAccessScope.operations
      : MobileAccessScope.incorporation;
}

final class MobileEmailOtpChallenge {
  const MobileEmailOtpChallenge({
    required this.verificationToken,
    required this.otpExpiresAt,
    required this.challengeExpiresAt,
    required this.emailSent,
  });

  final String verificationToken;
  final DateTime otpExpiresAt;
  final DateTime challengeExpiresAt;
  final bool emailSent;

  factory MobileEmailOtpChallenge.fromJson(Map<String, dynamic> json) {
    final expiresAt = DateTime.tryParse(_requiredString(json, 'otpExpiresAt'));
    final challengeExpiresAt = DateTime.tryParse(
      _requiredString(json, 'challengeExpiresAt'),
    );
    if (expiresAt == null || challengeExpiresAt == null) {
      throw const FormatException('La vigencia de verificación no es válida.');
    }
    return MobileEmailOtpChallenge(
      verificationToken: _requiredString(json, 'verificationToken'),
      otpExpiresAt: expiresAt,
      challengeExpiresAt: challengeExpiresAt,
      emailSent: json['emailSent'] == true,
    );
  }
}

sealed class MobileAuthLoginResult {
  const MobileAuthLoginResult();

  factory MobileAuthLoginResult.authenticated(MobileAuthSession session) =
      MobileAuthenticatedLoginResult;
  factory MobileAuthLoginResult.verificationRequired(
    MobileEmailOtpChallenge challenge,
  ) = MobileEmailVerificationRequiredResult;
}

final class MobileAuthenticatedLoginResult extends MobileAuthLoginResult {
  const MobileAuthenticatedLoginResult(this.session);

  final MobileAuthSession session;
}

final class MobileEmailVerificationRequiredResult
    extends MobileAuthLoginResult {
  const MobileEmailVerificationRequiredResult(this.challenge);

  final MobileEmailOtpChallenge challenge;
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('Falta el campo $key en la respuesta móvil.');
}

int _requiredPositiveInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is int && value > 0) {
    return value;
  }
  if (value is num && value > 0 && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException('El campo $key no es válido en la respuesta móvil.');
}
