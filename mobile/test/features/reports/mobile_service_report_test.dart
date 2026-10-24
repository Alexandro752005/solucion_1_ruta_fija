import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/features/reports/data/mobile_service_report_repository.dart';
import 'package:ruta_fija_conductor/features/reports/data/android_report_file_gateway.dart';
import 'package:ruta_fija_conductor/features/reports/domain/mobile_service_report.dart';
import 'package:ruta_fija_conductor/features/reports/presentation/mobile_reports_page.dart';
import '../../support/l07_report_fakes.dart';

final class _Token implements MobileAccessTokenProvider {
  @override String? get accessToken=>'SYNTHETIC_TOKEN';
  @override Future<bool> refreshAccessToken()async=>false;
  @override Future<void> invalidateSession()async{}
}
void main(){
  TestWidgetsFlutterBinding.ensureInitialized();
  MobileServiceReportRepository repository(http.Client transport)=>MobileServiceReportRepository(MobileApiClient(httpClient:transport,environment:RouteFijaEnvironment.fromApiBaseUrl('https://api.example.test/api/v1'),accessTokenProvider:_Token()));
  test('own report and emission requests have no tenant or driver selector',()async{
    final requests=<http.Request>[];final api=repository(MockClient((request)async{requests.add(request);return http.Response(jsonEncode(request.method=='POST'?l07EmissionJson():l07ReportJson()),200);}));
    final from=DateTime(2026,10,1),to=DateTime(2026,10,21);final live=await api.live(from,to);final emission=await api.issue(from,to,'66666666-6666-4666-8666-666666666666');
    expect(live.completionPercent,50);expect(emission.id,l07EmissionId);expect(requests.first.url.queryParameters,{'from':'2026-10-01','to':'2026-10-21'});
    expect(jsonDecode(requests.last.body),{'operationKey':'66666666-6666-4666-8666-666666666666','from':'2026-10-01','to':'2026-10-21'});expect(requests.every((r)=>r.url.path.startsWith('/api/v1/mobile/reports/services')),true);
  });
  test('binary export uses authenticated header and same persisted emission reference',()async{
    late http.Request request;final api=repository(MockClient((r)async{request=r;return http.Response.bytes([1,2,3],200,headers:{'content-type':'application/pdf'});}));
    final bytes=await api.export(l07EmissionId,MobileReportFormat.pdf);expect(bytes,[1,2,3]);expect(request.url.queryParameters,{'format':'pdf'});expect(request.url.path,'/api/v1/mobile/reports/services/emissions/$l07EmissionId/export');expect(request.headers['authorization'],'Bearer SYNTHETIC_TOKEN');expect(request.url.toString(),isNot(contains('TOKEN')));
  });
  test('wrong content type and invalid ranges cannot be treated as successful downloads',()async{
    final api=repository(MockClient((_)async=>http.Response('not pdf',200,headers:{'content-type':'text/html'})));
    await expectLater(api.export(l07EmissionId,MobileReportFormat.pdf),throwsFormatException);
    await expectLater(api.live(DateTime(2026,1,1),DateTime(2026,12,31)),throwsArgumentError);
    await expectLater(api.emission('../foreign'),throwsArgumentError);
  });
  test('zero denominator remains undefined',(){final json=l07ReportJson();(json['totals'] as Map)['completionPercent']=null;expect(MobileServiceReport.fromJson(json).completionPercent,isNull);});
  test('Android save bridge sends bytes and fixed file metadata without credentials or URI',()async{
    MethodCall? captured;TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,(call)async{captured=call;return true;});
    addTearDown(()=>TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,null));
    expect(await const AndroidReportFileGateway().save(Uint8List.fromList([1,2,3]),l07EmissionId,MobileReportFormat.pdf),true);
    expect(captured?.method,'saveReport');expect((captured?.arguments as Map).keys.toSet(),{'bytes','fileName','mimeType'});expect((captured?.arguments as Map)['fileName'],'ruta-fija-$l07EmissionId.pdf');
  });
  test('Android dialog cancellation remains cancellation, not success',()async{
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,(_)async=>false);
    addTearDown(()=>TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,null));
    expect(await const AndroidReportFileGateway().save(Uint8List.fromList([1]),l07EmissionId,MobileReportFormat.csv),false);
  });
  testWidgets('unknown issue result retains operation key; next explicit action does not create a new intention',(tester)async{
    final reports=L07ReportFake()..uncertainFirst=true;
    await tester.pumpWidget(MaterialApp(home:MobileReportsPage(gateway:reports,files:L07ReportFileFake())));await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('report-issue')));await tester.pumpAndSettle();expect(reports.issuedKeys,hasLength(1));
    expect(find.textContaining('Resultado de emisión no confirmado'),findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('report-issue')));await tester.tap(find.byKey(const Key('report-issue')));await tester.pumpAndSettle();
    expect(reports.issuedKeys,hasLength(2));expect(reports.issuedKeys[0],reports.issuedKeys[1]);
  });
  testWidgets('all formats download same selected emission; cancelled save is explicit',(tester)async{
    tester.view.physicalSize=const Size(1100,1800);tester.view.devicePixelRatio=1;addTearDown(tester.view.resetPhysicalSize);addTearDown(tester.view.resetDevicePixelRatio);
    final reports=L07ReportFake(),files=L07ReportFileFake()..result=false;
    await tester.pumpWidget(MaterialApp(home:MobileReportsPage(gateway:reports,files:files)));await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('report-emission-$l07EmissionId')));await tester.tap(find.byKey(const Key('report-emission-$l07EmissionId')));await tester.pumpAndSettle();
    for(final format in MobileReportFormat.values){final button=find.byKey(Key('report-export-${format.extension}'));await tester.ensureVisible(button);await tester.tap(button);await tester.pumpAndSettle();}
    expect(reports.exportedIds,[l07EmissionId,l07EmissionId,l07EmissionId]);expect(files.formats,MobileReportFormat.values);expect(find.textContaining('Guardado cancelado'),findsWidgets);
  });
}
