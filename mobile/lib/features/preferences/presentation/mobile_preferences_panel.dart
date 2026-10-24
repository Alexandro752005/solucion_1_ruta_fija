import 'package:flutter/material.dart';
import '../../../core/presentation/mobile_error_message.dart';
import '../domain/mobile_preferences.dart';

class MobilePreferencesPanel extends StatefulWidget {
  const MobilePreferencesPanel({required this.controller,super.key});
  final MobilePreferencesController controller;
  @override State<MobilePreferencesPanel> createState()=>_MobilePreferencesPanelState();
}
class _MobilePreferencesPanelState extends State<MobilePreferencesPanel> {
  MobilePreferences? _value;
  Object? _error;
  bool _busy=true;
  bool _optional=true;
  MobileTextSize _size=MobileTextSize.normal;
  @override void initState(){super.initState();_load();}
  Future<void> _load() async {
    setState(()=>_busy=true);
    try { final value=await widget.controller.load();if(mounted)setState((){_value=value;_optional=value.optionalNoticeAlerts;_size=value.textSize;_error=null;}); }
    catch(error){if(mounted)setState(()=>_error=error);}
    finally {if(mounted)setState(()=>_busy=false);}
  }
  Future<void> _save() async {
    final value=_value;if(value==null||_busy)return;
    setState((){_busy=true;_error=null;});
    try {final updated=await widget.controller.save(version:value.version,optionalNoticeAlerts:_optional,textSize:_size);
      if(mounted){setState(()=>_value=updated);ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Preferencias confirmadas por el servidor.')));}}
    catch(error){if(mounted)setState(()=>_error=error);}
    finally {if(mounted)setState(()=>_busy=false);}
  }
  @override Widget build(BuildContext context)=>Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    Text('Preferencias',style:Theme.of(context).textTheme.titleMedium),
    const Text('La bandeja, las respuestas de servicio y los avisos de seguridad siempre permanecen disponibles.'),
    if(_busy)const LinearProgressIndicator(),
    if(_error!=null)...[Text(mobileErrorMessage(_error!)),TextButton(onPressed:_busy?null:_load,child:const Text('Consultar estado de preferencias'))],
    if(_value!=null)...[
      SwitchListTile(key:const Key('preference-optional-notices'),contentPadding:EdgeInsets.zero,title:const Text('Sugerencias visuales opcionales en Inicio'),value:_optional,onChanged:_busy?null:(v)=>setState(()=>_optional=v)),
      DropdownButtonFormField<MobileTextSize>(key:const Key('preference-text-size'),initialValue:_size,decoration:const InputDecoration(labelText:'Tamaño de texto'),items:[for(final size in MobileTextSize.values)DropdownMenuItem(value:size,child:Text(size.label))],onChanged:_busy?null:(v){if(v!=null)setState(()=>_size=v);}),
      const Text('Se conserva también el tamaño accesible configurado en Android.'),
      FilledButton(onPressed:_busy||_error!=null?null:_save,child:const Text('Guardar preferencias')),
    ],
  ])));
}
