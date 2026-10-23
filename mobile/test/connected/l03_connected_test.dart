// Negative real TCP check only. Happy own-route behavior has separate HTTP/PG and model tests.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';

final class _VolatileAdmin implements MobileAccessTokenProvider {
  _VolatileAdmin(this.accessToken);
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
  test(
    'L03 real backend rejects a CRM identity on the own mobile route',
    () async {
      final base = Platform.environment['RF_L03_CONNECTED_BASE'];
      final control = Platform.environment['RF_L03_CONNECTED_CONTROL'];
      if (base == null ||
          !RegExp(r'^http://127\.0\.0\.1:\d+$').hasMatch(base) ||
          control == null) {
        throw StateError('EXPLICIT_LOOPBACK_TEST_REQUIRED');
      }
      final previous = HttpOverrides.current;
      HttpOverrides.global = null;
      final client = http.Client();
      addTearDown(() {
        client.close();
        HttpOverrides.global = previous;
      });
      final r = await client.get(
        Uri.parse('$base/__l03/state'),
        headers: {'X-L03-Control': control},
      );
      if (r.statusCode != 200) throw StateError('CONTROL_REFUSED');
      final state = jsonDecode(r.body) as Map<String, dynamic>;
      final access = _VolatileAdmin(state['adminToken'] as String);
      final api = MobileApiClient(
        httpClient: client,
        environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),
        accessTokenProvider: access,
      );
      final repository = MobileAssignmentRepository(
        api,
        MobileSessionAssignmentCommandStore(),
      );
      await expectLater(
        repository.assignedRoute(state['vehicleId'] as String),
        throwsA(
          isA<MobileApiException>().having(
            (e) => e.status,
            'role rejection',
            403,
          ),
        ),
      );
      // Wrong object id does not demonstrate object isolation here; this case proves role rejection only.
      stdout.writeln(
        'CONNECTED_L03_FLUTTER_RESULT=PASS_REAL_HTTP_ADMIN_REJECTED_403_NOT_MOBILE_HAPPY_PATH',
      );
    },
    skip: Platform.environment['RF_L03_CONNECTED_CLIENTS'] != 'true',
  );
}
