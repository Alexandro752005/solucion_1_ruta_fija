import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_date_time.dart';
import '../domain/mobile_assignment_models.dart';

String assignmentDateTimeLabel(DateTime value) => mobileDateTimeLabel(value);

Color assignmentStatusColor(
  BuildContext context,
  MobileAssignmentStatus status,
) {
  final scheme = Theme.of(context).colorScheme;
  return switch (status) {
    MobileAssignmentStatus.pendingResponse => scheme.tertiary,
    MobileAssignmentStatus.scheduled => scheme.primary,
    MobileAssignmentStatus.enServicio => scheme.secondary,
    MobileAssignmentStatus.completed => Colors.green.shade700,
    MobileAssignmentStatus.transferred => scheme.outline,
    MobileAssignmentStatus.rejected ||
    MobileAssignmentStatus.interrupted ||
    MobileAssignmentStatus.cancelled ||
    MobileAssignmentStatus.expired => scheme.error,
  };
}

IconData assignmentCommandIcon(MobileAssignmentCommandType command) {
  return switch (command) {
    MobileAssignmentCommandType.accept => Icons.check_circle_outline,
    MobileAssignmentCommandType.reject => Icons.cancel_outlined,
    MobileAssignmentCommandType.start => Icons.play_circle_outline,
    MobileAssignmentCommandType.complete => Icons.task_alt_outlined,
  };
}
