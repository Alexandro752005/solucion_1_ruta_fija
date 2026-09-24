import 'package:flutter/material.dart';

import '../../../app/ruta_fija_environment.dart';
import '../../../core/navigation/conductor_destination.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
import '../../announcements/data/mobile_announcement_repository.dart';
import '../../announcements/presentation/announcements_page.dart';
import '../../assignments/data/mobile_assignment_repository.dart';
import '../../assignments/presentation/assignments_page.dart';
import '../../incidents/data/mobile_incident_repository.dart';
import '../../incidents/presentation/incidents_page.dart';
import '../../profile/data/mobile_driver_repository.dart';
import '../../profile/presentation/availability_page.dart';
import '../../profile/presentation/conductor_home_page.dart';
import '../../profile/presentation/profile_page.dart';

class ConductorNavigationShell extends StatelessWidget {
  const ConductorNavigationShell({
    required this.destination,
    required this.environment,
    required this.sessionController,
    required this.driverGateway,
    required this.assignmentGateway,
    required this.incidentGateway,
    required this.announcementGateway,
    super.key,
  });

  final ConductorDestination destination;
  final RouteFijaEnvironment environment;
  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;

  Future<void> _logout(BuildContext context) async {
    try {
      await sessionController.logout();
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ruta Fija Conductor'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: () => _logout(context),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: switch (destination) {
            ConductorDestination.home => ConductorHomePage(
              driverGateway: driverGateway,
            ),
            ConductorDestination.availability => AvailabilityPage(
              driverGateway: driverGateway,
            ),
            ConductorDestination.assignments => AssignmentsPage(
              driverGateway: assignmentGateway,
              driverId: sessionController.state.user!.driverId,
            ),
            ConductorDestination.incidents => IncidentsPage(
              incidentGateway: incidentGateway,
            ),
            ConductorDestination.announcements => AnnouncementsPage(
              announcementGateway: announcementGateway,
            ),
            ConductorDestination.profile => ProfilePage(
              driverGateway: driverGateway,
            ),
          },
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: destination.index,
        onDestinationSelected: (index) {
          final next = ConductorDestination.values[index];
          if (next != destination) {
            Navigator.of(context).pushReplacementNamed(next.path);
          }
        },
        destinations: [
          for (final item in ConductorDestination.values)
            NavigationDestination(
              key: Key('nav-${item.name}'),
              icon: Icon(item.icon),
              label: item.label,
            ),
        ],
      ),
    );
  }
}
