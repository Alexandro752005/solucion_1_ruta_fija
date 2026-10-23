import 'package:flutter/material.dart';

import '../domain/mobile_service_location_controller.dart';

class ServiceLocationStatus extends StatelessWidget {
  const ServiceLocationStatus({
    required this.controller,
    required this.child,
    this.onOpenService,
    super.key,
  });
  final MobileServiceLocationController controller;
  final Widget child;
  final VoidCallback? onOpenService;
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) {
      if (!controller.hasActiveService) return child;
      final age = controller.captureAge;
      final state = switch (controller.phase) {
        ServiceLocationPhase.sharing =>
          age != null && age > const Duration(seconds: 120)
              ? 'GPS sin captura reciente'
              : 'GPS del servicio activo',
        ServiceLocationPhase.noConsent =>
          'GPS sin consentimiento: revise Ubicación',
        ServiceLocationPhase.noPermission =>
          'GPS sin permiso o ubicación apagada: revise los ajustes',
        ServiceLocationPhase.noConnection =>
          'GPS sin conexión; no se guardan puntos para enviar después',
        ServiceLocationPhase.unavailable =>
          'GPS sin confirmación: consulte el servicio y comunique la pérdida',
        ServiceLocationPhase.paused => 'GPS detenido en segundo plano',
        _ => 'Comprobando GPS del servicio',
      };
      return Column(
        children: [
          Material(
            color: Theme.of(context).colorScheme.secondaryContainer,
            child: SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '$state. ${age == null ? 'Sin captura confirmada.' : 'Última captura confirmada hace ${age.inSeconds < 0 ? 0 : age.inSeconds}s.'}',
                      key: const Key('service-gps-status'),
                    ),
                    if (onOpenService != null)
                      TextButton(
                        onPressed: onOpenService,
                        child: const Text('Consultar servicio en ejecución'),
                      ),
                  ],
                ),
              ),
            ),
          ),
          Expanded(child: child),
        ],
      );
    },
  );
}
