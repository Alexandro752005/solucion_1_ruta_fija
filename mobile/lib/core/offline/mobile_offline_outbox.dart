import 'dart:async';

import 'mobile_offline_event.dart';
import 'mobile_offline_queue_store.dart';

/// FIFO coordinator for the deliberately bounded, foreground-only M8 outbox.
///
/// It does not observe connectivity, schedule background work, or submit any
/// request itself. A presentation/application service asks for [nextReady],
/// sends that exact body, and reports the result through one of the mutation
/// methods below. This separation makes retries explicit and auditable.
final class MobileOfflineOutbox {
  MobileOfflineOutbox({
    required this.store,
    DateTime Function()? clock,
    this.maximumEvents = MobileSecureOfflineQueueStore.maximumEvents,
    this.initialRetryDelay = const Duration(seconds: 5),
    this.maximumRetryDelay = const Duration(minutes: 5),
  }) : _clock = clock ?? DateTime.now {
    if (maximumEvents < 1 ||
        maximumEvents > MobileSecureOfflineQueueStore.maximumEvents) {
      throw ArgumentError.value(
        maximumEvents,
        'maximumEvents',
        'Debe estar entre 1 y ${MobileSecureOfflineQueueStore.maximumEvents}.',
      );
    }
    if (initialRetryDelay <= Duration.zero ||
        maximumRetryDelay < initialRetryDelay) {
      throw ArgumentError(
        'Los intervalos de reintento offline no son válidos.',
      );
    }
  }

  final MobileOfflineQueueStore store;
  final DateTime Function() _clock;
  final int maximumEvents;
  final Duration initialRetryDelay;
  final Duration maximumRetryDelay;
  final Map<String, Future<void>> _ownerTails = {};

  Future<List<MobileOfflineEvent>> list(MobileOfflineQueueOwner owner) {
    return _serialized(owner, () async => store.read(owner));
  }

  Future<int> count(MobileOfflineQueueOwner owner) async =>
      (await list(owner)).length;

  Future<MobileOfflineEvent> enqueue(MobileOfflineEvent event) {
    return _serialized(event.owner, () async {
      final events = (await store.read(event.owner)).toList(growable: true);
      if (events.isNotEmpty && events.first.isBlocked) {
        throw StateError(
          'La primera accion offline esta bloqueada y debe conciliarse antes de crear otra.',
        );
      }
      if (events.length >= maximumEvents) {
        throw StateError(
          'La cola offline llegó a su límite de $maximumEvents acciones.',
        );
      }
      if (events.any((candidate) => candidate.eventId == event.eventId)) {
        throw StateError('El evento offline ya existe en la cola.');
      }
      events.add(event);
      await store.replace(event.owner, events);
      return event;
    });
  }

  /// Returns only the head of the FIFO when it is pending and its bounded
  /// backoff has elapsed. A blocked head always stops later events.
  Future<MobileOfflineEvent?> nextReady(
    MobileOfflineQueueOwner owner, {
    DateTime? at,
    bool ignoreBackoff = false,
  }) {
    return _serialized(owner, () async {
      final events = await store.read(owner);
      if (events.isEmpty) {
        return null;
      }
      final head = events.first;
      if (head.isBlocked ||
          (!ignoreBackoff && !head.isEligibleAt(at ?? _clock()))) {
        return null;
      }
      return head;
    });
  }

  /// Returns the FIFO head even when it is blocked or waiting for backoff.
  Future<MobileOfflineEvent?> head(MobileOfflineQueueOwner owner) {
    return _serialized(owner, () async {
      final events = await store.read(owner);
      return events.isEmpty ? null : events.first;
    });
  }

  /// Removes exactly the delivered FIFO head. Later events cannot be removed
  /// by a response for an older request.
  Future<void> markDelivered(MobileOfflineQueueOwner owner, String eventId) {
    return _serialized(owner, () async {
      final events = (await store.read(owner)).toList(growable: true);
      _requirePendingHead(events, eventId);
      events.removeAt(0);
      await store.replace(owner, events);
    });
  }

  /// Preserves the original event and applies a bounded exponential backoff.
  Future<MobileOfflineEvent> markTemporaryFailure(
    MobileOfflineQueueOwner owner,
    String eventId, {
    DateTime? at,
  }) {
    return _serialized(owner, () async {
      final events = (await store.read(owner)).toList(growable: true);
      final head = _requirePendingHead(events, eventId);
      final nextAttempts = head.attempts + 1;
      final retryAt = (at ?? _clock()).toUtc().add(
        retryDelayForAttempt(nextAttempts),
      );
      final updated = head.afterTemporaryFailure(
        attempts: nextAttempts,
        nextRetryAt: retryAt,
      );
      events[0] = updated;
      await store.replace(owner, events);
      return updated;
    });
  }

  /// Marks the FIFO head as requiring human reconciliation. The event remains
  /// visible and durable, but it cannot be sent again or bypassed.
  Future<MobileOfflineEvent> markBlocked(
    MobileOfflineQueueOwner owner,
    String eventId, {
    String? code,
    String? message,
  }) {
    return _serialized(owner, () async {
      final events = (await store.read(owner)).toList(growable: true);
      final head = _requirePendingHead(events, eventId);
      final updated = head.asBlocked(code: code, message: message);
      events[0] = updated;
      await store.replace(owner, events);
      return updated;
    });
  }

  /// Explicit reconciliation action. It can discard only the visible head,
  /// never a later event that depends on it.
  Future<void> discardHead(MobileOfflineQueueOwner owner, String eventId) {
    return _serialized(owner, () async {
      final events = (await store.read(owner)).toList(growable: true);
      if (events.isEmpty || events.first.eventId != eventId) {
        throw StateError('El evento a descartar no es la cabecera de la cola.');
      }
      events.removeAt(0);
      await store.replace(owner, events);
    });
  }

  Future<void> clear(MobileOfflineQueueOwner owner) {
    return _serialized(owner, () => store.clear(owner));
  }

  Future<void> clearAll() => store.clearAll();

  Duration retryDelayForAttempt(int attempt) {
    if (attempt < 1) {
      throw ArgumentError.value(attempt, 'attempt', 'Debe ser al menos 1.');
    }
    var delay = initialRetryDelay;
    for (var index = 1; index < attempt; index += 1) {
      final doubled = Duration(microseconds: delay.inMicroseconds * 2);
      if (doubled >= maximumRetryDelay) {
        return maximumRetryDelay;
      }
      delay = doubled;
    }
    return delay;
  }

  MobileOfflineEvent _requirePendingHead(
    List<MobileOfflineEvent> events,
    String eventId,
  ) {
    if (events.isEmpty || events.first.eventId != eventId) {
      throw StateError('El evento no es la cabecera de la cola offline.');
    }
    final head = events.first;
    if (head.isBlocked) {
      throw StateError('El evento offline está bloqueado y debe conciliarse.');
    }
    return head;
  }

  Future<T> _serialized<T>(
    MobileOfflineQueueOwner owner,
    Future<T> Function() action,
  ) async {
    final key = owner.storageScope;
    final previous = _ownerTails[key] ?? Future<void>.value();
    final release = Completer<void>();
    final tail = previous.onError((_, _) {}).then((_) => release.future);
    _ownerTails[key] = tail;
    await previous.onError((_, _) {});
    try {
      return await action();
    } finally {
      release.complete();
      if (identical(_ownerTails[key], tail)) {
        _ownerTails.remove(key);
      }
    }
  }
}
