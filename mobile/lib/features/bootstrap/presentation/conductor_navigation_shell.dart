import 'package:flutter/material.dart';

import '../../../app/ruta_fija_environment.dart';
import '../../../core/navigation/conductor_destination.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
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
    super.key,
  });

  final ConductorDestination destination;
  final RouteFijaEnvironment environment;
  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;

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
            ConductorDestination.profile => ProfilePage(
              driverGateway: driverGateway,
            ),
            _ => _PendingModule(destination: destination),
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

class _PendingModule extends StatelessWidget {
  const _PendingModule({required this.destination});

  final ConductorDestination destination;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(destination.icon, size: 36),
                const SizedBox(height: 16),
                Text(
                  '${destination.milestone} — ${destination.label}',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                const Text('Pendiente de construcción'),
                const SizedBox(height: 12),
                const Text(
                  'Esta ruta existe para validar la navegación M1. No expone acciones, '
                  'datos locales ni respuestas simuladas antes de su fase funcional.',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
