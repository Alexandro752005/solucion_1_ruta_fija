import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_announcement_models.dart';

abstract interface class MobileAnnouncementGateway {
  Future<MobileAnnouncementPage> list({int page = 0, int size = 20});

  Future<MobileAnnouncement> get(String announcementId);

  Future<MobileAnnouncementReadResult> markRead(String announcementId);
}

final class MobileAnnouncementRepository implements MobileAnnouncementGateway {
  MobileAnnouncementRepository(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileAnnouncementPage> list({int page = 0, int size = 20}) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('La página o el tamaño solicitado no son válidos.');
    }
    final response = await _apiClient.getObject(
      'mobile/announcements',
      queryParameters: {
        'page': '$page',
        'size': '$size',
        'sort': 'createdAt,desc',
      },
    );
    return MobileAnnouncementPage.fromJson(response);
  }

  @override
  Future<MobileAnnouncement> get(String announcementId) async {
    final normalizedId = _requiredIdentifier(announcementId);
    final response = await _apiClient.getObject(
      'mobile/announcements/$normalizedId',
    );
    return MobileAnnouncement.fromJson(response);
  }

  @override
  Future<MobileAnnouncementReadResult> markRead(String announcementId) async {
    final normalizedId = _requiredIdentifier(announcementId);
    final response = await _apiClient.postObject(
      'mobile/announcements/$normalizedId/read',
      const {},
    );
    return MobileAnnouncementReadResult.fromJson(response);
  }

  String _requiredIdentifier(String value) {
    final normalized = value.trim();
    if (normalized.isEmpty) {
      throw ArgumentError.value(
        value,
        'announcementId',
        'El identificador es obligatorio.',
      );
    }
    return normalized;
  }
}
