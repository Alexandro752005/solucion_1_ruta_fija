import '../domain/mobile_applicant_access_models.dart';
import 'mobile_applicant_documentary_repository.dart';

abstract interface class MobileApplicantAccessGateway {
  Future<MobileApplicantAccess> login({
    required String email,
    required String password,
  });
  Future<List<MobileApplicantRequest>> requests(String token);
  Future<void> logout(String token);
}

/// Uses a public HTTP client and an explicit purpose header, never bearer auth.
final class MobileApplicantAccessRepository
    extends MobileApplicantDocumentaryRepository
    implements MobileApplicantAccessGateway {
  MobileApplicantAccessRepository(super.api);

  @override
  Future<MobileApplicantAccess> login({
    required String email,
    required String password,
  }) async {
    final response = await api.postObject('onboarding/access/login', {
      'email': email.trim().toLowerCase(),
      'password': password,
    }, authenticated: false);
    return MobileApplicantAccess.fromJson(response);
  }

  @override
  Future<List<MobileApplicantRequest>> requests(String token) async {
    final response = await api.getList(
      'onboarding/applicant/requests',
      authenticated: false,
      headers: {'X-Onboarding-Token': token},
    );
    return response
        .map(MobileApplicantRequest.fromJson)
        .toList(growable: false);
  }

  @override
  Future<void> logout(String token) => api.postNoContent(
    'onboarding/access/logout',
    const {},
    authenticated: false,
    headers: {'X-Onboarding-Token': token},
  );
}
