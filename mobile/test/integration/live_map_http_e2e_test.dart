import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';

// A real Dart HTTP boundary and real API/PG. Only the sensor is synthetic.
// No physical Android, tile provider, shared database or production account is used.
void main() {
  final fixturePath = Platform.environment['RF_MAP_BROWSER_FIXTURE_PATH'];
  final stage = Platform.environment['RF_MAP_HTTP_STAGE'] ?? 'publish';
  test(
    'real isolated Flutter repositories -> Spring -> PostgreSQL map ($stage)',
    () async {
      var root = Directory.current.absolute;
      if (root.path.split(Platform.pathSeparator).last == 'mobile') {
        root = root.parent;
      }
      var allowed = File(
        '${root.path}${Platform.pathSeparator}.runtime${Platform.pathSeparator}'
        'live-map-isolated${Platform.pathSeparator}browser-fixture.json',
      ).absolute;
      if (Platform.environment['RF_MAP_ISOLATED_AUDIT'] ==
          'EXCLUSIVE_AUDIT_2026_11_13') {
        final auditRoot = Directory(
          '${root.path}${Platform.pathSeparator}.runtime'
          '${Platform.pathSeparator}auditoria-integral-2026-11-13'
          '${Platform.pathSeparator}postgresql${Platform.pathSeparator}mapa',
        ).resolveSymbolicLinksSync();
        final supplied = File(fixturePath!).resolveSymbolicLinksSync();
        final parent = File(supplied).parent;
        expect(parent.parent.resolveSymbolicLinksSync(), auditRoot);
        expect(
          parent.path.split(Platform.pathSeparator).last,
          matches(RegExp(r'^maven-[0-9a-f-]{36}$')),
        );
        expect(File(supplied).uri.pathSegments.last, 'browser-fixture.json');
        // Exact new private audit directory; never repoint or replace the old
        // fixture. The real HTTP target remains the dedicated API on 18080.
        allowed = File(supplied);
      }
      expect(File(fixturePath!).absolute.path, allowed.path);
      final fixture =
          jsonDecode(await allowed.readAsString()) as Map<String, dynamic>;
      expect(fixture['synthetic'], true);
      expect(['publish', 'revoke', 'complete'], contains(stage));
      final transport = _CountedHttpClient(http.Client());
      addTearDown(transport.close);
      final token = _TestToken(fixture['mobileToken'] as String);
      final api = MobileApiClient(
        httpClient: transport,
        environment: RouteFijaEnvironment.fromApiBaseUrl(
          'http://127.0.0.1:18080/api/v1',
        ),
        accessTokenProvider: token,
      );
      late MobileServiceLocationController controller;
      final driver = MobileDriverRepository(
        api,
        onConsentChanged: (consent) => controller.consentChanged(consent),
        onLocationCleared: () => controller.clearMapPoint(),
      );
      final commands = MobileSessionAssignmentCommandStore();
      final assignments = MobileAssignmentRepository(
        api,
        commands,
        onStateChanged: (assignment) => controller.observe(assignment),
      );
      final sensor = _SyntheticSensor();
      controller = MobileServiceLocationController(
        assignments: assignments,
        driver: driver,
        device: sensor,
      );
      addTearDown(controller.dispose);

      // Session identity is already issued by the isolated test API; credentials
      // and positions never appear in test names, assertion diagnostics or logs.
      if (stage == 'publish') {
        controller.setSession(fixture['driverId'] as String);
        controller.setMapVisible(true);
        controller.setForeground(true);
        await controller.tick();
        expect(controller.phase, ServiceLocationPhase.sharing);
        expect(controller.mapPhase, MapLocationPhase.ready);
        expect(controller.mapPoint, isNotNull);
        expect(transport.locationPuts, 1);
        expect(sensor.maxConcurrent, 1);
        final snapshot = await _adminFeed(transport, fixture);
        final own = (snapshot['items'] as List)
            .cast<Map<String, dynamic>>()
            .where((row) => row['driverId'] == fixture['driverId'])
            .single;
        final storedCapture = DateTime.parse(own['capturedAt'] as String);
        expect(
          storedCapture
              .difference(controller.mapPoint!.capturedAt)
              .abs()
              .inMicroseconds,
          lessThanOrEqualTo(1),
        );
        expect(own['freshness'], 'CURRENT');
        await _receipt(root, stage, {
          'realFlutterHttp': true,
          'realApiAndPostgres': true,
          'syntheticSensor': true,
          'physicalAndroid': false,
          'singleCaptureAndUpload': transport.locationPuts == 1,
          'captureConfirmedByAdminFeed': true,
        });
      } else if (stage == 'revoke') {
        final changed = await driver.changeLocationConsent(false);
        expect(changed.locationConsent, false);
        controller.setSession(fixture['driverId'] as String);
        controller.setMapVisible(true);
        controller.setForeground(true);
        await controller.tick();
        expect(controller.phase, ServiceLocationPhase.noConsent);
        expect(
          controller.mapPoint,
          isNotNull,
        ); // Personal remains usable locally.
        expect(transport.locationPuts, 0);
        expect((await _adminFeed(transport, fixture))['items'], isEmpty);
        await _receipt(root, stage, {
          'consentRevocationConfirmedByBackend': true,
          'snapshotRemovedFromAdminFeed': true,
          'personalPointWithoutUpload': true,
        });
      } else {
        final current = await assignments.get(
          fixture['assignmentId'] as String,
        );
        expect(current.status, MobileAssignmentStatus.enServicio);
        await assignments.send(current, MobileAssignmentCommandType.complete);
        expect(
          (await assignments.get(current.id)).status,
          MobileAssignmentStatus.completed,
        );
        controller.setSession(fixture['driverId'] as String);
        controller.setMapVisible(true);
        controller.setForeground(true);
        await controller.tick();
        expect(controller.hasActiveService, false);
        expect(controller.mapPoint, isNotNull);
        expect(transport.locationPuts, 0);
        expect(
          await commands.readForDriver(fixture['driverId'] as String),
          isEmpty,
        );
        expect((await _adminFeed(transport, fixture))['items'], isEmpty);
        controller.setForeground(false);
        expect(controller.mapPoint, isNull);
        await _receipt(root, stage, {
          'existingCompletionEndpointConfirmed': true,
          'personalMapAfterCompletionWithoutUpload': true,
          'backgroundClearsPersonalPoint': true,
          'offlineQueueAbsent': true,
        });
      }
    },
    skip: fixturePath == null
        ? 'Exclusive map test runtime not requested'
        : false,
  );
}

Future<Map<String, dynamic>> _adminFeed(
  http.Client client,
  Map<String, dynamic> fixture,
) async {
  final response = await client.get(
    Uri.parse('http://127.0.0.1:18080/api/v1/live-map/vehicles?limit=5'),
    headers: {'Authorization': 'Bearer ${fixture['adminToken']}'},
  );
  expect(response.statusCode, 200);
  expect(response.headers['cache-control'], contains('no-store'));
  return jsonDecode(response.body) as Map<String, dynamic>;
}

Future<void> _receipt(
  Directory root,
  String stage,
  Map<String, Object> facts,
) async {
  final destination = Platform.environment['RF_MAP_ISOLATED_AUDIT'] ==
          'EXCLUSIVE_AUDIT_2026_11_13'
      ? 'docs/auditoria-integral-2026-11-13/e2e/'
      : 'docs/mapa-en-vivo/verificacion/postgres/';
  final file = File(
    '${root.path}/$destination'
    'flutter-http-$stage.json',
  );
  await file.writeAsString(
    const JsonEncoder.withIndent('  ').convert({
      'recordedAt': DateTime.now().toUtc().toIso8601String(),
      'stage': stage,
      'result': 'PASS',
      ...facts,
    }),
  );
}

final class _TestToken implements MobileAccessTokenProvider {
  _TestToken(this.accessToken);
  @override
  final String accessToken;
  @override
  Future<bool> refreshAccessToken() async => false;
  @override
  Future<void> invalidateSession() async {}
}

final class _CountedHttpClient extends http.BaseClient {
  _CountedHttpClient(this.inner);
  final http.Client inner;
  int locationPuts = 0;
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    expect(request.url.host, '127.0.0.1');
    expect(request.url.port, 18080);
    if (request.method == 'PUT' &&
        request.url.path == '/api/v1/mobile/location/current') {
      locationPuts++;
    }
    return inner.send(request);
  }

  @override
  void close() => inner.close();
}

final class _SyntheticSensor implements MobileDeviceLocationGateway {
  int active = 0;
  int maxConcurrent = 0;
  @override
  Future<MobileCurrentLocation> currentPosition() async {
    active++;
    if (active > maxConcurrent) maxConcurrent = active;
    await Future<void>.delayed(const Duration(milliseconds: 5));
    active--;
    return MobileCurrentLocation(
      latitude: -6.770001,
      longitude: -79.840001,
      accuracyMeters: 5,
      capturedAt: DateTime.now().toUtc(),
    );
  }

  @override
  Future<bool> isLocationServiceEnabled() async => true;
  @override
  Future<MobileDeviceLocationPermission> checkPermission() async =>
      MobileDeviceLocationPermission.whileInUse;
  @override
  Future<MobileDeviceLocationPermission> requestPermission() =>
      checkPermission();
  @override
  Future<bool> openLocationSettings() async => false;
  @override
  Future<bool> openAppSettings() async => false;
}
