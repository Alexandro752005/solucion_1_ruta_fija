// Opt-in real HTTP repositories against the coordinator's existing L-02 test
// fixture. This test does not create accounts, bypass gates or clean PostgreSQL.
// It stores the supplied synthetic token in memory and never prints its value.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/qualification/data/mobile_qualification_repository.dart';

final class _VolatileSyntheticAccess implements MobileAccessTokenProvider {
  _VolatileSyntheticAccess(this.accessToken);
  @override
  String? accessToken;
  int invalidations = 0;
  @override
  Future<bool> refreshAccessToken() async => false;
  @override
  Future<void> invalidateSession() async {
    invalidations++;
    accessToken = null;
  }
}

void main() {
  final enabled = Platform.environment['RF_L02_CONNECTED_CLIENTS'] == 'true';
  test(
    'L02 production mobile repositories use real HTTP and PostgreSQL fixture',
    () async {
      final base = Platform.environment['RF_L02_CONNECTED_BASE'];
      final token = Platform.environment['RF_L02_CONNECTED_TOKEN'];
      final runId = Platform.environment['RF_L02_CONNECTED_RUN_ID'];
      final expectedDriver = Platform.environment['RF_L02_CONNECTED_DRIVER_ID'];
      final uri = Uri.tryParse(base ?? '');
      if (uri == null ||
          uri.scheme != 'http' ||
          uri.host != '127.0.0.1' ||
          uri.port < 1024 ||
          uri.path.isNotEmpty ||
          uri.query.isNotEmpty ||
          uri.fragment.isNotEmpty ||
          token == null ||
          token.isEmpty ||
          !RegExp(r'^[0-9a-f-]{36}$').hasMatch(runId ?? '') ||
          !RegExp(r'^[0-9a-f-]{36}$').hasMatch(expectedDriver ?? '')) {
        throw StateError(
          'L02 connected test configuration refused; details redacted.',
        );
      }
      final pieces = token.split('.');
      if (pieces.length != 3) {
        throw StateError('Synthetic mobile token refused.');
      }
      final claims = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(pieces[1]))),
      );
      if (claims is! Map ||
          claims['role'] != 'CONDUCTOR' ||
          claims['sessionChannel'] != 'MOBILE' ||
          claims['accessScope'] != 'INCORPORATION') {
        throw StateError(
          'Synthetic limited mobile claims required; details redacted.',
        );
      }
      final previousOverrides = HttpOverrides.current;
      HttpOverrides.global = null;
      final client = http.Client();
      final access = _VolatileSyntheticAccess(token);
      addTearDown(() {
        client.close();
        HttpOverrides.global = previousOverrides;
      });
      final api = MobileApiClient(
        httpClient: client,
        environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),
        accessTokenProvider: access,
      );
      final repository = MobileQualificationRepository(api);
      final before = await repository.qualification();
      expect(before.driverId, expectedDriver);
      expect(before.operationalEnabled, isFalse);
      expect(
        before.hasPendingUnitRequest,
        isFalse,
        reason:
            'Preserve the existing case; do not create successive requests.',
      );
      final catalog = await repository.personalCatalog();
      expect(
        catalog.map((item) => item.id).toSet(),
        containsAll(['CON-01', 'CON-02', 'CON-03', 'CON-04', 'CON-05']),
      );

      MobileApiException? denied;
      try {
        await api.getObject('mobile/me');
      } on MobileApiException catch (error) {
        denied = error;
      }
      expect(denied?.status, 403);
      expect(denied?.code, 'DRIVER_INACTIVE');
      expect(
        access.invalidations,
        0,
        reason: 'Limited access must retain consultation and correction.',
      );
      expect((await repository.qualification()).driverId, expectedDriver);

      final operationKey = 'l02-flutter-unit-$runId';
      final reference = 'SYNTHETIC-L02-${runId!.substring(0, 8)}';
      final accepted = await repository.requestUnit(
        expectedVersion: before.version,
        operationKey: operationKey,
        kind: 'OWN',
        reference: reference,
      );
      final acceptedRequest = accepted.unitRequests
          .where((item) => item.reference == reference)
          .single;
      expect(acceptedRequest.status, 'PENDING_REVIEW');
      expect(accepted.version, greaterThan(before.version));
      expect(accepted.operationalEnabled, isFalse);
      // This is an intentional exact idempotency check, not an automatic retry.
      final repeated = await repository.requestUnit(
        expectedVersion: before.version,
        operationKey: operationKey,
        kind: 'OWN',
        reference: reference,
      );
      expect(
        repeated.unitRequests.where((item) => item.id == acceptedRequest.id),
        hasLength(1),
      );
      expect(repeated.version, accepted.version);
      final persisted = await repository.qualification();
      expect(
        persisted.unitRequests.where((item) => item.id == acceptedRequest.id),
        hasLength(1),
      );
      expect(
        persisted.unitRequests.where((item) => item.status == 'PENDING_REVIEW'),
        hasLength(1),
      );
      expect(persisted.operationalEnabled, isFalse);
      // These markers contain only outcome and public fixture references.
      // ignore: avoid_print
      print(
        'L02_CONNECTED_FLUTTER=PASS_REAL_HTTP_LIMITED_SCOPE_AND_UNIT_REQUEST',
      );
      // ignore: avoid_print
      print('L02_CONNECTED_RUN_ID=$runId');
      // ignore: avoid_print
      print('L02_CONNECTED_UNIT_REQUEST_ID=${acceptedRequest.id}');
    },
    skip: !enabled,
  );
}
