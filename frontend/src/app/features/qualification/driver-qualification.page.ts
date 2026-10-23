import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnDestroy, signal } from '@angular/core';
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
    this.vehiclesQuery = this.management.listVehicles({ page, size: 50, active: true }).pipe(
      finalize(() => this.vehiclesLoading.set(false)),
    ).subscribe({
      next: (result) => {
        // Keep only references/status; ordinary screens never retain plate or owner details.
        this.vehicleReferences.set(result.items.filter((vehicle) => vehicle.active).map(({ id, status }) => ({ id, status })));
        this.vehiclePage.set(page);
        this.vehicleTotalPages.set(result.totalPages);
      },
      error: () => { this.vehicleReferences.set([]); this.error.set('No se pudieron consultar las referencias de unidad. No se ha creado ningún vínculo.'); },
    });
  }

  load(restricted = this.restricted()): void {
    if (!this.driverId || this.saving()) return;
    this.query?.unsubscribe();
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
      },
      error: (error: unknown) => this.error.set(this.failure(error)),
    });
  }

  closeRestricted(): void {
    this.query?.unsubscribe();
    if (this.saving()) {
      this.uncertain.set(true);
      this.mustRefresh.set(true);
      this.mutation?.unsubscribe();
    }
    this.clearRestricted();
    this.state.set(null);
    this.restricted.set(false);
    this.load(false);
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
    const base = this.command();
    if (!base) return;
    if (!this.vehicleId.trim() || !this.start || (this.kind === 'COMPANY' && !this.end)) {
      this.error.set('Indica la unidad y el inicio. La unidad de empresa requiere un fin.'); return;
    }
    // Instants require an explicit UTC offset or Z; the browser timezone is never inferred.
    if (this.timeMode === 'INSTANT' && (!this.validInstant(this.start) || (this.end && !this.validInstant(this.end)))) {
      this.error.set('Indica instantes ISO con Z o desplazamiento, por ejemplo 2026-10-18T08:00:00-05:00.'); return;
    }
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
    const current = this.state();
    if (!current || this.loading() || this.saving() || this.mustRefresh() || this.uncertain()) return null;
    if (!this.reason.trim() || this.reason.trim().length > 500) {
      this.error.set('Ingresa un motivo de 1 a 500 caracteres, sin datos personales ni secretos.'); return null;
    }
    return { expectedVersion: current.version, operationKey: crypto.randomUUID(), reason: this.reason.trim() };
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
        this.notice.set('Cambio confirmado por el servidor. La revisión y el vínculo no activan automáticamente al conductor.');
      },
      error: (error: unknown) => {
        this.mustRefresh.set(true);
        this.uncertain.set(!(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500);
        this.clearRestricted();
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
