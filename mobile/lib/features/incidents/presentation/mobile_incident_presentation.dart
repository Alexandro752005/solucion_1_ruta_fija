import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_date_time.dart';
import '../domain/mobile_incident_models.dart';

String incidentDateTimeLabel(DateTime value) => mobileDateTimeLabel(value);

Color incidentStatusColor(BuildContext context, MobileIncidentStatus status) {
  final colors = Theme.of(context).colorScheme;
  return switch (status) {
    MobileIncidentStatus.open => colors.error,
    MobileIncidentStatus.followUp => colors.tertiary,
    MobileIncidentStatus.resolved => Colors.green.shade700,
    MobileIncidentStatus.driverClosed => Colors.blueGrey.shade700,
  };
}

IconData incidentCategoryIcon(MobileIncidentCategory category) {
  return switch (category) {
    MobileIncidentCategory.averia => Icons.build_outlined,
    MobileIncidentCategory.accidente => Icons.car_crash_outlined,
    MobileIncidentCategory.retraso => Icons.schedule_outlined,
    MobileIncidentCategory.licenciaVencida => Icons.badge_outlined,
    MobileIncidentCategory.imposibilidadServicio => Icons.block_outlined,
    MobileIncidentCategory.ubicacionNoCorroborada =>
      Icons.location_off_outlined,
    MobileIncidentCategory.riesgoSeguridad => Icons.warning_amber_outlined,
    MobileIncidentCategory.accesoParada => Icons.not_accessible_outlined,
    MobileIncidentCategory.falloPlataforma => Icons.phonelink_erase_outlined,
    MobileIncidentCategory.otro => Icons.report_problem_outlined,
  };
}
