enum MobileAssignmentStatus {
  pendingResponse('PENDING_RESPONSE', 'Pendiente de respuesta'),
  scheduled('SCHEDULED', 'Programada'),
  enServicio('EN_SERVICIO', 'En servicio'),
  completed('COMPLETED', 'Completada'),
  rejected('REJECTED', 'Rechazada'),
  cancelled('CANCELLED', 'Cancelada'),
  expired('EXPIRED', 'Vencida');

  const MobileAssignmentStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  bool get isTerminal {
    return this == completed ||
        this == rejected ||
        this == cancelled ||
        this == expired;
  }

  static MobileAssignmentStatus fromApi(String value) {
    return MobileAssignmentStatus.values.firstWhere(
      (status) => status.apiValue == value,
      orElse: () =>
          throw FormatException('Estado de asignación desconocido: $value'),
    );
  }
}

enum MobileAssignmentResponseMode {
  adminDirect('ADMIN_DIRECT', 'Programación administrativa'),
  mobileConfirmation('MOBILE_CONFIRMATION', 'Respuesta requerida');

  const MobileAssignmentResponseMode(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static MobileAssignmentResponseMode fromApi(String value) {
    return MobileAssignmentResponseMode.values.firstWhere(
      (mode) => mode.apiValue == value,
      orElse: () =>
          throw FormatException('Modo de respuesta desconocido: $value'),
    );
  }
}

enum MobileAssignmentCommandType {
  accept('accept', 'Aceptar asignación'),
  reject('reject', 'Rechazar asignación'),
  start('start', 'Iniciar servicio'),
  complete('complete', 'Completar servicio');

  const MobileAssignmentCommandType(this.endpointSegment, this.label);

  final String endpointSegment;
  final String label;

  static MobileAssignmentCommandType fromStorage(String value) {
    return MobileAssignmentCommandType.values.firstWhere(
      (command) => command.name == value,
      orElse: () =>
          throw FormatException('Comando de asignación desconocido: $value'),
    );
  }
}

final class MobileAssignment {
  const MobileAssignment({
    required this.id,
    required this.status,
    required this.responseMode,
    required this.version,
    required this.driverId,
    required this.driverName,
    required this.groupName,
    required this.vehiclePlate,
    required this.originText,
    required this.destinationText,
    required this.scheduledAt,
    required this.scheduledEndAt,
    required this.reservedAt,
    required this.startedAt,
    required this.completedAt,
    required this.responseDeadlineAt,
    required this.acceptedAt,
    required this.rejectedAt,
    required this.rejectionReason,
    required this.expiredAt,
    required this.notes,
  });

  final String id;
  final MobileAssignmentStatus status;
  final MobileAssignmentResponseMode responseMode;
  final int version;
  final String driverId;
  final String driverName;
  final String groupName;
  final String vehiclePlate;
  final String originText;
  final String destinationText;
  final DateTime scheduledAt;
  final DateTime scheduledEndAt;
  final DateTime? reservedAt;
  final DateTime? startedAt;
  final DateTime? completedAt;
  final DateTime? responseDeadlineAt;
  final DateTime? acceptedAt;
  final DateTime? rejectedAt;
  final String? rejectionReason;
  final DateTime? expiredAt;
  final String? notes;

  bool get requiresMobileResponse {
    return status == MobileAssignmentStatus.pendingResponse &&
        responseMode == MobileAssignmentResponseMode.mobileConfirmation;
  }

  bool get canStart {
    return status == MobileAssignmentStatus.scheduled && reservedAt != null;
  }

  bool get canComplete => status == MobileAssignmentStatus.enServicio;

  bool allows(MobileAssignmentCommandType command) {
    return switch (command) {
      MobileAssignmentCommandType.accept ||
      MobileAssignmentCommandType.reject => requiresMobileResponse,
      MobileAssignmentCommandType.start => canStart,
      MobileAssignmentCommandType.complete => canComplete,
    };
  }

  factory MobileAssignment.fromJson(Map<String, dynamic> json) {
    return MobileAssignment(
      id: _requiredString(json, 'id'),
      status: MobileAssignmentStatus.fromApi(_requiredString(json, 'status')),
      responseMode: MobileAssignmentResponseMode.fromApi(
        _requiredString(json, 'responseMode'),
      ),
      version: _requiredInt(json, 'version'),
      driverId: _requiredString(json, 'driverId'),
      driverName: _requiredString(json, 'driverName'),
      groupName: _requiredString(json, 'groupName'),
      vehiclePlate: _requiredString(json, 'vehiclePlate'),
      originText: _requiredString(json, 'originText'),
      destinationText: _requiredString(json, 'destinationText'),
      scheduledAt: _requiredDateTime(json, 'scheduledAt'),
      scheduledEndAt: _requiredDateTime(json, 'scheduledEndAt'),
      reservedAt: _nullableDateTime(json, 'reservedAt'),
      startedAt: _nullableDateTime(json, 'startedAt'),
      completedAt: _nullableDateTime(json, 'completedAt'),
      responseDeadlineAt: _nullableDateTime(json, 'responseDeadlineAt'),
      acceptedAt: _nullableDateTime(json, 'acceptedAt'),
      rejectedAt: _nullableDateTime(json, 'rejectedAt'),
      rejectionReason: _nullableString(json, 'rejectionReason'),
      expiredAt: _nullableDateTime(json, 'expiredAt'),
      notes: _nullableString(json, 'notes'),
    );
  }
}

final class MobileAssignmentPage {
  const MobileAssignmentPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  final List<MobileAssignment> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;

  factory MobileAssignmentPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('Falta la lista de asignaciones.');
    }
    return MobileAssignmentPage(
      items: rawItems
          .map((item) {
            if (item is! Map) {
              throw const FormatException('Una asignación no es válida.');
            }
            return MobileAssignment.fromJson(Map<String, dynamic>.from(item));
          })
          .toList(growable: false),
      page: _requiredInt(json, 'page'),
      size: _requiredInt(json, 'size'),
      totalItems: _requiredInt(json, 'totalItems'),
      totalPages: _requiredInt(json, 'totalPages'),
    );
  }
}

/// A command is written before it is sent so a retry preserves its identity.
final class MobilePendingAssignmentCommand {
  const MobilePendingAssignmentCommand({
    required this.driverId,
    required this.assignmentId,
    required this.command,
    required this.clientEventId,
    required this.version,
    required this.occurredAt,
    required this.rejectionReason,
  });

  final String driverId;
  final String assignmentId;
  final MobileAssignmentCommandType command;
  final String clientEventId;
  final int version;
  final DateTime occurredAt;
  final String? rejectionReason;

  Map<String, Object?> toRequestBody() {
    final body = <String, Object?>{
      'clientEventId': clientEventId,
      'version': version,
      'occurredAt': occurredAt.toUtc().toIso8601String(),
    };
    final reason = rejectionReason?.trim();
    if (command == MobileAssignmentCommandType.reject &&
        reason != null &&
        reason.isNotEmpty) {
      body['reason'] = reason;
    }
    return body;
  }

  Map<String, Object?> toStorageJson() => {
    'driverId': driverId,
    'assignmentId': assignmentId,
    'command': command.name,
    'clientEventId': clientEventId,
    'version': version,
    'occurredAt': occurredAt.toUtc().toIso8601String(),
    'rejectionReason': rejectionReason,
  };

  factory MobilePendingAssignmentCommand.fromStorageJson(
    Map<String, dynamic> json,
  ) {
    return MobilePendingAssignmentCommand(
      driverId: _requiredString(json, 'driverId'),
      assignmentId: _requiredString(json, 'assignmentId'),
      command: MobileAssignmentCommandType.fromStorage(
        _requiredString(json, 'command'),
      ),
      clientEventId: _requiredString(json, 'clientEventId'),
      version: _requiredInt(json, 'version'),
      occurredAt: _requiredDateTime(json, 'occurredAt'),
      rejectionReason: _nullableString(json, 'rejectionReason'),
    );
  }
}

final class MobileAssignmentCommandResult {
  const MobileAssignmentCommandResult({
    required this.assignment,
    required this.replayed,
  });

  final MobileAssignment assignment;
  final bool replayed;
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('Falta el campo $key en la asignación móvil.');
}

String? _nullableString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is String) {
    final normalized = value.trim();
    return normalized.isEmpty ? null : normalized;
  }
  throw FormatException('El campo $key no es texto válido.');
}

int _requiredInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is int && value >= 0) {
    return value;
  }
  if (value is num && value >= 0 && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException('Falta o es inválido el número $key.');
}

DateTime _requiredDateTime(Map<String, dynamic> json, String key) {
  final value = _nullableDateTime(json, key);
  if (value == null) {
    throw FormatException('Falta la fecha $key.');
  }
  return value;
}

DateTime? _nullableDateTime(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('La fecha $key no es válida.');
  }
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw FormatException('La fecha $key no es válida.');
  }
  return parsed.toUtc();
}
