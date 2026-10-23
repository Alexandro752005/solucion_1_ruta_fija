import 'package:flutter/material.dart';
import '../../../core/network/correlation_id.dart';
import '../../../core/presentation/mobile_date_time.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../domain/mobile_service_report.dart';

class MobileReportsPage extends StatefulWidget {
  const MobileReportsPage({required this.gateway,required this.files,this.initialFrom,this.initialTo,super.key});
  final MobileServiceReportGateway gateway;
  final MobileReportFileGateway files;
  final DateTime? initialFrom,initialTo;
  @override State<MobileReportsPage> createState()=>_MobileReportsPageState();
}
class _MobileReportsPageState extends State<MobileReportsPage> {
  late DateTime _from,_to;
  MobileServiceReport? _live;
  MobileReportEmission? _selected;
  List<MobileReportSummary> _emissions=const[];
  Object? _error;
  bool _busy=false;
  String? _issueKey;
  bool _uncertain=false;
  @override void initState(){super.initState();final now=DateTime.now();_to=widget.initialTo??DateTime(now.year,now.month,now.day);_from=widget.initialFrom??_to.subtract(const Duration(days:29));_load();}
  Future<void> _run(Future<void> Function() action) async {
    if(_busy)return;setState((){_busy=true;_error=null;});
    try{await action();}catch(error){if(mounted)setState(()=>_error=error);}finally{if(mounted)setState(()=>_busy=false);}
  }
  Future<void> _load()=>_run(() async {
    final report=await widget.gateway.live(_from,_to);
    final emissions=await widget.gateway.emissions(_from,_to);
    if(mounted)setState((){_live=report;_emissions=emissions;});
  });
  Future<void> _issue()=>_run(() async {
    _issueKey??=createCorrelationId();
    try {final result=await widget.gateway.issue(_from,_to,_issueKey!);
      if(mounted)setState((){_selected=result;_issueKey=null;_uncertain=false;});
      final emissions=await widget.gateway.emissions(_from,_to);if(mounted)setState(()=>_emissions=emissions);
    }catch(_){_uncertain=true;rethrow;}
  });
  Future<void> _select(String id)=>_run(() async {
    final result=await widget.gateway.emission(id);if(mounted)setState(()=>_selected=result);
  });
  Future<void> _export(MobileReportFormat format)=>_run(() async {
    final emission=_selected;if(emission==null)return;
    final bytes=await widget.gateway.export(emission.id,format);
    final saved=await widget.files.save(bytes,emission.id,format);
    if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(saved?'Archivo guardado en el destino elegido.':'Guardado cancelado; no se declaró una descarga completada.')));
  });
  Future<void> _pick(bool first) async {
    final value=await showDatePicker(context:context,initialDate:first?_from:_to,firstDate:DateTime(2020),lastDate:DateTime(2100));
    if(value==null||!mounted)return;setState((){if(first){_from=value;}else{_to=value;}_selected=null;_live=null;_emissions=const[];});
  }
  @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('Mis resultados y reportes')),body:ListView(padding:const EdgeInsets.all(20),children:[
    const Text('Solo sus participaciones. Las ofertas no son servicios completados. La vista actual y cada emisión conservan cortes diferentes.'),
    Wrap(spacing:12,children:[TextButton(onPressed:_busy||_uncertain?null:()=>_pick(true),child:Text('Desde: ${_day(_from)}')),TextButton(onPressed:_busy||_uncertain?null:()=>_pick(false),child:Text('Hasta: ${_day(_to)}'))]),
    if(_busy)const LinearProgressIndicator(),
    if(_error!=null)Text(mobileErrorMessage(_error!)),
    if(_uncertain)const Text('Resultado de emisión no confirmado. Consulte las emisiones guardadas; si continúa, se usará la misma referencia para evitar duplicados. No hay reenvío automático.'),
    Wrap(spacing:12,children:[OutlinedButton(onPressed:_busy?null:_load,child:const Text('Consultar estado y emisiones')),FilledButton(key:const Key('report-issue'),onPressed:_busy?null:_issue,child:Text(_uncertain?'Consultar/continuar la misma emisión':'Emitir reporte propio'))]),
    if(_live!=null)_summary('Vista vigente',_live!),
    const Divider(),
    Text('Emisiones conservadas',style:Theme.of(context).textTheme.titleMedium),
    if(_emissions.isEmpty)const Text('No hay emisiones para este periodo.'),
    for(final emission in _emissions)ListTile(key:Key('report-emission-${emission.id}'),contentPadding:EdgeInsets.zero,title:Text('Versión ${emission.revision} · ${emission.id}'),subtitle:Text(mobileDateTimeLabel(emission.issuedAt)),onTap:_busy?null:()=>_select(emission.id)),
    if(_selected case final emission?)...[
      _summary('Emisión ${emission.id} · versión ${emission.revision}',emission.report),
      SelectableText('SHA-256: ${emission.sha256}'),
      const Text('Cada formato usa esta emisión, sin recalcular. El destino elegido conservará una copia fuera del servidor; gestione su retiro según las reglas del piloto.'),
      Wrap(spacing:8,children:[for(final format in MobileReportFormat.values)OutlinedButton(key:Key('report-export-${format.extension}'),onPressed:_busy?null:()=>_export(format),child:Text('Guardar ${format.extension.toUpperCase()}'))]),
    ],
  ]));
  Widget _summary(String title,MobileServiceReport report)=>Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    Text(title,style:Theme.of(context).textTheme.titleMedium),Text('Corte del servidor: ${mobileDateTimeLabel(report.cutoffAt)}'),
    Text('Programados: ${report.scheduled} · Completados: ${report.completed}'),
    Text('Cancelados: ${report.cancelled} · Interrumpidos: ${report.interrupted} · Pendientes: ${report.pending} · Relevados: ${report.transferred}'),
    Text(report.completionPercent==null?'Sin denominador; no se calcula porcentaje.':'Terminación: ${report.completionPercent}%'),
    if(report.coverageNotice.isNotEmpty)Text(report.coverageNotice=='LEGACY_NOT_CERTIFIED_NO_BACKFILL'?'Los registros antiguos sin historia verificable no se certifican como completados.':report.coverageNotice),
    for(final item in report.items)ListTile(contentPadding:EdgeInsets.zero,title:Text('Servicio ${item['serviceId']}'),subtitle:Text('${item['status']} · Cohorte ${item['cohortScheduledAt']}\nFin efectivo: ${item['effectiveEndedAt']??'Sin cierre'}\nResultado terminal: ${item['terminalResult']??'No disponible'}')),
  ])));
  String _day(DateTime value)=>'${value.day.toString().padLeft(2,'0')}/${value.month.toString().padLeft(2,'0')}/${value.year}';
}
