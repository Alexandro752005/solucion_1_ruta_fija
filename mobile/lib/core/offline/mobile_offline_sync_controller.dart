import 'package:flutter/foundation.dart';

import '../network/mobile_api_client.dart';
import '../network/mobile_api_exception.dart';
import 'mobile_offline_event.dart';
import 'mobile_offline_outbox.dart';

/// Scalar result returned by the M8 synchronization boundary for one FIFO head.
enum MobileOfflineServerResultStatus {
  applied('APPLIED'),
  alreadyApplied('ALREADY_APPLIED'),
  conflict('CONFLICT'),
  rejected('REJECTED');

  const MobileOfflineServerResultStatus(this.apiValue);

  final String apiValue;

  static MobileOfflineServerResultStatus fromApiValue(String value) {
    return MobileOfflineServerResultStatus.values.firstWhere(
      (status) => status.apiValue == value,
      orElse: () => throw FormatException(
        'Estado de sincronización offline desconocido: $value.',
      ),
    );
  }
}

final class MobileOfflineServerResult {
  const MobileOfflineServerResult({
    required this.eventId,
    required this.status,
    this.serverState,
    this.errorCode,
  });

  final String eventId;
  final MobileOfflineServerResultStatus status;
  final String? serverState;
  final String? errorCode;
}

/// Keeps the HTTP boundary replaceable in focused tests. It sends one FIFO head
/// per request even though the server also accepts a bounded batch.
abstract interface class MobileOfflineSyncTransport {
  Future<MobileOfflineServerResult> send(MobileOfflineEvent event);
}

final class MobileApiOfflineSyncTransport
    implements MobileOfflineSyncTransport {
  MobileApiOfflineSyncTransport(this._apiClient);

  final MobileApiClient _apiClient;

  @override
  Future<MobileOfflineServerResult> send(MobileOfflineEvent event) async {
    final response = await _apiClient.postObject('mobile/sync/events', {
      'events': [event.toSyncEventJson()],
    });
    final rawResults = response['results'];
    if (rawResults is! List ||
        rawResults.length != 1 ||
        rawResults.single is! Map) {
      throw const FormatException(
        'La conciliación offline no devolvió un resultado único.',
      );
    }
    final raw = Map<String, dynamic>.from(rawResults.single as Map);
    final eventId = _requiredText(raw, 'eventId');
    if (eventId != event.eventId) {
      throw const FormatException(
        'La conciliación offline devolvió otro identificador de evento.',
      );
    }
    return MobileOfflineServerResult(
      eventId: eventId,
      status: MobileOfflineServerResultStatus.fromApiValue(
        _requiredText(raw, 'status'),
      ),
      serverState: _optionalText(raw, 'serverState'),
      errorCode: _optionalText(raw, 'errorCode'),
    );
  }
}

enum MobileOfflineSyncOutcomeStatus {
  idle,
  delivered,
  waitingForRetry,
  temporaryFailure,
  blocked,
  sessionCleared,
  busy,
}

/// Summary of one explicit, foreground synchronization attempt.
final class MobileOfflineSyncOutcome {
  const MobileOfflineSyncOutcome({
    required this.status,
    this.head,
    this.errorCode,
    this.serverState,
    this.deliveredEventIds = const [],
    this.replayedEventIds = const [],
  });

  final MobileOfflineSyncOutcomeStatus status;
  final MobileOfflineEvent? head;
  final String? errorCode;
  final String? serverState;
  final List<String> deliveredEventIds;
  final List<String> replayedEventIds;

  bool delivered(String eventId) => deliveredEventIds.contains(eventId);

  bool replayed(String eventId) => replayedEventIds.contains(eventId);

  bool get needsReconciliation =>
      status == MobileOfflineSyncOutcomeStatus.blocked;

  bool get keptForRetry =>
      status == MobileOfflineSyncOutcomeStatus.temporaryFailure ||
      status == MobileOfflineSyncOutcomeStatus.waitingForRetry;
}

final class MobileOfflineSubmission {
  const MobileOfflineSubmission({required this.event, required this.outcome});

  final MobileOfflineEvent event;
  final MobileOfflineSyncOutcome outcome;

  bool get delivered => outcome.delivered(event.eventId);

  bool get replayed => outcome.replayed(event.eventId);

  bool get pending => !delivered && outcome.keptForRetry;

  bool get blocked => !delivered && outcome.needsReconciliation;
}

/// Raised before writing a duplicate logical operation behind an existing FIFO
/// command. The conductor must reconcile the first action rather than create a
/// second intention for the same aggregate.
final class MobileOfflinePendingActionException implements Exception {
  const MobileOfflinePendingActionException(this.message);

  final String message;

  @override
  String toString() => 'MobileOfflinePendingActionException($message)';
}

/// Owns the visible foreground M8 outbox state. It never starts a timer,
/// listens for connectivity, or launches background work.
final class MobileOfflineSyncController extends ChangeNotifier {
  MobileOfflineSyncController(this._outbox, this._transport);

  final MobileOfflineOutbox _outbox;
  final MobileOfflineSyncTransport _transport;

  MobileOfflineQueueOwner? _owner;
  List<MobileOfflineEvent> _events = const [];
  bool _loaded = false;
  bool _synchronizing = false;
  MobileOfflineQueueOwner? _synchronizingOwner;
  Future<MobileOfflineSyncOutcome>? _synchronization;

  MobileOfflineQueueOwner? get owner => _owner;

  List<MobileOfflineEvent> get events => List.unmodifiable(_events);

  int get pendingCount => _events.length;

  MobileOfflineEvent? get head => _events.isEmpty ? null : _events.first;

  bool get isSynchronizing => _synchronizing;

  Future<void> bind(MobileOfflineQueueOwner owner) async {
    if (_owner?.sameAs(owner) == true && _loaded) {
      return;
    }
    _owner = owner;
    _events = const [];
    _loaded = false;
    notifyListeners();
    await _reload(owner);
  }

  Future<void> refresh({MobileOfflineQueueOwner? owner}) async {
    final target = owner ?? _owner;
    if (target == null) {
      return;
    }
    if (_owner?.sameAs(target) != true) {
      _owner = target;
      _events = const [];
      _loaded = false;
      notifyListeners();
    }
    await _reload(target);
  }

  Future<MobileOfflineSubmission> submitAvailability({
    required MobileOfflineQueueOwner owner,
    required String targetAvailability,
    required DateTime expectedAvailabilityUpdatedAt,
  }) {
    return _enqueueAndSynchronize(
      MobileOfflineEvent.availabilityChange(
        owner: owner,
        targetAvailability: targetAvailability,
        expectedAvailabilityUpdatedAt: expectedAvailabilityUpdatedAt,
      ),
    );
  }

  Future<MobileOfflineSubmission> submitServiceStart({
    required MobileOfflineQueueOwner owner,
    required String assignmentId,
    required int version,
  }) {
    return _enqueueAndSynchronize(
      MobileOfflineEvent.serviceStart(
        owner: owner,
        assignmentId: assignmentId,
        version: version,
      ),
    );
  }

  Future<MobileOfflineSubmission> submitServiceComplete({
    required MobileOfflineQueueOwner owner,
    required String assignmentId,
    required int version,
  }) {
    return _enqueueAndSynchronize(
      MobileOfflineEvent.serviceComplete(
        owner: owner,
        assignmentId: assignmentId,
        version: version,
      ),
    );
  }

  /// Safely adopts a durable M4 start/complete command created by an earlier
  /// app version. Its UUID and timestamp are preserved before its legacy copy
  /// is removed, so an upgrade never invents a second operation.
  Future<MobileOfflineSubmission> submitPersistedEvent(
    MobileOfflineEvent event,
  ) {
    return _enqueueAndSynchronize(event);
  }

  /// Explicit user action. It may ignore the informational backoff but only
  /// processes the FIFO head and stops at the first temporary or final result.
  Future<MobileOfflineSyncOutcome> synchronizeNow({
    MobileOfflineQueueOwner? owner,
  }) {
    final target = owner ?? _owner;
    if (target == null) {
      throw StateError('No hay una sesión de conductor para sincronizar.');
    }
    final current = _synchronization;
    if (current != null) {
      if (_synchronizingOwner?.sameAs(target) == true) {
        return current;
      }
      return Future.value(
        const MobileOfflineSyncOutcome(
          status: MobileOfflineSyncOutcomeStatus.busy,
        ),
      );
    }

    final work = _synchronize(target, ignoreBackoff: true);
    _synchronization = work;
    _synchronizingOwner = target;
    _synchronizing = true;
    notifyListeners();
    return work.whenComplete(() {
      if (identical(_synchronization, work)) {
        _synchronization = null;
        _synchronizingOwner = null;
        _synchronizing = false;
        notifyListeners();
      }
    });
  }

  Future<void> discardBlocked(MobileOfflineEvent event) async {
    final target = _requireOwner(event.owner);
    if (!event.isBlocked) {
      throw StateError('Solo puede descartarse una acción bloqueada.');
    }
    await _outbox.discardHead(target, event.eventId);
    await _reload(target);
  }

  Future<void> clearOwner(MobileOfflineQueueOwner owner) async {
    await _outbox.clear(owner);
    if (_owner?.sameAs(owner) == true) {
      _events = const [];
      _loaded = true;
      notifyListeners();
    }
  }

  Future<void> clearAll() async {
    await _outbox.clearAll();
    _events = const [];
    _loaded = true;
    notifyListeners();
  }

  Future<MobileOfflineSubmission> _enqueueAndSynchronize(
    MobileOfflineEvent event,
  ) async {
    await bind(event.owner);
    final queued = await _outbox.list(event.owner);
    _assertNoDuplicateAggregate(queued, event);
    await _outbox.enqueue(event);
    await _reload(event.owner);
    final outcome = await synchronizeNow(owner: event.owner);
    return MobileOfflineSubmission(event: event, outcome: outcome);
  }

  Future<MobileOfflineSyncOutcome> _synchronize(
    MobileOfflineQueueOwner target, {
    required bool ignoreBackoff,
  }) async {
    final delivered = <String>[];
    final replayed = <String>[];
    while (true) {
      final next = await _outbox.nextReady(
        target,
        ignoreBackoff: ignoreBackoff,
      );
      if (next == null) {
        final currentHead = await _outbox.head(target);
        await _reload(target);
        if (currentHead == null) {
          return MobileOfflineSyncOutcome(
            status: delivered.isEmpty
                ? MobileOfflineSyncOutcomeStatus.idle
                : MobileOfflineSyncOutcomeStatus.delivered,
            deliveredEventIds: List.unmodifiable(delivered),
            replayedEventIds: List.unmodifiable(replayed),
          );
        }
        return MobileOfflineSyncOutcome(
          status: currentHead.isBlocked
              ? MobileOfflineSyncOutcomeStatus.blocked
              : MobileOfflineSyncOutcomeStatus.waitingForRetry,
          head: currentHead,
          errorCode: currentHead.blockedCode,
          deliveredEventIds: List.unmodifiable(delivered),
          replayedEventIds: List.unmodifiable(replayed),
        );
      }

      MobileOfflineServerResult serverResult;
      try {
        serverResult = await _transport.send(next);
      } on MobileNetworkException {
        return _temporaryFailure(target, next, delivered, replayed);
      } on FormatException {
        return _temporaryFailure(target, next, delivered, replayed);
      } on MobileApiException catch (error) {
        if (error.status >= 500) {
          return _temporaryFailure(target, next, delivered, replayed);
        }
        if (await _outbox.head(target) == null) {
          await _reload(target);
          return MobileOfflineSyncOutcome(
            status: MobileOfflineSyncOutcomeStatus.sessionCleared,
            deliveredEventIds: List.unmodifiable(delivered),
            replayedEventIds: List.unmodifiable(replayed),
          );
        }
        return _block(target, next, error.code, delivered, replayed);
      }

      switch (serverResult.status) {
        case MobileOfflineServerResultStatus.applied:
        case MobileOfflineServerResultStatus.alreadyApplied:
          try {
            await _outbox.markDelivered(target, next.eventId);
          } on StateError {
            await _reload(target);
            return MobileOfflineSyncOutcome(
              status: MobileOfflineSyncOutcomeStatus.sessionCleared,
              deliveredEventIds: List.unmodifiable(delivered),
              replayedEventIds: List.unmodifiable(replayed),
            );
          }
          delivered.add(next.eventId);
          if (serverResult.status ==
              MobileOfflineServerResultStatus.alreadyApplied) {
            replayed.add(next.eventId);
          }
          continue;
        case MobileOfflineServerResultStatus.conflict:
        case MobileOfflineServerResultStatus.rejected:
          return _block(
            target,
            next,
            serverResult.errorCode,
            delivered,
            replayed,
            serverState: serverResult.serverState,
          );
      }
    }
  }

  Future<MobileOfflineSyncOutcome> _temporaryFailure(
    MobileOfflineQueueOwner owner,
    MobileOfflineEvent event,
    List<String> delivered,
    List<String> replayed,
  ) async {
    try {
      final pending = await _outbox.markTemporaryFailure(owner, event.eventId);
      await _reload(owner);
      return MobileOfflineSyncOutcome(
        status: MobileOfflineSyncOutcomeStatus.temporaryFailure,
        head: pending,
        deliveredEventIds: List.unmodifiable(delivered),
        replayedEventIds: List.unmodifiable(replayed),
      );
    } on StateError {
      await _reload(owner);
      return MobileOfflineSyncOutcome(
        status: MobileOfflineSyncOutcomeStatus.sessionCleared,
        deliveredEventIds: List.unmodifiable(delivered),
        replayedEventIds: List.unmodifiable(replayed),
      );
    }
  }

  Future<MobileOfflineSyncOutcome> _block(
    MobileOfflineQueueOwner owner,
    MobileOfflineEvent event,
    String? errorCode,
    List<String> delivered,
    List<String> replayed, {
    String? serverState,
  }) async {
    try {
      final blocked = await _outbox.markBlocked(
        owner,
        event.eventId,
        code: errorCode,
      );
      await _reload(owner);
      return MobileOfflineSyncOutcome(
        status: MobileOfflineSyncOutcomeStatus.blocked,
        head: blocked,
        errorCode: errorCode,
        serverState: serverState,
        deliveredEventIds: List.unmodifiable(delivered),
        replayedEventIds: List.unmodifiable(replayed),
      );
    } on StateError {
      await _reload(owner);
      return MobileOfflineSyncOutcome(
        status: MobileOfflineSyncOutcomeStatus.sessionCleared,
        deliveredEventIds: List.unmodifiable(delivered),
        replayedEventIds: List.unmodifiable(replayed),
      );
    }
  }

  void _assertNoDuplicateAggregate(
    List<MobileOfflineEvent> queued,
    MobileOfflineEvent candidate,
  ) {
    if (candidate.type == MobileOfflineEventType.availabilityChange &&
        queued.any(
          (event) => event.type == MobileOfflineEventType.availabilityChange,
        )) {
      throw const MobileOfflinePendingActionException(
        'Ya existe un cambio de disponibilidad pendiente. Sincronícelo antes de crear otro.',
      );
    }
    final assignmentId = candidate.assignmentId;
    if (assignmentId != null &&
        queued.any((event) => event.assignmentId == assignmentId)) {
      throw const MobileOfflinePendingActionException(
        'Esta asignación ya tiene una acción pendiente. Sincronice o concilie primero.',
      );
    }
  }

  MobileOfflineQueueOwner _requireOwner(MobileOfflineQueueOwner requested) {
    final current = _owner;
    if (current == null || !current.sameAs(requested)) {
      throw StateError('La acción no corresponde a la sesión actual.');
    }
    return current;
  }

  Future<void> _reload(MobileOfflineQueueOwner target) async {
    final events = await _outbox.list(target);
    if (_owner?.sameAs(target) != true) {
      return;
    }
    _events = events;
    _loaded = true;
    notifyListeners();
  }
}

String _requiredText(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException('Falta el campo $key en la conciliación offline.');
}

String? _optionalText(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw FormatException(
    'El campo $key no es válido en la conciliación offline.',
  );
}
