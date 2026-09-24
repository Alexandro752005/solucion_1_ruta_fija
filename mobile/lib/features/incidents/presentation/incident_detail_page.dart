import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_incident_repository.dart';
import '../domain/mobile_incident_models.dart';
import 'mobile_incident_presentation.dart';

class IncidentDetailPage extends StatefulWidget {
  const IncidentDetailPage({
    required this.incidentId,
    required this.incidentGateway,
    super.key,
  });

  final String incidentId;
  final MobileIncidentGateway incidentGateway;

  @override
  State<IncidentDetailPage> createState() => _IncidentDetailPageState();
}

class _IncidentDetailPageState extends State<IncidentDetailPage> {
  MobileIncident? _incident;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
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
    return Scaffold(
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
                      _DetailRow('Origen', incident.source.label),
                      if (incident.assignmentId != null)
                        _DetailRow(
                          'Asignación vinculada',
                          incident.assignmentId!,
                        ),
                      if (incident.followUpNote != null)
                        _DetailRow('Seguimiento', incident.followUpNote!),
                      if (incident.followedUpByName != null)
                        _DetailRow('Atendido por', incident.followedUpByName!),
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
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
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
