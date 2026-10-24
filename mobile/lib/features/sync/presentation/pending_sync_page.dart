import 'dart:async';

import 'package:flutter/material.dart';

import '../../../core/offline/mobile_offline.dart';
import '../../../core/presentation/mobile_date_time.dart';

class PendingSyncPage extends StatefulWidget {
  const PendingSyncPage({
    required this.offlineSyncController,
    required this.offlineOwner,
    super.key,
  });

  final MobileOfflineSyncController offlineSyncController;
  final MobileOfflineQueueOwner offlineOwner;

  @override
  State<PendingSyncPage> createState() => _PendingSyncPageState();
}

class _PendingSyncPageState extends State<PendingSyncPage> {
  @override
  void initState() {
    super.initState();
    unawaited(widget.offlineSyncController.bind(widget.offlineOwner));
  }

  @override
  void didUpdateWidget(covariant PendingSyncPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.offlineOwner.sameAs(widget.offlineOwner)) {
      unawaited(widget.offlineSyncController.bind(widget.offlineOwner));
    }
  }

  Future<void> _synchronize() async {
    final outcome = await widget.offlineSyncController.synchronizeNow(
      owner: widget.offlineOwner,
    );
    if (!mounted) {
      return;
    }
    final message = switch (outcome.status) {
      MobileOfflineSyncOutcomeStatus.delivered =>
        'Los pendientes se conciliaron en orden.',
      MobileOfflineSyncOutcomeStatus.idle => 'No hay acciones pendientes.',
      MobileOfflineSyncOutcomeStatus.temporaryFailure =>
        'No fue posible conectar. La acción se conserva para reintentarla.',
      MobileOfflineSyncOutcomeStatus.waitingForRetry => 'La acción conserva una espera de reintento. Puede volver a sincronizar cuando decida.',
      MobileOfflineSyncOutcomeStatus.blocked => 'La cola se detuvo. Actualice el estado del servidor y concilie la primera acción.',
      MobileOfflineSyncOutcomeStatus.sessionCleared =>
        'La sesión cambió y los pendientes protegidos fueron eliminados.',
      MobileOfflineSyncOutcomeStatus.busy =>
        'Ya se está procesando una sincronización visible.',
    };
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _discard(MobileOfflineEvent event) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Descartar acción bloqueada'),
        content: const Text(
          'Primero verifique el estado actual en Ruta Fija. Descartar no aplica esta acción ni modifica el servidor; solo permite conciliar la siguiente acción pendiente.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            key: const Key('pending-sync-discard-confirm'),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Descartar pendiente'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    await widget.offlineSyncController.discardBlocked(event);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Acción bloqueada descartada. Revise el siguiente pendiente antes de sincronizar.',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.offlineSyncController,
      builder: (context, _) {
        final controller = widget.offlineSyncController;
        final events = controller.events;
        return Scaffold(
          appBar: AppBar(
            title: const Text('Pendientes de sincronización'),
            actions: [
              IconButton(
                tooltip: 'Actualizar pendientes',
                onPressed: controller.isSynchronizing
                    ? null
                    : () => controller.refresh(owner: widget.offlineOwner),
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          body: SafeArea(
            child: events.isEmpty
                ? _EmptyPendingSync(
                    onRefresh: () =>
                        controller.refresh(owner: widget.offlineOwner),
                  )
                : ListView(
                    padding: const EdgeInsets.all(20),
                    children: [
                      const Text(
                        'Las acciones se conservan en el orden en que fueron creadas. La aplicación solo las envía cuando usted lo indica.',
                      ),
                      const SizedBox(height: 16),
                      _HeadPendingSyncCard(
                        event: events.first,
                        isSynchronizing: controller.isSynchronizing,
                        onSynchronize: _synchronize,
                        onDiscard: () => _discard(events.first),
                      ),
                      if (events.length > 1) ...[
                        const SizedBox(height: 20),
                        Text(
                          'Esperando la primera acción',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        for (final event in events.skip(1))
                          _QueuedEventTile(event: event),
                      ],
                    ],
                  ),
          ),
        );
      },
    );
  }
}

class _EmptyPendingSync extends StatelessWidget {
  const _EmptyPendingSync({required this.onRefresh});

  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      liveRegion: true,
      label: 'Estado de sincronización: no hay acciones pendientes.',
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_done_outlined, size: 48),
              const SizedBox(height: 16),
              Text(
                'No hay acciones pendientes.',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              const Text(
                'Disponibilidad, inicio y cierre se confirmaron o no existe una acción por conciliar.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh),
                label: const Text('Actualizar'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HeadPendingSyncCard extends StatelessWidget {
  const _HeadPendingSyncCard({
    required this.event,
    required this.isSynchronizing,
    required this.onSynchronize,
    required this.onDiscard,
  });

  final MobileOfflineEvent event;
  final bool isSynchronizing;
  final Future<void> Function() onSynchronize;
  final Future<void> Function() onDiscard;

  @override
  Widget build(BuildContext context) {
    final blocked = event.isBlocked;
    final retryAt = event.nextRetryAt;
    final stateLabel = blocked ? 'bloqueada' : 'pendiente';
    return Semantics(
      container: true,
      label:
          'Primera acción de sincronización: ${_eventLabel(event.type)}. Estado: $stateLabel.',
      child: Card(
        color: blocked
            ? Theme.of(context).colorScheme.errorContainer
            : Theme.of(context).colorScheme.secondaryContainer,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    blocked
                        ? Icons.warning_amber_outlined
                        : Icons.cloud_upload_outlined,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _eventLabel(event.type),
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.w800),
                    ),
                  ),
                  Chip(label: Text(blocked ? 'Bloqueada' : 'Pendiente')),
                ],
              ),
              const SizedBox(height: 12),
              Text('Creada: ${mobileDateTimeLabel(event.occurredAt)}'),
              if (event.assignmentId != null)
                Text('Asignación: ${event.assignmentId}'),
              if (retryAt != null && !blocked)
                Text(
                  'Intentos: ${event.attempts}. Espera informativa hasta ${mobileDateTimeLabel(retryAt)}.',
                ),
              if (blocked && event.blockedCode != null)
                Text('Motivo del servidor: ${event.blockedCode}.'),
              const SizedBox(height: 16),
              if (blocked) ...[
                const Text(
                  'Actualice primero el estado de Ruta Fija. Esta acción no se enviará nuevamente hasta que usted la descarte de forma consciente.',
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  key: const Key('pending-sync-discard'),
                  onPressed: isSynchronizing ? null : onDiscard,
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Descartar después de conciliar'),
                ),
              ] else
                FilledButton.icon(
                  key: const Key('pending-sync-now'),
                  onPressed: isSynchronizing ? null : onSynchronize,
                  icon: isSynchronizing
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.sync),
                  label: Text(
                    isSynchronizing
                        ? 'Sincronizando...'
                        : 'Sincronizar pendientes',
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QueuedEventTile extends StatelessWidget {
  const _QueuedEventTile({required this.event});

  final MobileOfflineEvent event;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.schedule_outlined),
        title: Text(_eventLabel(event.type)),
        subtitle: const Text(
          'Se conserva en orden hasta resolver la primera acción.',
        ),
      ),
    );
  }
}

String _eventLabel(MobileOfflineEventType type) {
  return switch (type) {
    MobileOfflineEventType.availabilityChange => 'Cambio de disponibilidad',
    MobileOfflineEventType.serviceStart => 'Inicio de servicio',
    MobileOfflineEventType.serviceComplete => 'Cierre de servicio',
  };
}
