import 'package:http/http.dart' as http;

import '../core/network/mobile_api_client.dart';
import '../core/session/mobile_secure_token_store.dart';
import '../core/session/mobile_session_controller.dart';
import '../features/announcements/data/mobile_announcement_repository.dart';
import '../features/assignments/data/mobile_assignment_command_store.dart';
import '../features/assignments/data/mobile_assignment_repository.dart';
import '../features/auth/data/mobile_auth_repository.dart';
import '../features/incidents/data/mobile_incident_repository.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import 'ruta_fija_environment.dart';

/// Composition root: the mobile client reaches Spring Boot only through HTTPS/HTTP APIs.
final class RutaFijaDependencies {
  RutaFijaDependencies({
    required this.sessionController,
    required this.driverGateway,
    required this.assignmentGateway,
    required this.incidentGateway,
    required this.announcementGateway,
    required this.httpClient,
  });

  factory RutaFijaDependencies.create(RouteFijaEnvironment environment) {
    final httpClient = http.Client();
    final publicApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
    );
    final sessionController = MobileSessionController(
      authGateway: MobileAuthRepository(publicApi),
      tokenStore: MobileSecureTokenStore(),
    );
    final authenticatedApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
      accessTokenProvider: sessionController,
    );
    final assignmentGateway = MobileAssignmentRepository(
      authenticatedApi,
      MobileSecureAssignmentCommandStore(),
    );

    return RutaFijaDependencies(
      sessionController: sessionController,
      driverGateway: MobileDriverRepository(authenticatedApi),
      assignmentGateway: assignmentGateway,
      incidentGateway: MobileIncidentRepository(authenticatedApi),
      announcementGateway: MobileAnnouncementRepository(authenticatedApi),
      httpClient: httpClient,
    );
  }

  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;
  final http.Client httpClient;

  void dispose() {
    sessionController.dispose();
    httpClient.close();
  }
}
