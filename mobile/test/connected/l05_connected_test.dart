// Opt-in L05-CON-01: production repositories/widgets, real loopback HTTP/PG,
// explicit synthetic permission/sensor doubles. Never a human/device claim.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/service_handover_panel.dart';
import 'package:ruta_fija_conductor/features/assignments/presentation/service_attention_panel.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';

final class _FixtureToken implements MobileAccessTokenProvider {
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
  final enabled = Platform.environment['RF_L05_CONNECTED_CLIENTS'] == 'true';
  // Construct the actual transport before testWidgets installs its default HTTP double.
  final transport = IOClient(
    HttpClient()..connectionTimeout = const Duration(seconds: 10),
  );
  testWidgets(
    'L05 authorized synthetic client stage through actual HTTP',
    (tester) async {
      final env = Platform.environment,
          base = Platform.environment['RF_L05_CONNECTED_BASE'],
          token = Platform.environment['RF_L05_CONNECTED_TOKEN'];
      final offer = env['RF_L05_CONNECTED_OFFER_ID'],
          stage = env['RF_L05_CONNECTED_STAGE'];
      expect(RegExp(r'^http://127\.0\.0\.1:\d+$').hasMatch(base ?? ''), true);
      expect(token, isNotEmpty);
      expect(offer, isNotEmpty);
      final api = MobileApiClient(
        httpClient: transport,
        environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),
        accessTokenProvider: _FixtureToken(token),
      );
      final store = MobileSessionAssignmentCommandStore();
      final repository = MobileAssignmentRepository(
        api,
        store,
        startGpsProofProvider: () async => {
          'permissionGranted': true,
          'locationServiceEnabled': true,
          'foreground': true,
        },
      );
      final profile = MobileDriverRepository(api);
      Future<T> io<T>(Future<T> Function() work) async {
        final value = await tester.runAsync(work);
        return value as T;
      }

      Future<void> until(bool Function() ready) async {
        for (var i = 0; i < 500; i++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 40)),
          );
          await tester.pump();
          if (ready()) return;
          final error = tester.takeException();
          if (error != null) throw StateError('SYNTHETIC_WIDGET_FAILURE');
        }
        throw StateError('SYNTHETIC_WIDGET_WAIT');
      }

      var current = await io(() => repository.get(offer!));
      expect(current.driverId, env['RF_L05_CONNECTED_DRIVER_ID']);
      expect(current.serviceId, isNotNull);
      if (stage == 'ACCEPT_START' || stage == 'ACCEPT_ONLY') {
        if (current.status == MobileAssignmentStatus.pendingResponse) {
          final result = await io(
            () => repository.send(current, MobileAssignmentCommandType.accept),
          );
          current = result.assignment;
        }
        if (stage == 'ACCEPT_ONLY') {
          expect(current.status, MobileAssignmentStatus.scheduled);
          expect(current.startedAt, isNull);
          expect(current.canStart, false);
        } else {
          await io(() => profile.changeLocationConsent(true));
          if (current.status == MobileAssignmentStatus.scheduled) {
            final result = await io(
              () => repository.send(current, MobileAssignmentCommandType.start),
            );
            current = result.assignment;
          }
          expect(current.status, MobileAssignmentStatus.enServicio);
          expect(current.startedAt, isNotNull);
        }
      } else if (stage == 'ASSUME') {
        await io(() => profile.changeLocationConsent(true));
        var changes = 0;
        final blocks = <bool>[];
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SingleChildScrollView(
                child: ServiceHandoverPanel(
                  assignment: current,
                  gateway: repository,
                  onChanged: () => changes++,
                  onBlocked: blocks.add,
                ),
              ),
            ),
          ),
        );
        if (current.status == MobileAssignmentStatus.scheduled) {
          await until(
            () => find.byType(CheckboxListTile).evaluate().isNotEmpty,
          );
          expect(
            tester
                .widget<CheckboxListTile>(find.byType(CheckboxListTile))
                .value,
            false,
          );
          await tester.ensureVisible(find.byType(CheckboxListTile));
          await tester.tap(find.byType(CheckboxListTile));
          await tester.pump();
          await tester.ensureVisible(find.text('Asumir relevo'));
          await tester.tap(find.text('Asumir relevo'));
          await until(() => changes == 1);
          expect(blocks.first, true);
          expect(blocks.last, false);
        } else {
          expect(current.status, MobileAssignmentStatus.enServicio);
          await until(
            () => find.textContaining('Relevo asumido').evaluate().isNotEmpty,
          );
        }
        current = await io(() => repository.get(offer!));
        expect(current.status, MobileAssignmentStatus.enServicio);
        expect(
          (await io(() => repository.handoverView(offer!))).rows.any(
            (r) => r.incomingAssignmentId == offer && r.state == 'ASSUMED',
          ),
          true,
        );
      } else if (stage == 'POINT_COMPLETE') {
        if (current.status == MobileAssignmentStatus.enServicio) {
          await io(
            () => profile.upsertCurrentLocation(
              MobileCurrentLocation(
                latitude: -6.77,
                longitude: -79.84,
                accuracyMeters: 5,
                capturedAt: DateTime.now().toUtc(),
              ),
            ),
          );
          final result = await io(
            () =>
                repository.send(current, MobileAssignmentCommandType.complete),
          );
          current = result.assignment;
        }
        expect(current.status, MobileAssignmentStatus.completed);
        final route = await io(() => repository.assignedRoute(offer!));
        expect(route.declaredAt, isNotNull);
      } else if (stage == 'INTERRUPT') {
        var changes = 0;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SingleChildScrollView(
                child: ServiceAttentionPanel(
                  assignment: current,
                  gateway: repository,
                  onChanged: () => changes++,
                  onBlocked: (_) {},
                ),
              ),
            ),
          ),
        );
        if (current.status == MobileAssignmentStatus.enServicio) {
          await until(
            () => find
                .byType(DropdownButtonFormField<String>)
                .evaluate()
                .isNotEmpty,
          );
          await tester.tap(find.byType(DropdownButtonFormField<String>));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Imposibilidad real de servicio').last);
          await tester.pumpAndSettle();
          await tester.enterText(
            find.byType(TextField),
            'SYNTHETIC inability to continue safely',
          );
          await tester.pump();
          await tester.ensureVisible(find.byType(SwitchListTile));
          await tester.tap(find.byType(SwitchListTile));
          await tester.pump();
          await tester.ensureVisible(
            find.text('Registrar interrupción propia'),
          );
          await tester.tap(find.text('Registrar interrupción propia'));
          await until(() => changes == 1);
        } else {
          expect(current.status, MobileAssignmentStatus.interrupted);
          await until(
            () => find.textContaining('Incidencia').evaluate().isNotEmpty,
          );
        }
        current = await io(() => repository.get(offer!));
        // ignore: avoid_print
        print('L05_CONNECTED_INTERRUPTION_CHECK=STATUS');
        expect(current.status, MobileAssignmentStatus.interrupted);
        expect(current.completedAt, isNull);
        final rows = await io(() => repository.ownAttention(offer!));
        // ignore: avoid_print
        print('L05_CONNECTED_INTERRUPTION_CHECK=OWN_ATTENTION');
        expect(rows.where((r) => r.assignmentId == offer), hasLength(1));
      } else {
        fail('UNSUPPORTED_SYNTHETIC_STAGE');
      }
      expect(await repository.pendingForDriver(current.driverId), isEmpty);
      transport.close();
      await tester.pumpWidget(const SizedBox.shrink());
      // ignore: avoid_print
      print(
        'L05_CONNECTED_RESULT=PASS_PRODUCTION_CLIENT_HTTP_WITH_SYNTHETIC_SENSOR_STAGE_$stage',
      );
    },
    skip: !enabled,
    timeout: const Timeout(Duration(minutes: 3)),
  );
}
