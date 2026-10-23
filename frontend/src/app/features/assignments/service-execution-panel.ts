import { ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked } from '@angular/core';
import { FormControl,FormGroup,ReactiveFormsModule,Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { Assignment } from '../../core/operations/operations.models';
import { ServiceExecutionApiService,ExecutionSegment,GpsAccessRequest } from '../../core/operations/service-execution-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({selector:'rf-service-execution-panel',imports:[ReactiveFormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
  <section class="management-detail"><h3>Tramos de ejecución</h3>
    @if(error()){<p role="alert">{{error()}}</p>}
    @if(!assignment().serviceId){<p>Registro histórico sin tramos reconstruidos.</p>}@else{
      <button type="button" (click)="load()" [disabled]="busy()">Consultar tramos y solicitudes GPS</button>
      @if(!segments().length){<p>No hay tramos originales registrados para este servicio.</p>}
      <ol>@for(s of segments();track s.id){<li>Tramo {{s.ordinal}} · CONDUCTOR-{{s.driverId}} · UNIDAD-{{s.vehicleId}}<br/>
        {{s.startedAt}} → {{s.endedAt||'En ejecución'}} · {{s.endKind||'Sin cierre'}}. Horas originales del servidor.</li>}</ol>
      <h4>Solicitudes verificadas de puntos propios</h4>
      <p>Revisión restringida por ADMIN de esta organización, con verificación de identidad y reautenticación. Plazo operativo48h; los puntos vencen a los siete días, también los incidentales. La solicitud no congela ni amplía retención.</p>
      @if(uncertain()){<p>Resultado sin confirmación. Consulte el estado: no se repite automáticamente la revisión.</p>}
      @for(r of requests();track r.id){<article><p>CONDUCTOR-{{r.driverId}} · {{label(r.effectiveStatus)}} · {{r.fromAt}} → {{r.toAt}}<br/>Revisión prevista hasta {{r.reviewDueAt}}</p>
        @if(r.status==='PENDING'){<button type="button" (click)="select(r)" [disabled]="busy()||uncertain()||externalBlocked()">Revisión restringida</button>}
        @if(r.grantExpiresAt){<p>Autorización al solicitante hasta {{r.grantExpiresAt}}. Este panel no entrega su trayectoria.</p>}
      </article>}
      <div><button type="button" (click)="page(-50)" [disabled]="busy()||offset()===0">Anteriores</button><button type="button" (click)="page(50)" [disabled]="busy()||requests().length<50">Siguientes</button></div>
      @if(selected();as r){<form [formGroup]="form" (ngSubmit)="review(true)">
        <p>Comprobar correspondencia de la cuenta solicitante mediante la revisión de identidad autorizada. No escribir documentos, nombres o teléfonos en esta vista.</p>
        <label><input type="checkbox" formControlName="identityConfirmed"/>He comprobado la correspondencia del solicitante en la revisión restringida.</label>
        <label>Contraseña actual ADMIN <input type="password" autocomplete="current-password" formControlName="password" maxlength="200"/></label>
        <button type="submit" [disabled]="busy()||uncertain()||externalBlocked()||form.invalid">Autorizar solo el alcance solicitado</button>
        <button type="button" (click)="review(false)" [disabled]="busy()||uncertain()||externalBlocked()||form.invalid">No autorizar solicitud</button>
        <button type="button" (click)="cancelReview()" [disabled]="busy()||uncertain()">Cerrar revisión</button>
      </form>}
    }
  </section>`})
export class ServiceExecutionPanel {
  readonly assignment=input.required<Assignment>();readonly externalBlocked=input(false);readonly blocked=output<boolean>();
  private readonly api=inject(ServiceExecutionApiService);private readonly errors=inject(ApiErrorService);
  readonly segments=signal<readonly ExecutionSegment[]>([]);readonly requests=signal<readonly GpsAccessRequest[]>([]);
  readonly error=signal<string|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);readonly selected=signal<GpsAccessRequest|null>(null);readonly offset=signal(0);
  readonly form=new FormGroup({identityConfirmed:new FormControl(false,{nonNullable:true,validators:Validators.requiredTrue}),password:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(200)]})});
  private sequence=0;
  constructor(){effect(()=>{this.assignment();untracked(()=>{this.segments.set([]);this.requests.set([]);this.cancelReview();this.offset.set(0);this.load();});});}
  label(s:string){return ({PENDING:'Pendiente de revisión',OVERDUE:'Revisión fuera del plazo operativo: requiere atención',APPROVED:'Acceso autorizado',NO_DATA:'Sin puntos disponibles',DENIED:'No autorizada',EXPIRED:'Autorización vencida'} as Record<string,string>)[s]??s;}
  load(clearError=true){const id=this.assignment().serviceId;if(!id||this.busy())return;const sequence=++this.sequence;
    forkJoin({segments:this.api.segments(id),requests:this.api.requests(id,this.offset())}).subscribe({next:v=>{
      if(sequence!==this.sequence)return;this.segments.set(v.segments);this.requests.set(v.requests);if(clearError)this.error.set(null);
      const previous=this.selected();const resolved=previous&&v.requests.find(r=>r.id===previous.id&&r.status!=='PENDING');
      if(this.uncertain()&&resolved){this.uncertain.set(false);this.blocked.emit(false);this.cancelReview();}
    },error:e=>{if(sequence===this.sequence)this.error.set(this.errors.toUiError(e).message);}});}
  page(step:number){if(this.busy())return;this.offset.set(Math.max(0,this.offset()+step));this.load();}
  select(r:GpsAccessRequest){if(this.busy()||this.uncertain()||this.externalBlocked()||r.status!=='PENDING')return;this.selected.set(r);this.form.reset({identityConfirmed:false,password:''});}
  cancelReview(){if(this.busy()||this.uncertain())return;this.form.reset({identityConfirmed:false,password:''});this.selected.set(null);}
  review(approved:boolean){const r=this.selected();if(!r||this.busy()||this.uncertain()||this.externalBlocked()||this.form.invalid)return;
    this.sequence++;this.busy.set(true);this.blocked.emit(true);
    const body={expectedVersion:r.version,approved,identityConfirmed:this.form.controls.identityConfirmed.value,verificationReference:'RESTRICTED_IDENTITY_MATCH' as const,currentPassword:this.form.controls.password.value};
    this.form.controls.password.setValue('');
    this.api.review(r.id,body).subscribe({next:()=>{this.busy.set(false);this.cancelReview();this.blocked.emit(false);this.load();},error:(e:unknown)=>{
      this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));
      this.blocked.emit(this.uncertain());this.load(false);
    }});
  }
}
