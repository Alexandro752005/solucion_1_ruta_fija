import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_theme.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_login_page.dart';

import '../../support/mobile_fakes.dart';

void main() {
  testWidgets(
    'L08 submit is single-flight;503 has an accessible error and does not retry',
    (tester) async {
      var requests = 0;
      final response = Completer<http.Response>();
      final transport = MockClient((_) {
        requests++;
        return response.future;
      });
      final store = InMemoryRefreshTokenStore();
      final controller = MobileSessionController(
        authGateway: MobileAuthRepository(
          MobileApiClient(
            httpClient: transport,
            environment: RouteFijaEnvironment.fromApiBaseUrl(
              'https://api.example.test/api/v1',
            ),
          ),
        ),
        tokenStore: store,
      );
      await controller.restore();
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          MaterialApp(
            theme: RutaFijaTheme.light(),
            home: MobileLoginPage(
              sessionController: controller,
              conductorRecoveryGateway: FakeMobileConductorRecoveryGateway(),
              conductorOnboardingGateway:
                  FakeMobileConductorOnboardingGateway(),
              applicantAccessGateway: FakeMobileApplicantAccessGateway(),
            ),
          ),
        );
        final fields = find.byType(TextFormField);
        final emptySubmit=find.byKey(const Key('mobile-login-submit'));
        await tester.ensureVisible(emptySubmit);
        await tester.tap(emptySubmit);
        await tester.pumpAndSettle();
        expect(find.text('Ingrese su correo electrónico.'),findsOneWidget);
        expect(find.text('Ingrese su contraseña.'),findsOneWidget);
        expect(requests,0);
        await tester.enterText(fields.at(0), 'synthetic@rutafija.test');
        await tester.enterText(fields.at(1), 'SYNTHETIC_NOT_A_CREDENTIAL');
        final submit = find.byKey(const Key('mobile-login-submit'));
        await tester.ensureVisible(submit);
        await tester.tap(submit);
        await tester.pump();
        expect(tester.widget<FilledButton>(submit).onPressed, isNull);
        expect(requests, 1);
        await tester.testTextInput.receiveAction(TextInputAction.done);
        await tester.pump();
        expect(requests, 1);
        response.complete(
          http.Response(
            jsonEncode({
              'status': 503,
              'code': 'SERVICE_UNAVAILABLE',
              'message': 'SYNTHETIC_PRIVATE_DIAGNOSTIC',
              'errors': [],
            }),
            503,
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.textContaining('no está disponible temporalmente'),
          findsOneWidget,
        );
        expect(find.textContaining('PRIVATE_DIAGNOSTIC'), findsNothing);
        expect(find.text('Ingrese su correo electrónico.'),findsNothing);
        expect(find.text('Ingrese su contraseña.'),findsNothing);
        final error = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              (widget.properties.label?.startsWith('Error de acceso:') ??
                  false),
        );
        expect(error, findsOneWidget);
        expect(
          tester
              .getSemantics(error)
              .getSemanticsData()
              .flagsCollection
              .isLiveRegion,
          true,
        );
        expect(controller.state.status, MobileSessionStatus.unauthenticated);
        expect(store.value, isNull);
        expect(controller.accessToken, isNull);
        await tester.pump(const Duration(minutes: 2));
        expect(requests, 1);
      } finally {
        try {
          await tester.pumpWidget(const SizedBox.shrink());
        } finally {
          // The binding verifies handles before addTearDown callbacks run.
          semantics.dispose();
          controller.dispose();
          transport.close();
        }
      }
    },
  );
  testWidgets(
    'L08 narrow screen and300percent text keep labelled login and validation reachable',
    (tester) async {
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final auth = FakeMobileAuthGateway();
      final controller = MobileSessionController(
        authGateway: auth,
        tokenStore: InMemoryRefreshTokenStore(),
      );
      await controller.restore();
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          MaterialApp(
            theme: RutaFijaTheme.light(),
            builder: (_, child) => MediaQuery(
              data: const MediaQueryData(
                size: Size(360, 740),
                textScaler: TextScaler.linear(3),
              ),
              child: child!,
            ),
            home: MobileLoginPage(
              sessionController: controller,
              conductorRecoveryGateway: FakeMobileConductorRecoveryGateway(),
              conductorOnboardingGateway:
                  FakeMobileConductorOnboardingGateway(),
              applicantAccessGateway: FakeMobileApplicantAccessGateway(),
            ),
          ),
        );
        final fields = find.byType(TextFormField);
        final email = tester.widget<TextField>(
          find.descendant(of: fields.first, matching: find.byType(TextField)),
        );
        final password = tester.widget<TextField>(
          find.descendant(of: fields.last, matching: find.byType(TextField)),
        );
        expect(email.decoration?.labelText, 'Correo electrónico');
        expect(password.decoration?.labelText, 'Contraseña');
        expect(password.obscureText, true);
        final submit = find.byKey(const Key('mobile-login-submit'));
        await tester.ensureVisible(submit);
        await tester.tap(submit);
        await tester.pumpAndSettle();
        expect(find.text('Ingrese su correo electrónico.'), findsOneWidget);
        expect(find.text('Ingrese su contraseña.'), findsOneWidget);
        expect(auth.loginCalls, 0);
        expect(tester.takeException(), isNull);
      } finally {
        try {
          await tester.pumpWidget(const SizedBox.shrink());
        } finally {
          semantics.dispose();
          controller.dispose();
        }
      }
    },
  );
}
