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

  Future<Map<String, dynamic>> getObject(
    String path, {
    bool authenticated = true,
    Map<String, String>? queryParameters,
  }) async => (await getObjectResponse(
    path,
    authenticated: authenticated,
    queryParameters: queryParameters,
  )).object;

  Future<MobileApiObjectResponse> getObjectResponse(
    String path, {
    bool authenticated = true,
    Map<String, String>? queryParameters,
  }) async {
    final response = await _execute(
      method: 'GET',
      path: path,
      authenticated: authenticated,
      queryParameters: queryParameters,
    );
    return _decodeObjectResponse(response, authenticated: authenticated);
  }

  Future<Map<String, dynamic>> postObject(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
  }) async => (await postObjectResponse(
    path,
    body,
    authenticated: authenticated,
  )).object;

  Future<MobileApiObjectResponse> postObjectResponse(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
  }) async {
    final response = await _execute(
      method: 'POST',
      path: path,
      body: body,
      authenticated: authenticated,
    );
    return _decodeObjectResponse(response, authenticated: authenticated);
  }

  Future<Map<String, dynamic>> putObject(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
  }) async {
    final response = await _execute(
      method: 'PUT',
      path: path,
      body: body,
      authenticated: authenticated,
    );
    return (await _decodeObjectResponse(
      response,
      authenticated: authenticated,
    )).object;
  }

  Future<void> postNoContent(
    String path,
    Map<String, Object?> body, {
    bool authenticated = true,
  }) async {
    final response = await _execute(
      method: 'POST',
      path: path,
      body: body,
      authenticated: authenticated,
    );
    await _throwIfUnsuccessful(response, authenticated: authenticated);
  }

  Future<http.Response> _execute({
    required String method,
    required String path,
    required bool authenticated,
    Map<String, Object?>? body,
    Map<String, String>? queryParameters,
    bool allowRefresh = true,
    String? correlationId,
  }) async {
    final requestCorrelationId = correlationId ?? correlationIdFactory();
    final response = await _sendOnce(
      method: method,
      path: path,
      body: body,
      authenticated: authenticated,
      correlationId: requestCorrelationId,
      queryParameters: queryParameters,
    );

    if (authenticated && response.statusCode == 401 && allowRefresh) {
      final refreshed =
          await accessTokenProvider?.refreshAccessToken() ?? false;
      if (refreshed) {
        return _execute(
          method: method,
          path: path,
          body: body,
          authenticated: true,
          queryParameters: queryParameters,
          allowRefresh: false,
          correlationId: requestCorrelationId,
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
  }) async {
    await _throwIfUnsuccessful(response, authenticated: authenticated);
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
  }) async {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    final exception = _toApiException(response);
    if (authenticated && _endsMobileSession(exception)) {
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
      'DRIVER_INACTIVE',
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
