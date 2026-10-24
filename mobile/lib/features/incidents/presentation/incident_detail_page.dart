import 'package:flutter/material.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_incident_repository.dart';
import '../domain/mobile_incident_models.dart';
import 'mobile_incident_presentation.dart';
import 'incident_history_panel.dart';
import 'incident_report_page.dart';

class IncidentDetailPage extends StatefulWidget {
  const IncidentDetailPage({
    required this.incidentId,
    required this.incidentGateway,
    required this.userId,
    super.key,
  });

  final String incidentId;
  final MobileIncidentGateway incidentGateway;
  final String userId;

  @override
  State<IncidentDetailPage> createState() => _IncidentDetailPageState();
}

class _IncidentDetailPageState extends State<IncidentDetailPage> {
  MobileIncident? _incident;
  Object? _error;
  bool _loading = true;
  bool _closing = false;
  bool _informationBlocked = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (_informationBlocked) return;
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final incident = await widget.incidentGateway.get(widget.incidentId);
      if (mounted) {
        setState(() => _incident = incident);
      }
    } catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _confirmClose() async {
    final incident = _incident;
    if (_closing ||
        _informationBlocked ||
        incident == null ||
        !incident.canBeClosedBy(widget.userId)) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Cerrar incidencia'),
        content: const Text(
          'El reporte y su cierre se conservarán. No podrá reabrirse ni recibir nuevos seguimientos, tampoco desde el CRM. Una recurrencia o cierre erróneo se registra como un caso posterior vinculado.',
        ),
        actions: [
          TextButton(
            key: const Key('incident-close-cancel'),
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Volver'),
          ),
          FilledButton(
            key: const Key('incident-close-confirm'),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Cerrar definitivamente'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await _close(incident);
    }
  }

  Future<void> _close(MobileIncident incident) async {
    setState(() => _closing = true);
    try {
      final closed = await widget.incidentGateway.close(
        incident.id,
        version: incident.version,
      );
      if (!mounted) {
        return;
      }
      setState(() => _incident = closed);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'La incidencia quedó cerrada por el conductor y conserva su historial.',
          ),
        ),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is MobileNetworkException
          ? 'No se pudo confirmar el cierre por la red. Actualice o vuelva a intentar: el servidor no creará otro reporte.'
          : mobileErrorMessage(error);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } finally {
      if (mounted) {
        setState(() => _closing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Incidencia')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.cloud_off_outlined, size: 40),
                const SizedBox(height: 16),
                Text(mobileErrorMessage(_error!), textAlign: TextAlign.center),
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: _load,
                  icon: const Icon(Icons.refresh),
                  label: const Text('Reintentar'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final incident = _incident!;
    final statusColor = incidentStatusColor(context, incident.status);
    return PopScope(
      canPop: !_informationBlocked,
      child: Scaffold(
        appBar: AppBar(title: const Text('Incidencia')),
        body: SafeArea(
          child: RefreshIndicator(
            onRefresh: _load,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(20),
              children: [
                Row(
                  children: [
                    Icon(incidentCategoryIcon(incident.category), size: 32),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        incident.category.label,
                        style: Theme.of(context).textTheme.headlineSmall
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                    ),
                    Chip(
                      backgroundColor: statusColor.withValues(alpha: 0.14),
                      side: BorderSide(color: statusColor),
                      label: Text(incident.status.label),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _DetailRow('Descripción', incident.description),
                        _DetailRow(
                          'Reportada',
                          incidentDateTimeLabel(incident.reportedAt),
                        ),
                        _DetailRow('Reportada por', incident.reportedByName),
                        _DetailRow('Origen', incident.source.label),
                        if (incident.assignmentId != null)
                          _DetailRow(
                            'Asignación vinculada',
                            incident.assignmentId!,
                          ),
                        if (incident.followUpNote != null)
                          _DetailRow('Seguimiento', incident.followUpNote!),
                        if (incident.followedUpByName != null)
                          _DetailRow(
                            'Atendido por',
                            incident.followedUpByName!,
                          ),
                        if (incident.followedUpAt != null)
                          _DetailRow(
                            'Fecha de seguimiento',
                            incidentDateTimeLabel(incident.followedUpAt!),
                          ),
                        if (incident.resolvedAt != null)
                          _DetailRow(
                            'Resuelta',
                            incidentDateTimeLabel(incident.resolvedAt!),
                          ),
                        if (incident.driverClosedByName != null)
                          _DetailRow(
                            'Cerrada por',
                            incident.driverClosedByName!,
                          ),
                        if (incident.driverClosedAt != null)
                          _DetailRow(
                            'Fecha de cierre del conductor',
                            incidentDateTimeLabel(incident.driverClosedAt!),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                if (incident.canBeClosedBy(widget.userId)) ...[
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Si el hecho ya fue solucionado, puede cerrar este reporte una sola vez.',
                          ),
                          const SizedBox(height: 8),
                          const Text(
                            'No se elimina ni se duplica. El cierre es irreversible y queda disponible para consulta del ADMIN. Una recurrencia requiere un caso nuevo vinculado.',
                          ),
                          const SizedBox(height: 16),
                          FilledButton.icon(
                            key: const Key('incident-close'),
                            onPressed: _closing || _informationBlocked
                                ? null
                                : _confirmClose,
                            icon: _closing
                                ? const SizedBox(
                                    height: 18,
                                    width: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.task_alt_outlined),
                            label: Text(
                              _closing
                                  ? 'Cerrando…'
                                  : 'Cerrar incidencia definitivamente',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                if (incident.wasClosedByDriver) ...[
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Text(
                        'Esta incidencia fue cerrada por el conductor. No se permite reapertura ni seguimiento adicional, tampoco desde el CRM. El hecho y su cierre se conservan.',
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                IncidentHistoryPanel(
                  incident: incident,
                  gateway: widget.incidentGateway,
                  onChanged: _load,
                  onBlocked: (v) {
                    if (mounted) setState(() => _informationBlocked = v);
                  },
                ),
                if (incident.isClosed)
                  OutlinedButton(
                    key: const Key('incident-new-related'),
                    onPressed: _informationBlocked
                        ? null
                        : () async {
                            final navigator = Navigator.of(context);
                            final result = await navigator.push<bool>(
                              MaterialPageRoute(
                                builder: (_) => IncidentReportPage(
                                  incidentGateway: widget.incidentGateway,
                                  previousIncidentId: incident.id,
                                ),
                              ),
                            );
                            if (mounted &&
                                navigator.mounted &&
                                result == true) {
                              navigator.pop(true);
                            }
                          },
                    child: const Text('Registrar caso posterior sin reabrir'),
                  ),
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(16),
                    child: Text(
                      'El conductor puede reportar y consultar sus incidencias. El seguimiento y la resolución corresponden al ADMIN desde el CRM.',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 2),
          Text(value),
        ],
      ),
    );
  }
}
