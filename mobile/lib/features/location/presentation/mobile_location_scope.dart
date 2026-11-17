import 'package:flutter/widgets.dart';

import '../domain/mobile_foreground_location.dart';
import '../domain/mobile_service_location_controller.dart';

/// App-owned location dependencies; a page never creates another GPS scheduler.
class MobileLocationScope extends InheritedWidget {
  const MobileLocationScope({
    required this.controller,
    required this.device,
    required super.child,
    super.key,
  });

  final MobileServiceLocationController? controller;
  final MobileDeviceLocationGateway device;

  static MobileLocationScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<MobileLocationScope>();

  @override
  bool updateShouldNotify(MobileLocationScope oldWidget) =>
      controller != oldWidget.controller || device != oldWidget.device;
}
