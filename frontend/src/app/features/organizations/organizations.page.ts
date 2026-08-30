import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  ORGANIZATION_STATUSES,
  Organization,
  OrganizationPayload,
  OrganizationStatus,
} from '../../core/management/management.models';

@Component({
  selector: 'rf-organizations-page',
  imports: [ReactiveFormsModule],
  templateUrl: './organizations.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationsPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly statuses = ORGANIZATION_STATUSES;
  readonly organizations = signal<readonly Organization[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly editing = signal<Organization | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    legalName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)],
    }),
    tradeName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(120)],
    }),
    timezone: new FormControl('America/Lima', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    status: new FormControl<OrganizationStatus>('ACTIVE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listOrganizations({
        page,
        size: 20,
        sort: 'legalName,asc',
        search: this.search().trim() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.organizations.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las organizaciones.')),
      });
  }

  applySearch(value: string): void {
    this.search.set(value);
    this.load(0);
  }

  submit(): void {
    this.notice.set(null);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const current = this.editing();
    const value = this.form.getRawValue();
    this.saving.set(true);
    const request = current === null
      ? this.api.createOrganization({
          legalName: value.legalName.trim(),
          tradeName: value.tradeName.trim() || undefined,
          timezone: value.timezone.trim(),
        } satisfies OrganizationPayload)
      : this.api.updateOrganization(current.id, {
          legalName: value.legalName.trim(),
          tradeName: value.tradeName.trim() || undefined,
          timezone: value.timezone.trim(),
          status: value.status,
        });

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Organización creada correctamente.' : 'Organización actualizada correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar la organización.')),
    });
  }

  startEdit(organization: Organization): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(organization);
    this.form.reset({
      legalName: organization.legalName,
      tradeName: organization.tradeName ?? '',
      timezone: organization.timezone,
      status: organization.status,
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  statusLabel(status: OrganizationStatus): string {
    return ({
      ACTIVE: 'Activa',
      SUSPENDED: 'Suspendida',
      INACTIVE: 'Inactiva',
    } as Record<OrganizationStatus, string>)[status];
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

  private resetForm(): void {
    this.editing.set(null);
    this.form.reset({
      legalName: '',
      tradeName: '',
      timezone: 'America/Lima',
      status: 'ACTIVE',
    });
  }
}
