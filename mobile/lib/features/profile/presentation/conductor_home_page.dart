import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';
import 'driver_profile_details.dart';

class ConductorHomePage extends StatefulWidget {
  const ConductorHomePage({required this.driverGateway, super.key});

  final MobileDriverGateway driverGateway;

  @override
  State<ConductorHomePage> createState() => _ConductorHomePageState();
}

class _ConductorHomePageState extends State<ConductorHomePage> {
  late Future<MobileDriverProfile> _profile;

  @override
  void initState() {
    super.initState();
    _profile = widget.driverGateway.profile();
  }

  Future<void> _reload() async {
    final profile = widget.driverGateway.profile();
    setState(() => _profile = profile);
    try {
      await profile;
    } catch (_) {
      // FutureBuilder renders the safe, user-facing error message.
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<MobileDriverProfile>(
      future: _profile,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return _LoadError(
            message: mobileErrorMessage(snapshot.error!),
            onRetry: () {
              _reload();
            },
          );
        }
        final profile = snapshot.requireData;
        return RefreshIndicator(
          onRefresh: _reload,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                'Sesión móvil activa',
                style: Theme.of(context).textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 16),
              DriverProfileDetails(profile: profile),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: () =>
                    Navigator.of(context).pushReplacementNamed('/availability'),
                icon: const Icon(Icons.toggle_on_outlined),
                label: const Text('Gestionar disponibilidad'),
              ),
              const SizedBox(height: 12),
              const Text(
                'Los datos provienen de su perfil real. Asignaciones, incidencias, '
                'comunicados y ubicación se habilitan en sus módulos posteriores.',
              ),
            ],
          ),
        );
      },
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 40),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}
