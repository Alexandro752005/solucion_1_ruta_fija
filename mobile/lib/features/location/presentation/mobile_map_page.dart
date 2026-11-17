import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/presentation/mobile_date_time.dart';
import '../domain/mobile_service_location_controller.dart';
import 'mobile_location_scope.dart';
import 'mobile_map_configuration.dart';

/// The same map is used by the personal tab and a confirmed service context.
/// Completion is delegated to AssignmentDetailPage's existing online flow.
class MobileMapPage extends StatefulWidget {
  const MobileMapPage({
    this.assignmentId,
    this.onComplete,
    this.tileProvider,
    super.key,
  });
  final String? assignmentId;
  final Future<bool> Function()? onComplete;

  /// An explicit provider supports isolated UI verification without public tiles.
  /// The production composition uses the private HTTP provider below.
  final TileProvider? tileProvider;

  @override
  State<MobileMapPage> createState() => _MobileMapPageState();
}

class _MobileMapPageState extends State<MobileMapPage>
    with WidgetsBindingObserver {
  final _map = MapController();
  MobileServiceLocationController? _controller;
  bool _tilesAccepted = false;
  bool _tileFailure = false;
  bool _working = false;
  bool _mapReady = false;
  bool _follow = true;
  Object? _error;
  Object? _lastPoint;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final next = MobileLocationScope.maybeOf(context)?.controller;
    if (identical(next, _controller)) return;
    _controller?.removeListener(_changed);
    _controller = next;
    next?.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) next?.setMapVisible(true);
    });
  }

  void _changed() {
    if (!mounted) return;
    final point = _controller?.mapPoint;
    if (!identical(point, _lastPoint)) {
      _lastPoint = point;
      if (point != null && _follow && _mapReady) {
        _map.move(LatLng(point.latitude, point.longitude), _map.camera.zoom);
      }
    }
    setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed && mounted) {
      // Removing the layer stops viewport requests; the app owner pauses GPS.
      setState(() => _mapReady = false);
    } else if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller?.removeListener(_changed);
    _controller?.setMapVisible(false);
    _map.dispose();
    super.dispose();
  }

  Future<void> _action(Future<void> Function() work) async {
    if (_working) return;
    setState(() {
      _working = true;
      _error = null;
    });
    try {
      await work();
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  void _showTiles() {
    try {
      MobileMapConfiguration.validateTemplate(
        MobileMapConfiguration.configuredTemplate,
      );
      setState(() {
        _tilesAccepted = true;
        _tileFailure = false;
        _error = null;
      });
    } catch (error) {
      setState(() => _error = error);
    }
  }

  Future<void> _attribution() async {
    try {
      if (!await launchUrl(
        Uri.parse('https://www.openstreetmap.org/copyright'),
        mode: LaunchMode.externalApplication,
      )) {
        throw StateError('No fue posible abrir la atribución del mapa.');
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = StateError(
            'Consulte la atribución en openstreetmap.org/copyright.',
          ),
        );
      }
    }
  }

  String _status(MobileServiceLocationController controller) {
    if (controller.hasActiveService) {
      return switch (controller.phase) {
        ServiceLocationPhase.sharing =>
          'Compartiendo ubicación durante el servicio',
        ServiceLocationPhase.noConsent => 'Servicio sin consentimiento de ubicación. Revise Gestionar ubicación.',
        ServiceLocationPhase.noPermission =>
          controller.mapPhase == MapLocationPhase.gpsDisabled
              ? 'Activa la ubicación del dispositivo. No se confirma el envío.'
              : 'Permite la ubicación mientras usas Ruta Fija. No se confirma el envío.',
        ServiceLocationPhase.noConnection =>
          'Sin conexión. No se guardan puntos para enviar después.',
        ServiceLocationPhase.paused =>
          'Captura detenida: Ruta Fija debe estar en primer plano.',
        _ => 'Comprobando ubicación del servicio. Aún no hay una nueva entrega confirmada.',
      };
    }
    return switch (controller.mapPhase) {
      MapLocationPhase.ready =>
        'Mapa personal. Tu ubicación no se comparte con la organización.',
      MapLocationPhase.noPermission =>
        'Permite la ubicación mientras usas Ruta Fija.',
      MapLocationPhase.gpsDisabled => 'Activa la ubicación del dispositivo.',
      MapLocationPhase.noConnection =>
        'Sin conexión. Reconectando; no hay modo offline.',
      MapLocationPhase.paused => 'Captura detenida en segundo plano.',
      MapLocationPhase.unavailable =>
        'No fue posible obtener una ubicación nueva. Revisa la señal GPS.',
      _ => 'Comprobando el estado del servicio y la ubicación del dispositivo.',
    };
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final scope = MobileLocationScope.maybeOf(context);
    final point = controller?.mapPoint;
    final foreground =
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    final online =
        controller?.mapPhase != MapLocationPhase.noConnection &&
        controller?.phase != ServiceLocationPhase.noConnection;
    final currentRoute = ModalRoute.isCurrentOf(context) ?? true;
    if (controller != null && controller.mapVisible != currentRoute) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) controller.setMapVisible(currentRoute);
      });
    }
    final stale =
        point != null &&
        controller!.clock().difference(point.capturedAt) >=
            const Duration(seconds: 120);
    final contextual = widget.assignmentId != null;
    final contextValid =
        contextual &&
        controller?.activeAssignmentId == widget.assignmentId &&
        controller!.hasActiveService;
    final theme = Theme.of(context);
    const touchTarget = WidgetStatePropertyAll(Size(48, 48));
    return Theme(
      data: theme.copyWith(
        filledButtonTheme: FilledButtonThemeData(
          style: (theme.filledButtonTheme.style ?? const ButtonStyle())
              .copyWith(minimumSize: touchTarget),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: (theme.outlinedButtonTheme.style ?? const ButtonStyle())
              .copyWith(minimumSize: touchTarget),
        ),
        textButtonTheme: TextButtonThemeData(
          style: (theme.textButtonTheme.style ?? const ButtonStyle()).copyWith(
            minimumSize: touchTarget,
          ),
        ),
      ),
      child: Scaffold(
        appBar: contextual
            ? AppBar(title: const Text('Mapa del servicio'))
            : null,
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      contextual ? 'Mapa del servicio' : 'Mi mapa',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Semantics(
                      liveRegion: true,
                      child: Card(
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Text(
                            controller == null
                                ? 'El mapa requiere una sesión de conductor válida.'
                                : _status(controller),
                            key: const Key('map-sharing-status'),
                          ),
                        ),
                      ),
                    ),
                    if (stale)
                      const Text(
                        'Ubicación desactualizada. El marcador no representa una captura actual.',
                        key: Key('map-stale'),
                      ),
                    if (point != null)
                      Text(
                        'Captura del dispositivo: ${mobileDateTimeLabel(point.capturedAt)} (hora local)',
                        key: const Key('map-capture-time'),
                      ),
                    if (controller?.hasActiveService == true) ...[
                      Text(
                        controller!.lastCaptureAt == null
                            ? 'Aún no hay una entrega GPS confirmada.'
                            : 'Última captura con entrega confirmada: ${mobileDateTimeLabel(controller.lastCaptureAt!)} (hora local)',
                        key: const Key('map-confirmed-time'),
                      ),
                      const Text(
                        'Los intentos de ubicación del servicio se realizan aproximadamente cada 30 segundos, solo con la app en primer plano y conexión.',
                      ),
                    ],
                    if (_error != null)
                      Text(
                        mobileErrorMessage(_error!),
                        key: const Key('map-action-error'),
                      ),
                    if (!_tilesAccepted)
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              const Text('Antes de mostrar el mapa'),
                              const SizedBox(height: 8),
                              const Text(
                                'OpenStreetMap recibirá tu dirección IP y la zona del mapa que consultes. Ruta Fija no envía tu nombre, placa ni sesión al proveedor. Las imágenes respetan su caché HTTP; no hay mapas offline ni descarga de zonas.',
                              ),
                              const SizedBox(height: 8),
                              FilledButton(
                                key: const Key('map-show-tiles'),
                                onPressed: controller == null
                                    ? null
                                    : _showTiles,
                                child: const Text('Mostrar mapa'),
                              ),
                              TextButton(
                                onPressed: () =>
                                    Navigator.of(context).maybePop(),
                                child: const Text('Ahora no'),
                              ),
                            ],
                          ),
                        ),
                      ),
                    if (_tilesAccepted &&
                        foreground &&
                        online &&
                        currentRoute &&
                        controller != null) ...[
                      SizedBox(
                        height: (constraints.maxHeight * .55).clamp(
                          260.0,
                          650.0,
                        ),
                        child: Stack(
                          children: [
                            FlutterMap(
                              mapController: _map,
                              options: MapOptions(
                                initialCenter: point == null
                                    ? const LatLng(-9.19, -75.0152)
                                    : LatLng(point.latitude, point.longitude),
                                initialZoom: point == null ? 5 : 16,
                                minZoom: 4,
                                maxZoom: 18,
                                onMapReady: () {
                                  _mapReady = true;
                                },
                                onPositionChanged: (_, hasGesture) {
                                  if (hasGesture) _follow = false;
                                },
                              ),
                              children: [
                                if (widget.tileProvider != null)
                                  TileLayer(
                                    urlTemplate: MobileMapConfiguration
                                        .configuredTemplate,
                                    tileProvider: widget.tileProvider,
                                    userAgentPackageName:
                                        'pe.rutafija.conductor',
                                    panBuffer: 0,
                                    keepBuffer: 0,
                                    tileDisplay:
                                        const TileDisplay.instantaneous(),
                                  )
                                else
                                  _PrivateMapTiles(
                                    onFailure: () {
                                      if (!mounted) return;
                                      WidgetsBinding.instance
                                          .addPostFrameCallback((_) {
                                            if (mounted) {
                                              setState(
                                                () => _tileFailure = true,
                                              );
                                            }
                                          });
                                    },
                                  ),
                                if (point != null)
                                  MarkerLayer(
                                    markers: [
                                      Marker(
                                        point: LatLng(
                                          point.latitude,
                                          point.longitude,
                                        ),
                                        width: 48,
                                        height: 48,
                                        child: Semantics(
                                          label: stale
                                              ? 'Tu ubicación desactualizada'
                                              : 'Tu ubicación en el mapa',
                                          child: DecoratedBox(
                                            decoration: BoxDecoration(
                                              color: stale
                                                  ? Colors.grey.shade700
                                                  : const Color(0xFF159447),
                                              shape: BoxShape.circle,
                                              border: Border.all(
                                                color: Colors.white,
                                                width: 3,
                                              ),
                                            ),
                                            child: const Icon(
                                              Icons.directions_car,
                                              color: Colors.white,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                              ],
                            ),
                            Positioned(
                              top: 8,
                              right: 8,
                              child: Column(
                                children: [
                                  _MapControl(
                                    label: 'Acercar mapa',
                                    icon: Icons.add,
                                    onTap: () {
                                      _follow = false;
                                      if (_mapReady) {
                                        _map.move(
                                          _map.camera.center,
                                          (_map.camera.zoom + 1).clamp(4, 18),
                                        );
                                      }
                                    },
                                  ),
                                  _MapControl(
                                    label: 'Alejar mapa',
                                    icon: Icons.remove,
                                    onTap: () {
                                      _follow = false;
                                      if (_mapReady) {
                                        _map.move(
                                          _map.camera.center,
                                          (_map.camera.zoom - 1).clamp(4, 18),
                                        );
                                      }
                                    },
                                  ),
                                  _MapControl(
                                    label: 'Centrar en mi ubicación',
                                    icon: Icons.my_location,
                                    onTap: point == null
                                        ? null
                                        : () {
                                            _follow = true;
                                            if (_mapReady) {
                                              _map.move(
                                                LatLng(
                                                  point.latitude,
                                                  point.longitude,
                                                ),
                                                _map.camera.zoom < 12
                                                    ? 16
                                                    : _map.camera.zoom,
                                              );
                                            }
                                          },
                                  ),
                                ],
                              ),
                            ),
                            Positioned(
                              bottom: 0,
                              left: 0,
                              right: 0,
                              child: Material(
                                color: const Color(0xF2FFFDF8),
                                child: TextButton(
                                  key: const Key('map-attribution'),
                                  style: TextButton.styleFrom(
                                    minimumSize: const Size(48, 48),
                                  ),
                                  onPressed: _attribution,
                                  child: const Text(
                                    '© OpenStreetMap contributors',
                                  ),
                                ),
                              ),
                            ),
                            if (point == null)
                              const Positioned(
                                bottom: 62,
                                left: 8,
                                child: Card(
                                  child: Padding(
                                    padding: EdgeInsets.all(8),
                                    child: Text(
                                      'Sin posición actual confirmada',
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                      if (_tileFailure)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              children: [
                                const Text(
                                  'No se pudo cargar el mapa. Revisa la conexión; no se cambiará a un servicio de pago.',
                                ),
                                TextButton(
                                  onPressed: () => setState(() {
                                    _tilesAccepted = false;
                                    _mapReady = false;
                                  }),
                                  child: const Text('Reintentar mapa'),
                                ),
                              ],
                            ),
                          ),
                        ),
                    ],
                    if (_tilesAccepted && !online)
                      const Text(
                        'Sin conexión con Ruta Fija. El mapa se reanudará tras comprobar el estado; no hay modo offline.',
                      ),
                    if (controller != null)
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          OutlinedButton.icon(
                            key: const Key('map-refresh'),
                            onPressed: _working
                                ? null
                                : () => _action(controller.tick),
                            icon: const Icon(Icons.refresh),
                            label: const Text('Actualizar ubicación'),
                          ),
                          if (controller.mapPhase ==
                              MapLocationPhase.noPermission)
                            FilledButton(
                              onPressed: _working
                                  ? null
                                  : () => _action(
                                      controller.requestMapPermission,
                                    ),
                              child: const Text('Permitir ubicación'),
                            ),
                          if (controller.mapPhase ==
                                  MapLocationPhase.noPermission ||
                              controller.mapPhase ==
                                  MapLocationPhase.gpsDisabled)
                            TextButton(
                              onPressed: () => _action(() async {
                                if (controller.mapPhase ==
                                    MapLocationPhase.gpsDisabled) {
                                  await scope!.device.openLocationSettings();
                                } else {
                                  await scope!.device.openAppSettings();
                                }
                              }),
                              child: const Text('Abrir ajustes'),
                            ),
                          TextButton(
                            onPressed: () =>
                                Navigator.of(context).pushNamed('/location'),
                            child: const Text('Gestionar consentimiento'),
                          ),
                        ],
                      ),
                    if (contextual && !contextValid)
                      const Text(
                        'El servicio ya no está confirmado en ejecución. Vuelve a la asignación para consultar su estado.',
                      ),
                    if (contextValid && widget.onComplete != null)
                      FilledButton.icon(
                        key: const Key('map-complete-service'),
                        onPressed: _working
                            ? null
                            : () => _action(() async {
                                final completed = await widget.onComplete!();
                                if (completed && context.mounted) {
                                  Navigator.of(context).pop(true);
                                }
                              }),
                        icon: const Icon(Icons.flag_outlined),
                        label: const Text('Ruta finalizada'),
                      ),
                    if (contextual)
                      TextButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text('Volver a la asignación'),
                      ),
                    const SizedBox(height: 12),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MapControl extends StatelessWidget {
  const _MapControl({
    required this.label,
    required this.icon,
    required this.onTap,
  });
  final String label;
  final IconData icon;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      elevation: 2,
      child: IconButton(
        tooltip: label,
        onPressed: onTap,
        icon: Icon(icon),
        constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
      ),
    ),
  );
}

/// Separate unauthenticated HTTP boundary: never copies backend headers or logs
/// the tile URL. The cache still receives standard successful HTTP responses.
final class _MapTileClient extends http.BaseClient {
  _MapTileClient({required this.onFailure});
  final VoidCallback onFailure;
  final http.Client _inner = http.Client();
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    try {
      final response = await _inner
          .send(request)
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200 && response.statusCode != 304) onFailure();
      return response;
    } on http.RequestAbortedException {
      // Panning away or hiding a viewport deliberately cancels obsolete tiles.
      rethrow;
    } catch (_) {
      onFailure();
      throw const _MapTileFailure();
    }
  }

  @override
  void close() {
    _inner.close();
    super.close();
  }
}

final class _MapTileFailure implements Exception {
  const _MapTileFailure();
  @override
  String toString() => 'Cartografía temporalmente no disponible';
}

class _PrivateMapTiles extends StatefulWidget {
  const _PrivateMapTiles({required this.onFailure});
  final VoidCallback onFailure;
  @override
  State<_PrivateMapTiles> createState() => _PrivateMapTilesState();
}

class _PrivateMapTilesState extends State<_PrivateMapTiles> {
  late final _PrivateTileProvider _provider;
  @override
  void initState() {
    super.initState();
    _provider = _PrivateTileProvider(
      _MapTileClient(onFailure: () => widget.onFailure()),
    );
  }

  @override
  Widget build(BuildContext context) => TileLayer(
    urlTemplate: MobileMapConfiguration.configuredTemplate,
    tileProvider: _provider,
    userAgentPackageName: 'pe.rutafija.conductor',
    panBuffer: 0,
    keepBuffer: 0,
    maxNativeZoom: 19,
  );
}

/// The cache is deliberately left at flutter_map's default built-in HTTP cache.
/// This provider owns its unauthenticated client and closes it with the layer.
final class _PrivateTileProvider extends NetworkTileProvider {
  _PrivateTileProvider(this.client)
    : super(httpClient: client, silenceExceptions: true);
  final http.Client client;
  @override
  Future<void> dispose() async {
    client.close();
    await super.dispose();
  }
}
