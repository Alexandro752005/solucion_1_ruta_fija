enum MobileIncidentCategory {
  averia('AVERIA', 'Avería'),
  accidente('ACCIDENTE', 'Accidente'),
  retraso('RETRASO', 'Retraso'),
  otro('OTRO', 'Otro');

  const MobileIncidentCategory(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static MobileIncidentCategory fromApi(Object? value) {
    return MobileIncidentCategory.values.firstWhere(
      (item) => item.apiValue == value,
      orElse: () => throw FormatException('Categoría de incidencia inválida.'),
    );
  }
}

enum MobileIncidentStatus {
  open('OPEN', 'Abierta'),
  followUp('FOLLOW_UP', 'En seguimiento'),
  resolved('RESOLVED', 'Resuelta');

  const MobileIncidentStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static MobileIncidentStatus fromApi(Object? value) {
    return MobileIncidentStatus.values.firstWhere(
      (item) => item.apiValue == value,
      orElse: () => throw FormatException('Estado de incidencia inválido.'),
    );
  }
}

enum MobileIncidentSource {
  mobileApp('MOBILE_APP', 'Aplicación móvil'),
  crmWeb('CRM_WEB', 'CRM');

  const MobileIncidentSource(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static MobileIncidentSource fromApi(Object? value) {
    return MobileIncidentSource.values.firstWhere(
      (item) => item.apiValue == value,
      orElse: () => throw FormatException('Origen de incidencia inválido.'),
    );
  }
}

final class MobileIncident {
  const MobileIncident({
    required this.id,
    required this.version,
    required this.driverId,
    required this.driverName,
    required this.assignmentId,
    required this.category,
    required this.source,
    required this.status,
    required this.description,
    required this.reportedAt,
    required this.followUpNote,
    required this.followedUpByName,
    required this.followedUpAt,
    required this.resolvedAt,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final int version;
  final String driverId;
  final String driverName;
  final String? assignmentId;
  final MobileIncidentCategory category;
  final MobileIncidentSource source;
  final MobileIncidentStatus status;
  final String description;
  final DateTime reportedAt;
  final String? followUpNote;
  final String? followedUpByName;
  final DateTime? followedUpAt;
  final DateTime? resolvedAt;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory MobileIncident.fromJson(Map<String, dynamic> json) {
    return MobileIncident(
      id: _requiredText(json['id'], 'id'),
      version: _requiredInt(json['version'], 'version'),
      driverId: _requiredText(json['driverId'], 'driverId'),
      driverName: _requiredText(json['driverName'], 'driverName'),
      assignmentId: _optionalText(json['assignmentId']),
      category: MobileIncidentCategory.fromApi(json['category']),
      source: MobileIncidentSource.fromApi(json['source']),
      status: MobileIncidentStatus.fromApi(json['status']),
      description: _requiredText(json['description'], 'description'),
      reportedAt: _requiredDate(json['reportedAt'], 'reportedAt'),
      followUpNote: _optionalText(json['followUpNote']),
      followedUpByName: _optionalText(json['followedUpByName']),
      followedUpAt: _optionalDate(json['followedUpAt']),
      resolvedAt: _optionalDate(json['resolvedAt']),
      createdAt: _requiredDate(json['createdAt'], 'createdAt'),
      updatedAt: _requiredDate(json['updatedAt'], 'updatedAt'),
    );
  }
}

final class MobileIncidentPage {
  const MobileIncidentPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  final List<MobileIncident> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;

  factory MobileIncidentPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('La lista de incidencias no es válida.');
    }
    return MobileIncidentPage(
      items: rawItems
          .map((item) {
            if (item is! Map) {
              throw const FormatException('Una incidencia no es válida.');
            }
            return MobileIncident.fromJson(Map<String, dynamic>.from(item));
          })
          .toList(growable: false),
      page: _requiredInt(json['page'], 'page'),
      size: _requiredInt(json['size'], 'size'),
      totalItems: _requiredInt(json['totalItems'], 'totalItems'),
      totalPages: _requiredInt(json['totalPages'], 'totalPages'),
    );
  }
}

String _requiredText(Object? value, String field) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('$field es obligatorio.');
}

String? _optionalText(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  return null;
}

int _requiredInt(Object? value, String field) {
  if (value is int) {
    return value;
  }
  throw FormatException('$field debe ser numérico.');
}

DateTime _requiredDate(Object? value, String field) {
  if (value is String) {
    final parsed = DateTime.tryParse(value);
    if (parsed != null) {
      return parsed.toUtc();
    }
  }
  throw FormatException('$field debe ser una fecha UTC válida.');
}

DateTime? _optionalDate(Object? value) {
  if (value == null) {
    return null;
  }
  return _requiredDate(value, 'fecha opcional');
}
