import '../../../core/network/mobile_api_client.dart';
import '../../location/domain/mobile_foreground_location.dart';
import '../domain/mobile_driver_models.dart';

abstract interface class MobileDriverGateway {
  Future<MobileDriverProfile> profile();

  Future<MobileAvailability> availability();

  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  );

  Future<MobileDriverProfile> changeLocationConsent(bool consent);

  Future<void> upsertCurrentLocation(MobileCurrentLocation location);

  Future<void> clearCurrentLocation();
}

final class MobileDriverRepository implements MobileDriverGateway {
  MobileDriverRepository(
    this._apiClient, {
    this.onConsentChanged,
    this.onLocationCleared,
  });

  final MobileApiClient _apiClient;
  final void Function(bool)? onConsentChanged;
  final void Function()? onLocationCleared;

  @override
  Future<MobileDriverProfile> profile() async {
    final response = await _apiClient.getObject('mobile/me');
    return MobileDriverProfile.fromJson(response);
  }

  @override
  Future<MobileAvailability> availability() async {
    final response = await _apiClient.getObject('mobile/availability');
    return MobileAvailability.fromJson(response);
  }

  @override
  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  ) async {
    if (!status.canBeChosenByDriver) {
      throw ArgumentError.value(
        status,
        'status',
        'El conductor no puede seleccionar este estado directamente.',
      );
    }
    final response = await _apiClient.putObject('mobile/availability', {
      'status': status.apiValue,
    });
    return MobileAvailability.fromJson(response);
  }

  @override
  Future<MobileDriverProfile> changeLocationConsent(bool consent) async {
    if (!consent) onConsentChanged?.call(false);
    final response = await _apiClient.putObject('mobile/location/consent', {
      'consent': consent,
    });
    final profile = MobileDriverProfile.fromJson(response);
    onConsentChanged?.call(profile.locationConsent);
    return profile;
  }

  @override
  Future<void> upsertCurrentLocation(MobileCurrentLocation location) {
    return _apiClient.putNoContent(
      'mobile/location/current',
      location.toRequestBody(),
      allowSessionRefresh: false,
    );
  }

  @override
  Future<void> clearCurrentLocation() {
    onLocationCleared?.call();
    return _apiClient.deleteNoContent('mobile/location/current');
  }
}
