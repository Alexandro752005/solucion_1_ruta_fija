import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_applicant_documentary_models.dart';

abstract interface class MobileApplicantDocumentaryGateway {
  Future<MobileApplicantDossier> dossier(String token, String requestId);
  Future<MobileApplicantDossier> saveDocument(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String modality,
    required MobileApplicantDocumentInput document,
  });
  Future<MobileApplicantDossier> resubmit(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
  });
  Future<MobileApplicantDossier> reapply(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String invitationCode,
  });
}

base class MobileApplicantDocumentaryRepository
    implements MobileApplicantDocumentaryGateway {
  MobileApplicantDocumentaryRepository(this.api);
  final MobileApiClient api;

  String _path(String requestId) =>
      'onboarding/applicant/requests/${Uri.encodeComponent(requestId)}';
  Map<String, String> _headers(String token) => {'X-Onboarding-Token': token};

  @override
  Future<MobileApplicantDossier> dossier(
    String token,
    String requestId,
  ) async => MobileApplicantDossier.fromJson(
    await api.getObject(
      '${_path(requestId)}/documentary-status',
      authenticated: false,
      headers: _headers(token),
    ),
  );

  @override
  Future<MobileApplicantDossier> saveDocument(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String modality,
    required MobileApplicantDocumentInput document,
  }) async => MobileApplicantDossier.fromJson(
    await api.putObject(
      '${_path(requestId)}/documents',
      {
        'expectedVersion': expectedVersion,
        'operationKey': operationKey,
        'modality': modality,
        'documents': [document.toJson()],
      },
      authenticated: false,
      headers: _headers(token),
    ),
  );

  @override
  Future<MobileApplicantDossier> resubmit(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
  }) async => MobileApplicantDossier.fromJson(
    await api.postObject(
      '${_path(requestId)}/resubmit',
      {
        'expectedVersion': expectedVersion,
        'operationKey': operationKey,
        'reason': 'Reenvío para revisión tras subsanación.',
      },
      authenticated: false,
      headers: _headers(token),
    ),
  );

  @override
  Future<MobileApplicantDossier> reapply(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String invitationCode,
  }) async => MobileApplicantDossier.fromJson(
    await api.postObject(
      '${_path(requestId)}/reapply',
      {
        'expectedVersion': expectedVersion,
        'operationKey': operationKey,
        'invitationCode': invitationCode,
      },
      authenticated: false,
      headers: _headers(token),
    ),
  );
}
