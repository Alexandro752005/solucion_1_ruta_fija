import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_date_time.dart';
import '../domain/mobile_announcement_models.dart';

String announcementDateTimeLabel(DateTime value) => mobileDateTimeLabel(value);

Color announcementReadColor(BuildContext context, MobileAnnouncement item) {
  if (item.requiresReadConfirmation) {
    return Theme.of(context).colorScheme.tertiary;
  }
  if (item.readAt != null) {
    return Colors.green.shade700;
  }
  return Theme.of(context).colorScheme.primary;
}
