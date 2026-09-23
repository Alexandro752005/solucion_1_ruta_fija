import 'package:flutter/material.dart';

final class RutaFijaTheme {
  RutaFijaTheme._();

  static const _yellow = Color(0xFFFFC400);
  static const _ink = Color(0xFF111111);
  static const _surface = Color(0xFFFFFDF8);

  static ThemeData light() {
    final colors =
        ColorScheme.fromSeed(
          seedColor: _yellow,
          brightness: Brightness.light,
        ).copyWith(
          primary: _yellow,
          onPrimary: _ink,
          secondary: _ink,
          onSecondary: Colors.white,
          surface: _surface,
          onSurface: _ink,
        );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colors,
      scaffoldBackgroundColor: _surface,
      appBarTheme: const AppBarTheme(
        backgroundColor: _ink,
        foregroundColor: Colors.white,
        centerTitle: false,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: _yellow.withValues(alpha: 0.9),
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => TextStyle(
            color: states.contains(WidgetState.selected)
                ? _ink
                : const Color(0xFF595959),
            fontWeight: states.contains(WidgetState.selected)
                ? FontWeight.w700
                : FontWeight.w500,
          ),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: _yellow,
          foregroundColor: _ink,
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}
