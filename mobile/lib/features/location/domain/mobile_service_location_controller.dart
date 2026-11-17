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

enum ServiceLocationStep { discovery, profile, permission, capture, upload }

enum MapLocationPhase {
  idle,
  checking,
  ready,
  noPermission,
  gpsDisabled,
  noConnection,
  unavailable,
  paused,
}

enum ServiceLocationFailureKind {
  timeout,
  provider,
  invalidAccuracy,
  network,
  apiRejected,
  invalidResponse,
  unknown,
}

enum ServiceLocationApiCause {
  captureTime,
  consent,
  permission,
  state,
  session,
  validation,
  other,
}

/// In-memory classification only; never retains a point, payload or exception text.
final class ServiceLocationDiagnostic {
  const ServiceLocationDiagnostic({
    required this.step,
    required this.kind,
    this.httpStatus,
    this.apiCause = ServiceLocationApiCause.other,
  });

  final ServiceLocationStep step;
  final ServiceLocationFailureKind kind;
  final int? httpStatus;
  final ServiceLocationApiCause apiCause;

  @override
  String toString() =>
      'ServiceLocationDiagnostic(${step.name}, ${kind.name}, $httpStatus, ${apiCause.name})';
}

final class _LocationAttemptFailure implements Exception {
  const _LocationAttemptFailure(this.diagnostic);
  final ServiceLocationDiagnostic diagnostic;
}

/// Fresh online attempts only. No points, credentials or commands are stored in a local queue.
final class MobileServiceLocationController extends ChangeNotifier {
  MobileServiceLocationController({
    required this.assignments,
    required this.driver,
    required this.device,
    DateTime Function()? clock,
  }) : clock = clock ?? _now {
    if (device case MobileDeviceLocationSignals signals) {
      _deviceStatus = signals.locationServiceChanges.listen(
        (enabled) {
          if (_disposed) return;
          if (!enabled) {
            _epoch++;
            _mapPoint = null;
            _set(ServiceLocationPhase.noPermission);
            _mapPhase = MapLocationPhase.gpsDisabled;
            notifyListeners();
          } else if (_foreground && _driverId != null) {
            unawaited(tick());
          }
        },
        onError: (Object _) {
          if (_disposed) return;
          _epoch++;
          _mapPoint = null;
          _mapPhase = MapLocationPhase.unavailable;
          _set(ServiceLocationPhase.unavailable);
        },
      );
    }
  }
  final MobileAssignmentGateway assignments;
  final MobileDriverGateway driver;
  final MobileDeviceLocationGateway device;
  final DateTime Function() clock;
  Timer? _timer;
  StreamSubscription<bool>? _deviceStatus;
  String? _driverId;
  bool _foreground = false;
  bool _disposed = false;
  int _epoch = 0;
  Future<void>? _pending;
  MobileAssignment? _active;
  ServiceLocationPhase _phase = ServiceLocationPhase.idle;
  ServiceLocationDiagnostic? _diagnostic;
  DateTime? _lastCaptureAt;
  DateTime? _signalWatchStartedAt;
  // Only the visible map may retain a sensor point; never a queue or disk history.
  MobileCurrentLocation? _mapPoint;
  bool _mapVisible = false;
  bool _consentBlocked = false;
  MapLocationPhase _mapPhase = MapLocationPhase.idle;
  MobileCurrentLocation? get mapPoint => _mapPoint;
  MapLocationPhase get mapPhase => _mapPhase;
  bool get isForeground => _foreground;
  bool get mapVisible => _mapVisible;
  static const maximumCaptureAge = Duration(seconds: 120);
  ServiceLocationPhase get phase => _phase;
  ServiceLocationDiagnostic? get diagnostic => _diagnostic;
  DateTime? get lastCaptureAt => _lastCaptureAt;
  bool get hasActiveService => _active != null;
  String? get activeAssignmentId => _active?.id;
  Duration? get captureAge =>
      _lastCaptureAt == null ? null : clock().difference(_lastCaptureAt!);

  /// A freshness warning, not proof that the phone lost satellite reception.
  /// Permission, consent and network failures keep their own distinct states.
  bool get isSignalDegraded {
    if (!_foreground ||
        _active == null ||
        _phase == ServiceLocationPhase.idle ||
        _phase == ServiceLocationPhase.noConsent ||
        _phase == ServiceLocationPhase.noPermission ||
        _phase == ServiceLocationPhase.noConnection ||
        _phase == ServiceLocationPhase.paused ||
        _diagnostic?.kind == ServiceLocationFailureKind.network) {
      return false;
    }
    final since = _lastCaptureAt ?? _signalWatchStartedAt;
    return since != null && clock().difference(since) > maximumCaptureAge;
  }

  static DateTime _now() => DateTime.now().toUtc();

  void setSession(String? driverId) {
    if (_driverId == driverId) return;
    _epoch++;
    _driverId = driverId;
    _mapPoint = null;
    _consentBlocked = false;
    _active = null;
    _lastCaptureAt = null;
    _signalWatchStartedAt = null;
    _diagnostic = null;
    _restart();
  }

  void setForeground(bool value) {
    if (_foreground == value) return;
    _epoch++;
    _foreground = value;
    if (!value) {
      _mapPoint = null;
      _mapPhase = MapLocationPhase.paused;
    }
    _restart();
  }

  void setMapVisible(bool visible) {
    if (_disposed || _mapVisible == visible) return;
    _mapVisible = visible;
    if (!visible) {
      _mapPoint = null;
      _mapPhase = MapLocationPhase.idle;
    } else if (_foreground && _driverId != null) {
      unawaited(tick());
    }
    notifyListeners();
  }

  /// Called before a revocation request and after a confirmed explicit grant.
  /// A failed revocation remains locally blocked; no optimistic re-enabling.
  void consentChanged(bool consent) {
    if (_disposed) return;
    _epoch++;
    _consentBlocked = !consent;
    _mapPoint = null;
    _mapPhase = MapLocationPhase.idle;
    _set(
      consent ? ServiceLocationPhase.checking : ServiceLocationPhase.noConsent,
    );
    if (consent && _foreground) unawaited(tick());
  }

  void clearMapPoint() {
    if (_disposed) return;
    _epoch++;
    _mapPoint = null;
    _mapPhase = MapLocationPhase.idle;
    notifyListeners();
  }

  Future<void> requestMapPermission() async {
    if (!_foreground || _driverId == null || _disposed) return;
    if (!await device.isLocationServiceEnabled()) {
      _mapPoint = null;
      _mapPhase = MapLocationPhase.gpsDisabled;
      notifyListeners();
      return;
    }
    var permission = await device.checkPermission();
    if (permission == MobileDeviceLocationPermission.denied) {
      permission = await device.requestPermission();
    }
    if (_disposed) return;
    if (!permission.grantsForeground) {
      _mapPoint = null;
      _mapPhase = MapLocationPhase.noPermission;
      notifyListeners();
      return;
    }
    await tick();
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
    return _track(_cycle());
  }

  Future<void> _track(Future<void> work) {
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
      final page = await _at(
        ServiceLocationStep.discovery,
        () => assignments.list(
          status: MobileAssignmentStatus.enServicio,
          size: 2,
        ),
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
        if (ongoing.isEmpty && _mapVisible) await _capturePersonal(epoch);
        return;
      }
      final a = ongoing.single;
      if (_active?.id != a.id) {
        _lastCaptureAt = null;
        _signalWatchStartedAt = clock();
        _diagnostic = null;
      }
      _active = a;
      await _captureAndSend(a, epoch);
    } catch (error) {
      _recordFailure(error, epoch);
    }
  }

  Future<void> _captureAndSend(MobileAssignment a, int epoch) async {
    final profile = await _at(ServiceLocationStep.profile, driver.profile);
    if (!_valid(epoch)) return;
    if (profile.driverId != _driverId ||
        profile.availabilityStatus != MobileAvailabilityStatus.enServicio) {
      _active = null;
      _set(ServiceLocationPhase.idle);
      return;
    }
    if (!profile.locationConsent || _consentBlocked) {
      _set(ServiceLocationPhase.noConsent);
      if (_mapVisible) await _capturePersonal(epoch);
      return;
    }
    if (!await _serviceDeviceAllowed(epoch)) return;
    if (!_valid(epoch)) return;
    final point = await _at(
      ServiceLocationStep.capture,
      device.currentPosition,
    );
    if (!_valid(epoch) || _active?.id != a.id) return;
    // A permission change while obtaining a fix must not become an asserted permission in the request.
    if (!await _serviceDeviceAllowed(epoch)) return;
    await _at(
      ServiceLocationStep.upload,
      () => driver.upsertCurrentLocation(point),
    );
    if (!_valid(epoch)) return;
    _lastCaptureAt = point.capturedAt;
    if (_mapVisible) {
      _mapPoint = point;
      _mapPhase = MapLocationPhase.ready;
    }
    _set(ServiceLocationPhase.sharing);
  }

  Future<bool> _serviceDeviceAllowed(int epoch) async {
    final enabled = await _at(
      ServiceLocationStep.permission,
      device.isLocationServiceEnabled,
    );
    if (!_valid(epoch)) return false;
    if (!enabled) {
      _deviceUnavailable(gpsDisabled: true);
      return false;
    }
    final permission = await _at(
      ServiceLocationStep.permission,
      device.checkPermission,
    );
    if (!_valid(epoch)) return false;
    if (!permission.grantsForeground) {
      _deviceUnavailable(gpsDisabled: false);
      return false;
    }
    final stillEnabled = await _at(
      ServiceLocationStep.permission,
      device.isLocationServiceEnabled,
    );
    if (!_valid(epoch)) return false;
    if (!stillEnabled) {
      _deviceUnavailable(gpsDisabled: true);
      return false;
    }
    return true;
  }

  void _deviceUnavailable({required bool gpsDisabled}) {
    _mapPoint = null;
    _mapPhase = gpsDisabled
        ? MapLocationPhase.gpsDisabled
        : MapLocationPhase.noPermission;
    _phase = ServiceLocationPhase.noPermission;
    _diagnostic = null;
    notifyListeners();
  }

  Future<void> _capturePersonal(int epoch) async {
    if (!_valid(epoch) || !_mapVisible) return;
    _mapPhase = MapLocationPhase.checking;
    notifyListeners();
    if (!await _at(
      ServiceLocationStep.permission,
      device.isLocationServiceEnabled,
    )) {
      if (_valid(epoch)) {
        _mapPoint = null;
        _mapPhase = MapLocationPhase.gpsDisabled;
        notifyListeners();
      }
      return;
    }
    if (!(await _at(
      ServiceLocationStep.permission,
      device.checkPermission,
    )).grantsForeground) {
      if (_valid(epoch)) {
        _mapPoint = null;
        _mapPhase = MapLocationPhase.noPermission;
        notifyListeners();
      }
      return;
    }
    if (!_valid(epoch) || !_mapVisible) return;
    final point = await _at(
      ServiceLocationStep.capture,
      device.currentPosition,
    );
    if (!_valid(epoch) || !_mapVisible) return;
    final permitted = (await device.checkPermission()).grantsForeground;
    final enabled = await device.isLocationServiceEnabled();
    if (!_valid(epoch) || !_mapVisible) return;
    if (!permitted || !enabled) {
      _mapPoint = null;
      _mapPhase = enabled
          ? MapLocationPhase.noPermission
          : MapLocationPhase.gpsDisabled;
    } else if (!point.latitude.isFinite ||
        !point.longitude.isFinite ||
        point.latitude.abs() > 90 ||
        point.longitude.abs() > 180 ||
        !point.accuracyMeters.isFinite ||
        point.accuracyMeters < 0) {
      _mapPoint = null;
      _mapPhase = MapLocationPhase.unavailable;
    } else {
      _mapPoint = point;
      _mapPhase = MapLocationPhase.ready;
    }
    notifyListeners();
    // No repository upload is reachable from this branch.
  }

  void observe(MobileAssignment a) {
    if (_disposed || a.driverId != _driverId) return;
    if (a.status != MobileAssignmentStatus.enServicio) {
      if (_active?.id == a.id) {
        _epoch++;
        _active = null;
        _mapPoint = null;
        _mapPhase = MapLocationPhase.idle;
        _set(ServiceLocationPhase.idle);
      }
      return;
    }
    if (_active?.id != a.id) {
      _epoch++;
      _lastCaptureAt = null;
      _signalWatchStartedAt = clock();
      _diagnostic = null;
    }
    _active = a;
    if (_foreground) unawaited(tick());
  }

  /// One automatic punctual attempt before the user's completion command; missing GPS never prevents safe completion.
  Future<void> beforeComplete(MobileAssignment a) {
    if (!_foreground || _driverId != a.driverId || _disposed) {
      return Future.value();
    }
    final pending = _pending;
    if (pending != null) return pending.then((_) => beforeComplete(a));
    // Own the same single-flight lock before the first awaited GET, so the
    // periodic tick, map refresh and manual update can only join this attempt.
    return _track(_completeCapture(a));
  }

  Future<void> _completeCapture(MobileAssignment a) async {
    final epoch = _epoch;
    try {
      if (!_valid(epoch)) return;
      final current = await _at(
        ServiceLocationStep.discovery,
        () => assignments.get(a.id),
      );
      if (!_valid(epoch) ||
          current.status != MobileAssignmentStatus.enServicio ||
          current.startedAt == null) {
        return;
      }
      _active = current;
      _signalWatchStartedAt ??= clock();
      await _captureAndSend(current, epoch);
    } catch (error) {
      _recordFailure(error, epoch);
    }
  }

  Future<T> _at<T>(ServiceLocationStep step, Future<T> Function() work) async {
    try {
      return await work();
    } catch (error) {
      throw _LocationAttemptFailure(_classify(step, error));
    }
  }

  ServiceLocationDiagnostic _classify(ServiceLocationStep step, Object error) {
    final ServiceLocationFailureKind kind;
    if (error is MobileDeviceLocationFailure) {
      kind = switch (error.kind) {
        MobileDeviceLocationFailureKind.timeout =>
          ServiceLocationFailureKind.timeout,
        MobileDeviceLocationFailureKind.provider =>
          ServiceLocationFailureKind.provider,
        MobileDeviceLocationFailureKind.invalidAccuracy =>
          ServiceLocationFailureKind.invalidAccuracy,
        MobileDeviceLocationFailureKind.unknown =>
          ServiceLocationFailureKind.unknown,
      };
    } else if (error is TimeoutException) {
      kind = ServiceLocationFailureKind.timeout;
    } else if (error is MobileNetworkException) {
      kind = ServiceLocationFailureKind.network;
    } else if (error is MobileApiException) {
      // Only fixed protocol codes become diagnostic data, never server text.
      final cause = switch (error.code) {
        'LOCATION_CAPTURE_TOO_OLD' => ServiceLocationApiCause.captureTime,
        'LOCATION_CONSENT_REQUIRED' => ServiceLocationApiCause.consent,
        'LOCATION_PERMISSION_NOT_REPORTED' =>
          ServiceLocationApiCause.permission,
        'LOCATION_STATE_NOT_ALLOWED' => ServiceLocationApiCause.state,
        'AUTH_TOKEN_INVALID' ||
        'AUTH_TOKEN_EXPIRED' ||
        'MOBILE_SESSION_REQUIRED' ||
        'MOBILE_SESSION_CHANGED' => ServiceLocationApiCause.session,
        'VALIDATION_ERROR' => ServiceLocationApiCause.validation,
        _ => ServiceLocationApiCause.other,
      };
      return ServiceLocationDiagnostic(
        step: step,
        kind: ServiceLocationFailureKind.apiRejected,
        httpStatus: error.status >= 100 && error.status <= 599
            ? error.status
            : null,
        apiCause: cause,
      );
    } else if (error is FormatException) {
      kind = ServiceLocationFailureKind.invalidResponse;
    } else {
      kind = ServiceLocationFailureKind.unknown;
    }
    return ServiceLocationDiagnostic(step: step, kind: kind);
  }

  void _recordFailure(Object error, int epoch) {
    if (!_valid(epoch)) return;
    _mapPoint = null;
    _diagnostic = error is _LocationAttemptFailure
        ? error.diagnostic
        : _classify(ServiceLocationStep.discovery, error);
    _set(
      _diagnostic?.kind == ServiceLocationFailureKind.network
          ? ServiceLocationPhase.noConnection
          : ServiceLocationPhase.unavailable,
    );
    _mapPhase = _diagnostic?.kind == ServiceLocationFailureKind.network
        ? MapLocationPhase.noConnection
        : MapLocationPhase.unavailable;
    notifyListeners();
  }

  void _set(ServiceLocationPhase phase) {
    if (_disposed) return;
    _phase = phase;
    if (phase == ServiceLocationPhase.noPermission ||
        phase == ServiceLocationPhase.paused) {
      _mapPoint = null;
      _mapPhase = phase == ServiceLocationPhase.paused
          ? MapLocationPhase.paused
          : MapLocationPhase.noPermission;
    }
    if (phase == ServiceLocationPhase.idle ||
        phase == ServiceLocationPhase.sharing ||
        phase == ServiceLocationPhase.noConsent ||
        phase == ServiceLocationPhase.noPermission ||
        phase == ServiceLocationPhase.paused) {
      _diagnostic = null;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _epoch++;
    _timer?.cancel();
    _active = null;
    unawaited(_deviceStatus?.cancel());
    _mapPoint = null;
    _lastCaptureAt = null;
    _signalWatchStartedAt = null;
    _diagnostic = null;
    super.dispose();
  }
}
