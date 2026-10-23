final class MobileRouteStop {
  const MobileRouteStop({
    required this.key,
    required this.name,
    required this.kind,
  });
  final String key, name, kind;
  factory MobileRouteStop.fromJson(Map<String, dynamic> json) =>
      MobileRouteStop(
        key: json['key'] as String,
        name: json['name'] as String,
        kind: json['kind'] as String,
      );
}

final class MobileAssignedRoute {
  const MobileAssignedRoute({
    required this.assignmentId,
    required this.referenceStatus,
    this.versionId,
    this.name,
    this.revision,
    this.stops = const [],
    this.declaredAt,
    this.terminalResult,
    this.replacementAssignmentId,
  });
  final String assignmentId, referenceStatus;
  final String? versionId, name, terminalResult, replacementAssignmentId;
  final int? revision;
  final DateTime? declaredAt;
  final List<MobileRouteStop> stops;
  factory MobileAssignedRoute.fromJson(Map<String, dynamic> json) {
    final route = json['route'] as Map<String, dynamic>?;
    final declaration = json['declaration'] as Map<String, dynamic>?;
    return MobileAssignedRoute(
      assignmentId: json['assignmentId'] as String,
      referenceStatus: json['referenceStatus'] as String,
      versionId: json['versionId'] as String?,
      name: route?['name'] as String?,
      revision: route?['revision'] as int?,
      stops: (route?['stops'] as List<dynamic>? ?? const [])
          .map(
            (s) =>
                MobileRouteStop.fromJson(Map<String, dynamic>.from(s as Map)),
          )
          .toList(growable: false),
      declaredAt: declaration?['declaredAt'] is String
          ? DateTime.parse(declaration!['declaredAt'] as String)
          : null,
      terminalResult: declaration?['terminalResult'] as String?,
      replacementAssignmentId: json['replacementAssignmentId'] as String?,
    );
  }
}

abstract interface class MobileRouteGateway {
  Future<MobileAssignedRoute> assignedRoute(String assignmentId);
}
