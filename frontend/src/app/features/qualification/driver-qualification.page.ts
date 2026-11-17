import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, ChangeDetectionStrategy, Component, ElementRef, inject, Injector, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Observable, Subscription } from 'rxjs';
import { DocumentaryDocument } from '../../core/onboarding/onboarding.models';
import { ManagementApiService } from '../../core/management/management-api.service';
import { QualificationApiService } from '../../core/qualification/qualification-api.service';
import {
  Qualification, QualificationCommand, QualificationReview, QualificationScope, UnitKind,
} from '../../core/qualification/qualification.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

type GuideSection = 'personal' | 'reviews' | 'period' | 'activation';
interface PendingConfirmation {
  readonly action: 'period' | 'activate';
  readonly version: number;
  readonly reason: string;
  readonly period?: { vehicleId: string; kind: UnitKind; timeMode: 'DATE' | 'INSTANT'; start: string; end: string };
}

@Component({
  selector: 'rf-driver-qualification-page',
  imports: [DatePipe, FormsModule, RouterLink, PageHeaderComponent],
  templateUrl: './driver-qualification.page.html',
  styleUrl: './driver-qualification.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DriverQualificationPage implements OnDestroy {
  private readonly api = inject(QualificationApiService);
  private readonly management = inject(ManagementApiService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  readonly driverId = inject(ActivatedRoute).snapshot.paramMap.get('driverId') ?? '';
  private query?: Subscription;
  private mutation?: Subscription;
  private vehiclesQuery?: Subscription;
  readonly state = signal<Qualification | null>(null);
  readonly restricted = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly mustRefresh = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly lastOperationKey = signal<string | null>(null);
  readonly uncertain = signal(false);
  readonly vehicleReferences = signal<readonly { id: string; status: string }[]>([]);
  readonly vehiclesLoading = signal(false);
  readonly vehiclePage = signal(0);
  readonly vehicleTotalPages = signal(0);
  readonly vehiclesLoaded = signal(false);
  readonly vehicleError = signal<string | null>(null);
  readonly restrictedVehicleLabels = signal<Readonly<Record<string, string>>>({});
  readonly pendingConfirmation = signal<PendingConfirmation | null>(null);
  readonly guideSteps: readonly { id: GuideSection; title: string; description: string; action: string }[] = [
    { id: 'personal', title: 'Documentos personales', description: 'Comprueba la revisión y vigencia de la misma persona.', action: 'Abrir documentos en expediente restringido' },
    { id: 'reviews', title: 'Empresa y unidad', description: 'Revisa los requisitos aplicables del catálogo del servidor.', action: 'Abrir revisión en expediente restringido' },
    { id: 'period', title: 'Vínculo vigente', description: 'Elige una unidad revisada y autoriza sus fechas.', action: 'Elegir unidad y vigencia' },
    { id: 'activation', title: 'Habilitar conductor', description: 'Confirma la decisión con un motivo. El servidor valida las condiciones.', action: 'Ir a habilitación' },
  ];
  private pendingFocus?: GuideSection;
  private confirming = false;
  reason = '';
  evidenceReference = '';
  vehicleId = '';
  kind: UnitKind = 'OWN';
  timeMode: 'DATE' | 'INSTANT' = 'DATE';
  start = '';
  end = '';
  scope: QualificationScope = 'VEHICLE';
  reviewVehicleId = '';
  reviewDrafts: QualificationReview[] = [];
  personalDrafts: DocumentaryDocument[] = [];
  originalReviewed = false;
  samePersonConfirmed = false;
  personalReviewReference = '';
  correctionVehicleId = '';
  correctionPlate = '';
  correctionOriginalReviewed = false;
  correctionReviewReference = '';
  decisionVehicles: Record<string, string> = {};

  readonly reviewResults = ['POR_CONTRASTAR', 'OBSERVADO', 'CONFORME', 'NO_APLICA_JUSTIFICADO'];
  readonly expiryModes = ['UNKNOWN', 'DATED', 'NO_EXPIRY'];

  constructor() { this.load(); }

  loadVehicleReferences(page = 0): void {
    if (this.vehiclesLoading() || page < 0 || this.saving()) return;
    this.vehiclesLoading.set(true);
    this.vehicleError.set(null);
    const requestedRestricted = this.restricted();
    this.vehiclesQuery = this.management.listVehicles({ page, size: 50, active: true }).pipe(
      finalize(() => this.vehiclesLoading.set(false)),
    ).subscribe({
      next: (result) => {
        // Keep only references/status; ordinary screens never retain plate or owner details.
        this.vehicleReferences.set(result.items.filter((vehicle) => vehicle.active).map(({ id, status }) => ({ id, status })));
        // Plates are retained only after the explicit opening of the restricted file.
        if (requestedRestricted && this.restricted()) {
          this.restrictedVehicleLabels.update((labels) => ({ ...labels,
            ...Object.fromEntries(result.items.filter((vehicle) => vehicle.active).map(({ id, plate }) => [id, plate])),
          }));
        }
        this.vehiclesLoaded.set(true);
        this.vehiclePage.set(page);
        this.vehicleTotalPages.set(result.totalPages);
      },
      error: (error: unknown) => {
        this.vehicleReferences.set([]);
        this.restrictedVehicleLabels.set({});
        this.vehicleError.set('No se pudieron consultar las unidades. Consulta de nuevo; no se ha creado ningún vínculo.');
        if (error instanceof HttpErrorResponse && error.status === 403) {
          this.clearRestricted();
          this.state.set(null);
          this.restricted.set(false);
          this.mustRefresh.set(true);
          this.error.set(this.failure(error));
        }
      },
    });
  }

  load(restricted = this.restricted()): void {
    if (!this.driverId || this.saving()) return;
    this.query?.unsubscribe();
    this.vehiclesQuery?.unsubscribe();
    this.clearRestricted();
    this.state.set(null);
    this.restricted.set(restricted);
    this.loading.set(true);
    this.error.set(null);
    this.query = this.api.get(this.driverId, restricted).pipe(
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (state) => {
        const wasUncertain = this.uncertain();
        this.apply(state);
        this.mustRefresh.set(false);
        this.uncertain.set(false);
        if (wasUncertain) this.notice.set('Estado reconsultado. Revisa el resultado y conserva la referencia de operación; no se ha reenviado el cambio.');
        const section = this.pendingFocus;
        this.pendingFocus = undefined;
        if (section) {
          if (section === 'period') this.loadVehicleReferences();
          afterNextRender(() => this.focusSection(section), { injector: this.injector });
        }
      },
      error: (error: unknown) => {
        this.pendingFocus = undefined;
        this.restricted.set(false);
        this.clearRestricted();
        this.error.set(this.failure(error));
      },
    });
  }

  closeRestricted(): void {
    this.query?.unsubscribe();
    const pendingMutation = this.saving();
    if (pendingMutation) {
      this.uncertain.set(true);
      this.mustRefresh.set(true);
      this.mutation?.unsubscribe();
    }
    this.clearRestricted();
    this.state.set(null);
    this.restricted.set(false);
    if (!pendingMutation) this.load(false);
  }

  unitLabel(id: string): string {
    const plate = this.restricted() ? this.restrictedVehicleLabels()[id] : null;
    return plate ? `Unidad ${plate}` : `Unidad terminada en ${id.slice(-6)}`;
  }

  vehicleStatus(status: string): string {
    return ({ DISPONIBLE: 'Disponible', ASIGNADO: 'Asignada', EN_SERVICIO: 'En servicio',
      MANTENIMIENTO: 'En mantenimiento', INACTIVO: 'Inactiva', ATENCION: 'Requiere atención',
    } as Record<string, string>)[status] ?? 'Estado por consultar';
  }

  selectVehicle(id: string): void {
    if (this.saving() || !this.vehicleReferences().some((vehicle) => vehicle.id === id)) return;
    this.vehicleId = this.reviewVehicleId = id;
    this.pendingConfirmation.set(null);
    this.selectReviewScope();
  }

  openUnitPicker(): void {
    if (this.saving() || this.loading()) return;
    if (!this.restricted()) {
      this.pendingFocus = 'period';
      this.load(true);
    } else {
      if (!this.vehiclesLoaded()) this.loadVehicleReferences();
      this.focusSection('period');
    }
  }

  openGuidedStep(section: GuideSection): void {
    if ((section === 'personal' || section === 'reviews') && !this.restricted()) {
      this.pendingFocus = section;
      this.load(true);
    } else this.focusSection(section);
  }

  focusSection(section: GuideSection): void {
    const heading = this.host.nativeElement.querySelector<HTMLElement>(`#qualification-${section}-title`);
    let parent = heading?.parentElement;
    while (parent && parent !== this.host.nativeElement) {
      if (parent instanceof HTMLDetailsElement) parent.open = true;
      parent = parent.parentElement;
    }
    heading?.focus();
  }

  continueReview(): void {
    const current = this.state();
    const pending = this.guideSteps.find((step) => current?.blockers.some((blocker) => this.blockerInfo(blocker).section === step.id));
    this.openGuidedStep(pending?.id ?? (current && !['CURRENT', 'VALID'].includes(current.personalDocumentsStatus) ? 'personal' : 'activation'));
  }

  blockerInfo(code: string): { title: string; description: string; section: GuideSection } {
    const known: Record<string, { title: string; description: string; section: GuideSection }> = {
      AUTHORIZED_UNIT_PERIOD_REQUIRED: { title: 'Falta un vínculo de unidad vigente', description: 'Selecciona una unidad revisada y autoriza un período que cubra la operación. El servidor comprueba los cruces de vigencia.', section: 'period' },
      UNIT_PERIOD_REQUIRED: { title: 'Falta un vínculo de unidad vigente', description: 'Selecciona una unidad revisada y define su período autorizado.', section: 'period' },
      PERSONAL_APPROVAL_REQUIRED: { title: 'Falta acreditar la aprobación personal', description: 'Consulta la solicitud de incorporación y su revisión. Habilitar no aprueba documentos.', section: 'personal' },
      PERSONAL_CATALOG_REVIEW_REQUIRED: { title: 'La documentación personal requiere revisión', description: 'Contrasta los requisitos del catálogo vigente en el expediente restringido.', section: 'personal' },
      PERSONAL_DOCUMENTS_EXPIRED: { title: 'Hay documentos personales vencidos', description: 'Revisa y registra su renovación con los originales.', section: 'personal' },
      PERSONAL_DOCUMENTS_REVIEW_REQUIRED: { title: 'Hay documentos personales por revisar', description: 'Abre el expediente y comprueba sus requisitos y vigencias.', section: 'personal' },
      ACCOUNT_IDENTITY_GROUP_OR_DRIVER_REQUIRED: { title: 'La cuenta, identidad, grupo o activación requieren validación', description: 'El servidor comprobará estas condiciones al habilitar. Si el conductor está inactivo, esa activación forma parte de la decisión final.', section: 'activation' },
      OPERATIONAL_SUSPENSION: { title: 'Existe una suspensión operativa', description: 'Revisa el motivo y la evidencia antes de levantar la suspensión en los controles avanzados.', section: 'activation' },
      DRIVER_SAFETY_ATTENTION: { title: 'El conductor requiere atención de seguridad', description: 'Revisa los servicios y las atenciones pendientes antes de continuar.', section: 'activation' },
      VEHICLE_SAFETY_ATTENTION: { title: 'La unidad requiere atención de seguridad', description: 'Revisa la atención de la unidad antes de continuar.', section: 'reviews' },
      VEHICLE_MAINTENANCE_BLOCK: { title: 'La unidad tiene mantenimiento pendiente', description: 'Consulta el mantenimiento que afecta el período de operación.', section: 'reviews' },
      VEHICLE_ACTIVE_REQUIRED: { title: 'La unidad no está disponible para operar', description: 'Consulta el estado de la unidad seleccionada antes de habilitar.', section: 'reviews' },
      UNIT_OCCUPIED_BY_OTHER_DRIVER: { title: 'La unidad está en servicio con otro conductor', description: 'Consulta la asignación vigente antes de continuar.', section: 'period' },
      OPERATIONAL_FEATURE_DISABLED: { title: 'La operación no está habilitada para esta instalación', description: 'Solicita revisión de la configuración al responsable y vuelve a consultar el estado.', section: 'activation' },
    };
    if (known[code]) return known[code];
    const document = /^(CON|VEH|EMP)-\d{2}_(REVIEW_REQUIRED|VALIDITY_REQUIRED|APPLICABILITY_REQUIRED|UNIT_CHANGED_REVIEW_REQUIRED)$/.exec(code);
    if (document) {
      const personal = document[1] === 'CON';
      const subject = personal ? 'personal' : document[1] === 'VEH' ? 'de la unidad' : 'de la empresa';
      const condition = document[2] === 'VALIDITY_REQUIRED' ? 'vigencia por revisar'
        : document[2] === 'APPLICABILITY_REQUIRED' ? 'aplicabilidad por confirmar'
        : document[2] === 'UNIT_CHANGED_REVIEW_REQUIRED' ? 'datos de la unidad que requieren nuevo cotejo' : 'revisión pendiente';
      return { title: `Documentación ${subject}: ${condition}`, description: 'Abre el expediente restringido y comprueba los requisitos del catálogo. No se presume su cumplimiento.', section: personal ? 'personal' : 'reviews' };
    }
    // Never echo an unknown server value: it can contain technical or private details.
    return { title: 'Hay una condición que requiere revisión', description: 'Consulta el estado actual. Si continúa, solicita revisión al responsable antes de habilitar.', section: 'activation' };
  }

  stepStatus(section: GuideSection): string {
    const current = this.state();
    if (!current) return 'Por consultar';
    if (section === 'activation') return current.operationalEnabled ? 'Confirmado' : 'Pendiente';
    if (current.blockers.some((blocker) => this.blockerInfo(blocker).section === section)) return 'Pendiente';
    if (section === 'personal' && !['CURRENT', 'VALID'].includes(current.personalDocumentsStatus)) return 'Por revisar';
    if (section === 'reviews' && current.blockers.some((blocker) => ['AUTHORIZED_UNIT_PERIOD_REQUIRED', 'UNIT_PERIOD_REQUIRED'].includes(blocker))) return 'Por comprobar con la unidad';
    return 'Confirmado';
  }

  canActivate(): boolean {
    const current = this.state();
    return !!current && !current.operationalEnabled && !current.suspended && !this.loading() && !this.saving()
      && !this.mustRefresh() && !this.uncertain()
      // This aggregate can include the driver's inactive flag, which activation resolves.
      // Every other condition (including unknown values) still requires prior review.
      && current.blockers.every((blocker) => blocker === 'ACCOUNT_IDENTITY_GROUP_OR_DRIVER_REQUIRED');
  }

  useSuggestedReason(): void { this.reason = 'Autorización operativa revisada por ADMIN'; this.pendingConfirmation.set(null); }

  prepareActivation(): void {
    if (!this.commandReady()) return;
    if (!this.canActivate()) {
      this.error.set('Resuelve las condiciones pendientes y consulta el estado antes de habilitar.');
      return;
    }
    this.error.set(null);
    this.pendingConfirmation.set({ action: 'activate', version: this.state()!.version, reason: this.reason.trim() });
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('#qualification-confirmation-title')?.focus(), { injector: this.injector });
  }

  preparePeriod(): void {
    if (!this.commandReady() || !this.validPeriod()) return;
    this.error.set(null);
    this.pendingConfirmation.set({ action: 'period', version: this.state()!.version, reason: this.reason.trim(),
      period: { vehicleId: this.vehicleId.trim(), kind: this.kind, timeMode: this.timeMode, start: this.start, end: this.end } });
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('#qualification-confirmation-title')?.focus(), { injector: this.injector });
  }

  cancelConfirmation(): void {
    const action = this.pendingConfirmation()?.action;
    this.pendingConfirmation.set(null);
    this.host.nativeElement.querySelector<HTMLElement>(action === 'period' ? '#qualification-prepare-period' : '#qualification-prepare-activation')?.focus();
  }

  confirmPending(): void {
    const pending = this.pendingConfirmation();
    if (!pending || !this.commandReady()) return;
    const currentPeriod = { vehicleId: this.vehicleId.trim(), kind: this.kind, timeMode: this.timeMode, start: this.start, end: this.end };
    if (pending.version !== this.state()?.version || pending.reason !== this.reason.trim()
      || (pending.period && JSON.stringify(pending.period) !== JSON.stringify(currentPeriod))) {
      this.pendingConfirmation.set(null);
      this.error.set('El resumen cambió. Revisa los datos y prepara la confirmación de nuevo.');
      return;
    }
    this.confirming = true;
    try {
      if (pending.action === 'period') this.createPeriod(); else this.act('activate');
    } finally { this.confirming = false; this.pendingConfirmation.set(null); }
  }

  resultLabel(value: string): string {
    return ({ POR_CONTRASTAR: 'Por contrastar', OBSERVADO: 'Observado', CONFORME: 'Conforme',
      NO_APLICA_JUSTIFICADO: 'No aplica, con justificación', PENDING: 'Pendiente', UNKNOWN: 'Vigencia por confirmar',
      DATED: 'Con fecha de vencimiento', NO_EXPIRY: 'Sin vencimiento acreditado', CURRENT: 'Vigente', VALID: 'Vigente',
      REVIEW_REQUIRED: 'Requiere revisión', AUTHORIZED: 'Autorizado', REVOKED: 'Revocado',
      PENDING_REVIEW: 'Pendiente de revisión', APPROVED: 'Aprobada', REJECTED: 'Rechazada',
    } as Record<string, string>)[value] ?? 'Por consultar';
  }

  fieldLabel(key: string): string {
    return ({ documentType: 'Tipo de documento', documentNumber: 'Número de documento', fullName: 'Nombre para cotejo',
      issuer: 'Emisor', licenseNumber: 'Número de licencia', licenseClass: 'Clase de licencia', licenseCategory: 'Categoría de licencia',
      restrictions: 'Restricciones relevantes', titleNumber: 'Número de habilitación', authority: 'Autoridad',
      serviceScope: 'Ámbito del servicio', operatorReference: 'Referencia del operador', requirementName: 'Requisito aplicable',
      minimumReference: 'Referencia mínima', eligibilityResult: 'Resultado de elegibilidad', reviewDate: 'Fecha de comprobación',
      reference: 'Referencia documental', plate: 'Placa para cotejo', category: 'Categoría', bodyType: 'Carrocería',
      brand: 'Marca', model: 'Modelo', year: 'Año', insuranceType: 'Tipo de seguro', center: 'Centro de inspección',
      inspectionType: 'Tipo de inspección', inspectionResult: 'Resultado de inspección', titleName: 'Nombre de habilitación',
      holderReference: 'Referencia del titular', modality: 'Modalidad', variant: 'Variante de servicio',
      titleReference: 'Referencia de habilitación', fleetScope: 'Alcance de flota', routeReference: 'Referencia de ruta',
      unitsScope: 'Alcance de unidades',
    } as Record<string, string>)[key] ?? 'Dato del requisito para cotejo';
  }

  personalTitle(id: string): string {
    return ({ 'CON-01': 'Identidad de la persona', 'CON-02': 'Licencia compatible con el servicio',
      'CON-03': 'Habilitación personal aplicable', 'CON-04': 'Otros requisitos personales aplicables',
      'CON-05': 'Condiciones personales de colectivo',
    } as Record<string, string>)[id] ?? 'Documento personal del expediente';
  }

  selectReviewScope(): void {
    this.reviewDrafts = [];
    const current = this.state();
    if (!current || !this.restricted()) return;
    const selectedVehicle = this.scope === 'VEHICLE' ? this.reviewVehicleId.trim() : null;
    this.reviewDrafts = current.catalog.filter((requirement) => requirement.scope === this.scope).map((requirement) => {
      const previous = current.reviews.find((review) => review.scope === this.scope
        && review.requirementId === requirement.id && review.vehicleId === selectedVehicle);
      return {
        scope: this.scope, requirementId: requirement.id, vehicleId: selectedVehicle,
        fields: Object.fromEntries(requirement.fieldKeys.map((key) => [key, previous?.fields[key] ?? ''])),
        expiryMode: previous?.expiryMode ?? 'UNKNOWN', validUntil: this.localDate(previous?.validUntil),
        applicability: previous?.applicability ?? 'POR_CONTRASTAR', condition: previous?.condition ?? '',
        sourceReference: previous?.sourceReference ?? '', result: previous?.result ?? 'POR_CONTRASTAR',
        reviewReference: previous?.reviewReference ?? '',
      };
    });
  }

  reviewTitle(id: string): string { return this.state()?.catalog.find((item) => item.scope === this.scope && item.id === id)?.title ?? id; }

  fields(document: { fields: Record<string, string> }): string[] { return Object.keys(document.fields); }

  saveReviews(): void {
    const base = this.command();
    if (!base || !this.restricted()) return;
    if (!this.reviewDrafts.length || (this.scope === 'VEHICLE' && !this.reviewVehicleId.trim())) {
      this.error.set('Selecciona un catálogo registrado y la referencia de unidad cuando corresponda.'); return;
    }
    const documents = this.reviewDrafts.map((review) => ({
      requirementId: review.requirementId, fields: { ...review.fields },
      expiryMode: review.expiryMode, validUntil: this.documentInstant(review.expiryMode, review.validUntil),
      applicability: review.applicability, condition: review.condition, sourceReference: review.sourceReference,
      result: review.result, reviewReference: review.reviewReference,
    }));
    this.mutate(this.api.reviews(this.driverId, { ...base, scope: this.scope,
      vehicleId: this.scope === 'VEHICLE' ? this.reviewVehicleId.trim() : null, documents }), base.operationKey);
  }

  createPeriod(): void {
    if (!this.confirming) { this.preparePeriod(); return; }
    const base = this.command();
    if (!base || !this.validPeriod()) return;
    this.mutate(this.api.period(this.driverId, { ...base, vehicleId: this.vehicleId.trim(), kind: this.kind,
      startAt: this.timeMode === 'INSTANT' ? this.start : null,
      startDate: this.timeMode === 'DATE' ? this.start : null,
      endAt: this.timeMode === 'INSTANT' ? this.end || null : null,
      endDate: this.timeMode === 'DATE' ? this.end || null : null,
    }), base.operationKey);
  }

  revoke(periodId: string): void {
    const base = this.command();
    if (base) this.mutate(this.api.revoke(this.driverId, periodId, base), base.operationKey);
  }

  act(action: 'suspend' | 'resume' | 'activate'): void {
    if (action === 'activate' && !this.confirming) { this.prepareActivation(); return; }
    if (action === 'activate' && !this.canActivate()) return;
    const base = this.command();
    if (!base) return;
    if (action === 'suspend' && !this.evidenceReference.trim()) {
      this.error.set('La suspensión requiere una referencia de comprobación sin datos personales.'); return;
    }
    this.mutate(this.api.action(this.driverId, action, { ...base,
      ...(action === 'suspend' ? { evidenceReference: this.evidenceReference.trim() } : {}),
    }), base.operationKey);
  }

  savePersonal(): void {
    const base = this.command();
    if (!base || !this.restricted()) return;
    if (!this.personalDrafts.length || !this.originalReviewed || !this.samePersonConfirmed || !this.personalReviewReference.trim()) {
      this.error.set('Revisa los originales, confirma que es la misma persona e indica la referencia de comprobación.'); return;
    }
    const documents = this.personalDrafts.map((document) => ({ ...document, fields: { ...document.fields },
      validUntil: this.documentInstant(document.expiryMode, document.validUntil),
    }));
    this.mutate(this.api.personal(this.driverId, { ...base, documents, samePersonConfirmed: true,
      originalReviewed: true, reviewReference: this.personalReviewReference.trim() }), base.operationKey);
  }

  decide(requestId: string, approved: boolean): void {
    const base = this.command();
    if (!base) return;
    const request = this.state()?.unitRequests.find((item) => item.id === requestId);
    const vehicleId = this.decisionVehicles[requestId]?.trim() || request?.vehicleId || null;
    if (approved && !vehicleId) { this.error.set('Selecciona la referencia de una unidad existente y revisada.'); return; }
    this.mutate(this.api.decide(this.driverId, requestId, { ...base, approved, vehicleId }), base.operationKey);
  }

  correctVehicle(): void {
    const base = this.command();
    if (!base || !this.restricted()) return;
    if (!this.correctionVehicleId.trim() || !/^[A-Za-z0-9-]{3,15}$/.test(this.correctionPlate.trim())
      || !this.correctionOriginalReviewed || !this.correctionReviewReference.trim()) {
      this.error.set('Indica la unidad del expediente, placa de 3 a 15 caracteres, referencia y cotejo del original.'); return;
    }
    this.mutate(this.api.vehicleCorrection(this.driverId, { ...base, vehicleId: this.correctionVehicleId.trim(),
      plate: this.correctionPlate.trim(), originalReviewed: true, reviewReference: this.correctionReviewReference.trim(),
    }), base.operationKey);
  }

  ngOnDestroy(): void {
    this.query?.unsubscribe();
    this.mutation?.unsubscribe();
    this.vehiclesQuery?.unsubscribe();
    this.vehicleReferences.set([]);
    this.clearRestricted();
    this.state.set(null);
    this.reason = this.evidenceReference = this.vehicleId = this.start = this.end = '';
  }

  private command(): QualificationCommand | null {
    if (!this.commandReady()) return null;
    return { expectedVersion: this.state()!.version, operationKey: crypto.randomUUID(), reason: this.reason.trim() };
  }

  private commandReady(): boolean {
    const current = this.state();
    if (!current || this.loading() || this.saving() || this.mustRefresh() || this.uncertain()) return false;
    if (!this.reason.trim() || this.reason.trim().length > 500) {
      this.error.set('Ingresa un motivo de 1 a 500 caracteres, sin datos personales ni secretos.');
      this.host.nativeElement.querySelector<HTMLElement>('#qualification-reason')?.focus();
      return false;
    }
    return true;
  }

  private validPeriod(): boolean {
    if (!this.vehicleId.trim() || !this.start || (this.kind === 'COMPANY' && !this.end)) {
      this.error.set('Indica la unidad y el inicio. La unidad de empresa requiere un fin.');
      const selector = !this.vehicleId.trim() ? '#qualification-unit-picker-title'
        : !this.start ? '#qualification-start-date, #qualification-start-at' : '#qualification-end-date, #qualification-end-at';
      this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
      return false;
    }
    // Instants require an explicit UTC offset or Z; the browser timezone is never inferred.
    if (this.timeMode === 'INSTANT' && (!this.validInstant(this.start) || (this.end && !this.validInstant(this.end)))) {
      this.error.set('Indica instantes ISO con Z o desplazamiento, por ejemplo 2026-10-18T08:00:00-05:00.');
      this.host.nativeElement.querySelector<HTMLElement>('#qualification-start-at')?.focus();
      return false;
    }
    if (this.end && (this.timeMode === 'DATE' ? this.end < this.start : Date.parse(this.end) <= Date.parse(this.start))) {
      this.error.set('El fin debe ser posterior al inicio; por fecha puede ser el mismo día incluido en Lima.');
      this.host.nativeElement.querySelector<HTMLElement>('#qualification-end-date, #qualification-end-at')?.focus();
      return false;
    }
    return true;
  }

  private mutate(request: Observable<Qualification>, operationKey: string): void {
    this.saving.set(true);
    this.lastOperationKey.set(operationKey);
    this.error.set(null);
    this.notice.set(null);
    this.mutation = request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (state) => {
        this.apply(state);
        this.reason = this.evidenceReference = '';
        this.originalReviewed = this.samePersonConfirmed = false;
        this.personalReviewReference = '';
        this.clearVehicleCorrection();
        this.notice.set(state.operationalEnabled
          ? 'Cambio confirmado por el servidor. El conductor está habilitado para operar.'
          : 'Cambio confirmado por el servidor. Revisa las condiciones pendientes antes de habilitar al conductor.');
      },
      error: (error: unknown) => {
        this.mustRefresh.set(true);
        this.uncertain.set(!(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500);
        this.clearRestricted();
        this.restricted.set(false);
        this.state.set(null);
        this.error.set(this.failure(error));
      },
    });
  }

  private apply(state: Qualification): void {
    // Restricted response fields never survive a return to the ordinary view.
    this.state.set(this.restricted() ? state : { ...state, personalDocuments: [],
      reviews: state.reviews.map((review) => ({ ...review, fields: {}, reviewReference: null, sourceReference: null, condition: null })),
    });
    this.selectReviewScope();
    this.personalDrafts = this.restricted() ? state.personalDocuments.map((document) => ({ ...document,
      fields: { ...document.fields }, validUntil: this.localDate(document.validUntil),
    })) : [];
  }

  private clearRestricted(): void {
    this.vehiclesQuery?.unsubscribe();
    this.vehicleReferences.set([]);
    this.restrictedVehicleLabels.set({});
    this.vehiclesLoaded.set(false);
    this.vehicleError.set(null);
    this.pendingConfirmation.set(null);
    this.reviewDrafts = [];
    this.personalDrafts = [];
    this.reviewVehicleId = this.personalReviewReference = '';
    this.originalReviewed = this.samePersonConfirmed = false;
    this.decisionVehicles = {};
    this.clearVehicleCorrection();
  }

  private clearVehicleCorrection(): void {
    this.correctionVehicleId = this.correctionPlate = this.correctionReviewReference = '';
    this.correctionOriginalReviewed = false;
  }

  private documentInstant(mode: string, value: string | null): string | null {
    // Date inputs include the whole selected day in Lima: the stored bound is exclusive.
    return mode === 'DATED' && value
      ? new Date(new Date(`${value}T00:00:00-05:00`).getTime() + 86_400_000).toISOString() : null;
  }

  private localDate(value: string | null | undefined): string | null {
    if (!value) return null;
    const instant = new Date(value);
    const exclusiveLimaMidnight = instant.getUTCHours() === 5 && instant.getUTCMinutes() === 0
      && instant.getUTCSeconds() === 0 && instant.getUTCMilliseconds() === 0;
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(
      exclusiveLimaMidnight ? new Date(instant.getTime() - 1) : instant,
    );
  }

  private validInstant(value: string): boolean {
    return /T\d{2}:\d{2}.*(?:Z|[+-]\d{2}:\d{2})$/.test(value) && !Number.isNaN(Date.parse(value));
  }

  private failure(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    return ({ 400: 'Revisa los campos, fechas y referencias del contrato documental.',
      401: 'La sesión no permite esta consulta. Inicia sesión de nuevo.',
      403: 'No tienes permiso para esta revisión.',
      404: 'El conductor o recurso no está disponible para tu organización.',
      409: 'La versión o el estado cambió, o existe un conflicto de vigencia. Reconsulta antes de continuar.',
      422: 'La revisión no cumple las condiciones de habilitación.',
    } as Record<number, string>)[status] ?? 'No se confirmó el resultado. Conserva la clave de operación y reconsulta; no se reenviará automáticamente.';
  }
}
