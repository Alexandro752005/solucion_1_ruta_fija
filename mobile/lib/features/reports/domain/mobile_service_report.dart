import 'dart:typed_data';

enum MobileReportFormat {
  csv('csv','text/csv'),xlsx('xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),pdf('pdf','application/pdf');
  const MobileReportFormat(this.extension,this.mimeType);
  final String extension;
  final String mimeType;
}
final class MobileServiceReport {
  const MobileServiceReport({required this.from,required this.to,required this.cutoffAt,required this.scope,required this.scheduled,required this.completed,required this.cancelled,required this.interrupted,required this.pending,required this.transferred,required this.completionPercent,required this.coverageNotice,required this.items});
  final DateTime from,to,cutoffAt;
  final String scope,coverageNotice;
  final int scheduled,completed,cancelled,interrupted,pending,transferred;
  final num? completionPercent;
  final List<Map<String,dynamic>> items;
  factory MobileServiceReport.fromJson(Map<String,dynamic> json) {
    final totals=Map<String,dynamic>.from(json['totals'] as Map);
    final coverage=Map<String,dynamic>.from(json['coverage'] as Map);
    return MobileServiceReport(from:DateTime.parse(json['from'] as String),to:DateTime.parse(json['to'] as String),cutoffAt:DateTime.parse(json['cutoffAt'] as String),scope:json['scope'] as String,
      scheduled:(totals['scheduled'] as num).toInt(),completed:(totals['completed'] as num).toInt(),cancelled:(totals['cancelled'] as num).toInt(),interrupted:(totals['interrupted'] as num).toInt(),pending:(totals['pending'] as num).toInt(),transferred:(totals['transferred'] as num).toInt(),completionPercent:totals['completionPercent'] as num?,coverageNotice:coverage['notice'] as String? ?? '',items:(json['items'] as List).map((v)=>Map<String,dynamic>.from(v as Map)).toList(growable:false));
  }
}
final class MobileReportEmission {
  const MobileReportEmission({required this.id,required this.revision,required this.issuedAt,required this.sha256,required this.report});
  final String id,sha256;
  final int revision;
  final DateTime issuedAt;
  final MobileServiceReport report;
  factory MobileReportEmission.fromJson(Map<String,dynamic> json)=>MobileReportEmission(id:json['id'] as String,revision:(json['revision'] as num).toInt(),issuedAt:DateTime.parse(json['issuedAt'] as String),sha256:json['contentSha256'] as String,report:MobileServiceReport.fromJson(Map<String,dynamic>.from(json['report'] as Map)));
}
final class MobileReportSummary {
  const MobileReportSummary({required this.id,required this.revision,required this.issuedAt});
  final String id;
  final int revision;
  final DateTime issuedAt;
  factory MobileReportSummary.fromJson(Map<String,dynamic> json)=>MobileReportSummary(id:json['id'] as String,revision:(json['revision'] as num).toInt(),issuedAt:DateTime.parse(json['issuedAt'] as String));
}
abstract interface class MobileServiceReportGateway {
  Future<MobileServiceReport> live(DateTime from,DateTime to);
  Future<MobileReportEmission> issue(DateTime from,DateTime to,String operationKey);
  Future<List<MobileReportSummary>> emissions(DateTime from,DateTime to);
  Future<MobileReportEmission> emission(String id);
  Future<Uint8List> export(String id,MobileReportFormat format);
}
abstract interface class MobileReportFileGateway {
  /// Returns false if the person cancels the local save dialog.
  Future<bool> save(Uint8List bytes,String emissionId,MobileReportFormat format);
}
