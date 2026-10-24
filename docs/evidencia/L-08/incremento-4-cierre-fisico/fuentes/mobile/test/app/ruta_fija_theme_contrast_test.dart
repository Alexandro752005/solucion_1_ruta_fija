import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_theme.dart';

void main() {
  for (final outlined in [false, true]) {
    testWidgets(
      '${outlined ? 'outlined' : 'text'} actions remain readable on app surfaces',
      (tester) async {
        final theme = RutaFijaTheme.light();
        final button = outlined
            ? OutlinedButton(onPressed: () {}, child: const Text('Consultar'))
            : TextButton(onPressed: () {}, child: const Text('Consultar'));
        await tester.pumpWidget(
          MaterialApp(
            theme: theme,
            home: Scaffold(body: Center(child: button)),
          ),
        );
        final foreground = DefaultTextStyle.of(
          tester.element(find.text('Consultar')),
        ).style.color!;
        for (final background in [
          theme.colorScheme.surface,
          theme.colorScheme.secondaryContainer,
        ]) {
          final a = foreground.computeLuminance();
          final b = background.computeLuminance();
          final contrast = a > b ? (a + .05) / (b + .05) : (b + .05) / (a + .05);
          expect(
            contrast,
            greaterThanOrEqualTo(4.5),
            reason: 'Enabled action labels must be readable on light surfaces.',
          );
        }
      },
    );
  }
}
