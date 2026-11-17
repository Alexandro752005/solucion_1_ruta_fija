/// Public map configuration. No API token or Ruta Fija identity is sent to tiles.
final class MobileMapConfiguration {
  static const configuredTemplate = String.fromEnvironment(
    'RF_MAP_TILE_URL',
    defaultValue: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  );

  static String validateTemplate(String template) {
    final resolved = template
        .replaceAll('{z}', '1')
        .replaceAll('{x}', '1')
        .replaceAll('{y}', '1');
    final uri = Uri.tryParse(resolved);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !template.contains('{z}') ||
        !template.contains('{x}') ||
        !template.contains('{y}')) {
      throw const FormatException(
        'La cartografía requiere una URL HTTPS pública de teselas sin credenciales.',
      );
    }
    return template;
  }
}
