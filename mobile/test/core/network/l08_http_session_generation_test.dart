import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';

final class _GenerationToken implements MobileAccessTokenProvider,MobileSessionGenerationProvider {
  @override String? accessToken='SYNTHETIC_PREVIOUS_ACCESS';
  @override int sessionGeneration=1;
  int refreshCalls=0,invalidations=0;
  Completer<bool>? refreshResult;
  final refreshStarted=Completer<void>();
  void replace(){sessionGeneration++;accessToken='SYNTHETIC_NEW_ACCESS';}
  void clear(){sessionGeneration++;accessToken=null;}
  @override Future<bool> refreshAccessToken()async{
    refreshCalls++;if(!refreshStarted.isCompleted)refreshStarted.complete();
    if(refreshResult!=null)return refreshResult!.future;
    accessToken='SYNTHETIC_ROTATED_ACCESS';return true;
  }
  @override Future<void> invalidateSession()async{invalidations++;clear();}
}
void main(){
  MobileApiClient api(http.Client client,_GenerationToken token)=>MobileApiClient(httpClient:client,environment:RouteFijaEnvironment.fromApiBaseUrl('https://api.example.test/api/v1'),accessTokenProvider:token);
  Matcher changed()=>isA<MobileApiException>().having((error)=>error.code,'local reason','MOBILE_SESSION_CHANGED');
  test('L08 late401 from prior session neither refreshes nor invalidates the new identity',()async{
    final token=_GenerationToken(),held=Completer<http.Response>(),started=Completer<void>();var requests=0;
    final client=MockClient((request){requests++;expect(request.headers['authorization'],'Bearer SYNTHETIC_PREVIOUS_ACCESS');started.complete();return held.future;});
    final work=api(client,token).getObject('mobile/me');final rejected=expectLater(work,throwsA(changed()));await started.future;token.replace();held.complete(http.Response(jsonEncode({'code':'AUTH_TOKEN_INVALID','message':'SYNTHETIC'}),401));await rejected;
    expect(token.refreshCalls,0);expect(token.invalidations,0);expect(token.accessToken,'SYNTHETIC_NEW_ACCESS');expect(requests,1);client.close();
  });
  for(final afterLogout in [true,false]){
    test('L08 late200 cannot publish former identity data after ${afterLogout?'logout':'new login'}',()async{
      final token=_GenerationToken(),held=Completer<http.Response>(),started=Completer<void>();
      final client=MockClient((_){started.complete();return held.future;});
      final rejected=expectLater(api(client,token).getObject('mobile/me'),throwsA(changed()));await started.future;
      if(afterLogout){token.clear();}else{token.replace();}
      held.complete(http.Response(jsonEncode({'driverId':'SYNTHETIC_PREVIOUS_PERSON_DATA'}),200));await rejected;expect(token.invalidations,0);client.close();
    });
  }
  test('L08 old binary export is discarded before local save after identity change',()async{
    final token=_GenerationToken(),held=Completer<http.Response>(),started=Completer<void>();final client=MockClient((_){started.complete();return held.future;});
    final rejected=expectLater(api(client,token).getBinary('mobile/reports/services/SYNTHETIC/export'),throwsA(changed()));await started.future;token.replace();held.complete(http.Response.bytes([1,2,3],200));await rejected;expect(token.invalidations,0);client.close();
  });
  test('L08 session change while refreshing never replays a mutation as another identity',()async{
    final token=_GenerationToken()..refreshResult=Completer<bool>();var requests=0;
    final client=MockClient((_)async{requests++;return http.Response(jsonEncode({'code':'AUTH_TOKEN_EXPIRED','message':'SYNTHETIC'}),401);});
    final rejected=expectLater(api(client,token).postObject('mobile/assignments/SYNTHETIC/start',{'clientEventId':'SYNTHETIC'}),throwsA(changed()));await token.refreshStarted.future;token.replace();token.refreshResult!.complete(false);await rejected;
    expect(requests,1);expect(token.invalidations,0);expect(token.accessToken,'SYNTHETIC_NEW_ACCESS');client.close();
  });
  test('L08 legitimate access rotation keeps generation and allows exactly one retry',()async{
    final token=_GenerationToken();final authorizations=<String?>[];
    final client=MockClient((request)async{authorizations.add(request.headers['authorization']);return authorizations.length==1?http.Response(jsonEncode({'code':'AUTH_TOKEN_EXPIRED','message':'SYNTHETIC'}),401):http.Response(jsonEncode({'status':'DISPONIBLE'}),200);});
    expect((await api(client,token).getObject('mobile/availability'))['status'],'DISPONIBLE');expect(token.sessionGeneration,1);expect(token.refreshCalls,1);expect(token.invalidations,0);expect(authorizations,['Bearer SYNTHETIC_PREVIOUS_ACCESS','Bearer SYNTHETIC_ROTATED_ACCESS']);client.close();
  });
}
