import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';
import 'driver_profile_details.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({required this.driverGateway, super.key});

  final MobileDriverGateway driverGateway;

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
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
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    mobileErrorMessage(snapshot.error!),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  OutlinedButton.icon(
                    onPressed: () {
                      _reload();
                    },
                    icon: const Icon(Icons.refresh),
                    label: const Text('Reintentar'),
                  ),
                ],
              ),
            ),
          );
        }
        return RefreshIndicator(
          onRefresh: _reload,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
            children: [
              DriverProfileDetails(profile: snapshot.requireData),
              const SizedBox(height: 20),
              const Card(
                child: Padding(
                  padding: EdgeInsets.all(16),
                  child: Text(
                    'La gestión de consentimiento y ubicación se habilitará en M7. '
                    'Esta pantalla no solicita permisos de ubicación todavía.',
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
