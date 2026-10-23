import '../../../core/network/mobile_api_client.dart';

abstract interface class MobileServiceGpsGateway {
  Future<List<MobileGpsRequest>> gpsRequests(
    String serviceId, {
    int offset = 0,
  });
  Future<MobileGpsRequest> requestGps(String serviceId, String requestKey);
  Future<MobileGpsDelivery> deliverGps(
    String requestId,
    String password, {
    int offset = 0,
  });
}

final class MobileServiceGpsRepository implements MobileServiceGpsGateway {
  MobileServiceGpsRepository(this.api);
  final MobileApiClient api;
  @override
  Future<List<MobileGpsRequest>> gpsRequests(
    String serviceId, {
    int offset = 0,
  }) async => (await api.getList(
    'mobile/gps/access-requests',
    queryParameters: {
      'serviceId': serviceId,
      'limit': '50',
      'offset': '$offset',
    },
  )).map(MobileGpsRequest.fromJson).toList();
  @override
  Future<MobileGpsRequest> requestGps(
    String serviceId,
    String requestKey,
  ) async => MobileGpsRequest.fromJson(
    await api.postObject('mobile/gps/access-requests', {
      'serviceId': serviceId,
      'requestKey': requestKey,
    }, allowSessionRefresh: false),
  );
  @override
  Future<MobileGpsDelivery> deliverGps(
    String requestId,
    String password, {
    int offset = 0,
  }) async => MobileGpsDelivery.fromJson(
    await api.postObject(
      'mobile/gps/access-requests/${Uri.encodeComponent(requestId)}/points',
      {'currentPassword': password, 'limit': 1000, 'offset': offset},
      allowSessionRefresh: false,
    ),
  );
}

final class MobileGpsRequest {
  const MobileGpsRequest({
    required this.id,
    required this.requestKey,
    required this.status,
    required this.fromAt,
    required this.toAt,
    required this.reviewDueAt,
    this.grantExpiresAt,
  });
  final String id, requestKey, status;
  final DateTime fromAt, toAt, reviewDueAt;
  final DateTime? grantExpiresAt;
  factory MobileGpsRequest.fromJson(Map<String, dynamic> j) => MobileGpsRequest(
    id: j['id'] as String,
    requestKey: j['requestKey'] as String,
    status: (j['effectiveStatus'] ?? j['status']) as String,
    fromAt: DateTime.parse(j['fromAt'] as String),
    toAt: DateTime.parse(j['toAt'] as String),
    reviewDueAt: DateTime.parse(j['reviewDueAt'] as String),
    grantExpiresAt: j['grantExpiresAt'] == null
        ? null
        : DateTime.parse(j['grantExpiresAt'] as String),
  );
}

final class MobileGpsDelivery {
  const MobileGpsDelivery({
    required this.expiresAt,
    required this.items,
    this.nextOffset,
  });
  final DateTime expiresAt;
  final List<MobileGpsPoint> items;
  final int? nextOffset;
  factory MobileGpsDelivery.fromJson(Map<String, dynamic> j) =>
      MobileGpsDelivery(
        expiresAt: DateTime.parse(j['grantExpiresAt'] as String),
        items: (j['items'] as List)
            .map(
              (r) =>
                  MobileGpsPoint.fromJson(Map<String, dynamic>.from(r as Map)),
            )
            .toList(),
        nextOffset: j['nextOffset'] as int?,
      );
}

final class MobileGpsPoint {
  const MobileGpsPoint(
    this.latitude,
    this.longitude,
    this.accuracy,
    this.capturedAt,
  );
  final double latitude, longitude, accuracy;
  final DateTime capturedAt;
  factory MobileGpsPoint.fromJson(Map<String, dynamic> j) => MobileGpsPoint(
    (j['latitude'] as num).toDouble(),
    (j['longitude'] as num).toDouble(),
    (j['accuracyM'] as num).toDouble(),
    DateTime.parse(j['capturedAt'] as String),
  );
  @override
  String toString() => 'MobileGpsPoint[coordinates redacted]';
}
