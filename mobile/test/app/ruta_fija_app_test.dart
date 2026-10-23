import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_app.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_dependencies.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/incidents/domain/mobile_incident_models.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../support/mobile_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  RutaFijaDependencies dependencies({
    required FakeMobileAuthGateway authGateway,
    required FakeMobileDriverGateway driverGateway,
    required FakeMobileAssignmentGateway assignmentGateway,
    FakeMobileIncidentGateway? incidentGateway,
    FakeMobileAnnouncementGateway? announcementGateway,
    FakeMobileDeviceLocationGateway? deviceLocationGateway,
    FakeMobileConductorRecoveryGateway? conductorRecoveryGateway,
    FakeMobileConductorOnboardingGateway? conductorOnboardingGateway,
    FakeMobileApplicantAccessGateway? applicantAccessGateway,
  }) {
    return RutaFijaDependencies(
      sessionController: MobileSessionController(
        authGateway: authGateway,
        tokenStore: InMemoryRefreshTokenStore(),
      ),
      conductorRecoveryGateway:
          conductorRecoveryGateway ?? FakeMobileConductorRecoveryGateway(),
      conductorOnboardingGateway:
          conductorOnboardingGateway ?? FakeMobileConductorOnboardingGateway(),
      applicantAccessGateway:
          applicantAccessGateway ?? FakeMobileApplicantAccessGateway(),
      driverGateway: driverGateway,
      qualificationGateway: FakeMobileQualificationGateway(),
      assignmentGateway: assignmentGateway,
      incidentGateway: incidentGateway ?? FakeMobileIncidentGateway(),
      announcementGateway:
          announcementGateway ?? FakeMobileAnnouncementGateway(),
      deviceLocationGateway:
          deviceLocationGateway ?? FakeMobileDeviceLocationGateway(),
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

  testWidgets(
    'consults preliminary requests without creating an operational session',
    (tester) async {
      final auth = FakeMobileAuthGateway();
      final applicant = FakeMobileApplicantAccessGateway();
      final dependenciesForTest = dependencies(
        authGateway: auth,
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
        applicantAccessGateway: applicant,
      );
      await tester.pumpWidget(
        RutaFijaApp(
          environment: environment,
          dependencies: dependenciesForTest,
        ),
      );
      await tester.pumpAndSettle();
      final open = find.byKey(const Key('mobile-applicant-open'));
      await tester.ensureVisible(open);
      await tester.tap(open);
      await tester.pumpAndSettle();
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

      expect(find.text('Pendiente de revisión'), findsOneWidget);
      expect(
        dependenciesForTest.sessionController.state.status,
        MobileSessionStatus.unauthenticated,
      );
      expect(dependenciesForTest.sessionController.accessToken, isNull);
      expect(auth.loginCalls, 0);
      expect(auth.refreshedTokens, isEmpty);
      expect(find.byKey(const Key('nav-assignments')), findsNothing);

      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(applicant.loggedOutTokens, [
        FakeMobileApplicantAccessGateway.token,
      ]);
      expect(find.text('Acceso del conductor'), findsOneWidget);
    },
  );

  testWidgets(
    'accepts a conductor invitation through OTP and stays outside the operational session',
    (tester) async {
      final auth = FakeMobileAuthGateway();
      final onboarding = FakeMobileConductorOnboardingGateway();
      final dependenciesForTest = dependencies(
        authGateway: auth,
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
        conductorOnboardingGateway: onboarding,
      );
      await tester.pumpWidget(
        RutaFijaApp(
          environment: environment,
          dependencies: dependenciesForTest,
        ),
      );
      await tester.pumpAndSettle();

      final open = find.byKey(const Key('mobile-onboarding-open'));
      await tester.ensureVisible(open);
      await tester.tap(open);
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('onboarding-full-name')),
        'Persona sintética',
      );
      await tester.enterText(
        find.byKey(const Key('onboarding-email')),
        'synthetic.driver@example.test',
      );
      await tester.enterText(
        find.byKey(const Key('onboarding-phone')),
        '+51999999999',
      );
      await tester.enterText(
        find.byKey(const Key('onboarding-password')),
        'Synthetic-Onboarding-Password-2026',
      );
      await tester.enterText(
        find.byKey(const Key('onboarding-confirm-password')),
        'Synthetic-Onboarding-Password-2026',
      );
      await tester.enterText(
        find.byKey(const Key('onboarding-invitation-code')),
        '004207',
      );
      final submit = find.byKey(const Key('onboarding-submit'));
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(onboarding.starts, hasLength(1));
      expect(
        onboarding.starts.single['email'],
        'synthetic.driver@example.test',
      );
      expect(onboarding.starts.single['invitationCode'], '004207');
      expect(find.byKey(const Key('onboarding-otp-code')), findsOneWidget);
      expect(find.text('Persona sintética'), findsNothing);
      expect(find.text('synthetic.driver@example.test'), findsNothing);
      expect(find.text('+51999999999'), findsNothing);

      await tester.enterText(
        find.byKey(const Key('onboarding-otp-code')),
        '001234',
      );
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(onboarding.verifications, [
        ('synthetic-onboarding-token', '001234'),
      ]);
      expect(find.text('Solicitud recibida'), findsOneWidget);
      expect(find.text('Pendiente de revisión'), findsOneWidget);
      expect(
        dependenciesForTest.sessionController.state.status,
        MobileSessionStatus.unauthenticated,
      );
      expect(auth.loginCalls, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'completes assisted recovery without displaying or storing the reset token',
    (tester) async {
      final recovery = FakeMobileConductorRecoveryGateway();
      final dependenciesForTest = dependencies(
        authGateway: FakeMobileAuthGateway(),
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
        conductorRecoveryGateway: recovery,
      );
      await tester.pumpWidget(
        RutaFijaApp(
          environment: environment,
          dependencies: dependenciesForTest,
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('mobile-recovery-open')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('recovery-id')),
        'recovery-id',
      );
      await tester.enterText(
        find.byKey(const Key('recovery-token')),
        'handoff-token',
      );
      await tester.enterText(find.byKey(const Key('recovery-otp')), '001234');
      final submit = find.byKey(const Key('mobile-login-submit'));
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(recovery.otpVerifications, [
        ('recovery-id', 'handoff-token', '001234'),
      ]);
      expect(find.byKey(const Key('recovery-new-password')), findsOneWidget);
      expect(find.text('synthetic-reset-token'), findsNothing);

      await tester.enterText(
        find.byKey(const Key('recovery-new-password')),
        'new-test-password-123',
      );
      await tester.enterText(
        find.byKey(const Key('recovery-confirm-password')),
        'new-test-password-123',
      );
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();

      expect(recovery.passwordResets, [
        ('recovery-id', 'synthetic-reset-token', 'new-test-password-123'),
      ]);
      expect(find.textContaining('Contraseña actualizada'), findsOneWidget);
      expect(find.text('synthetic-reset-token'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'shows the six-digit verification step before creating a session',
    (tester) async {
      final auth = FakeMobileAuthGateway(
        loginChallenge: MobileEmailOtpChallenge(
          verificationToken: 'in-memory-verification-token',
          otpExpiresAt: DateTime.utc(2026, 10, 14, 17, 5),
          challengeExpiresAt: DateTime.utc(2026, 10, 14, 17, 30),
          emailSent: true,
        ),
      );
      final dependenciesForTest = dependencies(
        authGateway: auth,
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
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
      await tester.enterText(find.byType(TextFormField).at(1), 'test-password');
      await tester.tap(find.byKey(const Key('mobile-login-submit')));
      await tester.pumpAndSettle();

      expect(
        dependenciesForTest.sessionController.state.status,
        MobileSessionStatus.emailVerificationRequired,
      );
      expect(find.byKey(const Key('mobile-otp-code')), findsOneWidget);
      expect(find.byKey(const Key('mobile-otp-resend')), findsOneWidget);
      expect(find.byKey(const Key('mobile-otp-cancel')), findsOneWidget);
      expect(find.textContaining('driver@test.dev'), findsNothing);
      expect(find.text('test-password'), findsNothing);
      expect(find.textContaining('vence a las'), findsOneWidget);
      expect(dependenciesForTest.sessionController.accessToken, isNull);
    },
  );

  testWidgets('keeps login controls reachable with 140 percent text scaling', (
    tester,
  ) async {
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
      assignmentGateway: FakeMobileAssignmentGateway(),
    );
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(1.4)),
        child: RutaFijaApp(
          environment: environment,
          dependencies: dependenciesForTest,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final submit = find.byKey(const Key('mobile-login-submit'));
    await tester.ensureVisible(submit);
    expect(submit, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('announces a login error through a live semantic region', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      final dependenciesForTest = dependencies(
        authGateway: FakeMobileAuthGateway(
          loginFailure: const MobileApiException(
            status: 401,
            code: 'AUTH_INVALID_CREDENTIALS',
            message: 'No usar este mensaje técnico.',
          ),
        ),
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
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
        'incorrect-password',
      );
      await tester.tap(find.byKey(const Key('mobile-login-submit')));
      await tester.pumpAndSettle();

      final error = find.bySemanticsLabel(
        'Error de acceso: El correo o la contraseña no son correctos.',
      );
      expect(error, findsOneWidget);
      expect(
        tester
            .getSemantics(error)
            .getSemanticsData()
            .flagsCollection
            .isLiveRegion,
        isTrue,
      );
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('does not expose the legacy pending-sync screen or action', (
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

    expect(find.byKey(const Key('pending-sync-open')), findsNothing);
    unawaited(
      tester
          .state<NavigatorState>(find.byType(Navigator))
          .pushNamed<void>('/sync-pending'),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('pending-sync-open')), findsNothing);
    expect(find.textContaining('Pendientes'), findsNothing);
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

  testWidgets('changes availability through the online mobile API gateway', (
    tester,
  ) async {
    final driverGateway = FakeMobileDriverGateway();
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: driverGateway,
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

    await tester.tap(find.byKey(const Key('nav-availability')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Descanso'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const Key('availability-save')),
      180,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.ensureVisible(find.byKey(const Key('availability-save')));
    await tester.tap(find.byKey(const Key('availability-save')));
    await tester.pumpAndSettle();

    expect(driverGateway.changedTo, [MobileAvailabilityStatus.descanso]);
    expect(
      driverGateway.currentAvailability.status,
      MobileAvailabilityStatus.descanso,
    );
    expect(find.byKey(const Key('pending-sync-open')), findsNothing);
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

  testWidgets('reports a real own incident without sending another identity', (
    tester,
  ) async {
    final incidentGateway = FakeMobileIncidentGateway();
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
      assignmentGateway: FakeMobileAssignmentGateway(),
      incidentGateway: incidentGateway,
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

    await tester.tap(find.byKey(const Key('nav-incidents')));
    await tester.pumpAndSettle();
    expect(find.text('Incidencias'), findsWidgets);
    await tester.tap(find.byKey(const Key('incident-report-open')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('incident-category')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retraso').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('incident-severity')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Baja').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('incident-description')),
      'Demora comprobada en la ruta.',
    );
    await tester.tap(find.byKey(const Key('incident-submit')));
    await tester.pumpAndSettle();

    expect(incidentGateway.createdCategories, [MobileIncidentCategory.retraso]);
    expect(incidentGateway.createdDescriptions, [
      'Demora comprobada en la ruta.',
    ]);
    expect(incidentGateway.createdAssignmentIds, [isNull]);
  });

  testWidgets('closes an own mobile incident once without deleting it', (
    tester,
  ) async {
    final incidentGateway = FakeMobileIncidentGateway();
    final dependenciesForTest = dependencies(
      authGateway: FakeMobileAuthGateway(),
      driverGateway: FakeMobileDriverGateway(),
      assignmentGateway: FakeMobileAssignmentGateway(),
      incidentGateway: incidentGateway,
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

    await tester.tap(find.byKey(const Key('nav-incidents')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const Key('incident-44444444-4444-4444-8444-444444444444')),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('incident-close')), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('incident-close')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('incident-close')));
    await tester.pumpAndSettle();
    expect(find.text('Cerrar incidencia'), findsOneWidget);
    await tester.tap(find.byKey(const Key('incident-close-confirm')));
    await tester.pumpAndSettle();

    expect(incidentGateway.closedIncidentIds, [
      '44444444-4444-4444-8444-444444444444',
    ]);
    expect(find.byKey(const Key('incident-close')), findsNothing);
  });

  testWidgets(
    'confirms announcement reading only after explicit conductor action',
    (tester) async {
      final announcementGateway = FakeMobileAnnouncementGateway();
      final dependenciesForTest = dependencies(
        authGateway: FakeMobileAuthGateway(),
        driverGateway: FakeMobileDriverGateway(),
        assignmentGateway: FakeMobileAssignmentGateway(),
        announcementGateway: announcementGateway,
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

      await tester.tap(find.byKey(const Key('nav-announcements')));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(
          const Key('announcement-66666666-6666-4666-8666-666666666666'),
        ),
      );
      await tester.pumpAndSettle();
      expect(announcementGateway.markedReadIds, isEmpty);
      await tester.tap(find.byKey(const Key('announcement-confirm-read')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Confirmar lectura').last);
      await tester.pumpAndSettle();

      expect(announcementGateway.markedReadIds, [
        '66666666-6666-4666-8666-666666666666',
      ]);
      expect(find.text('Lectura confirmada'), findsWidgets);
    },
  );
}
