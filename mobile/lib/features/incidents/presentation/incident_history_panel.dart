import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_incident_repository.dart';
import '../domain/mobile_incident_models.dart';

class IncidentHistoryPanel extends StatefulWidget {
  const IncidentHistoryPanel({
    required this.incident,
    required this.gateway,
    required this.onChanged,
    required this.onBlocked,
    super.key,
  });
  final MobileIncident incident;
  final MobileIncidentGateway gateway;
  final VoidCallback onChanged;
  final ValueChanged<bool> onBlocked;
  @override
  State<IncidentHistoryPanel> createState() => _IncidentHistoryPanelState();
}

class _IncidentHistoryPanelState extends State<IncidentHistoryPanel> {
  final _note = TextEditingController();
  List<MobileIncidentAction> _rows = [];
  Object? _error;
  bool _busy = false, _uncertain = false;
  String? _eventKey;
  int _sequence = 0;
  @override
  void initState() {
    super.initState();
    _read();
  }

  @override
  void dispose() {
    _sequence++;
    _note.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant IncidentHistoryPanel old) {
    super.didUpdateWidget(old);
    if (old.incident.id != widget.incident.id ||
        old.incident.version != widget.incident.version) {
      // The parent cannot replace a command whose result is still uncertain.
      // Discarding that key would invite a second POST for the same action.
      if (_busy || _uncertain) return;
      _sequence++;
      _read();
    }
  }

  Future<void> _read() async {
    if (_busy) return;
    final n = ++_sequence;
    try {
      final rows = await widget.gateway.history(widget.incident.id);
      if (!mounted || n != _sequence) return;
      final matched =
          _eventKey != null &&
          rows.any(
            (r) => r.operationKey == _eventKey && r.kind == 'INFORMATION',
          );
      setState(() {
        _rows = rows;
        _error = null;
        if (matched) {
          _eventKey = null;
          _uncertain = false;
        }
      });
      if (matched) {
        _note.clear();
        widget.onBlocked(false);
        widget.onChanged();
      }
    } catch (e) {
      if (mounted && n == _sequence) setState(() => _error = e);
    }
  }

  Future<void> _submit() async {
    if (_busy ||
        _uncertain ||
        widget.incident.isClosed ||
        _note.text.trim().isEmpty) {
      return;
    }
    final n = ++_sequence;
    _eventKey = createCorrelationId();
    setState(() {
      _busy = true;
      _error = null;
    });
    widget.onBlocked(true);
    try {
      await widget.gateway.information(
        widget.incident.id,
        version: widget.incident.version,
        operationKey: _eventKey!,
        note: _note.text.trim(),
      );
      if (!mounted || n != _sequence) return;
      setState(() {
        _eventKey = null;
        _uncertain = false;
      });
      _note.clear();
      widget.onBlocked(false);
      widget.onChanged();
    } catch (e) {
      if (!mounted || n != _sequence) return;
      setState(() {
        _error = e;
        _uncertain =
            !(e is MobileApiException &&
                [400, 401, 403, 404, 409, 422, 429].contains(e.status));
        if (!_uncertain) _eventKey = null;
      });
      widget.onBlocked(_uncertain);
    } finally {
      if (mounted && n == _sequence) {
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
            'Responsable e historial',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Text(
            'Responsable: ${widget.incident.responsibleAdminId == null ? 'Sin designar' : 'CUENTA-${widget.incident.responsibleAdminId}'}. Gravedad: ${widget.incident.severity.label}.',
          ),
          Text(
            widget.incident.operationalEffect == 'INTERRUPTION_RECORDED'
                ? 'Existe una interrupción registrada; la atención de los recursos se revisa por separado.'
                : 'No hay interrupción registrada. Esto no confirma que sea seguro continuar.',
          ),
          if (widget.incident.previousIncidentId != null)
            Text(
              'Vínculo original: INCIDENCIA-${widget.incident.previousIncidentId}.',
            ),
          const Text(
            'El cierre no devuelve recursos a operación ni sustituye comprobaciones de seguridad, documentos o mantenimiento.',
          ),
          TextButton(
            onPressed: _busy ? null : _read,
            child: const Text('Consultar historial y confirmación'),
          ),
          if (_error != null) Text(mobileErrorMessage(_error!)),
          if (_uncertain)
            const Text(
              'No se confirmó la información. Consulta antes de enviar otra acción.',
            ),
          for (final r in _rows)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                '${_kind(r.kind)} · CUENTA-${r.actorId} · ${r.recordedAt.toLocal()}\n${r.detail.entries.map((e) => '${_field(e.key)}: ${_value(e.value)}').join('\n')}',
              ),
            ),
          if (widget.incident.isClosed)
            const Text(
              'El cierre se conserva; no se permite reapertura ni añadir información.',
            ),
          if (!widget.incident.isClosed) ...[
            TextField(
              key: const Key('incident-information-note'),
              controller: _note,
              maxLength: 1000,
              maxLines: 3,
              enabled: !_busy && !_uncertain,
              decoration: const InputDecoration(
                labelText:
                    'Información adicional sin datos personales ni coordenadas',
              ),
              onChanged: (_) => setState(() {}),
            ),
            FilledButton(
              key: const Key('incident-information-submit'),
              onPressed: _busy || _uncertain || _note.text.trim().isEmpty
                  ? null
                  : _submit,
              child: const Text('Aportar información'),
            ),
          ],
        ],
      ),
    ),
  );
  String _kind(String s) => switch (s) {
    'REPORT' => 'Reporte original',
    'RESPONSIBILITY' => 'ADMIN responsable',
    'ASSESSMENT' => 'Evaluación de gravedad',
    'FOLLOW_UP' => 'Seguimiento ADMIN',
    'INFORMATION' => 'Información del conductor',
    'RESOLUTION' => 'Cierre ADMIN',
    'DRIVER_CLOSE' => 'Cierre por conductor',
    _ => 'Acción registrada',
  };
  String _value(Object? v) => v is bool
      ? (v ? 'Sí' : 'No')
      : switch (v) {
          'LOW' => 'Baja',
          'MEDIUM' => 'Media',
          'HIGH' => 'Alta',
          'CRITICAL' => 'Crítica',
          'UNASSESSED' => 'Sin evaluar',
          'REFERENCE' => 'Referencia',
          'UNAVAILABLE_JUSTIFIED' => 'Imposibilidad justificada',
          'NOT_REQUIRED' => 'No requerida',
          'RECURRENCE' => 'Recurrencia',
          'CLOSURE_ERROR' => 'Cierre erróneo',
          _ => v?.toString() ?? 'Sin dato',
        };
  String _field(String s) => switch (s) {
    'reason' => 'Motivo',
    'note' => 'Nota',
    'diagnosis' => 'Diagnóstico',
    'actions' => 'Acciones',
    'result' => 'Resultado',
    'adminId' => 'ADMIN designado',
    'previousAdminId' => 'ADMIN anterior',
    'before' => 'Gravedad anterior',
    'after' => 'Gravedad registrada',
    'severity' => 'Gravedad',
    'evidenceMode' => 'Tratamiento de evidencia',
    'evidenceReference' => 'Referencia',
    'evidenceExplanation' => 'Explicación',
    'evidenceRequired' => 'Evidencia requerida',
    'responsibleAdminId' => 'ADMIN responsable',
    'resourcesReleased' => 'Liberación de recursos',
    'closedAt' => 'Fecha de cierre',
    'previousIncidentId' => 'Incidencia anterior',
    'relationReason' => 'Motivo del vínculo',
    _ => 'Referencia de acción',
  };
}
