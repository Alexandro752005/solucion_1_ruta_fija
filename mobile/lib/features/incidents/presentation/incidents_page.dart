import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_incident_repository.dart';
import '../domain/mobile_incident_models.dart';
import 'incident_detail_page.dart';
import 'incident_report_page.dart';
import 'mobile_incident_presentation.dart';

class IncidentsPage extends StatefulWidget {
  const IncidentsPage({required this.incidentGateway, super.key});

  final MobileIncidentGateway incidentGateway;

  @override
  State<IncidentsPage> createState() => _IncidentsPageState();
}

class _IncidentsPageState extends State<IncidentsPage> {
  static const _pageSize = 20;

  final List<MobileIncident> _incidents = [];
  Object? _error;
  int _page = 0;
  int _totalPages = 0;
  int _totalItems = 0;
  bool _loading = true;
  bool _loadingMore = false;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _loadingMore = false;
        _error = null;
      });
    }
    try {
      final result = await widget.incidentGateway.list(size: _pageSize);
      if (!mounted) {
        return;
      }
      setState(() {
        _incidents
          ..clear()
          ..addAll(result.items);
        _page = result.page;
        _totalPages = result.totalPages;
        _totalItems = result.totalItems;
      });
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

  Future<void> _loadMore() async {
    if (_loadingMore || _page + 1 >= _totalPages) {
      return;
    }
    setState(() => _loadingMore = true);
    try {
      final result = await widget.incidentGateway.list(
        page: _page + 1,
        size: _pageSize,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _incidents.addAll(result.items);
        _page = result.page;
        _totalPages = result.totalPages;
        _totalItems = result.totalItems;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'No se pudo cargar más incidencias: ${mobileErrorMessage(error)}',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingMore = false);
      }
    }
  }

  Future<void> _reportIncident() async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) =>
            IncidentReportPage(incidentGateway: widget.incidentGateway),
      ),
    );
    if (changed == true && mounted) {
      await _reload();
    }
  }

  Future<void> _openIncident(MobileIncident incident) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => IncidentDetailPage(
          incidentId: incident.id,
          incidentGateway: widget.incidentGateway,
        ),
      ),
    );
    if (changed == true && mounted) {
      await _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return _IncidentsError(
        message: mobileErrorMessage(_error!),
        onRetry: _reload,
      );
    }

    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Incidencias',
                  style: Theme.of(context).textTheme.headlineSmall
                      ?.copyWith(fontWeight: FontWeight.w800),
                ),
              ),
              FilledButton.icon(
                key: const Key('incident-report-open'),
                onPressed: _reportIncident,
                icon: const Icon(Icons.add_alert_outlined),
                label: const Text('Reportar'),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text('$_totalItems incidencia(s) propias. Deslice para actualizar.'),
          const SizedBox(height: 8),
          const Text(
            'El reporte queda asociado a su sesión de conductor; no puede reportar por otra persona.',
          ),
          const SizedBox(height: 16),
          if (_incidents.isEmpty)
            const _EmptyIncidents()
          else
            for (final incident in _incidents) ...[
              _IncidentCard(
                incident: incident,
                onTap: () => _openIncident(incident),
              ),
              const SizedBox(height: 12),
            ],
          if (_page + 1 < _totalPages) ...[
            const SizedBox(height: 4),
            Center(
              child: OutlinedButton.icon(
                onPressed: _loadingMore ? null : _loadMore,
                icon: _loadingMore
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.expand_more),
                label: Text(
                  _loadingMore ? 'Cargando…' : 'Cargar más incidencias',
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _IncidentCard extends StatelessWidget {
  const _IncidentCard({required this.incident, required this.onTap});

  final MobileIncident incident;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final statusColor = incidentStatusColor(context, incident.status);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: Key('incident-${incident.id}'),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(incidentCategoryIcon(incident.category)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      incident.category.label,
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  Chip(
                    backgroundColor: statusColor.withValues(alpha: 0.14),
                    side: BorderSide(color: statusColor),
                    label: Text(incident.status.label),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text('Reportada: ${incidentDateTimeLabel(incident.reportedAt)}'),
              const SizedBox(height: 6),
              Text(
                incident.description,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              Text(
                incident.source.label,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyIncidents extends StatelessWidget {
  const _EmptyIncidents();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(
        child: Column(
          children: [
            Icon(Icons.report_problem_outlined, size: 42),
            SizedBox(height: 12),
            Text('No tiene incidencias registradas.'),
          ],
        ),
      ),
    );
  }
}

class _IncidentsError extends StatelessWidget {
  const _IncidentsError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

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
