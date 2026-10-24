import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_service_handover_repository.dart';
import '../domain/mobile_assignment_models.dart';
import 'mobile_assignment_presentation.dart';

class ServiceHandoverPanel extends StatefulWidget {
  const ServiceHandoverPanel({
    required this.assignment,
    required this.gateway,
    required this.onChanged,
    required this.onBlocked,
    this.externalBlocked = false,
    super.key,
  });
  final MobileAssignment assignment;
  final MobileServiceHandoverGateway gateway;
  final VoidCallback onChanged;
  final ValueChanged<bool> onBlocked;
  final bool externalBlocked;
  @override
  State<ServiceHandoverPanel> createState() => _ServiceHandoverPanelState();
}

class _ServiceHandoverPanelState extends State<ServiceHandoverPanel> {
  MobileHandoverView? _view;
  Object? _error;
  bool _busy = false, _ready = false, _confirmed = false;
  String? _pendingKey;
  int _sequence = 0;
  @override
  void initState() {
    super.initState();
    _read();
  }

  @override
  void dispose() {
    _sequence++;
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant ServiceHandoverPanel old) {
    super.didUpdateWidget(old);
    if (old.assignment.id != widget.assignment.id) {
      _sequence++;
      _view = null;
      _error = null;
      _busy = false;
      _ready = false;
      _confirmed = false;
      _pendingKey = null;
      _read();
    }
  }

  Future<void> _read() async {
    if (_busy) return;
    final n = ++_sequence;
    try {
      final v = await widget.gateway.handoverView(widget.assignment.id);
      if (!mounted || n != _sequence) return;
      final resolved =
          _pendingKey != null &&
          v.rows.any(
            (r) =>
                r.incomingAssignmentId == widget.assignment.id &&
                r.state == 'ASSUMED' &&
                r.resolutionKey == _pendingKey,
          );
      setState(() {
        _view = v;
        if (resolved) {
          _pendingKey = null;
          _confirmed = true;
          _error = null;
        }
      });
      if (resolved) {
        widget.onBlocked(false);
        widget.onChanged();
      }
    } catch (e) {
      if (mounted && n == _sequence) setState(() => _error = e);
    }
  }

  Future<void> _assume() async {
    if (_busy ||
        _pendingKey != null ||
        _confirmed ||
        !_ready ||
        widget.externalBlocked ||
        widget.assignment.status != MobileAssignmentStatus.scheduled ||
        widget.assignment.handoverId == null) {
      return;
    }
    final n = ++_sequence, key = createCorrelationId();
    setState(() {
      _pendingKey = key;
      _busy = true;
      _error = null;
    });
    widget.onBlocked(true);
    try {
      await widget.gateway.assumeHandover(
        widget.assignment,
        eventId: key,
        readyConfirmed: true,
      );
      if (!mounted || n != _sequence) return;
      setState(() {
        _pendingKey = null;
        _confirmed = true;
        _ready = false;
      });
      widget.onBlocked(false);
      widget.onChanged();
    } catch (e) {
      if (!mounted || n != _sequence) return;
      setState(() {
        _error = e;
        if (e is MobileHandoverPreconditionException ||
            (e is MobileApiException &&
                [400, 401, 403, 404, 409, 422, 429].contains(e.status))) {
          _pendingKey = null;
        }
      });
      widget.onBlocked(_pendingKey != null);
    } finally {
      if (mounted && n == _sequence) {
        setState(() => _busy = false);
        await _read();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.assignment;
    final h = _view?.rows
        .where((r) => r.incomingAssignmentId == a.id)
        .firstOrNull;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Relevo y continuidad',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            TextButton(
              onPressed: _busy ? null : _read,
              child: const Text('Consultar relevo del servicio'),
            ),
            if (_view?.startOverdue == true)
              const Text(
                'Inicio previsto superado sin inicio válido. El servicio sigue programado; ADMIN debe decidir expresamente. No se inicia ni cancela automáticamente.',
              ),
            if (_view?.originalStartedAt case final at?)
              Text(
                'Inicio original: ${assignmentDateTimeLabel(at)}. No se reinicia al cambiar de conductor.',
              ),
            if (_error != null)
              Text(
                _error is MobileHandoverPreconditionException
                    ? 'No se envió la confirmación: comprueba el permiso GPS y el primer plano.'
                    : mobileErrorMessage(_error!),
              ),
            if (_pendingKey != null && !_busy)
              const Text(
                'Relevo sin confirmación. Consulta el mismo caso sin enviar otra acción.',
              ),
            for (final r in _view?.rows ?? <MobileHandover>[])
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  '${r.pointReference} · ${r.reason}\n${handoverStateLabel(r.state)}${r.resolvedAt == null ? '' : ' · ${assignmentDateTimeLabel(r.resolvedAt!)}'}${r.pauseSeconds == null ? '' : ' · pausa ${r.pauseSeconds}s'}',
                ),
              ),
            if (a.handoverId != null &&
                a.status == MobileAssignmentStatus.scheduled &&
                h?.state == 'ACCEPTED' &&
                !_confirmed) ...[
              const Text(
                'Aceptar reserva el nuevo tramo; solo Asumir relevo cambia la ejecución. ADMIN comprobó la detención y transferencia. Debes estar preparado personalmente y con GPS disponible.',
              ),
              CheckboxListTile(
                value: _ready,
                onChanged:
                    _busy || _pendingKey != null || widget.externalBlocked
                    ? null
                    : (v) => setState(() => _ready = v == true),
                title: const Text(
                  'Estoy preparado para asumir el relevo online',
                ),
              ),
              FilledButton(
                onPressed:
                    _busy ||
                        _pendingKey != null ||
                        !_ready ||
                        widget.externalBlocked
                    ? null
                    : _assume,
                child: const Text('Asumir relevo'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

String handoverStateLabel(String s) => switch (s) {
  'PENDING_RESPONSE' => 'Oferta pendiente de respuesta',
  'ACCEPTED' => 'Aceptada; todavía no asumida',
  'ASSUMED' => 'Relevo asumido',
  'WITHDRAWN' => 'Relevo retirado',
  'SERVICE_ENDED' => 'Servicio finalizado antes del relevo',
  'EXPIRED' => 'Oferta vencida',
  'REJECTED' => 'Oferta rechazada',
  'CANCELLED' => 'Oferta cancelada',
  _ => 'Estado de relevo registrado',
};
