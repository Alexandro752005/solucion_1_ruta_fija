import 'package:flutter/material.dart';

import '../core/offline/mobile_offline.dart';
import '../core/session/mobile_session_controller.dart';
import '../core/navigation/conductor_destination.dart';
import '../features/announcements/data/mobile_announcement_repository.dart';
import '../features/assignments/data/mobile_assignment_repository.dart';
import '../features/bootstrap/presentation/conductor_navigation_shell.dart';
import '../features/incidents/data/mobile_incident_repository.dart';
import '../features/location/domain/mobile_foreground_location.dart';
import '../features/location/presentation/foreground_location_page.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import '../features/sync/presentation/pending_sync_page.dart';
import 'ruta_fija_environment.dart';

final class RutaFijaRouter {
  RutaFijaRouter._();

  static const initialRoute = '/';

  static Widget home({
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
    required MobileAssignmentGateway assignmentGateway,
    required MobileIncidentGateway incidentGateway,
    required MobileAnnouncementGateway announcementGateway,
    required MobileOfflineSyncController offlineSyncController,
  }) {
    return ConductorNavigationShell(
      destination: ConductorDestination.home,
      environment: environment,
      sessionController: sessionController,
      driverGateway: driverGateway,
      assignmentGateway: assignmentGateway,
      incidentGateway: incidentGateway,
      announcementGateway: announcementGateway,
      offlineSyncController: offlineSyncController,
    );
  }

  static Route<void> onGenerateRoute(
    RouteSettings settings, {
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
    required MobileAssignmentGateway assignmentGateway,
    required MobileIncidentGateway incidentGateway,
    required MobileAnnouncementGateway announcementGateway,
    required MobileDeviceLocationGateway deviceLocationGateway,
    required MobileOfflineSyncController offlineSyncController,
  }) {
    if (settings.name == '/sync-pending') {
      final user = sessionController.state.user;
      if (user == null) {
        throw StateError(
          'No existe una sesion de conductor para ver pendientes.',
        );
      }
      return MaterialPageRoute<void>(
        settings: const RouteSettings(name: '/sync-pending'),
        builder: (_) => PendingSyncPage(
          offlineSyncController: offlineSyncController,
          offlineOwner: MobileOfflineQueueOwner(
            userId: user.id,
            driverId: user.driverId,
          ),
        ),
      );
    }
    if (settings.name == '/location') {
      return MaterialPageRoute<void>(
        settings: const RouteSettings(name: '/location'),
        builder: (_) => ForegroundLocationPage(
          driverGateway: driverGateway,
          deviceLocationGateway: deviceLocationGateway,
        ),
      );
    }
    final destination = ConductorDestination.fromPath(settings.name);
    return MaterialPageRoute<void>(
      settings: RouteSettings(name: destination.path),
      builder: (_) => ConductorNavigationShell(
        destination: destination,
        environment: environment,
        sessionController: sessionController,
        driverGateway: driverGateway,
        assignmentGateway: assignmentGateway,
        incidentGateway: incidentGateway,
        announcementGateway: announcementGateway,
        offlineSyncController: offlineSyncController,
      ),
    );
  }
}
