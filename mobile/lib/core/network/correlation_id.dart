import 'dart:math';

String createCorrelationId() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final encoded = bytes
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();

  return '${encoded.substring(0, 8)}-${encoded.substring(8, 12)}-'
      '${encoded.substring(12, 16)}-${encoded.substring(16, 20)}-'
      '${encoded.substring(20)}';
}
