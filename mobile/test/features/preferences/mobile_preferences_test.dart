import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/preferences/data/mobile_preferences_repository.dart';
import 'package:ruta_fija_conductor/features/preferences/domain/mobile_preferences.dart';
import 'package:ruta_fija_conductor/features/preferences/presentation/mobile_preferences_panel.dart';

final class _Token implements MobileAccessTokenProvider {
  @override String? get accessToken=>'SYNTHETIC_TOKEN';
  @override Future<bool> refreshAccessToken() async=>false;
  @override Future<void> invalidateSession() async{}
}
final class _Preferences implements MobilePreferencesGateway {
  MobilePreferences value=const MobilePreferences(version:0,optionalNoticeAlerts:true,textSize:MobileTextSize.normal);
  Completer<MobilePreferences>? pending;
  Object? failure;
  @override Future<MobilePreferences> read() async=>value;
  @override Future<MobilePreferences> update({required int version,required bool optionalNoticeAlerts,required MobileTextSize textSize}) async {
    if(failure!=null)throw failure!;
    if(pending!=null)return pending!.future;
    return value=MobilePreferences(version:version+1,optionalNoticeAlerts:optionalNoticeAlerts,textSize:textSize);
  }
}
final class _DeferredPreferences implements MobilePreferencesGateway {
  final Completer<MobilePreferences> response=Completer<MobilePreferences>();
  @override Future<MobilePreferences> read()=>response.future;
  @override Future<MobilePreferences> update({required int version,required bool optionalNoticeAlerts,required MobileTextSize textSize})=>throw UnimplementedError();
}
void main(){
  test('own preferences carry version only; no user or organization authority',() async {
    final requests=<http.Request>[];
    final repository=MobilePreferencesRepository(MobileApiClient(httpClient:MockClient((request)async{
      requests.add(request);return http.Response(jsonEncode({'version':request.method=='GET'?0:1,'optionalNoticeAlerts':false,'textSize':'LARGE','mandatoryNoticesAlwaysVisible':true}),200);
    }),environment:RouteFijaEnvironment.fromApiBaseUrl('https://api.example.test/api/v1'),accessTokenProvider:_Token()));
    await repository.read();final value=await repository.update(version:0,optionalNoticeAlerts:false,textSize:MobileTextSize.large);
    expect(value.textSize,MobileTextSize.large);expect(requests.first.url.path,'/api/v1/mobile/preferences');
    expect(jsonDecode(requests.last.body),{'version':0,'optionalNoticeAlerts':false,'textSize':'LARGE'});
    expect(requests.last.headers['authorization'],'Bearer SYNTHETIC_TOKEN');expect(requests.last.url.query,isEmpty);
  });
  test('mandatory notices cannot be disabled by response configuration',(){
    expect(()=>MobilePreferences.fromJson({'version':1,'optionalNoticeAlerts':false,'textSize':'NORMAL','mandatoryNoticesAlwaysVisible':false}),throwsFormatException);
  });
  test('session clear prevents delayed preference response from repopulating memory',()async{
    final gateway=_Preferences(),controller=MobilePreferencesController(_Preferences());
    final subject=MobilePreferencesController(gateway);await subject.setUser('SYNTHETIC');
    gateway.pending=Completer();final write=subject.save(version:0,optionalNoticeAlerts:false,textSize:MobileTextSize.extraLarge);
    await subject.setUser(null);gateway.pending!.complete(const MobilePreferences(version:1,optionalNoticeAlerts:false,textSize:MobileTextSize.extraLarge));await write;
    expect(subject.value,isNull);subject.dispose();controller.dispose();
  });
  test('disposed controller ignores an outstanding read without restoring account data',()async{
    final gateway=_DeferredPreferences(),controller=MobilePreferencesController(_DeferredPreferences());
    final subject=MobilePreferencesController(gateway);final reading=subject.setUser('SYNTHETIC');subject.dispose();
    gateway.response.complete(const MobilePreferences(version:1,optionalNoticeAlerts:false,textSize:MobileTextSize.large));await reading;
    expect(subject.value,isNull);controller.dispose();
  });
  testWidgets('preferences confirm only after response and always explain mandatory notices',(tester)async{
    final gateway=_Preferences()..pending=Completer<MobilePreferences>();final controller=MobilePreferencesController(gateway);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(child:MobilePreferencesPanel(controller:controller)))));await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('preference-optional-notices')));await tester.pump();
    await tester.ensureVisible(find.text('Guardar preferencias'));await tester.tap(find.text('Guardar preferencias'));await tester.pump();
    expect(find.text('Preferencias confirmadas por el servidor.'),findsNothing);expect(controller.value?.optionalNoticeAlerts,true);
    expect(find.textContaining('seguridad siempre'),findsOneWidget);
    gateway.pending!.complete(const MobilePreferences(version:1,optionalNoticeAlerts:false,textSize:MobileTextSize.normal));await tester.pumpAndSettle();
    expect(controller.value?.optionalNoticeAlerts,false);expect(find.text('Preferencias confirmadas por el servidor.'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());controller.dispose();
  });
  testWidgets('version conflict keeps server value and requires state consultation',(tester)async{
    final gateway=_Preferences()..failure=const MobileApiException(status:409,code:'RESOURCE_VERSION_CONFLICT',message:'Estado cambiado');final controller=MobilePreferencesController(gateway);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(child:MobilePreferencesPanel(controller:controller)))));await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('preference-optional-notices')));await tester.pump();await tester.ensureVisible(find.text('Guardar preferencias'));await tester.tap(find.text('Guardar preferencias'));await tester.pumpAndSettle();
    expect(controller.value?.optionalNoticeAlerts,true);expect(find.text('Consultar estado de preferencias'),findsOneWidget);
    expect(find.text('Preferencias confirmadas por el servidor.'),findsNothing);await tester.pumpWidget(const SizedBox.shrink());controller.dispose();
  });
}
