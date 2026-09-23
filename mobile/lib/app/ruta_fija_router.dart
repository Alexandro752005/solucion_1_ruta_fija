import 'package:flutter/material.dart';

import '../core/session/mobile_session_controller.dart';
import '../core/navigation/conductor_destination.dart';
import '../features/bootstrap/presentation/conductor_navigation_shell.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import 'ruta_fija_environment.dart';

final class RutaFijaRouter {
  RutaFijaRouter._();

  static const initialRoute = '/';

  static Widget home({
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
  }) {
    return ConductorNavigationShell(
      destination: ConductorDestination.home,
      environment: environment,
      sessionController: sessionController,
      driverGateway: driverGateway,
    );
  }

  static Route<void> onGenerateRoute(
    RouteSettings settings, {
    required RouteFijaEnvironment environment,
    required MobileSessionController sessionController,
    required MobileDriverGateway driverGateway,
  }) {
    final destination = ConductorDestination.fromPath(settings.name);
    return MaterialPageRoute<void>(
      settings: RouteSettings(name: destination.path),
      builder: (_) => ConductorNavigationShell(
        destination: destination,
        environment: environment,
        sessionController: sessionController,
        driverGateway: driverGateway,
      ),
    );
  }
}
