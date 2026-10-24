import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, Observable, Subscription } from 'rxjs';

import { OnboardingApiService } from '../../core/onboarding/onboarding-api.service';
import { ConductorOnboardingExpediente, ConductorOnboardingSummary, DocumentaryDocument, DocumentaryExpediente, DocumentaryMutation } from '../../core/onboarding/onboarding.models';
import { ManagementApiService } from '../../core/management/management-api.service';
import { TransportGroup } from '../../core/management/management.models';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  selector: 'rf-onboarding-requests-page',
  imports: [DatePipe, FormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './onboarding-requests.page.html',
  styleUrl: './onboarding-requests.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingRequestsPage implements OnDestroy {
  private readonly api = inject(OnboardingApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly management = inject(ManagementApiService);
  private expedienteRequest?: Subscription;
  private documentaryRequest?: Subscription;
  private mutationRequest?: Subscription;
  private groupsRequest?: Subscription;

  readonly requests = signal<readonly ConductorOnboardingSummary[]>([]);
  readonly loading = signal(false);
  readonly listError = signal<string | null>(null);
  readonly selectedRequestId = signal<string | null>(null);
  readonly expediente = signal<ConductorOnboardingExpediente | null>(null);
  readonly expedienteLoading = signal(false);
  readonly expedienteError = signal<string | null>(null);
  readonly documentary = signal<DocumentaryExpediente | null>(null);
  readonly documentaryLoading = signal(false);
  readonly saving = signal(false);
  readonly reviewError = signal<string | null>(null);
  readonly reviewNotice = signal<string | null>(null);
  readonly mustRefresh = signal(false);
  readonly activeGroups = signal<readonly TransportGroup[]>([]);
  draftDocuments: DocumentaryDocument[] = [];
  modality: 'TAXI' | 'COLECTIVO' = 'TAXI';
  reason = '';
  deadline = '';
  groupId = '';
  reiteration = false;
  existingBindingConfirmed = false;

  readonly resultOptions = [
    { value: 'PENDING', label: 'Pendiente' }, { value: 'CONFORME', label: 'Conforme' },
    { value: 'OBSERVADO', label: 'Observado' }, { value: 'NO_APLICA_JUSTIFICADO', label: 'No aplica con justificación' },
    { value: 'POR_CONTRASTAR', label: 'Por contrastar' },
  ];
  readonly licenseCategories = ['A-IIA', 'A-IIB', 'A-IIIA', 'A-IIIB', 'A-IIIC'];

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) {
      return;
    }
    this.closeExpediente();
    this.loading.set(true);
    this.listError.set(null);
    this.api.list().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (requests) => this.requests.set(requests),
      error: (error: unknown) => {
        this.requests.set([]);
        this.listError.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'No tienes permiso para consultar estas solicitudes.'
          : 'No se pudieron cargar las solicitudes. Intenta nuevamente.');
      },
    });
  }

  openExpediente(requestId: string): void {
    this.closeExpediente();
    this.selectedRequestId.set(requestId);
    this.expedienteLoading.set(true);
    this.expedienteRequest = this.api.expediente(requestId).pipe(
      finalize(() => this.expedienteLoading.set(false)),
    ).subscribe({
      next: (expediente) => this.expediente.set(expediente),
      error: (error: unknown) => this.expedienteError.set(this.expedienteFailure(error)),
    });
  }

  closeExpediente(): void {
    this.expedienteRequest?.unsubscribe();
    this.expedienteRequest = undefined;
    this.documentaryRequest?.unsubscribe();
    this.mutationRequest?.unsubscribe();
    this.groupsRequest?.unsubscribe();
    this.documentary.set(null);
    this.draftDocuments = [];
    this.activeGroups.set([]);
    this.reviewError.set(null);
    this.reviewNotice.set(null);
    this.documentaryLoading.set(false);
    this.mustRefresh.set(false);
    this.saving.set(false);
    this.reason = this.deadline = this.groupId = '';
    this.existingBindingConfirmed = false;
    this.expediente.set(null);
    this.selectedRequestId.set(null);
    this.expedienteError.set(null);
    this.expedienteLoading.set(false);
  }

  ngOnDestroy(): void {
    this.closeExpediente();
    this.requests.set([]);
  }

  statusLabel(status: string): string {
    return ({ PENDING_REVIEW: 'Pendiente de revisión', RETURNED: 'Devuelta para subsanación',
      APPROVED: 'Aprobación personal', REJECTED: 'Rechazada' } as Record<string, string>)[status] ?? status;
  }

  loadDocumentary(): void {
    const id = this.selectedRequestId();
    if (!id || this.documentaryLoading() || this.saving()) return;
    this.documentaryRequest?.unsubscribe();
    this.documentaryLoading.set(true);
    this.reviewError.set(null);
    this.documentaryRequest = this.api.documentary(id).pipe(
      finalize(() => this.documentaryLoading.set(false)),
    ).subscribe({
      next: (detail) => { this.applyDocumentary(detail); this.mustRefresh.set(false); },
      error: () => this.reviewError.set('No se pudo consultar la revisión documental. Intenta actualizarla.'),
    });
  }

  saveDocumentary(): void {
    const id = this.selectedRequestId();
    const base = this.mutationBase();
    if (!id || !base) return;
    this.mutate(this.api.saveDocumentary(id, { ...base, modality: this.modality,
      documents: this.draftDocuments.map((document) => ({ ...document,
        validUntil: document.expiryMode === 'DATED' && document.validUntil
          ? `${document.validUntil}T23:59:59-05:00` : null,
      })),
    }));
  }

  act(action: 'return' | 'extend' | 'approve' | 'reject' | 'provision' | 'provision-existing' | 'resubmit-existing'): void {
    const id = this.selectedRequestId();
    const base = this.mutationBase();
    if (!id || !base) return;
    const dossier = this.documentary();
    if (action === 'provision' && dossier?.existingAccountRegularization) {
      this.reviewError.set('Este expediente corresponde a una cuenta existente. Utiliza su vinculación sin crear otra cuenta.'); return;
    }
    if (action === 'provision-existing' && (!dossier?.existingAccountRegularization
        || !dossier.existingUserId || !dossier.existingDriverId || !this.existingBindingConfirmed)) {
      this.reviewError.set('Comprueba la cuenta y ficha existentes y confirma expresamente su conservación antes de vincular.'); return;
    }
    if (action === 'resubmit-existing' && (!dossier?.existingAccountRegularization || dossier.status !== 'RETURNED')) {
      this.reviewError.set('Solo puede reabrirse expresamente la revisión de un expediente de cuenta existente devuelto para subsanación.'); return;
    }
    if (['return', 'extend', 'reject', 'resubmit-existing'].includes(action) && !this.reason.trim()) {
      this.reviewError.set('Ingresa un motivo sin datos personales para esta decisión.'); return;
    }
    if (action === 'extend' && !this.deadline) {
      this.reviewError.set('Selecciona el nuevo plazo.'); return;
    }
    if (action === 'provision' && !this.groupId) {
      this.reviewError.set('Selecciona un grupo activo. La aprobación personal se conserva sin crear acceso.'); return;
    }
    this.mutate(this.api.review(id, action, { ...base,
      reason: this.reason.trim() || (action === 'approve' ? 'Aprobación personal tras cotejo documental.'
        : action === 'provision-existing' ? 'Vinculación del expediente personalmente aprobado con la cuenta y ficha existentes.'
        : 'Alta tras aprobación personal y asignación de grupo.'),
      ...(action === 'extend' ? { deadline: new Date(this.deadline).toISOString() } : {}),
      ...(action === 'reject' ? { reiteration: this.reiteration } : {}),
      ...(action === 'provision' ? { groupId: this.groupId } : {}),
    }));
  }

  loadGroups(): void {
    this.groupsRequest?.unsubscribe();
    this.groupsRequest = this.management.listGroups({ page: 0, size: 100, sort: 'name,asc', active: true }).subscribe({
      next: (page) => this.activeGroups.set(page.items.filter((group) => group.active)),
      error: () => this.reviewError.set('No se pudieron cargar los grupos activos. No se ha creado la cuenta.'),
    });
  }

  fieldLabel(key: string): string {
    return ({ documentType: 'Tipo de documento', documentNumber: 'Número de documento', fullName: 'Nombre para cotejo',
      issuer: 'Emisor', licenseNumber: 'Número de licencia', licenseClass: 'Clase de licencia', licenseCategory: 'Categoría de licencia',
      restrictions: 'Restricciones relevantes', titleNumber: 'Número de habilitación', authority: 'Autoridad',
      serviceScope: 'Ámbito del servicio', operatorReference: 'Referencia del operador', requirementName: 'Requisito aplicable',
      minimumReference: 'Referencia mínima', eligibilityResult: 'Resultado de elegibilidad', reviewDate: 'Fecha de comprobación',
    } as Record<string, string>)[key] ?? key;
  }

  actionLabel(action: string): string {
    return ({ DOCUMENTS_REVIEWED: 'Cotejo documental registrado', DOCUMENTS_CORRECTED: 'Datos subsanados',
      RETURNED: 'Devuelta para subsanar', DEADLINE_EXTENDED: 'Plazo ampliado', RESUBMITTED: 'Reenviada para revisión',
      PERSONAL_APPROVED: 'Aprobación personal', REJECTED: 'Rechazo', ACCOUNT_PROVISIONED: 'Cuenta y conductor creados',
      LEGACY_REGULARIZATION_OPENED: 'Expediente de cuenta existente abierto',
      EXISTING_ACCOUNT_LINKED: 'Expediente vinculado a cuenta y conductor existentes',
      LEGACY_RESUBMITTED: 'Revisión reabierta tras subsanación',
      REAPPLIED: 'Nueva postulación',
    } as Record<string, string>)[action] ?? 'Movimiento de revisión';
  }

  private mutationBase(): DocumentaryMutation | null {
    const current = this.documentary();
    if (!current || this.saving() || this.documentaryLoading() || this.mustRefresh()) return null;
    return { expectedVersion: current.version, operationKey: crypto.randomUUID() };
  }

  private mutate(request: Observable<DocumentaryExpediente>): void {
    this.saving.set(true);
    this.reviewError.set(null);
    this.reviewNotice.set(null);
    this.mutationRequest = request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (detail) => {
        this.applyDocumentary(detail);
        this.reviewNotice.set('Cambio confirmado por el servidor.');
      },
      error: (error: unknown) => {
        this.mustRefresh.set(true);
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        this.reviewError.set(({ 400: 'Revisa los campos, fechas y referencias. Las observaciones no deben contener datos personales.',
          403: 'No tienes permiso para modificar este expediente.',
          404: 'El expediente no está disponible para tu organización.',
          409: 'La revisión cambió o la transición no está permitida. Actualiza antes de continuar.',
        } as Record<number, string>)[status] ?? 'No se confirmó el cambio. Consulta la revisión antes de realizar otra acción.');
      },
    });
  }

  private applyDocumentary(detail: DocumentaryExpediente): void {
    this.existingBindingConfirmed = false;
    this.documentary.set(detail);
    this.expediente.update((expediente) => expediente?.requestId === detail.requestId
      ? { ...expediente, status: detail.status } : expediente);
    this.modality = detail.modality ?? 'TAXI';
    this.draftDocuments = detail.catalog.map((requirement) => {
      const existing = detail.documents.find((document) => document.requirementId === requirement.requirementId);
      return { requirementId: requirement.requirementId,
        fields: Object.fromEntries(requirement.fieldKeys.map((key) => [key, existing?.fields[key] ?? ''])),
        expiryMode: existing?.expiryMode ?? 'UNKNOWN', validUntil: existing?.validUntil
          ? new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date(existing.validUntil)) : null,
        applicability: existing?.applicability ?? 'POR_CONTRASTAR', condition: existing?.condition ?? '',
        sourceReference: existing?.sourceReference ?? '', result: existing?.result ?? 'PENDING',
        reviewReference: existing?.reviewReference ?? '', observation: existing?.observation ?? '',
      };
    });
    this.requests.update((requests) => requests.map((request) => request.requestId === detail.requestId
      ? { ...request, status: detail.status, reviewRounds: detail.reviewRounds } : request));
  }

  private expedienteFailure(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 404) {
        return 'El expediente no está disponible para tu organización.';
      }
      if (error.status === 403) {
        return 'No tienes permiso para abrir este expediente.';
      }
    }
    return 'No se pudo abrir el expediente. Cierra esta vista e intenta nuevamente.';
  }
}
