import 'dart:async';

import 'package:flutter/material.dart';

import '../../../app/ruta_fija_environment.dart';
import '../../../core/navigation/conductor_destination.dart';
import '../../../core/offline/mobile_offline.dart';
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

class ConductorNavigationShell extends StatefulWidget {
  const ConductorNavigationShell({
    required this.destination,
    required this.environment,
    required this.sessionController,
    required this.driverGateway,
    required this.assignmentGateway,
    required this.incidentGateway,
    required this.announcementGateway,
    required this.offlineSyncController,
    super.key,
  });

  final ConductorDestination destination;
  final RouteFijaEnvironment environment;
  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway assignmentGateway;
  final MobileIncidentGateway incidentGateway;
  final MobileAnnouncementGateway announcementGateway;
  final MobileOfflineSyncController offlineSyncController;

  @override
  State<ConductorNavigationShell> createState() =>
      _ConductorNavigationShellState();
}

class _ConductorNavigationShellState extends State<ConductorNavigationShell> {
  @override
  void initState() {
    super.initState();
    _bindOfflineQueue();
  }

  @override
  void didUpdateWidget(covariant ConductorNavigationShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.sessionController != widget.sessionController ||
        oldWidget.offlineSyncController != widget.offlineSyncController) {
      _bindOfflineQueue();
    }
  }

  void _bindOfflineQueue() {
    final user = widget.sessionController.state.user;
    if (user == null) {
      return;
    }
    unawaited(
      widget.offlineSyncController.bind(
        MobileOfflineQueueOwner(userId: user.id, driverId: user.driverId),
      ),
    );
  }

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

  void _openPendingSync(BuildContext context) {
    Navigator.of(context).pushNamed('/sync-pending');
  }

  String _pendingSyncLabel(int count) {
    if (count == 0) {
      return 'Abrir pendientes de sincronización. No hay acciones pendientes.';
    }
    final noun = count == 1 ? 'acción pendiente.' : 'acciones pendientes.';
    return 'Abrir pendientes de sincronización. $count $noun';
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.sessionController.state.user;
    if (user == null) {
      return const SizedBox.shrink();
    }
    final offlineOwner = MobileOfflineQueueOwner(
      userId: user.id,
      driverId: user.driverId,
    );
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ruta Fija Conductor'),
        actions: [
          AnimatedBuilder(
            animation: widget.offlineSyncController,
            builder: (context, _) {
              final count = widget.offlineSyncController.pendingCount;
              final label = _pendingSyncLabel(count);
              return Semantics(
                container: true,
                button: true,
                label: label,
                onTap: () => _openPendingSync(context),
                child: ExcludeSemantics(
                  child: IconButton(
                    key: const Key('pending-sync-open'),
                    tooltip: label,
                    onPressed: () => _openPendingSync(context),
                    icon: Badge(
                      isLabelVisible: count > 0,
                      label: Text('$count'),
                      child: const Icon(Icons.sync_outlined),
                    ),
                  ),
                ),
              );
            },
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
            ),
            ConductorDestination.availability => AvailabilityPage(
              driverGateway: widget.driverGateway,
              offlineSyncController: widget.offlineSyncController,
              offlineOwner: offlineOwner,
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
            ),
          },
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: widget.destination.index,
        onDestinationSelected: (index) {
          final next = ConductorDestination.values[index];
          if (next != widget.destination) {
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
