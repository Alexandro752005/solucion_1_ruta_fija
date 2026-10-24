import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_access_token_provider.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_exception.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_access_repository.dart';
import 'package:ruta_fija_conductor/features/auth/domain/mobile_applicant_documentary_models.dart';

import '../../support/documentary_fakes.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );
  const token = 'oa1.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ';
  const key = '11111111-1111-4111-8111-111111111111';
  const document = MobileApplicantDocumentInput(
    requirementId: 'CON-01',
    fields: {'documentType': 'DNI', 'documentNumber': '12345678'},
    expiryMode: 'DATED',
    validUntil: '2028-01-31T23:59:59-05:00',
    applicability: 'CONFIRMED',
    condition: null,
    sourceReference: 'Fuente sintética',
  );

  test('documentary commands use purpose header and exact pending/versioned contract without sessions', () async {
    final provider = _OperationalTokenProvider();
    final sent = <http.Request>[];
    final repository = MobileApplicantAccessRepository(
      MobileApiClient(
        environment: environment,
        accessTokenProvider: provider,
        httpClient: MockClient((request) async {
          sent.add(request);
          expect(request.headers['x-onboarding-token'], token);
          expect(request.headers['authorization'], isNull);
          expect(request.headers['cache-control'], 'no-store');
          expect(request.url.query, isEmpty);
          expect(request.url.toString(), isNot(contains(token)));
          return http.Response(jsonEncode(documentaryJson()), 200);
        }),
      ),
    );

    final dossier = await repository.dossier(token, 'synthetic-request-id');
    await repository.saveDocument(
      token,
      dossier.requestId,
      expectedVersion: dossier.version,
      operationKey: key,
      modality: 'TAXI',
      document: document,
    );
    await repository.resubmit(
      token,
      dossier.requestId,
      expectedVersion: 8,
      operationKey: key,
    );
    await repository.reapply(
      token,
      dossier.requestId,
      expectedVersion: 9,
      operationKey: key,
      invitationCode: '004207',
    );

    expect(sent.map((request) => request.method), [
      'GET',
      'PUT',
      'POST',
      'POST',
    ]);
    expect(sent.map((request) => request.url.path), [
      '/api/v1/onboarding/applicant/requests/synthetic-request-id/documentary-status',
      '/api/v1/onboarding/applicant/requests/synthetic-request-id/documents',
      '/api/v1/onboarding/applicant/requests/synthetic-request-id/resubmit',
      '/api/v1/onboarding/applicant/requests/synthetic-request-id/reapply',
    ]);
    final save = jsonDecode(sent[1].body) as Map;
    expect(save['expectedVersion'], 7);
    expect(save['operationKey'], key);
    expect(save['modality'], 'TAXI');
    expect((save['documents'] as List).single, {
      'requirementId': 'CON-01',
      'fields': {'documentType': 'DNI', 'documentNumber': '12345678'},
      'expiryMode': 'DATED',
      'validUntil': '2028-01-31T23:59:59-05:00',
      'applicability': 'CONFIRMED',
      'condition': null,
      'sourceReference': 'Fuente sintética',
      'result': 'PENDING',
      'reviewReference': null,
      'observation': null,
    });
    expect(jsonDecode(sent[2].body), {
      'expectedVersion': 8,
      'operationKey': key,
      'reason': 'Reenvío para revisión tras subsanación.',
    });
    expect(jsonDecode(sent[3].body), {
      'expectedVersion': 9,
      'operationKey': key,
      'invitationCode': '004207',
    });
    expect(provider.refreshCalls, 0);
    expect(provider.invalidations, 0);
  });

  test('409 is surfaced once without retry or operational refresh', () async {
    final provider = _OperationalTokenProvider();
    var calls = 0;
    final repository = MobileApplicantAccessRepository(
      MobileApiClient(
        environment: environment,
        accessTokenProvider: provider,
        httpClient: MockClient((request) async {
          calls++;
          return http.Response(
            jsonEncode({
              'code': 'RESOURCE_VERSION_CONFLICT',
              'message': 'La solicitud cambió.',
            }),
            409,
          );
        }),
      ),
    );
    await expectLater(
      repository.saveDocument(
        token,
        'synthetic-request-id',
        expectedVersion: 7,
        operationKey: key,
        modality: 'TAXI',
        document: document,
      ),
      throwsA(
        isA<MobileApiException>().having(
          (error) => error.status,
          'status',
          409,
        ),
      ),
    );
    expect(calls, 1);
    expect(provider.refreshCalls, 0);
  });

  test('unknown and no-expiry input keep validUntil null and never mark compliance', () {
    for (final mode in ['UNKNOWN', 'NO_EXPIRY']) {
      final json = MobileApplicantDocumentInput(
        requirementId: 'CON-01',
        fields: {},
        expiryMode: mode,
        validUntil: null,
        applicability: 'POR_CONTRASTAR',
      ).toJson();
      expect(json['validUntil'], isNull);
      expect(json['result'], 'PENDING');
      expect(json['reviewReference'], isNull);
      expect(json['observation'], isNull);
    }
  });
}

final class _OperationalTokenProvider implements MobileAccessTokenProvider {
  int refreshCalls = 0;
  int invalidations = 0;
  @override
  String get accessToken => 'must-not-be-used';
  @override
  Future<bool> refreshAccessToken() async {
    refreshCalls++;
    return true;
  }

  @override
  Future<void> invalidateSession() async {
    invalidations++;
  }
}
