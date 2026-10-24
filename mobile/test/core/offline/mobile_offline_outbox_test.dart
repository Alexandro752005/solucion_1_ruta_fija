import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/offline/mobile_offline.dart';

void main() {
  final owner = MobileOfflineQueueOwner(userId: 'user-1', driverId: 'driver-1');
  final instant = DateTime.utc(2026, 9, 25, 15, 30);

  group('MobileOfflineOutbox', () {
    test(
      'keeps a FIFO payload unchanged and enforces the bounded queue',
      () async {
        final store = _MemoryOfflineQueueStore();
        final outbox = MobileOfflineOutbox(
          store: store,
          maximumEvents: 2,
          clock: () => instant,
        );
        final availability = MobileOfflineEvent.availabilityChange(
          owner: owner,
          eventId: '11111111-1111-4111-8111-111111111111',
          occurredAt: instant,
          targetAvailability: 'DESCANSO',
          expectedAvailabilityUpdatedAt: instant.subtract(
            const Duration(minutes: 1),
          ),
        );
        final start = MobileOfflineEvent.serviceStart(
          owner: owner,
          eventId: '22222222-2222-4222-8222-222222222222',
          occurredAt: instant.add(const Duration(seconds: 1)),
          assignmentId: 'assignment-1',
          version: 4,
        );

        await outbox.enqueue(availability);
        await outbox.enqueue(start);

        final events = await outbox.list(owner);
        final restored = MobileOfflineEvent.fromStorageJson(
          Map<String, dynamic>.from(availability.toStorageJson()),
        );
        expect(events.map((event) => event.eventId), [
          availability.eventId,
          start.eventId,
        ]);
        expect(events.first.toSyncEventJson(), availability.toSyncEventJson());
        expect(events.last.toSyncEventJson(), start.toSyncEventJson());
        expect(restored.toSyncEventJson(), availability.toSyncEventJson());
        expect(restored.owner.sameAs(owner), isTrue);

        await expectLater(
          outbox.enqueue(
            MobileOfflineEvent.serviceComplete(
              owner: owner,
              eventId: '33333333-3333-4333-8333-333333333333',
              occurredAt: instant,
              assignmentId: 'assignment-1',
              version: 4,
            ),
          ),
          throwsA(isA<StateError>()),
        );
      },
    );

    test(
      'uses bounded backoff without changing the event identity or body',
      () async {
        final outbox = MobileOfflineOutbox(
          store: _MemoryOfflineQueueStore(),
          clock: () => instant,
        );
        final event = MobileOfflineEvent.serviceStart(
          owner: owner,
          eventId: '44444444-4444-4444-8444-444444444444',
          occurredAt: instant,
          assignmentId: 'assignment-2',
          version: 3,
        );
        final originalBody = event.toSyncEventJson();
        await outbox.enqueue(event);

        final pending = await outbox.markTemporaryFailure(
          owner,
          event.eventId,
          at: instant,
        );

        expect(pending.attempts, 1);
        expect(pending.nextRetryAt, instant.add(const Duration(seconds: 5)));
        expect(pending.toSyncEventJson(), originalBody);
        expect(
          await outbox.nextReady(
            owner,
            at: instant.add(const Duration(seconds: 4)),
          ),
          isNull,
        );
        expect(
          (await outbox.nextReady(
            owner,
            at: instant.add(const Duration(seconds: 5)),
          ))?.eventId,
          event.eventId,
        );
        expect(outbox.retryDelayForAttempt(8), const Duration(minutes: 5));
      },
    );

    test(
      'a blocked head stops FIFO delivery until explicitly discarded',
      () async {
        final outbox = MobileOfflineOutbox(
          store: _MemoryOfflineQueueStore(),
          clock: () => instant,
        );
        final availability = MobileOfflineEvent.availabilityChange(
          owner: owner,
          eventId: '55555555-5555-4555-8555-555555555555',
          occurredAt: instant,
          targetAvailability: 'NO_DISPONIBLE',
          expectedAvailabilityUpdatedAt: instant,
        );
        final complete = MobileOfflineEvent.serviceComplete(
          owner: owner,
          eventId: '66666666-6666-4666-8666-666666666666',
          occurredAt: instant.add(const Duration(seconds: 1)),
          assignmentId: 'assignment-3',
          version: 2,
        );
        await outbox.enqueue(availability);
        await outbox.enqueue(complete);

        final blocked = await outbox.markBlocked(
          owner,
          availability.eventId,
          code: 'RESOURCE_VERSION_CONFLICT',
        );

        expect(blocked.isBlocked, isTrue);
        expect(await outbox.nextReady(owner), isNull);
        await expectLater(
          outbox.markDelivered(owner, complete.eventId),
          throwsA(isA<StateError>()),
        );

        await outbox.discardHead(owner, availability.eventId);
        expect((await outbox.nextReady(owner))?.eventId, complete.eventId);
      },
    );

    test('keeps queues isolated by authenticated user and driver', () async {
      final outbox = MobileOfflineOutbox(
        store: _MemoryOfflineQueueStore(),
        clock: () => instant,
      );
      final otherOwner = MobileOfflineQueueOwner(
        userId: 'user-2',
        driverId: 'driver-2',
      );
      await outbox.enqueue(
        MobileOfflineEvent.availabilityChange(
          owner: owner,
          eventId: '77777777-7777-4777-8777-777777777777',
          occurredAt: instant,
          targetAvailability: 'DISPONIBLE',
          expectedAvailabilityUpdatedAt: instant,
        ),
      );

      expect(await outbox.count(otherOwner), 0);
      expect(await outbox.count(owner), 1);
    });
  });
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
    return List<MobileOfflineEvent>.unmodifiable(
      _queues[owner.storageScope] ?? const [],
    );
  }

  @override
  Future<void> replace(
    MobileOfflineQueueOwner owner,
    List<MobileOfflineEvent> events,
  ) async {
    _queues[owner.storageScope] = List<MobileOfflineEvent>.from(events);
  }
}
