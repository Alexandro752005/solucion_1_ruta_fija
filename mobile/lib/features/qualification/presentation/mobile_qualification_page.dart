import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
import '../data/mobile_qualification_repository.dart';
import '../domain/mobile_qualification.dart';

/// Post-provisioning access: no operational repository is invoked by this page.
/// Fresh personal input is obscured, never repopulated, persisted, or uploaded.
class MobileQualificationPage extends StatefulWidget {
  const MobileQualificationPage({
    required this.gateway,
    required this.sessionController,
    this.initialState,
    this.onSessionRefreshed,
    super.key,
  });
  final MobileQualificationGateway gateway;
  final MobileSessionController sessionController;
  final MobileQualification? initialState;
  final VoidCallback? onSessionRefreshed;

  @override
  State<MobileQualificationPage> createState() =>
      _MobileQualificationPageState();
}

class _MobileQualificationPageState extends State<MobileQualificationPage> {
  final _unitForm = GlobalKey<FormState>();
  final _documentForm = GlobalKey<FormState>();
  final _scroll = ScrollController();
  final _vehicle = TextEditingController();
  final _reference = TextEditingController();
  final _date = TextEditingController();
  final _fields = <String, TextEditingController>{};
  MobileQualification? _state;
  List<MobilePersonalRequirement> _catalog = const [];
  MobilePersonalRequirement? _requirement;
  String _kind = 'OWN';
  String _expiry = 'UNKNOWN';
  String? _error;
  String? _notice;
  bool _busy = false;
  bool _mustConsult = false;

  @override
  void initState() {
    super.initState();
    _state = widget.initialState;
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    for (final controller in [_vehicle, _reference, _date, ..._fields.values]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _load({bool reconciliation = false}) async {
    if (_busy && !reconciliation) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final state = await widget.gateway.qualification();
      if (!mounted) return;
      setState(() {
        _state = state;
        _mustConsult = false;
        if (reconciliation) {
          _notice =
              'Se consultó el estado del servidor. La respuesta anterior '
              'no se confirmó; revise el estado antes de otra acción.';
        }
      });
      // Failure to load an auxiliary catalog must not hide the current status.
      final catalog = await widget.gateway.personalCatalog();
      if (mounted) setState(() => _catalog = catalog);
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
        if (_scroll.hasClients && (_notice != null || _error != null)) {
          _scroll.jumpTo(0);
        }
      }
    }
  }

  void _clearInputs() {
    _vehicle.clear();
    _reference.clear();
    _date.clear();
    for (final field in _fields.values) {
      field.clear();
    }
  }

  Future<void> _mutate(
    Future<MobileQualification> Function() action,
    String notice,
  ) async {
    if (_busy || _mustConsult) return;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      final state = await action();
      if (!mounted) return;
      setState(() {
        _state = state;
        _clearInputs();
        _notice = notice;
      });
    } catch (error) {
      if (!mounted) return;
      if (error is MobileNetworkException ||
          error is FormatException ||
          (error is MobileApiException &&
              (error.status == 409 || error.status >= 500))) {
        setState(() {
          _mustConsult = true;
          _clearInputs();
        });
        // Read only reconciliation. No POST replay or offline queue exists.
        await _load(reconciliation: true);
      } else {
        setState(() => _error = mobileErrorMessage(error));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _requestUnit() async {
    if (_state == null || !_unitForm.currentState!.validate()) return;
    final state = _state!;
    final vehicle = _vehicle.text.trim();
    final reference = _reference.text.trim();
    final kind = _kind;
    final operationKey = createCorrelationId();
    await _mutate(
      () => widget.gateway.requestUnit(
        expectedVersion: state.version,
        operationKey: operationKey,
        kind: kind,
        reference: reference,
        vehicleId: vehicle.isEmpty ? null : vehicle,
      ),
      'Solicitud recibida por el servidor. Requiere revisión ADMIN; '
      'no vincula unidad ni habilita operación.',
    );
  }

  void _selectDocument(String? id) {
    for (final field in _fields.values) {
      field.clear();
    }
    _date.clear();
    setState(() {
      _requirement = _catalog.where((item) => item.id == id).firstOrNull;
      _expiry = 'UNKNOWN';
      for (final key in _requirement?.fieldKeys ?? <String>[]) {
        _fields.putIfAbsent(key, TextEditingController.new);
      }
    });
  }

  Future<void> _presentDocument() async {
    if (_state == null ||
        _requirement == null ||
        !_documentForm.currentState!.validate()) {
      return;
    }
    final state = _state!;
    final document = MobileQualificationDocumentInput(
      requirementId: _requirement!.id,
      fields: {
        for (final key in _requirement!.fieldKeys)
          if (_fields[key]!.text.trim().isNotEmpty)
            key: _fields[key]!.text.trim(),
      },
      expiryMode: _expiry,
      validUntil: _expiry == 'DATED'
          ? _exclusivePeruEnd(_date.text.trim())
          : null,
      applicability: 'POR_CONTRASTAR',
    );
    final operationKey = createCorrelationId();
    await _mutate(
      () => widget.gateway.presentDocument(
        expectedVersion: state.version,
        operationKey: operationKey,
        document: document,
      ),
      'Datos recibidos para revisión ADMIN. No equivalen a renovación aprobada '
      'ni habilitación operativa.',
    );
  }

  Future<void> _refreshSession() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final refreshed = await widget.sessionController.refreshAccessToken();
      if (refreshed && mounted) widget.onSessionRefreshed?.call();
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = _state;
    final editable = state != null && !_busy && !_mustConsult;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mi incorporación'),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'Consultas propias',
            enabled: !_busy && !_mustConsult,
            onSelected: (route) => Navigator.of(context).pushNamed(route),
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: '/announcements',
                child: Text('Mis comunicados recibidos'),
              ),
              PopupMenuItem(
                value: '/incidents',
                child: Text('Mis incidencias e historial'),
              ),
              PopupMenuItem(
                value: '/notices',
                child: Text('Bandeja de avisos'),
              ),
            ],
          ),
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: _busy
                ? null
                : () async {
                    try {
                      await widget.sessionController.logout();
                    } catch (error) {
                      if (mounted) {
                        setState(() => _error = mobileErrorMessage(error));
                      }
                    }
                  },
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          controller: _scroll,
          padding: const EdgeInsets.all(20),
          children: [
            const Text(
              'Cuenta, aprobación personal, unidad y habilitación '
              'operativa son estados separados.',
            ),
            if (_notice != null)
              Semantics(liveRegion: true, child: Text(_notice!)),
            if (_error != null)
              Semantics(liveRegion: true, child: Text(_error!)),
            if (state != null) ...[
              const SizedBox(height: 16),
              Text(
                state.operationalEnabled
                    ? 'Requisitos operativos vigentes según el servidor.'
                    : 'Acceso limitado a incorporación. Operación bloqueada.',
              ),
              if (state.suspended)
                const Text('Suspensión operativa registrada.'),
              Text(
                'Documentación personal: ${_documentStatus(state.personalDocumentsStatus)}',
              ),
              if (state.personalValidUntil != null)
                Text('Vigencia personal: ${_peru(state.personalValidUntil!)}'),
              for (final document in state.personalDocumentStates)
                Text(
                  '${document.requirementId} ? ${document.result}${document.observation == null ? "" : " ? ${document.observation}"}',
                ),
              for (final blocker in state.blockers)
                Text('• ${_blocker(blocker)}'),
              Text('Estado consultado: ${_peru(state.observedAt)} (Perú)'),
              if (state.serviceAttention.isNotEmpty) ...[
                const SizedBox(height: 16),
                const Text('Servicios que requieren atención ADMIN'),
                for (final attention in state.serviceAttention)
                  Text(
                    '${attention.assignmentId} · ${attention.kind == 'SAFE_HANDOVER_REQUIRED' || attention.kind == 'SAFE_HANDOVER' || attention.kind == 'IN_PROGRESS_HANDOVER' ? 'Requiere relevo seguro. No continúe la conducción; contacte al ADMIN.' : 'Pendiente de reasignación ADMIN; no se acepta sustitución automática.'}',
                  ),
              ],
              const SizedBox(height: 16),
              const Text('Periodos de unidad'),
              if (state.periods.isEmpty)
                const Text('Sin periodos autorizados.'),
              for (final period in state.periods)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(
                    'Unidad ${period.vehicleId} · ${period.kind == 'OWN' ? 'Propia' : 'Empresarial'}',
                  ),
                  subtitle: Text(
                    '${_peru(period.startsAt)} — '
                    '${period.endsAt == null ? 'sin fin registrado' : _peru(period.endsAt!)}\n'
                    '${period.status == 'REVOKED' ? 'Revocado' : 'Autorizado; vigencia según servidor'}',
                  ),
                ),
              const Text('Solicitudes de unidad'),
              for (final request in state.unitRequests)
                Text('${request.id} · ${_requestStatus(request.status)}'),
              if (state.hasPendingUnitRequest)
                const Text('Ya hay una solicitud pendiente; no se crea otra.'),
              const Divider(height: 32),
              Form(
                key: _unitForm,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text('Solicitar revisión de una unidad'),
                    DropdownButtonFormField<String>(
                      key: ValueKey('qualification-kind-$_kind'),
                      initialValue: _kind,
                      decoration: const InputDecoration(
                        labelText: 'Tipo de vínculo solicitado',
                      ),
                      items: const [
                        DropdownMenuItem(
                          value: 'OWN',
                          child: Text('Unidad propia'),
                        ),
                        DropdownMenuItem(
                          value: 'COMPANY',
                          child: Text('Unidad empresarial'),
                        ),
                      ],
                      onChanged: editable && !state.hasPendingUnitRequest
                          ? (value) => setState(() => _kind = value!)
                          : null,
                    ),
                    TextFormField(
                      key: const Key('qualification-unit-id'),
                      controller: _vehicle,
                      enabled: editable && !state.hasPendingUnitRequest,
                      decoration: const InputDecoration(
                        labelText:
                            'Código de unidad existente (si corresponde)',
                      ),
                      validator: (value) {
                        final text = (value ?? '').trim();
                        if (text.isEmpty && _kind == 'OWN') return null;
                        return RegExp(
                              r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
                            ).hasMatch(text)
                            ? null
                            : 'Ingrese el código de la unidad.';
                      },
                    ),
                    TextFormField(
                      key: const Key('qualification-unit-reference'),
                      controller: _reference,
                      enabled: editable && !state.hasPendingUnitRequest,
                      maxLength: 160,
                      decoration: const InputDecoration(
                        labelText: 'Referencia sin datos personales',
                        helperText: 'Sin nombres, correos, DNI ni documentos originales.',
                      ),
                      validator: (value) => (value ?? '').trim().isEmpty
                          ? 'Ingrese una referencia.'
                          : null,
                    ),
                    FilledButton(
                      key: const Key('qualification-unit-submit'),
                      onPressed: editable && !state.hasPendingUnitRequest
                          ? _requestUnit
                          : null,
                      child: const Text('Solicitar revisión de unidad'),
                    ),
                  ],
                ),
              ),
              const Divider(height: 32),
              const Text('Presentar renovación o subsanación personal'),
              const Text(
                'Solo datos estructurados. La información se introduce '
                'de forma oculta y no se vuelve a mostrar. El ADMIN revisará '
                'el original por el procedimiento acordado; no se admiten archivos.',
              ),
              Form(
                key: _documentForm,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    DropdownButtonFormField<String>(
                      key: ValueKey(
                        'qualification-document-${_requirement?.id}',
                      ),
                      initialValue: _requirement?.id,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: 'Requisito del catálogo aprobado',
                      ),
                      items: [
                        for (final requirement in _catalog)
                          DropdownMenuItem(
                            value: requirement.id,
                            child: Text(
                              '${requirement.id}: ${requirement.title}',
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: editable ? _selectDocument : null,
                    ),
                    if (_requirement != null) ...[
                      for (final field in _requirement!.fieldKeys)
                        TextFormField(
                          key: Key('qualification-document-$field'),
                          controller: _fields[field],
                          enabled: editable,
                          obscureText: true,
                          enableSuggestions: false,
                          autocorrect: false,
                          maxLength: 200,
                          decoration: InputDecoration(
                            labelText: _fieldLabel(field),
                          ),
                        ),
                      DropdownButtonFormField<String>(
                        key: ValueKey('qualification-expiry-$_expiry'),
                        initialValue: _expiry,
                        decoration: const InputDecoration(
                          labelText: 'Vigencia según documento',
                        ),
                        items: const [
                          DropdownMenuItem(
                            value: 'UNKNOWN',
                            child: Text('Por contrastar'),
                          ),
                          DropdownMenuItem(
                            value: 'DATED',
                            child: Text('Con vencimiento'),
                          ),
                          DropdownMenuItem(
                            value: 'NO_EXPIRY',
                            child: Text('Sin vencimiento documentado'),
                          ),
                        ],
                        onChanged: editable
                            ? (value) => setState(() => _expiry = value!)
                            : null,
                      ),
                      if (_expiry == 'DATED')
                        TextFormField(
                          key: const Key('qualification-valid-until'),
                          controller: _date,
                          enabled: editable,
                          decoration: const InputDecoration(
                            labelText: 'Vence el (AAAA-MM-DD)',
                          ),
                          validator: (value) {
                            final date = (value ?? '').trim();
                            final parsed = DateTime.tryParse(date);
                            return RegExp(r'^\d{4}-\d{2}-\d{2}$')
                                        .hasMatch(date) &&
                                    parsed != null &&
                                    parsed.toIso8601String().startsWith(date)
                                ? null
                                : 'Ingrese una fecha real AAAA-MM-DD.';
                          },
                        ),
                      FilledButton(
                        key: const Key('qualification-document-submit'),
                        onPressed: editable ? _presentDocument : null,
                        child: const Text('Enviar para revisión ADMIN'),
                      ),
                    ],
                  ],
                ),
              ),
            ],
            const SizedBox(height: 16),
            OutlinedButton(
              key: const Key('qualification-refresh'),
              onPressed: _busy ? null : _load,
              child: const Text('Consultar estado actual'),
            ),
            if (state?.operationalEnabled == true)
              FilledButton(
                key: const Key('qualification-refresh-session'),
                onPressed: _busy ? null : _refreshSession,
                child: const Text('Comprobar acceso operativo'),
              ),
            if (_busy) const Center(child: CircularProgressIndicator()),
          ],
        ),
      ),
    );
  }

  String _peru(DateTime value) {
    final peru = value.toUtc().subtract(const Duration(hours: 5));
    return '${peru.day.toString().padLeft(2, '0')}/${peru.month.toString().padLeft(2, '0')}/${peru.year} '
        '${peru.hour.toString().padLeft(2, '0')}:${peru.minute.toString().padLeft(2, '0')}';
  }

  String _exclusivePeruEnd(String value) {
    final parsed = DateTime.parse(value);
    final next = DateTime.utc(
      parsed.year,
      parsed.month,
      parsed.day,
    ).add(const Duration(days: 1));
    return '${next.toIso8601String().substring(0, 10)}T00:00:00-05:00';
  }

  String _requestStatus(String value) => switch (value) {
    'PENDING_REVIEW' => 'Pendiente de revisión',
    'APPROVED' => 'Solicitud aprobada; periodo y habilitación separados',
    'REJECTED' => 'Solicitud rechazada',
    _ => 'Estado desconocido',
  };
  String _documentStatus(String value) => switch (value) {
    'CONFORME' || 'CURRENT' || 'VALID' => 'Revisión vigente',
    'EXPIRED' => 'Vencida',
    'PENDING' || 'PENDING_REVIEW' => 'Pendiente de revisión',
    _ => 'Requiere comprobación ADMIN',
  };
  String _blocker(String value) =>
      const {
        'DRIVER_INACTIVE': 'Habilitación del conductor pendiente.',
        'SUSPENDED': 'Suspensión operativa vigente.',
        'PERSONAL_DOCUMENTS_EXPIRED': 'Renovación personal requerida.',
        'PERSONAL_DOCUMENTS_PENDING': 'Documentación personal pendiente.',
        'UNIT_PERIOD_REQUIRED': 'Falta periodo de unidad vigente.',
        'UNIT_REVIEW_REQUIRED': 'Revisión de unidad pendiente.',
        'COMPANY_REVIEW_REQUIRED':
            'Revisión del servicio de empresa pendiente.',
      }[value] ??
      'Hay un requisito pendiente de comprobación ADMIN.';
  String _fieldLabel(String field) =>
      const {
        'documentType': 'Tipo de documento',
        'documentNumber': 'Número de documento',
        'fullName': 'Nombre según documento',
        'issuer': 'Emisor',
        'licenseNumber': 'Número de licencia',
        'licenseClass': 'Clase de licencia',
        'licenseCategory': 'Categoría de licencia',
        'restrictions': 'Restricciones',
        'titleNumber': 'Referencia de habilitación personal',
        'authority': 'Autoridad',
        'serviceScope': 'Ámbito del servicio',
        'operatorReference': 'Referencia del operador',
        'requirementName': 'Requisito aplicable',
        'minimumReference': 'Referencia mínima',
        'eligibilityResult': 'Resultado documentado',
        'reviewDate': 'Fecha de comprobación',
      }[field] ??
      field;
}
