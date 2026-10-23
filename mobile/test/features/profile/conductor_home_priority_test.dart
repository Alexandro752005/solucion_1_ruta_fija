import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_fija_conductor/features/profile/presentation/conductor_home_page.dart';
import 'package:ruta_fija_conductor/features/assignments/domain/mobile_assignment_models.dart';
import '../../support/mobile_fakes.dart';

void main(){
  testWidgets('accepted commitment precedes independent pending offer without sending commands',(tester)async{
    final assignments=FakeMobileAssignmentGateway(assignments:[testAssignment(id:'11111111-1111-4111-8111-111111111111'),testAssignment(id:'33333333-3333-4333-8333-333333333333',status:MobileAssignmentStatus.scheduled,acceptedAt:DateTime.utc(2026,9,24),reservedAt:DateTime.utc(2026,9,24))]);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:ConductorHomePage(driverGateway:FakeMobileDriverGateway(),assignmentGateway:assignments))));await tester.pumpAndSettle();
    expect(find.text('Próximo servicio aceptado'),findsOneWidget);expect(find.textContaining('Plazo de respuesta:'),findsOneWidget);
    expect(tester.getTopLeft(find.byKey(const Key('home-accepted-commitment'))).dy,lessThan(tester.getTopLeft(find.byKey(const Key('home-pending-offer'))).dy));expect(assignments.sentCommands,isEmpty);
  });
  testWidgets('pending offer never becomes accepted commitment merely by appearing',(tester)async{
    final gateway=FakeMobileAssignmentGateway();await tester.pumpWidget(MaterialApp(home:Scaffold(body:ConductorHomePage(driverGateway:FakeMobileDriverGateway(),assignmentGateway:gateway))));await tester.pumpAndSettle();
    expect(find.textContaining('No hay un servicio aceptado'),findsOneWidget);expect(find.byKey(const Key('home-open-accepted')),findsNothing);expect(gateway.sentCommands,isEmpty);
  });
  testWidgets('in-service participation has priority over future accepted service',(tester)async{
    final gateway=FakeMobileAssignmentGateway(assignments:[testAssignment(id:'11111111-1111-4111-8111-111111111111',status:MobileAssignmentStatus.scheduled,acceptedAt:DateTime.utc(2026,9,24),reservedAt:DateTime.utc(2026,9,24)),testAssignment(id:'33333333-3333-4333-8333-333333333333',status:MobileAssignmentStatus.enServicio,acceptedAt:DateTime.utc(2026,9,24),startedAt:DateTime.utc(2026,9,24))]);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:ConductorHomePage(driverGateway:FakeMobileDriverGateway(),assignmentGateway:gateway))));await tester.pumpAndSettle();
    expect(find.text('Servicio en curso'),findsOneWidget);expect(find.text('Continuar servicio'),findsOneWidget);expect(find.text('Próximo servicio aceptado'),findsNothing);
  });
}
