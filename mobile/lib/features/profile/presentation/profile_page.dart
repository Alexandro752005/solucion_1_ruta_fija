import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';
import 'driver_profile_details.dart';
import '../../preferences/domain/mobile_preferences.dart';
import '../../preferences/presentation/mobile_preferences_panel.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({required this.driverGateway, this.preferences, this.reportsAvailable=false, super.key});

  final MobileDriverGateway driverGateway;
  final MobilePreferencesController? preferences;
  final bool reportsAvailable;

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
              const Text('La identidad, el correo y los documentos solo cambian por sus flujos de revisión. No se editan aquí.'),
              if(widget.preferences case final preferences?)MobilePreferencesPanel(controller:preferences),
              if(widget.reportsAvailable)OutlinedButton.icon(key:const Key('profile-reports'),onPressed:()=>Navigator.of(context).pushNamed('/reports'),icon:const Icon(Icons.summarize_outlined),label:const Text('Mis resultados y reportes')),
              const SizedBox(height: 20),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Ubicación actual'),
                      const SizedBox(height: 8),
                      const Text(
                        'Puede compartir un único punto solo mientras usa la aplicación. ',
                      ),
                      const SizedBox(height: 12),
                      FilledButton.icon(
                        key: const Key('profile-manage-location'),
                        onPressed: () =>
                            Navigator.of(context).pushNamed('/location'),
                        icon: const Icon(Icons.my_location_outlined),
                        label: const Text('Gestionar ubicación'),
                      ),
                    ],
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
