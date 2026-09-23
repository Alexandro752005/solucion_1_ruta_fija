import 'package:flutter/material.dart';

import '../domain/mobile_assignment_models.dart';

String assignmentDateTimeLabel(DateTime value) {
  final local = value.toLocal();
  String two(int number) => number.toString().padLeft(2, '0');
  return '${two(local.day)}/${two(local.month)}/${local.year} '
      '${two(local.hour)}:${two(local.minute)}';
}

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
    MobileAssignmentStatus.rejected ||
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
