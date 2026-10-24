import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, Observable, of, switchMap } from 'rxjs';
import { ManagementApiService } from '../../core/management/management-api.service';
import { Organization } from '../../core/management/management.models';
import { AccountIdentityStatus, AdministrativeIdentityReview, AdminActivationCredential, PlatformAdmin, PlatformAdminsApiService } from '../../core/platform-admins/platform-admins-api.service';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

type FormMode = 'create' | 'replace' | 'rotate' | 'revoke' | 'owner' | 'review';

@Component({
  selector: 'rf-platform-admins-page',
  imports: [DatePipe, ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './platform-admins.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlatformAdminsPage implements OnDestroy {
  private readonly api = inject(PlatformAdminsApiService);
  private readonly management = inject(ManagementApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly admins = signal<readonly PlatformAdmin[]>([]);
  readonly organizations = signal<readonly Organization[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly mustRefresh = signal(false);
  readonly identityStatus = signal<AccountIdentityStatus | null>(null);
  readonly formMode = signal<FormMode | null>(null);
  readonly selected = signal<PlatformAdmin | null>(null);
  readonly credential = signal<AdminActivationCredential | null>(null);
  readonly form = new FormGroup({
    organizationId: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    fullName: new FormControl('', { nonNullable: true }),
    identityDocumentNumber: new FormControl('', { nonNullable: true }),
    identityReviewMethod: new FormControl<'PRIOR_SECURE_CONTACT' | 'IN_PERSON'>('IN_PERSON', { nonNullable: true }),
    identityReviewReference: new FormControl('', { nonNullable: true }),
    originalReviewed: new FormControl(false, { nonNullable: true }),
    identityConfirmed: new FormControl(false, { nonNullable: true }),
    currentPassword: new FormControl('', { nonNullable: true }),
    ownerDeclaration: new FormControl(false, { nonNullable: true }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
    verificationMethod: new FormControl<'PRIOR_SECURE_CONTACT' | 'IN_PERSON'>('PRIOR_SECURE_CONTACT', { nonNullable: true }),
    verificationReference: new FormControl('', { nonNullable: true }),
    identityChecked: new FormControl(false, { nonNullable: true }),
    replacementAuthorized: new FormControl(false, { nonNullable: true }),
    revocationConfirmed: new FormControl(false, { nonNullable: true }),
  });

  constructor() { this.load(); }

  load(): void {
    if (this.loading() || this.saving()) return;
    this.loading.set(true);
    this.error.set(null);
    this.identityStatus.set(null);
    this.api.identityStatus().pipe(switchMap((status) => {
      this.identityStatus.set(status);
      return status.identityVerified ? this.api.list() : of([]);
    }), takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false))).subscribe({
      next: (admins) => { this.admins.set(admins); this.mustRefresh.set(false); },
      error: () => { this.mustRefresh.set(true); this.error.set('No se pudo comprobar la identidad de la sesión ni consultar las cuentas ADMIN.'); },
    });
  }

  open(mode: FormMode, selected: PlatformAdmin | null = null): void {
    if (this.saving() || this.mustRefresh()) return;
    if (!this.identityStatus() || (mode !== 'owner' && !this.identityStatus()?.identityVerified)) return;
    if (mode === 'owner' && this.identityStatus()?.identityVerified) return;
    this.clearForm();
    this.credential.set(null);
    this.error.set(null);
    this.notice.set(null);
    this.selected.set(selected);
    this.formMode.set(mode);
    if (mode === 'create' || mode === 'replace') {
      this.form.controls.email.setValidators([Validators.required, Validators.email, Validators.maxLength(180)]);
      this.form.controls.fullName.setValidators([Validators.required, Validators.maxLength(160)]);
    }
    if (mode === 'create' || mode === 'replace' || mode === 'owner' || mode === 'review') {
      this.form.controls.identityDocumentNumber.setValidators([Validators.required, Validators.pattern(/^[0-9]{8}$/)]);
      this.form.controls.identityReviewReference.setValidators([Validators.required, Validators.maxLength(160)]);
      this.form.controls.originalReviewed.setValidators([Validators.requiredTrue]);
      this.form.controls.identityConfirmed.setValidators([Validators.requiredTrue]);
    }
    if (mode === 'owner') {
      this.form.controls.currentPassword.setValidators([Validators.required, Validators.maxLength(72)]);
      this.form.controls.ownerDeclaration.setValidators([Validators.requiredTrue]);
      this.form.controls.reason.clearValidators();
    }
    if (mode === 'create') {
      this.form.controls.organizationId.setValidators([Validators.required]);
      this.management.listOrganizations({ page: 0, size: 100, sort: 'legalName,asc' }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (page) => this.organizations.set(page.items.filter((org) => org.status === 'ACTIVE')),
        error: () => this.error.set('No se pudieron consultar las organizaciones activas.'),
      });
    }
    if (mode === 'replace') {
      this.form.controls.verificationReference.setValidators([Validators.required, Validators.maxLength(160)]);
      this.form.controls.identityChecked.setValidators([Validators.requiredTrue]);
      this.form.controls.replacementAuthorized.setValidators([Validators.requiredTrue]);
    }
    if (mode === 'revoke') this.form.controls.revocationConfirmed.setValidators([Validators.requiredTrue]);
    Object.values(this.form.controls).forEach((control) => control.updateValueAndValidity());
  }

  closeForm(): void {
    if (!this.saving()) this.clearForm();
  }

  hideCredential(): void { this.credential.set(null); }

  submit(): void {
    const mode = this.formMode();
    if (!mode || this.saving() || this.mustRefresh()) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    const selected = this.selected();
    const identityReview: AdministrativeIdentityReview = {
      documentType: 'DNI', documentNumber: value.identityDocumentNumber,
      method: value.identityReviewMethod, reference: value.identityReviewReference.trim(),
      originalReviewed: true, identityConfirmed: true,
    };
    let command: Observable<AdminActivationCredential | PlatformAdmin | AccountIdentityStatus>;
    if (mode === 'create') command = this.api.create({ organizationId: value.organizationId,
      email: value.email.trim(), fullName: value.fullName.trim(), reason: value.reason.trim(), identityReview });
    else if (selected && mode === 'replace') command = this.api.replace(selected.adminUserId, {
      email: value.email.trim(), fullName: value.fullName.trim(), verificationMethod: value.verificationMethod,
      verificationReference: value.verificationReference.trim(), reason: value.reason.trim(), identityChecked: true, replacementAuthorized: true, identityReview,
    });
    else if (selected && mode === 'revoke') command = this.api.revoke(selected.adminUserId, value.reason.trim());
    else if (mode === 'owner') command = this.api.enrollOwner(value.currentPassword, { ...identityReview, method: 'IN_PERSON' });
    else if (selected && mode === 'review') command = this.api.reviewIdentity(selected.adminUserId, identityReview, value.reason.trim());
    else if (selected) command = this.api.rotateCredential(selected.adminUserId, value.reason.trim());
    else return;
    this.saving.set(true);
    this.error.set(null);
    this.clearForm();
    command.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false))).subscribe({
      next: (result) => {
        this.clearForm();
        this.credential.set(mode === 'revoke' || mode === 'owner' || mode === 'review' ? null : result as AdminActivationCredential);
        if (mode === 'owner') this.identityStatus.set(result as AccountIdentityStatus);
        this.notice.set(mode === 'owner'
          ? 'El servidor confirmó la declaración de identidad del titular. No constituye una verificación por un tercero.'
          : mode === 'review' ? 'El servidor confirmó la revisión restringida de identidad de esta cuenta ADMIN.'
          : mode === 'revoke'
          ? 'El servidor confirmó la revocación de la cuenta ADMIN y sus sesiones. El reemplazo puede prepararse por separado.'
          : 'El servidor confirmó la credencial de activación. La cuenta todavía debe completar su activación.');
        if (this.identityStatus()?.identityVerified) this.api.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (admins) => this.admins.set(admins),
          error: () => { this.mustRefresh.set(true); this.error.set('La operación se confirmó, pero no se pudo actualizar el listado. Consulta las cuentas antes de continuar.'); } });
      },
      error: (error: unknown) => {
        this.mustRefresh.set(true);
        this.clearForm();
        this.error.set(error instanceof HttpErrorResponse && error.status === 409
          ? 'La operación no está disponible. Consulta las cuentas antes de continuar; el límite histórico también se comprueba en servidor.'
          : 'No se confirmó la operación. Consulta las cuentas antes de realizar otra acción.');
      },
    });
  }

  statusLabel(status: string): string {
    return ({ PENDING_CLAIM: 'Pendiente de reclamar credencial', IN_PROGRESS: 'Activación en curso', COMPLETED: 'Activación completada', NO_ACTIVATION: 'Sin activación registrada' } as Record<string, string>)[status] ?? 'Estado no disponible';
  }
  ngOnDestroy(): void { this.credential.set(null); this.clearForm(); }
  private clearForm(): void {
    this.formMode.set(null); this.selected.set(null);
    for (const key of ['organizationId', 'email', 'fullName', 'identityDocumentNumber', 'identityReviewReference', 'originalReviewed', 'identityConfirmed', 'currentPassword', 'ownerDeclaration', 'verificationReference', 'identityChecked', 'replacementAuthorized', 'revocationConfirmed'] as const) this.form.controls[key].clearValidators();
    this.form.controls.reason.setValidators([Validators.required, Validators.maxLength(500)]);
    this.form.reset({ organizationId: '', email: '', fullName: '', identityDocumentNumber: '', identityReviewMethod: 'IN_PERSON', identityReviewReference: '', originalReviewed: false, identityConfirmed: false,
      currentPassword: '', ownerDeclaration: false, reason: '', verificationMethod: 'PRIOR_SECURE_CONTACT', verificationReference: '', identityChecked: false, replacementAuthorized: false, revocationConfirmed: false });
  }
}
