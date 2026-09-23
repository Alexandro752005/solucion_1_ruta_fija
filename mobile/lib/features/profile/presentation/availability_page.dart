import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_driver_repository.dart';
import '../domain/mobile_driver_models.dart';

class AvailabilityPage extends StatefulWidget {
  const AvailabilityPage({required this.driverGateway, super.key});

  final MobileDriverGateway driverGateway;

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
      final updated = await widget.driverGateway.changeAvailability(selected);
      if (!mounted) {
        return;
      }
      setState(() {
        _availability = updated;
        _selectedStatus = updated.status.canBeChosenByDriver
            ? updated.status
            : null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Disponibilidad actualizada a ${updated.status.label}.',
          ),
        ),
      );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
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
          'El estado se consulta y actualiza directamente en Ruta Fija.',
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
