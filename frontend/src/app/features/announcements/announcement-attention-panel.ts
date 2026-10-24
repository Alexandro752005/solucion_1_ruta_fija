import {ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {AnnouncementAttention,AnnouncementRecipient,CommunicationApiService} from '../../core/operations/communication-api.service';
@Component({selector:'rf-announcement-attention',imports:[FormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
 <section class="management-detail"><h3>Recepción y lectura</h3>
 <p>Consultar no confirma Leído. No leer un comunicado no bloquea ni acepta servicios.</p>
 <button type="button" (click)="load()" [disabled]="busy()">Consultar lecturas y confirmación</button>
 @if(error()){<p role="alert">{{error()}}</p>}
 @if(state();as s){<p>{{s.recipients}} destinatarios al publicar; {{s.pending}} lecturas expresas pendientes.</p>
 @for(r of s.items;track r.userId){<article><strong>{{r.label}}</strong><p>{{access(r.accessStatus)}} · {{r.readAt?'Leído expreso: '+r.readAt:(r.deliveredAt?'Consultado: '+r.deliveredAt:'Sin consulta confirmada')}}</p>
 @if(r.accessStatus==='AVAILABLE'){<button type="button" (click)="select(r)" [disabled]="busy()||uncertain()">Retirar acceso por seguridad</button>}
 </article>}}
 @if(selected();as r){<label>Motivo de seguridad sin datos personales <textarea id="announcement-withdraw-reason" maxlength="300" [(ngModel)]="reason" [disabled]="busy()||uncertain()"></textarea></label>
 <label><input id="announcement-withdraw-confirm" type="checkbox" [(ngModel)]="confirmed" [disabled]="busy()||uncertain()"/>Confirmo retirar el acceso de {{r.label}}, conservando publicación y lecturas.</label>
 <button type="button" (click)="withdraw()" [disabled]="busy()||uncertain()||!confirmed||!reason.trim()">Confirmar retirada</button>}
 @if(uncertain()){<p>Retirada sin confirmar. Conserva el formulario y consulta el resultado; no se repetirá el envío.</p>}
 </section>`})
export class AnnouncementAttentionPanel {
 readonly announcementId=input.required<string>();readonly blocked=output<boolean>();
 private readonly api=inject(CommunicationApiService);private readonly errors=inject(ApiErrorService);private readonly session=inject(AuthSessionService);
 readonly state=signal<AnnouncementAttention|null>(null);readonly selected=signal<AnnouncementRecipient|null>(null);readonly error=signal<string|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);
 reason='';confirmed=false;private key:string|null=null;private generation=0;
 constructor(){effect(()=>{this.announcementId();untracked(()=>{this.generation++;this.selected.set(null);this.key=null;this.load();});});}
 access(s:string){return ({AVAILABLE:'Acceso disponible',WITHDRAWN:'Retirado por seguridad',MEMBERSHIP_ENDED:'Pertenencia terminada'} as Record<string,string>)[s]??'Consultar estado';}
 select(r:AnnouncementRecipient){if(this.busy()||this.uncertain())return;this.selected.set(r);this.reason='';this.confirmed=false;}
 load(){if(this.busy())return;const n=++this.generation;this.api.attention(this.announcementId()).subscribe({next:s=>{if(n!==this.generation)return;this.state.set(s);const matched=s.items.some(r=>r.userId===this.selected()?.userId&&r.withdrawalKey===this.key&&r.withdrawnBy===this.session.user()?.id);if(this.uncertain()&&matched)this.finish();},error:e=>{if(n===this.generation)this.error.set(this.errors.toUiError(e).message);}});}
 withdraw(){const r=this.selected();if(!r||this.busy()||this.uncertain()||!this.confirmed||!this.reason.trim())return;this.key=crypto.randomUUID();this.generation++;this.busy.set(true);this.blocked.emit(true);this.error.set(null);
 this.api.withdraw(this.announcementId(),r.userId,{operationKey:this.key,expectedVersion:r.version,reason:this.reason.trim()}).subscribe({next:s=>{this.state.set(s);this.finish();},error:(e:unknown)=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));this.blocked.emit(this.uncertain());if(!this.uncertain())this.key=null;this.load();}});}
 private finish(){this.key=null;this.selected.set(null);this.confirmed=false;this.busy.set(false);this.uncertain.set(false);this.blocked.emit(false);}
}
