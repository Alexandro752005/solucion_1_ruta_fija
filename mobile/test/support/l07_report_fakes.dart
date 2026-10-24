import 'dart:typed_data';
import 'package:ruta_fija_conductor/features/reports/domain/mobile_service_report.dart';

Map<String,dynamic> l07ReportJson()=>{
  'ruleVersion':'REP-L07-01-v1','scope':'OWN_PARTICIPATION','from':'2026-10-01','to':'2026-10-21','zoneId':'America/Lima','cutoffAt':'2026-10-21T12:00:00Z',
  'totals':<String,dynamic>{'scheduled':2,'completed':1,'cancelled':0,'interrupted':0,'pending':1,'transferred':0,'completionPercent':50},
  'coverage':{'excludedLegacyOffers':0,'canonicalServicesWithoutFirstOffer':0,'notice':'Cobertura sintética'},
  'items':[{'serviceId':'33333333-3333-4333-8333-333333333333','status':'COMPLETED','cohortScheduledAt':'2026-10-20T12:00:00Z','effectiveEndedAt':'2026-10-20T13:00:00Z','terminalResult':'NOT_CORROBORATED_NO_POINT'}]
};
const l07EmissionId='44444444-4444-4444-8444-444444444444';
Map<String,dynamic> l07EmissionJson()=>{'id':l07EmissionId,'seriesId':'55555555-5555-4555-8555-555555555555','revision':1,'previousEmissionId':null,'issuedAt':'2026-10-21T12:00:00Z','contentSha256':'a'*64,'report':l07ReportJson()};
final class L07ReportFake implements MobileServiceReportGateway {
  final List<String> issuedKeys=[];
  final List<String> exportedIds=[];
  bool uncertainFirst=false;
  @override Future<MobileServiceReport> live(DateTime from,DateTime to)async=>MobileServiceReport.fromJson(l07ReportJson());
  @override Future<MobileReportEmission> issue(DateTime from,DateTime to,String operationKey)async{
    issuedKeys.add(operationKey);if(uncertainFirst&&issuedKeys.length==1)throw const FormatException('Respuesta no confirmada');return MobileReportEmission.fromJson(l07EmissionJson());
  }
  @override Future<List<MobileReportSummary>> emissions(DateTime from,DateTime to)async=>[MobileReportSummary(id:l07EmissionId,revision:1,issuedAt:DateTime.utc(2026,10,21,12))];
  @override Future<MobileReportEmission> emission(String id)async=>MobileReportEmission.fromJson(l07EmissionJson());
  @override Future<Uint8List> export(String id,MobileReportFormat format)async{exportedIds.add(id);return Uint8List.fromList([1,2,3]);}
}
final class L07ReportFileFake implements MobileReportFileGateway {
  final List<MobileReportFormat> formats=[];
  bool result=true;
  @override Future<bool> save(Uint8List bytes,String emissionId,MobileReportFormat format)async{formats.add(format);return result;}
}
