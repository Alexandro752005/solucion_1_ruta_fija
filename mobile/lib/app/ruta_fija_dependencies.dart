import 'package:http/http.dart' as http;
import 'package:flutter/widgets.dart';

import '../core/network/mobile_api_client.dart';
import '../core/session/mobile_secure_token_store.dart';
import '../core/session/mobile_session_controller.dart';
import '../features/announcements/data/mobile_announcement_repository.dart';
import '../features/announcements/data/mobile_notice_repository.dart';
import '../features/assignments/data/mobile_assignment_command_store.dart';
import '../features/assignments/data/mobile_assignment_repository.dart';
import '../features/auth/data/mobile_auth_repository.dart';
import '../features/auth/data/mobile_applicant_access_repository.dart';
import '../features/auth/data/mobile_conductor_recovery_repository.dart';
import '../features/auth/data/mobile_conductor_onboarding_repository.dart';
import '../features/incidents/data/mobile_incident_repository.dart';
import '../features/location/data/geolocator_device_location_gateway.dart';
import '../features/location/domain/mobile_foreground_location.dart';
import '../features/location/domain/mobile_execution_location_gate.dart';
import '../features/location/domain/mobile_service_location_controller.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import '../features/qualification/data/mobile_qualification_repository.dart';
import '../features/preferences/data/mobile_preferences_repository.dart';
import '../features/preferences/domain/mobile_preferences.dart';
import '../features/reports/data/mobile_service_report_repository.dart';
import '../features/reports/data/android_report_file_gateway.dart';
import '../features/reports/domain/mobile_service_report.dart';
import 'ruta_fija_environment.dart';

/// Composition root: the mobile client reaches Spring Boot only through HTTPS/HTTP APIs.
final class RutaFijaDependencies {
  RutaFijaDependencies({
    required this.sessionController,
    required this.conductorRecoveryGateway,
    required this.conductorOnboardingGateway,
    required this.applicantAccessGateway,
    required this.driverGateway,
    required this.qualificationGateway,
    required this.assignmentGateway,
    required this.incidentGateway,
    required this.announcementGateway,
    required this.deviceLocationGateway,
    required this.httpClient,
    this.serviceLocation,
    this.noticeGateway,
    this.preferences,
    this.reportGateway,
    this.reportFiles,
  });

  factory RutaFijaDependencies.create(RouteFijaEnvironment environment) {
    final httpClient = http.Client();
    final publicApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
    );
    final assignmentCommandStore = MobileSessionAssignmentCommandStore();
    final sessionController = MobileSessionController(
      authGateway: MobileAuthRepository(publicApi),
      tokenStore: MobileSecureTokenStore(),
      onLocalSessionCleared: (user) async {
        await assignmentCommandStore.clearAll();
      },
    );
    final authenticatedApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
      accessTokenProvider: sessionController,
    );
    final deviceLocationGateway = GeolocatorDeviceLocationGateway();
    final executionLocationGate = MobileExecutionLocationGate(
      device: deviceLocationGateway,
      isForeground: () =>
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed,
    );
    MobileServiceLocationController? serviceLocation;
    final driverGateway = MobileDriverRepository(authenticatedApi);
    final assignmentGateway = MobileAssignmentRepository(
      authenticatedApi,
      assignmentCommandStore,
      enableInMemoryRetryQueue: false,
      startGpsProofProvider: executionLocationGate.beforeStart,
      beforeComplete: (a) async {
        await serviceLocation?.beforeComplete(a);
      },
      onStateChanged: (a) => serviceLocation?.observe(a),
    );
    serviceLocation = MobileServiceLocationController(
      assignments: assignmentGateway,
      driver: driverGateway,
      device: deviceLocationGateway,
    );

    return RutaFijaDependencies(
      sessionController: sessionController,
      conductorRecoveryGateway: MobileConductorRecoveryRepository(publicApi),
      conductorOnboardingGateway: MobileConductorOnboardingRepository(
        publicApi,
      ),
      applicantAccessGateway: MobileApplicantAccessRepository(publicApi),
      driverGateway: driverGateway,
      qualificationGateway: MobileQualificationRepository(authenticatedApi),
      assignmentGateway: assignmentGateway,
      incidentGateway: MobileIncidentRepository(authenticatedApi),
      announcementGateway: MobileAnnouncementRepository(authenticatedApi),
      noticeGateway: MobileNoticeRepository(authenticatedApi),
      deviceLocationGateway: deviceLocationGateway,
      httpClient: httpClient,
      serviceLocation: serviceLocation,
      preferences:MobilePreferencesController(MobilePreferencesRepository(authenticatedApi)),
      reportGateway:MobileServiceReportRepository(authenticatedApi),
      reportFiles:const AndroidReportFileGateway(),
    );
  }

  final MobileSessionController sessionController;
  final MobileConductorRecoveryGateway conductorRecoveryGateway;
  final MobileConductorOnboardingGateway conductorOnboardingGateway;
  final MobileApplicantAccessGateway applicantAccessGateway;
  final MobileDriverGateway driverGateway;
  final MobileQualificationGateway qualificationGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;
  final MobileNoticeGateway? noticeGateway;
  final MobileDeviceLocationGateway deviceLocationGateway;
  final http.Client httpClient;
  final MobileServiceLocationController? serviceLocation;
  final MobilePreferencesController? preferences;
  final MobileServiceReportGateway? reportGateway;
  final MobileReportFileGateway? reportFiles;

  void dispose() {
    serviceLocation?.dispose();
    preferences?.dispose();
    sessionController.dispose();
    httpClient.close();
  }
}
