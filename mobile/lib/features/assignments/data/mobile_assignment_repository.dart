import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_client.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/offline/mobile_offline.dart';
import 'mobile_assignment_command_store.dart';
import '../domain/mobile_assignment_models.dart';

typedef MobileClientEventIdFactory = String Function();
typedef MobileUtcClock = DateTime Function();
typedef MobileOfflineOwnerProvider = MobileOfflineQueueOwner Function();

abstract interface class MobileAssignmentGateway {
  Future<MobileAssignmentPage> list({
    MobileAssignmentStatus? status,
    int page = 0,
    int size = 30,
  });

  Future<MobileAssignment> get(String assignmentId);

  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(
    String driverId,
  );

  Future<MobileAssignmentCommandResult> send(
    MobileAssignment assignment,
    MobileAssignmentCommandType command, {
    String? rejectionReason,
  });

  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand command, {
    MobileAssignment? fallbackAssignment,
  });
}

final class MobileAssignmentRepository implements MobileAssignmentGateway {
  MobileAssignmentRepository(
    this._apiClient,
    this._commandStore, {
    this.offlineSyncController,
    this.offlineOwnerProvider,
    this.clientEventIdFactory = createCorrelationId,
    this.utcClock = _nowUtc,
  });

  final MobileApiClient _apiClient;
  final MobileAssignmentCommandStore _commandStore;
  final MobileOfflineSyncController? offlineSyncController;
  final MobileOfflineOwnerProvider? offlineOwnerProvider;
  final MobileClientEventIdFactory clientEventIdFactory;
  final MobileUtcClock utcClock;

  @override
  Future<MobileAssignmentPage> list({
    MobileAssignmentStatus? status,
    int page = 0,
    int size = 30,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('La página o el tamaño solicitado no son válidos.');
    }
    final queryParameters = <String, String>{
      'page': '$page',
      'size': '$size',
      'sort': 'scheduledAt,desc',
    };
    if (status != null) {
      queryParameters['status'] = status.apiValue;
    }
    final response = await _apiClient.getObject(
      'mobile/assignments',
      queryParameters: queryParameters,
    );
    return MobileAssignmentPage.fromJson(response);
  }

  @override
  Future<MobileAssignment> get(String assignmentId) async {
    final normalizedId = _requiredIdentifier(assignmentId, 'assignmentId');
    final response = await _apiClient.getObject(
      'mobile/assignments/$normalizedId',
    );
    return MobileAssignment.fromJson(response);
  }

  @override
  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(
    String driverId,
  ) {
    return _commandStore.readForDriver(
      _requiredIdentifier(driverId, 'driverId'),
    );
  }

  @override
  Future<MobileAssignmentCommandResult> send(
    MobileAssignment assignment,
    MobileAssignmentCommandType command, {
    String? rejectionReason,
  }) async {
    if (!assignment.allows(command)) {
      throw ArgumentError.value(
        command,
        'command',
        'La acción no está permitida para el estado actual de la asignación.',
      );
    }
    if (command == MobileAssignmentCommandType.start ||
        command == MobileAssignmentCommandType.complete) {
      return _sendServiceThroughOfflineBoundary(assignment, command);
    }
    final existing = await _pendingForAssignment(
      assignment.driverId,
      assignment.id,
    );
    if (existing != null) {
      throw MobileAssignmentPendingCommandException(existing);
    }

    final normalizedReason = _normalizeReason(rejectionReason, command);
    final pending = MobilePendingAssignmentCommand(
      driverId: assignment.driverId,
      assignmentId: assignment.id,
      command: command,
      clientEventId: clientEventIdFactory(),
      version: assignment.version,
      occurredAt: utcClock(),
      rejectionReason: normalizedReason,
    );
    await _commandStore.write(pending);
    return _deliver(pending);
  }

  @override
  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand command, {
    MobileAssignment? fallbackAssignment,
  }) {
    if (command.command == MobileAssignmentCommandType.start ||
        command.command == MobileAssignmentCommandType.complete) {
      if (fallbackAssignment == null) {
        throw StateError(
          'Se requiere la asignación actual para recuperar este servicio.',
        );
      }
      return _adoptLegacyServiceCommand(command, fallbackAssignment);
    }
    return _deliver(command);
  }

  Future<MobileAssignmentCommandResult> _sendServiceThroughOfflineBoundary(
    MobileAssignment assignment,
    MobileAssignmentCommandType command,
  ) async {
    final sync = offlineSyncController;
    final ownerProvider = offlineOwnerProvider;
    if (sync == null || ownerProvider == null) {
      throw StateError(
        'La cola M8 no fue configurada para iniciar o cerrar servicios.',
      );
    }
    final owner = ownerProvider();
    if (owner.driverId != assignment.driverId) {
      throw StateError(
        'La asignación no pertenece al conductor de la sesión actual.',
      );
    }
    final submission = command == MobileAssignmentCommandType.start
        ? await sync.submitServiceStart(
            owner: owner,
            assignmentId: assignment.id,
            version: assignment.version,
          )
        : await sync.submitServiceComplete(
            owner: owner,
            assignmentId: assignment.id,
            version: assignment.version,
          );
    return MobileAssignmentCommandResult(
      assignment: assignment,
      replayed: submission.replayed,
      queued: !submission.delivered,
      blocked: submission.blocked,
    );
  }

  Future<MobileAssignmentCommandResult> _adoptLegacyServiceCommand(
    MobilePendingAssignmentCommand command,
    MobileAssignment fallbackAssignment,
  ) async {
    final sync = offlineSyncController;
    final ownerProvider = offlineOwnerProvider;
    if (sync == null || ownerProvider == null) {
      throw StateError(
        'La cola M8 no fue configurada para recuperar este servicio.',
      );
    }
    final owner = ownerProvider();
    if (owner.driverId != command.driverId) {
      throw StateError('La acción pendiente no pertenece al conductor actual.');
    }
    final event = command.command == MobileAssignmentCommandType.start
        ? MobileOfflineEvent.serviceStart(
            owner: owner,
            eventId: command.clientEventId,
            occurredAt: command.occurredAt,
            assignmentId: command.assignmentId,
            version: command.version,
          )
        : MobileOfflineEvent.serviceComplete(
            owner: owner,
            eventId: command.clientEventId,
            occurredAt: command.occurredAt,
            assignmentId: command.assignmentId,
            version: command.version,
          );
    final submission = await sync.submitPersistedEvent(event);
    await _commandStore.delete(command);
    final assignment = submission.delivered
        ? await get(command.assignmentId)
        : fallbackAssignment;
    return MobileAssignmentCommandResult(
      assignment: assignment,
      replayed: submission.replayed,
      queued: !submission.delivered,
      blocked: submission.blocked,
    );
  }

  Future<MobileAssignmentCommandResult> _deliver(
    MobilePendingAssignmentCommand command,
  ) async {
    try {
      final response = await _apiClient.postObjectResponse(
        'mobile/assignments/${command.assignmentId}/${command.command.endpointSegment}',
        command.toRequestBody(),
      );
      final assignment = MobileAssignment.fromJson(response.object);
      final replayed =
          response.header('x-idempotent-replay')?.toLowerCase() == 'true';
      await _commandStore.delete(command);
      return MobileAssignmentCommandResult(
        assignment: assignment,
        replayed: replayed,
      );
    } catch (error) {
      if (_hasDefinitiveOutcome(error)) {
        await _commandStore.delete(command);
      }
      rethrow;
    }
  }

  Future<MobilePendingAssignmentCommand?> _pendingForAssignment(
    String driverId,
    String assignmentId,
  ) async {
    final commands = await pendingForDriver(driverId);
    for (final command in commands) {
      if (command.assignmentId == assignmentId) {
        return command;
      }
    }
    return null;
  }

  bool _hasDefinitiveOutcome(Object error) {
    if (error is MobileNetworkException) {
      return false;
    }
    if (error is! MobileApiException) {
      return false;
    }
    if (error.status < 400 || error.status >= 500) {
      return false;
    }
    return !const {
      'AUTH_TOKEN_INVALID',
      'AUTH_TOKEN_EXPIRED',
      'AUTH_REFRESH_REUSE_DETECTED',
      'MOBILE_SESSION_REQUIRED',
      'RATE_LIMIT_EXCEEDED',
    }.contains(error.code);
  }

  String? _normalizeReason(String? value, MobileAssignmentCommandType command) {
    if (command != MobileAssignmentCommandType.reject) {
      return null;
    }
    final normalized = value?.trim();
    if (normalized == null || normalized.isEmpty) {
      return null;
    }
    if (normalized.length > 300) {
      throw ArgumentError.value(
        value,
        'rejectionReason',
        'El motivo no puede superar 300 caracteres.',
      );
    }
    return normalized;
  }

  String _requiredIdentifier(String value, String field) {
    final normalized = value.trim();
    if (normalized.isEmpty) {
      throw ArgumentError.value(
        value,
        field,
        'El identificador es obligatorio.',
      );
    }
    return normalized;
  }
}

final class MobileAssignmentPendingCommandException implements Exception {
  const MobileAssignmentPendingCommandException(this.command);

  final MobilePendingAssignmentCommand command;

  @override
  String toString() => 'MobileAssignmentPendingCommandException';
}

DateTime _nowUtc() => DateTime.now().toUtc();
