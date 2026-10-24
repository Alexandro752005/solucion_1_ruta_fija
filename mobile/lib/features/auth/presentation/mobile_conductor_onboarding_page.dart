import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/network/correlation_id.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../data/mobile_conductor_onboarding_repository.dart';
import '../domain/mobile_conductor_onboarding_models.dart';

class MobileConductorOnboardingPage extends StatefulWidget {
  const MobileConductorOnboardingPage({required this.gateway, super.key});

  final MobileConductorOnboardingGateway gateway;

  @override
  State<MobileConductorOnboardingPage> createState() =>
      _MobileConductorOnboardingPageState();
}

class _MobileConductorOnboardingPageState
    extends State<MobileConductorOnboardingPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _invitationController = TextEditingController();
  final _otpController = TextEditingController();
  final _idempotencyKey = createCorrelationId();

  MobileConductorOnboardingChallenge? _challenge;
  MobileConductorOnboardingAccepted? _accepted;
  bool _submitting = false;
  String? _error;
  String? _notice;

  @override
  void dispose() {
    _emailController.dispose();
    _nameController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _invitationController.dispose();
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    final challenge = _challenge;
    if (challenge == null) {
      await _start();
    } else {
      await _verify(challenge);
    }
  }

  Future<void> _start() async {
    setState(() {
      _submitting = true;
      _error = null;
      _notice = null;
    });
    try {
      final challenge = await widget.gateway.start(
        email: _emailController.text.trim(),
        password: _passwordController.text,
        fullName: _nameController.text.trim(),
        phone: _phoneController.text.trim().isEmpty
            ? null
            : _phoneController.text.trim(),
        invitationCode: _invitationController.text.trim(),
        idempotencyKey: _idempotencyKey,
      );
      if (!mounted) return;
      _emailController.clear();
      _nameController.clear();
      _phoneController.clear();
      _passwordController.clear();
      _confirmPasswordController.clear();
      _invitationController.clear();
      setState(() {
        _challenge = challenge;
        if (!challenge.emailSent) {
          _notice = 'No se confirmó la entrega. Puedes solicitar otro código dentro de los límites permitidos.';
        }
      });
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) {
        _passwordController.clear();
        _confirmPasswordController.clear();
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _verify(MobileConductorOnboardingChallenge challenge) async {
    setState(() {
      _submitting = true;
      _error = null;
      _notice = null;
    });
    try {
      final accepted = await widget.gateway.verifyOtp(
        verificationToken: challenge.verificationToken,
        code: _otpController.text,
      );
      if (!mounted) return;
      _otpController.clear();
      setState(() => _accepted = accepted);
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) {
        _otpController.clear();
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _resend() async {
    final challenge = _challenge;
    if (challenge == null || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
      _notice = null;
    });
    try {
      final refreshed = await widget.gateway.resendOtp(
        challenge.verificationToken,
      );
      if (!mounted) return;
      setState(() {
        _challenge = refreshed;
        _otpController.clear();
        if (!refreshed.emailSent) {
          _notice = 'No se confirmó la entrega del nuevo código.';
        }
      });
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _formatExpiry(DateTime expiresAt) {
    final local = expiresAt.toLocal();
    return '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final challenge = _challenge;
    final accepted = _accepted;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Incorporación de conductor')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    ExcludeSemantics(
                      child: Icon(
                        Icons.person_add_alt_1_outlined,
                        size: 52,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Semantics(
                      header: true,
                      child: Text(
                        accepted != null
                            ? 'Solicitud recibida'
                            : challenge != null
                            ? 'Verifica tu correo'
                            : 'Usa tu invitación',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      accepted != null
                          ? 'Tu solicitud quedó pendiente de revisión. La verificación y la invitación no habilitan una cuenta ni acceso operativo.'
                          : challenge != null
                          ? 'Ingresa el código de seis dígitos enviado al correo registrado. No compartas el código.'
                          : 'Completa los datos y el código que te entregó la organización. Tu contraseña preliminar no permitirá iniciar sesión hasta que se revise la solicitud.',
                      textAlign: TextAlign.center,
                    ),
                    if (accepted != null) ...[
                      const SizedBox(height: 24),
                      _StatusCard(
                        title: 'Pendiente de revisión',
                        detail: 'Código de solicitud: ${accepted.requestId}',
                      ),
                    ] else ...[
                      if (_notice != null) ...[
                        const SizedBox(height: 18),
                        _MessageCard(message: _notice!),
                      ],
                      if (_error != null) ...[
                        const SizedBox(height: 18),
                        _MessageCard(message: _error!, isError: true),
                      ],
                      const SizedBox(height: 24),
                      if (challenge == null) ..._registrationFields(),
                      if (challenge != null) ...[
                        Text(
                          'El código vence a las ${_formatExpiry(challenge.otpExpiresAt)} hora local.',
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          key: const Key('onboarding-otp-code'),
                          controller: _otpController,
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
                          validator: (value) => (value ?? '').length == 6
                              ? null
                              : 'Ingresa los seis dígitos del código.',
                        ),
                      ],
                      const SizedBox(height: 20),
                      FilledButton.icon(
                        key: const Key('onboarding-submit'),
                        onPressed: _submitting ? null : _submit,
                        icon: _submitting
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Icon(
                                challenge == null
                                    ? Icons.mark_email_read_outlined
                                    : Icons.verified_outlined,
                              ),
                        label: Text(
                          _submitting
                              ? 'Procesando…'
                              : challenge == null
                              ? 'Enviar código'
                              : 'Verificar y enviar solicitud',
                        ),
                      ),
                      if (challenge != null) ...[
                        const SizedBox(height: 8),
                        TextButton(
                          key: const Key('onboarding-resend'),
                          onPressed: _submitting ? null : _resend,
                          child: const Text('Reenviar código'),
                        ),
                      ],
                    ],
                    const SizedBox(height: 16),
                    TextButton(
                      key: const Key('onboarding-close'),
                      onPressed: _submitting
                          ? null
                          : () => Navigator.of(context).maybePop(),
                      child: Text(
                        accepted == null ? 'Volver al acceso' : 'Finalizar',
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _registrationFields() => [
    TextFormField(
      key: const Key('onboarding-full-name'),
      controller: _nameController,
      enabled: !_submitting,
      textCapitalization: TextCapitalization.words,
      textInputAction: TextInputAction.next,
      decoration: const InputDecoration(labelText: 'Nombre completo'),
      validator: (value) =>
          (value ?? '').trim().isEmpty ? 'Ingresa tu nombre completo.' : null,
    ),
    const SizedBox(height: 14),
    TextFormField(
      key: const Key('onboarding-email'),
      controller: _emailController,
      enabled: !_submitting,
      keyboardType: TextInputType.emailAddress,
      textInputAction: TextInputAction.next,
      autofillHints: const [AutofillHints.email],
      decoration: const InputDecoration(labelText: 'Correo electrónico'),
      validator: (value) {
        final email = value?.trim() ?? '';
        if (!email.contains('@') || email.length > 180) {
          return 'Ingresa un correo válido.';
        }
        return null;
      },
    ),
    const SizedBox(height: 14),
    TextFormField(
      key: const Key('onboarding-phone'),
      controller: _phoneController,
      enabled: !_submitting,
      keyboardType: TextInputType.phone,
      textInputAction: TextInputAction.next,
      decoration: const InputDecoration(labelText: 'Teléfono (opcional)'),
    ),
    const SizedBox(height: 14),
    TextFormField(
      key: const Key('onboarding-password'),
      controller: _passwordController,
      enabled: !_submitting,
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      textInputAction: TextInputAction.next,
      decoration: const InputDecoration(labelText: 'Contraseña preliminar'),
      validator: (value) =>
          (value ?? '').length >= 12 ? null : 'Usa al menos 12 caracteres.',
    ),
    const SizedBox(height: 14),
    TextFormField(
      key: const Key('onboarding-confirm-password'),
      controller: _confirmPasswordController,
      enabled: !_submitting,
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      textInputAction: TextInputAction.next,
      decoration: const InputDecoration(labelText: 'Confirma la contraseña'),
      validator: (value) =>
          value == _passwordController.text && (value ?? '').length >= 12
          ? null
          : 'Las contraseñas deben coincidir.',
    ),
    const SizedBox(height: 14),
    TextFormField(
      key: const Key('onboarding-invitation-code'),
      controller: _invitationController,
      enabled: !_submitting,
      keyboardType: TextInputType.number,
      textInputAction: TextInputAction.done,
      inputFormatters: [
        FilteringTextInputFormatter.digitsOnly,
        LengthLimitingTextInputFormatter(6),
      ],
      decoration: const InputDecoration(labelText: 'Código de invitación'),
      validator: (value) => RegExp(r'^\d{6}$').hasMatch(value ?? '')
          ? null
          : 'Ingresa los seis dígitos de la invitación.',
    ),
  ];
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.title, required this.detail});

  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      color: scheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.hourglass_top, color: scheme.onSecondaryContainer),
            const SizedBox(height: 8),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            SelectableText(detail, textAlign: TextAlign.center),
          ],
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
    return Semantics(
      container: true,
      liveRegion: true,
      label: '${isError ? 'Error' : 'Aviso'}: $message',
      child: ExcludeSemantics(
        child: Card(
          color: background,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text(message, style: TextStyle(color: foreground)),
          ),
        ),
      ),
    );
  }
}
