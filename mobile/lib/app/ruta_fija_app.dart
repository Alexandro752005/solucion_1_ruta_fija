import 'dart:async';

import 'package:flutter/material.dart';

import '../core/session/mobile_session_controller.dart';
import '../features/auth/presentation/mobile_login_page.dart';
import '../features/assignments/presentation/assignment_detail_page.dart';
import '../features/location/presentation/service_location_status.dart';
import 'ruta_fija_environment.dart';
import 'ruta_fija_dependencies.dart';
import 'ruta_fija_router.dart';
import 'ruta_fija_theme.dart';

class RutaFijaApp extends StatefulWidget {
  const RutaFijaApp({
    required this.environment,
    required this.dependencies,
    super.key,
  });

  final RouteFijaEnvironment environment;
  final RutaFijaDependencies dependencies;

  @override
  State<RutaFijaApp> createState() => _RutaFijaAppState();
}

class _RutaFijaAppState extends State<RutaFijaApp> with WidgetsBindingObserver {
  var _navigatorKey = GlobalKey<NavigatorState>();
  MobileSessionStatus? _navigationStatus;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.dependencies.sessionController.addListener(_syncLocationSession);
    _syncLocationSession();
    widget.dependencies.serviceLocation?.setForeground(
      WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed,
    );
    unawaited(widget.dependencies.sessionController.restore());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.dependencies.sessionController.removeListener(_syncLocationSession);
    widget.dependencies.dispose();
    super.dispose();
  }

  void _syncLocationSession() {
    final state = widget.dependencies.sessionController.state;
    if (_navigationStatus != state.status) {
      _navigationStatus = state.status;
      _navigatorKey = GlobalKey<NavigatorState>();
    }
    widget.dependencies.serviceLocation?.setSession(
      state.status == MobileSessionStatus.authenticated
          ? state.user?.driverId
          : null,
    );
    unawaited(widget.dependencies.preferences?.setUser(state.status==MobileSessionStatus.authenticated?state.user?.id:null));
  }

  void _openTrackedService() {
    final id = widget.dependencies.serviceLocation?.activeAssignmentId;
    final user = widget.dependencies.sessionController.state.user;
    if (id == null || user == null) return;
    _navigatorKey.currentState?.push(
      MaterialPageRoute<void>(
        builder: (_) => AssignmentDetailPage(
          assignmentId: id,
          driverId: user.driverId,
          driverGateway: widget.dependencies.assignmentGateway,
        ),
      ),
    );
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    widget.dependencies.serviceLocation?.setForeground(
      state == AppLifecycleState.resumed,
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([widget.dependencies.sessionController,if(widget.dependencies.preferences!=null)widget.dependencies.preferences!]),
      builder: (context, _) {
        final session = widget.dependencies.sessionController;
        final state = session.state;
        return MaterialApp(
          navigatorKey: _navigatorKey,
          key: ValueKey(state.status),
          title: 'Ruta Fija Conductor',
          debugShowCheckedModeBanner: false,
          theme: RutaFijaTheme.light(),
          builder: (context, child) {
            final media=MediaQuery.of(context);
            final factor=widget.dependencies.preferences?.value?.textSize.factor??1.0;
            final scaled=MediaQuery(data:media.copyWith(textScaler:_PreferenceTextScaler(media.textScaler,factor)),child:child!);
            return widget.dependencies.serviceLocation == null
              ? scaled
              : ServiceLocationStatus(
                  controller: widget.dependencies.serviceLocation!,
                  onOpenService: _openTrackedService,
                  child: scaled,
                );
          },
          onGenerateRoute: (settings) => RutaFijaRouter.onGenerateRoute(
            settings,
            preferences:widget.dependencies.preferences,
            reportGateway:widget.dependencies.reportGateway,
            reportFiles:widget.dependencies.reportFiles,
            environment: widget.environment,
            sessionController: session,
            driverGateway: widget.dependencies.driverGateway,
            assignmentGateway: widget.dependencies.assignmentGateway,
            incidentGateway: widget.dependencies.incidentGateway,
            announcementGateway: widget.dependencies.announcementGateway,
            noticeGateway: widget.dependencies.noticeGateway,
            deviceLocationGateway: widget.dependencies.deviceLocationGateway,
            qualificationGateway: widget.dependencies.qualificationGateway,
          ),
          home: switch (state.status) {
            MobileSessionStatus.restoring => const _SessionRestorePage(),
            MobileSessionStatus.unauthenticated => MobileLoginPage(
              sessionController: session,
              conductorRecoveryGateway:
                  widget.dependencies.conductorRecoveryGateway,
              conductorOnboardingGateway:
                  widget.dependencies.conductorOnboardingGateway,
              applicantAccessGateway:
                  widget.dependencies.applicantAccessGateway,
            ),
            MobileSessionStatus.emailVerificationRequired => MobileLoginPage(
              sessionController: session,
              conductorRecoveryGateway:
                  widget.dependencies.conductorRecoveryGateway,
              conductorOnboardingGateway:
                  widget.dependencies.conductorOnboardingGateway,
              applicantAccessGateway:
                  widget.dependencies.applicantAccessGateway,
            ),
            MobileSessionStatus.authenticated => RutaFijaRouter.home(
              preferences:widget.dependencies.preferences,
              reportGateway:widget.dependencies.reportGateway,
              reportFiles:widget.dependencies.reportFiles,
              environment: widget.environment,
              sessionController: session,
              driverGateway: widget.dependencies.driverGateway,
              assignmentGateway: widget.dependencies.assignmentGateway,
              incidentGateway: widget.dependencies.incidentGateway,
              announcementGateway: widget.dependencies.announcementGateway,
              noticeGateway: widget.dependencies.noticeGateway,
              qualificationGateway: widget.dependencies.qualificationGateway,
            ),
          },
        );
      },
    );
  }
}

/// Applies a presentation preference after the system's accessible (possibly
/// nonlinear) scaling, never replacing or capping that system setting.
final class _PreferenceTextScaler extends TextScaler {
  const _PreferenceTextScaler(this.system,this.factor);
  final TextScaler system;
  final double factor;
  @override double scale(double fontSize)=>system.scale(fontSize)*factor;
  @override double get textScaleFactor=>scale(1);
}

class _SessionRestorePage extends StatelessWidget {
  const _SessionRestorePage();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Semantics(
        container: true,
        liveRegion: true,
        label: 'Verificando sesión protegida',
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ExcludeSemantics(child: CircularProgressIndicator()),
              SizedBox(height: 16),
              Text('Verificando sesión protegida…'),
            ],
          ),
        ),
      ),
    );
  }
}
