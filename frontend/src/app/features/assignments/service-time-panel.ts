import { ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked } from '@angular/core';
import { FormArray,FormControl,FormGroup,ReactiveFormsModule,Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Assignment } from '../../core/operations/operations.models';
import { EffectiveServiceTime,ServiceTimeApiService,ServiceTimeHistory } from '../../core/operations/service-time-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({selector:'rf-service-time-panel',imports:[ReactiveFormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
  @if(assignment().serviceId){<section class="management-detail"><h3>Tiempos originales y correcciones</h3>
    @if(error()){<p role="alert">{{error()}}</p>}
    <button type="button" (click)="load()" [disabled]="busy()">Consultar tiempos e historial</button>
    @if(uncertain()){<p>Resultado sin confirmación. Consulte el historial antes de otra corrección.</p>}
    @if(history();as h){
      <ol>@for(s of h.segments;track s.segmentId){<li>Tramo {{s.ordinal}} · CONDUCTOR-{{s.driverId}} · UNIDAD-{{s.vehicleId}}<br/>
        Original: {{s.originalStartedAt}} → {{s.originalEndedAt||'Sin cierre'}}<br/>Vigente para revisión: {{s.startedAt}} → {{s.endedAt||'Sin cierre'}}</li>}</ol>
      @for(c of h.corrections;track c.id){<article><p>Corrección {{c.revision}} · CUENTA-{{c.actorId}} · {{c.correctedAt}} · {{c.reason}}</p>
        <p>Corroboración original: {{resultLabel(c.originalTerminalResult)}}. Revisión temporal: {{resultLabel(c.effectiveTerminalResult)}}. Los puntos y tiempos originales se conservan.</p>
        <ul>@for(s of c.segments;track s.segmentId){<li>{{s.beforeStartedAt}} → {{s.beforeEndedAt}}; corregido: {{s.startedAt}} → {{s.endedAt}}</li>}</ul>
      </article>}
      <h4>Solicitudes del conductor</h4>
      @for(r of h.requests;track r.id){<p>CUENTA-{{r.requestedBy}} · {{r.requestedAt}} · {{r.reason}} · {{r.correctionId?'Atendida mediante corrección':'Pendiente de revisión'}}</p>}
      @if((h.status==='COMPLETED'||h.status==='CANCELLED')&&h.segments.length){<form [formGroup]="form" (ngSubmit)="submit()">
        <p>Corrección ADMIN de todos los tramos finalizados. Horas de entrada en la zona local de este navegador. No modifica GPS ni confirma una acción física que nunca registró el servidor.</p>
        <div formArrayName="times">@for(row of form.controls.times.controls;track row;let i=$index){<fieldset [formGroupName]="i"><legend>Tramo {{h.segments[i].ordinal}}</legend>
          <label>Inicio corregido <input type="datetime-local" step="0.001" formControlName="start"/></label>
          <label>Fin corregido <input type="datetime-local" step="0.001" formControlName="end"/></label>
        </fieldset>}</div>
        <label>Motivo sin datos personales <textarea maxlength="300" formControlName="reason"></textarea></label>
        <label>Solicitud atendida <select formControlName="requestId"><option value="">Corrección sin solicitud previa</option>@for(r of h.requests;track r.id){@if(!r.correctionId){<option [value]="r.id">SOLICITUD-{{r.id}}</option>}}</select></label>
        <label><input type="checkbox" formControlName="confirmed"/>Revisé tiempos y motivo; los originales se conservarán.</label>
        <button type="submit" [disabled]="busy()||uncertain()||externalBlocked()||form.invalid">Registrar corrección</button>
      </form>}
    }
  </section>}`})
export class ServiceTimePanel {
  readonly assignment=input.required<Assignment>();readonly externalBlocked=input(false);readonly blocked=output<boolean>();readonly changed=output<void>();
  private readonly api=inject(ServiceTimeApiService);private readonly errors=inject(ApiErrorService);
  readonly history=signal<ServiceTimeHistory|null>(null);readonly error=signal<string|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);
  readonly form=new FormGroup({times:new FormArray<FormGroup<{start:FormControl<string>;end:FormControl<string>}>>([]),reason:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(300)]}),requestId:new FormControl('',{nonNullable:true}),confirmed:new FormControl(false,{nonNullable:true,validators:Validators.requiredTrue})});
  private key:string|null=null;private sequence=0;
  resultLabel(value:string|null){return value===null?'No registrada':({CORROBORATED:'Llegada al terminal corroborada',DISCREPANCY:'Ubicación válida fuera del terminal: requiere revisión',NOT_CORROBORATED_TERMINAL_UNCONFIGURED:'Terminal sin ubicación configurada',NOT_CORROBORATED_NO_POINT:'Sin punto válido para corroborar',NOT_CORROBORATED_STALE_OR_IMPRECISE:'Punto fuera del intervalo o sin precisión suficiente',NOT_CORROBORATED_OUTSIDE_CORRECTED_SEGMENT:'Punto anterior al tramo corregido',NOT_REASSESSABLE_ORIGINAL_POINT_EXPIRED_OR_ABSENT:'No se puede volver a contrastar: punto original ausente o vencido'} as Record<string,string>)[value]??'Resultado no disponible';}
  constructor(){effect(()=>{this.assignment();untracked(()=>{this.sequence++;this.history.set(null);this.error.set(null);this.load();});});}
  local(value:string){const d=new Date(value);d.setMinutes(d.getMinutes()-d.getTimezoneOffset());return d.toISOString().slice(0,23);}
  private reset(h:ServiceTimeHistory){this.form.controls.times.clear();for(const s of h.segments)this.form.controls.times.push(new FormGroup({start:new FormControl(this.local(s.startedAt),{nonNullable:true,validators:Validators.required}),end:new FormControl(s.endedAt?this.local(s.endedAt):'',{nonNullable:true,validators:Validators.required})}));this.form.controls.reason.reset('');this.form.controls.requestId.reset('');this.form.controls.confirmed.reset(false);}
  load(){const id=this.assignment().serviceId;if(!id||this.busy())return;const sequence=++this.sequence;this.api.read(id).subscribe({next:h=>{
    if(sequence!==this.sequence)return;this.history.set(h);
    if(this.uncertain()&&h.corrections.some(c=>c.operationKey===this.key)){this.uncertain.set(false);this.key=null;this.error.set(null);this.blocked.emit(false);this.changed.emit();}
    if(!this.uncertain())this.reset(h);
  },error:e=>{if(sequence===this.sequence)this.error.set(this.errors.toUiError(e).message);}});}
  private instant(value:string,original:string){return value===this.local(original)?original:new Date(value).toISOString();}
  submit(){const h=this.history();if(!h||!['COMPLETED','CANCELLED'].includes(h.status)||!h.segments.length||h.segments.some(s=>s.endedAt===null)||this.busy()||this.uncertain()||this.externalBlocked()||this.form.invalid)return;
    let segments:readonly {segmentId:string;startedAt:string;endedAt:string}[];
    try{segments=h.segments.map((s:EffectiveServiceTime,i:number)=>({segmentId:s.segmentId,startedAt:this.instant(this.form.controls.times.at(i).controls.start.value,s.startedAt),endedAt:this.instant(this.form.controls.times.at(i).controls.end.value,s.endedAt!)}));}catch{this.error.set('Comprueba los tiempos de todos los tramos.');return;}
    const reason=this.form.controls.reason.value.trim();if(!reason)return;
    this.key=globalThis.crypto.randomUUID();const key=this.key;this.sequence++;this.busy.set(true);this.blocked.emit(true);
    const requestId=this.form.controls.requestId.value||undefined;
    this.api.correct(h.serviceId,{operationKey:key,expectedVersion:h.serviceVersion,reason,requestId,segments}).subscribe({next:v=>{this.history.set(v);this.busy.set(false);this.key=null;this.error.set(null);this.reset(v);this.blocked.emit(false);this.changed.emit();},error:(e:unknown)=>{
      this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));if(!this.uncertain())this.key=null;this.blocked.emit(this.uncertain());this.load();
    }});
  }
}
