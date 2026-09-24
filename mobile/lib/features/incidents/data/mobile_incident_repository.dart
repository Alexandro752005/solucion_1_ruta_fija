import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_incident_models.dart';

typedef MobileIncidentUtcClock = DateTime Function();

abstract interface class MobileIncidentGateway {
  Future<MobileIncidentPage> list({int page = 0, int size = 20});

  Future<MobileIncident> get(String incidentId);

  Future<MobileIncident> create({
    required MobileIncidentCategory category,
    required String description,
    String? assignmentId,
  });
}

final class MobileIncidentRepository implements MobileIncidentGateway {
  MobileIncidentRepository(this._apiClient, {this.utcClock = _nowUtc});

  final MobileApiClient _apiClient;
  final MobileIncidentUtcClock utcClock;

  @override
  Future<MobileIncidentPage> list({int page = 0, int size = 20}) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('La página o el tamaño solicitado no son válidos.');
    }
    final response = await _apiClient.getObject(
      'mobile/incidents',
      queryParameters: {
        'page': '$page',
        'size': '$size',
        'sort': 'reportedAt,desc',
      },
    );
    return MobileIncidentPage.fromJson(response);
  }

  @override
  Future<MobileIncident> get(String incidentId) async {
    final normalizedId = _requiredIdentifier(incidentId, 'incidentId');
    final response = await _apiClient.getObject(
      'mobile/incidents/$normalizedId',
    );
    return MobileIncident.fromJson(response);
  }

  @override
  Future<MobileIncident> create({
    required MobileIncidentCategory category,
    required String description,
    String? assignmentId,
  }) async {
    final normalizedDescription = description.trim();
    if (normalizedDescription.isEmpty || normalizedDescription.length > 1000) {
      throw ArgumentError.value(
        description,
        'description',
        'La descripción debe contener entre 1 y 1000 caracteres.',
      );
    }
    final normalizedAssignmentId = assignmentId == null
        ? null
        : _requiredIdentifier(assignmentId, 'assignmentId');
    final body = <String, Object?>{
      'category': category.apiValue,
      'description': normalizedDescription,
      'occurredAt': utcClock().toUtc().toIso8601String(),
    };
    if (normalizedAssignmentId != null) {
      body['assignmentId'] = normalizedAssignmentId;
    }
    final response = await _apiClient.postObject('mobile/incidents', body);
    return MobileIncident.fromJson(response);
  }

  String _requiredIdentifier(String value, String field) {
    final normalized = value.trim();
    if (normalized.isEmpty) {
      throw ArgumentError.value(
        value,
        field,
        'El identificador es obligatorio.',
      );
    }
    return normalized;
  }
}

DateTime _nowUtc() => DateTime.now().toUtc();
