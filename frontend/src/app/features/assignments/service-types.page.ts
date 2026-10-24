import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiErrorService } from '../../core/api/api-error.service';
import { ServiceTypesApiService, ServiceTypeSummary, ServiceTypeView } from '../../core/operations/service-types-api.service';

@Component({selector:'rf-service-types',imports:[ReactiveFormsModule,RouterLink],changeDetection:ChangeDetectionStrategy.OnPush,
  template:`<section class="management-page"><header><h1>Tipos de servicio y márgenes</h1><a routerLink="/assignments">Volver a asignaciones</a>
    <p>Configura expresamente los minutos anteriores y posteriores. Cada oferta conservará su versión.</p></header>
    @if(error()){<p role="alert">{{error()}}</p>} @if(notice()){<p role="status">{{notice()}}</p>}
    <button type="button" (click)="newType()" [disabled]="busy()||uncertain()">Nuevo tipo</button>
    @for(t of types();track t.id){<article><h2>{{t.name}}</h2><p>{{t.active?'Activo':'Retirado'}} · revisión de control {{t.version}}</p><button type="button" (click)="open(t.id)" [disabled]="busy()||uncertain()">Consultar versiones</button></article>}
    <button type="button" (click)="load(Math.max(0,offset()-100))" [disabled]="offset()===0||busy()||uncertain()">Anteriores</button>
    <button type="button" (click)="load(offset()+100)" [disabled]="types().length<100||busy()||uncertain()">Siguientes</button>
    @if(selected();as t){<section><h2>Versiones conservadas de {{t.name}}</h2>
      @for(v of t.versions;track v.id){<p>Versión {{v.revision}} · {{v.beforeMinutes}} min antes · {{v.afterMinutes}} min después</p>}
      <button type="button" (click)="editSelected()" [disabled]="busy()||uncertain()||!t.active">Preparar nueva versión</button>
      <button type="button" (click)="changeStatus()" [disabled]="busy()||uncertain()||form.controls.reason.invalid">{{t.active?'Retirar para nuevas ofertas':'Reactivar'}}</button>
    </section>}
    <form [formGroup]="form" (ngSubmit)="save()"><h2>{{editing()?'Nueva versión':'Configuración nueva'}}</h2>
      <label>Referencia del tipo, sin datos personales<input formControlName="name" maxlength="100" /></label>
      <label>Minutos anteriores<input type="number" formControlName="beforeMinutes" min="0" step="1" /></label>
      <label>Minutos posteriores<input type="number" formControlName="afterMinutes" min="0" step="1" /></label>
      <label>Motivo sin datos personales<input formControlName="reason" maxlength="160" /></label>
      <button type="submit" [disabled]="busy()||uncertain()||form.invalid">Guardar configuración expresa</button>
    </form>
    @if(uncertain()){<p>No se conoce el resultado. Consulta la operación antes de otra escritura.</p><button type="button" (click)="reconcile()" [disabled]="busy()">Consultar operación</button>}
  </section>`})
export class ServiceTypesPage {
  private readonly api=inject(ServiceTypesApiService); private readonly errors=inject(ApiErrorService);
  readonly Math=Math;readonly types=signal<readonly ServiceTypeSummary[]>([]);readonly selected=signal<ServiceTypeView|null>(null);
  readonly editing=signal(false);readonly busy=signal(false);readonly uncertain=signal(false);readonly offset=signal(0);
  readonly error=signal<string|null>(null);readonly notice=signal<string|null>(null);private pendingKey='';private readSequence=0;
  readonly form=new FormGroup({name:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(100)]}),
    beforeMinutes:new FormControl<number|null>(null,[Validators.required,Validators.min(0),Validators.pattern(/^\d+$/)]),
    afterMinutes:new FormControl<number|null>(null,[Validators.required,Validators.min(0),Validators.pattern(/^\d+$/)]),
    reason:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(160)]})});
  constructor(){this.load();}
  load(offset=0):void {this.api.list(offset).subscribe({next:v=>{this.types.set(v);this.offset.set(offset);},error:e=>this.fail(e)});}
  newType():void {if(this.busy()||this.uncertain())return;this.readSequence++;this.selected.set(null);this.editing.set(false);this.form.reset({name:'',beforeMinutes:null,afterMinutes:null,reason:''});this.notice.set(null);}
  open(id:string):void {if(this.busy()||this.uncertain())return;const sequence=++this.readSequence;this.api.get(id).subscribe({next:v=>{if(sequence!==this.readSequence)return;this.selected.set(v);this.editing.set(false);},error:e=>this.fail(e)});}
  editSelected():void {const t=this.selected();if(!t||this.busy()||this.uncertain()||!t.active)return;const v=t.versions.find(x=>x.id===t.currentVersionId);if(!v)return;this.editing.set(true);this.form.reset({name:v.name,beforeMinutes:v.beforeMinutes,afterMinutes:v.afterMinutes,reason:''});}
  save():void {if(this.busy()||this.uncertain())return;if(this.form.invalid){this.form.markAllAsTouched();return;}const f=this.form.getRawValue();if(f.beforeMinutes===null||f.afterMinutes===null)return;
    const c={name:f.name.trim(),beforeMinutes:f.beforeMinutes,afterMinutes:f.afterMinutes,reason:f.reason.trim(),operationKey:crypto.randomUUID()};const t=this.selected();
    this.pendingKey=c.operationKey;this.busy.set(true);this.error.set(null);this.notice.set(null);
    const req=this.editing()&&t?this.api.revise(t.id,{...c,expectedVersion:t.version}):this.api.create(c);
    req.pipe(finalize(()=>this.busy.set(false))).subscribe({next:v=>this.completed(v),error:e=>this.mutationFailed(e)});
  }
  changeStatus():void {const t=this.selected();if(!t||this.busy()||this.uncertain()||this.form.controls.reason.invalid)return;this.pendingKey=crypto.randomUUID();this.busy.set(true);this.error.set(null);this.notice.set(null);
    this.api.status(t.id,{expectedVersion:t.version,active:!t.active,operationKey:this.pendingKey,reason:this.form.controls.reason.value.trim()}).pipe(finalize(()=>this.busy.set(false))).subscribe({next:v=>this.completed(v),error:e=>this.mutationFailed(e)});
  }
  reconcile():void {if(!this.uncertain()||this.busy())return;this.busy.set(true);this.api.operation(this.pendingKey).pipe(finalize(()=>this.busy.set(false))).subscribe({next:v=>this.completed(v),error:e=>this.fail(e)});}
  private completed(v:ServiceTypeView):void {this.readSequence++;this.selected.set(v);this.editing.set(false);this.uncertain.set(false);this.pendingKey='';this.notice.set('Operación registrada; las versiones anteriores se conservan.');this.form.controls.reason.setValue('');this.load(this.offset());}
  private mutationFailed(e:unknown):void {const status=(e as {status?:number})?.status;if(status===0||status===undefined||status>=500)this.uncertain.set(true);this.fail(e);}
  private fail(e:unknown):void {this.error.set(this.errors.toUiError(e).message);}
}
