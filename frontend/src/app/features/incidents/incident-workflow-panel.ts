import {ChangeDetectionStrategy,Component,effect,inject,input,output,signal,untracked} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {ApiErrorService} from '../../core/api/api-error.service';
import {AuthSessionService} from '../../core/auth/auth-session.service';
import {IncidentWorkflowApiService,IncidentAction,IncidentResponsibleCandidate} from '../../core/operations/incident-workflow-api.service';
import {Incident,INCIDENT_SEVERITIES} from '../../core/operations/operations.models';

@Component({selector:'rf-incident-workflow-panel',imports:[FormsModule],changeDetection:ChangeDetectionStrategy.OnPush,template:`
 <section class="management-detail">
  <h3>Responsable, evaluación e historial</h3>
  <p>Responsable: {{incident().responsibleAdminId ? 'CUENTA-'+incident().responsibleAdminId : 'Sin designar'}}. Gravedad: {{severityLabel(incident().severity??'UNASSESSED')}}.</p>
  <p>{{incident().operationalEffect==='INTERRUPTION_RECORDED'?'Existe interrupción registrada. La atención de conductor y unidad se revisa por separado.':'No hay interrupción registrada en esta incidencia. Esto no acredita que sea seguro continuar.'}}</p>
  <p>Cerrar una incidencia no devuelve recursos a operación ni sustituye las comprobaciones de seguridad, documentación o mantenimiento.</p>
  <button type="button" (click)="read()" [disabled]="busy()">Consultar historial y confirmación</button>
  @if(error()){<p role="alert">{{error()}}</p>}
  @if(uncertain()){<p>No se confirmó el resultado. Consulta la misma operación; no envíes otra acción.</p>}
  @for(row of rows();track row.id){<article class="management-detail">
    <strong>{{kindLabel(row.kind)}}</strong> · CUENTA-{{row.actorId}} · {{row.recordedAt}} · revisión {{row.resultingVersion}}
    @for(entry of detailRows(row);track entry.key){<p>{{entry.key}}: {{entry.value}}</p>}
  </article>}
  @if(closed()){<p>El cierre se conserva. No se permite reapertura ni seguimiento adicional.</p>}
  @else {
   <label>Acción <select id="incident-action" [(ngModel)]="kind" [disabled]="blockedNow()"><option value="responsibility">Designar o cambiar responsable</option><option value="assessment">Evaluar gravedad</option><option value="follow-up">Añadir seguimiento</option><option value="closure">Cerrar con resultado y evidencia</option></select></label>
   @if(kind==='responsibility'){<label>ADMIN responsable <select id="incident-responsible" [(ngModel)]="adminId" [disabled]="blockedNow()"><option value="">Seleccionar ADMIN activo</option>@for(c of candidates();track c.adminId){<option [value]="c.adminId">{{c.label}}</option>}</select></label>}
   @if(kind==='assessment'){<label>Gravedad <select id="incident-assessment" [(ngModel)]="severity" [disabled]="blockedNow()"><option value="">Seleccionar</option>@for(s of severities;track s){<option [value]="s">{{severityLabel(s)}}</option>}</select></label>}
   @if(kind!=='closure'){<label>{{kind==='follow-up'?'Nota de seguimiento':'Motivo de la acción'}} <textarea id="incident-action-reason" [(ngModel)]="reason" maxlength="1000" [disabled]="blockedNow()"></textarea></label>}
   @else {
    <label>Diagnóstico o motivo <textarea id="incident-diagnosis" [(ngModel)]="diagnosis" maxlength="300" [disabled]="blockedNow()"></textarea></label>
    <label>Acciones tomadas <textarea id="incident-measures" [(ngModel)]="measures" maxlength="300" [disabled]="blockedNow()"></textarea></label>
    <label>Resultado <textarea id="incident-result" [(ngModel)]="result" maxlength="300" [disabled]="blockedNow()"></textarea></label>
    <label>¿La naturaleza del caso requiere evidencia? <select id="incident-evidence-required" [(ngModel)]="evidenceRequired" [disabled]="blockedNow()"><option value="">Seleccionar</option><option value="YES">Sí</option><option value="NO">No, se explicará</option></select></label>
    <label>Evidencia <select id="incident-evidence-mode" [(ngModel)]="evidenceMode" [disabled]="blockedNow()"><option value="">Seleccionar</option><option value="REFERENCE">Referencia de comprobación</option><option value="UNAVAILABLE_JUSTIFIED">Imposibilidad justificada</option>@if(evidenceRequired==='NO'){<option value="NOT_REQUIRED">No requerida por la naturaleza del caso</option>}</select></label>
    @if(evidenceMode==='REFERENCE'){<label>Referencia codificada <input id="incident-evidence-reference" [(ngModel)]="evidenceReference" maxlength="100" [disabled]="blockedNow()"/></label>}
    @else {<label>Explicación <textarea id="incident-evidence-explanation" [(ngModel)]="evidenceExplanation" maxlength="300" [disabled]="blockedNow()"></textarea></label>}
   }
   <p>Usa referencias y hechos sin datos personales ni coordenadas. No se conservan archivos originales en este flujo.</p>
   <label><input id="incident-action-confirm" type="checkbox" [(ngModel)]="confirmed" [disabled]="blockedNow()"/>Confirmo registrar esta acción sobre la incidencia vigente.</label>
   <button type="button" (click)="submit()" [disabled]="blockedNow()||!valid()">Registrar acción de incidencia</button>
  }
 </section>`})
export class IncidentWorkflowPanel {
 readonly incident=input.required<Incident>();readonly changed=output<void>();readonly blocked=output<boolean>();
 private readonly api=inject(IncidentWorkflowApiService);private readonly errors=inject(ApiErrorService);private readonly session=inject(AuthSessionService);
 readonly rows=signal<readonly IncidentAction[]>([]);readonly candidates=signal<readonly IncidentResponsibleCandidate[]>([]);readonly error=signal<string|null>(null);readonly busy=signal(false);readonly uncertain=signal(false);readonly severities=INCIDENT_SEVERITIES;
 kind='responsibility';adminId='';severity='';reason='';diagnosis='';measures='';result='';evidenceRequired='';evidenceMode='';evidenceReference='';evidenceExplanation='';confirmed=false;
 private key:string|null=null;private pendingKind='';private sequence=0;
 constructor(){effect(()=>{this.incident();untracked(()=>{this.sequence++;this.key=null;this.uncertain.set(false);this.busy.set(false);this.confirmed=false;this.read();});});}
 closed(){return ['RESOLVED','CLOSED_BY_DRIVER'].includes(this.incident().status);}
 blockedNow(){return this.busy()||this.uncertain();}
 severityLabel(s:string){return ({UNASSESSED:'Sin evaluar',LOW:'Baja',MEDIUM:'Media',HIGH:'Alta',CRITICAL:'Crítica'} as Record<string,string>)[s]??s;}
 kindLabel(s:string){return ({REPORT:'Reporte original',RESPONSIBILITY:'Designación de responsable',ASSESSMENT:'Evaluación de gravedad',FOLLOW_UP:'Seguimiento ADMIN',INFORMATION:'Información del conductor',RESOLUTION:'Cierre ADMIN',DRIVER_CLOSE:'Cierre por conductor'} as Record<string,string>)[s]??s;}
 detailRows(row:IncidentAction){return Object.entries(row.detail).map(([key,value])=>({key:({reason:'Motivo',note:'Nota',diagnosis:'Diagnóstico',actions:'Acciones',result:'Resultado',adminId:'ADMIN designado',previousAdminId:'ADMIN anterior',before:'Gravedad anterior',after:'Gravedad registrada',evidenceMode:'Tratamiento de evidencia',evidenceReference:'Referencia',evidenceExplanation:'Explicación',evidenceRequired:'Evidencia requerida',responsibleAdminId:'ADMIN responsable',resourcesReleased:'Liberación de recursos',closedAt:'Fecha de cierre',severity:'Gravedad',previousIncidentId:'Incidencia anterior',relationReason:'Motivo del vínculo'} as Record<string,string>)[key]??key,value:typeof value==='boolean'?(value?'Sí':'No'):String(value)}));}
 read(){if(this.busy())return;const n=++this.sequence;this.api.history(this.incident().id).subscribe({next:rows=>{if(n!==this.sequence)return;this.rows.set(rows);const receipt=rows.find(r=>r.operationKey===this.key&&r.kind===this.pendingKind&&r.actorId===this.session.user()?.id);if(this.uncertain()&&receipt)this.finish();},error:e=>{if(n===this.sequence)this.error.set(this.errors.toUiError(e).message);}});this.api.candidates().subscribe({next:c=>{if(n===this.sequence)this.candidates.set(c);},error:e=>{if(n===this.sequence)this.error.set(this.errors.toUiError(e).message);}});}
 valid(){if(!this.confirmed||this.closed())return false;if(this.kind==='closure')return !!this.diagnosis.trim()&&!!this.measures.trim()&&!!this.result.trim()&&!!this.evidenceRequired&&!!this.evidenceMode&&!(this.evidenceRequired==='YES'&&this.evidenceMode==='NOT_REQUIRED')&&(this.evidenceMode==='REFERENCE'?!!this.evidenceReference.trim():!!this.evidenceExplanation.trim());return !!this.reason.trim()&&(this.kind!=='responsibility'||!!this.adminId)&&(this.kind!=='assessment'||!!this.severity);}
 submit(){if(this.blockedNow()||!this.valid())return;this.key=crypto.randomUUID();this.pendingKind=({responsibility:'RESPONSIBILITY',assessment:'ASSESSMENT','follow-up':'FOLLOW_UP',closure:'RESOLUTION'} as Record<string,string>)[this.kind];const body:Record<string,unknown>={operationKey:this.key,expectedVersion:this.incident().version};
  if(this.kind==='responsibility')Object.assign(body,{adminId:this.adminId,reason:this.reason.trim()});
  else if(this.kind==='assessment')Object.assign(body,{severity:this.severity,reason:this.reason.trim()});
  else if(this.kind==='follow-up'){delete body['expectedVersion'];Object.assign(body,{version:this.incident().version,note:this.reason.trim(),resolve:false});}
  else Object.assign(body,{diagnosis:this.diagnosis.trim(),actions:this.measures.trim(),result:this.result.trim(),evidenceRequired:this.evidenceRequired==='YES',evidenceMode:this.evidenceMode,...(this.evidenceMode==='REFERENCE'?{evidenceReference:this.evidenceReference.trim()}:{evidenceExplanation:this.evidenceExplanation.trim()})});
  this.sequence++;this.busy.set(true);this.blocked.emit(true);this.error.set(null);this.api.act(this.incident().id,this.kind,body).subscribe({next:()=>this.finish(),error:(e:unknown)=>{this.busy.set(false);this.error.set(this.errors.toUiError(e).message);this.uncertain.set(!(e instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(e.status));if(!this.uncertain())this.key=null;this.blocked.emit(this.uncertain());this.read();}});
 }
 private finish(){this.key=null;this.busy.set(false);this.uncertain.set(false);this.confirmed=false;this.blocked.emit(false);this.changed.emit();}
}
