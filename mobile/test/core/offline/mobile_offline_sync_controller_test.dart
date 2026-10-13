import 'dart:async';
import 'dart:collection';

import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/offline/mobile_offline.dart';

void main() {
  final owner = MobileOfflineQueueOwner(
    userId: '11111111-1111-4111-8111-111111111111',
    driverId: '22222222-2222-4222-8222-222222222222',
  );
  final instant = DateTime.utc(2026, 9, 26, 12);

  test(
    'submits one allowed availability event through the bounded endpoint',
    () async {
      final transport = _ScriptedTransport();
      final controller = _controller(transport);

      final pending = controller.submitAvailability(
        owner: owner,
        targetAvailability: 'DESCANSO',
        expectedAvailabilityUpdatedAt: instant,
      );
      await _waitForCall(transport);
      final sent = transport.calls.single;
      transport.reply(
        MobileOfflineServerResult(
          eventId: sent.eventId,
          status: MobileOfflineServerResultStatus.applied,
          serverState: 'DESCANSO',
        ),
      );

      final result = await pending;

      expect(result.delivered, isTrue);
      expect(controller.pendingCount, 0);
      expect(sent.type, MobileOfflineEventType.availabilityChange);
      expect(
        sent.toSyncEventJson().keys,
        containsAll(<String>[
          'eventId',
          'type',
          'occurredAt',
          'targetAvailability',
          'expectedAvailabilityUpdatedAt',
        ]),
      );
      expect(sent.toSyncEventJson(), isNot(contains('driverId')));
    },
  );

  test(
    'keeps the exact event body after a temporary failure then replays it',
    () async {
      final transport = _ScriptedTransport();
      final controller = _controller(transport);
      final firstAttempt = controller.submitServiceStart(
        owner: owner,
        assignmentId: '33333333-3333-4333-8333-333333333333',
        version: 4,
      );
      await _waitForCall(transport);
      final original = transport.calls.single;
      final originalBody = original.toSyncEventJson();
      transport.reply(const MobileNetworkException('Sin red'));

      final queued = await firstAttempt;
      expect(queued.pending, isTrue);
      expect(controller.pendingCount, 1);
      expect(controller.head?.attempts, 1);

      final retry = controller.synchronizeNow(owner: owner);
      await _waitForCall(transport, expectedCalls: 2);
      final replay = transport.calls.last;
      transport.reply(
        MobileOfflineServerResult(
          eventId: replay.eventId,
          status: MobileOfflineServerResultStatus.alreadyApplied,
          serverState: 'EN_SERVICIO',
        ),
      );
      final outcome = await retry;

      expect(replay.eventId, original.eventId);
      expect(replay.toSyncEventJson(), originalBody);
      expect(outcome.replayed(original.eventId), isTrue);
      expect(controller.pendingCount, 0);
    },
  );

  test('blocks the FIFO head and never sends its following event', () async {
    final store = _MemoryOfflineQueueStore();
    final outbox = MobileOfflineOutbox(store: store, clock: () => instant);
    final transport = _ScriptedTransport();
    final controller = MobileOfflineSyncController(outbox, transport);
    final first = MobileOfflineEvent.availabilityChange(
      owner: owner,
      eventId: '44444444-4444-4444-8444-444444444444',
      occurredAt: instant,
      targetAvailability: 'NO_DISPONIBLE',
      expectedAvailabilityUpdatedAt: instant,
    );
    final following = MobileOfflineEvent.serviceStart(
      owner: owner,
      eventId: '55555555-5555-4555-8555-555555555555',
      occurredAt: instant.add(const Duration(seconds: 1)),
      assignmentId: '33333333-3333-4333-8333-333333333333',
      version: 2,
    );
    await outbox.enqueue(first);
    await outbox.enqueue(following);
    await controller.bind(owner);

    final operation = controller.synchronizeNow(owner: owner);
    await _waitForCall(transport);
    transport.reply(
      MobileOfflineServerResult(
        eventId: first.eventId,
        status: MobileOfflineServerResultStatus.conflict,
        errorCode: 'RESOURCE_VERSION_CONFLICT',
      ),
    );
    final result = await operation;

    expect(result.needsReconciliation, isTrue);
    expect(controller.events, hasLength(2));
    expect(controller.head?.eventId, first.eventId);
    expect(controller.head?.isBlocked, isTrue);
    expect(transport.calls.map((event) => event.eventId), [first.eventId]);
  });
}

MobileOfflineSyncController _controller(_ScriptedTransport transport) {
  return MobileOfflineSyncController(
    MobileOfflineOutbox(store: _MemoryOfflineQueueStore()),
    transport,
  );
}

Future<void> _waitForCall(
  _ScriptedTransport transport, {
  int expectedCalls = 1,
}) async {
  for (var index = 0; index < 20; index += 1) {
    if (transport.calls.length >= expectedCalls) {
      return;
    }
    await Future<void>.delayed(Duration.zero);
  }
  fail('La prueba no recibio el envio M8 esperado.');
}

final class _ScriptedTransport implements MobileOfflineSyncTransport {
  final List<MobileOfflineEvent> calls = [];
  final Queue<Completer<Object>> _responses = Queue<Completer<Object>>();

  @override
  Future<MobileOfflineServerResult> send(MobileOfflineEvent event) async {
    calls.add(event);
    final response = Completer<Object>();
    _responses.add(response);
    final value = await response.future;
    if (value is MobileOfflineServerResult) {
      return value;
    }
    throw value;
  }

  void reply(Object value) {
    if (_responses.isEmpty) {
      fail('No existe un envio pendiente para responder.');
    }
    _responses.removeFirst().complete(value);
  }
}

final class _MemoryOfflineQueueStore implements MobileOfflineQueueStore {
  final Map<String, List<MobileOfflineEvent>> _queues = {};

  @override
  Future<void> clear(MobileOfflineQueueOwner owner) async {
    _queues.remove(owner.storageScope);
  }

  @override
  Future<void> clearAll() async {
    _queues.clear();
  }

  @override
  Future<List<MobileOfflineEvent>> read(MobileOfflineQueueOwner owner) async {
    return List.unmodifiable(_queues[owner.storageScope] ?? const []);
  }

  @override
  Future<void> replace(
    MobileOfflineQueueOwner owner,
    List<MobileOfflineEvent> events,
  ) async {
    _queues[owner.storageScope] = List.of(events);
  }
}
