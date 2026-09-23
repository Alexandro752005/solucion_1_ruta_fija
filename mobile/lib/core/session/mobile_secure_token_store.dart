import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract interface class MobileRefreshTokenStore {
  Future<String?> readRefreshToken();

  Future<void> writeRefreshToken(String value);

  Future<void> clear();
}

/// Persists only the rotating refresh token in Android's protected storage.
final class MobileSecureTokenStore implements MobileRefreshTokenStore {
  MobileSecureTokenStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              storageNamespace: 'pe.rutafija.conductor.session',
            ),
          );

  static const _refreshTokenKey = 'mobile_refresh_token';

  final FlutterSecureStorage _storage;

  @override
  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  @override
  Future<void> writeRefreshToken(String value) =>
      _storage.write(key: _refreshTokenKey, value: value);

  @override
  Future<void> clear() => _storage.delete(key: _refreshTokenKey);
}
