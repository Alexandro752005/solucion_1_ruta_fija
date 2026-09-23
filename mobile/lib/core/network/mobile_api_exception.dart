final class MobileApiException implements Exception {
  const MobileApiException({
    required this.status,
    required this.code,
    required this.message,
    this.correlationId,
    this.fieldErrors = const {},
  });

  final int status;
  final String code;
  final String message;
  final String? correlationId;
  final Map<String, String> fieldErrors;

  @override
  String toString() => 'MobileApiException(status: $status, code: $code)';
}

final class MobileNetworkException implements Exception {
  const MobileNetworkException(this.message);

  final String message;

  @override
  String toString() => 'MobileNetworkException($message)';
}
