import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_applicant_documentary_repository.dart';
import '../domain/mobile_applicant_documentary_models.dart';

/// Mounted only while the parent holds its purpose-limited access in memory.
class MobileApplicantDossierView extends StatefulWidget {
  const MobileApplicantDossierView({
    required this.gateway,
    required this.token,
    required this.requestId,
    required this.onExpired,
    required this.onReturnToRequests,
    super.key,
  });
  final MobileApplicantDocumentaryGateway gateway;
  final String token;
  final String requestId;
  final VoidCallback onExpired;
  final VoidCallback onReturnToRequests;

  @override
  State<MobileApplicantDossierView> createState() =>
      _MobileApplicantDossierViewState();
}

class _MobileApplicantDossierViewState
    extends State<MobileApplicantDossierView> {
  final _form = GlobalKey<FormState>();
  final _fields = <String, TextEditingController>{};
  final _date = TextEditingController();
  final _condition = TextEditingController();
  final _source = TextEditingController();
  final _invitation = TextEditingController();
  MobileApplicantDossier? _dossier;
  MobileApplicantRequirement? _requirement;
  String? _modality;
  String _expiryMode = 'UNKNOWN';
  String _applicability = 'POR_CONTRASTAR';
  bool _busy = false;
  bool _needsRefresh = true;
  String? _error;
  String? _notice;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final controller in _fields.values) {
      controller.dispose();
    }
    _date.dispose();
    _condition.dispose();
    _source.dispose();
    _invitation.dispose();
    super.dispose();
  }

  void _clearInputs() {
    for (final controller in _fields.values) {
      controller.clear();
    }
    _date.clear();
    _condition.clear();
    _source.clear();
    _invitation.clear();
    _expiryMode = 'UNKNOWN';
    _applicability = 'POR_CONTRASTAR';
  }

  void _accept(MobileApplicantDossier value) {
    _clearInputs();
    _dossier = value;
    _modality = value.modality;
    _requirement = null;
    _needsRefresh = false;
  }

  Future<bool> _load({bool recovering = false}) async {
    if (!mounted) return false;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final value = await widget.gateway.dossier(
        widget.token,
        _dossier?.requestId ?? widget.requestId,
      );
      if (!mounted) return false;
      setState(() {
        _accept(value);
        if (recovering) _notice = 'Estado consultado en el servidor. Compruebe el resultado antes de realizar otra acción; no se repitió la operación.';
      });
      return true;
    } catch (error) {
      if (!mounted) return false;
      if (error is MobileApiException && error.status == 401) {
        widget.onExpired();
      } else {
        setState(() {
          _needsRefresh = true;
          _clearInputs();
          _error = 'No se pudo consultar el estado vigente. Actualice el expediente antes de realizar cambios.';
        });
      }
      return false;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _mutate(
    Future<MobileApplicantDossier> Function() operation,
    String success, {
    bool reapply = false,
  }) async {
    if (_busy || _needsRefresh) return;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      final result = await operation();
      if (!mounted) return;
      setState(() {
        _accept(result);
        _notice = success;
      });
    } catch (error) {
      if (!mounted) return;
      if (error is MobileApiException && error.status == 401) {
        widget.onExpired();
      } else if (error is MobileNetworkException ||
          error is FormatException ||
          (error is MobileApiException &&
              (error.status == 409 || error.status >= 500))) {
        setState(() {
          _needsRefresh = true;
          _clearInputs();
        });
        final consulted = await _load(recovering: true);
        // A lost reapply response may have created a new request. The own list
        // is the source for that identifier; never repeat the creation here.
        if (reapply && consulted && mounted) widget.onReturnToRequests();
      } else {
        setState(() => _error = mobileErrorMessage(error));
      }
    } finally {
      if (mounted) {
        _invitation.clear();
        setState(() => _busy = false);
      }
    }
  }

  void _selectRequirement(String? id) {
    _clearInputs();
    setState(() {
      _requirement = _dossier!.catalog
          .where((value) => value.id == id)
          .firstOrNull;
      for (final key in _requirement?.fieldKeys ?? <String>[]) {
        _fields.putIfAbsent(key, TextEditingController.new);
      }
    });
  }

  Future<void> _save() async {
    final dossier = _dossier;
    final requirement = _requirement;
    if (dossier == null ||
        requirement == null ||
        !_form.currentState!.validate()) {
      return;
    }
    final input = MobileApplicantDocumentInput(
      requirementId: requirement.id,
      fields: {
        for (final key in requirement.fieldKeys)
          if (_fields[key]!.text.trim().isNotEmpty)
            key: _fields[key]!.text.trim(),
      },
      expiryMode: _expiryMode,
      validUntil: _expiryMode == 'DATED'
          ? '${_date.text.trim()}T23:59:59-05:00'
          : null,
      applicability: _applicability,
      condition: _nullable(_condition.text),
      sourceReference: _nullable(_source.text),
    );
    final key = createCorrelationId();
    await _mutate(
      () => widget.gateway.saveDocument(
        widget.token,
        dossier.requestId,
        expectedVersion: dossier.version,
        operationKey: key,
        modality: _modality!,
        document: input,
      ),
      'Datos enviados al servidor. El requisito permanece pendiente de revisión ADMIN.',
    );
  }

  Future<void> _resubmit() async {
    final dossier = _dossier!;
    final key = createCorrelationId();
    await _mutate(
      () => widget.gateway.resubmit(
        widget.token,
        dossier.requestId,
        expectedVersion: dossier.version,
        operationKey: key,
      ),
      'Solicitud reenviada. La revisión corresponde al ADMIN.',
    );
  }

  Future<void> _reapply() async {
    if (!RegExp(r'^[0-9]{6}$').hasMatch(_invitation.text)) {
      setState(
        () => _error = 'Ingrese los seis dígitos de la nueva invitación.',
      );
      return;
    }
    final dossier = _dossier!;
    final key = createCorrelationId();
    final code = _invitation.text;
    await _mutate(
      () => widget.gateway.reapply(
        widget.token,
        dossier.requestId,
        expectedVersion: dossier.version,
        operationKey: key,
        invitationCode: code,
      ),
      'Nueva solicitud recibida, pendiente de revisión y sin acceso operativo.',
      reapply: true,
    );
  }

  @override
  Widget build(BuildContext context) {
    final dossier = _dossier;
    final editable =
        dossier != null &&
        const {'PENDING_REVIEW', 'RETURNED'}.contains(dossier.status) &&
        !_needsRefresh &&
        !_busy;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextButton(
          key: const Key('applicant-dossier-back'),
          onPressed: widget.onReturnToRequests,
          child: const Text('Volver a mis solicitudes'),
        ),
        if (_notice != null) Semantics(liveRegion: true, child: Text(_notice!)),
        if (_error != null) Semantics(liveRegion: true, child: Text(_error!)),
        if (dossier != null) ...[
          Text(
            'Expediente: ${dossier.requestId}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Text(_status(dossier.status)),
          Text('Catálogo ${dossier.catalogId} v${dossier.catalogVersion}'),
          Text(
            'Versión del expediente: ${dossier.version}. Rondas: ${dossier.reviewRounds}.',
          ),
          Text(
            dossier.personalApproved
                ? 'Aprobación personal registrada.'
                : 'Aprobación personal pendiente.',
          ),
          const Text(
            'El expediente personal no acredita grupo, unidad vinculada ni habilitación operativa.',
          ),
          if (dossier.correctionDeadline != null)
            Text(
              'Plazo de subsanación: ${_dateTime(dossier.correctionDeadline!)} (hora de Perú).',
            ),
          if (dossier.overdue)
            const Text(
              'El plazo está vencido. Consulte al ADMIN; no se registra un rechazo automático.',
            ),
          const SizedBox(height: 16),
          const Text('Requisitos y observaciones'),
          for (final requirement in dossier.catalog)
            _requirementStatus(requirement, dossier),
          const SizedBox(height: 16),
          const Text('Historial de revisión'),
          if (dossier.history.isEmpty)
            const Text('Aún no hay decisiones registradas.'),
          for (final event in dossier.history)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Text(
                '${_dateTime(event.occurredAt)} · ${_action(event.action)} · ronda ${event.round}${event.reason == null ? '' : '\n${event.reason}'}',
              ),
            ),
          if (const {
            'PENDING_REVIEW',
            'RETURNED',
          }.contains(dossier.status)) ...[
            const Divider(height: 32),
            const Text('Registrar datos estructurados o subsanar'),
            const Text(
              'Ingrese únicamente los datos del requisito que necesita actualizar. No se admiten archivos. Los datos personales enviados no se muestran de nuevo en esta consulta.',
            ),
            Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  DropdownButtonFormField<String>(
                    key: ValueKey('document-modality-$_modality'),
                    initialValue: _modality,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Modalidad'),
                    items: const [
                      DropdownMenuItem(value: 'TAXI', child: Text('Taxi')),
                      DropdownMenuItem(
                        value: 'COLECTIVO',
                        child: Text('Colectivo'),
                      ),
                    ],
                    onChanged: editable && dossier.modality == null
                        ? (value) => setState(() => _modality = value)
                        : null,
                    validator: (value) => value == null
                        ? 'Seleccione la modalidad del expediente.'
                        : null,
                  ),
                  DropdownButtonFormField<String>(
                    key: ValueKey('document-requirement-${_requirement?.id}'),
                    initialValue: _requirement?.id,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Requisito a actualizar',
                    ),
                    items: [
                      for (final requirement in dossier.catalog)
                        DropdownMenuItem(
                          value: requirement.id,
                          child: Text(
                            '${requirement.id}: ${requirement.title}',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                    ],
                    onChanged: editable ? _selectRequirement : null,
                  ),
                  if (_requirement != null) ...[
                    for (final field in _requirement!.fieldKeys)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: TextFormField(
                          key: Key('document-field-$field'),
                          controller: _fields[field],
                          enabled: editable,
                          maxLength: 200,
                          autocorrect: false,
                          enableSuggestions: false,
                          obscureText: const {
                            'documentNumber',
                            'fullName',
                            'licenseNumber',
                            'titleNumber',
                          }.contains(field),
                          decoration: InputDecoration(
                            labelText: _fieldLabel(field),
                          ),
                        ),
                      ),
                    DropdownButtonFormField<String>(
                      key: ValueKey('document-expiry-$_expiryMode'),
                      initialValue: _expiryMode,
                      decoration: const InputDecoration(
                        labelText: 'Vigencia documentada',
                      ),
                      items: const [
                        DropdownMenuItem(
                          value: 'UNKNOWN',
                          child: Text('Por contrastar'),
                        ),
                        DropdownMenuItem(
                          value: 'DATED',
                          child: Text('Con fecha de vencimiento'),
                        ),
                        DropdownMenuItem(
                          value: 'NO_EXPIRY',
                          child: Text('Sin vencimiento acreditado'),
                        ),
                      ],
                      onChanged: editable
                          ? (value) => setState(() {
                              _expiryMode = value!;
                              _date.clear();
                            })
                          : null,
                    ),
                    if (_expiryMode == 'DATED')
                      TextFormField(
                        key: const Key('document-valid-until'),
                        controller: _date,
                        enabled: editable,
                        decoration: const InputDecoration(
                          labelText: 'Vence el (AAAA-MM-DD)',
                          helperText: 'Se registra al final del día en Perú.',
                        ),
                        validator: (value) => _validDate(value ?? '')
                            ? null
                            : 'Ingrese una fecha real en formato AAAA-MM-DD.',
                      ),
                    DropdownButtonFormField<String>(
                      key: ValueKey('document-applicability-$_applicability'),
                      initialValue: _applicability,
                      decoration: const InputDecoration(
                        labelText: 'Aplicabilidad del requisito',
                      ),
                      items: const [
                        DropdownMenuItem(
                          value: 'POR_CONTRASTAR',
                          child: Text('Por contrastar'),
                        ),
                        DropdownMenuItem(
                          value: 'CONFIRMED',
                          child: Text('Confirmada con documentación'),
                        ),
                      ],
                      onChanged: editable
                          ? (value) => setState(() => _applicability = value!)
                          : null,
                    ),
                    TextFormField(
                      key: const Key('document-condition'),
                      controller: _condition,
                      enabled: editable,
                      maxLength: 500,
                      decoration: const InputDecoration(
                        labelText: 'Condición o excepción (si corresponde)',
                        helperText:
                            'No copie datos personales en esta indicación.',
                      ),
                    ),
                    TextFormField(
                      key: const Key('document-source'),
                      controller: _source,
                      enabled: editable,
                      maxLength: 500,
                      decoration: const InputDecoration(
                        labelText: 'Referencia de la fuente (si se conoce)',
                        helperText:
                            'Una fuente desconocida permanece por contrastar.',
                      ),
                    ),
                    FilledButton(
                      key: const Key('document-save'),
                      onPressed: editable ? _save : null,
                      child: const Text('Enviar datos para revisión'),
                    ),
                  ],
                ],
              ),
            ),
            if (dossier.status == 'RETURNED')
              OutlinedButton(
                key: const Key('document-resubmit'),
                onPressed: editable ? _resubmit : null,
                child: const Text('Reenviar solicitud a revisión'),
              ),
          ],
          if (dossier.status == 'REJECTED') ...[
            if (dossier.reapplyAfter != null)
              Text(
                'Puede volver a postular desde ${_dateTime(dossier.reapplyAfter!)} (hora de Perú), con una nueva invitación.',
              ),
            TextFormField(
              key: const Key('document-reapply-code'),
              controller: _invitation,
              enabled: !_busy && !_needsRefresh,
              obscureText: true,
              keyboardType: TextInputType.number,
              autocorrect: false,
              enableSuggestions: false,
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(6),
              ],
              decoration: const InputDecoration(
                labelText: 'Nueva invitación de la misma organización',
              ),
            ),
            FilledButton(
              key: const Key('document-reapply'),
              onPressed:
                  !_busy &&
                      !_needsRefresh &&
                      (dossier.reapplyAfter == null ||
                          !dossier.reapplyAfter!.isAfter(DateTime.now()))
                  ? _reapply
                  : null,
              child: const Text('Crear nueva postulación'),
            ),
          ],
        ],
        OutlinedButton(
          key: const Key('document-refresh'),
          onPressed: _busy ? null : () => _load(),
          child: const Text('Consultar expediente vigente'),
        ),
        if (_busy)
          const Center(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: CircularProgressIndicator(),
            ),
          ),
      ],
    );
  }

  Widget _requirementStatus(
    MobileApplicantRequirement requirement,
    MobileApplicantDossier dossier,
  ) {
    final status = dossier.documents
        .where((value) => value.requirementId == requirement.id)
        .firstOrNull;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${requirement.id}: ${requirement.title}${requirement.mandatory ? ' (obligatorio)' : ' (condicional)'}',
          ),
          Text(_result(status?.result ?? 'PENDING')),
          if (status?.applicability == 'POR_CONTRASTAR')
            const Text('Aplicabilidad por contrastar.'),
          if (status?.observation != null)
            Text('Indicación de subsanación: ${status!.observation}'),
        ],
      ),
    );
  }

  bool _validDate(String value) {
    if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value.trim())) return false;
    final parsed = DateTime.tryParse(value.trim());
    return parsed != null && parsed.toIso8601String().startsWith(value.trim());
  }

  String? _nullable(String value) => value.trim().isEmpty ? null : value.trim();
  String _dateTime(DateTime value) {
    final peru = value.toUtc().subtract(const Duration(hours: 5));
    return '${peru.day.toString().padLeft(2, '0')}/${peru.month.toString().padLeft(2, '0')}/${peru.year} ${peru.hour.toString().padLeft(2, '0')}:${peru.minute.toString().padLeft(2, '0')}';
  }

  String _status(String value) => switch (value) {
    'PENDING_REVIEW' => 'Pendiente de revisión ADMIN',
    'RETURNED' => 'Devuelta para subsanación',
    'APPROVED' => 'Aprobación personal registrada',
    'REJECTED' => 'Solicitud rechazada',
    _ => 'Estado no disponible',
  };
  String _result(String value) => switch (value) {
    'CONFORME' => 'Conforme según revisión ADMIN',
    'OBSERVADO' => 'Observado',
    'NO_APLICA_JUSTIFICADO' => 'No aplica con justificación',
    'POR_CONTRASTAR' => 'Por contrastar',
    _ => 'Pendiente de revisión',
  };
  String _action(String value) => switch (value) {
    'DOCUMENTS_REVIEWED' => 'Revisión documental ADMIN',
    'DOCUMENTS_CORRECTED' => 'Datos remitidos por postulante',
    'RETURNED' => 'Devuelta para subsanación',
    'DEADLINE_EXTENDED' => 'Plazo ampliado por ADMIN',
    'RESUBMITTED' => 'Reenvío para revisión',
    'PERSONAL_APPROVED' => 'Aprobación personal',
    'REJECTED' => 'Rechazo registrado',
    'ACCOUNT_PROVISIONED' => 'Cuenta vinculada sin habilitación operativa',
    'REAPPLIED' => 'Nueva postulación',
    _ => 'Evento de incorporación',
  };
  String _fieldLabel(String field) =>
      const {
        'documentType': 'Tipo de documento',
        'documentNumber': 'Número de documento',
        'fullName': 'Nombre según documento',
        'issuer': 'Emisor',
        'licenseNumber': 'Número de licencia',
        'licenseClass': 'Clase de licencia',
        'licenseCategory': 'Categoría de licencia',
        'restrictions': 'Restricciones según documento',
        'titleNumber': 'Referencia de habilitación personal',
        'authority': 'Autoridad emisora',
        'serviceScope': 'Ámbito del servicio',
        'operatorReference': 'Referencia del operador',
        'requirementName': 'Requisito aplicable',
        'minimumReference': 'Referencia mínima',
        'eligibilityResult': 'Resultado de elegibilidad documentada',
        'reviewDate': 'Fecha de comprobación (AAAA-MM-DD)',
      }[field] ??
      field;
}
