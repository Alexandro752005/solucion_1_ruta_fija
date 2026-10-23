// Opt-in production Flutter repository -> real HTTP -> PostgreSQL, synthetic account only.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';

final class _VolatileAccess implements MobileAccessTokenProvider {
  _VolatileAccess(this.accessToken);
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
  final enabled = Platform.environment['RF_L04_CONNECTED_CLIENTS'] == 'true';
  test(
    'L04 own acceptance or rejection using actual online repository',
    () async {
      final env = Platform.environment,
          base = env['RF_L04_CONNECTED_BASE'],
          token = env['RF_L04_CONNECTED_TOKEN'];
      final stage = env['RF_L04_CONNECTED_STAGE'],
          offer = env['RF_L04_CONNECTED_OFFER_ID'],
          service = env['RF_L04_CONNECTED_SERVICE_ID'];
      final uri = Uri.tryParse(base ?? '');
      if (uri == null ||
          uri.host != '127.0.0.1' ||
          uri.scheme != 'http' ||
          uri.port < 1024 ||
          token == null ||
          !['ACCEPT', 'REJECT'].contains(stage) ||
          offer == null ||
          service == null) {
        throw StateError('L04 synthetic configuration refused.');
      }
      final parts = token.split('.');
      if (parts.length != 3) throw StateError('Synthetic access refused.');
      final claims = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      );
      if (claims['role'] != 'CONDUCTOR' ||
          claims['sessionChannel'] != 'MOBILE' ||
          claims['accessScope'] != 'OPERATIONS') {
        throw StateError('Own operational synthetic session required.');
      }
      final overrides = HttpOverrides.current;
      HttpOverrides.global = null;
      final client = http.Client();
      addTearDown(() {
        client.close();
        HttpOverrides.global = overrides;
      });
      final api = MobileApiClient(
        httpClient: client,
        environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),
        accessTokenProvider: _VolatileAccess(token),
      );
      final repository = MobileAssignmentRepository(
        api,
        MobileSessionAssignmentCommandStore(),
      );
      final before = await repository.get(offer);
      expect(before.serviceId, service);
      expect(before.requiresMobileResponse, isTrue);
      expect(before.acceptedAt, isNull);
      expect(before.canStart, isFalse);
      final result = await repository.send(
        before,
        stage == 'ACCEPT'
            ? MobileAssignmentCommandType.accept
            : MobileAssignmentCommandType.reject,
        rejectionReason: stage == 'REJECT'
            ? 'SYNTHETIC-EXPLICIT-REJECTION'
            : null,
      );
      final after = await repository.get(offer);
      expect(after.serviceId, service);
      if (stage == 'ACCEPT') {
        expect(after.status, MobileAssignmentStatus.scheduled);
        expect(after.acceptedAt, isNotNull);
        expect(after.startedAt, isNull);
        expect(after.canStart, isTrue);
      } else {
        expect(after.status, MobileAssignmentStatus.rejected);
        expect(after.acceptedAt, isNull);
        expect(after.rejectedAt, isNotNull);
        expect(after.canStart, isFalse);
      }
      expect(result.replayed, isFalse);
      expect(await repository.pendingForDriver(before.driverId), isEmpty);
      final route = await repository.assignedRoute(offer);
      expect(route.versionId, isNotNull);
      // ignore: avoid_print
      print(
        'CONNECTED_L04_FLUTTER_$stage=PASS_REAL_HTTP_POSTGRES_NO_OFFLINE_QUEUE',
      );
    },
    skip: !enabled,
  );
}
