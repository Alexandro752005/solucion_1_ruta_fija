import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
import '../data/mobile_conductor_onboarding_repository.dart';
import '../data/mobile_applicant_access_repository.dart';
import '../data/mobile_conductor_recovery_repository.dart';
import 'mobile_conductor_onboarding_page.dart';
import 'mobile_applicant_requests_page.dart';

class MobileLoginPage extends StatefulWidget {
  const MobileLoginPage({
    required this.sessionController,
    required this.conductorRecoveryGateway,
    required this.conductorOnboardingGateway,
    required this.applicantAccessGateway,
    super.key,
  });

  final MobileSessionController sessionController;
  final MobileConductorRecoveryGateway conductorRecoveryGateway;
  final MobileConductorOnboardingGateway conductorOnboardingGateway;
  final MobileApplicantAccessGateway applicantAccessGateway;

  @override
  State<MobileLoginPage> createState() => _MobileLoginPageState();
}

class _MobileLoginPageState extends State<MobileLoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _codeController = TextEditingController();
  final _recoveryIdController = TextEditingController();
  final _recoveryTokenController = TextEditingController();
  final _recoveryCodeController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _submitting = false;
  bool _recoveryMode = false;
  String? _passwordResetToken;
  String? _recoveryNotice;
  String? _error;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _codeController.dispose();
    _recoveryIdController.dispose();
    _recoveryTokenController.dispose();
    _recoveryCodeController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    _passwordResetToken = null;
    super.dispose();
  }

  Future<void> _submit() async {
    if (_recoveryMode) {
      await _submitRecovery();
      return;
    }
    final verificationPending =
        widget.sessionController.state.status ==
        MobileSessionStatus.emailVerificationRequired;
    if (!_formKey.currentState!.validate() || _submitting) {
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      if (verificationPending) {
        await widget.sessionController.verifyEmailOtp(_codeController.text);
      } else {
        await widget.sessionController.login(
          email: _emailController.text,
          password: _passwordController.text,
        );
      }
    } catch (error) {
      if (mounted) {
        setState(() => _error = mobileErrorMessage(error));
      }
    } finally {
      if (mounted) {
        _emailController.clear();
        _passwordController.clear();
        _codeController.clear();
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _submitRecovery() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
      _recoveryNotice = null;
    });
    try {
      if (_passwordResetToken == null) {
        final result = await widget.conductorRecoveryGateway.verifyOtp(
          recoveryId: _recoveryIdController.text.trim(),
          verificationToken: _recoveryTokenController.text.trim(),
          code: _recoveryCodeController.text,
        );
        if (!mounted) return;
        _recoveryCodeController.clear();
        if (result.passwordResetRequired) {
          setState(() {
            _passwordResetToken = result.passwordResetToken;
            _recoveryNotice = 'Correo verificado. Elija una contraseña nueva para completar la recuperación.';
          });
        } else {
          _clearRecoveryFields();
          setState(() {
            _recoveryMode = false;
            _recoveryNotice = 'Correo verificado. Puede iniciar sesión con su contraseña vigente.';
          });
        }
      } else {
        await widget.conductorRecoveryGateway.resetPassword(
          recoveryId: _recoveryIdController.text.trim(),
          passwordResetToken: _passwordResetToken!,
          newPassword: _newPasswordController.text,
        );
        if (!mounted) return;
        _clearRecoveryFields();
        setState(() {
          _passwordResetToken = null;
          _recoveryMode = false;
          _recoveryNotice =
              'Contraseña actualizada. Inicie sesión con la nueva contraseña.';
        });
      }
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) {
        _recoveryCodeController.clear();
        _newPasswordController.clear();
        _confirmPasswordController.clear();
        setState(() => _submitting = false);
      }
    }
  }

  void _beginRecovery() {
    _emailController.clear();
    _passwordController.clear();
    _codeController.clear();
    setState(() {
      _recoveryMode = true;
      _error = null;
      _recoveryNotice = null;
      _passwordResetToken = null;
    });
  }

  Future<void> _openOnboarding() async {
    _emailController.clear();
    _passwordController.clear();
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => MobileConductorOnboardingPage(
          gateway: widget.conductorOnboardingGateway,
        ),
      ),
    );
  }

  Future<void> _openApplicantRequests() async {
    _emailController.clear();
    _passwordController.clear();
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) =>
            MobileApplicantRequestsPage(gateway: widget.applicantAccessGateway),
      ),
    );
  }

  void _cancelRecovery() {
    if (_submitting) return;
    _clearRecoveryFields();
    setState(() {
      _recoveryMode = false;
      _passwordResetToken = null;
      _error = null;
      _recoveryNotice = null;
    });
  }

  void _clearRecoveryFields() {
    _recoveryIdController.clear();
    _recoveryTokenController.clear();
    _recoveryCodeController.clear();
    _newPasswordController.clear();
    _confirmPasswordController.clear();
  }

  Future<void> _resendOtp() async {
    if (_submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.resendEmailOtp();
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _cancelOtp() async {
    await widget.sessionController.cancelEmailOtp();
    _codeController.clear();
    if (mounted) setState(() => _error = null);
  }

  String _formatExpiry(DateTime expiresAt) {
    final local = expiresAt.toLocal();
    return '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.sessionController.state;
    final notice = state.notice;
    final verificationPending =
        state.status == MobileSessionStatus.emailVerificationRequired;
    final passwordResetPending = _passwordResetToken != null;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Ruta Fija Conductor')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: AutofillGroup(
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      ExcludeSemantics(
                        child: Icon(
                          Icons.route_outlined,
                          size: 56,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                      const SizedBox(height: 20),
                      Semantics(
                        header: true,
                        child: Text(
                          'Acceso del conductor',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.headlineSmall?.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        verificationPending
                            ? 'Ingrese el código de seis dígitos enviado al correo registrado.'
                            : _recoveryMode
                            ? 'Use el identificador y token entregados por el ADMIN autorizado. El código se envía al correo nuevo.'
                            : 'Inicie sesión con la cuenta vinculada a su conductor. '
                                  'La aplicación no usa la sesión web del CRM.',
                        textAlign: TextAlign.center,
                      ),
                      if (verificationPending &&
                          state.verificationEmailSent == false) ...[
                        const SizedBox(height: 12),
                        const Text(
                          'No se confirmó el envío del código. Puede solicitar '
                          'otro dentro del límite permitido.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                      if (verificationPending &&
                          state.verificationExpiresAt != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          'El código vence a las '
                          '${_formatExpiry(state.verificationExpiresAt!)} '
                          'hora local.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                      if (notice != null) ...[
                        const SizedBox(height: 20),
                        _MessageCard(message: notice),
                      ],
                      if (_recoveryNotice != null) ...[
                        const SizedBox(height: 20),
                        _MessageCard(message: _recoveryNotice!),
                      ],
                      if (_error != null) ...[
                        const SizedBox(height: 20),
                        _MessageCard(message: _error!, isError: true),
                      ],
                      const SizedBox(height: 24),
                      if (verificationPending) ...[
                        TextFormField(
                          key: const Key('mobile-otp-code'),
                          controller: _codeController,
                          enabled: !_submitting,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          autofillHints: const [AutofillHints.oneTimeCode],
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly,
                            LengthLimitingTextInputFormatter(6),
                          ],
                          onFieldSubmitted: (_) => _submit(),
                          decoration: const InputDecoration(
                            labelText: 'Código de verificación',
                            prefixIcon: Icon(Icons.pin_outlined),
                          ),
                          validator: (value) {
                            if ((value ?? '').length != 6) {
                              return 'Ingrese los seis dígitos del código.';
                            }
                            return null;
                          },
                        ),
                      ] else if (_recoveryMode && !passwordResetPending) ...[
                        TextFormField(
                          key: const Key('recovery-id'),
                          controller: _recoveryIdController,
                          enabled: !_submitting,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Identificador de recuperación',
                          ),
                          validator: (value) => (value ?? '').trim().isEmpty
                              ? 'Ingrese el identificador autorizado.'
                              : null,
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          key: const Key('recovery-token'),
                          controller: _recoveryTokenController,
                          enabled: !_submitting,
                          obscureText: true,
                          autocorrect: false,
                          enableSuggestions: false,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Token temporal entregado por el ADMIN',
                          ),
                          validator: (value) => (value ?? '').trim().isEmpty
                              ? 'Ingrese el token temporal.'
                              : null,
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          key: const Key('recovery-otp'),
                          controller: _recoveryCodeController,
                          enabled: !_submitting,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          autofillHints: const [AutofillHints.oneTimeCode],
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly,
                            LengthLimitingTextInputFormatter(6),
                          ],
                          decoration: const InputDecoration(
                            labelText: 'Código enviado al correo nuevo',
                            prefixIcon: Icon(Icons.pin_outlined),
                          ),
                          validator: (value) => (value ?? '').length == 6
                              ? null
                              : 'Ingrese los seis dígitos del código.',
                        ),
                      ] else if (_recoveryMode) ...[
                        TextFormField(
                          key: const Key('recovery-new-password'),
                          controller: _newPasswordController,
                          enabled: !_submitting,
                          obscureText: true,
                          enableSuggestions: false,
                          autocorrect: false,
                          decoration: const InputDecoration(
                            labelText: 'Nueva contraseña',
                          ),
                          validator: (value) => (value ?? '').length >= 12
                              ? null
                              : 'Use al menos 12 caracteres.',
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          key: const Key('recovery-confirm-password'),
                          controller: _confirmPasswordController,
                          enabled: !_submitting,
                          obscureText: true,
                          enableSuggestions: false,
                          autocorrect: false,
                          textInputAction: TextInputAction.done,
                          decoration: const InputDecoration(
                            labelText: 'Confirme la nueva contraseña',
                          ),
                          validator: (value) =>
                              value == _newPasswordController.text &&
                                  (value ?? '').length >= 12
                              ? null
                              : 'Las contraseñas deben coincidir.',
                        ),
                      ] else ...[
                        TextFormField(
                          controller: _emailController,
                          enabled: !_submitting,
                          autofillHints: const [
                            AutofillHints.username,
                            AutofillHints.email,
                          ],
                          keyboardType: TextInputType.emailAddress,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Correo electrónico',
                            prefixIcon: Icon(Icons.alternate_email_outlined),
                          ),
                          validator: (value) {
                            final email = value?.trim() ?? '';
                            if (email.isEmpty) {
                              return 'Ingrese su correo electrónico.';
                            }
                            if (!email.contains('@') || email.length > 180) {
                              return 'Ingrese un correo válido.';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _passwordController,
                          enabled: !_submitting,
                          autofillHints: const [AutofillHints.password],
                          obscureText: true,
                          enableSuggestions: false,
                          autocorrect: false,
                          onFieldSubmitted: (_) => _submit(),
                          decoration: const InputDecoration(
                            labelText: 'Contraseña',
                            prefixIcon: Icon(Icons.lock_outline),
                          ),
                          validator: (value) {
                            if ((value ?? '').isEmpty) {
                              return 'Ingrese su contraseña.';
                            }
                            return null;
                          },
                        ),
                      ],
                      const SizedBox(height: 24),
                      FilledButton.icon(
                        key: const Key('mobile-login-submit'),
                        onPressed: _submitting ? null : _submit,
                        icon: _submitting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.login),
                        label: Text(
                          _submitting
                              ? 'Validando…'
                              : verificationPending
                              ? 'Verificar código'
                              : _recoveryMode
                              ? passwordResetPending
                                    ? 'Actualizar contraseña'
                                    : 'Verificar recuperación'
                              : 'Iniciar sesión',
                        ),
                      ),
                      if (verificationPending) ...[
                        const SizedBox(height: 8),
                        TextButton(
                          key: const Key('mobile-otp-resend'),
                          onPressed: _submitting ? null : _resendOtp,
                          child: const Text('Reenviar código'),
                        ),
                        TextButton(
                          key: const Key('mobile-otp-cancel'),
                          onPressed: _submitting ? null : _cancelOtp,
                          child: const Text('Volver al inicio de sesión'),
                        ),
                      ],
                      if (!verificationPending && !_recoveryMode) ...[
                        const SizedBox(height: 8),
                        TextButton(
                          key: const Key('mobile-recovery-open'),
                          onPressed: _submitting ? null : _beginRecovery,
                          child: const Text(
                            'Completar recuperación autorizada',
                          ),
                        ),
                        TextButton(
                          key: const Key('mobile-onboarding-open'),
                          onPressed: _submitting ? null : _openOnboarding,
                          child: const Text('Tengo una invitación'),
                        ),
                        TextButton(
                          key: const Key('mobile-applicant-open'),
                          onPressed: _submitting
                              ? null
                              : _openApplicantRequests,
                          child: const Text('Consultar mi solicitud'),
                        ),
                      ],
                      if (_recoveryMode) ...[
                        const SizedBox(height: 8),
                        TextButton(
                          key: const Key('mobile-recovery-cancel'),
                          onPressed: _submitting ? null : _cancelRecovery,
                          child: const Text('Volver al inicio de sesión'),
                        ),
                      ],
                      const SizedBox(height: 16),
                      const Text(
                        'El token de renovación se conserva únicamente en el '
                        'almacenamiento protegido del dispositivo.',
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({required this.message, this.isError = false});

  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final background = isError
        ? scheme.errorContainer
        : scheme.secondaryContainer;
    final foreground = isError
        ? scheme.onErrorContainer
        : scheme.onSecondaryContainer;
    final kind = isError ? 'Error de acceso' : 'Aviso de acceso';
    return Semantics(
      container: true,
      label: '$kind: $message',
      liveRegion: true,
      child: ExcludeSemantics(
        child: Card(
          color: background,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  isError ? Icons.error_outline : Icons.info_outline,
                  color: foreground,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(message, style: TextStyle(color: foreground)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
