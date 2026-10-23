import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_applicant_dossier.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_applicant_requests_page.dart';

import '../../support/documentary_fakes.dart';
import '../../support/mobile_fakes.dart';

void main() {
  Future<void> mount(
    WidgetTester tester,
    FakeMobileApplicantDocumentaryGateway gateway, {
    VoidCallback? onExpired,
    VoidCallback? onReturn,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: MobileApplicantDossierView(
                gateway: gateway,
                token: FakeMobileApplicantAccessGateway.token,
                requestId: 'synthetic-request-id',
                onExpired: onExpired ?? () {},
                onReturnToRequests: onReturn ?? () {},
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  Future<void> selectIdentity(WidgetTester tester) async {
    await tap(tester, find.byKey(const ValueKey('document-requirement-null')));
    await tester.tap(find.text('CON-01: Identidad del postulante').last);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'shows observations, rounds, deadline and history without stored personal fields',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway();
      await mount(tester, gateway);
      expect(find.text('Catálogo L01-CHICLAYO v0.1.0'), findsOneWidget);
      expect(
        find.text(
          'Indicación de subsanación: CON-01: confirme la vigencia documental.',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('Complete los requisitos observados.'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Versión del expediente: 7. Rondas: 1.'),
        findsOneWidget,
      );
      expect(find.textContaining('17/10/2026 10:00'), findsOneWidget);
      for (final text in [
        'PII-must-be-discarded',
        'Name must not render',
        'Private condition must not render',
        'Private source must not render',
        'Private review must not render',
      ]) {
        expect(find.textContaining(text), findsNothing);
      }
      await selectIdentity(tester);
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const Key('document-field-documentNumber')),
            )
            .controller!
            .text,
        isEmpty,
      );
      expect(
        tester
            .widget<TextField>(
              find.descendant(
                of: find.byKey(const Key('document-field-documentNumber')),
                matching: find.byType(TextField),
              ),
            )
            .obscureText,
        isTrue,
      );
      expect(
        find.textContaining(
          'grupo, unidad vinculada ni habilitación operativa',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'saves one fresh requirement with Peru end-of-day and then resubmits using returned version',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway();
      await mount(tester, gateway);
      await selectIdentity(tester);
      await tester.enterText(
        find.byKey(const Key('document-field-documentNumber')),
        '12345678',
      );
      await tap(tester, find.byKey(const ValueKey('document-expiry-UNKNOWN')));
      await tester.tap(find.text('Con fecha de vencimiento').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('document-valid-until')),
        '2026-02-30',
      );
      await tap(tester, find.byKey(const Key('document-save')));
      expect(gateway.saves, isEmpty);
      expect(
        find.text('Ingrese una fecha real en formato AAAA-MM-DD.'),
        findsOneWidget,
      );
      await tester.enterText(
        find.byKey(const Key('document-valid-until')),
        '2028-01-31',
      );
      await tap(tester, find.byKey(const Key('document-save')));

      expect(gateway.saves.single.version, 7);
      expect(
        gateway.saves.single.operationKey,
        matches(RegExp(r'^[0-9a-f-]{36}$')),
      );
      expect(gateway.saves.single.document.fields, {
        'documentNumber': '12345678',
      });
      expect(
        gateway.saves.single.document.validUntil,
        '2028-01-31T23:59:59-05:00',
      );
      expect(gateway.saves.single.document.applicability, 'POR_CONTRASTAR');
      expect(
        find.byKey(const Key('document-field-documentNumber')),
        findsNothing,
      );
      expect(
        find.textContaining(
          'El requisito permanece pendiente de revisión ADMIN.',
        ),
        findsOneWidget,
      );
      await tap(tester, find.byKey(const Key('document-resubmit')));
      expect(gateway.resubmissions.single.version, 8);
      expect(
        gateway.resubmissions.single.operationKey,
        isNot(gateway.saves.single.operationKey),
      );
      expect(find.byKey(const Key('document-resubmit')), findsNothing);
      expect(find.text('Pendiente de revisión ADMIN'), findsOneWidget);
      expect(gateway.events, ['GET', 'PUT', 'RESUBMIT']);
    },
  );

  testWidgets(
    '409 consults before another mutation, clears PII, and never retries automatically',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway();
      await mount(tester, gateway);
      await selectIdentity(tester);
      await tester.enterText(
        find.byKey(const Key('document-field-fullName')),
        'Transient synthetic name',
      );
      gateway.nextMutationFailure = const MobileApiException(
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: 'Changed',
      );
      gateway.json = {...gateway.json, 'version': 10};
      await tap(tester, find.byKey(const Key('document-save')));

      expect(gateway.events, ['GET', 'PUT', 'GET']);
      expect(gateway.saves, hasLength(1));
      expect(find.textContaining('no se repitió la operación'), findsOneWidget);
      await selectIdentity(tester);
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const Key('document-field-fullName')),
            )
            .controller!
            .text,
        isEmpty,
      );
      await tester.enterText(
        find.byKey(const Key('document-field-fullName')),
        'Explicit new input',
      );
      await tap(tester, find.byKey(const Key('document-save')));
      expect(gateway.saves.last.version, 10);
      expect(
        gateway.saves.last.operationKey,
        isNot(gateway.saves.first.operationKey),
      );
      expect(gateway.events, ['GET', 'PUT', 'GET', 'PUT']);
    },
  );

  testWidgets(
    'a lost response plus failed consultation blocks changes until explicit fresh GET',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway();
      await mount(tester, gateway);
      gateway.nextMutationFailure = const MobileNetworkException(
        'Lost response',
      );
      gateway.failReadAfterMutation = true;
      await tap(tester, find.byKey(const Key('document-resubmit')));

      expect(gateway.events, ['GET', 'RESUBMIT', 'GET']);
      expect(gateway.resubmissions, hasLength(1));
      expect(
        tester
            .widget<OutlinedButton>(find.byKey(const Key('document-resubmit')))
            .onPressed,
        isNull,
      );
      expect(
        find.textContaining(
          'Actualice el expediente antes de realizar cambios.',
        ),
        findsOneWidget,
      );
      gateway.json = {
        ...gateway.json,
        'status': 'PENDING_REVIEW',
        'version': 8,
      };
      await tap(tester, find.byKey(const Key('document-refresh')));
      expect(find.byKey(const Key('document-resubmit')), findsNothing);
      expect(gateway.resubmissions, hasLength(1));
      expect(gateway.events, ['GET', 'RESUBMIT', 'GET', 'GET']);
    },
  );

  testWidgets(
    'rejection requires a new invitation and switches to returned new request only after server confirmation',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway(status: 'REJECTED');
      await mount(tester, gateway);
      expect(find.byKey(const Key('document-save')), findsNothing);
      expect(find.byKey(const Key('document-resubmit')), findsNothing);
      await tester.enterText(
        find.byKey(const Key('document-reapply-code')),
        '004207',
      );
      await tap(tester, find.byKey(const Key('document-reapply')));
      expect(gateway.reapplications.single.invitationCode, '004207');
      expect(gateway.reapplications.single.version, 7);
      expect(find.text('Expediente: new-synthetic-request-id'), findsOneWidget);
      expect(find.textContaining('sin acceso operativo'), findsOneWidget);
      expect(find.byKey(const Key('document-reapply-code')), findsNothing);
    },
  );

  testWidgets(
    'reapplication respects future wait and uncertain response returns to own request list',
    (tester) async {
      var returnCalls = 0;
      final gateway = FakeMobileApplicantDocumentaryGateway(
        status: 'REJECTED',
        reapplyAfter: DateTime.now()
            .add(const Duration(days: 7))
            .toUtc()
            .toIso8601String(),
      );
      await mount(tester, gateway, onReturn: () => returnCalls++);
      expect(
        tester
            .widget<FilledButton>(find.byKey(const Key('document-reapply')))
            .onPressed,
        isNull,
      );
      gateway.json = {...gateway.json, 'reapplyAfter': null};
      await tap(tester, find.byKey(const Key('document-refresh')));
      gateway.nextMutationFailure = const MobileNetworkException(
        'Lost response',
      );
      await tester.enterText(
        find.byKey(const Key('document-reapply-code')),
        '004207',
      );
      await tap(tester, find.byKey(const Key('document-reapply')));
      expect(gateway.events, ['GET', 'GET', 'REAPPLY', 'GET']);
      expect(gateway.reapplications, hasLength(1));
      expect(returnCalls, 1);
    },
  );

  testWidgets('401 returns to access without retrying or granting a session', (
    tester,
  ) async {
    var expired = 0;
    final gateway = FakeMobileApplicantDocumentaryGateway();
    await mount(tester, gateway, onExpired: () => expired++);
    gateway.nextReadFailure = const MobileApiException(
      status: 401,
      code: 'AUTH_TOKEN_INVALID',
      message: 'Expired',
    );
    await tap(tester, find.byKey(const Key('document-refresh')));
    expect(expired, 1);
    expect(gateway.events, ['GET', 'GET']);
  });

  testWidgets(
    'unknown modality is cleared after a conflict and must be chosen again',
    (tester) async {
      final gateway = FakeMobileApplicantDocumentaryGateway();
      gateway.json = {...gateway.json, 'modality': null};
      await mount(tester, gateway);
      await tap(tester, find.byKey(const ValueKey('document-modality-null')));
      await tester.tap(find.text('Taxi').last);
      await tester.pumpAndSettle();
      await selectIdentity(tester);
      gateway.nextMutationFailure = const MobileApiException(
        status: 409,
        code: 'RESOURCE_VERSION_CONFLICT',
        message: 'Changed',
      );
      await tap(tester, find.byKey(const Key('document-save')));
      expect(gateway.saves, hasLength(1));
      expect(
        find.byKey(const ValueKey('document-modality-null')),
        findsOneWidget,
      );
      await selectIdentity(tester);
      await tap(tester, find.byKey(const Key('document-save')));
      expect(gateway.saves, hasLength(1));
      expect(
        find.text('Seleccione la modalidad del expediente.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'parent expiry removes documentary editor and revokes token with no persisted form',
    (tester) async {
      final access = FakeMobileApplicantAccessGateway(
        expiresAt: DateTime.now().add(const Duration(seconds: 20)),
      );
      final documentary = FakeMobileApplicantDocumentaryGateway();
      await tester.pumpWidget(
        MaterialApp(
          home: MobileApplicantRequestsPage(
            gateway: access,
            documentaryGateway: documentary,
          ),
        ),
      );
      await tester.enterText(
        find.byKey(const Key('applicant-email')),
        'synthetic@example.test',
      );
      await tester.enterText(
        find.byKey(const Key('applicant-password')),
        'synthetic-password',
      );
      await tap(tester, find.byKey(const Key('applicant-login')));
      await tap(
        tester,
        find.byKey(const Key('applicant-documentary-synthetic-request-id')),
      );
      await selectIdentity(tester);
      await tester.enterText(
        find.byKey(const Key('document-field-fullName')),
        'Transient synthetic name',
      );
      await tester.pump(const Duration(seconds: 21));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('applicant-login')), findsOneWidget);
      expect(find.byKey(const Key('document-field-fullName')), findsNothing);
      expect(access.loggedOutTokens, [FakeMobileApplicantAccessGateway.token]);
      expect(documentary.saves, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );
}
