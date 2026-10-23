import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../domain/mobile_assignment_models.dart';

abstract interface class MobileAssignmentCommandStore {
  Future<List<MobilePendingAssignmentCommand>> readForDriver(String driverId);

  Future<void> write(MobilePendingAssignmentCommand command);

  Future<void> delete(MobilePendingAssignmentCommand command);

  Future<void> clearAll();
}

/// Holds an uncertain online result only for this app process.
final class MobileSessionAssignmentCommandStore
    implements MobileAssignmentCommandStore {
  final Map<String, MobilePendingAssignmentCommand> _commands = {};

  @override
  Future<List<MobilePendingAssignmentCommand>> readForDriver(
    String driverId,
  ) async {
    final commands = _commands.values
        .where((command) => command.driverId == driverId)
        .toList(growable: false);
    return List.unmodifiable(commands);
  }

  @override
  Future<void> write(MobilePendingAssignmentCommand command) async {
    _commands[_keyFor(command)] = command;
  }

  @override
  Future<void> delete(MobilePendingAssignmentCommand command) async {
    _commands.remove(_keyFor(command));
  }

  @override
  Future<void> clearAll() async {
    _commands.clear();
  }

  String _keyFor(MobilePendingAssignmentCommand command) {
    return '${command.driverId}:${command.assignmentId}';
  }
}

/// Legacy persistent store retained for historical compatibility. The v3.1
/// application runtime uses [MobileSessionAssignmentCommandStore].
final class MobileSecureAssignmentCommandStore
    implements MobileAssignmentCommandStore {
  MobileSecureAssignmentCommandStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              storageNamespace: 'pe.rutafija.conductor.operations',
            ),
          );

  static const _keyPrefix = 'pending_assignment_command_v1:';

  final FlutterSecureStorage _storage;

  @override
  Future<List<MobilePendingAssignmentCommand>> readForDriver(
    String driverId,
  ) async {
    final values = await _storage.readAll();
    final commands = <MobilePendingAssignmentCommand>[];
    for (final entry in values.entries) {
      if (!entry.key.startsWith(_keyPrefix)) {
        continue;
      }
      try {
        final decoded = jsonDecode(entry.value);
        if (decoded is! Map) {
          throw const FormatException('El comando persistido no es un objeto.');
        }
        final command = MobilePendingAssignmentCommand.fromStorageJson(
          Map<String, dynamic>.from(decoded),
        );
        if (command.driverId == driverId) {
          commands.add(command);
        }
      } on FormatException {
        await _storage.delete(key: entry.key);
      }
    }
    commands.sort((left, right) => left.occurredAt.compareTo(right.occurredAt));
    return List.unmodifiable(commands);
  }

  @override
  Future<void> write(MobilePendingAssignmentCommand command) {
    return _storage.write(
      key: _keyFor(command),
      value: jsonEncode(command.toStorageJson()),
    );
  }

  @override
  Future<void> delete(MobilePendingAssignmentCommand command) {
    return _storage.delete(key: _keyFor(command));
  }

  @override
  Future<void> clearAll() async {
    final values = await _storage.readAll();
    for (final key in values.keys) {
      if (key.startsWith(_keyPrefix)) {
        await _storage.delete(key: key);
      }
    }
  }

  String _keyFor(MobilePendingAssignmentCommand command) {
    return '$_keyPrefix${command.driverId}:${command.assignmentId}';
  }
}
