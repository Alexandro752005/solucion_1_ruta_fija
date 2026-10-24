import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';

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
        jsonEncode({
          'driverId': 'driver-1',
          'status': 'DESCANSO',
          'updatedAt': '2026-09-25T12:00:00Z',
        }),
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

  test(
    'sends consent and one current point through own mobile endpoints only',
    () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        if (request.url.path == '/api/v1/mobile/location/consent') {
          return http.Response(
            jsonEncode({
              'driverId': 'driver-1',
              'fullName': 'Ana Conductor',
              'groupId': 'group-1',
              'groupName': 'Grupo Centro',
              'availabilityStatus': 'DISPONIBLE',
              'locationConsent': true,
              'primaryVehicle': null,
            }),
            200,
          );
        }
        return http.Response('', 204);
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

      final profile = await repository.changeLocationConsent(true);
      await repository.upsertCurrentLocation(
        MobileCurrentLocation(
          latitude: -12.046374,
          longitude: -77.042793,
          accuracyMeters: 8.2,
          capturedAt: DateTime.utc(2026, 9, 25, 12),
        ),
      );
      await repository.clearCurrentLocation();

      expect(profile.locationConsent, isTrue);
      expect(requests, hasLength(3));
      expect(requests[0].method, 'PUT');
      expect(requests[0].url.path, '/api/v1/mobile/location/consent');
      expect(jsonDecode(requests[0].body), {'consent': true});
      expect(requests[1].method, 'PUT');
      expect(requests[1].url.path, '/api/v1/mobile/location/current');
      expect(jsonDecode(requests[1].body), {
        'latitude': -12.046374,
        'longitude': -77.042793,
        'accuracyM': 8.2,
        'capturedAt': '2026-09-25T12:00:00.000Z',
        'permissionGranted': true,
      });
      expect(requests[1].body, isNot(contains('driverId')));
      expect(requests[2].method, 'DELETE');
      expect(requests[2].url.path, '/api/v1/mobile/location/current');
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
