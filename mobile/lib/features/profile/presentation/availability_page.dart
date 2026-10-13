import 'package:flutter/material.dart';

import '../../../core/offline/mobile_offline.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';

class AvailabilityPage extends StatefulWidget {
  const AvailabilityPage({
    required this.driverGateway,
    required this.offlineSyncController,
    required this.offlineOwner,
    super.key,
  });

  final MobileDriverGateway driverGateway;
  final MobileOfflineSyncController offlineSyncController;
  final MobileOfflineQueueOwner offlineOwner;

  @override
  State<AvailabilityPage> createState() => _AvailabilityPageState();
}

class _AvailabilityPageState extends State<AvailabilityPage> {
  MobileAvailability? _availability;
  MobileAvailabilityStatus? _selectedStatus;
  Object? _error;
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final availability = await widget.driverGateway.availability();
      if (!mounted) {
        return;
      }
      setState(() {
        _availability = availability;
        _selectedStatus = availability.status.canBeChosenByDriver
            ? availability.status
            : null;
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

  Future<void> _save() async {
    final selected = _selectedStatus;
    final current = _availability;
    if (_saving ||
        selected == null ||
        current == null ||
        selected == current.status) {
      return;
    }

    setState(() => _saving = true);
    try {
      final submission = await widget.offlineSyncController.submitAvailability(
        owner: widget.offlineOwner,
        targetAvailability: selected.apiValue,
        expectedAvailabilityUpdatedAt: current.updatedAt,
      );
      if (!mounted) {
        return;
      }
      if (submission.delivered) {
        await _load();
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              submission.replayed
                  ? 'El cambio anterior fue confirmado sin duplicarlo.'
                  : 'Disponibilidad actualizada a ${selected.label}.',
            ),
          ),
        );
      } else {
        if (submission.blocked) {
          await _load();
          if (!mounted) {
            return;
          }
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              submission.blocked
                  ? 'El cambio requiere conciliación. Abra Pendientes de sincronización antes de intentarlo nuevamente.'
                  : 'El cambio quedó guardado en Pendientes de sincronización.',
            ),
          ),
        );
      }
    } catch (error) {
      if (mounted) {
        final message = error is MobileOfflinePendingActionException
            ? error.message
            : error is StateError
            ? error.message
            : mobileErrorMessage(error);
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null || _availability == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                mobileErrorMessage(
                  _error ?? StateError('No se recibió disponibilidad.'),
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

    final current = _availability!;
    final canChange = current.status.canBeChosenByDriver;
    final selectableStatuses = MobileAvailabilityStatus.values
        .where((status) => status.canBeChosenByDriver)
        .toList(growable: false);
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text(
          'Disponibilidad',
          style: Theme.of(context).textTheme.headlineSmall
              ?.copyWith(fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        const Text(
          'El cambio se intenta en Ruta Fija; ante una falla temporal queda visible para sincronizarlo manualmente.',
        ),
        const SizedBox(height: 20),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Estado actual'),
                const SizedBox(height: 8),
                Chip(
                  avatar: const Icon(Icons.toggle_on_outlined),
                  label: Text(current.status.label),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        if (!canChange)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'RESERVADO y EN SERVICIO son controlados por la asignación. '
                'No pueden cambiarse manualmente desde el conductor.',
              ),
            ),
          )
        else ...[
          Text(
            'Seleccione su estado administrativo',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          SegmentedButton<MobileAvailabilityStatus>(
            segments: [
              for (final status in selectableStatuses)
                ButtonSegment<MobileAvailabilityStatus>(
                  value: status,
                  label: Text(status.label),
                ),
            ],
            selected: {_selectedStatus ?? current.status},
            onSelectionChanged: _saving
                ? null
                : (selection) =>
                      setState(() => _selectedStatus = selection.single),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            key: const Key('availability-save'),
            onPressed: _saving || _selectedStatus == current.status
                ? null
                : _save,
            icon: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: Text(_saving ? 'Actualizando…' : 'Guardar disponibilidad'),
          ),
        ],
      ],
    );
  }
}
