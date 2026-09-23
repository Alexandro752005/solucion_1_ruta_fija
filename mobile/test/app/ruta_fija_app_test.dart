import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_app.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_dependencies.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

import '../support/mobile_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  RutaFijaDependencies dependencies({
    required FakeMobileAuthGateway authGateway,
    required FakeMobileDriverGateway driverGateway,
    required FakeMobileAssignmentGateway assignmentGateway,
  }) {
    return RutaFijaDependencies(
      sessionController: MobileSessionController(
        authGateway: authGateway,
        tokenStore: InMemoryRefreshTokenStore(),
      ),
      driverGateway: driverGateway,
      assignmentGateway: assignmentGateway,
      httpClient: http.Client(),
    );
  }

  testWidgets('shows real mobile login before any protected profile data', (
    tester,
  ) async {
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
      assignmentGateway: FakeMobileAssignmentGateway(),
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
      assignmentGateway: FakeMobileAssignmentGateway(),
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

  testWidgets(
    'shows and confirms a real own assignment after a mobile session',
    (tester) async {
      final assignmentGateway = FakeMobileAssignmentGateway();
      final dependenciesForTest = dependencies(
        authGateway: FakeMobileAuthGateway(),
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: assignmentGateway,
      );
      await tester.pumpWidget(
        RutaFijaApp(
          environment: environment,
          dependencies: dependenciesForTest,
        ),
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextFormField).at(0),
        'driver@test.dev',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        'correct-password',
      );
      await tester.tap(find.byKey(const Key('mobile-login-submit')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('nav-assignments')));
      await tester.pumpAndSettle();

      expect(find.text('Asignaciones'), findsWidgets);
      expect(find.text('Terminal Norte → Terminal Sur'), findsOneWidget);
      await tester.tap(
        find.byKey(
          const Key('assignment-33333333-3333-4333-8333-333333333333'),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('assignment-command-accept')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Confirmar'));
      await tester.pumpAndSettle();

      expect(assignmentGateway.sentCommands, [
        MobileAssignmentCommandType.accept,
      ]);
      expect(find.text('Programada'), findsWidgets);
    },
  );
}
