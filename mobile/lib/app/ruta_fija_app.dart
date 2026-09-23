import 'dart:async';

import 'package:flutter/material.dart';

import '../core/session/mobile_session_controller.dart';
import '../features/auth/presentation/mobile_login_page.dart';
import 'ruta_fija_environment.dart';
import 'ruta_fija_dependencies.dart';
import 'ruta_fija_router.dart';
import 'ruta_fija_theme.dart';

class RutaFijaApp extends StatefulWidget {
  const RutaFijaApp({
    required this.environment,
    required this.dependencies,
    super.key,
  });

  final RouteFijaEnvironment environment;
  final RutaFijaDependencies dependencies;

  @override
  State<RutaFijaApp> createState() => _RutaFijaAppState();
}

class _RutaFijaAppState extends State<RutaFijaApp> {
  @override
  void initState() {
    super.initState();
    unawaited(widget.dependencies.sessionController.restore());
  }

  @override
  void dispose() {
    widget.dependencies.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.dependencies.sessionController,
      builder: (context, _) {
        final session = widget.dependencies.sessionController;
        final state = session.state;
        return MaterialApp(
          key: ValueKey(state.status),
          title: 'Ruta Fija Conductor',
          debugShowCheckedModeBanner: false,
          theme: RutaFijaTheme.light(),
          onGenerateRoute: (settings) => RutaFijaRouter.onGenerateRoute(
            settings,
            environment: widget.environment,
            sessionController: session,
            driverGateway: widget.dependencies.driverGateway,
            assignmentGateway: widget.dependencies.assignmentGateway,
          ),
          home: switch (state.status) {
            MobileSessionStatus.restoring => const _SessionRestorePage(),
            MobileSessionStatus.unauthenticated => MobileLoginPage(
              sessionController: session,
            ),
            MobileSessionStatus.authenticated => RutaFijaRouter.home(
              environment: widget.environment,
              sessionController: session,
              driverGateway: widget.dependencies.driverGateway,
              assignmentGateway: widget.dependencies.assignmentGateway,
            ),
          },
        );
      },
    );
  }
}

class _SessionRestorePage extends StatelessWidget {
  const _SessionRestorePage();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Verificando sesión protegida…'),
          ],
        ),
      ),
    );
  }
}
