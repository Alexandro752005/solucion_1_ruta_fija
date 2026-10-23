import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/core/offline/mobile_offline.dart';
import 'package:ruta_fija_conductor/features/announcements/data/mobile_announcement_repository.dart';
import 'package:ruta_fija_conductor/features/announcements/domain/mobile_announcement_models.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_access_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_applicant_access_models.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_recovery_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_onboarding_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_conductor_onboarding_models.dart';
import 'package:ruta_fija_conductor/features/incidents/data/mobile_incident_repository.dart';
import 'package:ruta_fija_conductor/features/incidents/domain/mobile_incident_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';
import 'package:ruta_fija_conductor/features/qualification/data/mobile_qualification_repository.dart';
import 'package:ruta_fija_conductor/features/qualification/domain/mobile_qualification.dart';

MobileAuthSession testSession({
  String accessToken = 'access-token',
  String refreshToken = 'refresh-token',
}) {
  return MobileAuthSession(
    accessToken: accessToken,
    refreshToken: refreshToken,
    expiresIn: 900,
    refreshExpiresIn: 604800,
    accessScope: MobileAccessScope.operations,
    user: const MobileAuthenticatedUser(
      id: '11111111-1111-4111-8111-111111111111',
      driverId: '22222222-2222-4222-8222-222222222222',
      fullName: 'Conductora de prueba',
      role: 'CONDUCTOR',
    ),
  );
}

final class FakeMobileQualificationGateway
    implements MobileQualificationGateway {
  FakeMobileQualificationGateway({MobileQualification? state})
    : state =
          state ??
          MobileQualification(
            driverId: '22222222-2222-4222-8222-222222222222',
            version: 0,
            suspended: false,
            operationalEnabled: true,
            personalDocumentsStatus: 'CONFORME',
            blockers: const [],
            periods: const [],
            unitRequests: const [],
            observedAt: DateTime.utc(2026, 10, 18),
          );
  MobileQualification state;
  int reads = 0;
  int unitWrites = 0;
  int documentWrites = 0;
  MobileQualificationDocumentInput? lastDocument;
  Object? readFailure;
  Object? mutationFailure;
  @override
  Future<MobileQualification> qualification() async {
    reads++;
    if (readFailure != null) throw readFailure!;
    return state;
  }

  @override
  Future<List<MobilePersonalRequirement>> personalCatalog() async => const [
    MobilePersonalRequirement(
      id: 'CON-02',
      title: 'Licencia',
      mandatory: true,
      fieldKeys: ['licenseNumber', 'licenseCategory'],
    ),
  ];
  @override
  Future<MobileQualification> requestUnit({
    required int expectedVersion,
    required String operationKey,
    required String kind,
    required String reference,
    String? vehicleId,
  }) async {
    unitWrites++;
    if (mutationFailure != null) throw mutationFailure!;
    return state;
  }

  @override
  Future<MobileQualification> presentDocument({
    required int expectedVersion,
    required String operationKey,
    required MobileQualificationDocumentInput document,
  }) async {
    documentWrites++;
    lastDocument = document;
    if (mutationFailure != null) throw mutationFailure!;
    return state;
  }
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
    this.loginChallenge,
    this.refreshFailure,
    this.loginFailure,
    this.verificationFailure,
  }) : session = session ?? testSession();

  MobileAuthSession session;
  final MobileEmailOtpChallenge? loginChallenge;
  Object? refreshFailure;
  Object? loginFailure;
  Object? verificationFailure;
  final List<String> refreshedTokens = [];
  final List<String> loggedOutTokens = [];
  final List<String> verifiedCodes = [];
  final List<String> verificationTokens = [];
  int loginCalls = 0;

  @override
  Future<MobileAuthLoginResult> login({
    required String email,
    required String password,
  }) async {
    loginCalls++;
    if (loginFailure != null) {
      throw loginFailure!;
    }
    final challenge = loginChallenge;
    if (challenge != null) {
      return MobileAuthLoginResult.verificationRequired(challenge);
    }
    return MobileAuthLoginResult.authenticated(session);
  }

  @override
  Future<MobileAuthSession> verifyEmailOtp({
    required String verificationToken,
    required String code,
  }) async {
    verificationTokens.add(verificationToken);
    verifiedCodes.add(code);
    if (verificationFailure != null) {
      throw verificationFailure!;
    }
    return session;
  }

  @override
  Future<MobileEmailOtpChallenge> resendEmailOtp(
    String verificationToken,
  ) async {
    verificationTokens.add(verificationToken);
    return loginChallenge ??
        MobileEmailOtpChallenge(
          verificationToken: verificationToken,
          otpExpiresAt: DateTime.utc(2026, 10, 14, 12, 5),
          challengeExpiresAt: DateTime.utc(2026, 10, 14, 12, 30),
          emailSent: true,
        );
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

final class FakeMobileConductorRecoveryGateway
    implements MobileConductorRecoveryGateway {
  FakeMobileConductorRecoveryGateway({
    this.otpResult = const MobileConductorRecoveryOtpResult(
      passwordResetRequired: true,
      passwordResetToken: 'synthetic-reset-token',
    ),
  });

  final MobileConductorRecoveryOtpResult otpResult;
  final List<(String, String, String)> otpVerifications = [];
  final List<(String, String, String)> passwordResets = [];

  @override
  Future<MobileConductorRecoveryOtpResult> verifyOtp({
    required String recoveryId,
    required String verificationToken,
    required String code,
  }) async {
    otpVerifications.add((recoveryId, verificationToken, code));
    return otpResult;
  }

  @override
  Future<void> resetPassword({
    required String recoveryId,
    required String passwordResetToken,
    required String newPassword,
  }) async {
    passwordResets.add((recoveryId, passwordResetToken, newPassword));
  }
}

final class FakeMobileConductorOnboardingGateway
    implements MobileConductorOnboardingGateway {
  FakeMobileConductorOnboardingGateway({
    MobileConductorOnboardingChallenge? challenge,
    MobileConductorOnboardingAccepted? accepted,
  }) : challenge =
           challenge ??
           MobileConductorOnboardingChallenge(
             verificationToken: 'synthetic-onboarding-token',
             otpExpiresAt: DateTime.utc(2026, 10, 15, 18, 5),
             challengeExpiresAt: DateTime.utc(2026, 10, 15, 18, 30),
             emailSent: true,
           ),
       accepted =
           accepted ??
           const MobileConductorOnboardingAccepted(
             requestId: 'synthetic-request-id',
             status: 'PENDING_REVIEW',
           );

  final MobileConductorOnboardingChallenge challenge;
  final MobileConductorOnboardingAccepted accepted;
  final List<Map<String, Object?>> starts = [];
  final List<String> resendTokens = [];
  final List<(String, String)> verifications = [];

  @override
  Future<MobileConductorOnboardingChallenge> start({
    required String email,
    required String password,
    required String fullName,
    String? phone,
    required String invitationCode,
    required String idempotencyKey,
  }) async {
    starts.add({
      'email': email,
      'password': password,
      'fullName': fullName,
      'phone': phone,
      'invitationCode': invitationCode,
      'idempotencyKey': idempotencyKey,
    });
    return challenge;
  }

  @override
  Future<MobileConductorOnboardingChallenge> resendOtp(
    String verificationToken,
  ) async {
    resendTokens.add(verificationToken);
    return challenge;
  }

  @override
  Future<MobileConductorOnboardingAccepted> verifyOtp({
    required String verificationToken,
    required String code,
  }) async {
    verifications.add((verificationToken, code));
    return accepted;
  }
}

final class FakeMobileApplicantAccessGateway
    implements MobileApplicantAccessGateway {
  FakeMobileApplicantAccessGateway({this.expiresAt, this.requestsFailure});

  final DateTime? expiresAt;
  Object? requestsFailure;
  int loginCalls = 0;
  int requestCalls = 0;
  final List<String> loggedOutTokens = [];
  static const token = 'oa1.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ';

  @override
  Future<MobileApplicantAccess> login({
    required String email,
    required String password,
  }) async {
    loginCalls++;
    return MobileApplicantAccess(
      token: token,
      expiresAt: expiresAt ?? DateTime.now().add(const Duration(minutes: 30)),
    );
  }

  @override
  Future<List<MobileApplicantRequest>> requests(String token) async {
    requestCalls++;
    if (requestsFailure != null) throw requestsFailure!;
    return [
      MobileApplicantRequest(
        requestId: 'synthetic-request-id',
        organizationId: 'synthetic-organization-id',
        status: 'PENDING_REVIEW',
        reviewRounds: 0,
        submittedAt: DateTime.utc(2026, 10, 15),
      ),
    ];
  }

  @override
  Future<void> logout(String token) async => loggedOutTokens.add(token);
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
             locationConsent: false,
             primaryVehicle: MobileVehicle(
               plate: 'ABC-123',
               status: 'DISPONIBLE',
             ),
           ),
       currentAvailability =
           availability ??
           MobileAvailability(
             driverId: '22222222-2222-4222-8222-222222222222',
             status: MobileAvailabilityStatus.disponible,
             updatedAt: DateTime.utc(2026, 9, 25, 12),
           );

  MobileDriverProfile profileValue;
  MobileAvailability currentAvailability;
  Object? failure;
  final List<MobileAvailabilityStatus> changedTo = [];
  final List<bool> locationConsentChanges = [];
  final List<MobileCurrentLocation> uploadedLocations = [];
  int clearLocationCalls = 0;

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
      updatedAt: currentAvailability.updatedAt.add(const Duration(seconds: 1)),
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

  @override
  Future<MobileDriverProfile> changeLocationConsent(bool consent) async {
    if (failure != null) {
      throw failure!;
    }
    locationConsentChanges.add(consent);
    profileValue = MobileDriverProfile(
      driverId: profileValue.driverId,
      fullName: profileValue.fullName,
      groupName: profileValue.groupName,
      availabilityStatus: profileValue.availabilityStatus,
      locationConsent: consent,
      primaryVehicle: profileValue.primaryVehicle,
    );
    return profileValue;
  }

  @override
  Future<void> upsertCurrentLocation(MobileCurrentLocation location) async {
    if (failure != null) {
      throw failure!;
    }
    uploadedLocations.add(location);
  }

  @override
  Future<void> clearCurrentLocation() async {
    if (failure != null) {
      throw failure!;
    }
    clearLocationCalls++;
  }
}

final class FakeMobileDeviceLocationGateway
    implements MobileDeviceLocationGateway {
  bool serviceEnabled;
  MobileDeviceLocationPermission currentPermission;
  MobileDeviceLocationPermission requestResult;
  MobileCurrentLocation nextLocation;
  Object? failure;
  int permissionRequests = 0;
  int locationSettingsOpenCalls = 0;
  int appSettingsOpenCalls = 0;
  int locationReads = 0;

  FakeMobileDeviceLocationGateway({
    this.serviceEnabled = true,
    this.currentPermission = MobileDeviceLocationPermission.whileInUse,
    this.requestResult = MobileDeviceLocationPermission.whileInUse,
    MobileCurrentLocation? nextLocation,
  }) : nextLocation =
           nextLocation ??
           MobileCurrentLocation(
             latitude: -12.046374,
             longitude: -77.042793,
             accuracyMeters: 8.2,
             capturedAt: DateTime.utc(2026, 9, 25, 12),
           );

  @override
  Future<MobileDeviceLocationPermission> checkPermission() async {
    _throwIfNeeded();
    return currentPermission;
  }

  @override
  Future<MobileCurrentLocation> currentPosition() async {
    _throwIfNeeded();
    locationReads++;
    return nextLocation;
  }

  @override
  Future<bool> isLocationServiceEnabled() async {
    _throwIfNeeded();
    return serviceEnabled;
  }

  @override
  Future<bool> openAppSettings() async {
    appSettingsOpenCalls++;
    return true;
  }

  @override
  Future<bool> openLocationSettings() async {
    locationSettingsOpenCalls++;
    return true;
  }

  @override
  Future<MobileDeviceLocationPermission> requestPermission() async {
    _throwIfNeeded();
    permissionRequests++;
    currentPermission = requestResult;
    return currentPermission;
  }

  void _throwIfNeeded() {
    if (failure != null) {
      throw failure!;
    }
  }
}

MobileAssignment testAssignment({
  String? serviceId,
  String? handoverId,
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
    serviceId: serviceId,
    handoverId: handoverId,
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
  Future<void> clearAll() async {
    _values.clear();
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

MobileOfflineSyncController testOfflineSyncController() {
  return MobileOfflineSyncController(
    MobileOfflineOutbox(store: InMemoryOfflineQueueStore()),
    const FakeMobileOfflineSyncTransport(),
  );
}

final class InMemoryOfflineQueueStore implements MobileOfflineQueueStore {
  final Map<String, List<MobileOfflineEvent>> _events = {};

  @override
  Future<void> clear(MobileOfflineQueueOwner owner) async {
    _events.remove(owner.storageScope);
  }

  @override
  Future<void> clearAll() async {
    _events.clear();
  }

  @override
  Future<List<MobileOfflineEvent>> read(MobileOfflineQueueOwner owner) async {
    return List.unmodifiable(_events[owner.storageScope] ?? const []);
  }

  @override
  Future<void> replace(
    MobileOfflineQueueOwner owner,
    List<MobileOfflineEvent> events,
  ) async {
    _events[owner.storageScope] = List.of(events);
  }
}

final class FakeMobileOfflineSyncTransport
    implements MobileOfflineSyncTransport {
  const FakeMobileOfflineSyncTransport();

  @override
  Future<MobileOfflineServerResult> send(MobileOfflineEvent event) async {
    return MobileOfflineServerResult(
      eventId: event.eventId,
      status: MobileOfflineServerResultStatus.applied,
    );
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

MobileIncident testIncident({
  String id = '44444444-4444-4444-8444-444444444444',
  MobileIncidentCategory category = MobileIncidentCategory.averia,
  MobileIncidentStatus status = MobileIncidentStatus.open,
  MobileIncidentSource source = MobileIncidentSource.mobileApp,
  String reportedById = '11111111-1111-4111-8111-111111111111',
  String reportedByName = 'Conductora de prueba',
  String description = 'El vehículo presenta una alerta de prueba.',
}) {
  final now = DateTime.utc(2026, 9, 24, 14);
  final wasClosedByDriver = status == MobileIncidentStatus.driverClosed;
  return MobileIncident(
    id: id,
    version: 0,
    driverId: '22222222-2222-4222-8222-222222222222',
    driverName: 'Conductora de prueba',
    assignmentId: '33333333-3333-4333-8333-333333333333',
    category: category,
    source: source,
    status: status,
    description: description,
    reportedById: reportedById,
    reportedByName: reportedByName,
    reportedAt: now,
    followUpNote: null,
    followedUpByName: null,
    followedUpAt: null,
    resolvedAt: null,
    driverClosedById: wasClosedByDriver ? reportedById : null,
    driverClosedByName: wasClosedByDriver ? reportedByName : null,
    driverClosedAt: wasClosedByDriver
        ? now.add(const Duration(minutes: 1))
        : null,
    createdAt: now,
    updatedAt: now,
  );
}

final class FakeMobileIncidentGateway implements MobileIncidentGateway {
  FakeMobileIncidentGateway({List<MobileIncident>? incidents})
    : _incidents = incidents ?? [testIncident()];

  final List<MobileIncident> _incidents;
  final List<MobileIncidentCategory> createdCategories = [];
  final List<String> createdDescriptions = [];
  final List<String?> createdAssignmentIds = [];
  final List<String> closedIncidentIds = [];
  Object? failure;

  @override
  Future<MobileIncident> create({
    required MobileIncidentCategory category,
    required String description,
    required MobileIncidentSeverity severity,
    String? assignmentId,
    String? operationKey,
    DateTime? occurredAt,
    String? previousIncidentId,
    String? relationReason,
  }) async {
    _throwIfNeeded();
    createdCategories.add(category);
    createdDescriptions.add(description);
    createdAssignmentIds.add(assignmentId);
    final created = testIncident(
      id: '55555555-5555-4555-8555-555555555555',
      category: category,
      description: description.trim(),
    );
    _incidents.insert(0, created);
    return created;
  }

  @override
  Future<MobileIncident> get(String incidentId) async {
    _throwIfNeeded();
    return _incidents.firstWhere((item) => item.id == incidentId);
  }

  @override
  Future<MobileIncident> close(
    String incidentId, {
    required int version,
  }) async {
    _throwIfNeeded();
    final index = _incidents.indexWhere((item) => item.id == incidentId);
    if (index < 0) {
      throw StateError('La incidencia no existe.');
    }
    final incident = _incidents[index];
    if (incident.wasClosedByDriver) {
      return incident;
    }
    if (version != incident.version ||
        !incident.canBeClosedBy('11111111-1111-4111-8111-111111111111')) {
      throw StateError('La incidencia no puede cerrarse.');
    }
    final closed = incident.withDriverClosure(
      actorId: '11111111-1111-4111-8111-111111111111',
      actorName: 'Conductora de prueba',
      closedAt: DateTime.utc(2026, 9, 24, 15),
    );
    _incidents[index] = closed;
    closedIncidentIds.add(incidentId);
    return closed;
  }

  @override
  Future<MobileIncidentPage> list({int page = 0, int size = 20}) async {
    _throwIfNeeded();
    return MobileIncidentPage(
      items: _incidents,
      page: page,
      size: size,
      totalItems: _incidents.length,
      totalPages: _incidents.isEmpty ? 0 : 1,
    );
  }

  void _throwIfNeeded() {
    if (failure != null) {
      throw failure!;
    }
  }

  @override
  Future<List<MobileIncidentAction>> history(String incidentId) async {
    _throwIfNeeded();
    return [];
  }

  @override
  Future<MobileIncidentOperation> operation(String operationKey) async {
    _throwIfNeeded();
    throw StateError('No synthetic operation receipt was prepared.');
  }

  @override
  Future<MobileIncident> information(
    String incidentId, {
    required int version,
    required String operationKey,
    required String note,
  }) async {
    _throwIfNeeded();
    throw StateError('No synthetic information command was prepared.');
  }
}

MobileAnnouncement testAnnouncement({
  String id = '66666666-6666-4666-8666-666666666666',
  bool requireReadAck = true,
  DateTime? readAt,
}) {
  return MobileAnnouncement(
    id: id,
    title: 'Comunicado de prueba',
    body: 'Revise esta información operativa antes de iniciar el servicio.',
    requireReadAck: requireReadAck,
    createdAt: DateTime.utc(2026, 9, 24, 14),
    deliveredAt: DateTime.utc(2026, 9, 24, 14, 1),
    readAt: readAt,
  );
}

final class FakeMobileAnnouncementGateway implements MobileAnnouncementGateway {
  FakeMobileAnnouncementGateway({List<MobileAnnouncement>? announcements})
    : _announcements = announcements ?? [testAnnouncement()];

  final List<MobileAnnouncement> _announcements;
  final List<String> markedReadIds = [];
  Object? failure;

  @override
  Future<MobileAnnouncement> get(String announcementId) async {
    _throwIfNeeded();
    return _announcements.firstWhere((item) => item.id == announcementId);
  }

  @override
  Future<MobileAnnouncementPage> list({int page = 0, int size = 20}) async {
    _throwIfNeeded();
    return MobileAnnouncementPage(
      items: _announcements,
      page: page,
      size: size,
      totalItems: _announcements.length,
      totalPages: _announcements.isEmpty ? 0 : 1,
    );
  }

  @override
  Future<MobileAnnouncementReadResult> markRead(String announcementId) async {
    _throwIfNeeded();
    markedReadIds.add(announcementId);
    final index = _announcements.indexWhere(
      (item) => item.id == announcementId,
    );
    final readAt =
        _announcements[index].readAt ?? DateTime.utc(2026, 9, 24, 15);
    _announcements[index] = _announcements[index].withReadAt(readAt);
    return MobileAnnouncementReadResult(
      announcementId: announcementId,
      deliveredAt: _announcements[index].deliveredAt,
      readAt: readAt,
    );
  }

  void _throwIfNeeded() {
    if (failure != null) {
      throw failure!;
    }
  }
}
