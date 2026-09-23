import 'package:flutter/material.dart';

import '../domain/mobile_driver_models.dart';

class DriverProfileDetails extends StatelessWidget {
  const DriverProfileDetails({required this.profile, super.key});

  final MobileDriverProfile profile;

  @override
  Widget build(BuildContext context) {
    final vehicle = profile.primaryVehicle;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          profile.fullName,
          style: Theme.of(context).textTheme.headlineSmall
              ?.copyWith(fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 6),
        Text('Grupo: ${profile.groupName}'),
        const SizedBox(height: 20),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Disponibilidad actual'),
                const SizedBox(height: 8),
                Chip(
                  avatar: const Icon(Icons.toggle_on_outlined),
                  label: Text(profile.availabilityStatus.label),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Vehículo principal'),
                const SizedBox(height: 8),
                if (vehicle == null)
                  const Text('No tiene un vehículo principal vinculado.')
                else ...[
                  Text(
                    vehicle.plate,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text('Estado del vehículo: ${vehicle.status}'),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}
