import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_assignment_models.dart';

abstract interface class MobileServiceHandoverGateway {
  Future<MobileHandoverView> handoverView(String assignmentId);
  Future<MobileAssignment> assumeHandover(
    MobileAssignment assignment, {
    required String eventId,
    required bool readyConfirmed,
  });
}

final class MobileHandoverPreconditionException implements Exception {
  const MobileHandoverPreconditionException();
}

final class MobileServiceHandoverRepository
    implements MobileServiceHandoverGateway {
  MobileServiceHandoverRepository(
    this.api, {
    this.gpsProof,
    DateTime Function()? now,
  }) : now = now ?? _utcNow;
  final MobileApiClient api;
  final Future<Map<String, Object>> Function()? gpsProof;
  final DateTime Function() now;
  static DateTime _utcNow() => DateTime.now().toUtc();
  @override
  Future<MobileHandoverView> handoverView(String assignmentId) async =>
      MobileHandoverView.fromJson(
        await api.getObject(
          'mobile/assignments/${Uri.encodeComponent(assignmentId)}/handovers',
        ),
      );
  @override
  Future<MobileAssignment> assumeHandover(
    MobileAssignment assignment, {
    required String eventId,
    required bool readyConfirmed,
  }) async {
    if (!readyConfirmed ||
        assignment.handoverId == null ||
        assignment.status != MobileAssignmentStatus.scheduled ||
        assignment.acceptedAt == null) {
      throw const MobileHandoverPreconditionException();
    }
    Map<String, Object> proof;
    try {
      final provider = gpsProof;
      if (provider == null) throw const MobileHandoverPreconditionException();
      proof = await provider();
      if ([
        'permissionGranted',
        'locationServiceEnabled',
        'foreground',
      ].any((k) => proof[k] != true)) {
        throw const MobileHandoverPreconditionException();
      }
    } catch (_) {
      throw const MobileHandoverPreconditionException();
    }
    final result = MobileAssignment.fromJson(
      await api.postObject(
        'mobile/assignments/${Uri.encodeComponent(assignment.id)}/assume-handover',
        {
          'clientEventId': eventId,
          'version': assignment.version,
          'occurredAt': now().toUtc().toIso8601String(),
          'gps': proof,
          'readyConfirmed': true,
        },
        allowSessionRefresh: false,
      ),
    );
    if (result.id != assignment.id ||
        result.driverId != assignment.driverId ||
        result.status != MobileAssignmentStatus.enServicio ||
        result.handoverId != assignment.handoverId) {
      throw const FormatException(
        'Relevo sin estado contrastable. Consulte el mismo caso.',
      );
    }
    return result;
  }
}

final class MobileHandoverView {
  const MobileHandoverView({
    required this.status,
    required this.originalStartedAt,
    required this.startOverdue,
    required this.rows,
  });
  final String status;
  final DateTime? originalStartedAt;
  final bool startOverdue;
  final List<MobileHandover> rows;
  factory MobileHandoverView.fromJson(Map<String, dynamic> j) {
    final s = j['service'] as Map<String, dynamic>;
    return MobileHandoverView(
      status: s['status'] as String,
      originalStartedAt: s['originalStartedAt'] == null
          ? null
          : DateTime.parse(s['originalStartedAt'] as String),
      startOverdue: s['startOverdue'] == true,
      rows: (j['handovers'] as List)
          .map((r) => MobileHandover.fromJson(r as Map<String, dynamic>))
          .toList(),
    );
  }
}

final class MobileHandover {
  const MobileHandover({
    required this.id,
    required this.incomingAssignmentId,
    required this.outgoingAssignmentId,
    required this.state,
    required this.reason,
    required this.pointReference,
    this.resolutionKey,
    this.resolvedAt,
    this.pauseSeconds,
  });
  final String id,
      incomingAssignmentId,
      outgoingAssignmentId,
      state,
      reason,
      pointReference;
  final String? resolutionKey;
  final DateTime? resolvedAt;
  final int? pauseSeconds;
  factory MobileHandover.fromJson(Map<String, dynamic> j) => MobileHandover(
    id: j['id'] as String,
    incomingAssignmentId: j['incomingAssignmentId'] as String,
    outgoingAssignmentId: j['outgoingAssignmentId'] as String,
    state: j['state'] as String,
    reason: j['reason'] as String,
    pointReference: j['pointReference'] as String,
    resolutionKey: j['resolutionKey'] as String?,
    resolvedAt: j['resolvedAt'] == null
        ? null
        : DateTime.parse(j['resolvedAt'] as String),
    pauseSeconds: j['pauseSeconds'] as int?,
  );
}
