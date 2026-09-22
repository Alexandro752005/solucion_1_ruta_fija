import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { GroupPayload, TransportGroup } from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

@Component({
  selector: 'rf-groups-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './groups.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupsPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly session = inject(AuthSessionService);

  readonly canManage = computed(() => this.session.hasAnyRole(['ADMIN']));
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly activeFilter = signal<boolean | undefined>(undefined);
  readonly editing = signal<TransportGroup | null>(null);
  readonly formOpen = signal(false);
  readonly confirmingDeactivation = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  private deactivationApproved = false;

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(300)],
    }),
    active: new FormControl(true, { nonNullable: true }),
  });
  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listGroups({
      page,
      size: 20,
      sort: 'name,asc',
      search: this.search().trim() || undefined,
      active: this.activeFilter(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.groups.set(response.items);
        this.page.set(response.page);
        this.totalPages.set(response.totalPages);
        this.totalItems.set(response.totalItems);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los grupos.')),
    });
  }

  applyFilters(search: string, active: string): void {
    this.search.set(search);
    this.activeFilter.set(active === '' ? undefined : active === 'true');
    this.load(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.activeFilter.set(undefined);
    this.load(0);
  }

  startCreate(): void {
    this.notice.set(null);
    this.error.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  startEdit(group: TransportGroup): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(group);
    this.form.reset({ name: group.name, description: group.description ?? '', active: group.active });
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
    }
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
    if (current?.active && !value.active && !this.deactivationApproved) {
      this.confirmingDeactivation.set(true);
      return;
    }

    this.deactivationApproved = false;
    this.saving.set(true);
    const request = current === null
      ? this.api.createGroup({
          name: value.name.trim(),
          description: value.description.trim() || undefined,
        } satisfies GroupPayload)
      : this.api.updateGroup(current.id, {
          name: value.name.trim(),
          description: value.description.trim() || undefined,
          active: value.active,
        });

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Grupo creado correctamente.' : 'Grupo actualizado correctamente.');
        this.resetForm();
        this.load(this.page());
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el grupo.')),
    });
  }

  confirmDeactivation(): void {
    this.confirmingDeactivation.set(false);
    this.deactivationApproved = true;
    this.submit();
  }

  cancelDeactivation(): void {
    this.confirmingDeactivation.set(false);
  }

  previousPage(): void { if (this.page() > 0) this.load(this.page() - 1); }
  nextPage(): void { if (this.page() + 1 < this.totalPages()) this.load(this.page() + 1); }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.deactivationApproved = false;
    this.confirmingDeactivation.set(false);
    this.form.reset({ name: '', description: '', active: true });
    if (close) this.formOpen.set(false);
  }
}
