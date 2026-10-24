import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_documentary_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_applicant_documentary_models.dart';

Map<String, dynamic> documentaryJson({
  String status = 'RETURNED',
  int version = 7,
  String requestId = 'synthetic-request-id',
  String? reapplyAfter,
}) => {
  'requestId': requestId,
  'status': status,
  'version': version,
  'reviewRounds': 1,
  'modality': 'TAXI',
  'catalogId': 'L01-CHICLAYO',
  'catalogVersion': '0.1.0',
  'catalogHash': 'synthetic-catalog-hash',
  'catalog': [
    {
      'requirementId': 'CON-01',
      'title': 'Identidad del postulante',
      'mandatory': true,
      'fieldKeys': ['documentType', 'documentNumber', 'fullName', 'issuer'],
    },
    {
      'requirementId': 'CON-02',
      'title': 'Licencia compatible',
      'mandatory': true,
      'fieldKeys': ['licenseNumber', 'licenseClass'],
    },
  ],
  'documents': [
    {
      'requirementId': 'CON-01',
      'entity': 'PERSON',
      'fields': {
        'documentNumber': 'PII-must-be-discarded',
        'fullName': 'Name must not render',
      },
      'expiryMode': 'UNKNOWN',
      'validUntil': null,
      'applicability': 'POR_CONTRASTAR',
      'condition': 'Private condition must not render',
      'sourceReference': 'Private source must not render',
      'result': 'OBSERVADO',
      'reviewReference': 'Private review must not render',
      'observation': 'CON-01: confirme la vigencia documental.',
    },
    {
      'requirementId': 'CON-02',
      'entity': 'PERSON',
      'fields': {},
      'expiryMode': 'UNKNOWN',
      'validUntil': null,
      'applicability': 'POR_CONTRASTAR',
      'condition': null,
      'sourceReference': null,
      'result': 'PENDING',
      'reviewReference': null,
      'observation': null,
    },
  ],
  'history': [
    {
      'action': 'RETURNED',
      'occurredAt': '2026-10-15T15:00:00Z',
      'reason': 'Complete los requisitos observados.',
      'round': 1,
    },
  ],
  'correctionDeadline': status == 'RETURNED' ? '2026-10-17T15:00:00Z' : null,
  'overdue': false,
  'personalApproved': status == 'APPROVED',
  'provisionedUserId': null,
  'provisionedDriverId': null,
  'operationalEnabled': false,
  'reapplyAfter': reapplyAfter,
};

final class FakeMobileApplicantDocumentaryGateway
    implements MobileApplicantDocumentaryGateway {
  FakeMobileApplicantDocumentaryGateway({
    String status = 'RETURNED',
    String? reapplyAfter,
  }) : json = documentaryJson(status: status, reapplyAfter: reapplyAfter);
  Map<String, dynamic> json;
  final events = <String>[];
  final saves =
      <
        ({
          int version,
          String operationKey,
          String modality,
          MobileApplicantDocumentInput document,
        })
      >[];
  final resubmissions = <({int version, String operationKey})>[];
  final reapplications =
      <({int version, String operationKey, String invitationCode})>[];
  Object? nextMutationFailure;
  Object? nextReadFailure;
  bool failReadAfterMutation = false;

  void _failMutation() {
    final failure = nextMutationFailure;
    nextMutationFailure = null;
    if (failure != null) {
      if (failReadAfterMutation) nextReadFailure = failure;
      throw failure;
    }
  }

  @override
  Future<MobileApplicantDossier> dossier(String token, String requestId) async {
    events.add('GET');
    final failure = nextReadFailure;
    nextReadFailure = null;
    if (failure != null) throw failure;
    return MobileApplicantDossier.fromJson(json);
  }

  @override
  Future<MobileApplicantDossier> saveDocument(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String modality,
    required MobileApplicantDocumentInput document,
  }) async {
    events.add('PUT');
    saves.add((
      version: expectedVersion,
      operationKey: operationKey,
      modality: modality,
      document: document,
    ));
    _failMutation();
    json = {...json, 'version': expectedVersion + 1};
    return MobileApplicantDossier.fromJson(json);
  }

  @override
  Future<MobileApplicantDossier> resubmit(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
  }) async {
    events.add('RESUBMIT');
    resubmissions.add((version: expectedVersion, operationKey: operationKey));
    _failMutation();
    json = {
      ...json,
      'version': expectedVersion + 1,
      'status': 'PENDING_REVIEW',
      'correctionDeadline': null,
    };
    return MobileApplicantDossier.fromJson(json);
  }

  @override
  Future<MobileApplicantDossier> reapply(
    String token,
    String requestId, {
    required int expectedVersion,
    required String operationKey,
    required String invitationCode,
  }) async {
    events.add('REAPPLY');
    reapplications.add((
      version: expectedVersion,
      operationKey: operationKey,
      invitationCode: invitationCode,
    ));
    _failMutation();
    json = documentaryJson(
      status: 'PENDING_REVIEW',
      version: 0,
      requestId: 'new-synthetic-request-id',
    );
    return MobileApplicantDossier.fromJson(json);
  }
}
