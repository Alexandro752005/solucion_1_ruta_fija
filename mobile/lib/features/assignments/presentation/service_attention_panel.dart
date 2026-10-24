import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_service_attention_repository.dart';
import '../domain/mobile_assignment_models.dart';
import 'mobile_assignment_presentation.dart';

class ServiceAttentionPanel extends StatefulWidget {
  const ServiceAttentionPanel({
    required this.assignment,
    required this.gateway,
    required this.onChanged,
    required this.onBlocked,
    this.externalBlocked = false,
    super.key,
  });
  final MobileAssignment assignment;
  final MobileServiceAttentionGateway gateway;
  final VoidCallback onChanged;
  final ValueChanged<bool> onBlocked;
  final bool externalBlocked;
  @override
  State<ServiceAttentionPanel> createState() => _ServiceAttentionPanelState();
}

class _ServiceAttentionPanelState extends State<ServiceAttentionPanel> {
  final _detail = TextEditingController();
  List<MobileSafetyAttention> _rows = [];
  String? _reason, _pendingKey, _lastNotified;
  Object? _error;
  bool _stopped = false, _busy = false, _confirmed = false;
  int _sequence = 0;
  @override
  void initState() {
    super.initState();
    _read();
  }

  @override
  void didUpdateWidget(covariant ServiceAttentionPanel old) {
    super.didUpdateWidget(old);
    if (old.assignment.id != widget.assignment.id) {
      _sequence++;
      _rows = [];
      _reason = null;
      _pendingKey = null;
      _lastNotified = null;
      _busy = false;
      _confirmed = false;
      _stopped = false;
      _detail.clear();
      _read();
    }
  }

  @override
  void dispose() {
    _sequence++;
    _detail.dispose();
    super.dispose();
  }

  Future<void> _read() async {
    if (_busy) return;
    final sequence = ++_sequence;
    try {
      final rows = await widget.gateway.ownAttention(widget.assignment.id);
      if (!mounted || sequence != _sequence) return;
      final own = rows
          .where((r) => r.assignmentId == widget.assignment.id)
          .firstOrNull;
      final resolved = _pendingKey != null && own?.operationKey == _pendingKey;
      setState(() {
        _rows = rows;
        if (own != null) _confirmed = true;
        if (resolved) {
          _pendingKey = null;
          _error = null;
          _detail.clear();
        }
      });
      if (resolved) widget.onBlocked(false);
      if (own != null &&
          own.operationKey != _lastNotified &&
          widget.assignment.status == MobileAssignmentStatus.enServicio) {
        _lastNotified = own.operationKey;
        widget.onChanged();
      }
    } catch (e) {
      if (mounted && sequence == _sequence) setState(() => _error = e);
    }
  }

  Future<void> _interrupt() async {
    final reason = _reason, detail = _detail.text.trim();
    if (_busy ||
        _pendingKey != null ||
        _confirmed ||
        widget.externalBlocked ||
        reason == null ||
        detail.isEmpty ||
        detail.length > 300 ||
        widget.assignment.status != MobileAssignmentStatus.enServicio) {
      return;
    }
    final key = createCorrelationId(), sequence = ++_sequence;
    setState(() {
      _busy = true;
      _pendingKey = key;
      _error = null;
    });
    widget.onBlocked(true);
    try {
      await widget.gateway.interruptService(
        widget.assignment,
        eventId: key,
        reason: reason,
        detail: detail,
        safeStopConfirmed: _stopped,
      );
      if (!mounted || sequence != _sequence) return;
      setState(() {
        _confirmed = true;
        _pendingKey = null;
        _lastNotified = key;
        _detail.clear();
      });
      widget.onBlocked(false);
      widget.onChanged();
    } catch (e) {
      if (!mounted || sequence != _sequence) return;
      setState(() {
        _error = e;
        if (e is MobileApiException &&
            [400, 401, 403, 404, 409, 422, 429].contains(e.status)) {
          _pendingKey = null;
        }
      });
      widget.onBlocked(_pendingKey != null);
    } finally {
      if (mounted && sequence == _sequence) {
        setState(() => _busy = false);
        await _read();
      }
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Interrupción y atención segura',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const Text(
            'Una interrupción no declara cumplimiento. Se conserva lo realizado, se detiene GPS y ambos recursos requieren revisión ADMIN por separado.',
          ),
          TextButton(
            onPressed: _busy ? null : _read,
            child: const Text('Consultar atención del servicio'),
          ),
          if (_error != null) Text(mobileErrorMessage(_error!)),
          if (_pendingKey != null && !_busy)
            const Text(
              'Interrupción sin confirmación. Consulta el estado antes de otra acción.',
            ),
          for (final r in _rows)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                '${safetyReasonLabel(r.reason)} · ${assignmentDateTimeLabel(r.at)}\n${r.source == 'CONDUCTOR' ? 'Declaración propia' : 'Comprobación ADMIN comunicada'} · ${r.detail}\nTramo ${r.segmentId} · Incidencia ${r.incidentId}\nDetención confirmada al registrar: ${r.safeStopConfirmed ? 'Sí' : 'Todavía no'}. Conductor: ${r.driverPending ? 'atención pendiente' : 'atención revisada'}. Unidad: ${r.vehiclePending ? 'atención pendiente' : 'revisión registrada'}. La habilitación se comprueba por separado.',
              ),
            ),
          if (widget.assignment.status == MobileAssignmentStatus.enServicio &&
              !_confirmed) ...[
            DropdownButtonFormField<String>(
              initialValue: _reason,
              decoration: const InputDecoration(
                labelText: 'Causa de interrupción',
              ),
              items: const [
                DropdownMenuItem(
                  value: 'ACCIDENT_RISK',
                  child: Text('Accidente con riesgo'),
                ),
                DropdownMenuItem(
                  value: 'UNSAFE_BREAKDOWN',
                  child: Text('Avería insegura'),
                ),
                DropdownMenuItem(
                  value: 'IMPOSSIBLE_SERVICE',
                  child: Text('Imposibilidad real de servicio'),
                ),
              ],
              onChanged: _busy || _pendingKey != null || widget.externalBlocked
                  ? null
                  : (v) => setState(() => _reason = v),
            ),
            TextField(
              controller: _detail,
              maxLength: 300,
              maxLines: 3,
              enabled: !_busy && _pendingKey == null && !widget.externalBlocked,
              decoration: const InputDecoration(
                labelText: 'Motivo sin datos personales ni coordenadas',
              ),
              onChanged: (_) => setState(() {}),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Ya pude detenerme de forma segura'),
              value: _stopped,
              onChanged: _busy || _pendingKey != null || widget.externalBlocked
                  ? null
                  : (v) => setState(() => _stopped = v),
            ),
            const Text(
              'Si aún no puedes confirmar la detención, el riesgo queda registrado y la atención permanece pendiente. Retraso y falta de corroboración GPS no son causas automáticas.',
            ),
            FilledButton(
              onPressed:
                  _busy ||
                      _pendingKey != null ||
                      widget.externalBlocked ||
                      _reason == null ||
                      _detail.text.trim().isEmpty
                  ? null
                  : _interrupt,
              child: const Text('Registrar interrupción propia'),
            ),
          ],
        ],
      ),
    ),
  );
}

String safetyReasonLabel(String value) => switch (value) {
  'ACCIDENT_RISK' => 'Accidente con riesgo',
  'UNSAFE_BREAKDOWN' => 'Avería insegura',
  'IMPOSSIBLE_SERVICE' => 'Imposibilidad real de servicio',
  _ => 'Interrupción registrada',
};
