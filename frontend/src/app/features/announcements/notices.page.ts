import {ChangeDetectionStrategy,Component,inject,signal} from '@angular/core';
import {CommunicationApiService,OperationNotice} from '../../core/operations/communication-api.service';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {PageHeaderComponent} from '../../shared/ui/page-header.component';
@Component({selector:'rf-notices-page',imports:[PageHeaderComponent],changeDetection:ChangeDetectionStrategy.OnPush,template:`
 <section class="management-page"><rf-page-header title="Bandeja de avisos" description="Avisos conservados por el servidor y resultados de entrega externa."/>
 <button type="button" (click)="select(false)" [disabled]="busy()">Mi bandeja</button><button type="button" (click)="select(true)" [disabled]="busy()">Entregas no realizadas de la organización</button><button type="button" (click)="load()" [disabled]="busy()">Consultar confirmaciones</button>
 <p>Consultar no confirma lectura. La lectura de un aviso no sustituye Leído del comunicado ni responde ofertas.</p>
 @if(error()){<p role="alert">{{error()}}</p>}
 @for(n of rows();track n.id){<article class="management-detail"><h2>{{label(n.eventType)}}</h2><p>{{n.createdAt}} · CUENTA-{{n.userId}}</p>
 @for(r of refs(n);track r.key){<p>{{r.key}}: {{r.value}}</p>}
 <p>{{delivery(n.externalStatus)}}{{n.externalStatus==='NOT_CONFIGURED'?' · Proveedor/dispositivo y autorización de entrega pendientes.':''}}</p>
 <p>{{n.readAt?'Lectura de aviso confirmada: '+n.readAt:'Sin lectura de aviso confirmada'}}</p>
 @if(!n.readAt&&n.userId===session.user()?.id){<button type="button" (click)="read(n)" [disabled]="busy()||pending()!==null">Confirmar lectura de aviso</button>}
 </article>}@empty{<p>No hay avisos en esta consulta.</p>}
 @if(pending()){<p>Lectura sin confirmar; consulta el resultado antes de otra acción.</p>}
 <button type="button" (click)="pageChange(-1)" [disabled]="busy()||pending()!==null||page()===0">Anterior</button><button type="button" (click)="pageChange(1)" [disabled]="busy()||pending()!==null||page()+1>=pages()">Siguiente</button>
 </section>`})
export class NoticesPage {
 private readonly api=inject(CommunicationApiService);private readonly errors=inject(ApiErrorService);readonly session=inject(AuthSessionService);
 readonly rows=signal<readonly OperationNotice[]>([]);readonly busy=signal(false);readonly pending=signal<string|null>(null);readonly error=signal<string|null>(null);readonly page=signal(0);readonly pages=signal(0);private failures=false;
 constructor(){this.load();}
 select(f:boolean){if(this.busy()||this.pending())return;this.failures=f;this.page.set(0);this.load();}
 pageChange(delta:number){if(this.busy()||this.pending())return;this.page.update(p=>p+delta);this.load();}
 load(){if(this.busy())return;this.busy.set(true);this.api.inbox(this.failures,this.page()).subscribe({next:r=>{this.rows.set(r.items);this.pages.set(r.totalPages);if(r.items.some(n=>n.id===this.pending()&&!!n.readAt))this.pending.set(null);this.busy.set(false);},error:e=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);}});}
 read(n:OperationNotice){if(this.busy()||this.pending())return;this.busy.set(true);this.pending.set(n.id);this.api.readNotice(n.id).subscribe({next:r=>{this.rows.update(rows=>rows.map(v=>v.id===r.id?r:v));this.pending.set(null);this.busy.set(false);},error:e=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.load();}});}
 refs(n:OperationNotice){return Object.entries(n.references).map(([key,value])=>({key:({assignmentId:'Oferta',serviceId:'Servicio',incidentId:'Incidencia',driverId:'Conductor',vehicleId:'Unidad',announcementId:'Comunicado',attentionId:'Atención',handoverId:'Relevo'} as Record<string,string>)[key]??'Referencia',value}));}
 delivery(s:string){return ({NOT_REQUIRED:'Aviso en bandeja; sin entrega externa requerida',NOT_CONFIGURED:'Entrega externa no realizada',FAILED:'Fallo de canal externo; consultar desde bandeja',PROVIDER_ACCEPTED:'Proveedor aceptó el aviso; no acredita entrega al dispositivo ni lectura'} as Record<string,string>)[s]??'Resultado de entrega sin acreditar';}
 label(s:string){if(s.startsWith('assignment.'))return 'Cambio de oferta';if(s.startsWith('incident.'))return 'Cambio de incidencia';if(s.startsWith('service.'))return 'Cambio de servicio';return ({'announcement.published':'Comunicado publicado','announcement.read.pending':'Lecturas de comunicado pendientes','announcement.read':'Lectura expresa de comunicado','driver.suspended':'Suspensión operativa','driver.status.changed':'Cambio de disponibilidad'} as Record<string,string>)[s]??'Aviso operativo';}
}
