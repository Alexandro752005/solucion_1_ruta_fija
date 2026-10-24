import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/presentation/mobile_date_time.dart';
import '../../assignments/data/mobile_assignment_repository.dart';
import '../../assignments/domain/mobile_assignment_models.dart';
import '../../assignments/presentation/assignment_detail_page.dart';
import '../../preferences/domain/mobile_preferences.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';
import 'driver_profile_details.dart';

class ConductorHomePage extends StatefulWidget {
  const ConductorHomePage({required this.driverGateway, this.assignmentGateway, this.preferences, super.key});

  final MobileDriverGateway driverGateway;
  final MobileAssignmentGateway? assignmentGateway;
  final MobilePreferencesController? preferences;

  @override
  State<ConductorHomePage> createState() => _ConductorHomePageState();
}

class _ConductorHomePageState extends State<ConductorHomePage> {
  late Future<MobileDriverProfile> _profile;
  Future<_HomeCommitments>? _commitments;

  @override
  void initState() {
    super.initState();
    _profile = widget.driverGateway.profile();
    _commitments = _loadCommitments();
  }

  Future<void> _reload() async {
    final profile = widget.driverGateway.profile();
    setState(() { _profile = profile; _commitments = _loadCommitments(); });
    try {
      await profile;
    } catch (_) {
      // FutureBuilder renders the safe, user-facing error message.
    }
  }

  Future<_HomeCommitments>? _loadCommitments() {
    final gateway=widget.assignmentGateway;if(gateway==null)return null;
    Future<List<MobileAssignment>> all(MobileAssignmentStatus status) async {
      final rows=<MobileAssignment>[];
      for(var page=0;page<50;page++){
        final response=await gateway.list(status:status,page:page,size:100);
        rows.addAll(response.items.where((a)=>a.status==status));
        if(page+1>=response.totalPages)return rows;
      }
      throw StateError('Consulte Asignaciones para revisar todos los compromisos.');
    }
    return Future.wait([all(MobileAssignmentStatus.enServicio),all(MobileAssignmentStatus.scheduled),all(MobileAssignmentStatus.pendingResponse)]).then((lists){
      final accepted=[...lists[0],...lists[1].where((a)=>a.acceptedAt!=null)]..sort((a,b){
        if(a.status!=b.status){if(a.status==MobileAssignmentStatus.enServicio)return -1;if(b.status==MobileAssignmentStatus.enServicio)return 1;}
        return a.scheduledAt.compareTo(b.scheduledAt);
      });
      final offers=lists[2].where((a)=>a.requiresMobileResponse).toList()..sort((a,b)=>(a.responseDeadlineAt??a.scheduledAt).compareTo(b.responseDeadlineAt??b.scheduledAt));
      return _HomeCommitments(accepted.firstOrNull,offers.firstOrNull,accepted.length,offers.length);
    });
  }

  Future<void> _openAssignment(MobileAssignment assignment) async {
    final gateway=widget.assignmentGateway;if(gateway==null)return;
    await Navigator.of(context).push(MaterialPageRoute<void>(builder:(_)=>AssignmentDetailPage(assignmentId:assignment.id,driverId:assignment.driverId,driverGateway:gateway)));
    if(mounted)await _reload();
  }

  Widget _commitmentPanel()=>FutureBuilder<_HomeCommitments>(future:_commitments,builder:(context,snapshot){
    if(snapshot.connectionState!=ConnectionState.done)return const LinearProgressIndicator();
    if(snapshot.hasError)return Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(children:[Text(mobileErrorMessage(snapshot.error!)),TextButton(onPressed:_reload,child:const Text('Consultar compromisos'))])));
    final state=snapshot.requireData;
    return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      Card(key:const Key('home-accepted-commitment'),child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text('Su compromiso principal',style:Theme.of(context).textTheme.titleLarge),
        if(state.accepted case final assignment?)...[
          Text(assignment.status==MobileAssignmentStatus.enServicio?'Servicio en curso':'Próximo servicio aceptado'),
          Text('Servicio ${assignment.serviceId??assignment.id}'),
          Text('Programación: ${mobileDateTimeLabel(assignment.scheduledAt)}'),
          Text('Grupo: ${assignment.groupName}'),
          FilledButton(key:const Key('home-open-accepted'),onPressed:()=>_openAssignment(assignment),child:Text(assignment.canStart?'Ver e iniciar servicio':assignment.handoverId!=null?'Revisar relevo':'Continuar servicio')),
          if(state.acceptedCount>1)Text('Tiene ${state.acceptedCount} compromisos. Consulte Asignaciones para verlos todos.'),
        ]else const Text('No hay un servicio aceptado o en curso. Una oferta pendiente no es un compromiso aceptado.'),
      ]))),
      Card(key:const Key('home-pending-offer'),child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text('Oferta pendiente · requiere respuesta',style:Theme.of(context).textTheme.titleMedium),
        if(state.offer case final offer?)...[
          Text('Oferta ${offer.id}'),
          Text('Plazo de respuesta: ${offer.responseDeadlineAt==null?'Consulte el detalle':mobileDateTimeLabel(offer.responseDeadlineAt!)}'),
          Text('Servicio previsto: ${mobileDateTimeLabel(offer.scheduledAt)}'),
          Wrap(spacing:8,children:[OutlinedButton(onPressed:()=>_openAssignment(offer),child:const Text('Revisar y aceptar')),TextButton(onPressed:()=>_openAssignment(offer),child:const Text('Revisar y rechazar'))]),
          const Text('La respuesta se confirma en el detalle y solo vale cuando el servidor la registra.'),
          if(state.offerCount>1)Text('Hay ${state.offerCount} ofertas pendientes; revise las restantes en Asignaciones.'),
        ]else const Text('No hay ofertas pendientes de respuesta.'),
      ]))),
    ]);
  });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<MobileDriverProfile>(
      future: _profile,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return _LoadError(
            message: mobileErrorMessage(snapshot.error!),
            onRetry: () {
              _reload();
            },
          );
        }
        final profile = snapshot.requireData;
        return RefreshIndicator(
          onRefresh: _reload,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
            children: [
              if(_commitments!=null)_commitmentPanel(),
              Text(
                'Sesión móvil activa',
                style: Theme.of(context).textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 16),
              DriverProfileDetails(profile: profile),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: () =>
                    Navigator.of(context).pushReplacementNamed('/availability'),
                icon: const Icon(Icons.toggle_on_outlined),
                label: const Text('Gestionar disponibilidad'),
              ),
              const SizedBox(height: 12),
              if(widget.preferences?.value?.optionalNoticeAlerts!=false)const Text(
                'Los datos provienen de su perfil real. Asignaciones, incidencias, '
                'comunicados y ubicación se gestionan desde sus módulos propios.',
              ),
            ],
          ),
        );
      },
    );
  }
}

final class _HomeCommitments {
  const _HomeCommitments(this.accepted,this.offer,this.acceptedCount,this.offerCount);
  final MobileAssignment? accepted,offer;
  final int acceptedCount,offerCount;
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 40),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}
