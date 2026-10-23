import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_execution_location_gate.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';

import '../../support/mobile_fakes.dart';

void main() {
  test('start proof checks OS state without obtaining a point', () async {
    final device = FakeMobileDeviceLocationGateway(
      currentPermission: MobileDeviceLocationPermission.denied,
      requestResult: MobileDeviceLocationPermission.whileInUse,
    );
    final gate = MobileExecutionLocationGate(
      device: device,
      isForeground: () => true,
    );
    expect(await gate.beforeStart(), {
      'permissionGranted': true,
      'locationServiceEnabled': true,
      'foreground': true,
    });
    expect(device.permissionRequests, 1);
    expect(device.locationReads, 0);
  });
  test('denied GPS does not read or synthesize an OS permission', () async {
    final device = FakeMobileDeviceLocationGateway(
      currentPermission: MobileDeviceLocationPermission.deniedForever,
    );
    await expectLater(
      MobileExecutionLocationGate(
        device: device,
        isForeground: () => true,
      ).beforeStart(),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
    expect(device.permissionRequests, 0);
    expect(device.locationReads, 0);
  });
  test('background or loss of foreground cannot issue a start proof', () async {
    final device = FakeMobileDeviceLocationGateway();
    await expectLater(
      MobileExecutionLocationGate(
        device: device,
        isForeground: () => false,
      ).beforeStart(),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
    var calls = 0;
    await expectLater(
      MobileExecutionLocationGate(
        device: device,
        isForeground: () => ++calls == 1,
      ).beforeStart(),
      throwsA(isA<MobileDeviceLocationFailure>()),
    );
    expect(device.locationReads, 0);
  });
}
