import '../../../core/network/mobile_api_client.dart';

abstract interface class MobileServiceTimeGateway {
  Future<MobileServiceTimeHistory> timeHistory(String assignmentId);
  Future<MobileTimeRequest> requestTimeCorrection(
    String assignmentId,
    String requestKey,
    String reason,
  );
}

final class MobileServiceTimeRepository implements MobileServiceTimeGateway {
  MobileServiceTimeRepository(this.api);
  final MobileApiClient api;
  @override
  Future<MobileServiceTimeHistory> timeHistory(
    String assignmentId,
  ) async => MobileServiceTimeHistory.fromJson(
    await api.getObject(
      'mobile/assignments/${Uri.encodeComponent(assignmentId)}/time-history',
    ),
  );
  @override
  Future<MobileTimeRequest> requestTimeCorrection(
    String assignmentId,
    String requestKey,
    String reason,
  ) async => MobileTimeRequest.fromJson(
    await api.postObject(
      'mobile/assignments/${Uri.encodeComponent(assignmentId)}/time-correction-requests',
      {'requestKey': requestKey, 'reason': reason},
      allowSessionRefresh: false,
    ),
  );
}

final class MobileServiceTimeHistory {
  const MobileServiceTimeHistory({
    required this.status,
    required this.segments,
    required this.corrections,
    required this.requests,
  });
  final String status;
  final List<MobileSegmentTime> segments;
  final List<MobileTimeRevision> corrections;
  final List<MobileTimeRequest> requests;
  factory MobileServiceTimeHistory.fromJson(Map<String, dynamic> j) =>
      MobileServiceTimeHistory(
        status: j['status'] as String,
        segments: (j['segments'] as List)
            .map((x) => MobileSegmentTime.fromJson(x as Map<String, dynamic>))
            .toList(),
        corrections: (j['corrections'] as List)
            .map((x) => MobileTimeRevision.fromJson(x as Map<String, dynamic>))
            .toList(),
        requests: (j['requests'] as List)
            .map((x) => MobileTimeRequest.fromJson(x as Map<String, dynamic>))
            .toList(),
      );
}

final class MobileSegmentTime {
  const MobileSegmentTime({
    required this.ordinal,
    required this.originalStart,
    required this.originalEnd,
    required this.start,
    required this.end,
  });
  final int ordinal;
  final DateTime originalStart, start;
  final DateTime? originalEnd, end;
  factory MobileSegmentTime.fromJson(Map<String, dynamic> j) =>
      MobileSegmentTime(
        ordinal: j['ordinal'] as int,
        originalStart: DateTime.parse(j['originalStartedAt'] as String),
        originalEnd: _optionalDate(j['originalEndedAt']),
        start: DateTime.parse(j['startedAt'] as String),
        end: _optionalDate(j['endedAt']),
      );
}

final class MobileTimeRevision {
  const MobileTimeRevision({
    required this.revision,
    required this.at,
    required this.reason,
    required this.originalResult,
    required this.effectiveResult,
  });
  final int revision;
  final DateTime at;
  final String reason, effectiveResult;
  final String? originalResult;
  factory MobileTimeRevision.fromJson(Map<String, dynamic> j) =>
      MobileTimeRevision(
        revision: j['revision'] as int,
        at: DateTime.parse(j['correctedAt'] as String),
        reason: j['reason'] as String,
        originalResult: j['originalTerminalResult'] as String?,
        effectiveResult: j['effectiveTerminalResult'] as String,
      );
}

final class MobileTimeRequest {
  const MobileTimeRequest({
    required this.id,
    required this.key,
    required this.reason,
    required this.at,
    this.correctionId,
  });
  final String id, key, reason;
  final DateTime at;
  final String? correctionId;
  factory MobileTimeRequest.fromJson(Map<String, dynamic> j) =>
      MobileTimeRequest(
        id: j['id'] as String,
        key: j['requestKey'] as String,
        reason: j['reason'] as String,
        at: DateTime.parse(j['requestedAt'] as String),
        correctionId: j['correctionId'] as String?,
      );
}

DateTime? _optionalDate(Object? value) =>
    value == null ? null : DateTime.parse(value as String);
