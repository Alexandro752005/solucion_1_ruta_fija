import 'package:flutter/material.dart';

import '../core/session/mobile_session_controller.dart';
import '../core/navigation/conductor_destination.dart';
import '../features/auth/domain/mobile_auth_models.dart';
import '../features/announcements/data/mobile_announcement_repository.dart';
import '../features/announcements/data/mobile_notice_repository.dart';
import '../features/announcements/presentation/notices_page.dart';
import '../features/announcements/presentation/announcements_page.dart';
import '../features/incidents/presentation/incidents_page.dart';
import '../features/assignments/data/mobile_assignment_repository.dart';
import '../features/bootstrap/presentation/conductor_navigation_shell.dart';
import '../features/incidents/data/mobile_incident_repository.dart';
import '../features/location/domain/mobile_foreground_location.dart';
import '../features/location/presentation/foreground_location_page.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import '../features/qualification/data/mobile_qualification_repository.dart';
import '../features/qualification/presentation/mobile_qualification_gate.dart';
import '../features/qualification/presentation/mobile_qualification_page.dart';
import '../features/preferences/domain/mobile_preferences.dart';
import '../features/reports/domain/mobile_service_report.dart';
import '../features/reports/presentation/mobile_reports_page.dart';
import 'ruta_fija_environment.dart';

final class RutaFijaRouter {
  RutaFijaRouter._();

  static const initialRoute = '/';

  static Widget home({
    MobilePreferencesController? preferences,
    MobileServiceReportGateway? reportGateway,
    MobileReportFileGateway? reportFiles,
    MobileNoticeGateway? noticeGateway,
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
    required MobileAssignmentGateway assignmentGateway,
    required MobileIncidentGateway incidentGateway,
    required MobileAnnouncementGateway announcementGateway,
    required MobileQualificationGateway qualificationGateway,
  }) {
    return MobileQualificationGate(
      gateway: qualificationGateway,
      sessionController: sessionController,
      operationalBuilder: (_) => ConductorNavigationShell(
        destination: ConductorDestination.home,
        environment: environment,
        sessionController: sessionController,
        driverGateway: driverGateway,
        assignmentGateway: assignmentGateway,
        incidentGateway: incidentGateway,
        announcementGateway: announcementGateway,
        noticeGateway: noticeGateway,
        preferences: preferences,
        reportsAvailable: reportGateway != null && reportFiles != null,
      ),
    );
  }

  static Route<void> onGenerateRoute(
    RouteSettings settings, {
    MobilePreferencesController? preferences,
    MobileServiceReportGateway? reportGateway,
    MobileReportFileGateway? reportFiles,
    MobileNoticeGateway? noticeGateway,
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
    required MobileAssignmentGateway assignmentGateway,
    required MobileIncidentGateway incidentGateway,
    required MobileAnnouncementGateway announcementGateway,
    required MobileDeviceLocationGateway deviceLocationGateway,
    required MobileQualificationGateway qualificationGateway,
  }) {
    Widget guard(WidgetBuilder builder) => MobileQualificationGate(
      gateway: qualificationGateway,
      sessionController: sessionController,
      operationalBuilder: builder,
    );
    Widget shell(ConductorDestination destination) => ConductorNavigationShell(
      destination: destination,
      environment: environment,
      sessionController: sessionController,
      driverGateway: driverGateway,
      assignmentGateway: assignmentGateway,
      incidentGateway: incidentGateway,
      announcementGateway: announcementGateway,
      noticeGateway: noticeGateway,
      preferences: preferences,
      reportsAvailable: reportGateway != null && reportFiles != null,
    );
    // These routes use consultation APIs. Loss of operational eligibility must
    // not hide an already-approved member's own incidents or received messages.
    // Operational sessions keep the shared navigation while consulting them;
    // the other destinations still enforce their own qualification gate.
    if (settings.name == '/notices' && noticeGateway != null) {
      return MaterialPageRoute<void>(
        settings: settings,
        builder: (_) => NoticesPage(gateway: noticeGateway),
      );
    }
    if (settings.name == '/reports' &&
        reportGateway != null &&
        reportFiles != null) {
      return MaterialPageRoute<void>(
        settings: settings,
        builder: (_) =>
            MobileReportsPage(gateway: reportGateway, files: reportFiles),
      );
    }
    if (settings.name == '/announcements') {
      return MaterialPageRoute<void>(
        settings: settings,
        builder: (_) =>
            sessionController.state.accessScope == MobileAccessScope.operations
            ? shell(ConductorDestination.announcements)
            : Scaffold(
                appBar: AppBar(title: const Text('Mis comunicados')),
                body: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: AnnouncementsPage(
                      announcementGateway: announcementGateway,
                    ),
                  ),
                ),
              ),
      );
    }
    if (settings.name == '/incidents' && sessionController.state.user != null) {
      return MaterialPageRoute<void>(
        settings: settings,
        builder: (_) =>
            sessionController.state.accessScope == MobileAccessScope.operations
            ? shell(ConductorDestination.incidents)
            : Scaffold(
                appBar: AppBar(title: const Text('Mis incidencias')),
                body: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: IncidentsPage(
                      incidentGateway: incidentGateway,
                      userId: sessionController.state.user!.id,
                    ),
                  ),
                ),
              ),
      );
    }
    if (settings.name == '/qualification') {
      return MaterialPageRoute<void>(
        settings: const RouteSettings(name: '/qualification'),
        builder: (context) => MobileQualificationPage(
          gateway: qualificationGateway,
          sessionController: sessionController,
          onSessionRefreshed: () =>
              Navigator.of(context).pushNamedAndRemoveUntil('/', (_) => false),
        ),
      );
    }
    if (settings.name == '/location') {
      return MaterialPageRoute<void>(
        settings: const RouteSettings(name: '/location'),
        builder: (_) => guard(
          (_) => ForegroundLocationPage(
            driverGateway: driverGateway,
            deviceLocationGateway: deviceLocationGateway,
          ),
        ),
      );
    }
    final destination = ConductorDestination.fromPath(settings.name);
    return MaterialPageRoute<void>(
      settings: RouteSettings(name: destination.path),
      builder: (_) => guard((_) => shell(destination)),
    );
  }
}
