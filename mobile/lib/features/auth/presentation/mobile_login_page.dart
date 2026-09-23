import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';

class MobileLoginPage extends StatefulWidget {
  const MobileLoginPage({required this.sessionController, super.key});

  final MobileSessionController sessionController;

  @override
  State<MobileLoginPage> createState() => _MobileLoginPageState();
}

class _MobileLoginPageState extends State<MobileLoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _submitting) {
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.login(
        email: _emailController.text,
        password: _passwordController.text,
      );
    } catch (error) {
      if (mounted) {
        setState(() => _error = mobileErrorMessage(error));
      }
    } finally {
      _passwordController.clear();
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final notice = widget.sessionController.state.notice;
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
                      Icon(
                        Icons.route_outlined,
                        size: 56,
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(height: 20),
                      Text(
                        'Acceso del conductor',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Inicie sesión con la cuenta vinculada a su conductor. '
                        'La aplicación no usa la sesión web del CRM.',
                        textAlign: TextAlign.center,
                      ),
                      if (notice != null) ...[
                        const SizedBox(height: 20),
                        _MessageCard(message: notice),
                      ],
                      if (_error != null) ...[
                        const SizedBox(height: 20),
                        _MessageCard(message: _error!, isError: true),
                      ],
                      const SizedBox(height: 24),
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
                          _submitting ? 'Validando…' : 'Iniciar sesión',
                        ),
                      ),
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
    return Semantics(
      liveRegion: true,
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
    );
  }
}
