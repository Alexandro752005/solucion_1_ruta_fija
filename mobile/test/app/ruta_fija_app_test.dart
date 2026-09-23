import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_app.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_dependencies.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';

import '../support/mobile_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  RutaFijaDependencies dependencies({
    required FakeMobileAuthGateway authGateway,
    required FakeMobileDriverGateway driverGateway,
  }) {
    return RutaFijaDependencies(
      sessionController: MobileSessionController(
        authGateway: authGateway,
        tokenStore: InMemoryRefreshTokenStore(),
      ),
      driverGateway: driverGateway,
      httpClient: http.Client(),
    );
  }

  testWidgets('shows real mobile login before any protected profile data', (
    tester,
  ) async {
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
    );
    await tester.pumpWidget(
      RutaFijaApp(environment: environment, dependencies: dependenciesForTest),
    );
    await tester.pumpAndSettle();

    expect(find.text('Acceso del conductor'), findsOneWidget);
    expect(find.textContaining('no usa la sesión web del CRM'), findsOneWidget);
    expect(find.byKey(const Key('mobile-login-submit')), findsOneWidget);
  });

  testWidgets('authenticates and exposes only the own mobile profile', (
    tester,
  ) async {
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
    );
    await tester.pumpWidget(
      RutaFijaApp(environment: environment, dependencies: dependenciesForTest),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'driver@test.dev');
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'correct-password',
    );
    await tester.tap(find.byKey(const Key('mobile-login-submit')));
    await tester.pumpAndSettle();

    expect(find.text('Sesión móvil activa'), findsOneWidget);
    expect(find.text('Conductora de prueba'), findsOneWidget);
    expect(find.text('ABC-123'), findsOneWidget);
  });

  testWidgets('keeps unbuilt assignments honest after a real session', (
    tester,
  ) async {
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
    );
    await tester.pumpWidget(
      RutaFijaApp(environment: environment, dependencies: dependenciesForTest),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).at(0), 'driver@test.dev');
    await tester.enterText(
      find.byType(TextFormField).at(1),
      'correct-password',
    );
    await tester.tap(find.byKey(const Key('mobile-login-submit')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('nav-assignments')));
    await tester.pumpAndSettle();

    expect(find.text('M4 — Asignaciones'), findsOneWidget);
    expect(find.text('Pendiente de construcción'), findsOneWidget);
  });
}
