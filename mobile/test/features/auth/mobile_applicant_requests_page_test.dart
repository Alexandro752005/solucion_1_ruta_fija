import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_applicant_requests_page.dart';

import '../../support/mobile_fakes.dart';

void main() {
  Future<void> login(WidgetTester tester) async {
    await tester.enterText(
      find.byKey(const Key('applicant-email')),
      'synthetic@example.test',
    );
    await tester.enterText(
      find.byKey(const Key('applicant-password')),
      'preliminary-secret',
    );
    await tester.tap(find.byKey(const Key('applicant-login')));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'shows only request metadata and clears access on explicit logout',
    (tester) async {
      final gateway = FakeMobileApplicantAccessGateway();
      await tester.pumpWidget(
        MaterialApp(home: MobileApplicantRequestsPage(gateway: gateway)),
      );
      await login(tester);

      expect(find.text('Pendiente de revisión'), findsOneWidget);
      expect(find.textContaining('synthetic-request-id'), findsOneWidget);
      expect(find.textContaining('synthetic-organization-id'), findsOneWidget);
      expect(find.textContaining('synthetic@example.test'), findsNothing);
      expect(find.textContaining('preliminary-secret'), findsNothing);
      expect(
        find.textContaining(FakeMobileApplicantAccessGateway.token),
        findsNothing,
      );
      expect(find.byKey(const Key('applicant-password')), findsNothing);

      await tester.tap(find.byKey(const Key('applicant-logout')));
      await tester.pumpAndSettle();

      expect(gateway.loggedOutTokens, [FakeMobileApplicantAccessGateway.token]);
      expect(find.textContaining('synthetic-request-id'), findsNothing);
      final password = tester.widget<TextFormField>(
        find.byKey(const Key('applicant-password')),
      );
      final email = tester.widget<TextFormField>(
        find.byKey(const Key('applicant-email')),
      );
      expect(password.controller!.text, isEmpty);
      expect(email.controller!.text, isEmpty);
    },
  );

  testWidgets(
    'server expiry clears the list and returns to preliminary access',
    (tester) async {
      final gateway = FakeMobileApplicantAccessGateway();
      await tester.pumpWidget(
        MaterialApp(home: MobileApplicantRequestsPage(gateway: gateway)),
      );
      await login(tester);
      gateway.requestsFailure = const MobileApiException(
        status: 401,
        code: 'AUTH_TOKEN_INVALID',
        message: 'technical detail',
      );
      await tester.tap(find.byKey(const Key('applicant-refresh')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('applicant-login')), findsOneWidget);
      expect(
        find.text('El acceso de consulta venció. Ingrese nuevamente.'),
        findsOneWidget,
      );
      expect(find.textContaining('synthetic-request-id'), findsNothing);
      expect(gateway.loggedOutTokens, [FakeMobileApplicantAccessGateway.token]);
      expect(gateway.loginCalls, 1);
    },
  );

  testWidgets(
    'local expiry revokes access without a refresh or additional login',
    (tester) async {
      final gateway = FakeMobileApplicantAccessGateway(
        expiresAt: DateTime.now().add(const Duration(seconds: 5)),
      );
      await tester.pumpWidget(
        MaterialApp(home: MobileApplicantRequestsPage(gateway: gateway)),
      );
      await login(tester);
      await tester.pump(const Duration(seconds: 6));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('applicant-login')), findsOneWidget);
      expect(find.textContaining('synthetic-request-id'), findsNothing);
      expect(gateway.loggedOutTokens, [FakeMobileApplicantAccessGateway.token]);
      expect(gateway.loginCalls, 1);
      expect(gateway.requestCalls, 1);
    },
  );

  testWidgets('leaving the page revokes its in-memory access', (tester) async {
    final gateway = FakeMobileApplicantAccessGateway();
    await tester.pumpWidget(
      MaterialApp(home: MobileApplicantRequestsPage(gateway: gateway)),
    );
    await login(tester);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();

    expect(gateway.loggedOutTokens, [FakeMobileApplicantAccessGateway.token]);
    expect(tester.takeException(), isNull);
  });
}
