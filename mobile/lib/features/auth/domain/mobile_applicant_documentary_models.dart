/// Applicant metadata intentionally excludes stored personal document fields.
final class MobileApplicantDossier {
  const MobileApplicantDossier({
    required this.requestId,
    required this.status,
    required this.version,
    required this.reviewRounds,
    required this.modality,
    required this.catalogId,
    required this.catalogVersion,
    required this.catalogHash,
    required this.catalog,
    required this.documents,
    required this.history,
    required this.correctionDeadline,
    required this.overdue,
    required this.personalApproved,
    required this.operationalEnabled,
    required this.reapplyAfter,
  });

  final String requestId;
  final String status;
  final int version;
  final int reviewRounds;
  final String? modality;
  final String catalogId;
  final String catalogVersion;
  final String catalogHash;
  final List<MobileApplicantRequirement> catalog;
  final List<MobileApplicantDocumentStatus> documents;
  final List<MobileApplicantDocumentHistory> history;
  final DateTime? correctionDeadline;
  final bool overdue;
  final bool personalApproved;
  final bool operationalEnabled;
  final DateTime? reapplyAfter;

  factory MobileApplicantDossier.fromJson(Map<String, dynamic> json) {
    final status = _string(json, 'status');
    final modality = json['modality'];
    if (!const {
          'PENDING_REVIEW',
          'RETURNED',
          'APPROVED',
          'REJECTED',
        }.contains(status) ||
        (modality != null && !const {'TAXI', 'COLECTIVO'}.contains(modality))) {
      throw const FormatException('El estado documental no es válido.');
    }
    return MobileApplicantDossier(
      requestId: _string(json, 'requestId'),
      status: status,
      version: _integer(json, 'version'),
      reviewRounds: _integer(json, 'reviewRounds'),
      modality: modality as String?,
      catalogId: _string(json, 'catalogId'),
      catalogVersion: _string(json, 'catalogVersion'),
      catalogHash: _string(json, 'catalogHash'),
      catalog: _list(
        json,
        'catalog',
      ).map(MobileApplicantRequirement.fromJson).toList(growable: false),
      documents: _list(
        json,
        'documents',
      ).map(MobileApplicantDocumentStatus.fromJson).toList(growable: false),
      history: _list(
        json,
        'history',
      ).map(MobileApplicantDocumentHistory.fromJson).toList(growable: false),
      correctionDeadline: _date(json, 'correctionDeadline'),
      overdue: json['overdue'] == true,
      personalApproved: json['personalApproved'] == true,
      operationalEnabled: json['operationalEnabled'] == true,
      reapplyAfter: _date(json, 'reapplyAfter'),
    );
  }
}

final class MobileApplicantRequirement {
  const MobileApplicantRequirement({
    required this.id,
    required this.title,
    required this.mandatory,
    required this.fieldKeys,
  });
  final String id;
  final String title;
  final bool mandatory;
  final List<String> fieldKeys;

  factory MobileApplicantRequirement.fromJson(Map<String, dynamic> json) {
    final fields = json['fieldKeys'];
    if (fields is! List || fields.any((item) => item is! String)) {
      throw const FormatException('El catálogo documental no es válido.');
    }
    return MobileApplicantRequirement(
      id: _string(json, 'requirementId'),
      title: _string(json, 'title'),
      mandatory: json['mandatory'] == true,
      fieldKeys: fields.cast<String>().toList(growable: false),
    );
  }
}

final class MobileApplicantDocumentStatus {
  const MobileApplicantDocumentStatus({
    required this.requirementId,
    required this.result,
    required this.applicability,
    this.observation,
  });
  final String requirementId;
  final String result;
  final String applicability;
  final String? observation;

  factory MobileApplicantDocumentStatus.fromJson(Map<String, dynamic> json) =>
      MobileApplicantDocumentStatus(
        requirementId: _string(json, 'requirementId'),
        result: _string(json, 'result'),
        applicability: _string(json, 'applicability'),
        observation: json['observation'] as String?,
      );
}

final class MobileApplicantDocumentHistory {
  const MobileApplicantDocumentHistory({
    required this.action,
    required this.occurredAt,
    required this.round,
    this.reason,
  });
  final String action;
  final DateTime occurredAt;
  final int round;
  final String? reason;

  factory MobileApplicantDocumentHistory.fromJson(Map<String, dynamic> json) =>
      MobileApplicantDocumentHistory(
        action: _string(json, 'action'),
        occurredAt:
            _date(json, 'occurredAt') ??
            (throw const FormatException('Falta la fecha del historial.')),
        round: _integer(json, 'round'),
        reason: json['reason'] as String?,
      );
}

/// Fresh input exists only while editing or resolving an uncertain request.
final class MobileApplicantDocumentInput {
  const MobileApplicantDocumentInput({
    required this.requirementId,
    required this.fields,
    required this.expiryMode,
    required this.validUntil,
    required this.applicability,
    this.condition,
    this.sourceReference,
  });
  final String requirementId;
  final Map<String, String> fields;
  final String expiryMode;
  final String? validUntil;
  final String applicability;
  final String? condition;
  final String? sourceReference;

  Map<String, Object?> toJson() => {
    'requirementId': requirementId,
    'fields': fields,
    'expiryMode': expiryMode,
    'validUntil': validUntil,
    'applicability': applicability,
    'condition': condition,
    'sourceReference': sourceReference,
    'result': 'PENDING',
    'reviewReference': null,
    'observation': null,
  };
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) return value.trim();
  throw FormatException('Falta $key en el expediente.');
}

int _integer(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is int && value >= 0) return value;
  throw FormatException('El valor $key no es válido.');
}

DateTime? _date(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) return null;
  if (value is String) {
    final date = DateTime.tryParse(value);
    if (date != null) return date;
  }
  throw FormatException('La fecha $key no es válida.');
}

List<Map<String, dynamic>> _list(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! List || value.any((item) => item is! Map)) {
    throw FormatException('La lista $key no es válida.');
  }
  return value
      .map((item) => Map<String, dynamic>.from(item as Map))
      .toList(growable: false);
}
