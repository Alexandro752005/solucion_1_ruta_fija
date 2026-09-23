import 'package:flutter/material.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_assignment_repository.dart';
import '../domain/mobile_assignment_models.dart';
import 'mobile_assignment_presentation.dart';

class AssignmentDetailPage extends StatefulWidget {
  const AssignmentDetailPage({
    required this.assignmentId,
    required this.driverId,
    required this.driverGateway,
    super.key,
  });

  final String assignmentId;
  final String driverId;
  final MobileAssignmentGateway driverGateway;

  @override
  State<AssignmentDetailPage> createState() => _AssignmentDetailPageState();
}

class _AssignmentDetailPageState extends State<AssignmentDetailPage> {
  MobileAssignment? _assignment;
  MobilePendingAssignmentCommand? _pendingCommand;
  Object? _error;
  bool _loading = true;
  bool _submitting = false;
  bool _changed = false;

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
      final result = await Future.wait([
        widget.driverGateway.get(widget.assignmentId),
        widget.driverGateway.pendingForDriver(widget.driverId),
      ]);
      if (!mounted) {
        return;
      }
      final pending = result[1] as List<MobilePendingAssignmentCommand>;
      setState(() {
        _assignment = result[0] as MobileAssignment;
        _pendingCommand = pending
            .where((command) => command.assignmentId == widget.assignmentId)
            .firstOrNull;
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

  Future<void> _refreshPendingOnly() async {
    try {
      final pending = await widget.driverGateway.pendingForDriver(
        widget.driverId,
      );
      if (mounted) {
        setState(() {
          _pendingCommand = pending
              .where((command) => command.assignmentId == widget.assignmentId)
              .firstOrNull;
        });
      }
    } catch (_) {
      // The original error remains the user-facing source of truth.
    }
  }

  Future<void> _submit(MobileAssignmentCommandType command) async {
    final assignment = _assignment;
    if (assignment == null || _submitting) {
      return;
    }

    String? rejectionReason;
    if (command == MobileAssignmentCommandType.reject) {
      rejectionReason = await _askRejectionReason();
      if (rejectionReason == null || !mounted) {
        return;
      }
    }
    final confirmed = await _confirm(command);
    if (!confirmed || !mounted) {
      return;
    }

    setState(() => _submitting = true);
    try {
      final result = await widget.driverGateway.send(
        assignment,
        command,
        rejectionReason: rejectionReason,
      );
      if (!mounted) {
        return;
      }
      _changed = true;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result.replayed
                ? 'Se recuperó la confirmación anterior de forma segura.'
                : '${command.label} confirmado.',
          ),
        ),
      );
      await _load();
    } catch (error) {
      if (!mounted) {
        return;
      }
      if (error is MobileAssignmentPendingCommandException) {
        await _refreshPendingOnly();
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Ya existe una acción pendiente. Reinténtela sin crear un evento nuevo.',
            ),
          ),
        );
      } else if (error is MobileNetworkException) {
        await _refreshPendingOnly();
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'No se conoce aún el resultado. La acción se guardó para reintentarla exactamente igual.',
            ),
          ),
        );
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
        if (error is MobileApiException) {
          await _load();
        }
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _retryPending() async {
    final command = _pendingCommand;
    if (command == null || _submitting) {
      return;
    }
    final confirmed = await _confirm(command.command, retry: true);
    if (!confirmed || !mounted) {
      return;
    }
    setState(() => _submitting = true);
    try {
      final result = await widget.driverGateway.retry(command);
      if (!mounted) {
        return;
      }
      _changed = true;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result.replayed
                ? 'El servidor confirmó el evento anterior sin duplicarlo.'
                : '${command.command.label} confirmado.',
          ),
        ),
      );
      await _load();
    } catch (error) {
      if (!mounted) {
        return;
      }
      if (error is MobileNetworkException) {
        await _refreshPendingOnly();
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'La confirmación continúa pendiente. Inténtelo más tarde.',
            ),
          ),
        );
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
        if (error is MobileApiException) {
          await _load();
        }
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<bool> _confirm(
    MobileAssignmentCommandType command, {
    bool retry = false,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(retry ? 'Reintentar acción' : command.label),
        content: Text(
          retry
              ? 'Se enviará exactamente el mismo evento guardado. No se creará otra acción.'
              : _confirmationText(command),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(retry ? 'Reintentar' : 'Confirmar'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<String?> _askRejectionReason() async {
    final controller = TextEditingController();
    try {
      return await showDialog<String?>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('Motivo de rechazo'),
          content: TextField(
            controller: controller,
            autofocus: true,
            maxLength: 300,
            maxLines: 3,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              hintText: 'Opcional, máximo 300 caracteres',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(controller.text),
              child: const Text('Continuar'),
            ),
          ],
        ),
      );
    } finally {
      controller.dispose();
    }
  }

  String _confirmationText(MobileAssignmentCommandType command) {
    return switch (command) {
      MobileAssignmentCommandType.accept => 'Aceptará esta solicitud. Ruta Fija reservará su disponibilidad; el vehículo seguirá disponible hasta iniciar el servicio.',
      MobileAssignmentCommandType.reject => 'Rechazará esta solicitud. Esta decisión se registrará como respuesta auténtica del conductor.',
      MobileAssignmentCommandType.start => 'Iniciará el servicio. El conductor y el vehículo pasarán a EN SERVICIO si el servidor confirma las condiciones.',
      MobileAssignmentCommandType.complete => 'Completará el servicio. Esta operación cerrará la asignación si el servidor confirma el estado actual.',
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Detalle de asignación'),
        leading: IconButton(
          tooltip: 'Volver',
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).pop(_changed),
        ),
      ),
      body: SafeArea(child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null || _assignment == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_off_outlined, size: 40),
              const SizedBox(height: 16),
              Text(
                mobileErrorMessage(
                  _error ?? StateError('No se recibió la asignación.'),
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _load,
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ],
          ),
        ),
      );
    }

    final assignment = _assignment!;
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '${assignment.originText} → ${assignment.destinationText}',
                  style: Theme.of(context).textTheme.headlineSmall
                      ?.copyWith(fontWeight: FontWeight.w800),
                ),
              ),
              const SizedBox(width: 12),
              Chip(label: Text(assignment.status.label)),
            ],
          ),
          const SizedBox(height: 20),
          _DetailsCard(assignment: assignment),
          const SizedBox(height: 16),
          if (_pendingCommand != null)
            _PendingActionCard(
              command: _pendingCommand!,
              saving: _submitting,
              onRetry: _retryPending,
            )
          else
            _ActionPanel(
              assignment: assignment,
              saving: _submitting,
              onCommand: _submit,
            ),
          const SizedBox(height: 16),
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Las acciones se validan en el servidor. La aplicación no puede '
                'aceptar ni rechazar por otro conductor, ni duplicar un evento al reintentar.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DetailsCard extends StatelessWidget {
  const _DetailsCard({required this.assignment});

  final MobileAssignment assignment;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _DetailRow(
              'Programada',
              assignmentDateTimeLabel(assignment.scheduledAt),
            ),
            _DetailRow(
              'Fin previsto',
              assignmentDateTimeLabel(assignment.scheduledEndAt),
            ),
            _DetailRow('Vehículo', assignment.vehiclePlate),
            _DetailRow('Grupo', assignment.groupName),
            _DetailRow('Modo', assignment.responseMode.label),
            if (assignment.responseDeadlineAt != null)
              _DetailRow(
                'Plazo de respuesta',
                assignmentDateTimeLabel(assignment.responseDeadlineAt!),
              ),
            if (assignment.acceptedAt != null)
              _DetailRow(
                'Aceptada',
                assignmentDateTimeLabel(assignment.acceptedAt!),
              ),
            if (assignment.rejectedAt != null)
              _DetailRow(
                'Rechazada',
                assignmentDateTimeLabel(assignment.rejectedAt!),
              ),
            if (assignment.rejectionReason != null)
              _DetailRow('Motivo', assignment.rejectionReason!),
            if (assignment.startedAt != null)
              _DetailRow(
                'Iniciada',
                assignmentDateTimeLabel(assignment.startedAt!),
              ),
            if (assignment.completedAt != null)
              _DetailRow(
                'Completada',
                assignmentDateTimeLabel(assignment.completedAt!),
              ),
            if (assignment.expiredAt != null)
              _DetailRow(
                'Vencida',
                assignmentDateTimeLabel(assignment.expiredAt!),
              ),
            if (assignment.notes != null)
              _DetailRow('Notas', assignment.notes!),
          ],
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
      padding: const EdgeInsets.only(bottom: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          Text(value),
        ],
      ),
    );
  }
}

class _PendingActionCard extends StatelessWidget {
  const _PendingActionCard({
    required this.command,
    required this.saving,
    required this.onRetry,
  });

  final MobilePendingAssignmentCommand command;
  final bool saving;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Confirmación pendiente: ${command.command.label}',
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            const Text(
              'No cree otra acción. Reintentar enviará el mismo evento y el servidor '
              'responderá sin duplicar la transición.',
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              key: const Key('assignment-command-retry'),
              onPressed: saving ? null : onRetry,
              icon: saving
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(assignmentCommandIcon(command.command)),
              label: Text(saving ? 'Reintentando…' : 'Reintentar exactamente'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ActionPanel extends StatelessWidget {
  const _ActionPanel({
    required this.assignment,
    required this.saving,
    required this.onCommand,
  });

  final MobileAssignment assignment;
  final bool saving;
  final ValueChanged<MobileAssignmentCommandType> onCommand;

  @override
  Widget build(BuildContext context) {
    if (assignment.requiresMobileResponse) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FilledButton.icon(
            key: const Key('assignment-command-accept'),
            onPressed: saving
                ? null
                : () => onCommand(MobileAssignmentCommandType.accept),
            icon: const Icon(Icons.check_circle_outline),
            label: const Text('Aceptar asignación'),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            key: const Key('assignment-command-reject'),
            onPressed: saving
                ? null
                : () => onCommand(MobileAssignmentCommandType.reject),
            icon: const Icon(Icons.cancel_outlined),
            label: const Text('Rechazar asignación'),
          ),
        ],
      );
    }
    if (assignment.canStart) {
      return FilledButton.icon(
        key: const Key('assignment-command-start'),
        onPressed: saving
            ? null
            : () => onCommand(MobileAssignmentCommandType.start),
        icon: const Icon(Icons.play_circle_outline),
        label: const Text('Iniciar servicio'),
      );
    }
    if (assignment.canComplete) {
      return FilledButton.icon(
        key: const Key('assignment-command-complete'),
        onPressed: saving
            ? null
            : () => onCommand(MobileAssignmentCommandType.complete),
        icon: const Icon(Icons.task_alt_outlined),
        label: const Text('Completar servicio'),
      );
    }
    return const Card(
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Text(
          'No hay una acción móvil disponible para el estado actual. '
          'Actualice si la operación fue modificada recientemente.',
        ),
      ),
    );
  }
}
