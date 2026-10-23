import 'dart:async';

import 'package:flutter/material.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/network/mobile_api_exception.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_service_gps_repository.dart';

class ServiceGpsAccessPanel extends StatefulWidget {
  const ServiceGpsAccessPanel({
    required this.serviceId,
    required this.gateway,
    super.key,
  });
  final String serviceId;
  final MobileServiceGpsGateway gateway;
  @override
  State<ServiceGpsAccessPanel> createState() => _ServiceGpsAccessPanelState();
}

class _ServiceGpsAccessPanelState extends State<ServiceGpsAccessPanel>
    with WidgetsBindingObserver {
  List<MobileGpsRequest> _requests = [];
  bool _busy = false, _uncertain = false;
  String? _pendingKey, _error;
  int _offset = 0;
  MobileGpsDelivery? _delivery;
  MobileGpsRequest? _deliveryRequest;
  int _readSequence = 0;
  Timer? _expiry;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _expiry?.cancel();
    _delivery = null;
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      _expiry?.cancel();
      if (mounted) setState(() => _delivery = null);
    }
  }

  Future<void> _load() async {
    final sequence = ++_readSequence;
    try {
      final rows = await widget.gateway.gpsRequests(
        widget.serviceId,
        offset: _offset,
      );
      if (!mounted || sequence != _readSequence) return;
      setState(() {
        _requests = rows;
        _error = null;
        if (_pendingKey != null &&
            rows.any((r) => r.requestKey == _pendingKey)) {
          _uncertain = false;
          _pendingKey = null;
        }
      });
    } catch (e) {
      if (mounted) setState(() => _error = mobileErrorMessage(e));
    }
  }

  Future<void> _request() async {
    if (_busy || _uncertain) return;
    setState(() {
      _busy = true;
      _error = null;
      _offset = 0;
    });
    _pendingKey = createCorrelationId();
    try {
      await widget.gateway.requestGps(widget.serviceId, _pendingKey!);
      _pendingKey = null;
      _offset = 0;
      await _load();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = mobileErrorMessage(e);
          _uncertain =
              e is! MobileApiException ||
              ![400, 401, 403, 404, 409, 422, 429].contains(e.status);
          if (!_uncertain) _pendingKey = null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _deliver(MobileGpsRequest r, {int offset = 0}) async {
    if (_busy) return;
    String? password = await showDialog<String>(
      context: context,
      builder: (_) => const _GpsReauthenticationDialog(),
    );
    if (password == null ||
        password.isEmpty ||
        !mounted ||
        WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed) {
      return;
    }
    setState(() {
      _busy = true;
      _delivery = null;
      _error = null;
    });
    try {
      final result = await widget.gateway.deliverGps(
        r.id,
        password,
        offset: offset,
      );
      if (!mounted ||
          WidgetsBinding.instance.lifecycleState != AppLifecycleState.resumed ||
          !DateTime.now().toUtc().isBefore(result.expiresAt)) {
        return;
      }
      _expiry?.cancel();
      _expiry = Timer(result.expiresAt.difference(DateTime.now().toUtc()), () {
        if (mounted) setState(() => _delivery = null);
      });
      setState(() {
        _delivery = result;
        _deliveryRequest = r;
      });
    } catch (e) {
      if (mounted) setState(() => _error = mobileErrorMessage(e));
    } finally {
      password = null;
      if (mounted) setState(() => _busy = false);
    }
  }

  String _label(String s) => switch (s) {
    'PENDING' => 'Pendiente de revisión',
    'OVERDUE' => 'Revisión fuera del plazo operativo',
    'APPROVED' => 'Acceso autorizado',
    'NO_DATA' => 'Sin puntos disponibles',
    'DENIED' => 'Solicitud no autorizada',
    'EXPIRED' => 'Autorización vencida',
    _ => s,
  };
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Solicitud de mis puntos GPS'),
          const Text(
            'La vista ordinaria muestra solo el resumen del servicio. Los puntos requieren revisión ADMIN y verificación de su cuenta; no se conservan más de siete días y solicitar acceso no amplía ese plazo.',
          ),
          if (_error != null) Text(_error!, key: const Key('gps-access-error')),
          if (_uncertain)
            const Text(
              'Solicitud sin confirmación. Consulte su estado antes de solicitar otra; no se reenvía automáticamente.',
            ),
          TextButton(
            onPressed: _busy ? null : _load,
            child: const Text('Consultar solicitudes'),
          ),
          FilledButton(
            key: const Key('gps-own-request'),
            onPressed: _busy || _uncertain ? null : _request,
            child: const Text('Solicitar mis puntos de este servicio'),
          ),
          for (final r in _requests)
            ListTile(
              title: Text(_label(r.status)),
              subtitle: Text(
                '${r.fromAt.toLocal()} → ${r.toAt.toLocal()}\nRevisión prevista hasta ${r.reviewDueAt.toLocal()}',
              ),
              trailing:
                  r.status == 'APPROVED' &&
                      r.grantExpiresAt != null &&
                      DateTime.now().toUtc().isBefore(r.grantExpiresAt!)
                  ? TextButton(
                      onPressed: _busy ? null : () => _deliver(r),
                      child: const Text('Acceso restringido'),
                    )
                  : null,
            ),
          Row(
            children: [
              TextButton(
                onPressed: _busy || _offset == 0
                    ? null
                    : () {
                        _offset -= 50;
                        unawaited(_load());
                      },
                child: const Text('Anteriores'),
              ),
              TextButton(
                onPressed: _busy || _requests.length < 50
                    ? null
                    : () {
                        _offset += 50;
                        unawaited(_load());
                      },
                child: const Text('Siguientes'),
              ),
            ],
          ),
          if (_delivery case final data?) ...[
            const Text(
              'Mis puntos — consulta restringida temporal, sin archivo guardado',
            ),
            TextButton(
              onPressed: () {
                _expiry?.cancel();
                setState(() => _delivery = null);
              },
              child: const Text('Ocultar puntos'),
            ),
            for (final point in data.items)
              Text(
                '${point.capturedAt.toLocal()} · ${point.latitude.toStringAsFixed(6)}, ${point.longitude.toStringAsFixed(6)} · precisión ${point.accuracy}m',
              ),
            if (data.nextOffset != null)
              TextButton(
                onPressed: _busy
                    ? null
                    : () =>
                          _deliver(_deliveryRequest!, offset: data.nextOffset!),
                child: const Text(
                  'Siguientes puntos: volver a verificar cuenta',
                ),
              ),
          ],
        ],
      ),
    ),
  );
}

class _GpsReauthenticationDialog extends StatefulWidget {
  const _GpsReauthenticationDialog();
  @override
  State<_GpsReauthenticationDialog> createState() =>
      _GpsReauthenticationDialogState();
}

class _GpsReauthenticationDialogState extends State<_GpsReauthenticationDialog>
    with WidgetsBindingObserver {
  final input = TextEditingController();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    input.clear();
    input.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) input.clear();
  }

  void submit() {
    if (input.text.isNotEmpty) Navigator.pop(context, input.text);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Acceso restringido a mis puntos'),
    content: TextField(
      controller: input,
      obscureText: true,
      autocorrect: false,
      enableSuggestions: false,
      maxLength: 200,
      onSubmitted: (_) => submit(),
      decoration: const InputDecoration(labelText: 'Contraseña de su cuenta'),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: submit,
        child: const Text('Verificar y consultar'),
      ),
    ],
  );
}
