import '../../../core/network/mobile_api_client.dart';
import '../../../core/network/correlation_id.dart';
import '../domain/mobile_incident_models.dart';

typedef MobileIncidentUtcClock = DateTime Function();

abstract interface class MobileIncidentGateway {
  Future<MobileIncidentPage> list({int page = 0, int size = 20});

  Future<MobileIncident> get(String incidentId);

  Future<MobileIncident> close(String incidentId, {required int version});

  Future<MobileIncident> create({
    required MobileIncidentCategory category,
    required String description,
    required MobileIncidentSeverity severity,
    String? assignmentId,
    String? operationKey,
    DateTime? occurredAt,
    String? previousIncidentId,
    String? relationReason,
  });
  Future<List<MobileIncidentAction>> history(String incidentId);
  Future<MobileIncidentOperation> operation(String operationKey);
  Future<MobileIncident> information(
    String incidentId, {
    required int version,
    required String operationKey,
    required String note,
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
  Future<MobileIncident> close(
    String incidentId, {
    required int version,
  }) async {
    final normalizedId = _requiredIdentifier(incidentId, 'incidentId');
    if (version < 0) {
      throw ArgumentError.value(version, 'version', 'La versión no es válida.');
    }
    final response = await _apiClient.postObject(
      'mobile/incidents/$normalizedId/close',
      {'version': version},
      allowSessionRefresh: false,
    );
    return MobileIncident.fromJson(response);
  }

  @override
  Future<MobileIncident> create({
    required MobileIncidentCategory category,
    required String description,
    required MobileIncidentSeverity severity,
    String? assignmentId,
    String? operationKey,
    DateTime? occurredAt,
    String? previousIncidentId,
    String? relationReason,
  }) async {
    if (severity == MobileIncidentSeverity.unassessed) {
      throw ArgumentError('Selecciona una gravedad explícita.');
    }
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
      'occurredAt': (occurredAt ?? utcClock()).toUtc().toIso8601String(),
      'severity': severity.apiValue,
      'operationKey': operationKey ?? createCorrelationId(),
    };
    if (normalizedAssignmentId != null) {
      body['assignmentId'] = normalizedAssignmentId;
    }
    if (previousIncidentId != null) {
      if (!['RECURRENCE', 'CLOSURE_ERROR'].contains(relationReason)) {
        throw ArgumentError('Selecciona el motivo del caso posterior.');
      }
      body['previousIncidentId'] = previousIncidentId;
      body['relationReason'] = relationReason;
    }
    final response = await _apiClient.postObject(
      'mobile/incidents',
      body,
      allowSessionRefresh: false,
    );
    return MobileIncident.fromJson(response);
  }

  @override
  Future<List<MobileIncidentAction>> history(
    String incidentId,
  ) async => (await _apiClient.getList(
    'mobile/incidents/${_requiredIdentifier(incidentId, 'incidentId')}/history',
  )).map(MobileIncidentAction.fromJson).toList();
  @override
  Future<MobileIncidentOperation> operation(
    String operationKey,
  ) async => MobileIncidentOperation.fromJson(
    await _apiClient.getObject(
      'mobile/incidents/operations/${_requiredIdentifier(operationKey, 'operationKey')}',
    ),
  );
  @override
  Future<MobileIncident> information(
    String incidentId, {
    required int version,
    required String operationKey,
    required String note,
  }) async {
    if (version < 0 || note.trim().isEmpty || note.trim().length > 1000) {
      throw ArgumentError('Completa una nota válida.');
    }
    return MobileIncident.fromJson(
      await _apiClient.postObject(
        'mobile/incidents/${_requiredIdentifier(incidentId, 'incidentId')}/information',
        {
          'operationKey': operationKey,
          'expectedVersion': version,
          'note': note.trim(),
        },
        allowSessionRefresh: false,
      ),
    );
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
