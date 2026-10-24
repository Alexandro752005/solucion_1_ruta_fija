import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'mobile_offline_event.dart';

abstract interface class MobileOfflineQueueStore {
  Future<List<MobileOfflineEvent>> read(MobileOfflineQueueOwner owner);

  Future<void> replace(
    MobileOfflineQueueOwner owner,
    List<MobileOfflineEvent> events,
  );

  Future<void> clear(MobileOfflineQueueOwner owner);

  /// Removes every M8 envelope from this protected namespace. This is used
  /// only when a stored session cannot be attributed to an authenticated owner.
  Future<void> clearAll();
}

/// Encrypted, owner-scoped persistence for the intentionally small M8 outbox.
///
/// The queue is kept as a single JSON envelope so its insertion order remains
/// the delivery order after the application is closed or the phone restarts.
final class MobileSecureOfflineQueueStore implements MobileOfflineQueueStore {
  MobileSecureOfflineQueueStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              storageNamespace: 'pe.rutafija.conductor.offline',
            ),
          );

  static const int maximumEvents = 10;
  static const int _schemaVersion = 1;
  static const String _keyPrefix = 'mobile_offline_outbox_v1:';

  final FlutterSecureStorage _storage;

  @override
  Future<List<MobileOfflineEvent>> read(MobileOfflineQueueOwner owner) async {
    final key = _keyFor(owner);
    final raw = await _storage.read(key: key);
    if (raw == null || raw.isEmpty) {
      return const [];
    }

    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) {
        throw const FormatException('La cola offline no es un objeto JSON.');
      }
      final envelope = Map<String, dynamic>.from(decoded);
      if (envelope['schemaVersion'] != _schemaVersion) {
        throw const FormatException(
          'La versión de la cola offline no es válida.',
        );
      }

      final rawOwner = envelope['owner'];
      if (rawOwner is! Map) {
        throw const FormatException('Falta el propietario de la cola offline.');
      }
      final storedOwner = MobileOfflineQueueOwner.fromStorageJson(
        Map<String, dynamic>.from(rawOwner),
      );
      if (!storedOwner.sameAs(owner)) {
        throw const FormatException(
          'La cola offline no pertenece a la sesión.',
        );
      }

      final rawEvents = envelope['events'];
      if (rawEvents is! List || rawEvents.length > maximumEvents) {
        throw const FormatException(
          'La cola offline tiene una cantidad no válida.',
        );
      }
      final eventIds = <String>{};
      final events = <MobileOfflineEvent>[];
      for (final rawEvent in rawEvents) {
        if (rawEvent is! Map) {
          throw const FormatException(
            'Un evento offline no es un objeto JSON.',
          );
        }
        final event = MobileOfflineEvent.fromStorageJson(
          Map<String, dynamic>.from(rawEvent),
        );
        if (!event.owner.sameAs(owner) || !eventIds.add(event.eventId)) {
          throw const FormatException(
            'La cola offline contiene un evento inválido.',
          );
        }
        events.add(event);
      }
      return List<MobileOfflineEvent>.unmodifiable(events);
    } on FormatException {
      // A corrupt or cross-account envelope must not be reused as a command.
      await _storage.delete(key: key);
      return const [];
    }
  }

  @override
  Future<void> replace(
    MobileOfflineQueueOwner owner,
    List<MobileOfflineEvent> events,
  ) async {
    if (events.length > maximumEvents) {
      throw StateError(
        'La cola offline admite como máximo $maximumEvents eventos.',
      );
    }
    final eventIds = <String>{};
    for (final event in events) {
      if (!event.owner.sameAs(owner) || !eventIds.add(event.eventId)) {
        throw StateError(
          'La cola offline no contiene eventos válidos para la sesión.',
        );
      }
    }

    if (events.isEmpty) {
      await clear(owner);
      return;
    }

    await _storage.write(
      key: _keyFor(owner),
      value: jsonEncode({
        'schemaVersion': _schemaVersion,
        'owner': owner.toStorageJson(),
        'events': events.map((event) => event.toStorageJson()).toList(),
      }),
    );
  }

  @override
  Future<void> clear(MobileOfflineQueueOwner owner) {
    return _storage.delete(key: _keyFor(owner));
  }

  @override
  Future<void> clearAll() async {
    final values = await _storage.readAll();
    for (final key in values.keys) {
      if (key.startsWith(_keyPrefix)) {
        await _storage.delete(key: key);
      }
    }
  }

  String _keyFor(MobileOfflineQueueOwner owner) =>
      '$_keyPrefix${owner.storageScope}';
}
