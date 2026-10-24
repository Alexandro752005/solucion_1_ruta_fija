import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../core/network/mobile_api_exception.dart';
import '../../assignments/data/mobile_assignment_repository.dart';
import '../../assignments/domain/mobile_assignment_models.dart';
import '../../profile/data/mobile_driver_repository.dart';
import '../../profile/domain/mobile_driver_models.dart';
import 'mobile_foreground_location.dart';

enum ServiceLocationPhase {
  idle,
  checking,
  sharing,
  noConsent,
  noPermission,
  noConnection,
  unavailable,
  paused,
}

/// Fresh online attempts only. No points, credentials or commands are stored in a local queue.
final class MobileServiceLocationController extends ChangeNotifier {
  MobileServiceLocationController({
    required this.assignments,
    required this.driver,
    required this.device,
    DateTime Function()? clock,
  }) : clock = clock ?? _now;
  final MobileAssignmentGateway assignments;
  final MobileDriverGateway driver;
  final MobileDeviceLocationGateway device;
  final DateTime Function() clock;
  Timer? _timer;
  String? _driverId;
  bool _foreground = false;
  bool _disposed = false;
  int _epoch = 0;
  Future<void>? _pending;
  MobileAssignment? _active;
  ServiceLocationPhase _phase = ServiceLocationPhase.idle;
  DateTime? _lastCaptureAt;
  ServiceLocationPhase get phase => _phase;
  DateTime? get lastCaptureAt => _lastCaptureAt;
  bool get hasActiveService => _active != null;
  String? get activeAssignmentId => _active?.id;
  Duration? get captureAge =>
      _lastCaptureAt == null ? null : clock().difference(_lastCaptureAt!);
  static DateTime _now() => DateTime.now().toUtc();

  void setSession(String? driverId) {
    if (_driverId == driverId) return;
    _epoch++;
    _driverId = driverId;
    _active = null;
    _lastCaptureAt = null;
    _restart();
  }

  void setForeground(bool value) {
    if (_foreground == value) return;
    _epoch++;
    _foreground = value;
    _restart();
  }

  void _restart() {
    _timer?.cancel();
    _timer = null;
    if (_driverId == null) {
      _set(ServiceLocationPhase.idle);
      return;
    }
    if (!_foreground) {
      _set(ServiceLocationPhase.paused);
      return;
    }
    _timer = Timer.periodic(const Duration(seconds: 30), (_) {
      unawaited(tick());
    });
    unawaited(tick());
  }

  bool _valid(int epoch) =>
      !_disposed && _foreground && _driverId != null && _epoch == epoch;
  Future<void> tick() {
    if (_pending != null) return _pending!;
    final work = _cycle();
    _pending = work;
    unawaited(
      work.whenComplete(() {
        if (identical(_pending, work)) _pending = null;
      }),
    );
    return work;
  }

  Future<void> _cycle() async {
    final epoch = _epoch;
    if (!_valid(epoch)) return;
    try {
      _set(ServiceLocationPhase.checking);
      final page = await assignments.list(
        status: MobileAssignmentStatus.enServicio,
        size: 2,
      );
      if (!_valid(epoch)) return;
      final ongoing = page.items
          .where(
            (a) =>
                a.driverId == _driverId &&
                a.serviceId != null &&
                a.acceptedAt != null &&
                a.startedAt != null,
          )
          .toList();
      if (ongoing.length != 1) {
        _active = null;
        _set(
          ongoing.isEmpty
              ? ServiceLocationPhase.idle
              : ServiceLocationPhase.unavailable,
        );
        return;
      }
      final a = ongoing.single;
      if (_active?.id != a.id) _lastCaptureAt = null;
      _active = a;
      await _captureAndSend(a, epoch);
    } catch (error) {
      if (_valid(epoch)) {
        _set(
          error is MobileNetworkException
              ? ServiceLocationPhase.noConnection
              : ServiceLocationPhase.unavailable,
        );
      }
    }
  }

  Future<void> _captureAndSend(MobileAssignment a, int epoch) async {
    final profile = await driver.profile();
    if (!_valid(epoch)) return;
    if (profile.driverId != _driverId ||
        profile.availabilityStatus != MobileAvailabilityStatus.enServicio) {
      _active = null;
      _set(ServiceLocationPhase.idle);
      return;
    }
    if (!profile.locationConsent) {
      _set(ServiceLocationPhase.noConsent);
      return;
    }
    if (!await device.isLocationServiceEnabled() ||
        !(await device.checkPermission()).grantsForeground) {
      if (_valid(epoch)) _set(ServiceLocationPhase.noPermission);
      return;
    }
    if (!_valid(epoch)) return;
    final point = await device.currentPosition();
    if (!_valid(epoch) || _active?.id != a.id) return;
    // A permission change while obtaining a fix must not become an asserted permission in the request.
    if (!(await device.checkPermission()).grantsForeground ||
        !await device.isLocationServiceEnabled() ||
        !_valid(epoch)) {
      if (_valid(epoch)) _set(ServiceLocationPhase.noPermission);
      return;
    }
    await driver.upsertCurrentLocation(point);
    if (!_valid(epoch)) return;
    _lastCaptureAt = point.capturedAt;
    _set(ServiceLocationPhase.sharing);
  }

  void observe(MobileAssignment a) {
    if (_disposed || a.driverId != _driverId) return;
    if (a.status != MobileAssignmentStatus.enServicio) {
      if (_active?.id == a.id) {
        _epoch++;
        _active = null;
        _set(ServiceLocationPhase.idle);
      }
      return;
    }
    if (_active?.id != a.id) {
      _epoch++;
      _lastCaptureAt = null;
    }
    _active = a;
    if (_foreground) unawaited(tick());
  }

  /// One automatic punctual attempt before the user's completion command; missing GPS never prevents safe completion.
  Future<void> beforeComplete(MobileAssignment a) async {
    if (!_foreground || _driverId != a.driverId || _disposed) return;
    final epoch = _epoch;
    try {
      if (_pending != null) await _pending;
      if (!_valid(epoch)) return;
      final current = await assignments.get(a.id);
      if (!_valid(epoch) ||
          current.status != MobileAssignmentStatus.enServicio ||
          current.startedAt == null) {
        return;
      }
      _active = current;
      await _captureAndSend(current, epoch);
    } catch (error) {
      if (_valid(epoch)) {
        _set(
          error is MobileNetworkException
              ? ServiceLocationPhase.noConnection
              : ServiceLocationPhase.unavailable,
        );
      }
    }
  }

  void _set(ServiceLocationPhase phase) {
    if (_disposed) return;
    _phase = phase;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _epoch++;
    _timer?.cancel();
    _active = null;
    _lastCaptureAt = null;
    super.dispose();
  }
}
