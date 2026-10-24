import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { Driver, Vehicle } from '../../core/management/management.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  ASSIGNMENT_STATUSES,
  Assignment,
  AssignmentResponseMode,
  AssignmentPayload,
  AssignmentStatus,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';
import { RoutesApiService, RouteSummary } from '../../core/routes/routes-api.service';
import { AssignmentRoutePanel } from '../routes/assignment-route-panel';
import { ServiceTypesApiService, ServiceTypeSummary, AssignmentCandidate, AssignmentCandidates } from '../../core/operations/service-types-api.service';
import { ScheduledServicesApiService, ScheduledService } from '../../core/operations/scheduled-services-api.service';
import { ScheduledServicePanel } from './scheduled-service-panel';
import { ServiceExecutionPanel } from './service-execution-panel';
import { ServiceTimePanel } from './service-time-panel';
import { ServiceAttentionPanel } from './service-attention-panel';
import { ServiceHandoverPanel } from './service-handover-panel';

type AssignmentActionKind = 'reserve' | 'start' | 'complete';

interface PendingAssignmentAction {
  readonly assignment: Assignment;
  readonly kind: AssignmentActionKind;
}

@Component({
  selector: 'rf-assignments-page',
  imports: [RouterLink, AssignmentRoutePanel, ScheduledServicePanel, ServiceExecutionPanel, ServiceTimePanel, ServiceAttentionPanel, ServiceHandoverPanel, ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './assignments.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentsPage {
  private readonly api = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly routesApi = inject(RoutesApiService);
  private readonly serviceTypesApi=inject(ServiceTypesApiService);
  private readonly servicesApi=inject(ScheduledServicesApiService);
  readonly reofferTarget=signal<ScheduledService|null>(null);
  readonly serviceBlocked=signal(false);
  readonly reofferUncertain=signal(false);
  readonly creationUncertain=signal(false);private creationKey:string|null=null;
  readonly cancellationUncertain=signal(false);
  readonly reofferReason=new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(160)]});
  private reofferKey:string|null=null;
  readonly typeChoices=signal<readonly ServiceTypeSummary[]>([]);
  readonly moreTypes=signal(false);private typeOffset=0;
  readonly candidates=signal<readonly AssignmentCandidate[]>([]);
  readonly candidateLoading=signal(false);
  readonly candidateResult=signal<AssignmentCandidates|null>(null);
  private candidateFingerprint='';private candidateSequence=0;
  readonly routeChoices = signal<readonly RouteSummary[]>([]);
  readonly routeLoading = signal(false);

  readonly statuses = ASSIGNMENT_STATUSES;
  readonly assignments = signal<readonly Assignment[]>([]);
  readonly drivers = signal<readonly Driver[]>([]);
  readonly vehicles = signal<readonly Vehicle[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly actionId = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedStatus = signal<AssignmentStatus | ''>('');
  readonly selectedDriverId = signal('');
  readonly selectedVehicleId = signal('');
  readonly fromFilter = signal('');
  readonly toFilter = signal('');
  readonly editing = signal<Assignment | null>(null);
  readonly formOpen = signal(false);
  readonly selectedAssignment = signal<Assignment | null>(null);
  readonly detailOpen = signal(false);
  readonly pendingAction = signal<PendingAssignmentAction | null>(null);
  readonly cancellationTarget = signal<Assignment | null>(null);
  readonly linkedVehicleIds = signal<ReadonlySet<string> | null>(null);
  readonly loadingEligibleVehicles = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly eligibleVehicles = computed(() => {
    const linked = this.linkedVehicleIds();
    return linked === null ? [] : this.vehicles().filter((vehicle) => linked.has(vehicle.id));
  });

  readonly form = new FormGroup({
    serviceTypeVersionId:new FormControl('',{nonNullable:true,validators:[Validators.required]}),
    routeVersionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    driverId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    vehicleId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    originText: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(250)],
    }),
    destinationText: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(250)],
    }),
    scheduledAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    scheduledEndAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
  });
  readonly cancellationForm = new FormGroup({
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(300)],
    }),
  });

  constructor() {
    this.load();
    this.loadResources();
    this.realtime.connect();
    this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(event => {
      if(event.event==='service.closure.observed')this.notice.set('Un cierre requiere revisión del terminal. La discrepancia GPS no implica fraude ni demuestra todo el recorrido. Consulta el detalle del servicio.');
      this.load();
    });
    this.destroyRef.onDestroy(() => this.realtime.disconnect());
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listAssignments({
        page,
        size: 20,
        sort: 'scheduledAt,desc',
        status: this.selectedStatus() || undefined,
        driverId: this.selectedDriverId() || undefined,
        vehicleId: this.selectedVehicleId() || undefined,
        from: this.filterInstant(this.fromFilter(), false),
        to: this.filterInstant(this.toFilter(), true),
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.assignments.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las asignaciones.')),
      });
  }

  applyFilters(status: string, driverId: string, vehicleId: string, from: string, to: string): void {
    this.selectedStatus.set(this.isAssignmentStatus(status) ? status : '');
    this.selectedDriverId.set(driverId);
    this.selectedVehicleId.set(vehicleId);
    this.fromFilter.set(from);
    this.toFilter.set(to);
    this.load(0);
  }

  clearFilters(): void {
    this.selectedStatus.set('');
    this.selectedDriverId.set('');
    this.selectedVehicleId.set('');
    this.fromFilter.set('');
    this.toFilter.set('');
    this.load(0);
  }

  startCreate(): void {
    if(this.reofferUncertain()||this.creationUncertain()||this.cancellationUncertain()||this.saving()||this.serviceBlocked())return;
    this.error.set(null);
    this.notice.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
    this.typeOffset=0;this.loadTypes();this.resetCandidates();
    this.routesApi.list().subscribe({ next: (r) => this.routeChoices.set(r.filter((x) => x.active)), error: (e: unknown) => this.error.set(this.apiErrors.toUiError(e)) });
  }

  routeChanged(id: string): void {
    this.resetCandidates();
    const choice=this.routeChoices().find((r)=>r.currentVersionId===id);
    this.form.patchValue({ originText: '', destinationText: '' });
    if (!choice || this.editing()) return; this.routeLoading.set(true);
    this.routesApi.get(choice.id).subscribe({ next: (v) => { if (this.form.controls.routeVersionId.value === id) {
      const route=v.versions.find((r)=>r.id===id); if (route && v.active && v.currentVersionId===id) this.form.patchValue({ originText: route.stops[0].name, destinationText: route.stops[route.stops.length-1].name });
    } this.routeLoading.set(false); }, error: (e: unknown) => { this.routeLoading.set(false); this.error.set(this.apiErrors.toUiError(e)); } });
  }
  routeReplaced(): void { this.closeDetails(); this.load(0); this.notice.set('Ruta reemplazada: la nueva programación requiere aceptación CONDUCTOR.'); }
  serviceChanged(): void { this.closeDetails(); this.load(0); this.notice.set('Servicio actualizado. Consulta su estado, participantes y revisiones pendientes.'); }
  startReoffer(service:ScheduledService):void {
    const a=this.selectedAssignment();if(!a||a.id!==service.currentOfferId||this.serviceBlocked())return;
    this.startCreate();this.reofferTarget.set(service);this.reofferReason.reset('');
    this.form.patchValue({serviceTypeVersionId:service.serviceTypeVersionId,routeVersionId:service.routeVersionId,
      scheduledAt:this.toDateTimeLocal(service.scheduledAt),scheduledEndAt:this.toDateTimeLocal(service.scheduledEndAt),
      originText:a.originText,destinationText:a.destinationText,notes:a.notes??''});
    this.detailOpen.set(false);this.selectedAssignment.set(null);
  }
  reconcileReoffer():void {
    const s=this.reofferTarget();if(!s||!this.reofferKey||!this.reofferUncertain()||this.saving())return;
    this.saving.set(true);this.servicesApi.operation(this.reofferKey).pipe(finalize(()=>this.saving.set(false))).subscribe({next:r=>{
      if(r.serviceId!==s.id||r.action!=='REOFFER'){this.error.set({message:'La operación no corresponde a esta solicitud; conserva el caso.',fieldErrors:[]});return;}
      this.reofferUncertain.set(false);this.resetForm();this.load(0);this.notice.set('Nueva oferta registrada; requiere aceptación CONDUCTOR.');
    },error:e=>this.error.set(this.apiErrors.toUiError(e))});
  }
  reconcileCreation():void {
    if(!this.creationKey||!this.creationUncertain()||this.saving())return;this.saving.set(true);
    this.api.assignmentCreationOperation(this.creationKey).pipe(finalize(()=>this.saving.set(false))).subscribe({next:a=>{
      if(!a.id||!a.serviceId){this.error.set({message:'La consulta no acredita una oferta y servicio; conserva el caso.',fieldErrors:[]});return;}
      this.creationUncertain.set(false);this.resetForm();this.load(0);this.notice.set('Emisión localizada en el servidor; consulta su estado vigente.');
    },error:e=>this.error.set(this.apiErrors.toUiError(e))});
  }

  loadTypes(more=false):void {
    const offset=more?this.typeOffset+100:0;
    this.serviceTypesApi.list(offset).subscribe({next:r=>{this.typeOffset=offset;this.moreTypes.set(r.length===100);this.typeChoices.set([...(more?this.typeChoices():[]),...r.filter(t=>t.active)]);},error:e=>this.error.set(this.apiErrors.toUiError(e))});
  }
  resetCandidates():void {this.candidateSequence++;this.candidates.set([]);this.candidateResult.set(null);this.candidateFingerprint='';this.candidateLoading.set(false);this.form.patchValue({driverId:'',vehicleId:''});}
  private fingerprint():string {const v=this.form.getRawValue();return JSON.stringify([v.serviceTypeVersionId,v.routeVersionId,v.scheduledAt,v.scheduledEndAt,this.reofferTarget()?.id]);}
  findCandidates(more=false):void {
    const f=this.form.getRawValue(),start=this.toIso(f.scheduledAt),end=this.toIso(f.scheduledEndAt);
    if(!f.serviceTypeVersionId||!f.routeVersionId||!start||!end||end<=start){this.error.set({message:'Selecciona tipo, ruta y horario válido antes de buscar.',fieldErrors:[]});return;}
    const previous=this.candidateResult();if(more&&(previous?.nextOffset==null||this.fingerprint()!==this.candidateFingerprint))return;
    if(!more)this.resetCandidates();const sequence=++this.candidateSequence,key=this.fingerprint();this.candidateLoading.set(true);this.error.set(null);
    this.serviceTypesApi.candidates({serviceTypeVersionId:f.serviceTypeVersionId,routeVersionId:f.routeVersionId,scheduledAt:start,scheduledEndAt:end,offset:more?previous?.nextOffset??0:0,
      ...(this.reofferTarget()?{serviceId:this.reofferTarget()!.id}:{})})
      .pipe(finalize(()=>{if(sequence===this.candidateSequence)this.candidateLoading.set(false);})).subscribe({next:r=>{
        if(sequence!==this.candidateSequence||key!==this.fingerprint())return;this.candidateFingerprint=key;this.candidateResult.set(r);
        this.candidates.set([...(more?this.candidates():[]),...r.items]);
      },error:e=>{if(sequence===this.candidateSequence)this.error.set(this.apiErrors.toUiError(e));}});
  }
  chooseCandidate(c:AssignmentCandidate):void {
    if(this.candidateFingerprint!==this.fingerprint()||!this.candidates().some(x=>x.driverId===c.driverId&&x.vehicleId===c.vehicleId&&x.periodId===c.periodId))return;
    this.form.patchValue({driverId:c.driverId,vehicleId:c.vehicleId});
  }

  viewDetails(assignment: Assignment): void {
    if(this.serviceBlocked()||this.cancellationUncertain())return;
    this.selectedAssignment.set(assignment);
    this.detailOpen.set(true);
  }

  closeDetails(): void {
    if (this.actionId() === null&&!this.serviceBlocked()&&!this.cancellationUncertain()) {
      this.detailOpen.set(false);
      this.selectedAssignment.set(null);
      this.pendingAction.set(null);
    }
  }

  driverChanged(driverId: string): void {
    this.form.controls.vehicleId.setValue('');
    this.loadEligibleVehicles(driverId);
  }

  submit(): void {
    this.error.set(null);
    this.notice.set(null);
    if(this.saving()||this.reofferUncertain()||this.creationUncertain())return;
    if(this.reofferTarget()&&this.reofferReason.invalid){this.reofferReason.markAsTouched();return;}
    const selected=this.form.getRawValue();
    if(!this.editing()&&(this.candidateFingerprint!==this.fingerprint()||!this.candidates().some(c=>c.driverId===selected.driverId&&c.vehicleId===selected.vehicleId))){
      this.error.set({message:'Consulta y selecciona un candidato para este horario; el servidor volverá a validarlo.',fieldErrors:[]});return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const payload = this.payloadFromForm();
    if (payload === null) {
      this.error.set({ message: 'Las fechas de programación no son válidas.', fieldErrors: [] });
      return;
    }

    const current = this.editing();
    this.saving.set(true);
    const service=this.reofferTarget();
    if(service)this.reofferKey=this.idempotencyKey();
    if(!service&&current===null)this.creationKey=this.idempotencyKey();
    const request = service
      ? this.servicesApi.reoffer(service.id,{expectedVersion:service.version,operationKey:this.reofferKey!,reason:this.reofferReason.value.trim(),offer:payload})
          .pipe(map(v=>v.offers.find(a=>a.id===v.service.currentOfferId)!))
      : current === null
      ? this.api.createAssignment(payload, this.creationKey!)
      : this.api.updateAssignment(current.id, { ...payload, version: current.version });
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Oferta emitida: pendiente de aceptación de CONDUCTOR.' : 'Asignación actualizada correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) => {
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar la asignación.'));
        if(service)this.reofferUncertain.set(!(error instanceof HttpErrorResponse)||![400,401,403,404,409,422].includes(error.status));
        else if(current===null)this.creationUncertain.set(!(error instanceof HttpErrorResponse)||![400,401,403,404,409,422].includes(error.status));
      },
    });
  }

  startEdit(assignment: Assignment): void {
    if (!this.canEdit(assignment)) {
      return;
    }
    this.editing.set(assignment);
    this.form.reset({
      serviceTypeVersionId:assignment.serviceTypeVersionId??'',
      driverId: assignment.driverId,
      routeVersionId: assignment.routeVersionId ?? 'LEGACY_UNRECORDED',
      vehicleId: assignment.vehicleId,
      originText: assignment.originText,
      destinationText: assignment.destinationText,
      scheduledAt: this.toDateTimeLocal(assignment.scheduledAt),
      scheduledEndAt: this.toDateTimeLocal(assignment.scheduledEndAt),
      notes: assignment.notes ?? '',
    });
    this.detailOpen.set(false);
    this.selectedAssignment.set(null);
    this.formOpen.set(true);
    this.loadEligibleVehicles(assignment.driverId, assignment.vehicleId);
  }

  cancelEdit(): void {
    this.resetForm();
  }

  closeForm(): void {
    if (!this.saving()&&!this.reofferUncertain()&&!this.creationUncertain()) {
      this.resetForm();
    }
  }

  reserve(assignment: Assignment): void {
    if (this.canReserve(assignment)) {
      this.pendingAction.set({ assignment, kind: 'reserve' });
    }
  }

  start(assignment: Assignment): void {
    if (this.canStart(assignment)) {
      this.pendingAction.set({ assignment, kind: 'start' });
    }
  }

  complete(assignment: Assignment): void {
    if (this.canComplete(assignment)) {
      this.pendingAction.set({ assignment, kind: 'complete' });
    }
  }

  cancel(assignment: Assignment): void {
    if (!this.canCancel(assignment)||this.cancellationUncertain()) {
      return;
    }
    this.cancellationForm.reset({ reason: '' });
    this.cancellationTarget.set(assignment);
  }

  closeCancellation(): void {
    if (this.actionId() === null&&!this.cancellationUncertain()) {
      this.cancellationTarget.set(null);
      this.cancellationForm.reset({ reason: '' });
    }
  }

  submitCancellation(): void {
    const assignment = this.cancellationTarget();
    if (assignment === null || this.cancellationForm.invalid || this.actionId() !== null||this.cancellationUncertain()) {
      this.cancellationForm.markAllAsTouched();
      return;
    }
    const reason = this.cancellationForm.controls.reason.getRawValue().trim();
    this.actionId.set(assignment.id);
    this.error.set(null);
    this.api.cancelAssignment(assignment.id, { version: assignment.version, reason })
      .pipe(finalize(() => this.actionId.set(null)))
      .subscribe({
        next: () => {
          this.notice.set('Asignación cancelada correctamente.');
          this.cancellationTarget.set(null);
          this.detailOpen.set(false);
          this.selectedAssignment.set(null);
          this.load();
        },
        error: (error: unknown) => {
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo cancelar la oferta.'));
          this.cancellationUncertain.set(!(error instanceof HttpErrorResponse)||![400,401,403,404,409,422].includes(error.status));
        },
      });
  }
  reconcileCancellation():void {
    const a=this.cancellationTarget();if(!a||!this.cancellationUncertain()||this.actionId())return;
    this.actionId.set(a.id);this.api.getAssignment(a.id).pipe(finalize(()=>this.actionId.set(null))).subscribe({next:current=>{
      if(current.status!=='CANCELLED'){this.error.set({message:'El servidor aún no confirma una oferta cancelada; conserva el caso sin repetir el comando.',fieldErrors:[]});return;}
      this.cancellationUncertain.set(false);this.cancellationTarget.set(null);this.detailOpen.set(false);this.selectedAssignment.set(null);
      this.notice.set('El servidor informa que la oferta está cancelada; el servicio se conserva salvo cancelación expresa separada.');this.load();
    },error:e=>this.error.set(this.apiErrors.toUiError(e))});
  }

  canEdit(_assignment: Assignment): boolean {
    return false;
  }

  canReserve(_assignment: Assignment): boolean {
    return false;
  }

  canStart(_assignment: Assignment): boolean {
    return false;
  }

  canComplete(_assignment: Assignment): boolean {
    return false; // Ruta finalizada is the responsible conductor's declaration.
  }

  canCancel(assignment: Assignment): boolean {
    if(assignment.serviceId&&assignment.status==='EN_SERVICIO')return false;
    return assignment.status === 'PENDING_RESPONSE'
      || assignment.status === 'SCHEDULED'
      || assignment.status === 'EN_SERVICIO';
  }

  statusLabel(status: AssignmentStatus): string {
    return ({
      PENDING_RESPONSE: 'Pendiente de respuesta',
      SCHEDULED: 'Programada',
      EN_SERVICIO: 'En servicio',
      INTERRUPTED: 'Interrumpida',
      TRANSFERRED: 'Tramo relevado',
      COMPLETED: 'Completada',
      REJECTED: 'Rechazada',
      CANCELLED: 'Cancelada',
      EXPIRED: 'Vencida',
    } as Record<AssignmentStatus, string>)[status];
  }

  responseModeLabel(mode: AssignmentResponseMode): string {
    return mode === 'MOBILE_CONFIRMATION'
      ? 'Confirmación móvil del conductor'
      : 'Programación directa del ADMIN';
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

  pendingActionTitle(): string {
    return ({ reserve: 'Reservar recursos', start: 'Iniciar servicio', complete: 'Completar servicio' } as Record<AssignmentActionKind, string>)[this.pendingAction()?.kind ?? 'reserve'];
  }

  pendingActionMessage(): string {
    const kind = this.pendingAction()?.kind;
    if (kind === 'reserve') return 'Se reservará el conductor para esta programación. ¿Deseas continuar?';
    if (kind === 'start') return 'El conductor y el vehículo pasarán a servicio. ¿Deseas iniciar?';
    return 'El servicio se marcará como completado y los recursos serán liberados. ¿Deseas continuar?';
  }

  pendingActionLabel(): string {
    if (this.actionId() !== null) return 'Procesando…';
    return ({ reserve: 'Reservar', start: 'Iniciar', complete: 'Completar' } as Record<AssignmentActionKind, string>)[this.pendingAction()?.kind ?? 'reserve'];
  }

  cancelPendingAction(): void {
    if (this.actionId() === null) {
      this.pendingAction.set(null);
    }
  }

  confirmPendingAction(): void {
    const pending = this.pendingAction();
    if (pending === null || this.actionId() !== null) {
      return;
    }
    const { assignment, kind } = pending;
    const request = kind === 'reserve'
      ? this.api.reserveAssignment(assignment.id, { version: assignment.version })
      : kind === 'start'
        ? this.api.startAssignment(assignment.id, { version: assignment.version })
        : this.api.completeAssignment(assignment.id, { version: assignment.version });
    const notice = kind === 'reserve'
      ? 'Conductor reservado para la asignación.'
      : kind === 'start'
        ? 'Servicio iniciado correctamente.'
        : 'Servicio completado y recursos liberados.';

    this.actionId.set(assignment.id);
    this.error.set(null);
    request.pipe(finalize(() => this.actionId.set(null))).subscribe({
      next: () => {
        this.notice.set(notice);
        this.pendingAction.set(null);
        this.detailOpen.set(false);
        this.selectedAssignment.set(null);
        this.load();
      },
      error: (error: unknown) => {
        this.pendingAction.set(null);
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar la asignación.'));
      },
    });
  }

  private loadResources(): void {
    this.managementApi.listDrivers({ page: 0, size: 100, sort: 'fullName,asc', active: true }).subscribe({
      next: (response) => this.drivers.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
    });
    this.managementApi.listVehicles({ page: 0, size: 100, sort: 'plate,asc', active: true }).subscribe({
      next: (response) => this.vehicles.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos.')),
      });
  }

  private loadEligibleVehicles(driverId: string, keepVehicleId?: string): void {
    this.linkedVehicleIds.set(null);
    if (!driverId) {
      return;
    }
    this.loadingEligibleVehicles.set(true);
    this.managementApi.getDriver(driverId)
      .pipe(finalize(() => this.loadingEligibleVehicles.set(false)))
      .subscribe({
        next: (detail) => {
          this.linkedVehicleIds.set(new Set(detail.vehicles.map((vehicle) => vehicle.vehicleId)));
          if (keepVehicleId && detail.vehicles.some((vehicle) => vehicle.vehicleId === keepVehicleId)) {
            this.form.controls.vehicleId.setValue(keepVehicleId);
          }
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos vinculados.')),
      });
  }

  driverStatusLabel(status: string): string {
    return ({ DISPONIBLE: 'Disponible', RESERVADO: 'Reservado', EN_SERVICIO: 'En servicio', ATENCION: 'Atención segura pendiente', DESCANSO: 'Descanso', NO_DISPONIBLE: 'No disponible' } as Record<string, string>)[status] ?? 'No disponible';
  }

  vehicleStatusLabel(status: string): string {
    return ({ DISPONIBLE: 'Disponible', EN_SERVICIO: 'En servicio', ATENCION: 'Atención segura pendiente', MANTENIMIENTO: 'Mantenimiento', INACTIVO: 'Inactivo' } as Record<string, string>)[status] ?? 'No disponible';
  }

  private filterInstant(value: string, endOfDay: boolean): string | undefined {
    if (!value) return undefined;
    const timestamp = Date.parse(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
    return Number.isNaN(timestamp) ? undefined : new Date(timestamp).toISOString();
  }

  private payloadFromForm(): AssignmentPayload | null {
    const value = this.form.getRawValue();
    const scheduledAt = this.toIso(value.scheduledAt);
    const scheduledEndAt = this.toIso(value.scheduledEndAt);
    if (scheduledAt === null || scheduledEndAt === null || scheduledEndAt <= scheduledAt) {
      return null;
    }
    return {
      serviceTypeVersionId:value.serviceTypeVersionId,
      driverId: value.driverId,
      routeVersionId: value.routeVersionId,
      vehicleId: value.vehicleId,
      originText: value.originText.trim(),
      destinationText: value.destinationText.trim(),
      scheduledAt,
      scheduledEndAt,
      notes: value.notes.trim() || undefined,
      responseMode: 'MOBILE_CONFIRMATION',
    };
  }

  private toIso(value: string): string | null {
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? null : new Date(timestamp).toISOString();
  }

  private toDateTimeLocal(value: string): string {
    const date = new Date(value);
    const pad = (number: number) => number.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  private idempotencyKey(): string {
    return typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  private isAssignmentStatus(value: string): value is AssignmentStatus {
    return (ASSIGNMENT_STATUSES as readonly string[]).includes(value);
  }

  private resetForm(close = true): void {
    this.reofferTarget.set(null);this.reofferKey=null;this.reofferReason.reset('');
    this.creationKey=null;
    this.editing.set(null);
    this.form.reset({
      driverId: '',
      routeVersionId: '',
      vehicleId: '',
      originText: '',
      destinationText: '',
      scheduledAt: '',
      scheduledEndAt: '',
      notes: '',
    });
    this.linkedVehicleIds.set(null);
    if (close) {
      this.formOpen.set(false);
    }
  }
}
