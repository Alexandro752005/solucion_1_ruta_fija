import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_assignment_models.dart';

abstract interface class MobileServiceAttentionGateway {
  Future<List<MobileSafetyAttention>> ownAttention(String assignmentId);
  Future<MobileAssignment> interruptService(
    MobileAssignment assignment, {
    required String eventId,
    required String reason,
    required String detail,
    required bool safeStopConfirmed,
  });
}

final class MobileServiceAttentionRepository
    implements MobileServiceAttentionGateway {
  MobileServiceAttentionRepository(this.api, {DateTime Function()? now})
    : now = now ?? _utcNow;
  final MobileApiClient api;
  final DateTime Function() now;
  static DateTime _utcNow() => DateTime.now().toUtc();
  @override
  Future<List<MobileSafetyAttention>> ownAttention(String assignmentId) async =>
      (await api.getList(
        'mobile/assignments/${Uri.encodeComponent(assignmentId)}/attention',
      )).map(MobileSafetyAttention.fromJson).toList();
  @override
  Future<MobileAssignment> interruptService(
    MobileAssignment assignment, {
    required String eventId,
    required String reason,
    required String detail,
    required bool safeStopConfirmed,
  }) async {
    final result = MobileAssignment.fromJson(
      await api.postObject(
        'mobile/assignments/${Uri.encodeComponent(assignment.id)}/interrupt',
        {
          'clientEventId': eventId,
          'version': assignment.version,
          'occurredAt': now().toUtc().toIso8601String(),
          'reason': reason,
          'detail': detail,
          'safeStopConfirmed': safeStopConfirmed,
        },
        allowSessionRefresh: false,
      ),
    );
    if (result.id != assignment.id ||
        result.driverId != assignment.driverId ||
        result.status != MobileAssignmentStatus.interrupted) {
      throw const FormatException(
        'No se pudo contrastar la interrupción confirmada. Consulte su estado.',
      );
    }
    return result;
  }
}

final class MobileSafetyAttention {
  const MobileSafetyAttention({
    required this.id,
    required this.operationKey,
    required this.assignmentId,
    required this.segmentId,
    required this.incidentId,
    required this.reason,
    required this.detail,
    required this.at,
    required this.source,
    required this.safeStopConfirmed,
    required this.driverPending,
    required this.vehiclePending,
  });
  final String id,
      operationKey,
      assignmentId,
      segmentId,
      incidentId,
      reason,
      detail,
      source;
  final DateTime at;
  final bool safeStopConfirmed, driverPending, vehiclePending;
  factory MobileSafetyAttention.fromJson(Map<String, dynamic> j) =>
      MobileSafetyAttention(
        id: j['id'] as String,
        operationKey: j['operationKey'] as String,
        assignmentId: j['assignmentId'] as String,
        segmentId: j['segmentId'] as String,
        incidentId: j['incidentId'] as String,
        reason: j['reason'] as String,
        detail: j['detail'] as String,
        at: DateTime.parse(j['interruptedAt'] as String),
        source: j['source'] as String,
        safeStopConfirmed: j['safeStopConfirmed'] as bool,
        driverPending: j['driverPending'] as bool,
        vehiclePending: j['vehiclePending'] as bool,
      );
}
