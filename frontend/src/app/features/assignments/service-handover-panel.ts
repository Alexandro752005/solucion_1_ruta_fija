import { ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Assignment } from '../../core/operations/operations.models';
import { Handover,HandoverView,HandoverCandidate,ServiceHandoverApiService } from '../../core/operations/service-handover-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({selector:'rf-service-handover-panel',imports:[FormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
 @if(assignment().serviceId){<section class="management-detail"><h3>Relevo y participantes originales</h3>
 <button type="button" (click)="read()" [disabled]="busy()">Consultar relevo</button>
 @if(error()){<p role="alert">{{error()}}</p>}@if(uncertain()){<p>Resultado sin confirmar. Consulta la misma clave; no se repite la acción automáticamente.</p>}
 @if(view();as v){
  @if(v.service.originalStartedAt){<p>Inicio original: {{v.service.originalStartedAt}}. La planificación original se conserva.</p>}
  @for(h of v.handovers;track h.id){<article><p>Relevo {{h.id}} · {{stateLabel(h.state)}} · {{h.pointReference}} · {{h.reason}}<br/>Preparó CUENTA-{{h.preparedBy}} · {{h.preparedAt}}<br/>OFERTA-{{h.incomingAssignmentId}} · CONDUCTOR-{{h.incomingDriverId}} · UNIDAD-{{h.incomingVehicleId}}</p>
   @if(h.resolvedAt){<p>Resultado registrado {{h.resolvedAt}} · CUENTA-{{h.resolvedBy}}. @if(h.pauseSeconds!==null&&h.pauseSeconds!==undefined){Pausa original: {{h.pauseSeconds}}s.}</p>}
   @if(['PENDING_RESPONSE','ACCEPTED'].includes(h.state)){<button type="button" (click)="selectedWithdrawal.set(h)" [disabled]="blockedNow()">Preparar retiro de este relevo no asumido</button>}
  </article>}
  @if(canPrepare()){
   <h4>Preparar oferta de relevo</h4><p>Conserva ruta y tipo capturados. El entrante debe aceptar y después asumir personalmente online. La atención del saliente se revisa por separado.</p>
   <label>Inicio previsto del tramo <input id="handover-start" type="datetime-local" [(ngModel)]="from" (ngModelChange)="invalidateCandidates()" [disabled]="blockedNow()"/></label>
   <label>Fin previsto del tramo <input id="handover-end" type="datetime-local" [(ngModel)]="to" (ngModelChange)="invalidateCandidates()" [disabled]="blockedNow()"/></label>
   <button type="button" (click)="queryCandidates()" [disabled]="blockedNow()||!validWindow()">Consultar sustitutos con unidad vinculada</button>
   <label>Conductor y unidad <select id="handover-pair" [(ngModel)]="selectedPair" [disabled]="blockedNow()"><option value="">Seleccionar</option>@for(c of candidates();track c.periodId){<option [value]="c.periodId">CONDUCTOR-{{c.driverId}} · UNIDAD-{{c.vehicleId}}</option>}</select></label>
   @if(nextOffset()!==null){<button type="button" (click)="queryCandidates(true)" [disabled]="blockedNow()">Consultar más sustitutos</button>}
   <label>Punto acordado, referencia codificada sin coordenadas <input id="handover-point" [(ngModel)]="pointReference" maxlength="100" [disabled]="blockedNow()"/></label>
   <label>Motivo sin datos personales <textarea id="handover-reason" [(ngModel)]="reason" maxlength="300" [disabled]="blockedNow()"></textarea></label>
   <label><input id="handover-safety" type="checkbox" [(ngModel)]="safety" [disabled]="blockedNow()"/>Comprobé la detención segura y la transferencia de pasajeros cuando corresponde.</label>
   <button type="button" (click)="prepare()" [disabled]="blockedNow()||!safety||!selectedPair||!reason.trim()||!pointReference.trim()">Emitir oferta de relevo; no iniciar</button>
  }
  @if(selectedWithdrawal();as h){<h4>Retirar oferta de relevo sin cerrar el servicio</h4><label>Motivo <textarea [(ngModel)]="withdrawReason" maxlength="300" [disabled]="blockedNow()"></textarea></label><label><input type="checkbox" [(ngModel)]="withdrawConfirmed" [disabled]="blockedNow()"/>Confirmo retirar solo el relevo no asumido.</label><button type="button" (click)="withdraw(h)" [disabled]="blockedNow()||!withdrawConfirmed||!withdrawReason.trim()">Retirar relevo expresamente</button>}
 }
 </section>}`})
export class ServiceHandoverPanel {
 readonly assignment=input.required<Assignment>();readonly externalBlocked=input(false);readonly changed=output<void>();readonly blocked=output<boolean>();
 private readonly api=inject(ServiceHandoverApiService);private readonly errors=inject(ApiErrorService);
 readonly view=signal<HandoverView|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);readonly error=signal<string|null>(null);readonly candidates=signal<readonly HandoverCandidate[]>([]);readonly nextOffset=signal<number|null>(null);readonly selectedWithdrawal=signal<Handover|null>(null);
 from='';to='';selectedPair='';pointReference='';reason='';safety=false;withdrawReason='';withdrawConfirmed=false;private pendingKey:string|null=null;private sequence=0;private candidateSequence=0;
 constructor(){effect(()=>{this.assignment();untracked(()=>{this.sequence++;this.view.set(null);this.read();});});}
 stateLabel(s:string){return ({PENDING_RESPONSE:'Pendiente de respuesta',ACCEPTED:'Aceptado, todavía no asumido',ASSUMED:'Relevo asumido por entrante',WITHDRAWN:'Retirado expresamente',SERVICE_ENDED:'Servicio finalizado antes del relevo',REJECTED:'Rechazado',EXPIRED:'Plazo de respuesta vencido',CANCELLED:'Oferta cancelada'} as Record<string,string>)[s]??s;}
 blockedNow(){return this.busy()||this.uncertain()||this.externalBlocked();}
 canPrepare(){const v=this.view();return !!v&&v.service.currentOfferId===this.assignment().id&&['IN_SERVICE','INTERRUPTED'].includes(v.service.status)&&!v.handovers.some(h=>['PENDING_RESPONSE','ACCEPTED'].includes(h.state));}
 read(){const service=this.assignment().serviceId;if(!service||this.busy())return;const n=++this.sequence;this.api.list(service).subscribe({next:v=>{if(n!==this.sequence)return;this.view.set(v);if(this.uncertain()&&v.handovers.some(h=>h.operationKey===this.pendingKey||h.resolutionKey===this.pendingKey)){this.uncertain.set(false);this.pendingKey=null;this.blocked.emit(false);this.changed.emit();}},error:e=>{if(n===this.sequence)this.error.set(this.errors.toUiError(e).message);}});}
 private instant(value:string){const ms=Date.parse(value);return Number.isFinite(ms)?new Date(ms).toISOString():null;}
 validWindow(){const from=this.instant(this.from),to=this.instant(this.to);return !!from&&!!to&&to>from;}
 invalidateCandidates(){this.candidateSequence++;this.candidates.set([]);this.nextOffset.set(null);this.selectedPair='';}
 queryCandidates(more=false){const service=this.assignment().serviceId,from=this.instant(this.from),to=this.instant(this.to);if(!service||!from||!to||!this.validWindow()||this.blockedNow())return;const n=++this.candidateSequence;this.api.candidates(service,from,to,more?this.nextOffset()??0:0).subscribe({next:r=>{if(n!==this.candidateSequence)return;this.candidates.set(more?[...this.candidates(),...r.items]:r.items);this.nextOffset.set(r.nextOffset);},error:e=>{if(n===this.candidateSequence)this.error.set(this.errors.toUiError(e).message);}});}
 prepare(){const v=this.view(),c=this.candidates().find(x=>x.periodId===this.selectedPair),from=this.instant(this.from),to=this.instant(this.to);if(!v||!c||!from||!to||!this.validWindow()||!this.canPrepare()||this.blockedNow()||!this.safety||!this.reason.trim()||!this.pointReference.trim())return;this.pendingKey=crypto.randomUUID();this.execute(this.api.prepare(v.service.id,{operationKey:this.pendingKey,expectedVersion:v.service.version,driverId:c.driverId,vehicleId:c.vehicleId,scheduledAt:from,scheduledEndAt:to,reason:this.reason.trim(),pointReference:this.pointReference.trim(),safetyAndTransferConfirmed:true}));}
 withdraw(h:Handover){const v=this.view();if(!v||this.blockedNow()||!this.withdrawConfirmed||!this.withdrawReason.trim())return;this.pendingKey=crypto.randomUUID();this.execute(this.api.withdraw(h.id,{operationKey:this.pendingKey,expectedVersion:v.service.version,reason:this.withdrawReason.trim()}));}
 private execute(request:Observable<HandoverView>){this.sequence++;this.busy.set(true);this.blocked.emit(true);request.subscribe({next:v=>{this.view.set(v);this.busy.set(false);this.pendingKey=null;this.safety=false;this.withdrawConfirmed=false;this.selectedWithdrawal.set(null);this.blocked.emit(false);this.changed.emit();},error:(e:unknown)=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));if(!this.uncertain())this.pendingKey=null;this.blocked.emit(this.uncertain());this.read();}});}
}
