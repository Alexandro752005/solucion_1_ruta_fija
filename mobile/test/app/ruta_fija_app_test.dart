import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_app.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_environment.dart';

void main() {
  final environment = RouteFijaEnvironment.fromApiBaseUrl(
    'https://api.example.test/api/v1',
  );

  testWidgets('shows the honest M1 foundation without operational mock data', (
    tester,
  ) async {
    await tester.pumpWidget(RutaFijaApp(environment: environment));

    expect(find.text('Base Flutter preparada'), findsOneWidget);
    expect(find.text('F4.1 · M1 · 8/80 puntos'), findsOneWidget);
    expect(find.textContaining('no muestran datos simulados'), findsOneWidget);
    expect(find.text('https://api.example.test/api/v1'), findsOneWidget);
  });

  testWidgets('navigates to a pending module without pretending it is built', (
    tester,
  ) async {
    await tester.pumpWidget(RutaFijaApp(environment: environment));

    await tester.tap(find.byKey(const Key('nav-assignments')));
    await tester.pumpAndSettle();

    expect(find.text('M4 — Asignaciones'), findsOneWidget);
    expect(find.text('Pendiente de construcción'), findsOneWidget);
  });
}
