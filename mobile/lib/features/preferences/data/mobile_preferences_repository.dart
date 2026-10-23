import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_preferences.dart';

final class MobilePreferencesRepository implements MobilePreferencesGateway {
  MobilePreferencesRepository(this.api);
  final MobileApiClient api;
  @override Future<MobilePreferences> read() async =>MobilePreferences.fromJson(await api.getObject('mobile/preferences'));
  @override Future<MobilePreferences> update({required int version,required bool optionalNoticeAlerts,required MobileTextSize textSize}) async =>
    MobilePreferences.fromJson(await api.putObject('mobile/preferences',{'version':version,'optionalNoticeAlerts':optionalNoticeAlerts,'textSize':textSize.apiValue}));
}
