import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/qualification/data/mobile_qualification_repository.dart';
import 'package:ruta_fija_conductor/features/qualification/domain/mobile_qualification.dart';
import 'package:ruta_fija_conductor/features/qualification/presentation/mobile_qualification_gate.dart';
import 'package:ruta_fija_conductor/features/qualification/presentation/mobile_qualification_page.dart';

import '../../support/mobile_fakes.dart';

Map<String, Object?> qualificationJson({bool enabled = false}) => {
  'driverId': '22222222-2222-4222-8222-222222222222',
  'version': 4,
  'suspended': false,
  'operationalEnabled': enabled,
  'personalDocumentsStatus': 'PENDING_REVIEW',
  'personalValidUntil': null,
  'blockers': ['UNIT_PERIOD_REQUIRED'],
  'periods': <Object?>[],
  'unitRequests': <Object?>[],
  'observedAt': '2026-10-18T14:00:00Z',
  // Deliberate unexpected PII to ensure the client does not retain/render it.
  'personalDocuments': [
    {
      'fields': {'fullName': 'SHOULD_NOT_APPEAR'},
    },
  ],
};

MobileAuthSession scopedSession(MobileAccessScope scope) {
  final source = testSession();
  return MobileAuthSession(
    accessToken: source.accessToken,
    refreshToken: source.refreshToken,
    expiresIn: source.expiresIn,
    refreshExpiresIn: source.refreshExpiresIn,
    user: source.user,
    accessScope: scope,
  );
}

void main() {
  test('parses server state conservatively and ignores personal documents', () {
    final state = MobileQualification.fromJson(qualificationJson());
    expect(state.operationalEnabled, isFalse);
    expect(state.blockers, ['UNIT_PERIOD_REQUIRED']);
    final incomplete = qualificationJson()..remove('operationalEnabled');
    expect(
      () => MobileQualification.fromJson(incomplete),
      throwsFormatException,
    );
  });

  test('personal correction guidance contains states without retaining dossier values', () {
    final data=qualificationJson();
    data['personalDocumentStates']=[{'requirementId':'CON-02','result':'OBSERVADO','applicability':'CONFIRMED','observation':'Revise la vigencia del requisito.','fields':{'licenseNumber':'SHOULD_NOT_APPEAR'}}];
    final state=MobileQualification.fromJson(data);
    expect(state.personalDocumentStates.single.requirementId,'CON-02');
    expect(state.personalDocumentStates.single.observation,'Revise la vigencia del requisito.');
  });

  test('authentication scope is explicit and unknown defaults to incorporation', () {
    Map<String, Object?> session(String scope) => {
      'tokenType': 'Bearer',
      'accessToken': 'synthetic-not-jwt',
      'refreshToken': 'synthetic-refresh',
      'expiresIn': 900,
      'refreshExpiresIn': 1000,
      'accessScope': scope,
      'user': {
        'id': 'u',
        'driverId': 'd',
        'fullName': 'hidden',
        'role': 'CONDUCTOR',
      },
    };
    expect(
      MobileAuthSession.fromJson(session('OPERATIONS')).accessScope,
      MobileAccessScope.operations,
    );
    expect(
      MobileAuthSession.fromJson(session('UNKNOWN')).accessScope,
      MobileAccessScope.incorporation,
    );
    final missing = session('OPERATIONS')..remove('accessScope');
    expect(
      MobileAuthSession.fromJson(missing).accessScope,
      MobileAccessScope.incorporation,
    );
    final jwt = session('OPERATIONS')..remove('accessScope');
    jwt['accessToken'] =
        'header.${base64Url.encode(utf8.encode(jsonEncode({'accessScope': 'OPERATIONS'}))).replaceAll('=', '')}.signature';
    expect(
      MobileAuthSession.fromJson(jwt).accessScope,
      MobileAccessScope.operations,
    );
  });

  test(
    'real repository uses own authenticated routes and pending document result',
    () async {
      final calls = <http.Request>[];
      final auth = FakeMobileAuthGateway();
      final session = MobileSessionController(
        authGateway: auth,
        tokenStore: InMemoryRefreshTokenStore(),
      );
      await session.login(
        email: 'synthetic@example.test',
        password: 'synthetic',
      );
      final client = MockClient((request) async {
        calls.add(request);
        expect(request.headers['authorization'], 'Bearer access-token');
        expect(request.headers['cache-control'], 'no-store');
        if (request.url.path.endsWith('/personal-catalog')) {
          return http.Response(
            jsonEncode([
              {
                'requirementId': 'CON-02',
                'title': 'Licencia',
                'mandatory': true,
                'fieldKeys': ['licenseNumber'],
              },
            ]),
            200,
          );
        }
        return http.Response(jsonEncode(qualificationJson()), 200);
      });
      addTearDown(client.close);
      addTearDown(session.dispose);
      final repository = MobileQualificationRepository(
        MobileApiClient(
          httpClient: client,
          accessTokenProvider: session,
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
        ),
      );
      await repository.qualification();
      expect((await repository.personalCatalog()).single.id, 'CON-02');
      await repository.requestUnit(
        expectedVersion: 4,
        operationKey: 'synthetic-operation-1',
        kind: 'OWN',
        reference: 'SYNTHETIC-UNIT',
      );
      await repository.presentDocument(
        expectedVersion: 4,
        operationKey: 'synthetic-operation-2',
        document: const MobileQualificationDocumentInput(
          requirementId: 'CON-02',
          fields: {'licenseNumber': 'SYNTHETIC'},
          expiryMode: 'UNKNOWN',
          validUntil: null,
          applicability: 'POR_CONTRASTAR',
        ),
      );
      expect(calls.map((request) => request.url.path), [
        '/api/v1/mobile/qualification',
        '/api/v1/mobile/qualification/personal-catalog',
        '/api/v1/mobile/qualification/unit-requests',
        '/api/v1/mobile/qualification/documents',
      ]);
      final body = jsonDecode(calls.last.body) as Map<String, dynamic>;
      expect(body['expectedVersion'], 4);
      expect(body['documents'][0]['result'], 'PENDING');
      expect(body['documents'][0]['reviewReference'], isNull);
      expect(body.containsKey('driverId'), isFalse);
    },
  );

  test(
    'operational denial retains the valid limited consultation session',
    () async {
      final session = MobileSessionController(
        authGateway: FakeMobileAuthGateway(
          session: scopedSession(MobileAccessScope.incorporation),
        ),
        tokenStore: InMemoryRefreshTokenStore(),
      );
      await session.login(
        email: 'synthetic@example.test',
        password: 'synthetic',
      );
      addTearDown(session.dispose);
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/mobile/me')) {
          return http.Response(
            jsonEncode({
              'code': 'DRIVER_INACTIVE',
              'message': 'Synthetic limited scope',
            }),
            403,
          );
        }
        return http.Response(jsonEncode(qualificationJson()), 200);
      });
      addTearDown(client.close);
      final api = MobileApiClient(
        httpClient: client,
        accessTokenProvider: session,
        environment: RouteFijaEnvironment.fromApiBaseUrl(
          'https://api.example.test/api/v1',
        ),
      );
      await expectLater(
        api.getObject('mobile/me'),
        throwsA(
          isA<MobileApiException>().having(
            (error) => error.status,
            'status',
            403,
          ),
        ),
      );
      expect(session.state.status, MobileSessionStatus.authenticated);
      expect(session.accessToken, isNotNull);
      final qualification = await MobileQualificationRepository(api)
          .qualification();
      expect(qualification.operationalEnabled, isFalse);
    },
  );

  testWidgets(
    'limited token cannot open operations even when state is enabled',
    (tester) async {
      final auth = FakeMobileAuthGateway(
        session: scopedSession(MobileAccessScope.incorporation),
      );
      final session = MobileSessionController(
        authGateway: auth,
        tokenStore: InMemoryRefreshTokenStore(),
      );
      await session.login(
        email: 'synthetic@example.test',
        password: 'synthetic',
      );
      addTearDown(session.dispose);
      final gateway = FakeMobileQualificationGateway(
        state: MobileQualification.fromJson(qualificationJson(enabled: true)),
      );
      var operationalBuilds = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: MobileQualificationGate(
            gateway: gateway,
            sessionController: session,
            operationalBuilder: (_) {
              operationalBuilds++;
              return const Text('OPERATIONS');
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(operationalBuilds, 0);
      expect(find.text('Mi incorporación'), findsOneWidget);
      expect(find.text('SHOULD_NOT_APPEAR'), findsNothing);

      auth.session = scopedSession(MobileAccessScope.operations);
      final renew = find.byKey(const Key('qualification-refresh-session'));
      await tester.scrollUntilVisible(
        renew,
        350,
        scrollable: find
            .descendant(
              of: find.byType(ListView),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(renew);
      await tester.pumpAndSettle();
      expect(auth.refreshedTokens, hasLength(1));
      expect(find.text('OPERATIONS'), findsOneWidget);
    },
  );

  testWidgets('server suspension blocks an operations token', (tester) async {
    final session = MobileSessionController(
      authGateway: FakeMobileAuthGateway(),
      tokenStore: InMemoryRefreshTokenStore(),
    );
    await session.login(email: 'synthetic@example.test', password: 'synthetic');
    addTearDown(session.dispose);
    final state = qualificationJson()..['suspended'] = true;
    var builds = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: MobileQualificationGate(
          gateway: FakeMobileQualificationGateway(
            state: MobileQualification.fromJson(state),
          ),
          sessionController: session,
          operationalBuilder: (_) {
            builds++;
            return const Text('OPERATIONS');
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(builds, 0);
    expect(find.text('Suspensión operativa registrada.'), findsOneWidget);
  });

  testWidgets('lost response consults state without repeating a mutation', (
    tester,
  ) async {
    final session = MobileSessionController(
      authGateway: FakeMobileAuthGateway(),
      tokenStore: InMemoryRefreshTokenStore(),
    );
    addTearDown(session.dispose);
    final gateway =
        FakeMobileQualificationGateway(
            state: MobileQualification.fromJson(qualificationJson()),
          )
          ..mutationFailure = const MobileNetworkException(
            'synthetic lost response',
          );
    await tester.pumpWidget(
      MaterialApp(
        home: MobileQualificationPage(
          gateway: gateway,
          sessionController: session,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('qualification-unit-reference')),
      'SYNTHETIC-UNIT',
    );
    final submit = find.byKey(const Key('qualification-unit-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(gateway.unitWrites, 1);
    expect(gateway.reads, 2);
    expect(
      find.textContaining('La respuesta anterior no se confirmó'),
      findsOneWidget,
    );
    expect(find.text('SYNTHETIC-UNIT'), findsNothing);
    expect(
      find.textContaining('Solicitud recibida por el servidor'),
      findsNothing,
    );
  });

  testWidgets('failed initial check never builds operational content', (
    tester,
  ) async {
    final session = MobileSessionController(
      authGateway: FakeMobileAuthGateway(),
      tokenStore: InMemoryRefreshTokenStore(),
    );
    addTearDown(session.dispose);
    var builds = 0;
    final gateway = FakeMobileQualificationGateway()
      ..readFailure = const MobileNetworkException('synthetic network failure');
    await tester.pumpWidget(
      MaterialApp(
        home: MobileQualificationGate(
          gateway: gateway,
          sessionController: session,
          operationalBuilder: (_) {
            builds++;
            return const Text('OPERATIONS');
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(builds, 0);
    expect(find.text('Consultar estado'), findsOneWidget);
  });

  testWidgets(
    'structured renewal is masked, sent once and never auto-approved',
    (tester) async {
      tester.view.physicalSize = const Size(1200, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
      final session = MobileSessionController(
        authGateway: FakeMobileAuthGateway(),
        tokenStore: InMemoryRefreshTokenStore(),
      );
      addTearDown(session.dispose);
      final gateway = FakeMobileQualificationGateway(
        state: MobileQualification.fromJson(qualificationJson()),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: MobileQualificationPage(
            gateway: gateway,
            sessionController: session,
          ),
        ),
      );
      await tester.pumpAndSettle();
      final select = find.byKey(const ValueKey('qualification-document-null'));
      await tester.ensureVisible(select);
      await tester.tap(select);
      await tester.pumpAndSettle();
      await tester.tap(find.text('CON-02: Licencia').last);
      await tester.pumpAndSettle();
      final license = find.byKey(
        const Key('qualification-document-licenseNumber'),
      );
      expect(tester.widget<TextFormField>(license).builder, isNotNull);
      final editable = find.descendant(
        of: license,
        matching: find.byType(EditableText),
      );
      expect(tester.widget<EditableText>(editable).obscureText, isTrue);
      await tester.enterText(license, 'SYNTHETIC-LICENSE');
      await tester.tap(
        find.byKey(const ValueKey('qualification-expiry-UNKNOWN')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Con vencimiento').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('qualification-valid-until')),
        '2026-12-31',
      );
      final submit = find.byKey(const Key('qualification-document-submit'));
      await tester.ensureVisible(submit);
      await tester.tap(submit);
      await tester.pumpAndSettle();
      expect(gateway.documentWrites, 1);
      expect(gateway.lastDocument?.validUntil, '2027-01-01T00:00:00-05:00');
      expect(gateway.unitWrites, 0);
      expect(find.text('SYNTHETIC-LICENSE'), findsNothing);
      expect(
        find.textContaining('No equivalen a renovación aprobada'),
        findsOneWidget,
      );
      expect(find.textContaining('Operación bloqueada'), findsOneWidget);
    },
  );

  testWidgets('unreconciled conflict keeps mutations disabled', (tester) async {
    final session = MobileSessionController(
      authGateway: FakeMobileAuthGateway(),
      tokenStore: InMemoryRefreshTokenStore(),
    );
    addTearDown(session.dispose);
    final gateway = FakeMobileQualificationGateway(
      state: MobileQualification.fromJson(qualificationJson()),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: MobileQualificationPage(
          gateway: gateway,
          sessionController: session,
        ),
      ),
    );
    await tester.pumpAndSettle();
    gateway.mutationFailure = const MobileApiException(
      status: 409,
      code: 'QUALIFICATION_VERSION_CONFLICT',
      message: 'synthetic conflict',
    );
    gateway.readFailure = const MobileNetworkException(
      'synthetic connection lost',
    );
    await tester.enterText(
      find.byKey(const Key('qualification-unit-reference')),
      'SYNTHETIC',
    );
    final submit = find.byKey(const Key('qualification-unit-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      submit,
      300,
      scrollable: find
          .descendant(
            of: find.byType(ListView),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(tester.widget<FilledButton>(submit).onPressed, isNull);
    expect(gateway.unitWrites, 1);
  });
}
