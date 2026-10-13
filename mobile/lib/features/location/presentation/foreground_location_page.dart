import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../profile/data/mobile_driver_repository.dart';
import '../../profile/domain/mobile_driver_models.dart';
import '../domain/mobile_foreground_location.dart';

class ForegroundLocationPage extends StatefulWidget {
  const ForegroundLocationPage({
    required this.driverGateway,
    required this.deviceLocationGateway,
    super.key,
  });

  final MobileDriverGateway driverGateway;
  final MobileDeviceLocationGateway deviceLocationGateway;

  @override
  State<ForegroundLocationPage> createState() => _ForegroundLocationPageState();
}

class _ForegroundLocationPageState extends State<ForegroundLocationPage> {
  MobileDriverProfile? _profile;
  Object? _error;
  bool _loading = true;
  bool _working = false;
  bool _sentInCurrentSession = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final profile = await widget.driverGateway.profile();
      if (mounted) {
        setState(() => _profile = profile);
      }
    } catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _shareCurrentLocation() async {
    final profile = _profile;
    if (profile == null || _working) {
      return;
    }
    if (!_stateAllowsLocation(profile.availabilityStatus)) {
      _showMessage(
        'La ubicación vigente solo puede actualizarse cuando esté Disponible o En servicio.',
      );
      return;
    }

    final confirmed = await _confirm(
      title: 'Compartir ubicación actual',
      message: 'Se solicitará el permiso del dispositivo solo para obtener un punto mientras esta pantalla está abierta. No se realizará seguimiento continuo.',
      confirmLabel: 'Continuar',
      key: const Key('location-consent-confirm'),
    );
    if (confirmed != true || !mounted) {
      return;
    }

    setState(() => _working = true);
    try {
      if (!await _ensureForegroundPermission()) {
        return;
      }
      final currentProfile = _profile;
      if (currentProfile == null) {
        return;
      }

      var updatedProfile = currentProfile;
      if (!updatedProfile.locationConsent) {
        updatedProfile = await widget.driverGateway.changeLocationConsent(true);
      }
      final point = await widget.deviceLocationGateway.currentPosition();
      await widget.driverGateway.upsertCurrentLocation(point);

      if (!mounted) {
        return;
      }
      setState(() {
        _profile = updatedProfile;
        _sentInCurrentSession = true;
      });
      _showMessage('Ubicación actualizada para esta sesión.');
    } catch (error) {
      if (mounted) {
        _showMessage(_messageFor(error));
      }
    } finally {
      if (mounted) {
        setState(() => _working = false);
      }
    }
  }

  Future<bool> _ensureForegroundPermission() async {
    if (!await widget.deviceLocationGateway.isLocationServiceEnabled()) {
      await _offerSettings(
        title: 'Active la ubicación del dispositivo',
        message: 'Ruta Fija necesita que el servicio de ubicación esté activo para obtener el punto que usted solicitó.',
        actionLabel: 'Abrir ajustes de ubicación',
        openSettings: widget.deviceLocationGateway.openLocationSettings,
      );
      return false;
    }

    var permission = await widget.deviceLocationGateway.checkPermission();
    if (permission == MobileDeviceLocationPermission.denied) {
      permission = await widget.deviceLocationGateway.requestPermission();
    }
    if (permission.grantsForeground) {
      return true;
    }
    if (permission == MobileDeviceLocationPermission.deniedForever) {
      await _offerSettings(
        title: 'Permiso de ubicación bloqueado',
        message: 'Abra los ajustes de la aplicación y permita la ubicación mientras usa Ruta Fija para continuar.',
        actionLabel: 'Abrir ajustes de la aplicación',
        openSettings: widget.deviceLocationGateway.openAppSettings,
      );
    } else {
      _showMessage(
        'No se otorgó el permiso de ubicación. No se envió ningún punto.',
      );
    }
    return false;
  }

  Future<void> _clearCurrentLocation() async {
    if (_working || _profile?.locationConsent != true) {
      return;
    }
    final confirmed = await _confirm(
      title: 'Eliminar ubicación vigente',
      message: 'Se eliminará el único punto vigente del servidor. El consentimiento permanecerá activo para una actualización manual posterior.',
      confirmLabel: 'Eliminar ubicación',
      key: const Key('location-clear-confirm'),
    );
    if (confirmed != true || !mounted) {
      return;
    }

    setState(() => _working = true);
    try {
      await widget.driverGateway.clearCurrentLocation();
      if (mounted) {
        setState(() => _sentInCurrentSession = false);
        _showMessage('La ubicación vigente fue eliminada.');
      }
    } catch (error) {
      if (mounted) {
        _showMessage(_messageFor(error));
      }
    } finally {
      if (mounted) {
        setState(() => _working = false);
      }
    }
  }

  Future<void> _revokeConsent() async {
    if (_working || _profile?.locationConsent != true) {
      return;
    }
    final confirmed = await _confirm(
      title: 'Desactivar ubicación',
      message: 'Se retirará su consentimiento y el servidor eliminará la ubicación vigente. Para compartir otra ubicación deberá autorizarla nuevamente.',
      confirmLabel: 'Desactivar',
      key: const Key('location-revoke-confirm'),
    );
    if (confirmed != true || !mounted) {
      return;
    }

    setState(() => _working = true);
    try {
      final profile = await widget.driverGateway.changeLocationConsent(false);
      if (mounted) {
        setState(() {
          _profile = profile;
          _sentInCurrentSession = false;
        });
        _showMessage('Consentimiento retirado y ubicación vigente eliminada.');
      }
    } catch (error) {
      if (mounted) {
        _showMessage(_messageFor(error));
      }
    } finally {
      if (mounted) {
        setState(() => _working = false);
      }
    }
  }

  Future<bool?> _confirm({
    required String title,
    required String message,
    required String confirmLabel,
    required Key key,
  }) {
    return showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            key: key,
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(confirmLabel),
          ),
        ],
      ),
    );
  }

  Future<void> _offerSettings({
    required String title,
    required String message,
    required String actionLabel,
    required Future<bool> Function() openSettings,
  }) async {
    if (!mounted) {
      return;
    }
    final open = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Ahora no'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(actionLabel),
          ),
        ],
      ),
    );
    if (open == true) {
      await openSettings();
    }
  }

  void _showMessage(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String _messageFor(Object error) {
    if (error is MobileDeviceLocationFailure) {
      return error.message;
    }
    return mobileErrorMessage(error);
  }

  bool _stateAllowsLocation(MobileAvailabilityStatus status) {
    return status == MobileAvailabilityStatus.disponible ||
        status == MobileAvailabilityStatus.enServicio;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ubicación actual')),
      body: SafeArea(child: _buildBody(context)),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null || _profile == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(_messageFor(_error ?? StateError('Perfil no disponible.'))),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _load,
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ],
          ),
        ),
      );
    }

    final profile = _profile!;
    final canSend = _stateAllowsLocation(profile.availabilityStatus);
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            'Ubicación en primer plano',
            style: Theme.of(context).textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          const Text(
            'Solo se obtiene un punto cuando usted lo solicita con la aplicación abierta. No se realiza seguimiento continuo ni se muestran coordenadas.',
          ),
          const SizedBox(height: 20),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Privacidad y estado'),
                  const SizedBox(height: 8),
                  Chip(
                    avatar: const Icon(Icons.privacy_tip_outlined),
                    label: Text(
                      profile.locationConsent
                          ? 'Consentimiento activo'
                          : 'Consentimiento pendiente',
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Disponibilidad actual: ${profile.availabilityStatus.label}',
                  ),
                  if (_sentInCurrentSession) ...[
                    const SizedBox(height: 8),
                    const Text(
                      'Se actualizó un único punto durante esta sesión.',
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          if (!canSend)
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'La ubicación vigente solo se permite cuando el conductor está Disponible o En servicio.',
                ),
              ),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            key: const Key('location-share'),
            onPressed: _working || !canSend ? null : _shareCurrentLocation,
            icon: _working
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.my_location_outlined),
            label: Text(
              _working
                  ? 'Actualizando ubicación…'
                  : profile.locationConsent
                  ? 'Actualizar ubicación actual'
                  : 'Autorizar y actualizar ubicación',
            ),
          ),
          if (profile.locationConsent) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              key: const Key('location-clear'),
              onPressed: _working ? null : _clearCurrentLocation,
              icon: const Icon(Icons.delete_outline),
              label: const Text('Eliminar ubicación vigente'),
            ),
            const SizedBox(height: 8),
            TextButton.icon(
              key: const Key('location-revoke'),
              onPressed: _working ? null : _revokeConsent,
              icon: const Icon(Icons.location_off_outlined),
              label: const Text('Desactivar ubicación'),
            ),
          ],
        ],
      ),
    );
  }
}
