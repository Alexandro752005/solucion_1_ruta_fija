import 'package:flutter/foundation.dart';

enum MobileTextSize {
  normal('NORMAL', 'Normal', 1),
  large('LARGE', 'Grande', 1.15),
  extraLarge('EXTRA_LARGE', 'Muy grande', 1.3);
  const MobileTextSize(this.apiValue, this.label, this.factor);
  final String apiValue;
  final String label;
  final double factor;
}

final class MobilePreferences {
  const MobilePreferences({required this.version, required this.optionalNoticeAlerts, required this.textSize});
  final int version;
  final bool optionalNoticeAlerts;
  final MobileTextSize textSize;
  factory MobilePreferences.fromJson(Map<String,dynamic> json) {
    if(json['version'] is! int || (json['version'] as int)<0 || json['optionalNoticeAlerts'] is! bool || json['mandatoryNoticesAlwaysVisible']!=true) {
      throw const FormatException('Preferencias inválidas.');
    }
    return MobilePreferences(version:json['version'] as int,optionalNoticeAlerts:json['optionalNoticeAlerts'] as bool,
      textSize:MobileTextSize.values.firstWhere((v)=>v.apiValue==json['textSize']));
  }
}

abstract interface class MobilePreferencesGateway {
  Future<MobilePreferences> read();
  Future<MobilePreferences> update({required int version,required bool optionalNoticeAlerts,required MobileTextSize textSize});
}

/// In-memory presentation state only. Writes succeed only after server response.
final class MobilePreferencesController extends ChangeNotifier {
  MobilePreferencesController(this.gateway);
  final MobilePreferencesGateway gateway;
  MobilePreferences? value;
  String? _user;
  int _generation=0;
  Future<void> setUser(String? user) async {
    if(_user==user)return;
    _user=user; value=null; final generation=++_generation;
    if(user==null){notifyListeners();return;}
    try { final next=await gateway.read(); if(generation==_generation){value=next;notifyListeners();} } catch (_) {
      // Defaults remain accessible. Profile renders explicit load/retry errors.
    }
  }
  Future<MobilePreferences> load() async {
    final generation=_generation; final next=await gateway.read();
    if(generation==_generation){value=next;notifyListeners();}
    return next;
  }
  Future<MobilePreferences> save({required int version,required bool optionalNoticeAlerts,required MobileTextSize textSize}) async {
    final generation=_generation;
    final next=await gateway.update(version:version,optionalNoticeAlerts:optionalNoticeAlerts,textSize:textSize);
    if(generation==_generation){value=next;notifyListeners();}
    return next;
  }
  @override
  void dispose(){_generation++;_user=null;value=null;super.dispose();}
}
