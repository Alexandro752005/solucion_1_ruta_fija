import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_notice_repository.dart';

class NoticesPage extends StatefulWidget {
  const NoticesPage({required this.gateway, super.key});
  final MobileNoticeGateway gateway;
  @override
  State<NoticesPage> createState() => _NoticesPageState();
}

class _NoticesPageState extends State<NoticesPage> {
  List<MobileNotice> _items = [];
  bool _busy = false;
  String? _pending;
  Object? _error;
  int _page = 0, _pages = 0;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final result = await widget.gateway.list(page: _page);
      if (!mounted) return;
      setState(() {
        _items = result.items;
        _pages = result.totalPages;
        _error = null;
        if (_items.any((n) => n.id == _pending && n.readAt != null)) {
          _pending = null;
        }
      });
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _read(MobileNotice n) async {
    if (_busy || _pending != null) return;
    setState(() {
      _busy = true;
      _pending = n.id;
    });
    try {
      final receipt = await widget.gateway.read(n.id);
      if (!mounted) return;
      setState(() {
        _items = _items.map((v) => v.id == receipt.id ? receipt : v).toList();
        _pending = null;
      });
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Bandeja de avisos')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Text(
          'Consultar no confirma lectura. Leer un aviso no sustituye Leído del comunicado ni responde ofertas.',
        ),
        TextButton(
          key: const Key('notices-refresh'),
          onPressed: _busy ? null : _load,
          child: const Text('Consultar confirmaciones'),
        ),
        if (_error != null) Text(mobileErrorMessage(_error!)),
        if (_busy) const LinearProgressIndicator(),
        if (_pending != null)
          const Text(
            'Lectura sin confirmar. Consulta el resultado antes de otra acción.',
          ),
        for (final n in _items)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _label(n.eventType),
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text(n.createdAt.toLocal().toString()),
                  for (final r in n.references.entries)
                    Text('${_reference(r.key)}: ${r.value}'),
                  Text(
                    n.externalStatus == 'PROVIDER_ACCEPTED'
                        ? 'Aceptado por el proveedor; entrega al equipo y lectura sin acreditar'
                        : n.externalStatus == 'NOT_REQUIRED'
                        ? 'Aviso disponible en bandeja'
                        : 'Aviso disponible en bandeja; entrega externa no realizada',
                  ),
                  Text(
                    n.readAt == null
                        ? 'Sin lectura de aviso confirmada'
                        : 'Lectura de aviso: ${n.readAt!.toLocal()}',
                  ),
                  if (n.readAt == null)
                    FilledButton(
                      key: Key('notice-read-${n.id}'),
                      onPressed: _busy || _pending != null
                          ? null
                          : () => _read(n),
                      child: const Text('Confirmar lectura de aviso'),
                    ),
                ],
              ),
            ),
          ),
        if (_items.isEmpty && !_busy)
          const Text('No hay avisos en esta consulta.'),
        Row(
          children: [
            TextButton(
              onPressed: _busy || _pending != null || _page == 0
                  ? null
                  : () {
                      setState(() => _page--);
                      _load();
                    },
              child: const Text('Anterior'),
            ),
            TextButton(
              onPressed: _busy || _pending != null || _page + 1 >= _pages
                  ? null
                  : () {
                      setState(() => _page++);
                      _load();
                    },
              child: const Text('Siguiente'),
            ),
          ],
        ),
      ],
    ),
  );
  String _label(String s) {
    if (s.startsWith('assignment.')) return 'Cambio de oferta';
    if (s.startsWith('incident.')) return 'Cambio de incidencia';
    if (s.startsWith('service.')) return 'Cambio de servicio';
    return switch (s) {
      'announcement.published' => 'Comunicado publicado',
      'driver.suspended' => 'Suspensión operativa',
      'driver.status.changed' => 'Cambio de disponibilidad',
      _ => 'Aviso operativo',
    };
  }

  String _reference(String k) => switch (k) {
    'assignmentId' => 'Oferta',
    'serviceId' => 'Servicio',
    'incidentId' => 'Incidencia',
    'driverId' => 'Conductor',
    'vehicleId' => 'Unidad',
    'announcementId' => 'Comunicado',
    'attentionId' => 'Atención',
    'handoverId' => 'Relevo',
    _ => 'Referencia',
  };
}
