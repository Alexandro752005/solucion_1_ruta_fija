// Opt-in actual Flutter pages and production HTTP repositories against the test backend.
// Mail and device token storage are doubles. No HTTP MockClient or fake gateway.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';
import 'package:ruta_fija_conductor/core/network/mobile_api_client.dart';
import 'package:ruta_fija_conductor/core/session/mobile_secure_token_store.dart';
import 'package:ruta_fija_conductor/core/session/mobile_session_controller.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_access_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_applicant_documentary_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_auth_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_onboarding_repository.dart';
import 'package:ruta_fija_conductor/features/auth/data/mobile_conductor_recovery_repository.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_applicant_requests_page.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_conductor_onboarding_page.dart';
import 'package:ruta_fija_conductor/features/auth/presentation/mobile_login_page.dart';

final class _VolatileDeviceStore implements MobileRefreshTokenStore {
  String? value;
  @override Future<String?> readRefreshToken() async => value;
  @override Future<void> writeRefreshToken(String value) async { this.value = value; }
  @override Future<void> clear() async { value = null; }
}

void main() {
  final base = Platform.environment['RF_L01_CONNECTED_BASE'];
  final key = Platform.environment['RF_L01_CONNECTED_CONTROL'];
  final stage = Platform.environment['RF_L01_CONNECTED_STAGE'];
  final enabled = Platform.environment['RF_L01_CONNECTED_CLIENTS'] == 'true' &&
      base != null && base.startsWith('http://127.0.0.1:') && key != null;

  testWidgets('L01 actual UI plus real HTTP and PostgreSQL', (tester) async {
    final previousOverrides = HttpOverrides.current;
    HttpOverrides.global = null; // Flutter's default network-denying test override is not an HTTP double here.
    final client = http.Client();
    tester.view.physicalSize = const Size(1400, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      client.close(); HttpOverrides.global = previousOverrides;
      tester.view.resetPhysicalSize(); tester.view.resetDevicePixelRatio();
    });
    Future<Map<String, dynamic>> control(String path) async {
      final response = await client.get(Uri.parse('$base/__l01/$path'), headers: {'X-Synthetic-Control': key!});
      if (response.statusCode != 200) throw StateError('Synthetic control unavailable');
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    final state = await tester.runAsync(() => control('state'));
    if (state == null) throw StateError('Synthetic fixture unavailable');
    final api = MobileApiClient(httpClient: client, environment: RouteFijaEnvironment.fromApiBaseUrl('$base/api/v1'));
    Future<void> until(Finder finder) async {
      for (var i = 0; i < 160; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 80)));
        await tester.pump(const Duration(milliseconds: 20));
        if (finder.evaluate().isNotEmpty) return;
      }
      throw StateError('Connected UI condition timed out');
    }
    Future<void> tap(Finder finder) async {
      await tester.ensureVisible(finder);
      await tester.runAsync(() async { await tester.tap(finder); await Future<void>.delayed(const Duration(milliseconds: 50)); });
      await tester.pump();
    }
    Future<void> field(String name, String value) async {
      final finder = find.byKey(Key(name));
      await tester.ensureVisible(finder); await tester.enterText(finder, value); await tester.pump();
    }
    Future<void> select(Finder finder, String label) async {
      await tap(finder); await tester.pumpAndSettle();
      await tap(find.text(label).last); await tester.pumpAndSettle();
    }

    if (stage == 'register' || stage == 'register-rejection') {
      final rejection = stage == 'register-rejection';
      await tester.pumpWidget(MaterialApp(home: MobileConductorOnboardingPage(gateway: MobileConductorOnboardingRepository(api))));
      await field('onboarding-full-name', 'SYNTHETIC APPLICANT');
      await field('onboarding-email', state[rejection ? 'rejectionEmail' : 'applicantEmail'] as String);
      await field('onboarding-password', state['password'] as String);
      await field('onboarding-confirm-password', state['password'] as String);
      await field('onboarding-invitation-code', state[rejection ? 'rejectionInvitationCode' : 'invitationCode'] as String);
      await tap(find.byKey(const Key('onboarding-submit')));
      await until(find.byKey(const Key('onboarding-otp-code')));
      final message = await tester.runAsync(() => control(rejection ? 'otp?rejection' : 'otp?applicant'));
      await field('onboarding-otp-code', message!['code'] as String);
      await tap(find.byKey(const Key('onboarding-submit'))); await until(find.text('Solicitud recibida'));
      expect(find.textContaining('no habilitan una cuenta ni acceso operativo'), findsOneWidget);
      // No operative login is attempted/created by this UI.
      // ignore: avoid_print
      print('CONNECTED_FLUTTER_${rejection ? 'REJECTION_CASE' : 'REGISTRATION'}_PENDING_REVIEW=PASS_REAL_HTTP');
    } else if (stage == 'correct') {
      await tester.pumpWidget(MaterialApp(home: MobileApplicantRequestsPage(
        gateway: MobileApplicantAccessRepository(api), documentaryGateway: MobileApplicantDocumentaryRepository(api))));
      await field('applicant-email', state['applicantEmail'] as String);
      await field('applicant-password', state['password'] as String);
      await tap(find.byKey(const Key('applicant-login'))); await until(find.text('Consultar expediente y subsanar'));
      expect(find.textContaining(state['applicantEmail'] as String), findsNothing);
      await tap(find.text('Consultar expediente y subsanar')); await until(find.textContaining('Catálogo L01-CHICLAYO'));
      await select(find.byWidgetPredicate((widget) => widget.key is ValueKey &&
          (widget.key as ValueKey).value.toString().startsWith('document-modality-')), 'Taxi');
      await select(find.byKey(const ValueKey('document-requirement-null')), 'CON-01: Identidad del postulante');
      for (final entry in {'documentType':'DNI','documentNumber':'50000001','fullName':'SYNTHETIC APPLICANT','issuer':'SYNTHETIC'}.entries) {
        await field('document-field-${entry.key}', entry.value);
      }
      await select(find.byKey(const ValueKey('document-expiry-UNKNOWN')), 'Sin vencimiento acreditado');
      await field('document-source','SYNTHETIC declaration for visual review');
      await tap(find.byKey(const Key('document-save')));
      await until(find.textContaining('Datos enviados al servidor.'));
      await tap(find.byKey(const Key('document-resubmit')));
      await until(find.textContaining('Pendiente de revisión'));
      // Explicit UI logout runs network timers in runAsync before disposal.
      await tap(find.byKey(const Key('applicant-dossier-back')));
      await until(find.byKey(const Key('applicant-logout')));
      await tap(find.byKey(const Key('applicant-logout')));
      await until(find.text('Consulta cerrada.'));
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 200)));
      // ignore: avoid_print
      print('CONNECTED_FLUTTER_SCOPED_CORRECTION_RESUBMISSION=PASS_REAL_HTTP');
    } else if (stage == 'recover') {
      final store = _VolatileDeviceStore();
      final session = MobileSessionController(authGateway: MobileAuthRepository(api), tokenStore: store);
      await session.restore();
      await tester.pumpWidget(MaterialApp(home: MobileLoginPage(sessionController: session,
        conductorRecoveryGateway: MobileConductorRecoveryRepository(api), conductorOnboardingGateway: MobileConductorOnboardingRepository(api),
        applicantAccessGateway: MobileApplicantAccessRepository(api))));
      await tap(find.byKey(const Key('mobile-recovery-open')));
      await field('recovery-id',state['recoveryId'] as String);await field('recovery-token',state['recoveryToken'] as String);
      final message = await tester.runAsync(() => control('otp?recovered'));
      await field('recovery-otp', message!['code'] as String);
      await tap(find.byKey(const Key('mobile-login-submit')));await until(find.byKey(const Key('recovery-new-password')));
      await field('recovery-new-password','${state['password']}-Recovered');
      await field('recovery-confirm-password','${state['password']}-Recovered');
      await tap(find.byKey(const Key('mobile-login-submit')));await until(find.textContaining('Contraseña actualizada.'));
      expect(store.value,isNull); // Recovery does not silently create an operative session.
      await tester.pumpWidget(const SizedBox.shrink());session.dispose();
      // ignore: avoid_print
      print('CONNECTED_FLUTTER_ASSISTED_RECOVERY_RESET=PASS_REAL_HTTP_NO_SESSION');
    } else { throw StateError('Unknown connected stage'); }
  }, skip: !enabled);
}
