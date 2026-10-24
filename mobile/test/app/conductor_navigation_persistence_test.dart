import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_app.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_dependencies.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/qualification/domain/mobile_qualification.dart';

import '../support/mobile_fakes.dart';

const destinations = [
  'home',
  'availability',
  'assignments',
  'incidents',
  'announcements',
  'profile',
];

Future<RutaFijaDependencies> startApp(
  WidgetTester tester, {
  MobileAccessScope scope = MobileAccessScope.operations,
  bool operational = true,
}) async {
  final original = testSession();
  final auth = FakeMobileAuthGateway(
    session: MobileAuthSession(
      accessToken: original.accessToken,
      refreshToken: original.refreshToken,
      expiresIn: original.expiresIn,
      refreshExpiresIn: original.refreshExpiresIn,
      user: original.user,
      accessScope: scope,
    ),
  );
  final dependencies = RutaFijaDependencies(
    sessionController: MobileSessionController(
      authGateway: auth,
      tokenStore: InMemoryRefreshTokenStore(),
    ),
    conductorRecoveryGateway: FakeMobileConductorRecoveryGateway(),
    conductorOnboardingGateway: FakeMobileConductorOnboardingGateway(),
    applicantAccessGateway: FakeMobileApplicantAccessGateway(),
    driverGateway: FakeMobileDriverGateway(),
    assignmentGateway: FakeMobileAssignmentGateway(),
    incidentGateway: FakeMobileIncidentGateway(),
    announcementGateway: FakeMobileAnnouncementGateway(),
    deviceLocationGateway: FakeMobileDeviceLocationGateway(),
    qualificationGateway: FakeMobileQualificationGateway(
      state: MobileQualification(
        driverId: original.user.driverId,
        version: 0,
        suspended: !operational,
        operationalEnabled: operational,
        personalDocumentsStatus: 'CURRENT',
        blockers: operational ? const [] : const ['OPERATIONAL_SUSPENSION'],
        periods: const [],
        unitRequests: const [],
        observedAt: DateTime.utc(2026, 10, 24),
      ),
    ),
    httpClient: http.Client(),
  );
  await tester.pumpWidget(
    RutaFijaApp(
      environment: RouteFijaEnvironment.fromApiBaseUrl(
        'https://api.example.test/api/v1',
      ),
      dependencies: dependencies,
    ),
  );
  await tester.pumpAndSettle();
  await dependencies.sessionController.login(
    email: 'navigation@example.test',
    password: 'synthetic-navigation-only',
  );
  await tester.pumpAndSettle();
  return dependencies;
}

void expectNavigation(WidgetTester tester, int selected) {
  expect(find.byType(NavigationBar), findsOneWidget);
  expect(
    tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
    selected,
  );
  for (final destination in destinations) {
    expect(find.byKey(Key('nav-$destination')), findsOneWidget);
  }
  expect(tester.takeException(), isNull);
}

void main() {
  final histories = [
    (
      name: 'incidents',
      index: 3,
      menu: 'Mis incidencias e historial',
      title: 'Mis incidencias',
    ),
    (
      name: 'announcements',
      index: 4,
      menu: 'Mis comunicados recibidos',
      title: 'Mis comunicados',
    ),
  ];
  for (final history in histories) {
    testWidgets(
      '${history.name} preserves all bottom destinations and can leave through the bar',
      (tester) async {
        await startApp(tester);
        await tester.tap(find.byKey(Key('nav-${history.name}')));
        await tester.pumpAndSettle();
        expectNavigation(tester, history.index);
        await tester.tap(find.byKey(const Key('nav-profile')));
        await tester.pumpAndSettle();
        expectNavigation(tester, 5);
        await tester.tap(find.byKey(Key('nav-${history.name}')));
        await tester.pumpAndSettle();
        expectNavigation(tester, history.index);
        await tester.tap(find.byKey(const Key('nav-home')));
        await tester.pumpAndSettle();
        expectNavigation(tester, 0);
      },
    );

    testWidgets(
      'direct /${history.name} route selects the matching bottom destination',
      (tester) async {
        await startApp(tester);
        tester
            .state<NavigatorState>(find.byType(Navigator))
            .pushNamed<void>('/${history.name}');
        await tester.pumpAndSettle();
        expectNavigation(tester, history.index);
        await tester.tap(find.byKey(const Key('nav-assignments')));
        await tester.pumpAndSettle();
        expectNavigation(tester, 2);
      },
    );

    testWidgets(
      'incorporation may consult ${history.name} without operational bottom destinations',
      (tester) async {
        await startApp(
          tester,
          scope: MobileAccessScope.incorporation,
          operational: false,
        );
        expect(find.text('Mi incorporación'), findsOneWidget);
        await tester.tap(find.byTooltip('Consultas propias'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(history.menu));
        await tester.pumpAndSettle();
        expect(find.text(history.title), findsOneWidget);
        expect(find.byType(NavigationBar), findsNothing);
        expect(find.byKey(const Key('nav-assignments')), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'suspended operations session keeps own ${history.name} but cannot enter availability',
      (tester) async {
        await startApp(tester, operational: false);
        expect(find.text('Mi incorporación'), findsOneWidget);
        await tester.tap(find.byTooltip('Consultas propias'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(history.menu));
        await tester.pumpAndSettle();
        expectNavigation(tester, history.index);
        await tester.tap(find.byKey(const Key('nav-availability')));
        await tester.pumpAndSettle();
        expect(find.text('Mi incorporación'), findsOneWidget);
        expect(find.byKey(const Key('availability-save')), findsNothing);
        expect(find.byType(NavigationBar), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
