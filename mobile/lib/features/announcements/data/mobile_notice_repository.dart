import '../../../core/network/mobile_api_client.dart';

class MobileNotice {
  const MobileNotice({
    required this.id,
    required this.eventType,
    required this.references,
    required this.createdAt,
    required this.readAt,
    required this.externalStatus,
  });
  final String id, eventType, externalStatus;
  final Map<String, String> references;
  final DateTime createdAt;
  final DateTime? readAt;
  factory MobileNotice.fromJson(Map<String, dynamic> j) => MobileNotice(
    id: j['id'] as String,
    eventType: j['eventType'] as String,
    references: Map<String, String>.from(j['references'] as Map),
    createdAt: DateTime.parse(j['createdAt'] as String),
    readAt: j['readAt'] == null ? null : DateTime.parse(j['readAt'] as String),
    externalStatus: j['externalStatus'] as String,
  );
}

class MobileNoticePage {
  const MobileNoticePage(this.items, this.page, this.totalPages);
  final List<MobileNotice> items;
  final int page, totalPages;
  factory MobileNoticePage.fromJson(Map<String, dynamic> j) => MobileNoticePage(
    (j['items'] as List)
        .map((r) => MobileNotice.fromJson(Map<String, dynamic>.from(r as Map)))
        .toList(growable: false),
    j['page'] as int,
    j['totalPages'] as int,
  );
}

abstract interface class MobileNoticeGateway {
  Future<MobileNoticePage> list({int page = 0});
  Future<MobileNotice> read(String id);
}

class MobileNoticeRepository implements MobileNoticeGateway {
  MobileNoticeRepository(this.api);
  final MobileApiClient api;
  @override
  Future<MobileNoticePage> list({int page = 0}) async {
    if (page < 0) throw ArgumentError('Página inválida');
    return MobileNoticePage.fromJson(
      await api.getObject(
        'mobile/notices',
        queryParameters: {'page': '$page', 'size': '20'},
      ),
    );
  }

  @override
  Future<MobileNotice> read(String id) async {
    if (id.trim().isEmpty) throw ArgumentError('Referencia de aviso requerida');
    return MobileNotice.fromJson(
      await api.postObject(
        'mobile/notices/${Uri.encodeComponent(id)}/read',
        const {},
        allowSessionRefresh: false,
      ),
    );
  }
}
