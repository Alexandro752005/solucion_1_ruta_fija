enum MobileAvailabilityStatus {
  disponible('DISPONIBLE', 'Disponible'),
  reservado('RESERVADO', 'Reservado'),
  enServicio('EN_SERVICIO', 'En servicio'),
  descanso('DESCANSO', 'Descanso'),
  noDisponible('NO_DISPONIBLE', 'No disponible');

  const MobileAvailabilityStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  bool get canBeChosenByDriver {
    return this == disponible || this == descanso || this == noDisponible;
  }

  static MobileAvailabilityStatus fromApi(String value) {
    return MobileAvailabilityStatus.values.firstWhere(
      (status) => status.apiValue == value,
      orElse: () =>
          throw FormatException('Estado de disponibilidad desconocido: $value'),
    );
  }
}

final class MobileVehicle {
  const MobileVehicle({required this.plate, required this.status});

  final String plate;
  final String status;

  factory MobileVehicle.fromJson(Map<String, dynamic> json) {
    return MobileVehicle(
      plate: _requiredString(json, 'plate'),
      status: _requiredString(json, 'status'),
    );
  }
}

final class MobileDriverProfile {
  const MobileDriverProfile({
    required this.driverId,
    required this.fullName,
    required this.groupName,
    required this.availabilityStatus,
    required this.primaryVehicle,
  });

  final String driverId;
  final String fullName;
  final String groupName;
  final MobileAvailabilityStatus availabilityStatus;
  final MobileVehicle? primaryVehicle;

  factory MobileDriverProfile.fromJson(Map<String, dynamic> json) {
    final rawVehicle = json['primaryVehicle'];
    return MobileDriverProfile(
      driverId: _requiredString(json, 'driverId'),
      fullName: _requiredString(json, 'fullName'),
      groupName: _requiredString(json, 'groupName'),
      availabilityStatus: MobileAvailabilityStatus.fromApi(
        _requiredString(json, 'availabilityStatus'),
      ),
      primaryVehicle: rawVehicle is Map
          ? MobileVehicle.fromJson(Map<String, dynamic>.from(rawVehicle))
          : null,
    );
  }
}

final class MobileAvailability {
  const MobileAvailability({required this.driverId, required this.status});

  final String driverId;
  final MobileAvailabilityStatus status;

  factory MobileAvailability.fromJson(Map<String, dynamic> json) {
    return MobileAvailability(
      driverId: _requiredString(json, 'driverId'),
      status: MobileAvailabilityStatus.fromApi(_requiredString(json, 'status')),
    );
  }
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('Falta el campo $key en el perfil móvil.');
}
