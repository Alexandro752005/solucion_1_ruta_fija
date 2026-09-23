import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_auth_models.dart';
import 'package:ruta_fija_conductor/features/profile/data/mobile_driver_repository.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

MobileAuthSession testSession({
  String accessToken = 'access-token',
  String refreshToken = 'refresh-token',
}) {
  return MobileAuthSession(
    accessToken: accessToken,
    refreshToken: refreshToken,
    expiresIn: 900,
    refreshExpiresIn: 604800,
    user: const MobileAuthenticatedUser(
      id: '11111111-1111-4111-8111-111111111111',
      driverId: '22222222-2222-4222-8222-222222222222',
      fullName: 'Conductora de prueba',
      role: 'CONDUCTOR',
    ),
  );
}

final class InMemoryRefreshTokenStore implements MobileRefreshTokenStore {
  InMemoryRefreshTokenStore([this.value]);

  String? value;
  int clearCalls = 0;

  @override
  Future<void> clear() async {
    clearCalls++;
    value = null;
  }

  @override
  Future<String?> readRefreshToken() async => value;

  @override
  Future<void> writeRefreshToken(String value) async {
    this.value = value;
  }
}

final class FakeMobileAuthGateway implements MobileAuthGateway {
  FakeMobileAuthGateway({
    MobileAuthSession? session,
    this.refreshFailure,
    this.loginFailure,
  }) : session = session ?? testSession();

  MobileAuthSession session;
  Object? refreshFailure;
  Object? loginFailure;
  final List<String> refreshedTokens = [];
  final List<String> loggedOutTokens = [];

  @override
  Future<MobileAuthSession> login({
    required String email,
    required String password,
  }) async {
    if (loginFailure != null) {
      throw loginFailure!;
    }
    return session;
  }

  @override
  Future<MobileAuthSession> refresh(String refreshToken) async {
    refreshedTokens.add(refreshToken);
    if (refreshFailure != null) {
      throw refreshFailure!;
    }
    return session;
  }

  @override
  Future<void> logout(String refreshToken) async {
    loggedOutTokens.add(refreshToken);
  }
}

final class FakeMobileDriverGateway implements MobileDriverGateway {
  FakeMobileDriverGateway({
    MobileDriverProfile? profile,
    MobileAvailability? availability,
  }) : profileValue =
           profile ??
           const MobileDriverProfile(
             driverId: '22222222-2222-4222-8222-222222222222',
             fullName: 'Conductora de prueba',
             groupName: 'Grupo Norte',
             availabilityStatus: MobileAvailabilityStatus.disponible,
             primaryVehicle: MobileVehicle(
               plate: 'ABC-123',
               status: 'DISPONIBLE',
             ),
           ),
       currentAvailability =
           availability ??
           const MobileAvailability(
             driverId: '22222222-2222-4222-8222-222222222222',
             status: MobileAvailabilityStatus.disponible,
           );

  MobileDriverProfile profileValue;
  MobileAvailability currentAvailability;
  Object? failure;
  final List<MobileAvailabilityStatus> changedTo = [];

  @override
  Future<MobileAvailability> availability() async {
    if (failure != null) {
      throw failure!;
    }
    return currentAvailability;
  }

  @override
  Future<MobileAvailability> changeAvailability(
    MobileAvailabilityStatus status,
  ) async {
    if (failure != null) {
      throw failure!;
    }
    changedTo.add(status);
    currentAvailability = MobileAvailability(
      driverId: currentAvailability.driverId,
      status: status,
    );
    return currentAvailability;
  }

  @override
  Future<MobileDriverProfile> profile() async {
    if (failure != null) {
      throw failure!;
    }
    return profileValue;
  }
}
