import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

void main() {
  test('loads profile and submits only permitted availability states to the mobile API', () async {
    final requests = <http.Request>[];
    final client = MockClient((request) async {
      requests.add(request);
      if (request.method == 'GET') {
        return http.Response(
          jsonEncode({
            'driverId': 'driver-1',
            'fullName': 'Ana Conductor',
            'groupId': 'group-1',
            'groupName': 'Grupo Centro',
            'availabilityStatus': 'DISPONIBLE',
            'locationConsent': false,
            'primaryVehicle': {
              'id': 'vehicle-1',
              'plate': 'ABC-123',
              'status': 'DISPONIBLE',
            },
          }),
          200,
        );
      }
      return http.Response(
        jsonEncode({'driverId': 'driver-1', 'status': 'DESCANSO'}),
        200,
      );
    });
    final repository = MobileDriverRepository(
      MobileApiClient(
        httpClient: client,
        environment: RouteFijaEnvironment.fromApiBaseUrl(
          'https://api.example.test/api/v1',
        ),
        accessTokenProvider: const _StaticTokenProvider(),
      ),
    );

    final profile = await repository.profile();
    final updated = await repository.changeAvailability(
      MobileAvailabilityStatus.descanso,
    );

    expect(profile.fullName, 'Ana Conductor');
    expect(profile.primaryVehicle?.plate, 'ABC-123');
    expect(updated.status, MobileAvailabilityStatus.descanso);
    expect(requests[0].url.path, '/api/v1/mobile/me');
    expect(requests[1].url.path, '/api/v1/mobile/availability');
    expect(requests[1].method, 'PUT');
    expect(requests[1].body, jsonEncode({'status': 'DESCANSO'}));
    expect(requests[1].headers['authorization'], 'Bearer access-token');
  });

  test(
    'does not allow the app to force reserved or in-service status',
    () async {
      final repository = MobileDriverRepository(
        MobileApiClient(
          httpClient: MockClient((_) async => http.Response('{}', 200)),
          environment: RouteFijaEnvironment.fromApiBaseUrl(
            'https://api.example.test/api/v1',
          ),
          accessTokenProvider: const _StaticTokenProvider(),
        ),
      );

      await expectLater(
        repository.changeAvailability(MobileAvailabilityStatus.reservado),
        throwsArgumentError,
      );
      await expectLater(
        repository.changeAvailability(MobileAvailabilityStatus.enServicio),
        throwsArgumentError,
      );
    },
  );
}

final class _StaticTokenProvider implements MobileAccessTokenProvider {
  const _StaticTokenProvider();

  @override
  String get accessToken => 'access-token';

  @override
  Future<void> invalidateSession() async {}

  @override
  Future<bool> refreshAccessToken() async => false;
}
