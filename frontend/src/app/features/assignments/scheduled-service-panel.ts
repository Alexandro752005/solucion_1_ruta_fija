import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Assignment } from '../../core/operations/operations.models';
import { ScheduledService, ScheduledServiceView, ScheduledServicesApiService } from '../../core/operations/scheduled-services-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({selector:'rf-scheduled-service-panel',imports:[FormsModule],changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<section class="management-detail"><h3>Servicio y ofertas</h3>
    @if(error()){<p role="alert">{{error()}}</p>}
    @if(view();as v){
      <p>SERVICIO-{{v.service.id}} · {{label(v.service.effectiveStatus)}} · versión {{v.service.version}}</p>
      <p>Programación: {{v.service.scheduledAt}} → {{v.service.scheduledEndAt}}. Retirar una oferta conserva este servicio.</p>
      @if(v.service.originalStartedAt){<p>Inicio original del servicio: {{v.service.originalStartedAt}}. Los relevos no lo reinician.</p>}
      @if(v.service.startOverdue){<p role="status">Inicio previsto superado sin inicio válido. El servicio sigue confirmado; ADMIN debe decidir expresamente si espera, prepara nueva oferta o cancela. No hay inicio ni cancelación automática.</p>}
      <ol>@for(a of v.offers;track a.id){<li>OFERTA-{{a.id}} · {{a.status}} @if(a.acceptedAt){· aceptación: {{a.acceptedAt}}}</li>}</ol>
      @if(canChange()){
        <button type="button" (click)="requestOffer()" [disabled]="busy()||uncertain()">Preparar nueva oferta y aceptación</button>
      }
      @if(canCancel()){
        <label>Motivo de cancelar el servicio completo, sin datos personales <input [(ngModel)]="reason" maxlength="160" [disabled]="busy()||uncertain()" /></label>
        <label><input type="checkbox" [(ngModel)]="confirmed" [disabled]="busy()||uncertain()" />Confirmo cancelar el servicio completo conservando historial y atención pendiente.</label>
        <button type="button" (click)="cancel()" [disabled]="busy()||uncertain()||!confirmed||!reason.trim()">Cancelar servicio expresamente</button>
      }
    } @else if(!assignment().serviceId){<p>Registro histórico sin servicio separado acreditado.</p>}
    <button type="button" (click)="read()" [disabled]="busy()">Consultar estado del servicio</button>
    @if(uncertain()){<p>Resultado incierto. Conserva este caso sin repetir la cancelación.</p><button type="button" (click)="reconcile()" [disabled]="busy()">Consultar operación</button>}
  </section>`})
export class ScheduledServicePanel {
  readonly assignment=input.required<Assignment>();readonly reoffer=output<ScheduledService>();
  readonly changed=output<void>();readonly blocked=output<boolean>();
  readonly externalBlocked=input(false);
  private readonly api=inject(ScheduledServicesApiService);private readonly errors=inject(ApiErrorService);
  readonly view=signal<ScheduledServiceView|null>(null);readonly error=signal<string|null>(null);
  readonly busy=signal(false);readonly uncertain=signal(false);reason='';confirmed=false;
  private pendingKey:string|null=null;private generation=0;
  constructor(){effect(()=>{this.assignment();this.view.set(null);this.read();});}
  label(status:string){return ({PENDING_RESPONSE:'Pendiente de aceptación',PENDING_REASSIGNMENT:'Pendiente de reasignación',CONFIRMED:'Confirmado, sin iniciar',IN_SERVICE:'En servicio',INTERRUPTED:'Interrumpido: continuidad pendiente de decisión',COMPLETED:'Completado',CANCELLED:'Servicio cancelado'} as Record<string,string>)[status]??status;}
  canChange(){const s=this.view()?.service;return !this.externalBlocked()&&!!s&&s.currentOfferId===this.assignment().id&&['PENDING_RESPONSE','CONFIRMED','PENDING_REASSIGNMENT'].includes(s.effectiveStatus);}
  canCancel(){const s=this.view()?.service;return !this.externalBlocked()&&!!s&&s.currentOfferId===this.assignment().id&&['PENDING_RESPONSE','CONFIRMED','PENDING_REASSIGNMENT','INTERRUPTED'].includes(s.effectiveStatus);}
  read(){const id=this.assignment().serviceId;if(!id)return;const generation=++this.generation;
    this.api.get(id).subscribe({next:v=>{if(generation===this.generation)this.view.set(v);},error:e=>{if(generation===this.generation)this.error.set(this.errors.toUiError(e).message);}});}
  requestOffer(){const s=this.view()?.service;if(s&&this.canChange()&&!this.busy()&&!this.uncertain())this.reoffer.emit(s);}
  cancel(){const s=this.view()?.service;if(!s||!this.canCancel()||this.busy()||this.uncertain()||!this.confirmed||!this.reason.trim())return;
    this.pendingKey=crypto.randomUUID();this.busy.set(true);this.blocked.emit(true);
    this.api.cancel(s.id,{expectedVersion:s.version,operationKey:this.pendingKey,reason:this.reason.trim()}).subscribe({next:v=>{
      this.view.set(v);this.busy.set(false);this.confirmed=false;this.blocked.emit(false);this.changed.emit();
    },error:(e:unknown)=>{this.busy.set(false);this.confirmed=false;this.error.set(this.errors.toUiError(e).message);
      this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422].includes(e.status));this.blocked.emit(this.uncertain());this.read();}});}
  reconcile(){if(!this.pendingKey||!this.uncertain()||this.busy())return;this.busy.set(true);
    this.api.operation(this.pendingKey).subscribe({next:r=>{this.busy.set(false);
      if(r.serviceId!==this.assignment().serviceId||r.action!=='CANCEL_SERVICE'){this.error.set('La operación no corresponde a este servicio; conserva el caso.');return;}
      this.uncertain.set(false);this.blocked.emit(false);this.read();this.changed.emit();
    },error:e=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);}});}
}
