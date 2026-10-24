final class MobileAnnouncement {
  const MobileAnnouncement({
    required this.id,
    required this.title,
    required this.body,
    required this.requireReadAck,
    required this.createdAt,
    required this.deliveredAt,
    required this.readAt,
  });

  final String id;
  final String title;
  final String body;
  final bool requireReadAck;
  final DateTime createdAt;
  final DateTime? deliveredAt;
  final DateTime? readAt;

  bool get requiresReadConfirmation => requireReadAck && readAt == null;

  MobileAnnouncement withReadAt(DateTime value) {
    return MobileAnnouncement(
      id: id,
      title: title,
      body: body,
      requireReadAck: requireReadAck,
      createdAt: createdAt,
      deliveredAt: deliveredAt,
      readAt: value,
    );
  }

  factory MobileAnnouncement.fromJson(Map<String, dynamic> json) {
    return MobileAnnouncement(
      id: _requiredText(json['id'], 'id'),
      title: _requiredText(json['title'], 'title'),
      body: _requiredText(json['body'], 'body'),
      requireReadAck: _requiredBool(json['requireReadAck'], 'requireReadAck'),
      createdAt: _requiredDate(json['createdAt'], 'createdAt'),
      deliveredAt: _optionalDate(json['deliveredAt']),
      readAt: _optionalDate(json['readAt']),
    );
  }
}

final class MobileAnnouncementPage {
  const MobileAnnouncementPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalItems,
    required this.totalPages,
  });

  final List<MobileAnnouncement> items;
  final int page;
  final int size;
  final int totalItems;
  final int totalPages;

  factory MobileAnnouncementPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('La lista de comunicados no es válida.');
    }
    return MobileAnnouncementPage(
      items: rawItems
          .map((item) {
            if (item is! Map) {
              throw const FormatException('Un comunicado no es válido.');
            }
            return MobileAnnouncement.fromJson(Map<String, dynamic>.from(item));
          })
          .toList(growable: false),
      page: _requiredInt(json['page'], 'page'),
      size: _requiredInt(json['size'], 'size'),
      totalItems: _requiredInt(json['totalItems'], 'totalItems'),
      totalPages: _requiredInt(json['totalPages'], 'totalPages'),
    );
  }
}

final class MobileAnnouncementReadResult {
  const MobileAnnouncementReadResult({
    required this.announcementId,
    required this.deliveredAt,
    required this.readAt,
  });

  final String announcementId;
  final DateTime? deliveredAt;
  final DateTime readAt;

  factory MobileAnnouncementReadResult.fromJson(Map<String, dynamic> json) {
    return MobileAnnouncementReadResult(
      announcementId: _requiredText(json['announcementId'], 'announcementId'),
      deliveredAt: _optionalDate(json['deliveredAt']),
      readAt: _requiredDate(json['readAt'], 'readAt'),
    );
  }
}

String _requiredText(Object? value, String field) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('$field es obligatorio.');
}

bool _requiredBool(Object? value, String field) {
  if (value is bool) {
    return value;
  }
  throw FormatException('$field debe ser booleano.');
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
