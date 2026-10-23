import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { IncidentWorkflowApiService } from '../../core/operations/incident-workflow-api.service';
import { IncidentWorkflowPanel } from './incident-workflow-panel';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { Driver } from '../../core/management/management.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  Assignment,
  INCIDENT_CATEGORIES,
  INCIDENT_STATUSES,
  Incident,
  IncidentCategory,
  IncidentStatus,
  IncidentSeverity, INCIDENT_SEVERITIES,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

@Component({
  selector: 'rf-incidents-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, PaginationComponent,IncidentWorkflowPanel],
  templateUrl: './incidents.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncidentsPage {
  private readonly api = inject(OperationsApiService);
  private readonly workflow=inject(IncidentWorkflowApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories = INCIDENT_CATEGORIES;
  readonly severities=INCIDENT_SEVERITIES;
  readonly createUncertain=signal(false);
  readonly attentionBlocked=signal(false);
  private createKey:string|null=null;
  readonly statuses = INCIDENT_STATUSES;
  readonly incidents = signal<readonly Incident[]>([]);
  readonly drivers = signal<readonly Driver[]>([]);
  readonly assignments = signal<readonly Assignment[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly followUpSaving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedStatus = signal<IncidentStatus | ''>('');
  readonly selectedCategory = signal<IncidentCategory | ''>('');
  readonly selectedDriverId = signal('');
  readonly fromFilter = signal('');
  readonly toFilter = signal('');
  readonly formOpen = signal(false);
  readonly selectedIncident = signal<Incident | null>(null);
  readonly detailOpen = signal(false);
  readonly selectedForFollowUp = signal<Incident | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );

  readonly form = new FormGroup({
    driverId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    assignmentId: new FormControl('', { nonNullable: true }),
    category: new FormControl<IncidentCategory>('AVERIA', { nonNullable: true, validators: [Validators.required] }),
    severity:new FormControl<IncidentSeverity|''>('',{nonNullable:true,validators:[Validators.required]}),
    previousIncidentId:new FormControl('',{nonNullable:true}),
    relationReason:new FormControl<''|'RECURRENCE'|'CLOSURE_ERROR'>('',{nonNullable:true}),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
  });
  readonly followUpForm = new FormGroup({
    note: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
    resolve: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    this.load();
    this.loadResources();
    this.realtime.connect();
    this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.destroyRef.onDestroy(() => this.realtime.disconnect());
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listIncidents({
        page,
        size: 20,
        sort: 'reportedAt,desc',
        status: this.selectedStatus() || undefined,
        category: this.selectedCategory() || undefined,
        driverId: this.selectedDriverId() || undefined,
        from: this.filterInstant(this.fromFilter(), false),
        to: this.filterInstant(this.toFilter(), true),
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.incidents.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las incidencias.')),
      });
  }

  applyFilters(status: string, category: string, driverId: string, from: string, to: string): void {
    this.selectedStatus.set(this.isIncidentStatus(status) ? status : '');
    this.selectedCategory.set(this.isIncidentCategory(category) ? category : '');
    this.selectedDriverId.set(driverId);
    this.fromFilter.set(from);
    this.toFilter.set(to);
    this.load(0);
  }

  clearFilters(): void {
    this.selectedStatus.set('');
    this.selectedCategory.set('');
    this.selectedDriverId.set('');
    this.fromFilter.set('');
    this.toFilter.set('');
    this.load(0);
  }

  startCreate(): void {
    if(this.createUncertain())return;
    this.error.set(null);
    this.notice.set(null);
    this.form.reset({ driverId: '', assignmentId: '', category: 'AVERIA', description: '',severity:'',previousIncidentId:'',relationReason:'' });
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (!this.saving()&&!this.createUncertain()) {
      this.formOpen.set(false);
    }
  }

  viewDetails(incident: Incident): void {
    this.selectedIncident.set(incident);
    this.selectedForFollowUp.set(null);
    this.detailOpen.set(true);
  }

  closeDetails(): void {
    if (!this.followUpSaving()&&!this.attentionBlocked()) {
      this.detailOpen.set(false);
      this.selectedIncident.set(null);
      this.selectedForFollowUp.set(null);
      this.followUpForm.reset({ note: '', resolve: false });
    }
  }

  submit(): void {
    if(this.saving()||this.createUncertain())return;
    this.error.set(null);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if(!value.severity)return;
    if(value.previousIncidentId&&!value.relationReason){this.form.controls.relationReason.setErrors({required:true});return;}
    this.createKey=crypto.randomUUID();
    this.saving.set(true);
    this.api
      .createIncident({
        driverId: value.driverId,
        assignmentId: value.assignmentId || undefined,
        category: value.category,
        description: value.description.trim(),
        severity:value.severity,operationKey:this.createKey,
        ...(value.previousIncidentId?{previousIncidentId:value.previousIncidentId,relationReason:value.relationReason as 'RECURRENCE'|'CLOSURE_ERROR'}:{}),
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.creationConfirmed();
        },
        error: (error: unknown) => {
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo registrar la incidencia.'));
          this.createUncertain.set(!(error instanceof HttpErrorResponse)||![400,401,403,404,409,422,429].includes(error.status));
          if(!this.createUncertain())this.createKey=null;
        },
      });
  }

  startRelated(incident:Incident){
    if(this.attentionBlocked()||this.createUncertain())return;this.closeDetails();this.startCreate();
    this.form.patchValue({driverId:incident.driverId,previousIncidentId:incident.id});
  }
  severityLabel(s:string){return ({LOW:'Baja',MEDIUM:'Media',HIGH:'Alta',CRITICAL:'Crítica',UNASSESSED:'Sin evaluar'} as Record<string,string>)[s]??s;}
  consultCreation(){
    if(!this.createUncertain()||!this.createKey)return;
    this.workflow.operation(this.createKey).subscribe({next:r=>{if(r.kind==='REPORT'&&r.operationKey===this.createKey)this.creationConfirmed();},error:e=>this.error.set(this.apiErrors.toUiError(e,'No hay confirmación del reporte. Conserva el formulario y consulta de nuevo.'))});
  }
  private creationConfirmed(){this.createKey=null;this.createUncertain.set(false);this.notice.set('Incidencia confirmada por el servidor.');this.form.reset({driverId:'',assignmentId:'',category:'AVERIA',description:'',severity:'',previousIncidentId:'',relationReason:''});this.formOpen.set(false);this.load(0);}
  attentionChanged(){const current=this.selectedIncident();if(!current)return;this.workflow.get(current.id).subscribe({next:r=>{this.selectedIncident.set(r);this.notice.set('Atención confirmada por el servidor.');this.load();},error:e=>this.error.set(this.apiErrors.toUiError(e))});}

  selectAssignment(assignmentId: string): void {
    this.form.controls.assignmentId.setValue(assignmentId);
    const assignment = this.assignments().find((candidate) => candidate.id === assignmentId);
    if (assignment !== undefined) {
      this.form.controls.driverId.setValue(assignment.driverId);
    }
  }

  beginFollowUp(incident: Incident): void {
    if (incident.status === 'RESOLVED' || incident.status === 'CLOSED_BY_DRIVER') {
      return;
    }
    this.selectedForFollowUp.set(incident);
    this.followUpForm.reset({ note: '', resolve: false });
  }

  cancelFollowUp(): void {
    this.selectedForFollowUp.set(null);
    this.followUpForm.reset({ note: '', resolve: false });
  }

  submitFollowUp(): void {
    const incident = this.selectedForFollowUp();
    if (incident === null) {
      return;
    }
    if (this.followUpForm.invalid) {
      this.followUpForm.markAllAsTouched();
      return;
    }
    const value = this.followUpForm.getRawValue();
    this.followUpSaving.set(true);
    this.api
      .followUpIncident(incident.id, {
        operationKey:crypto.randomUUID(),
        version: incident.version,
        note: value.note.trim(),
        resolve: value.resolve,
      })
      .pipe(finalize(() => this.followUpSaving.set(false)))
      .subscribe({
        next: () => {
          this.notice.set(value.resolve ? 'Incidencia resuelta.' : 'Seguimiento registrado.');
          this.cancelFollowUp();
          this.detailOpen.set(false);
          this.selectedIncident.set(null);
          this.load();
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo registrar el seguimiento.')),
      });
  }

  categoryLabel(category: IncidentCategory): string {
    return ({
      AVERIA: 'Avería',
      ACCIDENTE: 'Accidente',
      RETRASO: 'Retraso',
      LICENCIA_VENCIDA: 'Vencimiento de licencia',
      IMPOSIBILIDAD_SERVICIO: 'Imposibilidad de prestar el servicio',
      UBICACION_NO_CORROBORADA: 'Ubicación discrepante o no corroborada',
      RIESGO_SEGURIDAD: 'Riesgo de seguridad',
      ACCESO_PARADA: 'Problema de acceso a la parada',
      FALLO_PLATAFORMA: 'Fallo técnico de la plataforma',
      OTRO: 'Otro',
    } as Record<IncidentCategory, string>)[category];
  }

  statusLabel(status: IncidentStatus): string {
    return ({
      OPEN: 'Abierta',
      FOLLOW_UP: 'En seguimiento',
      RESOLVED: 'Resuelta',
      CLOSED_BY_DRIVER: 'Cerrada por conductor',
    } as Record<IncidentStatus, string>)[status];
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  private loadResources(): void {
    this.managementApi.listDrivers({ page: 0, size: 100, sort: 'fullName,asc', active: true }).subscribe({
      next: (response) => this.drivers.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
    });
    this.api.listAssignments({ page: 0, size: 100, sort: 'scheduledAt,desc' }).subscribe({
      next: (response) => this.assignments.set(response.items),
      error: () => {
        // La asignación es opcional para registrar una incidencia.
      },
    });
  }

  private filterInstant(value: string, endOfDay: boolean): string | undefined {
    if (!value) {
      return undefined;
    }
    const timestamp = Date.parse(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
    return Number.isNaN(timestamp) ? undefined : new Date(timestamp).toISOString();
  }

  private isIncidentStatus(value: string): value is IncidentStatus {
    return (INCIDENT_STATUSES as readonly string[]).includes(value);
  }

  private isIncidentCategory(value: string): value is IncidentCategory {
    return (INCIDENT_CATEGORIES as readonly string[]).includes(value);
  }
}
