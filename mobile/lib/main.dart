import 'package:flutter/widgets.dart';

import 'app/ruta_fija_app.dart';
import 'app/ruta_fija_environment.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    RutaFijaApp(environment: RouteFijaEnvironment.fromBuildConfiguration()),
  );
}
