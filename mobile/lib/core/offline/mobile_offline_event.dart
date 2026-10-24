import 'dart:convert';
import 'dart:math';

/// The only operations that may be retained for a later, foreground sync.
///
/// This deliberately excludes GPS samples, incidents, photos, announcements
/// and assignment responses. Those operations need their own explicit product
/// contract before they can be persisted on a device.
enum MobileOfflineEventType {
  availabilityChange('AVAILABILITY_CHANGE'),
  serviceStart('SERVICE_START'),
  serviceComplete('SERVICE_COMPLETE');

  const MobileOfflineEventType(this.apiValue);

  final String apiValue;

  static MobileOfflineEventType fromApiValue(String value) {
    return MobileOfflineEventType.values.firstWhere(
      (type) => type.apiValue == value,
      orElse: () =>
          throw FormatException('Tipo de evento offline no permitido: $value.'),
    );
  }
}

/// A blocked event is intentionally never retried until the conductor reviews
/// and discards it. This prevents a stale command from overtaking the FIFO.
enum MobileOfflineEventState {
  pending('PENDING'),
  blocked('BLOCKED');

  const MobileOfflineEventState(this.storageValue);

  final String storageValue;

  static MobileOfflineEventState fromStorageValue(String value) {
    return MobileOfflineEventState.values.firstWhere(
      (state) => state.storageValue == value,
      orElse: () =>
          throw FormatException('Estado de evento offline no válido: $value.'),
    );
  }
}

/// Identifies one protected queue. A driver id alone is not sufficient: a
/// shared device must never expose one account's pending commands to another.
final class MobileOfflineQueueOwner {
  MobileOfflineQueueOwner({required String userId, required String driverId})
    : userId = _requiredText(userId, 'userId'),
      driverId = _requiredText(driverId, 'driverId');

  final String userId;
  final String driverId;

  /// A URL-safe opaque storage scope, without placing raw account ids in the
  /// secure-storage key itself.
  String get storageScope => base64Url
      .encode(utf8.encode('$userId\u0000$driverId'))
      .replaceAll('=', '');

  bool sameAs(MobileOfflineQueueOwner other) =>
      userId == other.userId && driverId == other.driverId;

  Map<String, Object?> toStorageJson() => {
    'userId': userId,
    'driverId': driverId,
  };

  factory MobileOfflineQueueOwner.fromStorageJson(Map<String, dynamic> json) {
    return MobileOfflineQueueOwner(
      userId: _requiredJsonText(json, 'userId'),
      driverId: _requiredJsonText(json, 'driverId'),
    );
  }
}

/// Durable, immutable identity and payload for one M8 mobile sync event.
///
/// A caller writes this event *before* attempting delivery. Retrying must use
/// the exact same [eventId], [occurredAt], and [toSyncEventJson] payload.
final class MobileOfflineEvent {
  MobileOfflineEvent({
    required String eventId,
    required this.owner,
    required this.type,
    required DateTime occurredAt,
    this.assignmentId,
    this.version,
    this.targetAvailability,
    DateTime? expectedAvailabilityUpdatedAt,
    this.state = MobileOfflineEventState.pending,
    this.attempts = 0,
    DateTime? nextRetryAt,
    this.blockedCode,
    this.blockedMessage,
  }) : eventId = _requiredText(eventId, 'eventId'),
       occurredAt = occurredAt.toUtc(),
       expectedAvailabilityUpdatedAt = expectedAvailabilityUpdatedAt?.toUtc(),
       nextRetryAt = nextRetryAt?.toUtc() {
    _validate();
  }

  factory MobileOfflineEvent.availabilityChange({
    required MobileOfflineQueueOwner owner,
    required String targetAvailability,
    required DateTime expectedAvailabilityUpdatedAt,
    DateTime? occurredAt,
    String? eventId,
  }) {
    return MobileOfflineEvent(
      eventId: eventId ?? createMobileOfflineEventId(),
      owner: owner,
      type: MobileOfflineEventType.availabilityChange,
      occurredAt: occurredAt ?? DateTime.now().toUtc(),
      targetAvailability: targetAvailability,
      expectedAvailabilityUpdatedAt: expectedAvailabilityUpdatedAt,
    );
  }

  factory MobileOfflineEvent.serviceStart({
    required MobileOfflineQueueOwner owner,
    required String assignmentId,
    required int version,
    DateTime? occurredAt,
    String? eventId,
  }) {
    return MobileOfflineEvent(
      eventId: eventId ?? createMobileOfflineEventId(),
      owner: owner,
      type: MobileOfflineEventType.serviceStart,
      occurredAt: occurredAt ?? DateTime.now().toUtc(),
      assignmentId: assignmentId,
      version: version,
    );
  }

  factory MobileOfflineEvent.serviceComplete({
    required MobileOfflineQueueOwner owner,
    required String assignmentId,
    required int version,
    DateTime? occurredAt,
    String? eventId,
  }) {
    return MobileOfflineEvent(
      eventId: eventId ?? createMobileOfflineEventId(),
      owner: owner,
      type: MobileOfflineEventType.serviceComplete,
      occurredAt: occurredAt ?? DateTime.now().toUtc(),
      assignmentId: assignmentId,
      version: version,
    );
  }

  final String eventId;
  final MobileOfflineQueueOwner owner;
  final MobileOfflineEventType type;
  final String? assignmentId;
  final int? version;
  final DateTime occurredAt;
  final String? targetAvailability;
  final DateTime? expectedAvailabilityUpdatedAt;
  final MobileOfflineEventState state;
  final int attempts;
  final DateTime? nextRetryAt;
  final String? blockedCode;
  final String? blockedMessage;

  bool get isBlocked => state == MobileOfflineEventState.blocked;

  bool isEligibleAt(DateTime instant) {
    if (isBlocked) {
      return false;
    }
    final retryAt = nextRetryAt;
    return retryAt == null || !retryAt.isAfter(instant.toUtc());
  }

  /// Body of a member of `POST /mobile/sync/events`.
  Map<String, Object?> toSyncEventJson() {
    final body = <String, Object?>{
      'eventId': eventId,
      'type': type.apiValue,
      'occurredAt': occurredAt.toUtc().toIso8601String(),
    };
    if (assignmentId != null) {
      body['assignmentId'] = assignmentId;
    }
    if (version != null) {
      body['version'] = version;
    }
    if (targetAvailability != null) {
      body['targetAvailability'] = targetAvailability;
    }
    if (expectedAvailabilityUpdatedAt != null) {
      body['expectedAvailabilityUpdatedAt'] = expectedAvailabilityUpdatedAt!
          .toUtc()
          .toIso8601String();
    }
    return Map.unmodifiable(body);
  }

  Map<String, Object?> toStorageJson() => {
    'eventId': eventId,
    'owner': owner.toStorageJson(),
    'type': type.apiValue,
    'assignmentId': assignmentId,
    'version': version,
    'occurredAt': occurredAt.toUtc().toIso8601String(),
    'targetAvailability': targetAvailability,
    'expectedAvailabilityUpdatedAt': expectedAvailabilityUpdatedAt
        ?.toUtc()
        .toIso8601String(),
    'state': state.storageValue,
    'attempts': attempts,
    'nextRetryAt': nextRetryAt?.toUtc().toIso8601String(),
    'blockedCode': blockedCode,
    'blockedMessage': blockedMessage,
  };

  factory MobileOfflineEvent.fromStorageJson(Map<String, dynamic> json) {
    final rawOwner = json['owner'];
    if (rawOwner is! Map) {
      throw const FormatException('Falta el propietario del evento offline.');
    }
    return MobileOfflineEvent(
      eventId: _requiredJsonText(json, 'eventId'),
      owner: MobileOfflineQueueOwner.fromStorageJson(
        Map<String, dynamic>.from(rawOwner),
      ),
      type: MobileOfflineEventType.fromApiValue(
        _requiredJsonText(json, 'type'),
      ),
      assignmentId: _optionalJsonText(json, 'assignmentId'),
      version: _optionalJsonNonNegativeInt(json, 'version'),
      occurredAt: _requiredJsonDateTime(json, 'occurredAt'),
      targetAvailability: _optionalJsonText(json, 'targetAvailability'),
      expectedAvailabilityUpdatedAt: _optionalJsonDateTime(
        json,
        'expectedAvailabilityUpdatedAt',
      ),
      state: MobileOfflineEventState.fromStorageValue(
        _requiredJsonText(json, 'state'),
      ),
      attempts: _requiredJsonNonNegativeInt(json, 'attempts'),
      nextRetryAt: _optionalJsonDateTime(json, 'nextRetryAt'),
      blockedCode: _optionalJsonText(json, 'blockedCode'),
      blockedMessage: _optionalJsonText(json, 'blockedMessage'),
    );
  }

  MobileOfflineEvent afterTemporaryFailure({
    required int attempts,
    required DateTime nextRetryAt,
  }) {
    return MobileOfflineEvent(
      eventId: eventId,
      owner: owner,
      type: type,
      occurredAt: occurredAt,
      assignmentId: assignmentId,
      version: version,
      targetAvailability: targetAvailability,
      expectedAvailabilityUpdatedAt: expectedAvailabilityUpdatedAt,
      state: MobileOfflineEventState.pending,
      attempts: attempts,
      nextRetryAt: nextRetryAt,
    );
  }

  MobileOfflineEvent asBlocked({String? code, String? message}) {
    return MobileOfflineEvent(
      eventId: eventId,
      owner: owner,
      type: type,
      occurredAt: occurredAt,
      assignmentId: assignmentId,
      version: version,
      targetAvailability: targetAvailability,
      expectedAvailabilityUpdatedAt: expectedAvailabilityUpdatedAt,
      state: MobileOfflineEventState.blocked,
      attempts: attempts,
      blockedCode: _normalizedOptionalText(code),
      blockedMessage: _normalizedOptionalText(message),
    );
  }

  void _validate() {
    if (attempts < 0) {
      throw const FormatException(
        'Los intentos offline no pueden ser negativos.',
      );
    }
    if (version != null && version! < 0) {
      throw const FormatException('La versión offline no puede ser negativa.');
    }

    switch (type) {
      case MobileOfflineEventType.availabilityChange:
        if (assignmentId != null || version != null) {
          throw const FormatException(
            'AVAILABILITY_CHANGE no puede contener una asignación.',
          );
        }
        if (_normalizedOptionalText(targetAvailability) == null ||
            expectedAvailabilityUpdatedAt == null) {
          throw const FormatException(
            'AVAILABILITY_CHANGE requiere estado y versión de disponibilidad.',
          );
        }
      case MobileOfflineEventType.serviceStart ||
          MobileOfflineEventType.serviceComplete:
        if (_normalizedOptionalText(assignmentId) == null || version == null) {
          throw const FormatException(
            'El evento de servicio requiere asignación y versión.',
          );
        }
        if (targetAvailability != null ||
            expectedAvailabilityUpdatedAt != null) {
          throw const FormatException(
            'El evento de servicio no admite campos de disponibilidad.',
          );
        }
    }
  }
}

/// Generates a UUID v4 without introducing another application dependency.
String createMobileOfflineEventId() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-'
      '${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-'
      '${hex.substring(20)}';
}

String _requiredText(String value, String field) {
  final normalized = value.trim();
  if (normalized.isEmpty) {
    throw FormatException('El campo $field es obligatorio.');
  }
  return normalized;
}

String? _normalizedOptionalText(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim();
  return normalized.isEmpty ? null : normalized;
}

String _requiredJsonText(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String) {
    return _requiredText(value, key);
  }
  throw FormatException('Falta el campo $key en el evento offline.');
}

String? _optionalJsonText(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is String) {
    return _normalizedOptionalText(value);
  }
  throw FormatException('El campo $key del evento offline no es texto.');
}

int _requiredJsonNonNegativeInt(Map<String, dynamic> json, String key) {
  final value = _optionalJsonNonNegativeInt(json, key);
  if (value == null) {
    throw FormatException('Falta el número $key en el evento offline.');
  }
  return value;
}

int? _optionalJsonNonNegativeInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is int && value >= 0) {
    return value;
  }
  if (value is num && value >= 0 && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException(
    'El campo $key del evento offline no es un número válido.',
  );
}

DateTime _requiredJsonDateTime(Map<String, dynamic> json, String key) {
  final value = _optionalJsonDateTime(json, key);
  if (value == null) {
    throw FormatException('Falta la fecha $key en el evento offline.');
  }
  return value;
}

DateTime? _optionalJsonDateTime(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('La fecha $key del evento offline no es válida.');
  }
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw FormatException('La fecha $key del evento offline no es válida.');
  }
  return parsed.toUtc();
}
