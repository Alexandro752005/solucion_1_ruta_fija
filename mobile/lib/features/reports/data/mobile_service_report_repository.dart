import 'dart:typed_data';
import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_service_report.dart';

final class MobileServiceReportRepository implements MobileServiceReportGateway {
  MobileServiceReportRepository(this.api);
  final MobileApiClient api;
  static const _base='mobile/reports/services';
  Map<String,String> _range(DateTime from,DateTime to) {
    final start=DateTime.utc(from.year,from.month,from.day),end=DateTime.utc(to.year,to.month,to.day);
    if(end.isBefore(start)||end.difference(start).inDays>=90)throw ArgumentError('El periodo debe tener de1 a90 días.');
    String day(DateTime v)=>'${v.year.toString().padLeft(4,'0')}-${v.month.toString().padLeft(2,'0')}-${v.day.toString().padLeft(2,'0')}';
    return {'from':day(from),'to':day(to)};
  }
  String _id(String id){if(!RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(id))throw ArgumentError('Referencia de emisión inválida.');return id;}
  @override Future<MobileServiceReport> live(DateTime from,DateTime to) async=>MobileServiceReport.fromJson(await api.getObject(_base,queryParameters:_range(from,to)));
  @override Future<MobileReportEmission> issue(DateTime from,DateTime to,String operationKey) async=>MobileReportEmission.fromJson(await api.postObject('$_base/emissions',{'operationKey':_id(operationKey),..._range(from,to)}));
  @override Future<List<MobileReportSummary>> emissions(DateTime from,DateTime to) async=>(await api.getList('$_base/emissions',queryParameters:_range(from,to))).map(MobileReportSummary.fromJson).toList(growable:false);
  @override Future<MobileReportEmission> emission(String id) async=>MobileReportEmission.fromJson(await api.getObject('$_base/emissions/${_id(id)}'));
  @override Future<Uint8List> export(String id,MobileReportFormat format) async {
    final response=await api.getBinary('$_base/emissions/${_id(id)}/export',queryParameters:{'format':format.extension});
    final type=response.headers['content-type']?.split(';').first.trim().toLowerCase();
    if(type!=format.mimeType)throw const FormatException('El formato recibido no corresponde a la descarga.');
    return response.bodyBytes;
  }
}
