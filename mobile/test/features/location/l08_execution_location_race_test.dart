import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_execution_location_gate.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';

final class _ChangingPermissionDevice implements MobileDeviceLocationGateway {
  bool enabled=true;
  bool foreground=true;
  int pointReads=0;
  bool turnOffAfterPermission=true;
  @override Future<bool> isLocationServiceEnabled()async=>enabled;
  @override Future<MobileDeviceLocationPermission> checkPermission()async=>MobileDeviceLocationPermission.denied;
  @override Future<MobileDeviceLocationPermission> requestPermission()async{if(turnOffAfterPermission){enabled=false;}else{foreground=false;}return MobileDeviceLocationPermission.whileInUse;}
  @override Future<MobileCurrentLocation> currentPosition()async{pointReads++;throw UnimplementedError();}
  @override Future<bool> openAppSettings()async=>false;
  @override Future<bool> openLocationSettings()async=>false;
}
void main(){
  test('L08 GPS turned off during permission dialog cannot produce enabled start proof',()async{
    final device=_ChangingPermissionDevice();await expectLater(MobileExecutionLocationGate(device:device,isForeground:()=>device.foreground).beforeStart(),throwsA(isA<MobileDeviceLocationFailure>()));expect(device.pointReads,0);
  });
  test('L08 app leaving foreground during permission dialog cannot produce start proof',()async{
    final device=_ChangingPermissionDevice()..turnOffAfterPermission=false;await expectLater(MobileExecutionLocationGate(device:device,isForeground:()=>device.foreground).beforeStart(),throwsA(isA<MobileDeviceLocationFailure>()));expect(device.pointReads,0);
  });
}
