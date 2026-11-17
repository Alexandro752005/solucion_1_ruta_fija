import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/app/ruta_fija_theme.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_foreground_location.dart';
import 'package:ruta_fija_conductor/features/location/domain/mobile_service_location_controller.dart';
import 'package:ruta_fija_conductor/features/location/presentation/mobile_location_scope.dart';
import 'package:ruta_fija_conductor/features/location/presentation/mobile_map_configuration.dart';
import 'package:ruta_fija_conductor/features/location/presentation/mobile_map_page.dart';
import 'package:ruta_fija_conductor/features/profile/domain/mobile_driver_models.dart';

import '../../support/mobile_fakes.dart';

void main() {
  setUp(
    () => TestWidgetsFlutterBinding.ensureInitialized()
        .handleAppLifecycleStateChanged(AppLifecycleState.resumed),
  );

  test('tile configuration allows only public HTTPS templates without credentials or query tokens', () {
    expect(
      MobileMapConfiguration.validateTemplate(
        'https://tiles.example.test/{z}/{x}/{y}.png',
      ),
      contains('https://'),
    );
    for (final template in [
      'http://tiles.example.test/{z}/{x}/{y}.png',
      'https://secret@tiles.example.test/{z}/{x}/{y}.png',
      'https://tiles.example.test/{z}/{x}/{y}.png?key=secret',
      'https://tiles.example.test/map.png',
    ]) {
      expect(
        () => MobileMapConfiguration.validateTemplate(template),
        throwsFormatException,
      );
    }
  });

  testWidgets(
    'personal view explains third party disclosure before requesting tiles and never uploads',
    (tester) async {
      final h = MapHarness();
      await h.open(tester);
      expect(
        find.textContaining('no se comparte con la organización'),
        findsOneWidget,
      );
      expect(find.textContaining('dirección IP'), findsOneWidget);
      expect(find.byKey(const Key('map-show-tiles')), findsOneWidget);
      expect(find.byKey(const Key('map-complete-service')), findsNothing);
      expect(h.driver.uploadedLocations, isEmpty);
      expect(h.device.locationReads, 1);
      expect(find.textContaining('-12.'), findsNothing);
      expect(find.textContaining('-77.'), findsNothing);
      await h.close(tester);
    },
  );

  testWidgets(
    'a service continues to show sharing when entered through the personal tab',
    (tester) async {
      final h = MapHarness(service: true);
      await h.open(tester);
      expect(
        find.text('Compartiendo ubicación durante el servicio'),
        findsOneWidget,
      );
      expect(
        find.textContaining('no se comparte con la organización'),
        findsNothing,
      );
      expect(find.byKey(const Key('map-complete-service')), findsNothing);
      expect(h.driver.uploadedLocations, isNotEmpty);
      await h.close(tester);
    },
  );

  testWidgets(
    'context map invokes the existing completion callback once and returns only on confirmation',
    (tester) async {
      final h = MapHarness(service: true);
      var completions = 0;
      await h.open(
        tester,
        assignmentId: h.row!.id,
        onComplete: () async {
          completions++;
          return false;
        },
      );
      final uploadsBeforeAction = h.driver.uploadedLocations.length;
      await tester.ensureVisible(find.byKey(const Key('map-complete-service')));
      await tester.tap(find.byKey(const Key('map-complete-service')));
      await tester.pumpAndSettle();
      expect(completions, 1);
      expect(find.byType(MobileMapPage), findsOneWidget);
      expect(h.driver.uploadedLocations, hasLength(uploadsBeforeAction));
      await h.close(tester);
    },
  );

  testWidgets(
    'confirmed completion pops the context map and clears its visible point',
    (tester) async {
      final h = MapHarness(service: true);
      final navigator = GlobalKey<NavigatorState>();
      var completions = 0;
      h.controller.setSession(h.driver.profileValue.driverId);
      h.controller.setForeground(true);
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: navigator,
          builder: (context, child) => MobileLocationScope(
            controller: h.controller,
            device: h.device,
            child: child!,
          ),
          home: const Scaffold(body: Text('Synthetic assignment detail')),
        ),
      );
      final result = navigator.currentState!.push<bool>(
        MaterialPageRoute(
          builder: (_) => MobileMapPage(
            assignmentId: h.row!.id,
            onComplete: () async {
              completions++;
              // Simulate only the parent's already confirmed server result. The
              // real complete API and its state were tested separately via HTTP.
              h.controller.observe(
                testAssignment(
                  serviceId: h.row!.serviceId,
                  status: MobileAssignmentStatus.completed,
                  acceptedAt: h.row!.acceptedAt,
                  startedAt: h.row!.startedAt,
                  completedAt: DateTime.utc(2026, 11, 11),
                ),
              );
              return true;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byKey(const Key('map-complete-service')));
      await tester.tap(find.byKey(const Key('map-complete-service')));
      await tester.pumpAndSettle();
      expect(await result, true);
      expect(completions, 1);
      expect(find.byType(MobileMapPage), findsNothing);
      expect(find.text('Synthetic assignment detail'), findsOneWidget);
      expect(h.controller.mapPoint, isNull);
      expect(h.controller.mapVisible, false);
      expect(h.controller.hasActiveService, false);
      await h.close(tester);
    },
  );

  testWidgets(
    'denied permission is explained without automatic permission prompt or upload',
    (tester) async {
      final h = MapHarness(permission: MobileDeviceLocationPermission.denied);
      await h.open(tester);
      expect(
        find.text('Permite la ubicación mientras usas Ruta Fija.'),
        findsOneWidget,
      );
      expect(find.text('Permitir ubicación'), findsOneWidget);
      expect(h.device.permissionRequests, 0);
      expect(h.device.locationReads, 0);
      expect(h.driver.uploadedLocations, isEmpty);
      await h.close(tester);
    },
  );

  testWidgets(
    '360dp and 200 percent text keep controls readable without overflow',
    (tester) async {
      tester.view.resetPhysicalSize();
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = MapHarness();
      await h.open(tester, scale: 2);
      await tester.ensureVisible(find.byKey(const Key('map-refresh')));
      expect(tester.takeException(), isNull);
      await h.close(tester);
    },
  );

  testWidgets(
    'service GPS disabled opens location settings without asking for an already granted permission',
    (tester) async {
      final h = MapHarness(service: true);
      h.device.serviceEnabled = false;
      await h.open(tester);
      expect(h.controller.mapPhase, MapLocationPhase.gpsDisabled);
      expect(find.text('Permitir ubicación'), findsNothing);
      await tester.ensureVisible(find.text('Abrir ajustes'));
      await tester.tap(find.text('Abrir ajustes'));
      await tester.pumpAndSettle();
      expect(h.device.locationSettingsOpenCalls, 1);
      expect(h.device.appSettingsOpenCalls, 0);
      expect(h.device.permissionRequests, 0);
      expect(h.device.locationReads, 0);
      expect(h.driver.uploadedLocations, isEmpty);
      await h.close(tester);
    },
  );

  testWidgets(
    'synthetic basemap renders attribution and controls without any network tiles',
    (tester) async {
      tester.view.physicalSize = const Size(420, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.runAsync(loadCaptureFonts);
      final tile = (await tester.runAsync(syntheticTile))!;
      final tileProvider = SyntheticTileProvider(tile);
      final h = MapHarness(service: true);
      await h.open(
        tester,
        assignmentId: h.row!.id,
        onComplete: () async => false,
        tileProvider: tileProvider,
      );
      await tester.tap(find.byKey(const Key('map-show-tiles')));
      await tester.pumpAndSettle();
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pumpAndSettle();
      expect(find.text('© OpenStreetMap contributors'), findsOneWidget);
      expect(find.byTooltip('Centrar en mi ubicación'), findsOneWidget);
      expect(tileProvider.requests, greaterThan(0));
      final mapRect = tester.getRect(find.byType(FlutterMap));
      final attributionRect = tester.getRect(
        find.byKey(const Key('map-attribution')),
      );
      expect(attributionRect.top, greaterThanOrEqualTo(mapRect.top));
      expect(attributionRect.bottom, lessThanOrEqualTo(mapRect.bottom));
      expect(attributionRect.height, greaterThanOrEqualTo(48));
      expect(find.byKey(const Key('map-capture-time')), findsOneWidget);
      expect(find.byKey(const Key('map-confirmed-time')), findsOneWidget);
      expect(tester.takeException(), isNull);
      if (Platform.environment['RF_MAP_CAPTURE_ARTIFACTS'] == '1') {
        await tester.runAsync(() async {
          final boundary = tester.renderObject<RenderRepaintBoundary>(
            find.byKey(const Key('synthetic-map-capture')),
          );
          final image = await boundary.toImage(pixelRatio: 1);
          final png = await image.toByteData(format: ui.ImageByteFormat.png);
          final folder = Directory(
            '../docs/mapa-en-vivo/verificacion/mobile/screenshots',
          );
          await folder.create(recursive: true);
          await File('${folder.path}/mapa-servicio-render-sintetico.png')
              .writeAsBytes(png!.buffer.asUint8List());
          image.dispose();
        });
      }
      await h.close(tester);
    },
  );
}

final class MapHarness {
  MapHarness({
    bool service = false,
    MobileDeviceLocationPermission permission =
        MobileDeviceLocationPermission.whileInUse,
  }) {
    row = service
        ? testAssignment(
            serviceId: 'synthetic-service',
            status: MobileAssignmentStatus.enServicio,
            acceptedAt: DateTime.utc(2026, 11, 11),
            startedAt: DateTime.utc(2026, 11, 11),
          )
        : null;
    driver = FakeMobileDriverGateway(
      profile: MobileDriverProfile(
        driverId: '22222222-2222-4222-8222-222222222222',
        fullName: 'Synthetic',
        groupName: 'Synthetic',
        availabilityStatus: service
            ? MobileAvailabilityStatus.enServicio
            : MobileAvailabilityStatus.disponible,
        locationConsent: service,
        primaryVehicle: null,
      ),
    );
    device = FakeMobileDeviceLocationGateway(
      currentPermission: permission,
      nextLocation: MobileCurrentLocation(
        latitude: -12.04,
        longitude: -77.04,
        accuracyMeters: 8,
        capturedAt: DateTime.utc(2026, 11, 11),
      ),
    );
    controller = MobileServiceLocationController(
      assignments: FakeMobileAssignmentGateway(
        assignments: row == null ? [] : [row!],
      ),
      driver: driver,
      device: device,
      clock: () => DateTime.utc(2026, 11, 11),
    );
  }
  MobileAssignment? row;
  late final FakeMobileDriverGateway driver;
  late final FakeMobileDeviceLocationGateway device;
  late final MobileServiceLocationController controller;
  Future<void> open(
    WidgetTester tester, {
    String? assignmentId,
    Future<bool> Function()? onComplete,
    double scale = 1,
    TileProvider? tileProvider,
  }) async {
    controller.setSession(driver.profileValue.driverId);
    controller.setForeground(true);
    await tester.pumpWidget(
      RepaintBoundary(
        key: const Key('synthetic-map-capture'),
        child: MaterialApp(
          theme: RutaFijaTheme.light().copyWith(
            textTheme: RutaFijaTheme.light().textTheme.apply(
              fontFamily: 'Roboto',
            ),
            filledButtonTheme: FilledButtonThemeData(
              style: RutaFijaTheme.light().filledButtonTheme.style!.copyWith(
                textStyle: const WidgetStatePropertyAll(
                  TextStyle(fontFamily: 'Roboto', fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ),
          debugShowCheckedModeBanner: false,
          builder: (context, child) => MobileLocationScope(
            controller: controller,
            device: device,
            child: MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
          ),
          home: MobileMapPage(
            assignmentId: assignmentId,
            onComplete: onComplete,
            tileProvider: tileProvider,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    expect(tester.takeException(), isNull);
  }
}

/// Repeated fictional street grid, generated in memory; no remote tile request.
Future<Uint8List> syntheticTile() async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  canvas.drawRect(
    const Rect.fromLTWH(0, 0, 256, 256),
    Paint()..color = const Color(0xFFEFECE3),
  );
  final road = Paint()
    ..color = Colors.white
    ..strokeWidth = 12;
  for (var n = 32.0; n < 256; n += 64) {
    canvas.drawLine(Offset(n, 0), Offset(n, 256), road);
    canvas.drawLine(Offset(0, n), Offset(256, n), road);
  }
  final label = TextPainter(
    text: const TextSpan(
      text: 'SINTÉTICO',
      style: TextStyle(
        color: Color(0xFF777777),
        fontSize: 14,
        fontFamily: 'Roboto',
      ),
    ),
    textDirection: TextDirection.ltr,
  )..layout();
  label.paint(canvas, const Offset(65, 110));
  final picture = recorder.endRecording();
  final image = await picture.toImage(256, 256);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  picture.dispose();
  return data!.buffer.asUint8List();
}

final class SyntheticTileProvider extends TileProvider {
  SyntheticTileProvider(this.bytes);
  final Uint8List bytes;
  int requests = 0;
  @override
  ImageProvider getImage(TileCoordinates coordinates, TileLayer options) {
    requests++;
    return MemoryImage(bytes);
  }
}

/// Uses Flutter SDK fonts in renderer evidence; widget tests otherwise use
/// Ahem placeholder glyphs. No font is downloaded or copied into the product.
Future<void> loadCaptureFonts() async {
  var ancestor = File(Platform.resolvedExecutable).parent;
  Directory? fontDirectory;
  for (var depth = 0; depth < 12; depth++) {
    final candidate = Directory(
      '${ancestor.path}/bin/cache/artifacts/material_fonts',
    );
    if (await File('${candidate.path}/roboto-regular.ttf').exists()) {
      fontDirectory = candidate;
      break;
    }
    if (ancestor.parent.path == ancestor.path) break;
    ancestor = ancestor.parent;
  }
  if (fontDirectory == null) {
    throw StateError(
      'No se localizaron las fuentes del SDK para la captura sintética.',
    );
  }
  for (final item in [
    ('Roboto', ['roboto-regular.ttf', 'roboto-bold.ttf', 'roboto-medium.ttf']),
    ('MaterialIcons', ['materialicons-regular.otf']),
  ]) {
    final loader = FontLoader(item.$1);
    for (final filename in item.$2) {
      loader.addFont(
        File('${fontDirectory.path}/$filename')
            .readAsBytes()
            .then(ByteData.sublistView),
      );
    }
    await loader.load();
  }
}
