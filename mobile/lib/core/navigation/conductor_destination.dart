import 'package:flutter/material.dart';

enum ConductorDestination {
  home('/', 'Inicio', Icons.home_outlined, 'M1'),
  availability(
    '/availability',
    'Disponibilidad',
    Icons.toggle_on_outlined,
    'M3',
  ),
  assignments('/assignments', 'Asignaciones', Icons.route_outlined, 'M4'),
  incidents('/incidents', 'Incidencias', Icons.report_problem_outlined, 'M5'),
  announcements('/announcements', 'Comunicados', Icons.campaign_outlined, 'M6'),
  profile('/profile', 'Perfil', Icons.person_outline, 'M2');

  const ConductorDestination(this.path, this.label, this.icon, this.milestone);

  final String path;
  final String label;
  final IconData icon;
  final String milestone;

  static ConductorDestination fromPath(String? path) {
    return ConductorDestination.values.firstWhere(
      (destination) => destination.path == path,
      orElse: () => ConductorDestination.home,
    );
  }
}
