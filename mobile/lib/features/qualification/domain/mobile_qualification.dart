import '../../auth/domain/mobile_applicant_documentary_models.dart';

/// Only public references and states are retained. Personal document fields and
/// review references are deliberately not parsed, even if a server regresses.
final class MobileQualification {
  const MobileQualification({
    required this.driverId,
    required this.version,
    required this.suspended,
    required this.operationalEnabled,
    required this.personalDocumentsStatus,
    required this.blockers,
    required this.periods,
    required this.unitRequests,
    required this.observedAt,
    this.personalValidUntil,
    this.serviceAttention = const [],
    this.personalDocumentStates = const [],
  });

  final String driverId;
  final int version;
  final bool suspended;
  final bool operationalEnabled;
  final String personalDocumentsStatus;
  final DateTime? personalValidUntil;
  final List<String> blockers;
  final List<MobileQualificationPeriod> periods;
  final List<MobileUnitRequest> unitRequests;
  final DateTime observedAt;
  final List<MobileServiceAttention> serviceAttention;
  final List<MobilePersonalDocumentState> personalDocumentStates;

  bool get hasPendingUnitRequest =>
      unitRequests.any((request) => request.status == 'PENDING_REVIEW');

  factory MobileQualification.fromJson(Map<String, dynamic> json) {
    final blockers = json['blockers'];
    if (blockers is! List || blockers.any((item) => item is! String)) {
      throw const FormatException('Estado de habilitación no válido.');
    }
    return MobileQualification(
      driverId: _string(json, 'driverId'),
      version: _version(json),
      suspended: _boolean(json, 'suspended'),
      operationalEnabled: _boolean(json, 'operationalEnabled'),
      personalDocumentsStatus: _string(json, 'personalDocumentsStatus'),
      personalValidUntil: _date(json, 'personalValidUntil'),
      blockers: blockers.cast<String>().toList(growable: false),
      periods: _list(
        json,
        'periods',
      ).map(MobileQualificationPeriod.fromJson).toList(growable: false),
      unitRequests: _list(
        json,
        'unitRequests',
      ).map(MobileUnitRequest.fromJson).toList(growable: false),
      observedAt:
          _date(json, 'observedAt') ??
          (throw const FormatException('Falta la fecha del servidor.')),
      personalDocumentStates: json['personalDocumentStates'] == null
          ? const []
          : _list(
              json,
              'personalDocumentStates',
            ).map(MobilePersonalDocumentState.fromJson).toList(growable: false),
      serviceAttention: json['serviceAttention'] == null
          ? const []
          : _list(
              json,
              'serviceAttention',
            ).map(MobileServiceAttention.fromJson).toList(growable: false),
    );
  }
}

final class MobilePersonalDocumentState {
  const MobilePersonalDocumentState({
    required this.requirementId,
    required this.result,
    required this.applicability,
    this.observation,
  });
  final String requirementId;
  final String result;
  final String applicability;
  final String? observation;
  factory MobilePersonalDocumentState.fromJson(Map<String, dynamic> json) =>
      MobilePersonalDocumentState(
        requirementId: _string(json, 'requirementId'),
        result: _string(json, 'result'),
        applicability: _string(json, 'applicability'),
        observation: json['observation'] is String
            ? json['observation'] as String
            : null,
      );
}

final class MobileServiceAttention {
  const MobileServiceAttention({
    required this.id,
    required this.assignmentId,
    required this.kind,
    required this.status,
    this.scheduledAt,
    this.scheduledEndAt,
  });
  final String id;
  final String assignmentId;
  final String kind;
  final String status;
  final DateTime? scheduledAt;
  final DateTime? scheduledEndAt;
  factory MobileServiceAttention.fromJson(Map<String, dynamic> json) =>
      MobileServiceAttention(
        id: _string(json, 'id'),
        assignmentId: _string(json, 'assignmentId'),
        kind: _string(json, 'kind'),
        status: _string(json, 'status'),
        scheduledAt: _date(json, 'scheduledAt'),
        scheduledEndAt: _date(json, 'scheduledEndAt'),
      );
}

final class MobileQualificationPeriod {
  const MobileQualificationPeriod({
    required this.id,
    required this.vehicleId,
    required this.kind,
    required this.status,
    required this.startsAt,
    this.endsAt,
  });
  final String id;
  final String vehicleId;
  final String kind;
  final String status;
  final DateTime startsAt;
  final DateTime? endsAt;

  factory MobileQualificationPeriod.fromJson(Map<String, dynamic> json) =>
      MobileQualificationPeriod(
        id: _string(json, 'id'),
        vehicleId: _string(json, 'vehicleId'),
        kind: _choice(json, 'kind', const {'OWN', 'COMPANY'}),
        status: _choice(json, 'status', const {'AUTHORIZED', 'REVOKED'}),
        startsAt:
            _date(json, 'startsAt') ??
            (throw const FormatException('Falta inicio del periodo.')),
        endsAt: _date(json, 'endsAt'),
      );
}

final class MobileUnitRequest {
  const MobileUnitRequest({
    required this.id,
    required this.kind,
    required this.status,
    required this.reference,
    required this.createdAt,
    this.vehicleId,
  });
  final String id;
  final String? vehicleId;
  final String kind;
  final String status;
  final String reference;
  final DateTime createdAt;

  factory MobileUnitRequest.fromJson(Map<String, dynamic> json) =>
      MobileUnitRequest(
        id: _string(json, 'id'),
        vehicleId: json['vehicleId'] as String?,
        kind: _choice(json, 'kind', const {'OWN', 'COMPANY'}),
        status: _choice(json, 'status', const {
          'PENDING_REVIEW',
          'APPROVED',
          'REJECTED',
        }),
        reference: _string(json, 'reference'),
        createdAt:
            _date(json, 'createdAt') ??
            (throw const FormatException('Falta fecha de solicitud.')),
      );
}

typedef MobilePersonalRequirement = MobileApplicantRequirement;
typedef MobileQualificationDocumentInput = MobileApplicantDocumentInput;

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) return value.trim();
  throw FormatException('Falta $key en habilitación.');
}

String _choice(Map<String, dynamic> json, String key, Set<String> choices) {
  final value = _string(json, key);
  if (!choices.contains(value)) {
    throw const FormatException('Estado desconocido.');
  }
  return value;
}

int _version(Map<String, dynamic> json) {
  final value = json['version'];
  if (value is int && value >= 0) return value;
  throw const FormatException('Versión de habilitación no válida.');
}

bool _boolean(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is bool) return value;
  throw const FormatException('Estado de habilitación incompleto.');
}

DateTime? _date(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) return null;
  if (value is String) {
    final parsed = DateTime.tryParse(value);
    if (parsed != null && parsed.isUtc) return parsed;
  }
  throw const FormatException('Fecha de habilitación no válida.');
}

List<Map<String, dynamic>> _list(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! List || value.any((item) => item is! Map)) {
    throw const FormatException('Listado de habilitación no válido.');
  }
  return value
      .map((item) => Map<String, dynamic>.from(item as Map))
      .toList(growable: false);
}
