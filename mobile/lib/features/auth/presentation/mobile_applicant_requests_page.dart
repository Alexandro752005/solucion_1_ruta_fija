import 'dart:async';

import 'package:flutter/material.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_applicant_access_repository.dart';
import '../data/mobile_applicant_documentary_repository.dart';
import '../domain/mobile_applicant_access_models.dart';
import 'mobile_applicant_dossier.dart';

class MobileApplicantRequestsPage extends StatefulWidget {
  const MobileApplicantRequestsPage({
    required this.gateway,
    this.documentaryGateway,
    super.key,
  });

  final MobileApplicantAccessGateway gateway;
  final MobileApplicantDocumentaryGateway? documentaryGateway;

  @override
  State<MobileApplicantRequestsPage> createState() =>
      _MobileApplicantRequestsPageState();
}

class _MobileApplicantRequestsPageState
    extends State<MobileApplicantRequestsPage> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  MobileApplicantAccess? _access;
  List<MobileApplicantRequest>? _requests;
  String? _selectedRequestId;
  Timer? _expiry;
  bool _busy = false;
  String? _error;
  String? _notice;

  @override
  void dispose() {
    _clearAccess();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  // Clear local secrets before the best-effort revocation; never retry logout.
  void _clearAccess() {
    final token = _access?.token;
    _access = null;
    _requests = null;
    _selectedRequestId = null;
    _expiry?.cancel();
    _expiry = null;
    _password.clear();
    _email.clear();
    if (token != null) unawaited(_revoke(token));
  }

  Future<void> _revoke(String token) async {
    try {
      await widget.gateway.logout(token);
    } catch (_) {
      // No credentials survive locally; the server token still has its TTL.
    }
  }

  void _expire() {
    _clearAccess();
    if (mounted) {
      setState(() {
        _busy = false;
        _error = null;
        _notice = 'El acceso de consulta venció. Ingrese nuevamente.';
      });
    }
  }

  Future<void> _login() async {
    if (_busy || !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      final access = await widget.gateway.login(
        email: _email.text,
        password: _password.text,
      );
      if (!mounted) {
        unawaited(_revoke(access.token));
        return;
      }
      _password.clear();
      _email.clear();
      final remaining = access.expiresAt.difference(DateTime.now());
      if (remaining <= Duration.zero) {
        unawaited(_revoke(access.token));
        _expire();
        return;
      }
      setState(() => _access = access);
      _expiry = Timer(remaining, _expire);
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) {
        _password.clear();
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _load() async {
    final access = _access;
    if (access == null) return;
    if (!access.expiresAt.isAfter(DateTime.now())) {
      _expire();
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final requests = await widget.gateway.requests(access.token);
      if (mounted && identical(_access, access)) {
        setState(() => _requests = requests);
      }
    } catch (error) {
      if (!mounted || !identical(_access, access)) return;
      if (error is MobileApiException && error.status == 401) {
        _expire();
      } else {
        setState(() => _error = mobileErrorMessage(error));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _logout() {
    _clearAccess();
    setState(() {
      _error = null;
      _notice = 'Consulta cerrada.';
    });
  }

  @override
  Widget build(BuildContext context) {
    final access = _access;
    final gateway = widget.gateway;
    final documentaryGateway =
        widget.documentaryGateway ??
        (gateway is MobileApplicantDocumentaryGateway
            ? gateway as MobileApplicantDocumentaryGateway
            : null);
    return Scaffold(
      appBar: AppBar(title: const Text('Consultar mi solicitud')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'Consulte el estado de incorporación. Este acceso no permite operar como conductor.',
                  ),
                  if (_notice != null) ...[
                    const SizedBox(height: 16),
                    Semantics(liveRegion: true, child: Text(_notice!)),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Semantics(liveRegion: true, child: Text(_error!)),
                  ],
                  const SizedBox(height: 24),
                  if (access == null)
                    Form(
                      key: _formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          TextFormField(
                            key: const Key('applicant-email'),
                            controller: _email,
                            enabled: !_busy,
                            keyboardType: TextInputType.emailAddress,
                            textInputAction: TextInputAction.next,
                            autocorrect: false,
                            enableSuggestions: false,
                            decoration: const InputDecoration(
                              labelText: 'Correo de incorporación',
                            ),
                            validator: (value) =>
                                (value ?? '').trim().contains('@')
                                ? null
                                : 'Ingrese su correo.',
                          ),
                          const SizedBox(height: 16),
                          TextFormField(
                            key: const Key('applicant-password'),
                            controller: _password,
                            enabled: !_busy,
                            obscureText: true,
                            enableSuggestions: false,
                            autocorrect: false,
                            textInputAction: TextInputAction.done,
                            onFieldSubmitted: (_) => _login(),
                            decoration: const InputDecoration(
                              labelText: 'Contraseña preliminar',
                            ),
                            validator: (value) => (value ?? '').isEmpty
                                ? 'Ingrese su contraseña preliminar.'
                                : null,
                          ),
                          const SizedBox(height: 24),
                          FilledButton(
                            key: const Key('applicant-login'),
                            onPressed: _busy ? null : _login,
                            child: const Text('Consultar estado'),
                          ),
                        ],
                      ),
                    )
                  else ...[
                    if (_requests != null && _requests!.isEmpty)
                      const Text('No hay solicitudes para consultar.'),
                    if (_selectedRequestId != null &&
                        documentaryGateway != null)
                      MobileApplicantDossierView(
                        key: ValueKey(_selectedRequestId),
                        gateway: documentaryGateway,
                        token: access.token,
                        requestId: _selectedRequestId!,
                        onExpired: _expire,
                        onReturnToRequests: () {
                          setState(() => _selectedRequestId = null);
                          _load();
                        },
                      ),
                    if (_selectedRequestId == null)
                      for (final request
                          in _requests ?? <MobileApplicantRequest>[])
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Solicitud: ${request.requestId}'),
                                Text('Organización: ${request.organizationId}'),
                                Text(_status(request.status)),
                                Text(
                                  'Rondas de revisión: ${request.reviewRounds}',
                                ),
                                Text('Enviada: ${_date(request.submittedAt)}'),
                                if (documentaryGateway != null)
                                  TextButton(
                                    key: Key(
                                      'applicant-documentary-${request.requestId}',
                                    ),
                                    onPressed: _busy
                                        ? null
                                        : () => setState(
                                            () => _selectedRequestId =
                                                request.requestId,
                                          ),
                                    child: const Text(
                                      'Consultar expediente y subsanar',
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                    if (_selectedRequestId == null)
                      OutlinedButton(
                        key: const Key('applicant-refresh'),
                        onPressed: _busy ? null : _load,
                        child: const Text('Actualizar estado'),
                      ),
                    TextButton(
                      key: const Key('applicant-logout'),
                      onPressed: _logout,
                      child: const Text('Cerrar consulta'),
                    ),
                  ],
                  if (_busy)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: CircularProgressIndicator()),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _status(String status) => switch (status) {
    'PENDING_REVIEW' => 'Pendiente de revisión',
    'RETURNED' => 'Devuelta para subsanación',
    'APPROVED' =>
      'Aprobación personal registrada; no acredita acceso operativo.',
    'REJECTED' => 'Rechazada',
    _ => 'Estado no disponible',
  };

  String _date(DateTime value) {
    final local = value.toLocal();
    return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year}';
  }
}
