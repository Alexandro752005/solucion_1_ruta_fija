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
  });

  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final int refreshExpiresIn;
  final MobileAuthenticatedUser user;

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
    );
  }
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
