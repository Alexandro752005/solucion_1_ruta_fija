import 'package:flutter/material.dart';

import '../../../core/presentation/mobile_error_message.dart';
import '../../../core/session/mobile_session_controller.dart';
import '../../auth/domain/mobile_auth_models.dart';
import '../data/mobile_qualification_repository.dart';
import '../domain/mobile_qualification.dart';
import 'mobile_qualification_page.dart';

/// Applied to every named operational route, including location. A direct path
/// cannot bypass the limited presentation; APIs independently enforce access.
class MobileQualificationGate extends StatefulWidget {
  const MobileQualificationGate({
    required this.gateway,
    required this.sessionController,
    required this.operationalBuilder,
    super.key,
  });
  final MobileQualificationGateway gateway;
  final MobileSessionController sessionController;
  final WidgetBuilder operationalBuilder;

  @override
  State<MobileQualificationGate> createState() =>
      _MobileQualificationGateState();
}

class _MobileQualificationGateState extends State<MobileQualificationGate> {
  MobileQualification? _state;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final state = await widget.gateway.qualification();
      if (mounted) setState(() => _state = state);
    } catch (error) {
      if (mounted) setState(() => _error = mobileErrorMessage(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = _state;
    if (_loading || _error != null || state == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Comprobando incorporación')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_loading) const CircularProgressIndicator(),
                if (_error != null) ...[
                  Semantics(liveRegion: true, child: Text(_error!)),
                  OutlinedButton(
                    onPressed: _load,
                    child: const Text('Consultar estado'),
                  ),
                  TextButton(
                    onPressed: () async {
                      try {
                        await widget.sessionController.logout();
                      } catch (error) {
                        if (mounted) {
                          setState(() => _error = mobileErrorMessage(error));
                        }
                      }
                    },
                    child: const Text('Cerrar sesión'),
                  ),
                ],
              ],
            ),
          ),
        ),
      );
    }
    if (!state.operationalEnabled ||
        widget.sessionController.state.accessScope !=
            MobileAccessScope.operations) {
      return MobileQualificationPage(
        gateway: widget.gateway,
        sessionController: widget.sessionController,
        initialState: state,
        onSessionRefreshed: _load,
      );
    }
    return widget.operationalBuilder(context);
  }
}
