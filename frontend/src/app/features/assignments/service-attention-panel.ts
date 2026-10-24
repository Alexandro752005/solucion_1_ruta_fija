import { ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked } from '@angular/core';
import { FormControl,FormGroup,ReactiveFormsModule,Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Assignment } from '../../core/operations/operations.models';
import { ServiceAttentionApiService,ServiceAttention,SafetyReason,AttentionResolution } from '../../core/operations/service-attention-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({selector:'rf-service-attention-panel',imports:[ReactiveFormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
 @if(assignment().serviceId){<section class="management-detail"><h3>Interrupción y atención segura</h3>
  @if(error()){<p role="alert">{{error()}}</p>}
  <button type="button" (click)="read()" [disabled]="busy()">Consultar atención y decisiones</button>
  @if(uncertain()){<p>Acción sin confirmación. Consulte el estado; no se repite la operación automáticamente.</p>}
  @if(assignment().status==='EN_SERVICIO'&&!alreadyInterrupted()){
    <form [formGroup]="interruptForm" (ngSubmit)="interrupt()"><h4>Registrar una interrupción comunicada y comprobada</h4>
     <p>ADMIN registra su comprobación, sin suplantar al conductor ni declarar cumplimiento. El tramo y GPS se detienen; los recursos quedan bloqueados para atención separada.</p>
     <label>Causa <select formControlName="reason"><option value="">Seleccionar causa</option><option value="ACCIDENT_RISK">Accidente con riesgo</option><option value="UNSAFE_BREAKDOWN">Avería que impide circular con seguridad</option><option value="IMPOSSIBLE_SERVICE">Imposibilidad real de prestar el recorrido</option></select></label>
     <label>Motivo, sin datos personales ni coordenadas <textarea formControlName="detail" maxlength="300"></textarea></label>
     <label><input type="checkbox" formControlName="safeStopConfirmed"/>Comprobé la comunicación y la detención segura.</label>
     <button type="submit" [disabled]="busy()||uncertain()||externalBlocked()||interruptForm.invalid">Registrar interrupción comprobada</button>
    </form>
  }
  @for(r of rows();track r.id){<article><h4>{{reasonLabel(r.reason)}}</h4>
    <p>{{r.interruptedAt}} · {{r.source==='CONDUCTOR'?'Declaración propia del conductor':'Comprobación ADMIN comunicada'}} · CUENTA-{{r.recordedBy}}<br/>{{r.detail}}</p>
    <p>Tramo {{r.segmentId}} · Incidencia {{r.incidentId}}. Detención confirmada al registrar: {{r.safeStopConfirmed?'Sí':'Todavía no'}}.</p>
    <p>CONDUCTOR-{{r.driverId}}: {{r.driverPending?'Atención pendiente':'Atención revisada'}}. UNIDAD-{{r.vehicleId}}: {{r.vehiclePending?'Sigue bloqueada para atención':'Revisión de seguridad registrada'}}.</p>
    <ul>@for(d of r.resolutions;track d.id){<li>{{d.recordedAt}} · {{dispositionLabel(d.disposition)}} · CUENTA-{{d.actorId}} · {{d.reason}}</li>}</ul>
    @if(r.driverPending||r.vehiclePending){<button type="button" (click)="select(r)" [disabled]="busy()||uncertain()||externalBlocked()">Revisar recursos de esta atención</button>}
  </article>}
  @if(selected();as r){<form [formGroup]="resolutionForm" (ngSubmit)="resolve()"><h4>Revisión separada de recursos</h4>
    <p>Revisar atención no sustituye documentos, vínculo, disponibilidad ni retorno de mantenimiento. Mantener retirada una unidad conserva su bloqueo; declararla revisada tampoco reactiva una unidad inactiva.</p>
    @if(r.driverPending){<label><input type="checkbox" formControlName="releaseDriver"/>Comprobé que puede terminar la atención del conductor.</label>}
    @if(r.vehiclePending){<label>Decisión de unidad <select formControlName="vehicleDecision"><option value="NO_CHANGE">Conservar atención sin cambio</option>@if(!wasWithdrawn(r)){<option value="KEEP_WITHDRAWN">Mantener retirada y bloqueada</option>}<option value="READY">Revisión de seguridad y documentos completada</option></select></label>}
    <label>Motivo de la revisión, sin datos personales ni coordenadas <textarea formControlName="reason" maxlength="300"></textarea></label>
    <label><input type="checkbox" formControlName="safetyAndTransferConfirmed"/>Comprobé la seguridad y la transferencia de pasajeros cuando corresponde.</label>
    <button type="submit" [disabled]="busy()||uncertain()||externalBlocked()||resolutionForm.invalid||!hasDecision()">Registrar revisión de recursos</button>
    <button type="button" (click)="selected.set(null)" [disabled]="busy()||uncertain()">Cerrar revisión</button>
  </form>}
 </section>}`})
export class ServiceAttentionPanel {
  readonly assignment=input.required<Assignment>();readonly externalBlocked=input(false);readonly blocked=output<boolean>();readonly changed=output<void>();
  private readonly api=inject(ServiceAttentionApiService);private readonly errors=inject(ApiErrorService);
  readonly rows=signal<readonly ServiceAttention[]>([]);readonly selected=signal<ServiceAttention|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);readonly error=signal<string|null>(null);
  readonly interruptForm=new FormGroup({reason:new FormControl<SafetyReason|''>('',{nonNullable:true,validators:Validators.required}),detail:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(300)]}),safeStopConfirmed:new FormControl(false,{nonNullable:true,validators:Validators.requiredTrue})});
  readonly resolutionForm=new FormGroup({releaseDriver:new FormControl(false,{nonNullable:true}),vehicleDecision:new FormControl<AttentionResolution['vehicleDecision']>('NO_CHANGE',{nonNullable:true}),reason:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(300)]}),safetyAndTransferConfirmed:new FormControl(false,{nonNullable:true,validators:Validators.requiredTrue})});
  private key:string|null=null;private sequence=0;
  constructor(){effect(()=>{this.assignment();untracked(()=>{this.sequence++;this.rows.set([]);this.read();});});}
  alreadyInterrupted(){return this.rows().some(r=>r.assignmentId===this.assignment().id);}
  wasWithdrawn(r:ServiceAttention){return r.resolutions.some(d=>d.disposition==='WITHDRAWN');}
  hasDecision(){return this.resolutionForm.controls.releaseDriver.value||this.resolutionForm.controls.vehicleDecision.value!=='NO_CHANGE';}
  reasonLabel(s:string){return ({ACCIDENT_RISK:'Accidente con riesgo',UNSAFE_BREAKDOWN:'Avería insegura',IMPOSSIBLE_SERVICE:'Imposibilidad real de servicio'} as Record<string,string>)[s]??'Interrupción registrada';}
  dispositionLabel(s:string){return ({ELIGIBILITY_RECHECKED:'Atención del conductor revisada; habilitación comprobada',WITHDRAWN:'Unidad retirada; atención pendiente',READY:'Revisión de seguridad de la unidad registrada'} as Record<string,string>)[s]??'Decisión registrada';}
  read(){const id=this.assignment().serviceId;if(!id||this.busy())return;const sequence=++this.sequence;this.api.list(id).subscribe({next:rows=>{if(sequence!==this.sequence)return;this.rows.set(rows);
    if(this.uncertain()&&rows.some(r=>r.operationKey===this.key||r.resolutions.some(d=>d.operationKey===this.key))){this.uncertain.set(false);this.key=null;this.selected.set(null);this.error.set(null);this.blocked.emit(false);this.changed.emit();}
  },error:e=>{if(sequence===this.sequence)this.error.set(this.errors.toUiError(e).message);}});}
  select(r:ServiceAttention){if(this.busy()||this.uncertain()||this.externalBlocked())return;this.selected.set(r);this.resolutionForm.reset({releaseDriver:false,vehicleDecision:'NO_CHANGE',reason:'',safetyAndTransferConfirmed:false});}
  interrupt(){if(this.busy()||this.uncertain()||this.externalBlocked()||this.interruptForm.invalid||this.alreadyInterrupted()||this.assignment().status!=='EN_SERVICIO')return;
    const f=this.interruptForm.getRawValue();if(!f.reason||!f.detail.trim())return;const key=globalThis.crypto.randomUUID();this.key=key;
    this.execute(this.api.interrupt(this.assignment().id,{operationKey:key,expectedVersion:this.assignment().version,reason:f.reason,detail:f.detail.trim(),safeStopConfirmed:true,verificationReference:'REPORTED_SAFE_STOP_CHECKED'}));}
  resolve(){const r=this.selected();if(!r||this.busy()||this.uncertain()||this.externalBlocked()||this.resolutionForm.invalid||!this.hasDecision())return;
    const f=this.resolutionForm.getRawValue();if(!f.reason.trim())return;const key=globalThis.crypto.randomUUID();this.key=key;
    this.execute(this.api.resolve(r.id,{...f,operationKey:key,expectedVersion:r.version,reason:f.reason.trim(),safetyAndTransferConfirmed:true,verificationReference:'SAFETY_RESOURCE_REVIEW_CHECKED'}));}
  private execute(request:Observable<ServiceAttention>){this.sequence++;this.busy.set(true);this.blocked.emit(true);request.subscribe({next:()=>{this.busy.set(false);this.key=null;this.selected.set(null);this.error.set(null);this.interruptForm.reset({reason:'',detail:'',safeStopConfirmed:false});this.blocked.emit(false);this.read();this.changed.emit();},error:(e:unknown)=>{
    this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));if(!this.uncertain())this.key=null;this.blocked.emit(this.uncertain());this.read();
  }});}
}
