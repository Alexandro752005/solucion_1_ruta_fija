/// Build-time configuration that never contains credentials or database data.
final class RouteFijaEnvironment {
  RouteFijaEnvironment._(this.apiBaseUrl);

  static const defaultApiBaseUrl = 'http://10.0.2.2:8080/api/v1';

  static const _configuredApiBaseUrl = String.fromEnvironment(
    'RF_API_BASE_URL',
    defaultValue: defaultApiBaseUrl,
  );
  static const _isReleaseBuild = bool.fromEnvironment('dart.vm.product');

  /// Base URL used only by repositories in future phases.
  final String apiBaseUrl;

  factory RouteFijaEnvironment.fromBuildConfiguration() {
    return RouteFijaEnvironment.fromApiBaseUrl(_configuredApiBaseUrl);
  }

  factory RouteFijaEnvironment.fromApiBaseUrl(String value) {
    final normalized = value.trim().replaceFirst(RegExp(r'/+$'), '');
    final uri = Uri.tryParse(normalized);
    final valid =
        uri != null &&
        (uri.scheme == 'http' || uri.scheme == 'https') &&
        uri.host.isNotEmpty &&
        uri.userInfo.isEmpty &&
        uri.query.isEmpty &&
        uri.fragment.isEmpty &&
        uri.path == '/api/v1';

    if (!valid || (_isReleaseBuild && uri.scheme != 'https')) {
      throw ArgumentError.value(
        value,
        'RF_API_BASE_URL',
        'Debe ser una URL HTTP(S) terminada en /api/v1, sin credenciales ni consulta. '
            'La compilación release exige HTTPS.',
      );
    }

    return RouteFijaEnvironment._(normalized);
  }

  Uri endpoint(String relativePath) {
    final path = relativePath.replaceFirst(RegExp(r'^/+'), '');
    return Uri.parse('$apiBaseUrl/$path');
  }
}
