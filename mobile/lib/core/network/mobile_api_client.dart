import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../app/ruta_fija_environment.dart';
import 'correlation_id.dart';
import 'mobile_access_token_provider.dart';
import 'mobile_api_exception.dart';

typedef CorrelationIdFactory = String Function();

/// HTTP boundary for Ruta Fija mobile APIs. It never logs request bodies or tokens.
final class MobileApiClient {
  MobileApiClient({
    required this.httpClient,
    required this.environment,
    this.accessTokenProvider,
    this.correlationIdFactory = createCorrelationId,
  });

  static const _requestTimeout = Duration(seconds: 15);

  final http.Client httpClient;
  final RouteFijaEnvironment environment;
  final MobileAccessTokenProvider? accessTokenProvider;
  final CorrelationIdFactory correlationIdFactory;
  final _responseGeneration = Expando<int>('mobile-response-session-generation');

  int? _sessionGeneration() => switch (accessTokenProvider) {
    MobileSessionGenerationProvider provider => provider.sessionGeneration,
    _ => null,
  };

  void _requireGeneration(int? generation) {
    if (generation != null && generation != _sessionGeneration()) {
      throw const MobileApiException(
        status: 409,
        code: 'MOBILE_SESSION_CHANGED',
        message: 'La sesión cambió. Vuelva a consultar desde su sesión actual.',
      );
    }
  }

  void _requireResponseGeneration(http.Response response) =>
      _requireGeneration(_responseGeneration[response]);

  /// Authenticated binary export. Tokens remain headers, never URI parameters.
  Future<http.Response> getBinary(String path, {Map<String,String>? queryParameters}) async {
    final response=await _execute(method:'GET',path:path,authenticated:true,queryParameters:queryParameters);
    await _throwIfUnsuccessful(response,authenticated:true);
    _requireResponseGeneration(response);
    if(response.bodyBytes.isEmpty || response.bodyBytes.length>20*1024*1024) {
      throw const MobileNetworkException('El archivo recibido no tiene un tamaño permitido.');
    }
    return response;
  }

  Future<Map<String, dynamic>> getObject(
    String path, {
    bool authenticated = true,
    Map<String, String>? queryParameters,
    Map<String, String>? headers,
  }) async => (await getObjectResponse(
    path,
    authenticated: authenticated,
    queryParameters: queryParameters,
    headers: headers,
  )).object;

  Future<MobileApiObjectResponse> getObjectResponse(
    String path, {
    bool authenticated = true,
    Map<String, String>? queryParameters,
    Map<String, String>? headers,
  }) async {
    final response = await _execute(
      method: 'GET',
      path: path,
      authenticated: authenticated,
      queryParameters: queryParameters,
      headers: headers,
    );
    return _decodeObjectResponse(response, authenticated: authenticated);
  }

  Future<List<Map<String, dynamic>>> getList(
    String path, {
    bool authenticated = true,
    Map<String, String>? headers,
    Map<String, String>? queryParameters,
  }) async {
    final response = await _execute(
      method: 'GET',
      path: path,
      authenticated: authenticated,
      headers: headers,
      queryParameters: queryParameters,
    );
    await _throwIfUnsuccessful(response, authenticated: authenticated);
    _requireResponseGeneration(response);
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! List || decoded.any((item) => item is! Map)) {
        throw const FormatException('La respuesta no es una lista JSON.');
      }
      return decoded
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
    } on FormatException {
      throw const MobileNetworkException(
        'Ruta Fija devolvió una respuesta no válida. Inténtelo nuevamente.',
      );
    }
  }

  Future<Map<String, dynamic>> postObject(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
    Map<String, String>? headers,
    bool allowSessionRefresh = true,
  }) async => (await postObjectResponse(
    path,
    body,
    authenticated: authenticated,
    headers: headers,
    allowSessionRefresh: allowSessionRefresh,
  )).object;

  Future<MobileApiObjectResponse> postObjectResponse(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
    Map<String, String>? headers,
    bool allowSessionRefresh = true,
  }) async {
    final response = await _execute(
      method: 'POST',
      path: path,
      body: body,
      authenticated: authenticated,
      headers: headers,
      allowRefresh: allowSessionRefresh,
    );
    return _decodeObjectResponse(
      response,
      authenticated: authenticated,
      invalidateOnUnauthorized: allowSessionRefresh,
    );
  }

  Future<Map<String, dynamic>> putObject(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
    Map<String, String>? headers,
  }) async {
    final response = await _execute(
      method: 'PUT',
      path: path,
      body: body,
      authenticated: authenticated,
      headers: headers,
    );
    return (await _decodeObjectResponse(
      response,
      authenticated: authenticated,
    )).object;
  }

  Future<void> putNoContent(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
    bool allowSessionRefresh = true,
  }) async {
    final response = await _execute(
      method: 'PUT',
      path: path,
      body: body,
      authenticated: authenticated,
      allowRefresh: allowSessionRefresh,
    );
    await _throwIfUnsuccessful(
      response,
      authenticated: authenticated,
      invalidateOnUnauthorized: allowSessionRefresh,
    );
  }

  Future<void> deleteNoContent(String path, {bool authenticated = true}) async {
    final response = await _execute(
      method: 'DELETE',
      path: path,
      authenticated: authenticated,
    );
    await _throwIfUnsuccessful(response, authenticated: authenticated);
  }

  Future<void> postNoContent(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
    Map<String, String>? headers,
  }) async {
    final response = await _execute(
      method: 'POST',
      path: path,
      body: body,
      authenticated: authenticated,
      headers: headers,
    );
    await _throwIfUnsuccessful(response, authenticated: authenticated);
  }

  Future<http.Response> _execute({
    required String method,
    required String path,
    required bool authenticated,
    Map<String, Object?>? body,
    Map<String, String>? headers,
    Map<String, String>? queryParameters,
    bool allowRefresh = true,
    String? correlationId,
    int? sessionGeneration,
  }) async {
    final requestCorrelationId = correlationId ?? correlationIdFactory();
    final generation = authenticated ? sessionGeneration ?? _sessionGeneration() : null;
    _requireGeneration(generation);
    late http.Response response;
    try {
      response = await _sendOnce(
      method: method,
      path: path,
      body: body,
      headers: headers,
      authenticated: authenticated,
      correlationId: requestCorrelationId,
      queryParameters: queryParameters,
      );
    } catch (_) {
      _requireGeneration(generation);
      rethrow;
    }
    _requireGeneration(generation);
    _responseGeneration[response] = generation;

    if (authenticated && response.statusCode == 401 && allowRefresh) {
      final refreshed =
          await accessTokenProvider?.refreshAccessToken() ?? false;
      _requireGeneration(generation);
      if (refreshed) {
        return _execute(
          method: method,
          path: path,
          body: body,
          headers: headers,
          authenticated: true,
          queryParameters: queryParameters,
          allowRefresh: false,
          correlationId: requestCorrelationId,
          sessionGeneration: generation,
        );
      }
      await accessTokenProvider?.invalidateSession();
    }

    return response;
  }

  Future<http.Response> _sendOnce({
    required String method,
    required String path,
    required bool authenticated,
    required String correlationId,
    Map<String, Object?>? body,
    Map<String, String>? headers,
    Map<String, String>? queryParameters,
  }) async {
    final endpoint = environment.endpoint(path);
    final request = http.Request(
      method,
      queryParameters == null
          ? endpoint
          : endpoint.replace(queryParameters: queryParameters),
    );
    request.headers.addAll({
      'accept': 'application/json',
      'cache-control': 'no-store',
      'x-correlation-id': correlationId,
    });
    if (headers != null) {
      request.headers.addAll(headers);
    }

    if (authenticated) {
      final accessToken = accessTokenProvider?.accessToken;
      if (accessToken == null || accessToken.isEmpty) {
        throw const MobileApiException(
          status: 401,
          code: 'MOBILE_SESSION_REQUIRED',
          message: 'Inicie sesión nuevamente para continuar.',
        );
      }
      request.headers['authorization'] = 'Bearer $accessToken';
    }

    if (body != null) {
      request.headers['content-type'] = 'application/json; charset=utf-8';
      request.body = jsonEncode(body);
    }

    try {
      final streamed = await httpClient.send(request).timeout(_requestTimeout);
      return await http.Response.fromStream(streamed).timeout(_requestTimeout);
    } on TimeoutException {
      throw const MobileNetworkException(
        'La conexión tardó demasiado. Revise la red e inténtelo nuevamente.',
      );
    } on http.ClientException {
      throw const MobileNetworkException(
        'No fue posible comunicarse con Ruta Fija. Revise la conexión.',
      );
    }
  }

  Future<MobileApiObjectResponse> _decodeObjectResponse(
    http.Response response, {
    required bool authenticated,
    bool invalidateOnUnauthorized = true,
  }) async {
    await _throwIfUnsuccessful(
      response,
      authenticated: authenticated,
      invalidateOnUnauthorized: invalidateOnUnauthorized,
    );
    _requireResponseGeneration(response);
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map) {
        throw const FormatException('La respuesta no es un objeto JSON.');
      }
      return MobileApiObjectResponse(
        object: Map<String, dynamic>.from(decoded),
        headers: response.headers,
      );
    } on FormatException {
      throw const MobileNetworkException(
        'Ruta Fija devolvió una respuesta no válida. Inténtelo nuevamente.',
      );
    }
  }

  Future<void> _throwIfUnsuccessful(
    http.Response response, {
    required bool authenticated,
    bool invalidateOnUnauthorized = true,
  }) async {
    _requireResponseGeneration(response);
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    final exception = _toApiException(response);
    if (authenticated &&
        invalidateOnUnauthorized &&
        _endsMobileSession(exception)) {
      await accessTokenProvider?.invalidateSession();
    }
    throw exception;
  }

  MobileApiException _toApiException(http.Response response) {
    Map<String, dynamic>? body;
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        body = Map<String, dynamic>.from(decoded);
      }
    } on FormatException {
      body = null;
    }

    final fields = <String, String>{};
    final rawErrors = body?['errors'];
    if (rawErrors is List) {
      for (final item in rawErrors) {
        if (item is Map &&
            item['field'] is String &&
            item['message'] is String) {
          fields[item['field'] as String] = item['message'] as String;
        }
      }
    }

    return MobileApiException(
      status: response.statusCode,
      code: _nonBlank(body?['code']) ?? 'HTTP_${response.statusCode}',
      message:
          _nonBlank(body?['message']) ??
          'No fue posible completar la operación. Inténtelo nuevamente.',
      correlationId: _nonBlank(body?['correlationId']),
      fieldErrors: fields,
    );
  }

  bool _endsMobileSession(MobileApiException exception) {
    return const {
      'AUTH_TOKEN_INVALID',
      'AUTH_TOKEN_EXPIRED',
      'AUTH_REFRESH_REUSE_DETECTED',
      'MOBILE_SESSION_REQUIRED',
      'MOBILE_USER_NOT_DRIVER',
    }.contains(exception.code);
  }

  String? _nonBlank(Object? value) {
    if (value is String && value.trim().isNotEmpty) {
      return value.trim();
    }
    return null;
  }
}

/// A successful JSON object and its safe response metadata.
final class MobileApiObjectResponse {
  const MobileApiObjectResponse({required this.object, required this.headers});

  final Map<String, dynamic> object;
  final Map<String, String> headers;

  String? header(String name) {
    final normalized = name.toLowerCase();
    for (final entry in headers.entries) {
      if (entry.key.toLowerCase() == normalized) {
        return entry.value;
      }
    }
    return null;
  }
}
