// L06-CON-01, opt-in: real HTTP and production widgets/repositories.
// Automated synthetic interaction, not a human or physical Android validation.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/features/incidents/data/mobile_incident_repository.dart';
import 'package:ruta_fija_conductor/features/incidents/presentation/incident_report_page.dart';
import 'package:ruta_fija_conductor/features/incidents/presentation/incident_detail_page.dart';
import 'package:ruta_fija_conductor/features/announcements/data/mobile_announcement_repository.dart';
import 'package:ruta_fija_conductor/features/announcements/presentation/announcement_detail_page.dart';
import 'package:ruta_fija_conductor/features/announcements/data/mobile_notice_repository.dart';
import 'package:ruta_fija_conductor/features/announcements/presentation/notices_page.dart';

class _FixtureToken implements MobileAccessTokenProvider {
  _FixtureToken(this.accessToken);
  @override
  String? accessToken;
  @override
  Future<bool> refreshAccessToken() async => false;
  @override
  Future<void> invalidateSession() async {
    accessToken = null;
  }
}

void main() {
  final enabled = Platform.environment['RF_L06_CONNECTED_CLIENTS'] == 'true';
  // Created before testWidgets installs the framework's default HTTP double.
  final transport = IOClient(
    HttpClient()..connectionTimeout = const Duration(seconds: 10),
  );
  testWidgets(
    'L06 authorized synthetic communication stage through actual HTTP',
    (tester) async {
      final env = Platform.environment,
          base = Platform.environment['RF_L06_CONNECTED_BASE'],
          token = Platform.environment['RF_L06_CONNECTED_TOKEN'];
      expect(RegExp(r'^http://127\.0\.0\.1:\d+$').hasMatch(base ?? ''), true);
      expect(token, isNotEmpty);
      expect(
        env['RF_L06_CONNECTED_RUN_ID'],
        '51e69ee6-e4a0-4c3f-9a35-f19de0072422',
      );
      tester.view.physicalSize = const Size(1100, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      Future<T> io<T>(Future<T> Function() work) async {
        T? value;
        Object? failure;
        StackTrace? failureStack;
        // Keep expected HTTP rejections inside the matcher, rather than letting
        // runAsync report them as framework errors and return null.
        await tester.runAsync(() async {
          try {
            value = await work();
          } catch (error, stack) {
            failure = error;
            failureStack = stack;
          }
        });
        if (failure != null) {
          Error.throwWithStackTrace(failure!, failureStack!);
        }
        return value as T;
      }

      Future<void> until(bool Function() ready) async {
        for (int i = 0; i < 500; i++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 40)),
          );
          await tester.pump();
          final error = tester.takeException();
          if (error != null) throw StateError('SYNTHETIC_WIDGET_FAILURE');
          if (ready()) return;
        }
        throw StateError('SYNTHETIC_WIDGET_WAIT');
      }

      Future<void> tap(Finder finder) async {
        await tester.ensureVisible(finder);
        await tester.pump();
        await tester.tap(finder);
        await tester.pump();
      }

      final control = env['RF_L06_CONNECTED_CONTROL'];
      expect(control, isNotEmpty);
      final state = await io(() async {
        final r = await transport.get(
          Uri.parse('$base/__l06/state'),
          headers: {'X-L06-Control': control!},
        );
        expect(r.statusCode, 200);
        return jsonDecode(r.body) as Map<String, dynamic>;
      });
      final api = MobileApiClient(
        httpClient: transport,
        environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),
        accessTokenProvider: _FixtureToken(token),
      );
      final incidents = MobileIncidentRepository(api),
          announcements = MobileAnnouncementRepository(api),
          notices = MobileNoticeRepository(api);
      final stage = state['clientStage'] as String;
      if (stage == 'REPORT') {
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: FilledButton(
                  key: const Key('open-report'),
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) =>
                          IncidentReportPage(incidentGateway: incidents),
                    ),
                  ),
                  child: const Text('Abrir reporte'),
                ),
              ),
            ),
          ),
        );
        await tap(find.byKey(const Key('open-report')));
        await tester.pumpAndSettle();
        await tap(find.byKey(const Key('incident-severity')));
        await tester.pumpAndSettle();
        await tap(find.text('Baja').last);
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const Key('incident-description')),
          'SYNTHETIC connected incident report',
        );
        await tester.pump();
        await tap(find.byKey(const Key('incident-submit')));
        await until(
          () => find
              .byKey(const Key('incident-consult-operation'))
              .evaluate()
              .isNotEmpty,
        );
        await tap(find.byKey(const Key('incident-consult-operation')));
        await until(
          () => find.byKey(const Key('open-report')).evaluate().isNotEmpty,
        );
        final rows = await io(() => incidents.list());
        expect(
          rows.items.where(
            (r) => r.description == 'SYNTHETIC connected incident report',
          ),
          hasLength(1),
        );
        // ignore: avoid_print
        print(
          'L06_CONNECTED_REPORT=PASS_EXPLICIT_SEVERITY_UNKNOWN_RESPONSE_RECEIPT_NO_REPLAY',
        );
      } else if (stage == 'REPORT_RECONCILE') {
        final id = state['currentIncidentId'] as String;
        final row = await io(() => incidents.get(id));
        expect(row.reportedById, state['accountId']);
        final history = await io(() => incidents.history(id));
        expect(history.where((h) => h.kind == 'REPORT'), hasLength(1));
        // ignore: avoid_print
        print(
          'L06_CONNECTED_REPORT_RECONCILE=PASS_EXISTING_CASE_ONLY_NO_NEW_REPORT',
        );
      } else if (stage == 'INFORMATION') {
        final id = state['currentIncidentId'] as String;
        final previous = await io(() => incidents.history(id));
        await tester.pumpWidget(
          MaterialApp(
            home: IncidentDetailPage(
              incidentId: id,
              incidentGateway: incidents,
              userId: state['accountId'] as String,
            ),
          ),
        );
        await until(
          () => find
              .byKey(const Key('incident-information-note'))
              .evaluate()
              .isNotEmpty,
        );
        if (!previous.any(
          (r) =>
              r.kind == 'INFORMATION' &&
              r.detail['note'] == 'SYNTHETIC connected additional information',
        )) {
          await tester.enterText(
            find.byKey(const Key('incident-information-note')),
            'SYNTHETIC connected additional information',
          );
          await tester.pump();
          expect(
            tester
                .widget<FilledButton>(
                  find.byKey(const Key('incident-information-submit')),
                )
                .onPressed,
            isNotNull,
          );
          await tap(find.byKey(const Key('incident-information-submit')));
        }
        await until(
          () => find
              .byWidgetPredicate(
                (widget) =>
                    widget is Text &&
                    (widget.data?.contains(
                          'SYNTHETIC connected additional information',
                        ) ??
                        false),
              )
              .evaluate()
              .isNotEmpty,
        );
        final history = await io(() => incidents.history(id));
        expect(
          history.where(
            (r) =>
                r.kind == 'INFORMATION' &&
                r.detail['note'] ==
                    'SYNTHETIC connected additional information',
          ),
          hasLength(1),
        );
        // ignore: avoid_print
        print('L06_CONNECTED_INFORMATION=PASS_OWN_APPEND_ONLY_HISTORY');
      } else if (stage == 'CONSULT_INBOX') {
        final id = state['currentAnnouncementId'] as String;
        await tester.pumpWidget(
          MaterialApp(
            home: AnnouncementDetailPage(
              announcementId: id,
              announcementGateway: announcements,
            ),
          ),
        );
        await until(
          () => find
              .byKey(const Key('announcement-confirm-read'))
              .evaluate()
              .isNotEmpty,
        );
        expect((await io(() => announcements.get(id))).readAt, isNull);
        final inbox = await io(() => notices.list());
        final notice = inbox.items.firstWhere(
          (n) => n.references['announcementId'] == id,
        );
        await tester.pumpWidget(
          MaterialApp(home: NoticesPage(gateway: notices)),
        );
        await until(
          () =>
              find.byKey(Key('notice-read-${notice.id}')).evaluate().isNotEmpty,
        );
        await tap(find.byKey(Key('notice-read-${notice.id}')));
        await until(
          () => find.textContaining('Lectura de aviso:').evaluate().isNotEmpty,
        );
        expect((await io(() => announcements.get(id))).readAt, isNull);
        // ignore: avoid_print
        print(
          'L06_CONNECTED_INBOX=PASS_DURABLE_NOTICE_READ_SEPARATE_FROM_ANNOUNCEMENT_ACK',
        );
      } else if (stage == 'READ_ACK') {
        final id = state['currentAnnouncementId'] as String;
        expect((await io(() => announcements.get(id))).readAt, isNull);
        await expectLater(
          io(
            () => announcements.get(state['newGroupAnnouncementId'] as String),
          ),
          throwsA(
            isA<MobileApiException>().having((e) => e.status, 'status', 404),
          ),
        );
        await tester.pumpWidget(
          MaterialApp(
            home: AnnouncementDetailPage(
              announcementId: id,
              announcementGateway: announcements,
            ),
          ),
        );
        await until(
          () => find
              .byKey(const Key('announcement-confirm-read'))
              .evaluate()
              .isNotEmpty,
        );
        await tap(find.byKey(const Key('announcement-confirm-read')));
        await tester.pumpAndSettle();
        await tap(find.widgetWithText(FilledButton, 'Confirmar lectura').last);
        await until(
          () => find
              .text('El servidor ya registra su lectura.')
              .evaluate()
              .isNotEmpty,
        );
        expect((await io(() => announcements.get(id))).readAt, isNotNull);
        // ignore: avoid_print
        print(
          'L06_CONNECTED_READ_ACK=PASS_FORMER_GROUP_RECEPTION_RETAINED_NEW_MESSAGES_REJECTED_EXPLICIT_ACK',
        );
      } else if (stage == 'WITHDRAWN') {
        final id = state['currentAnnouncementId'] as String;
        await expectLater(
          io(() => announcements.get(id)),
          throwsA(
            isA<MobileApiException>().having((e) => e.status, 'status', 404),
          ),
        );
        final inbox = await io(() => notices.list());
        expect(
          inbox.items.where((n) => n.references['announcementId'] == id),
          isEmpty,
        );
        await tester.pumpWidget(
          MaterialApp(home: NoticesPage(gateway: notices)),
        );
        await until(() {
          final refresh = find.byKey(const Key('notices-refresh'));
          return refresh.evaluate().isNotEmpty &&
              tester.widget<TextButton>(refresh).onPressed != null &&
              find.byType(LinearProgressIndicator).evaluate().isEmpty;
        });
        expect(find.textContaining(id), findsNothing);
        // ignore: avoid_print
        print(
          'L06_CONNECTED_WITHDRAWN=PASS_SERVER_ACCESS_REVOKED_NOTICE_HIDDEN',
        );
      } else {
        fail('UNSUPPORTED_SYNTHETIC_STAGE');
      }
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 6));
      transport.close();
      // ignore: avoid_print
      print(
        'L06_CONNECTED_RESULT=PASS_PRODUCTION_WIDGET_REPOSITORY_HTTP_STAGE_$stage',
      );
    },
    skip: !enabled,
    timeout: const Timeout(Duration(minutes: 3)),
  );
}
