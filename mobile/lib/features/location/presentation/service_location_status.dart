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
      var state = switch (controller.phase) {
        ServiceLocationPhase.sharing => 'GPS del servicio activo',
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
      final diagnostic = controller.diagnostic;
      if (diagnostic != null &&
          (controller.phase == ServiceLocationPhase.unavailable ||
              controller.phase == ServiceLocationPhase.noConnection ||
              controller.phase == ServiceLocationPhase.checking)) {
        final detail = _diagnosticMessage(diagnostic);
        state = controller.phase == ServiceLocationPhase.checking
            ? 'Comprobando GPS. Último intento: $detail'
            : detail;
      }
      if (controller.isSignalDegraded) {
        state =
            'GPS sin captura reciente: señal degradada. '
            'El servicio continúa; mantenga la app visible y revise la señal. '
            '$state';
      }
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

String _diagnosticMessage(ServiceLocationDiagnostic diagnostic) {
  return switch (diagnostic.kind) {
    ServiceLocationFailureKind.timeout =>
      diagnostic.step == ServiceLocationStep.capture
          ? 'El dispositivo no obtuvo una lectura GPS en 15 segundos. Mantenga la app visible y revise la señal'
          : 'No se confirmó la comprobación de ubicación a tiempo',
    ServiceLocationFailureKind.provider => 'No fue posible consultar la ubicación del dispositivo. Revise sus ajustes',
    ServiceLocationFailureKind.invalidAccuracy =>
      'La lectura GPS no informó una precisión válida. No se envió ese punto',
    ServiceLocationFailureKind.network =>
      diagnostic.step == ServiceLocationStep.upload
          ? 'No se confirmó la entrega del punto. No se conserva para enviarlo después'
          : 'No se pudo confirmar el servicio con Ruta Fija. Revise la conexión',
    ServiceLocationFailureKind.invalidResponse => 'La respuesta de Ruta Fija no pudo validarse. No hay una captura confirmada nueva',
    ServiceLocationFailureKind.apiRejected => _apiDiagnosticMessage(diagnostic),
    ServiceLocationFailureKind.unknown =>
      'GPS sin confirmación: consulte el servicio y comunique la pérdida',
  };
}

String _apiDiagnosticMessage(ServiceLocationDiagnostic diagnostic) {
  if (diagnostic.httpStatus == 503) {
    return 'Ruta Fija no está disponible temporalmente. No se confirmó el punto';
  }
  return switch (diagnostic.apiCause) {
    ServiceLocationApiCause.captureTime => 'El servidor rechazó la hora de captura. Revise la fecha y hora del dispositivo',
    ServiceLocationApiCause.consent =>
      'El servidor requiere consentimiento de ubicación. Revise Ubicación',
    ServiceLocationApiCause.permission => 'El servidor no confirmó el permiso de ubicación. Revise sus ajustes con la app visible',
    ServiceLocationApiCause.state => 'El servidor ya no admite ubicación para este servicio. Consulte su estado',
    ServiceLocationApiCause.session =>
      'La sesión ya no permite confirmar ubicación. Consulte su sesión',
    ServiceLocationApiCause.validation => 'El servidor rechazó los datos de la lectura GPS. No se confirmó el punto',
    ServiceLocationApiCause.other => 'Ruta Fija no confirmó la operación de ubicación. Consulte el estado del servicio',
  };
}
