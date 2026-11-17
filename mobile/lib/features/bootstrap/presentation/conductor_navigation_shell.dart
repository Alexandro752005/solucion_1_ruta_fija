import 'package:flutter/material.dart';

import '../../../app/ruta_fija_environment.dart';
import '../../../core/navigation/conductor_destination.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
import '../../announcements/data/mobile_announcement_repository.dart';
import '../../announcements/data/mobile_notice_repository.dart';
import '../../announcements/presentation/announcements_page.dart';
import '../../assignments/data/mobile_assignment_repository.dart';
import '../../assignments/presentation/assignments_page.dart';
import '../../incidents/data/mobile_incident_repository.dart';
import '../../incidents/presentation/incidents_page.dart';
import '../../profile/data/mobile_driver_repository.dart';
import '../../profile/presentation/availability_page.dart';
import '../../profile/presentation/conductor_home_page.dart';
import '../../profile/presentation/profile_page.dart';
import '../../preferences/domain/mobile_preferences.dart';
import '../../location/presentation/mobile_map_page.dart';

class ConductorNavigationShell extends StatefulWidget {
  const ConductorNavigationShell({
    required this.destination,
    required this.environment,
    required this.sessionController,
    required this.driverGateway,
    required this.assignmentGateway,
    required this.incidentGateway,
    required this.announcementGateway,
    this.noticeGateway,
    this.preferences,
    this.reportsAvailable = false,
    super.key,
  });

  final ConductorDestination destination;
  final RouteFijaEnvironment environment;
  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;
  final MobileNoticeGateway? noticeGateway;
  final MobilePreferencesController? preferences;
  final bool reportsAvailable;

  @override
  State<ConductorNavigationShell> createState() =>
      _ConductorNavigationShellState();
}

class _ConductorNavigationShellState extends State<ConductorNavigationShell> {
  Future<void> _logout(BuildContext context) async {
    try {
      await widget.sessionController.logout();
    } catch (error) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.sessionController.state.user;
    if (user == null) {
      return const SizedBox.shrink();
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ruta Fija Conductor'),
        actions: [
          if (widget.noticeGateway != null)
            IconButton(
              key: const Key('notices-open'),
              tooltip: 'Bandeja de avisos',
              onPressed: () => Navigator.of(context).pushNamed('/notices'),
              icon: const Icon(Icons.notifications_outlined),
            ),
          IconButton(
            key: const Key('qualification-open'),
            tooltip: 'Mi incorporación',
            onPressed: () => Navigator.of(context).pushNamed('/qualification'),
            icon: const Icon(Icons.fact_check_outlined),
          ),
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
          child: switch (widget.destination) {
            ConductorDestination.home => ConductorHomePage(
              driverGateway: widget.driverGateway,
              assignmentGateway: widget.assignmentGateway,
              preferences: widget.preferences,
            ),
            ConductorDestination.availability => AvailabilityPage(
              driverGateway: widget.driverGateway,
            ),
            ConductorDestination.assignments => AssignmentsPage(
              driverGateway: widget.assignmentGateway,
              driverId: user.driverId,
            ),
            ConductorDestination.incidents => IncidentsPage(
              incidentGateway: widget.incidentGateway,
              userId: user.id,
            ),
            ConductorDestination.announcements => AnnouncementsPage(
              announcementGateway: widget.announcementGateway,
            ),
            ConductorDestination.profile => ProfilePage(
              driverGateway: widget.driverGateway,
              preferences: widget.preferences,
              reportsAvailable: widget.reportsAvailable,
            ),
            ConductorDestination.map => const MobileMapPage(),
            ConductorDestination.more => _MoreDestinations(),
          },
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: widget.destination.primaryIndex,
        onDestinationSelected: (index) {
          final next = ConductorDestination.primary[index];
          if (next != widget.destination) {
            Navigator.of(context).pushReplacementNamed(next.path);
          }
        },
        destinations: [
          for (final item in ConductorDestination.primary)
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

class _MoreDestinations extends StatelessWidget {
  @override
  Widget build(BuildContext context) => ListView(
    children: [
      Text('Más opciones', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 12),
      for (final item in ConductorDestination.additional)
        Card(
          child: ListTile(
            key: Key('more-${item.name}'),
            leading: Icon(item.icon),
            title: Text(item.label),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).pushReplacementNamed(item.path),
          ),
        ),
    ],
  );
}
