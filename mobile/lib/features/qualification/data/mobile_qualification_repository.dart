import '../../../core/network/mobile_api_client.dart';
import '../domain/mobile_qualification.dart';

abstract interface class MobileQualificationGateway {
  Future<MobileQualification> qualification();
  Future<List<MobilePersonalRequirement>> personalCatalog();
  Future<MobileQualification> requestUnit({
    required int expectedVersion,
    required String operationKey,
    required String kind,
    required String reference,
    String? vehicleId,
  });
  Future<MobileQualification> presentDocument({
    required int expectedVersion,
    required String operationKey,
    required MobileQualificationDocumentInput document,
  });
}

final class MobileQualificationRepository
    implements MobileQualificationGateway {
  MobileQualificationRepository(this.api);
  final MobileApiClient api;
  static const _path = 'mobile/qualification';

  @override
  Future<MobileQualification> qualification() async =>
      MobileQualification.fromJson(await api.getObject(_path));

  @override
  Future<List<MobilePersonalRequirement>> personalCatalog() async =>
      (await api.getList('$_path/personal-catalog'))
          .map(MobilePersonalRequirement.fromJson)
          .toList(growable: false);

  @override
  Future<MobileQualification> requestUnit({
    required int expectedVersion,
    required String operationKey,
    required String kind,
    required String reference,
    String? vehicleId,
  }) async => MobileQualification.fromJson(
    await api.postObject('$_path/unit-requests', {
      'expectedVersion': expectedVersion,
      'operationKey': operationKey,
      'reason': 'Solicitud de revisión de unidad desde la app.',
      'vehicleId': vehicleId,
      'kind': kind,
      'reference': reference,
    }),
  );

  @override
  Future<MobileQualification> presentDocument({
    required int expectedVersion,
    required String operationKey,
    required MobileQualificationDocumentInput document,
  }) async => MobileQualification.fromJson(
    await api.postObject('$_path/documents', {
      'expectedVersion': expectedVersion,
      'operationKey': operationKey,
      'reason': 'Datos estructurados presentados para revisión ADMIN.',
      'documents': [document.toJson()],
    }),
  );
}
