// L07-CON-01 opt-in. Actual HTTP, backend/PostgreSQL, production widgets and
// repositories. Automated synthetic interaction; Android destination dialog is
// an explicit MethodChannel double, not a physical storage/device assertion.
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/io_client.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/presentation/conductor_home_page.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_repository.dart';
import 'package:ruta_fija_conductor/features/assignments/data/mobile_assignment_command_store.dart';
import 'package:ruta_fija_conductor/features/preferences/data/mobile_preferences_repository.dart';
import 'package:ruta_fija_conductor/features/preferences/domain/mobile_preferences.dart';
import 'package:ruta_fija_conductor/features/preferences/presentation/mobile_preferences_panel.dart';
import 'package:ruta_fija_conductor/features/reports/data/mobile_service_report_repository.dart';
import 'package:ruta_fija_conductor/features/reports/data/android_report_file_gateway.dart';
import 'package:ruta_fija_conductor/features/reports/domain/mobile_service_report.dart';
import 'package:ruta_fija_conductor/features/reports/presentation/mobile_reports_page.dart';

final class _Token implements MobileAccessTokenProvider {
  _Token(this.accessToken);
  @override String? accessToken;
  @override Future<bool> refreshAccessToken()async=>false;
  @override Future<void> invalidateSession()async{accessToken=null;}
}
void main(){
  final enabled=Platform.environment['RF_L07_CONNECTED_CLIENTS']=='true';
  final transport=IOClient(HttpClient()..connectionTimeout=const Duration(seconds:10));
  testWidgets('L07 authorized own report, home and preference stage against actual HTTP',(tester)async{
    final env=Platform.environment,base=Platform.environment['RF_L07_CONNECTED_BASE'],token=Platform.environment['RF_L07_CONNECTED_TOKEN'];
    expect(RegExp(r'^http://127\.0\.0\.1:\d+$').hasMatch(base??''),true);expect(token,isNotEmpty);expect(env['RF_L07_CONNECTED_RUN_ID'],'6b496135-b86b-4c5b-938f-a60ddcfe32db');
    tester.view.physicalSize=const Size(1200,1600);tester.view.devicePixelRatio=1;addTearDown(tester.view.resetPhysicalSize);addTearDown(tester.view.resetDevicePixelRatio);
    Future<void> until(bool Function() ready)async{
      for(var i=0;i<500;i++){
        await tester.runAsync(()=>Future<void>.delayed(const Duration(milliseconds:40)));await tester.pump();
        if(tester.takeException()!=null)throw StateError('SYNTHETIC_WIDGET_FAILURE');if(ready())return;
      }throw StateError('SYNTHETIC_WIDGET_WAIT');
    }
    Future<T> io<T>(Future<T> Function() work)async{
      T? value;Object? failure;StackTrace? stack;var done=false;
      // Widget requests can open a pooled socket in the fake-clock zone. Keep
      // pumping while observing subsequent real HTTP, rather than blocking that
      // socket's response callbacks inside one long runAsync invocation.
      work().then((result){value=result;done=true;},onError:(Object error,StackTrace trace){failure=error;stack=trace;done=true;});
      await until(()=>done);
      if(failure!=null)Error.throwWithStackTrace(failure!,stack!);return value as T;
    }
    Future<void> tap(Finder finder)async{await tester.ensureVisible(finder);await tester.pump();await tester.tap(finder);await tester.pump();}
    final control=env['RF_L07_CONNECTED_CONTROL'];expect(control,isNotEmpty);
    final controlResponse=await io(()=>transport.get(Uri.parse('$base/__l07/state'),headers:{'X-L07-Control':control!}));
    expect(controlResponse.statusCode,200);
    final state=jsonDecode(controlResponse.body) as Map<String,dynamic>;
    final api=MobileApiClient(httpClient:transport,environment:RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'),accessTokenProvider:_Token(token));
    final stage=state['clientStage'] as String;
    if(stage=='HOME'){
      final assignments=MobileAssignmentRepository(api,MobileSessionAssignmentCommandStore(),enableInMemoryRetryQueue:false);
      await tester.pumpWidget(MaterialApp(home:Scaffold(body:ConductorHomePage(driverGateway:MobileDriverRepository(api),assignmentGateway:assignments))));
      await until(()=>find.byKey(const Key('home-open-accepted')).evaluate().isNotEmpty&&find.text('Revisar y aceptar').evaluate().isNotEmpty);
      expect(find.textContaining(state['pendingOfferId'] as String),findsOneWidget);expect(find.textContaining('Plazo de respuesta:'),findsOneWidget);
      expect(tester.getTopLeft(find.byKey(const Key('home-accepted-commitment'))).dy,lessThan(tester.getTopLeft(find.byKey(const Key('home-pending-offer'))).dy));
      final accepted=await io(()=>assignments.get(state['currentOfferId'] as String));expect(accepted.acceptedAt,isNotNull);
      // ignore: avoid_print
      print('L07_CONNECTED_HOME=PASS_ACCEPTED_FIRST_PENDING_OFFER_SEPARATE_NO_AUTOMATIC_COMMAND');
    }else if(stage=='PREFERENCES'){
      final repository=MobilePreferencesRepository(api),controller=MobilePreferencesController(MobilePreferencesRepository(api));
      await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(child:MobilePreferencesPanel(controller:controller)))));
      await until(()=>find.byKey(const Key('preference-optional-notices')).evaluate().isNotEmpty);
      final original=await io(repository.read);
      if(original.optionalNoticeAlerts)await tap(find.byKey(const Key('preference-optional-notices')));
      await tap(find.byKey(const Key('preference-text-size')));await tester.pumpAndSettle();await tap(find.text('Grande').last);await tester.pumpAndSettle();
      await tap(find.text('Guardar preferencias'));
      await until(()=>find.text('Preferencias confirmadas por el servidor.').evaluate().isNotEmpty);
      final after=await io(repository.read);expect(after.optionalNoticeAlerts,false);expect(after.textSize,MobileTextSize.large);expect(after.version,original.version+1);
      expect(find.textContaining('seguridad siempre'),findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());controller.dispose();
      // ignore: avoid_print
      print('L07_CONNECTED_PREFERENCES=PASS_OWN_SERVER_PERSISTENCE_MANDATORY_NOTICES_UNCHANGED');
    }else if(stage=='OWN_REPORT'){
      final repository=MobileServiceReportRepository(api),saved=<String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,(call)async{
        expect(call.method,'saveReport');final args=Map<String,dynamic>.from(call.arguments as Map);expect(args.keys.toSet(),{'bytes','fileName','mimeType'});expect((args['bytes'] as List).isNotEmpty,true);saved.add(args['fileName'] as String);return true;
      });
      addTearDown(()=>TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(AndroidReportFileGateway.channel,null));
      final from=DateTime.parse(state['reportFrom'] as String),to=DateTime.parse(state['reportTo'] as String);
      await tester.pumpWidget(MaterialApp(home:MobileReportsPage(gateway:repository,files:const AndroidReportFileGateway(),initialFrom:from,initialTo:to)));
      await until(()=>find.byKey(const Key('report-issue')).evaluate().isNotEmpty&&find.byType(LinearProgressIndicator).evaluate().isEmpty);
      await tap(find.byKey(const Key('report-issue')));
      await until(()=>find.byKey(const Key('report-export-pdf')).evaluate().isNotEmpty&&find.byType(LinearProgressIndicator).evaluate().isEmpty);
      final emissions=await io(()=>repository.emissions(from,to));expect(emissions,isNotEmpty);
      expect(find.textContaining('registros antiguos sin historia verificable'),findsWidgets);expect(find.text('LEGACY_NOT_CERTIFIED_NO_BACKFILL'),findsNothing);
      for(final format in MobileReportFormat.values){await tap(find.byKey(Key('report-export-${format.extension}')));await until(()=>saved.any((name)=>name.endsWith('.${format.extension}'))&&find.byType(LinearProgressIndicator).evaluate().isEmpty);}
      expect(saved.map((name)=>name.substring(0,name.lastIndexOf('.'))).toSet(),hasLength(1));
      // ignore: avoid_print
      print('L07_CONNECTED_OWN_REPORT=PASS_OWN_EMISSION_HTTP_CSV_XLSX_PDF_SAME_REFERENCE_ANDROID_SAVE_DOUBLE_ONLY');
    }else {fail('UNSUPPORTED_SYNTHETIC_STAGE');}
    await tester.pumpWidget(const SizedBox.shrink());await tester.pump(const Duration(seconds:6));transport.close();
    // ignore: avoid_print
    print('L07_CONNECTED_RESULT=PASS_PRODUCTION_WIDGET_REPOSITORY_HTTP_STAGE_$stage');
  },skip:!enabled,timeout:const Timeout(Duration(minutes:3)));
}
