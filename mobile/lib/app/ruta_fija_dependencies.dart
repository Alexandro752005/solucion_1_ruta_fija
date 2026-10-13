import 'package:http/http.dart' as http;

import '../core/network/mobile_api_client.dart';
import '../core/offline/mobile_offline.dart';
import '../core/session/mobile_secure_token_store.dart';
import '../core/session/mobile_session_controller.dart';
import '../features/announcements/data/mobile_announcement_repository.dart';
import '../features/assignments/data/mobile_assignment_command_store.dart';
import '../features/assignments/data/mobile_assignment_repository.dart';
import '../features/auth/data/mobile_auth_repository.dart';
import '../features/incidents/data/mobile_incident_repository.dart';
import '../features/location/data/geolocator_device_location_gateway.dart';
import '../features/location/domain/mobile_foreground_location.dart';
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
    required this.deviceLocationGateway,
    required this.offlineSyncController,
    required this.httpClient,
  });

  factory RutaFijaDependencies.create(RouteFijaEnvironment environment) {
    final httpClient = http.Client();
    final publicApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
    );
    final assignmentCommandStore = MobileSecureAssignmentCommandStore();
    final offlineOutbox = MobileOfflineOutbox(
      store: MobileSecureOfflineQueueStore(),
    );
    final sessionController = MobileSessionController(
      authGateway: MobileAuthRepository(publicApi),
      tokenStore: MobileSecureTokenStore(),
      onLocalSessionCleared: (user) async {
        if (user == null) {
          await offlineOutbox.clearAll();
        } else {
          await offlineOutbox.clear(
            MobileOfflineQueueOwner(userId: user.id, driverId: user.driverId),
          );
        }
        await assignmentCommandStore.clearAll();
      },
    );
    final authenticatedApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
      accessTokenProvider: sessionController,
    );
    final offlineSyncController = MobileOfflineSyncController(
      offlineOutbox,
      MobileApiOfflineSyncTransport(authenticatedApi),
    );
    final assignmentGateway = MobileAssignmentRepository(
      authenticatedApi,
      assignmentCommandStore,
      offlineSyncController: offlineSyncController,
      offlineOwnerProvider: () {
        final user = sessionController.state.user;
        if (user == null) {
          throw StateError('No hay una sesion de conductor para esta accion.');
        }
        return MobileOfflineQueueOwner(
          userId: user.id,
          driverId: user.driverId,
        );
      },
    );

    return RutaFijaDependencies(
      sessionController: sessionController,
      driverGateway: MobileDriverRepository(authenticatedApi),
      assignmentGateway: assignmentGateway,
      incidentGateway: MobileIncidentRepository(authenticatedApi),
      announcementGateway: MobileAnnouncementRepository(authenticatedApi),
      deviceLocationGateway: GeolocatorDeviceLocationGateway(),
      offlineSyncController: offlineSyncController,
      httpClient: httpClient,
    );
  }

  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;
  final MobileDeviceLocationGateway deviceLocationGateway;
  final MobileOfflineSyncController offlineSyncController;
  final http.Client httpClient;

  void dispose() {
    offlineSyncController.dispose();
    sessionController.dispose();
    httpClient.close();
  }
}
