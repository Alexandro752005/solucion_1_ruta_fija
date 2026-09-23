import 'package:http/http.dart' as http;

import '../core/network/mobile_api_client.dart';
import '../core/session/mobile_secure_token_store.dart';
import '../core/session/mobile_session_controller.dart';
import '../features/auth/data/mobile_auth_repository.dart';
import '../features/profile/data/mobile_driver_repository.dart';
import 'ruta_fija_environment.dart';

/// Composition root: the mobile client reaches Spring Boot only through HTTPS/HTTP APIs.
final class RutaFijaDependencies {
  RutaFijaDependencies({
    required this.sessionController,
    required this.driverGateway,
    required this.httpClient,
  });

  factory RutaFijaDependencies.create(RouteFijaEnvironment environment) {
    final httpClient = http.Client();
    final publicApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
    );
    final sessionController = MobileSessionController(
      authGateway: MobileAuthRepository(publicApi),
      tokenStore: MobileSecureTokenStore(),
    );
    final authenticatedApi = MobileApiClient(
      httpClient: httpClient,
      environment: environment,
      accessTokenProvider: sessionController,
    );

    return RutaFijaDependencies(
      sessionController: sessionController,
      driverGateway: MobileDriverRepository(authenticatedApi),
      httpClient: httpClient,
    );
  }

  final MobileSessionController sessionController;
  final MobileDriverGateway driverGateway;
  final http.Client httpClient;

  void dispose() {
    sessionController.dispose();
    httpClient.close();
  }
}
