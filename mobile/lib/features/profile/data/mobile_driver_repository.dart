import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_driver_models.dart';

abstract interface class MobileDriverGateway {
  Future<MobileDriverProfile> profile();

  Future<MobileAvailability> availability();

  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  );
}

final class MobileDriverRepository implements MobileDriverGateway {
  MobileDriverRepository(this._apiClient);

  final MobileApiClient _apiClient;

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
}
