import 'package:flutter/material.dart';

import 'ruta_fija_environment.dart';
import 'ruta_fija_router.dart';
import 'ruta_fija_theme.dart';

class RutaFijaApp extends StatelessWidget {
  const RutaFijaApp({required this.environment, super.key});

  final RouteFijaEnvironment environment;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ruta Fija Conductor',
      debugShowCheckedModeBanner: false,
      theme: RutaFijaTheme.light(),
      initialRoute: RutaFijaRouter.initialRoute,
      onGenerateRoute: (settings) =>
          RutaFijaRouter.onGenerateRoute(settings, environment),
    );
  }
}
