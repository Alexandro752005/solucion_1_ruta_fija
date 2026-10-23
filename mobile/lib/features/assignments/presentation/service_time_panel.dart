import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_service_time_repository.dart';
import 'mobile_assignment_presentation.dart';

class ServiceTimePanel extends StatefulWidget {
  const ServiceTimePanel({
    required this.assignmentId,
    required this.gateway,
    super.key,
  });
  final String assignmentId;
  final MobileServiceTimeGateway gateway;
  @override
  State<ServiceTimePanel> createState() => _ServiceTimePanelState();
}

class _ServiceTimePanelState extends State<ServiceTimePanel> {
  final _reason = TextEditingController();
  MobileServiceTimeHistory? _history;
  String? _pendingKey;
  Object? _error;
  bool _busy = false;
  int _sequence = 0;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant ServiceTimePanel old) {
    super.didUpdateWidget(old);
    if (old.assignmentId != widget.assignmentId) {
      _sequence++;
      _history = null;
      _pendingKey = null;
      _busy = false;
      _error = null;
      _reason.clear();
      _load();
    }
  }

  @override
  void dispose() {
    _sequence++;
    _reason.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (_busy) return;
    final sequence = ++_sequence;
    try {
      final h = await widget.gateway.timeHistory(widget.assignmentId);
      if (!mounted || sequence != _sequence) return;
      setState(() {
        _history = h;
        if (_pendingKey != null &&
            h.requests.any((r) => r.key == _pendingKey)) {
          _pendingKey = null;
          _reason.clear();
          _error = null;
        }
      });
    } catch (e) {
      if (mounted && sequence == _sequence) setState(() => _error = e);
    }
  }

  Future<void> _request() async {
    final reason = _reason.text.trim();
    if (_busy ||
        _pendingKey != null ||
        reason.isEmpty ||
        reason.length > 300 ||
        !['COMPLETED', 'CANCELLED'].contains(_history?.status)) {
      return;
    }
    final key = createCorrelationId();
    final sequence = ++_sequence;
    setState(() {
      _busy = true;
      _pendingKey = key;
      _error = null;
    });
    try {
      await widget.gateway.requestTimeCorrection(
        widget.assignmentId,
        key,
        reason,
      );
      if (!mounted || sequence != _sequence) return;
      setState(() {
        _pendingKey = null;
        _reason.clear();
      });
    } catch (e) {
      if (!mounted || sequence != _sequence) return;
      setState(() {
        _error = e;
        if (e is MobileApiException &&
            [400, 401, 403, 404, 409, 422, 429].contains(e.status)) {
          _pendingKey = null;
        }
      });
    } finally {
      if (mounted && sequence == _sequence) {
        setState(() => _busy = false);
        await _load();
      }
    }
  }

  String time(DateTime? t) =>
      t == null ? 'Sin cierre' : assignmentDateTimeLabel(t);
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Tiempos de mi participación',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const Text(
            'Las correcciones conservan las horas originales y no cambian los puntos GPS. Solo ADMIN puede registrar la corrección.',
          ),
          TextButton(
            onPressed: _busy ? null : _load,
            child: const Text('Consultar tiempos y solicitudes'),
          ),
          if (_error != null) Text(mobileErrorMessage(_error!)),
          if (_pendingKey != null && !_busy)
            const Text(
              'Solicitud sin confirmación. Consulta el historial antes de solicitar otra.',
            ),
          if (_history case final h?) ...[
            for (final s in h.segments)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  'Tramo ${s.ordinal}\nOriginal: ${time(s.originalStart)} → ${time(s.originalEnd)}\nVigente para revisión: ${time(s.start)} → ${time(s.end)}',
                ),
              ),
            for (final c in h.corrections)
              Text(
                'Corrección ${c.revision} · ${time(c.at)}\n${c.reason}\nCorroboración original: ${timeVerificationLabel(c.originalResult)}. Revisión temporal: ${timeVerificationLabel(c.effectiveResult)}.',
              ),
            for (final r in h.requests)
              Text(
                '${time(r.at)} · ${r.reason} · ${r.correctionId == null ? 'Pendiente de revisión' : 'Atendida mediante corrección'}',
              ),
            if (['COMPLETED', 'CANCELLED'].contains(h.status) &&
                h.segments.isNotEmpty) ...[
              TextField(
                controller: _reason,
                maxLength: 300,
                maxLines: 3,
                enabled: !_busy && _pendingKey == null,
                decoration: const InputDecoration(
                  labelText: 'Motivo de solicitud, sin datos personales',
                ),
              ),
              FilledButton(
                onPressed: _busy || _pendingKey != null ? null : _request,
                child: const Text('Solicitar revisión de tiempos'),
              ),
            ],
          ],
        ],
      ),
    ),
  );
}

String timeVerificationLabel(String? value) => switch (value) {
  null => 'No registrada',
  'CORROBORATED' => 'Llegada al terminal corroborada',
  'DISCREPANCY' => 'Ubicación válida fuera del terminal: requiere revisión',
  'NOT_CORROBORATED_TERMINAL_UNCONFIGURED' =>
    'Terminal sin ubicación configurada',
  'NOT_CORROBORATED_NO_POINT' => 'Sin punto válido para corroborar',
  'NOT_CORROBORATED_STALE_OR_IMPRECISE' =>
    'Punto fuera del intervalo o sin precisión suficiente',
  'NOT_CORROBORATED_OUTSIDE_CORRECTED_SEGMENT' =>
    'Punto anterior al tramo corregido',
  'NOT_REASSESSABLE_ORIGINAL_POINT_EXPIRED_OR_ABSENT' =>
    'No se puede volver a contrastar: punto original ausente o vencido',
  _ => 'Resultado no disponible',
};
