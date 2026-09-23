import 'package:flutter/material.dart';

import '../data/mobile_assignment_repository.dart';
import '../domain/mobile_assignment_models.dart';
import 'assignment_detail_page.dart';
import 'mobile_assignment_presentation.dart';

class AssignmentsPage extends StatefulWidget {
  const AssignmentsPage({
    required this.driverGateway,
    required this.driverId,
    super.key,
  });

  final MobileAssignmentGateway driverGateway;
  final String driverId;

  @override
  State<AssignmentsPage> createState() => _AssignmentsPageState();
}

class _AssignmentsPageState extends State<AssignmentsPage> {
  static const _pageSize = 30;

  final List<MobileAssignment> _assignments = [];
  List<MobilePendingAssignmentCommand> _pendingCommands = const [];
  MobileAssignmentStatus? _filter;
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
      final result = await Future.wait([
        widget.driverGateway.list(status: _filter, page: 0, size: _pageSize),
        widget.driverGateway.pendingForDriver(widget.driverId),
      ]);
      if (!mounted) {
        return;
      }
      final assignments = result[0] as MobileAssignmentPage;
      setState(() {
        _assignments
          ..clear()
          ..addAll(assignments.items);
        _pendingCommands = result[1] as List<MobilePendingAssignmentCommand>;
        _page = assignments.page;
        _totalPages = assignments.totalPages;
        _totalItems = assignments.totalItems;
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
      final next = await widget.driverGateway.list(
        status: _filter,
        page: _page + 1,
        size: _pageSize,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _assignments.addAll(next.items);
        _page = next.page;
        _totalPages = next.totalPages;
        _totalItems = next.totalItems;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('No se pudo cargar más asignaciones: $error')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loadingMore = false);
      }
    }
  }

  Future<void> _openAssignment(MobileAssignment assignment) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => AssignmentDetailPage(
          assignmentId: assignment.id,
          driverId: widget.driverId,
          driverGateway: widget.driverGateway,
        ),
      ),
    );
    if (changed == true && mounted) {
      await _reload();
    }
  }

  void _selectFilter(MobileAssignmentStatus? status) {
    if (_filter == status) {
      return;
    }
    setState(() => _filter = status);
    _reload();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return _AssignmentsError(onRetry: _reload);
    }

    final pendingByAssignment = <String, MobilePendingAssignmentCommand>{
      for (final command in _pendingCommands) command.assignmentId: command,
    };
    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Text(
            'Asignaciones',
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text('$_totalItems asignación(es) propias. Deslice para actualizar.'),
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _FilterChip(
                  label: 'Todas',
                  selected: _filter == null,
                  onSelected: () => _selectFilter(null),
                ),
                for (final status in MobileAssignmentStatus.values)
                  _FilterChip(
                    label: status.label,
                    selected: _filter == status,
                    onSelected: () => _selectFilter(status),
                  ),
              ],
            ),
          ),
          if (_pendingCommands.isNotEmpty) ...[
            const SizedBox(height: 16),
            _PendingDeliveryNotice(count: _pendingCommands.length),
          ],
          const SizedBox(height: 12),
          if (_assignments.isEmpty)
            const _EmptyAssignments()
          else
            for (final assignment in _assignments) ...[
              _AssignmentCard(
                assignment: assignment,
                pendingCommand: pendingByAssignment[assignment.id],
                onTap: () => _openAssignment(assignment),
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
                  _loadingMore ? 'Cargando…' : 'Cargar más asignaciones',
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final bool selected;
  final VoidCallback onSelected;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => onSelected(),
      ),
    );
  }
}

class _AssignmentCard extends StatelessWidget {
  const _AssignmentCard({
    required this.assignment,
    required this.pendingCommand,
    required this.onTap,
  });

  final MobileAssignment assignment;
  final MobilePendingAssignmentCommand? pendingCommand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final statusColor = assignmentStatusColor(context, assignment.status);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: Key('assignment-${assignment.id}'),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '${assignment.originText} → ${assignment.destinationText}',
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    backgroundColor: statusColor.withValues(alpha: 0.14),
                    side: BorderSide(color: statusColor),
                    label: Text(assignment.status.label),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                'Programada: ${assignmentDateTimeLabel(assignment.scheduledAt)}',
              ),
              Text('Vehículo: ${assignment.vehiclePlate}'),
              const SizedBox(height: 8),
              Text(
                assignment.responseMode.label,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              if (pendingCommand != null) ...[
                const SizedBox(height: 12),
                _PendingCommandBanner(command: pendingCommand!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PendingDeliveryNotice extends StatelessWidget {
  const _PendingDeliveryNotice({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          'Hay $count acción(es) cuya confirmación aún no se conoce. Abra la '
          'asignación correspondiente y use “Reintentar” para enviar exactamente '
          'el mismo evento.',
        ),
      ),
    );
  }
}

class _PendingCommandBanner extends StatelessWidget {
  const _PendingCommandBanner({required this.command});

  final MobilePendingAssignmentCommand command;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Acción pendiente de confirmación: ${command.command.label}',
      child: Row(
        children: [
          const Icon(Icons.sync_problem_outlined),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Pendiente: ${command.command.label}. Toque para reintentar.',
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyAssignments extends StatelessWidget {
  const _EmptyAssignments();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(
        child: Column(
          children: [
            Icon(Icons.route_outlined, size: 42),
            SizedBox(height: 12),
            Text('No hay asignaciones para este filtro.'),
          ],
        ),
      ),
    );
  }
}

class _AssignmentsError extends StatelessWidget {
  const _AssignmentsError({required this.onRetry});

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
            const Text(
              'No fue posible cargar sus asignaciones. Revise la conexión e inténtelo nuevamente.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () {
                onRetry();
              },
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}
