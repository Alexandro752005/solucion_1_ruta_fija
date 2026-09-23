import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

MobileAuthSession testSession({
  String accessToken = 'access-token',
  String refreshToken = 'refresh-token',
}) {
  return MobileAuthSession(
    accessToken: accessToken,
    refreshToken: refreshToken,
    expiresIn: 900,
    refreshExpiresIn: 604800,
    user: const MobileAuthenticatedUser(
      id: '11111111-1111-4111-8111-111111111111',
      driverId: '22222222-2222-4222-8222-222222222222',
      fullName: 'Conductora de prueba',
      role: 'CONDUCTOR',
    ),
  );
}

final class InMemoryRefreshTokenStore implements MobileRefreshTokenStore {
  InMemoryRefreshTokenStore([this.value]);

  String? value;
  int clearCalls = 0;

  @override
  Future<void> clear() async {
    clearCalls++;
    value = null;
  }

  @override
  Future<String?> readRefreshToken() async => value;

  @override
  Future<void> writeRefreshToken(String value) async {
    this.value = value;
  }
}

final class FakeMobileAuthGateway implements MobileAuthGateway {
  FakeMobileAuthGateway({
    MobileAuthSession? session,
    this.refreshFailure,
    this.loginFailure,
  }) : session = session ?? testSession();

  MobileAuthSession session;
  Object? refreshFailure;
  Object? loginFailure;
  final List<String> refreshedTokens = [];
  final List<String> loggedOutTokens = [];

  @override
  Future<MobileAuthSession> login({
    required String email,
    required String password,
  }) async {
    if (loginFailure != null) {
      throw loginFailure!;
    }
    return session;
  }

  @override
  Future<MobileAuthSession> refresh(String refreshToken) async {
    refreshedTokens.add(refreshToken);
    if (refreshFailure != null) {
      throw refreshFailure!;
    }
    return session;
  }

  @override
  Future<void> logout(String refreshToken) async {
    loggedOutTokens.add(refreshToken);
  }
}

final class FakeMobileDriverGateway implements MobileDriverGateway {
  FakeMobileDriverGateway({
    MobileDriverProfile? profile,
    MobileAvailability? availability,
  }) : profileValue =
           profile ??
           const MobileDriverProfile(
             driverId: '22222222-2222-4222-8222-222222222222',
             fullName: 'Conductora de prueba',
             groupName: 'Grupo Norte',
             availabilityStatus: MobileAvailabilityStatus.disponible,
             primaryVehicle: MobileVehicle(
               plate: 'ABC-123',
               status: 'DISPONIBLE',
             ),
           ),
       currentAvailability =
           availability ??
           const MobileAvailability(
             driverId: '22222222-2222-4222-8222-222222222222',
             status: MobileAvailabilityStatus.disponible,
           );

  MobileDriverProfile profileValue;
  MobileAvailability currentAvailability;
  Object? failure;
  final List<MobileAvailabilityStatus> changedTo = [];

  @override
  Future<MobileAvailability> availability() async {
    if (failure != null) {
      throw failure!;
    }
    return currentAvailability;
  }

  @override
  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  ) async {
    if (failure != null) {
      throw failure!;
    }
    changedTo.add(status);
    currentAvailability = MobileAvailability(
      driverId: currentAvailability.driverId,
      status: status,
    );
    return currentAvailability;
  }

  @override
  Future<MobileDriverProfile> profile() async {
    if (failure != null) {
      throw failure!;
    }
    return profileValue;
  }
}

MobileAssignment testAssignment({
  String id = '33333333-3333-4333-8333-333333333333',
  MobileAssignmentStatus status = MobileAssignmentStatus.pendingResponse,
  MobileAssignmentResponseMode responseMode =
      MobileAssignmentResponseMode.mobileConfirmation,
  int version = 0,
  DateTime? reservedAt,
  DateTime? acceptedAt,
  DateTime? startedAt,
  DateTime? completedAt,
}) {
  return MobileAssignment(
    id: id,
    status: status,
    responseMode: responseMode,
    version: version,
    driverId: '22222222-2222-4222-8222-222222222222',
    driverName: 'Conductora de prueba',
    groupName: 'Grupo Norte',
    vehiclePlate: 'ABC-123',
    originText: 'Terminal Norte',
    destinationText: 'Terminal Sur',
    scheduledAt: DateTime.utc(2026, 9, 24, 14),
    scheduledEndAt: DateTime.utc(2026, 9, 24, 15),
    reservedAt: reservedAt,
    startedAt: startedAt,
    completedAt: completedAt,
    responseDeadlineAt: DateTime.utc(2026, 9, 24, 12),
    acceptedAt: acceptedAt,
    rejectedAt: null,
    rejectionReason: null,
    expiredAt: null,
    notes: 'Prueba de asignación',
  );
}

final class InMemoryAssignmentCommandStore
    implements MobileAssignmentCommandStore {
  final Map<String, MobilePendingAssignmentCommand> _values = {};

  @override
  Future<void> delete(MobilePendingAssignmentCommand command) async {
    _values.remove(_key(command));
  }

  @override
  Future<List<MobilePendingAssignmentCommand>> readForDriver(
    String driverId,
  ) async {
    return _values.values
        .where((command) => command.driverId == driverId)
        .toList(growable: false);
  }

  @override
  Future<void> write(MobilePendingAssignmentCommand command) async {
    _values[_key(command)] = command;
  }

  String _key(MobilePendingAssignmentCommand command) {
    return '${command.driverId}:${command.assignmentId}';
  }
}

final class FakeMobileAssignmentGateway implements MobileAssignmentGateway {
  FakeMobileAssignmentGateway({List<MobileAssignment>? assignments})
    : _assignments = assignments ?? [testAssignment()];

  final List<MobileAssignment> _assignments;
  final List<MobilePendingAssignmentCommand> pending = [];
  final List<MobileAssignmentCommandType> sentCommands = [];
  Object? failure;

  @override
  Future<MobileAssignment> get(String assignmentId) async {
    _throwIfNeeded();
    return _assignments.firstWhere((item) => item.id == assignmentId);
  }

  @override
  Future<MobileAssignmentPage> list({
    MobileAssignmentStatus? status,
    int page = 0,
    int size = 30,
  }) async {
    _throwIfNeeded();
    final filtered = _assignments
        .where((item) => status == null || item.status == status)
        .toList(growable: false);
    return MobileAssignmentPage(
      items: filtered,
      page: page,
      size: size,
      totalItems: filtered.length,
      totalPages: filtered.isEmpty ? 0 : 1,
    );
  }

  @override
  Future<List<MobilePendingAssignmentCommand>> pendingForDriver(
    String driverId,
  ) async {
    _throwIfNeeded();
    return pending
        .where((command) => command.driverId == driverId)
        .toList(growable: false);
  }

  @override
  Future<MobileAssignmentCommandResult> retry(
    MobilePendingAssignmentCommand command,
  ) async {
    final assignment = await get(command.assignmentId);
    final result = await send(
      assignment,
      command.command,
      rejectionReason: command.rejectionReason,
    );
    pending.removeWhere(
      (item) =>
          item.driverId == command.driverId &&
          item.assignmentId == command.assignmentId,
    );
    return MobileAssignmentCommandResult(
      assignment: result.assignment,
      replayed: true,
    );
  }

  @override
  Future<MobileAssignmentCommandResult> send(
    MobileAssignment assignment,
    MobileAssignmentCommandType command, {
    String? rejectionReason,
  }) async {
    _throwIfNeeded();
    sentCommands.add(command);
    final now = DateTime.utc(2026, 9, 23, 12);
    final updated = switch (command) {
      MobileAssignmentCommandType.accept => _copy(
        assignment,
        status: MobileAssignmentStatus.scheduled,
        version: assignment.version + 1,
        reservedAt: now,
        acceptedAt: now,
      ),
      MobileAssignmentCommandType.reject => _copy(
        assignment,
        status: MobileAssignmentStatus.rejected,
        version: assignment.version + 1,
      ),
      MobileAssignmentCommandType.start => _copy(
        assignment,
        status: MobileAssignmentStatus.enServicio,
        version: assignment.version + 1,
        startedAt: now,
      ),
      MobileAssignmentCommandType.complete => _copy(
        assignment,
        status: MobileAssignmentStatus.completed,
        version: assignment.version + 1,
        completedAt: now,
      ),
    };
    final index = _assignments.indexWhere((item) => item.id == assignment.id);
    _assignments[index] = updated;
    return MobileAssignmentCommandResult(assignment: updated, replayed: false);
  }

  void _throwIfNeeded() {
    if (failure != null) {
      throw failure!;
    }
  }

  MobileAssignment _copy(
    MobileAssignment source, {
    required MobileAssignmentStatus status,
    required int version,
    DateTime? reservedAt,
    DateTime? acceptedAt,
    DateTime? startedAt,
    DateTime? completedAt,
  }) {
    return MobileAssignment(
      id: source.id,
      status: status,
      responseMode: source.responseMode,
      version: version,
      driverId: source.driverId,
      driverName: source.driverName,
      groupName: source.groupName,
      vehiclePlate: source.vehiclePlate,
      originText: source.originText,
      destinationText: source.destinationText,
      scheduledAt: source.scheduledAt,
      scheduledEndAt: source.scheduledEndAt,
      reservedAt: reservedAt ?? source.reservedAt,
      startedAt: startedAt ?? source.startedAt,
      completedAt: completedAt ?? source.completedAt,
      responseDeadlineAt: source.responseDeadlineAt,
      acceptedAt: acceptedAt ?? source.acceptedAt,
      rejectedAt: source.rejectedAt,
      rejectionReason: source.rejectionReason,
      expiredAt: source.expiredAt,
      notes: source.notes,
    );
  }
}
