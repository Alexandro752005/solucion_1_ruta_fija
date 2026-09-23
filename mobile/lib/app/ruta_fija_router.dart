import 'package:flutter/material.dart';

import '../core/navigation/conductor_destination.dart';
import '../features/bootstrap/presentation/conductor_navigation_shell.dart';
import 'ruta_fija_environment.dart';

final class RutaFijaRouter {
  RutaFijaRouter._();

  static const initialRoute = '/';

  static Route<void> onGenerateRoute(
    RouteSettings settings,
    RouteFijaEnvironment environment,
  ) {
    final destination = ConductorDestination.fromPath(settings.name);
    return MaterialPageRoute<void>(
      settings: RouteSettings(name: destination.path),
      builder: (_) => ConductorNavigationShell(
        destination: destination,
        environment: environment,
      ),
    );
  }
}
