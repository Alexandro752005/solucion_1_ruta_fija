import 'package:flutter/material.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../../core/network/correlation_id.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_incident_repository.dart';
import '../domain/mobile_incident_models.dart';

class IncidentReportPage extends StatefulWidget {
  const IncidentReportPage({
    required this.incidentGateway,
    this.previousIncidentId,
    super.key,
  });

  final MobileIncidentGateway incidentGateway;
  final String? previousIncidentId;

  @override
  State<IncidentReportPage> createState() => _IncidentReportPageState();
}

class _IncidentReportPageState extends State<IncidentReportPage> {
  final _formKey = GlobalKey<FormState>();
  final _descriptionController = TextEditingController();
  MobileIncidentCategory _category = MobileIncidentCategory.averia;
  MobileIncidentSeverity? _severity;
  String? _relationReason;
  final String _operationKey = createCorrelationId();
  DateTime? _occurredAt;
  bool _submitting = false;
  bool _resultUnknown = false;

  @override
  void dispose() {
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || _resultUnknown || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _submitting = true);
    _occurredAt ??= DateTime.now().toUtc();
    try {
      await widget.incidentGateway.create(
        category: _category,
        description: _descriptionController.text,
        severity: _severity!,
        operationKey: _operationKey,
        occurredAt: _occurredAt,
        previousIncidentId: widget.previousIncidentId,
        relationReason: _relationReason,
      );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Incidencia reportada correctamente.')),
      );
      Navigator.of(context).pop(true);
    } catch (error) {
      if (!mounted) {
        return;
      }
      if (error is MobileNetworkException ||
          (error is MobileApiException && error.status >= 500)) {
        setState(() => _resultUnknown = true);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'No se conoce el resultado. Vuelva a la lista y revise antes de enviar otro reporte.',
            ),
          ),
        );
      } else {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(error))));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _consult() async {
    if (!_resultUnknown || _submitting) return;
    setState(() => _submitting = true);
    try {
      final receipt = await widget.incidentGateway.operation(_operationKey);
      if (receipt.operationKey != _operationKey || receipt.kind != 'REPORT') {
        throw const FormatException('No se pudo contrastar el reporte.');
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Reporte confirmado por el servidor.')),
        );
        Navigator.of(context).pop(true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(mobileErrorMessage(e))));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Reportar incidencia')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                'Nueva incidencia',
                style: Theme.of(context).textTheme.headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              const Text(
                'El servidor asociará este reporte a su conductor autenticado y registrará la hora de recepción.',
              ),
              const SizedBox(height: 20),
              DropdownButtonFormField<MobileIncidentCategory>(
                key: const Key('incident-category'),
                initialValue: _category,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  labelText: 'Categoría',
                ),
                items: [
                  for (final category in MobileIncidentCategory.values)
                    DropdownMenuItem(
                      value: category,
                      child: Text(category.label),
                    ),
                ],
                onChanged: _resultUnknown
                    ? null
                    : (value) {
                        if (value != null) {
                          setState(() => _category = value);
                        }
                      },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<MobileIncidentSeverity>(
                key: const Key('incident-severity'),
                initialValue: _severity,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  labelText: 'Gravedad evaluada',
                ),
                items: [
                  for (final s in MobileIncidentSeverity.values.where(
                    (s) => s != MobileIncidentSeverity.unassessed,
                  ))
                    DropdownMenuItem(value: s, child: Text(s.label)),
                ],
                validator: (v) => v == null ? 'Selecciona la gravedad.' : null,
                onChanged: _resultUnknown
                    ? null
                    : (v) => setState(() => _severity = v),
              ),
              if (widget.previousIncidentId != null) ...[
                Text(
                  'Caso posterior a INCIDENCIA-${widget.previousIncidentId}. Su cierre se conserva.',
                ),
                DropdownButtonFormField<String>(
                  key: const Key('incident-relation'),
                  initialValue: _relationReason,
                  decoration: const InputDecoration(
                    labelText: 'Motivo del nuevo caso',
                  ),
                  items: const [
                    DropdownMenuItem(
                      value: 'RECURRENCE',
                      child: Text('Recurrencia'),
                    ),
                    DropdownMenuItem(
                      value: 'CLOSURE_ERROR',
                      child: Text('Cierre erróneo anterior'),
                    ),
                  ],
                  validator: (v) => v == null ? 'Selecciona el motivo.' : null,
                  onChanged: _resultUnknown
                      ? null
                      : (v) => setState(() => _relationReason = v),
                ),
              ],
              const Text(
                'No incluyas datos personales ni coordenadas. La gravedad no interrumpe el servicio por sí sola. Ante riesgo o imposibilidad real, registra la interrupción segura en tu servicio.',
              ),
              const SizedBox(height: 16),
              TextFormField(
                key: const Key('incident-description'),
                controller: _descriptionController,
                enabled: !_resultUnknown,
                minLines: 4,
                maxLines: 6,
                maxLength: 1000,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  labelText: 'Descripción',
                  hintText:
                      'Explique lo ocurrido para que el ADMIN pueda atenderlo.',
                ),
                validator: (value) {
                  final normalized = value?.trim() ?? '';
                  if (normalized.isEmpty) {
                    return 'Describa la incidencia.';
                  }
                  if (normalized.length > 1000) {
                    return 'Use como máximo 1000 caracteres.';
                  }
                  return null;
                },
              ),
              if (_resultUnknown) ...[
                const SizedBox(height: 16),
                Card(
                  color: Theme.of(context).colorScheme.tertiaryContainer,
                  child: const Padding(
                    padding: EdgeInsets.all(16),
                    child: Text(
                      'El resultado del envío es desconocido por la red. No se guardó una cola automática ni se reenviará solo, para evitar duplicados. Regrese y actualice la lista.',
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 20),
              FilledButton.icon(
                key: const Key('incident-submit'),
                onPressed: _submitting || _resultUnknown ? null : _submit,
                icon: _submitting
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send_outlined),
                label: Text(
                  _submitting
                      ? 'Enviando…'
                      : _resultUnknown
                      ? 'Revise la lista'
                      : 'Enviar reporte',
                ),
              ),
              if (_resultUnknown) ...[
                const SizedBox(height: 12),
                OutlinedButton(
                  key: const Key('incident-consult-operation'),
                  onPressed: _submitting ? null : _consult,
                  child: const Text('Consultar confirmación del reporte'),
                ),
                OutlinedButton.icon(
                  key: const Key('incident-review-list'),
                  onPressed: () => Navigator.of(context).pop(true),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Volver y revisar incidencias'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
