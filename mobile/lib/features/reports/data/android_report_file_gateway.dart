import 'package:flutter/services.dart';
import '../domain/mobile_service_report.dart';

final class AndroidReportFileGateway implements MobileReportFileGateway {
  const AndroidReportFileGateway();
  static const channel=MethodChannel('pe.rutafija.conductor/report_files');
  @override Future<bool> save(Uint8List bytes,String emissionId,MobileReportFormat format) async {
    if(!RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(emissionId)||bytes.isEmpty||bytes.length>20*1024*1024)throw ArgumentError('Archivo no permitido.');
    return await channel.invokeMethod<bool>('saveReport',{'bytes':bytes,'fileName':'ruta-fija-$emissionId.${format.extension}','mimeType':format.mimeType}) ?? false;
  }
}
