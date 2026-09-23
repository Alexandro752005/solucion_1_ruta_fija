import 'package:flutter/widgets.dart';

import 'app/ruta_fija_app.dart';
import 'app/ruta_fija_dependencies.dart';
import 'app/ruta_fija_environment.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final environment = RouteFijaEnvironment.fromBuildConfiguration();
  runApp(
    RutaFijaApp(
      environment: environment,
      dependencies: RutaFijaDependencies.create(environment),
    ),
  );
}
