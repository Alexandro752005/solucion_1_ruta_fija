import 'package:flutter/material.dart';

import '../../../app/ruta_fija_environment.dart';
import '../../../core/navigation/conductor_destination.dart';

class ConductorNavigationShell extends StatelessWidget {
  const ConductorNavigationShell({
    required this.destination,
    required this.environment,
    super.key,
  });

  final ConductorDestination destination;
  final RouteFijaEnvironment environment;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ruta Fija Conductor')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: destination == ConductorDestination.home
              ? _F41Home(environment: environment)
              : _PendingModule(destination: destination),
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

class _F41Home extends StatelessWidget {
  const _F41Home({required this.environment});

  final RouteFijaEnvironment environment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 640),
        child: ListView(
          shrinkWrap: true,
          children: [
            Text(
              'Base Flutter preparada',
              style: theme.textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 12),
            const Chip(label: Text('F4.1 · M1 · 8/80 puntos')),
            const SizedBox(height: 20),
            const Text(
              'La aplicación ya cuenta con tema, rutas y configuración segura por entorno. '
              'Todavía no inicia sesión ni consulta datos operativos.',
            ),
            const SizedBox(height: 20),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Canal API configurado',
                      style: theme.textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    SelectableText(environment.apiBaseUrl),
                    const SizedBox(height: 8),
                    const Text(
                      'La URL se cambia con RF_API_BASE_URL. No contiene credenciales, '
                      'tokens ni acceso directo a PostgreSQL.',
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Las funcionalidades de conductor se habilitan en las fases siguientes '
              'y no muestran datos simulados.',
            ),
          ],
        ),
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
