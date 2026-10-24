import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_client.dart';
import '../../../core/network/mobile_api_exception.dart';
import 'mobile_assignment_command_store.dart';
import 'mobile_service_time_repository.dart';
import 'mobile_service_attention_repository.dart';
import 'mobile_service_handover_repository.dart';
import '../domain/mobile_assignment_models.dart';
import '../domain/mobile_route_models.dart';
import '../../location/data/mobile_service_gps_repository.dart';

typedef MobileClientEventIdFactory = String Function();
typedef MobileUtcClock = DateTime Function();
typedef MobileStartGpsProofProvider = Future<Map<String, Object>> Function();

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
    MobilePendingAssignmentCommand command,
  );
}

final class MobileAssignmentRepository
    implements
        MobileAssignmentGateway,
        MobileRouteGateway,
        MobileServiceGpsGateway,
        MobileServiceTimeGateway,
        MobileServiceAttentionGateway,
        MobileServiceHandoverGateway {
  MobileAssignmentRepository(
    this._apiClient,
    this._commandStore, {
    this.clientEventIdFactory = createCorrelationId,
    this.utcClock = _nowUtc,
    this.enableInMemoryRetryQueue = false,
    this.startGpsProofProvider,
    this.beforeComplete,
    this.onStateChanged,
  });

  final MobileApiClient _apiClient;
  final MobileAssignmentCommandStore _commandStore;
  final MobileClientEventIdFactory clientEventIdFactory;
  final MobileUtcClock utcClock;
  final MobileStartGpsProofProvider? startGpsProofProvider;
  final Future<void> Function(MobileAssignment)? beforeComplete;
  final void Function(MobileAssignment)? onStateChanged;
  @override
  Future<MobileHandoverView> handoverView(String assignmentId) =>
      MobileServiceHandoverRepository(_apiClient).handoverView(assignmentId);
  @override
  Future<MobileAssignment> assumeHandover(
    MobileAssignment assignment, {
    required String eventId,
    required bool readyConfirmed,
  }) async {
    final result =
        await MobileServiceHandoverRepository(
          _apiClient,
          gpsProof: startGpsProofProvider,
          now: utcClock,
        ).assumeHandover(
          assignment,
          eventId: eventId,
          readyConfirmed: readyConfirmed,
        );
    onStateChanged?.call(result);
    return result;
  }

  @override
  Future<List<MobileSafetyAttention>> ownAttention(String assignmentId) =>
      MobileServiceAttentionRepository(_apiClient).ownAttention(assignmentId);
  @override
  Future<MobileAssignment> interruptService(
    MobileAssignment assignment, {
    required String eventId,
    required String reason,
    required String detail,
    required bool safeStopConfirmed,
  }) async {
    final result =
        await MobileServiceAttentionRepository(
          _apiClient,
          now: utcClock,
        ).interruptService(
          assignment,
          eventId: eventId,
          reason: reason,
          detail: detail,
          safeStopConfirmed: safeStopConfirmed,
        );
    onStateChanged?.call(result);
    return result;
  }

  @override
  Future<MobileServiceTimeHistory> timeHistory(String assignmentId) =>
      MobileServiceTimeRepository(_apiClient).timeHistory(assignmentId);
  @override
  Future<MobileTimeRequest> requestTimeCorrection(
    String assignmentId,
    String requestKey,
    String reason,
  ) =>
      MobileServiceTimeRepository(_apiClient)
          .requestTimeCorrection(assignmentId, requestKey, reason);
  @override
  Future<List<MobileGpsRequest>> gpsRequests(
    String serviceId, {
    int offset = 0,
  }) =>
      MobileServiceGpsRepository(_apiClient)
          .gpsRequests(serviceId, offset: offset);
  @override
  Future<MobileGpsRequest> requestGps(String serviceId, String requestKey) =>
      MobileServiceGpsRepository(_apiClient).requestGps(serviceId, requestKey);
  @override
  Future<MobileGpsDelivery> deliverGps(
    String requestId,
    String password, {
    int offset = 0,
  }) =>
      MobileServiceGpsRepository(_apiClient)
          .deliverGps(requestId, password, offset: offset);

  /// Keeps uncertain command results in process memory for explicit replay.
  /// Disabled in the v3.1 application runtime; tests may opt in to preserve
  /// coverage of the legacy retry behavior.
  final bool enableInMemoryRetryQueue;

  @override
  Future<MobileAssignedRoute> assignedRoute(String assignmentId) async {
    final id = _requiredIdentifier(assignmentId, 'assignmentId');
    final response = await _apiClient.getObject(
      'mobile/assignments/${Uri.encodeComponent(id)}/route',
    );
    return MobileAssignedRoute.fromJson(response);
  }

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
    final assignment = MobileAssignment.fromJson(response);
    // Consultation can resolve an uncertain interruption without another POST.
    // The location observer ignores terminal records from another service.
    if (assignment.status.isTerminal) onStateChanged?.call(assignment);
    return assignment;
  }

  @override
  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(
    String driverId,
  ) {
    if (!enableInMemoryRetryQueue) {
      return Future.value(const []);
    }
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
    if (enableInMemoryRetryQueue) {
      final existing = await _pendingForAssignment(
        assignment.driverId,
        assignment.id,
      );
      if (existing != null) {
        throw MobileAssignmentPendingCommandException(existing);
      }
    }

    if (command == MobileAssignmentCommandType.complete) {
      await beforeComplete?.call(assignment);
    }
    final pending = MobilePendingAssignmentCommand(
      driverId: assignment.driverId,
      assignmentId: assignment.id,
      command: command,
      clientEventId: clientEventIdFactory(),
      version: assignment.version,
      occurredAt: utcClock(),
      rejectionReason: _normalizeReason(rejectionReason, command),
    );
    if (enableInMemoryRetryQueue) {
      await _commandStore.write(pending);
    }
    // Permission context exists only in this online call; it is never an offline intention.
    final proof = command == MobileAssignmentCommandType.start
        ? await startGpsProofProvider?.call()
        : null;
    return _deliver(pending, gps: proof);
  }

  @override
  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand command,
  ) async {
    if (!enableInMemoryRetryQueue) {
      throw StateError(
        'La cola de reintento no está habilitada en este cliente.',
      );
    }
    final current = await get(command.assignmentId);
    if (_alreadyApplied(command.command, current.status)) {
      if (enableInMemoryRetryQueue) {
        await _commandStore.delete(command);
      }
      return MobileAssignmentCommandResult(assignment: current, replayed: true);
    }
    return _deliver(command);
  }

  bool _alreadyApplied(
    MobileAssignmentCommandType command,
    MobileAssignmentStatus status,
  ) {
    return switch (command) {
      MobileAssignmentCommandType.accept =>
        status == MobileAssignmentStatus.scheduled ||
            status == MobileAssignmentStatus.enServicio ||
            status == MobileAssignmentStatus.completed,
      MobileAssignmentCommandType.reject =>
        status == MobileAssignmentStatus.rejected,
      MobileAssignmentCommandType.start =>
        status == MobileAssignmentStatus.enServicio ||
            status == MobileAssignmentStatus.completed,
      MobileAssignmentCommandType.complete =>
        status == MobileAssignmentStatus.completed,
    };
  }

  Future<MobileAssignmentCommandResult> _deliver(
    MobilePendingAssignmentCommand command, {
    Map<String, Object>? gps,
  }) async {
    try {
      final body = command.toRequestBody();
      if (gps != null) body['gps'] = gps;
      final response = await _apiClient.postObjectResponse(
        'mobile/assignments/${command.assignmentId}/${command.command.endpointSegment}',
        body,
      );
      final assignment = MobileAssignment.fromJson(response.object);
      onStateChanged?.call(assignment);
      final replayed =
          response.header('x-idempotent-replay')?.toLowerCase() == 'true';
      if (enableInMemoryRetryQueue) {
        await _commandStore.delete(command);
      }
      return MobileAssignmentCommandResult(
        assignment: assignment,
        replayed: replayed,
      );
    } catch (error) {
      if (enableInMemoryRetryQueue && _hasDefinitiveOutcome(error)) {
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
